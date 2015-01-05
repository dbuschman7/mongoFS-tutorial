package me.lightspeed7.mongoFS.tutorial.image

import java.io.{ FileNotFoundException, InputStream }
import java.util.Date

import scala.util.{ Failure, Success, Try }

import org.bson.types.ObjectId
import org.mongodb.MongoDatabase
import org.slf4j.LoggerFactory

import com.mongodb.{ BasicDBObject, DBCollection, MongoClient, MongoClientURI, ReadPreference, WriteConcern }

import me.lightspeed7.mongoFS.tutorial.image.Image.fromMongoDB
import me.lightspeed7.mongofs.{ MongoFile, MongoFileConstants, MongoFileStore, MongoFileStoreConfig, MongoFileWriter }
import me.lightspeed7.mongofs.crypto.BasicCrypto
import me.lightspeed7.mongofs.url.MongoFileUrl
import me.lightspeed7.mongofs.util.ChunkSize

object ImageService {

  val baseName = "images"

  private lazy val db: MongoDatabase = init()
  private lazy val imageFS: MongoFileStore = buildStore(baseName, ChunkSize.medium_128K)
  private lazy val images: DBCollection = buildCollection(baseName)

  var hostUrl: String = _

  def setHostUrl(url: String) = {
    this.hostUrl = url
  }

  //
  // MongoDB lookup methods
  // //////////////////////////
  def find(uuid: String): Try[Image] = {
    val file = images.findOne(new BasicDBObject("_id", new ObjectId(uuid)))
    if (file != null) Success(file) else Failure(new FileNotFoundException(s"Could not find file for uuid =${uuid}"))
  }

  def getMongoFile(imageUrl: String): Try[MongoFile] = {
    getMongoFile(MongoFileUrl.construct(imageUrl))
  }

  def getMongoFile(url: MongoFileUrl): Try[MongoFile] = {
    val file = imageFS.findOne(url)
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
    val f = images.find().sort(orderBy)
    f.iterator()
  }

  //
  // MongoFS create file
  // ////////////////////////////
  def createNew(filename: String, contentType: String): Try[MongoFileWriter] = {
    Try(imageFS.createNew(filename, contentType))
  }

  //
  // MongoDB Update Methods
  // ////////////////////////////
  def insert(file: MongoFile): Option[ObjectId] = {
    try {
      val obj = new BasicDBObject("_id", file.getId()) //
        .append("imageUrl", file.getURL.toString()) //
        .append("ts", new Date())

      images.save(obj)
      Some(file.getId)
    } catch {
      case e: Exception => {
        LoggerFactory.getLogger(this.getClass).error("Error trying to save image to MongoDB", e);
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
      images.update(find, update, true, false, WriteConcern.ACKNOWLEDGED);
      val id: ObjectId = find.getObjectId(MongoFileConstants._id.name())
      Some(id)
    } catch {
      case e: Exception => {
        LoggerFactory.getLogger(this.getClass).error(s"Error trying to ${label} image to MongoDB", e);
        None
      }
    }
  }

  //
  // MongoConfig
  // ////////////////////////////////
  private[image] def init(): MongoDatabase = {
    val uri = new MongoClientURI(hostUrl)
    println("Mongo Connection(images) - standing up ...")
    val client = new MongoClient(uri);
    val db = new MongoDatabase(client.getDB(uri.getDatabase()));
    println("Mongo Connection(images) - ready!")
    db
  }

  private[image] def buildStore(bucketName: String, chunkSize: ChunkSize = ChunkSize.small_64K): MongoFileStore = {
    val config = MongoFileStoreConfig.builder().bucket(bucketName) //
      .enableCompression(true) //
      .enableEncryption(new BasicCrypto()) //
      .asyncDeletes(true) //
      .chunkSize(chunkSize) //
      .writeConcern(WriteConcern.ACKNOWLEDGED) //
      .build();

    new MongoFileStore(db, config)
  }

  private[image] def buildCollection(bucketName: String): DBCollection = {
    val coll = db.getSurrogate.getCollection(baseName)
    coll.setReadPreference(ReadPreference.primaryPreferred())
    coll.setWriteConcern(WriteConcern.ACKNOWLEDGED);
    coll
  }

  def dumpConfig: Unit = {
    println(images.toString())
    println(imageFS.toString())

  }
}
