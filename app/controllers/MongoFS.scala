package controllers

import java.io.OutputStream
import java.util.Date

import scala.collection.mutable.ListBuffer
import scala.compat.Platform

import org.slf4j.LoggerFactory

import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import me.lightspeed7.mongofs.{ MongoFile, MongoFileWriter }
import me.lightspeed7.mongofs.util.TimeMachine
import play.api.http.MimeTypes
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.api.mvc.{ Action, BodyParsers, Controller, MultipartFormData }

object MongoFS extends Controller {

  case class File(fileName: String, lookupUrl: String, storageSize: Long, contentType: String, duration: Long)

  case class Response(files: List[File], start: Date, duration: Long)

  implicit val fileFormat = Json.format[File]
  implicit val responseFormat = Json.format[Response]

  val logger: org.slf4j.Logger = LoggerFactory.getLogger(classOf[MongoFS]);

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

            val files: ListBuffer[File] = ListBuffer[File]()

            logger.info("Starting processing creation");
            request.body.files foreach {
              case MultipartFormData.FilePart(key, filename, contentType, file) =>
                {
                  // process the start time
                  val uploadStart: Long = file.get("start").toString().toLong
                  file.put("start", null) // clear it out
                  start = Math.min(start, uploadStart) // find the first file to upload

                  MongoConfig.imageFS.expireFile(file, TimeMachine.now().forward(2).hours().inTime)

                  val thisFile = File(file.getFilename(), file.getURL().getUrl().toString(),
                    file.getStorageLength(), file.getContentType(),
                    (Platform.currentTime - uploadStart) / 1000)

                  files += thisFile
                }
            }

            // send the response
            val duration = (Platform.currentTime - start) / 1000
            Ok(Json.toJson(Response(files.toList, new Date(start), duration)))

          } catch {
            case e: Exception => {

              logger.error(e.getMessage(), e)
              val duration = (Platform.currentTime - start) / 1000
              val response = Response(null, new Date(start), duration)
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
          mongoFile.put("start", uploadStart);
          mongoFile
        }

        logger.info("Starting file upload");

        // simply write the data straight to MongoDB as a mongoFS
        val mongoFileWriter: MongoFileWriter = MongoConfig.imageFS.createNew(filename, contentType.get)
        logger.info("Getting mongo input file");

        // stream the data through the proxy
        Iteratee.fold[Array[Byte], OutputStream](
          mongoFileWriter.getOutputStream()) { (os, data) =>
            // println("Writing data chunk");
            os.write(data)
            os
          }.map { os =>
            logger.info("Closing output stream");
            os.close()
            setStartTime(mongoFileWriter.getMongoFile())
          }
    }

}

class MongoFS {}
