package me.lightspeed7.mongoFS.tutorial.util

import me.lightspeed7.mongofs.MongoFileStore
import com.mongodb.DB
import com.mongodb.MongoClientURI
import com.mongodb.MongoClient
import me.lightspeed7.mongofs.MongoFileStoreConfig
import me.lightspeed7.mongofs.util.ChunkSize
import com.mongodb.WriteConcern
import me.lightspeed7.mongofs.crypto.BasicCrypto
import org.mongodb.MongoCollection
import com.mongodb.DBCollection
import org.mongodb.MongoDatabase
import org.mongodb.MongoCollectionOptions
import com.mongodb.ReadPreference
import org.mongodb.Document

object MongoConfig {

  var db: MongoDatabase = _
  val baseName = "images"

  lazy val imageFS: MongoFileStore = buildStore(baseName, ChunkSize.medium_128K)
  lazy val images: DBCollection = buildCollection(baseName)

  def init(hostUrl: String) = {

    println("Mongo Connection - standing up ...")
    val uri = new MongoClientURI(hostUrl)
    val client = new MongoClient(uri);
    db = new MongoDatabase(client.getDB(uri.getDatabase()));
    println("Mongo Connection - ready!")

  }

  def buildStore(bucketName: String, chunkSize: ChunkSize = ChunkSize.small_64K): MongoFileStore = {
    val config = MongoFileStoreConfig.builder().bucket(bucketName) //
      .enableCompression(true) //
      .enableEncryption(new BasicCrypto()) // 
      .asyncDeletes(true) //
      .chunkSize(chunkSize) //
      .writeConcern(WriteConcern.ACKNOWLEDGED) //
      .build();

    new MongoFileStore(db, config)

  }

  def buildCollection(bucketName: String): DBCollection = {

    val coll = db.getSurrogate.getCollection(baseName) 
    coll.setReadPreference(ReadPreference.primaryPreferred())
    coll.setWriteConcern(WriteConcern.ACKNOWLEDGED);
    coll
    
  }
}

class MongoConfig
