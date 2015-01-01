package me.lightspeed7.mongoFS.tutorial.image

import java.io.{ FileNotFoundException, InputStream }
import java.util.Date

import scala.util.{ Failure, Success, Try }

import org.bson.types.ObjectId

import com.mongodb.{ BasicDBObject, WriteConcern }

import me.lightspeed7.mongoFS.tutorial.image.Image.fromMongoDB
import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import me.lightspeed7.mongofs.{ MongoFile, MongoFileConstants }
import me.lightspeed7.mongofs.url.MongoFileUrl
import play.api.Logger

object ImageService {

  //
  // MongoDB lookup methods
  // //////////////////////////
  def find(uuid: String): Try[Image] = {
    val file = MongoConfig.images.findOne(new BasicDBObject("_id", new ObjectId(uuid)))
    if (file != null) Success(file) else Failure(new FileNotFoundException(s"Could not find file for uuid =${uuid}"))
  }

  def getMongoFile(url: MongoFileUrl): Try[MongoFile] = {
    val file = MongoConfig.imageFS.findOne(url)
    if (file != null) Success(file) else Failure(new FileNotFoundException(s"Could not find file for url =${url}"))
  }

  private[image] def determineSizeUrl(size: String, image: Image): Try[MongoFileUrl] = {
    val url: Option[String] = size match {
      case "medium" => image.mediumUrl
      case "thumb"  => image.thumbUrl
      case "image"  => Option(image.imageUrl)
      case _        => None
    }
    url.map { m => Success(MongoFileUrl.construct(m)) } //
      .getOrElse(Failure(new IllegalArgumentException("Invalid image size requested")))
  }

  def inputStreamForURL(uuid: String, size: String): Try[(Long, String, InputStream)] = {

    for {
      image <- find(uuid)
      url <- determineSizeUrl(size, image)
      file <- getMongoFile(url)
    } yield {
      (file.getLength, file.getContentType, file.getInputStream)
    }
  }

  def list(orderBy: BasicDBObject) = {
    val f = MongoConfig.images.find().sort(orderBy)
    f.iterator()
  }

  //
  // MongoDB Update Methods
  // ////////////////////////////
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

}
