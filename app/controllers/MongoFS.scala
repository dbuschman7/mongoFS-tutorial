package controllers

import java.io.{ InputStream, OutputStream }
import scala.compat.Platform
import scala.concurrent.Future
import scala.util.{ Failure, Success, Try }
import org.slf4j.LoggerFactory
import akka.actor.actorRef2Scala
import me.lightspeed7.mongoFS.tutorial.Actors.{ CreateImage, thumbnailers }
import me.lightspeed7.mongoFS.tutorial.image.ImageService
import me.lightspeed7.mongofs.{ MongoFile, MongoFileWriter }
import play.api.http.MimeTypes
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{ Enumerator, Iteratee }
import play.api.libs.json.Json
import play.api.mvc.{ Action, BodyParsers, Controller, MultipartFormData, ResponseHeader, Result }
import play.Logger

object MongoFS extends Controller {

  case class Response(duration: Long)
  implicit val responseFormat = Json.format[Response]

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

class MongoFS {}
