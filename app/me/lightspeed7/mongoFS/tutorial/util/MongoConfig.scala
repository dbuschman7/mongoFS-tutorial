package utils

import me.lightspeed7.mongofs.MongoFileStore
import com.mongodb.DB
import com.mongodb.MongoClientURI
import com.mongodb.MongoClient
import me.lightspeed7.mongofs.MongoFileStoreConfig
import me.lightspeed7.mongofs.util.ChunkSize
import com.mongodb.WriteConcern

object MongoConfig {

  var db: DB = _
  var store: MongoFileStore = _

  def init(hostUrl: String) = {

    println("Mongo Connection - standing up ...")
    val uri = new MongoClientURI(hostUrl)
    val client = new MongoClient(uri);
    db = client.getDB(uri.getDatabase());
    println("Mongo Connection - ready!")

    val config = MongoFileStoreConfig.builder().bucket("test") //
      .enableCompression(true) //
      .asyncDeletes(true) //
      .chunkSize(ChunkSize.small_64K) //
      .writeConcern(WriteConcern.ACKNOWLEDGED) //
      .build();

    store = new MongoFileStore(db, config)
  }

}

