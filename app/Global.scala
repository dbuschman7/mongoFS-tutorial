import java.io.File
import org.apache.commons.io.FileUtils
import play.api._
import play.libs.Akka
import akka.actor.Props
import org.slf4j.LoggerFactory
import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import akka.actor.Actor
import play.api.libs.iteratee.Concurrent

import me.lightspeed7.mongoFS.tutorial._

object Global extends GlobalSettings {

  override def onStart(app: Application) {

    val dbUri = Option(System.getenv("MONGOHQ_URL")).getOrElse("mongodb://localhost:27017/playground")
    MongoConfig.init(dbUri)

    println(MongoConfig.images.toString())
    println(MongoConfig.imageFS.toString())

   

  }
  
  
  
  override def onStop(app: Application) {
    //
  }

}