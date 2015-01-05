package me.lightspeed7.mongoFS.tutorial.file

import java.io.{ FileNotFoundException, InputStream }

import scala.util.{ Failure, Success, Try }

import org.bson.types.ObjectId
import org.mongodb.{ Document, MongoDatabase }

import com.mongodb.{ MongoClient, MongoClientURI, WriteConcern }

import me.lightspeed7.mongoFS.tutorial.file.UiFile.fromMongoDB
import me.lightspeed7.mongofs.{ MongoFileStore, MongoFileStoreConfig, MongoFileWriter }
import me.lightspeed7.mongofs.crypto.BasicCrypto
import me.lightspeed7.mongofs.util.ChunkSize

object FileService {

  val baseName = "files"

  private lazy val db: MongoDatabase = init()

  private lazy val ffMongoFS: MongoFileStore = buildStore(baseName, false, false, ChunkSize.medium_128K)
  private lazy val ftMongoFS: MongoFileStore = buildStore(baseName, false, true, ChunkSize.medium_128K)
  private lazy val tfMongoFS: MongoFileStore = buildStore(baseName, true, false, ChunkSize.medium_128K)
  private lazy val ttMongoFS: MongoFileStore = buildStore(baseName, true, true, ChunkSize.medium_128K)

  var hostUrl: String = _

  def setHostUrl(url: String) = {
    this.hostUrl = url
  }

  //
  // MongoDB lookup methods
  // //////////////////////////
  def find(uuid: String): Try[UiFile] = {
    val mongoFile = ttMongoFS.findOne(new ObjectId(uuid))
    val uiFile: UiFile = mongoFile
    if (uiFile != null) Success(uiFile) else Failure(new FileNotFoundException(s"Could not find file for uuid =${uuid}"))
  }

  def inputStreamForURL(uuid: String): Try[(Long, String, InputStream)] = {
    for {
      file <- Try(ttMongoFS.findOne(new ObjectId(uuid)))
    } yield {
      (file.getLength, file.getContentType, file.getInputStream)
    }
  }

  def list(orderBy: Document) = {
    val f = ttMongoFS.find(new Document("filename", new Document("$exists", 1)), orderBy)
    f.iterator()
  }

  //
  // MongoFS create file
  // ////////////////////////////

  def createNew(filename: String, contentType: String, gzip: Boolean, encrypted: Boolean): Try[MongoFileWriter] = {

    // old way
    //    val fs = if (gzip == false && encrypted == false) ffMongoFS
    //    else if (gzip == true && encrypted == false) tfMongoFS
    //    else if (gzip == false && encrypted == true) ftMongoFS
    //    else ttMongoFS

    val fs = (gzip, encrypted) match {
      case (false, false) => ffMongoFS
      case (false, true)  => ftMongoFS
      case (true, false)  => tfMongoFS
      case (true, true)   => ttMongoFS
    }

    Try(fs.createNew(filename, contentType))
  }

  //
  // MongoConfig
  // ////////////////////////////////
  private[file] def init(): MongoDatabase = {
    val uri = new MongoClientURI(hostUrl)
    println("Mongo Connection(files) - standing up ...")
    val client = new MongoClient(uri);
    val db = new MongoDatabase(client.getDB(uri.getDatabase()));
    println("Mongo Connection(files) - ready!")
    db
  }

  private[file] def buildStore(bucketName: String, gzip: Boolean, encrypted: Boolean, chunkSize: ChunkSize = ChunkSize.small_64K): MongoFileStore = {
    val config = MongoFileStoreConfig.builder().bucket(bucketName) //
      .enableCompression(gzip) //
      .enableEncryption(if (encrypted) new BasicCrypto() else null) //
      .asyncDeletes(true) //
      .chunkSize(chunkSize) //
      .writeConcern(WriteConcern.ACKNOWLEDGED) //
      .build();

    new MongoFileStore(db, config)
  }

  def dumpConfig: Unit = {
    println(ffMongoFS.toString())
    println(ftMongoFS.toString())
    println(tfMongoFS.toString())
    println(ttMongoFS.toString())

  }
}
