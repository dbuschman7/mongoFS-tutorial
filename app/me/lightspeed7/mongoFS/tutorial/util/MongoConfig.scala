package utils

import me.lightspeed7.mongofs.MongoFileStore
import com.mongodb.DB
import com.mongodb.MongoClientURI
import com.mongodb.MongoClient
import me.lightspeed7.mongofs.MongoFileStoreConfig
import me.lightspeed7.mongofs.util.ChunkSize
import com.mongodb.WriteConcern
import me.lightspeed7.mongofs.crypto.BasicCrypto

object MongoConfig {

  var db: DB = _
  lazy val images: MongoFileStore = buildStore("images", ChunkSize.medium_128K)
  lazy val medium: MongoFileStore = buildStore("medium", ChunkSize.small_64K)
  lazy val thumbs: MongoFileStore = buildStore("thumbs", ChunkSize.small_32K)

  def init(hostUrl: String) = {

    println("Mongo Connection - standing up ...")
    val uri = new MongoClientURI(hostUrl)
    val client = new MongoClient(uri);
    db = client.getDB(uri.getDatabase());
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
}

class MongoConfig
