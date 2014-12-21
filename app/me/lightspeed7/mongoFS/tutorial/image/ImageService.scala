package me.lightspeed7.mongoFS.tutorial.image

import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import me.lightspeed7.mongofs.MongoFile
import me.lightspeed7.mongofs.MongoFileWriter
import org.mongodb.Document
import org.mongodb.MongoCollection
import org.imgscalr._
import org.imgscalr.Scalr._
import play.api.libs.json._
import play.api.libs.functional.syntax._
import scala.util.Try
import me.lightspeed7.mongofs.MongoFileConstants
import org.bson.types.ObjectId
import org.bson.types.ObjectId
import play.api.Logger
import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import me.lightspeed7.mongofs.url.MongoFileUrl
import akka.io.Tcp.WriteCommand
import com.mongodb.WriteConcern
import com.mongodb.BasicDBObject

case class Image(
  _id: Option[ObjectId],
  imageUrl: String,
  thumbUrl: Option[String],
  mediumUrl: Option[String])

object Image {

  // JSON formatting
  implicit val objectIdFormat: Format[ObjectId] = new Format[ObjectId] {

    def reads(json: JsValue) = {
      json match {
        case jsString: JsString => {
          if (ObjectId.isValid(jsString.value)) JsSuccess(new ObjectId(jsString.value))
          else JsError("Invalid ObjectId")
        }
        case other => JsError("Can't parse json path as an ObjectId. Json content = " + other.toString())
      }
    }

    def writes(oId: ObjectId): JsValue = {
      JsString(oId.toString)
    }

  }
  implicit val imageFmt = Json.format[Image]

  def fields() = {
    val fields = (Map[String, Any]() /: Image.getClass.getDeclaredFields) { (a, f) =>
      f.setAccessible(true)
      a + (f.getName -> f.get(this))
    }
  }

}

object ImageService {

  //
  // MongoDB methods
  // //////////////////////////
  def insert(file: MongoFile): Option[ObjectId] = {
    try {
      val obj = new BasicDBObject("_id", file.getId()).append("imageUrl", file.getURL.toString())
 
      MongoConfig.images.save(obj)
      Some(file.getId)
    } catch {
      case e: Exception => {
        Logger.error("Error trying to save image to MongoDB", e);
        None
      }
    }
  }

  def updateMediumUrl(id: ObjectId, mediumUrl: MongoFileUrl): Option[ObjectId] = {
    doUpdate( //
      new BasicDBObject(MongoFileConstants._id.name(), id), //
      new BasicDBObject("$set", new BasicDBObject("mediumUrl", mediumUrl.toString())), //    
      "updateMediumUrl")
  }

  def updateThumbUrl(id: ObjectId, thumbUrl: MongoFileUrl): Option[ObjectId] = {
    doUpdate( //
      new BasicDBObject(MongoFileConstants._id.name(), id), //
      new BasicDBObject("$set", new BasicDBObject("thumbUrl", thumbUrl.toString())), //
      "updateThumbUrl")
  }

  private[image] def doUpdate(find: BasicDBObject, update: BasicDBObject, label: String): Option[ObjectId] = {
    try {
      MongoConfig.images.update(find, update, true, false, WriteConcern.ACKNOWLEDGED);
      val id: ObjectId = find.getObjectId(MongoFileConstants._id.name())
      Some(id)
    } catch {
      case e: Exception => {
        Logger.error(s"Error trying to ${label} image to MongoDB", e);
        None
      }
    }
  }

  // 
  // Thumbnail creation
  // //////////////////////////
  def createThumbnail(source: MongoFile, sink: MongoFileWriter, sideLength: Int) = {
    writeInto(sink, scaled(from(source), sideLength))
  }

  private[image] def scaled(image: BufferedImage, sideLength: Int): BufferedImage = {
    Scalr.resize(image, Scalr.Method.AUTOMATIC, Scalr.Mode.AUTOMATIC, sideLength, Scalr.OP_ANTIALIAS);
  }

  private[image] def from(file: MongoFile): BufferedImage = {
    ImageIO.read(file.getInputStream)
  }

  private[image] def writeInto(writer: MongoFileWriter, img: BufferedImage): MongoFile = {

    val out = writer.getOutputStream
    Try(ImageIO.write(img, "png", out)).map(_ => out.close)
    writer.getMongoFile
  }
}

