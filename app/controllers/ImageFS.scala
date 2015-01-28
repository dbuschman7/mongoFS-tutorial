package controllers

import java.io.{ InputStream, OutputStream }

import scala.compat.Platform
import scala.concurrent.Future
import scala.util.{ Failure, Success }

import akka.actor.{ ActorSystem, PoisonPill, Props, actorRef2Scala }
import me.lightspeed7.mongoFS.tutorial.MongoCircuitBreaker
import me.lightspeed7.mongoFS.tutorial.image.Actors.{ CreateImage, Listener, Load, Loader, thumbnailers }
import me.lightspeed7.mongoFS.tutorial.image.ImageService
import me.lightspeed7.mongofs.MongoFile
import play.Logger
import play.api.http.MimeTypes
import play.api.libs.EventSource
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{ Concurrent, Enumeratee, Enumerator, Iteratee }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Controller, MultipartFormData }
import play.api.mvc.{ ResponseHeader, Result }
import play.api.mvc.Action
import play.api.mvc.BodyParsers.parse.Multipart.PartHandler
import play.api.mvc.MultipartFormData.FilePart
import play.libs.Akka

object ImageFS extends Controller with MongoCircuitBreaker {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  case class Response(duration: Long)
  implicit val responseFormat = Json.format[Response]
  implicit val system: ActorSystem = Akka.system

  def sse(uuid: String) = Action.async { req =>

    breaker.withCircuitBreaker {
      Future {
        println(req.remoteAddress + " - SSE connected")
        val (out, channel) = Concurrent.broadcast[JsValue]

        // create unique actor for each uuid
        println(s"Creating Listener - ${uuid}")
        val listener = system.actorOf(Props(classOf[Listener], uuid, channel))

        def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
          Enumeratee.onIterateeDone { () =>
            {
              println(addr + " - SSE disconnected")
              listener ! PoisonPill
            }
          }

        val result =
          Ok.feed(out
            &> Concurrent.buffer(300)
            &> connDeathWatch(req.remoteAddress)
            &> EventSource()).as("text/event-stream")

        system.actorOf(Props(classOf[Loader], "Loader" + uuid)) ! Load(listener)
        result
      }
    }
  }

  def download(size: String, uuid: String) = Action.async { request =>

    breaker.withCircuitBreaker {
      println(s"Download - $size => $uuid")
      val future: Future[(Long, String, InputStream)] = ImageService.inputStreamForURL(uuid, size)
      future.map { data =>
        Result(body = Enumerator.fromStream(data._3),
          header = ResponseHeader(200, Map(CONTENT_LENGTH -> data._1.toString, CONTENT_TYPE -> data._2)))
      }
    }
  }

  def upload() =
    Action(parse.multipartFormData(handleFilePartToMongo)) {
      request =>
        {
          val accept: String = request.headers.get("Accept").getOrElse(MimeTypes.JSON) match {
            case "*/*"     => MimeTypes.JSON
            case s: String => s
          }

          var start: Long = Platform.currentTime
          try {
            request.body.files foreach {
              case MultipartFormData.FilePart(key, filename, contentType, file) =>
                {
                  // process the start time
                  val uploadStart: Long = file.get("start").toString().toLong
                  file.put("start", null) // clear it out
                  start = Math.min(start, uploadStart) // find the first file to upload
                }
            }

            // send the response
            val duration = (Platform.currentTime - start) / 1000
            Logger.info(s"Files Uploaded - ${duration} seconds ")
            Ok(Json.toJson(Response(duration)))

          } catch {
            case e: Exception => {
              Logger.error(e.getMessage(), e)
              val duration = (Platform.currentTime - start) / 1000
              val response = Response(duration)
              InternalServerError(Json.toJson(response))
            }
          }
        }
    }

  def handleFilePartToMongo: PartHandler[FilePart[MongoFile]] =
    parse.Multipart.handleFilePart {
      case parse.Multipart.FileInfo(partName, filename, contentType) =>

        val uploadStart: Long = Platform.currentTime

        def setStartTime(mongoFile: MongoFile): MongoFile = {
          mongoFile.put("start", uploadStart)
          mongoFile
        }

        // simply write the data straight to MongoDB as a mongoFS file
        ImageService.createNew(filename, contentType.get).map { writer =>
          Iteratee.fold[Array[Byte], OutputStream](writer.getOutputStream()) { (os, data) =>
            os.write(data); os
          }.map { os =>
            os.close()
            val file = setStartTime(writer.getMongoFile())
            thumbnailers ! CreateImage(file)
            file
          }
        }.getOrElse(null)
    }

}

class ImageFS {}
