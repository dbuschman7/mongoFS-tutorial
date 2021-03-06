// package default

import me.lightspeed7.mongoFS.tutorial.file.FileService
import me.lightspeed7.mongoFS.tutorial.image.ImageService
import play.api.Application
import play.api.GlobalSettings

object Global extends GlobalSettings {

  override def onStart(app: Application) {

    val dbUri = Option(System.getenv("MONGOHQ_URL")).getOrElse("mongodb://localhost:27017/mongoFS")

    ImageService.setHostUrl(dbUri)
    ImageService.dumpConfig

    FileService.setHostUrl(dbUri)
    FileService.dumpConfig
  }

  override def onStop(app: Application) {
    //
  }

}
