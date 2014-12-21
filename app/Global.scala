// package default

import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import play.api.{ Application, GlobalSettings }

object Global extends GlobalSettings {

  override def onStart(app: Application) {

    val dbUri = Option(System.getenv("MONGOHQ_URL")).getOrElse("mongodb://localhost:27017/playground")
    MongoConfig.setHostUrl(dbUri)

    println(MongoConfig.images.toString())
    println(MongoConfig.imageFS.toString())

  }

  override def onStop(app: Application) {
    //
  }

}
