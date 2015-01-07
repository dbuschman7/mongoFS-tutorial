package controllers

import java.io.{ FileInputStream, InputStream, OutputStream }

import scala.collection.JavaConversions.asScalaBuffer
import scala.compat.Platform
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }

import akka.actor.{ PoisonPill, Props, actorRef2Scala }
import me.lightspeed7.mongoFS.tutorial.file.Actors.{ Listener, Load, Loader, statistics, updater }
import me.lightspeed7.mongoFS.tutorial.file.FileService
import me.lightspeed7.mongofs.{ MongoFile, MongoFileWriter }
import play.Logger
import play.api.http.MimeTypes
import play.api.libs.EventSource
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{ Concurrent, Enumeratee, Enumerator, Iteratee }
import play.api.libs.json.{ JsValue, Json }
import play.api.mvc.{ Action, BodyParsers, Controller, MultipartFormData, ResponseHeader, Result }
import play.libs.Akka

object MongoFS extends Controller {

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

  def download(uuid: String) = Action.async { request =>

    Future {
      val input: Try[(Long, String, InputStream)] = FileService.inputStreamForURL(uuid)

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

  def expand(gzip: Boolean, encrypted: Boolean) = Action.async { request =>
    request.body.asMultipartFormData.map { part =>
      part.files.foreach { file =>
        val contentType = file.contentType
        val filename = file.filename

        val tempFile = file.ref.file
        println(tempFile.getCanonicalPath)
        val in = new FileInputStream(tempFile)
        try {
          val mfw = FileService.createNew(filename, contentType.get, gzip, encrypted).get
          val manifest = mfw.uploadZipFile(in)

          import scala.collection.JavaConversions._
          val files = manifest.getFiles :+ manifest.getZip
          files.foreach { file =>
            println(s"MongoFile - ${file.getURL.toString()}")
            updater ! file
          }
        } catch {
          case e: Exception => {
            println(e.getMessage)
            e.printStackTrace()
          }
        }

      }

    }

    Future(Ok("Success"))
  }

  def upload(gzip: Boolean, encrypted: Boolean) =
    Action(parse.multipartFormData(handleFilePartToMongo(gzip, encrypted))) {
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

                  statistics ! file
                  updater ! file
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

  def handleFilePartToMongo(gzip: Boolean, encrypted: Boolean): BodyParsers.parse.Multipart.PartHandler[MultipartFormData.FilePart[MongoFile]] =
    parse.Multipart.handleFilePart {
      case parse.Multipart.FileInfo(partName, filename, contentType) =>

        val uploadStart: Long = Platform.currentTime

        def setStartTime(mongoFile: MongoFile): MongoFile = {
          mongoFile.put("start", uploadStart)
          mongoFile
        }

        // simply write the data straight to MongoDB as a mongoFS file
        val f = FileService.createNew(filename, contentType.get, gzip, encrypted)
        val mongoFileWriter: MongoFileWriter = f.get
        val out: OutputStream = f.get.getOutputStream

        Iteratee.fold[Array[Byte], OutputStream](out) { (out, data) =>
          out.write(data)
          out
        }.map { out =>
          out.close()
          val file = setStartTime(mongoFileWriter.getMongoFile())
          file
        }
    }

}

class MongoFS {}
