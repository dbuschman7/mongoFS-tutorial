package me.lightspeed7.mongoFS.tutorial.image

import java.awt.image.BufferedImage
import scala.util.Try
import org.bson.types.ObjectId
import org.imgscalr.Scalr
import com.mongodb.{ BasicDBObject, WriteConcern }
import javax.imageio.ImageIO
import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import me.lightspeed7.mongofs.{ MongoFile, MongoFileConstants, MongoFileWriter }
import me.lightspeed7.mongofs.url.MongoFileUrl
import play.api.Logger
import java.util.Date
import scala.concurrent.Future

object ImageService {

  //
  // MongoDB methods
  // //////////////////////////
  def insert(file: MongoFile): Option[ObjectId] = {
    try {
      val obj = new BasicDBObject("_id", file.getId()) //
        .append("imageUrl", file.getURL.toString()) //
        .append("ts", new Date())

      MongoConfig.images.save(obj)
      Some(file.getId)
    } catch {
      case e: Exception => {
        Logger.error("Error trying to save image to MongoDB", e);
        None
      }
    }
  }

  def list(orderBy: BasicDBObject) = {
    val f = MongoConfig.images.find().sort(orderBy)
    f.iterator()
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
