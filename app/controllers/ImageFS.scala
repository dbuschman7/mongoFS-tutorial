package controllers

import java.io.{ InputStream, OutputStream }

import scala.compat.Platform
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

import akka.actor.{ PoisonPill, Props, actorRef2Scala }
import me.lightspeed7.mongoFS.tutorial.image.Actors.{ CreateImage, Listener, Load, Loader, thumbnailers }
import me.lightspeed7.mongoFS.tutorial.image.ImageService
import me.lightspeed7.mongofs.{ MongoFile, MongoFileWriter }
import play.Logger
import play.api.http.MimeTypes
import play.api.libs.EventSource
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{ Concurrent, Enumeratee, Enumerator, Iteratee }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, BodyParsers, Controller, MultipartFormData, ResponseHeader, Result }
import play.libs.Akka

object ImageFS extends Controller {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  case class Response(duration: Long)
  implicit val responseFormat = Json.format[Response]

  def sse(uuid: String) = Action.async { req =>

    Future {
      println(req.remoteAddress + " - SSE connected")
      val (out, channel) = Concurrent.broadcast[JsValue]

      // create unique actor for each uuid
      println(s"Creating Listener - ${uuid}")
      val listener = Akka.system.actorOf(Props(classOf[Listener], uuid, channel))

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

      Akka.system.actorOf(Props(classOf[Loader], "Loader" + uuid)) ! Load(listener)
      result
    }

  }

  def download(size: String, uuid: String) = Action.async { request =>

    Future {
      val input: Try[(Long, String, InputStream)] = ImageService.inputStreamForURL(uuid, size)

      input match {
        case Success((length, media, is)) => {
          Result(
            header = ResponseHeader(200, Map(CONTENT_LENGTH -> length.toString, CONTENT_TYPE -> media)),
            body = Enumerator.fromStream(is))
        }
        case Failure(f) => {
          NoContent
        }
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

  def handleFilePartToMongo: BodyParsers.parse.Multipart.PartHandler[MultipartFormData.FilePart[MongoFile]] =
    parse.Multipart.handleFilePart {
      case parse.Multipart.FileInfo(partName, filename, contentType) =>

        val uploadStart: Long = Platform.currentTime

        def setStartTime(mongoFile: MongoFile): MongoFile = {
          mongoFile.put("start", uploadStart)
          mongoFile
        }

        // simply write the data straight to MongoDB as a mongoFS file
        val f = ImageService.createNew(filename, contentType.get)
        val mongoFileWriter: MongoFileWriter = f.get

        Iteratee.fold[Array[Byte], OutputStream](
          mongoFileWriter.getOutputStream()) { (os, data) =>
            os.write(data)
            os
          }.map { os =>
            os.close()
            val file = setStartTime(mongoFileWriter.getMongoFile())
            thumbnailers ! CreateImage(file)
            file
          }
    }

}

class ImageFS {}
