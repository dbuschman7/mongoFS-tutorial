package me.lightspeed7.mongoFS.tutorial.util

import org.mongodb.MongoDatabase

import com.mongodb.{ DBCollection, MongoClient, MongoClientURI, ReadPreference, WriteConcern }

import me.lightspeed7.mongofs.{ MongoFileStore, MongoFileStoreConfig }
import me.lightspeed7.mongofs.crypto.BasicCrypto
import me.lightspeed7.mongofs.util.ChunkSize

class MongoConfig

object MongoConfig {

  val baseName = "images"
  var hostUrl: String = _

  lazy val db: MongoDatabase = init()
  lazy val imageFS: MongoFileStore = buildStore(baseName, ChunkSize.medium_128K)
  lazy val images: DBCollection = buildCollection(baseName)

  def setHostUrl(url: String) = {
    this.hostUrl = url
  }

  private[util] def init() = {
    val uri = new MongoClientURI(hostUrl)
    println("Mongo Connection - standing up ...")
    val client = new MongoClient(uri);
    val db = new MongoDatabase(client.getDB(uri.getDatabase()));
    println("Mongo Connection - ready!")
    db
  }

  private[util] def buildStore(bucketName: String, chunkSize: ChunkSize = ChunkSize.small_64K): MongoFileStore = {
    val config = MongoFileStoreConfig.builder().bucket(bucketName) //
      .enableCompression(true) //
      .enableEncryption(new BasicCrypto()) //
      .asyncDeletes(true) //
      .chunkSize(chunkSize) //
      .writeConcern(WriteConcern.ACKNOWLEDGED) //
      .build();

    new MongoFileStore(db, config)

  }

  private[util] def buildCollection(bucketName: String): DBCollection = {

    val coll = db.getSurrogate.getCollection(baseName)
    coll.setReadPreference(ReadPreference.primaryPreferred())
    coll.setWriteConcern(WriteConcern.ACKNOWLEDGED);
    coll

  }
}
