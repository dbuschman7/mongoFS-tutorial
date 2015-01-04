// package default

import me.lightspeed7.mongoFS.tutorial.image.ImageService
import play.api.{ Application, GlobalSettings }

object Global extends GlobalSettings {

  override def onStart(app: Application) {

    val dbUri = Option(System.getenv("MONGOHQ_URL")).getOrElse("mongodb://localhost:27017/playground")

    ImageService.setHostUrl(dbUri)
    ImageService.dumpConfig
  }

  override def onStop(app: Application) {
    //
  }

}
