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
import org.mongodb.MongoCollection
import com.mongodb.ReadPreference
import com.mongodb.DBCollection
import com.mongodb.DBObject
import java.util.ArrayList
import com.mongodb.BasicDBObject
import me.lightspeed7.mongoFS.tutorial.image.StatsData

object FileService {

  val baseName = "files"

  private lazy val db: MongoDatabase = init()

  private lazy val ffMongoFS: MongoFileStore = buildStore(baseName, false, false, ChunkSize.medium_128K)
  private lazy val ftMongoFS: MongoFileStore = buildStore(baseName, false, true, ChunkSize.medium_128K)
  private lazy val tfMongoFS: MongoFileStore = buildStore(baseName, true, false, ChunkSize.medium_128K)
  private lazy val ttMongoFS: MongoFileStore = buildStore(baseName, true, true, ChunkSize.medium_128K)

  private lazy val filesColl: DBCollection = buildFilesCollection(baseName)

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

  def generateStats(): StatsData = {
    // (files:Long, bytes:Long, storage:long)
    //    db.files.files.aggregate([
    //  { $project : { '_id': 1, 'length': 1, 'storage':1 } },
    //  { $group : { _id: 1, count : { $sum :  1 }, 'length' : { $sum :  '$length' }, 'storage' : { $sum :  '$storage' } } }
    //])

    var pipeline = new ArrayList[DBObject]()
    pipeline.add(new BasicDBObject("$project", new BasicDBObject("_id", 1).append("length", 1).append("storage", 1)))

    val group = new BasicDBObject("_id", 1) //
      .append("count", new BasicDBObject("$sum", 1))
      .append("length", new BasicDBObject("$sum", "$length"))
      .append("storage", new BasicDBObject("$sum", "$storage"))
    pipeline.add(new BasicDBObject("$group", group))

    val result = filesColl.aggregate(pipeline)

    val iter = result.results().iterator()
    if (iter.hasNext()) {
      val cur = iter.next()
      val count = cur.get("count").asInstanceOf[Integer]
      val length = cur.get("length").asInstanceOf[Long]
      val storage = cur.get("storage").asInstanceOf[Long]
      StatsData(count.longValue(), length, storage)
    } else {
      StatsData(0, 0, 0)
    }
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

  private[file] def buildFilesCollection(bucketName: String): DBCollection = {
    val coll = db.getSurrogate.getCollection(baseName + ".files")
    coll.setReadPreference(ReadPreference.primaryPreferred())
    coll.setWriteConcern(WriteConcern.ACKNOWLEDGED);
    coll
  }

  def dumpConfig: Unit = {
    println(ffMongoFS.toString())
    println(ftMongoFS.toString())
    println(tfMongoFS.toString())
    println(ttMongoFS.toString())

  }
}
