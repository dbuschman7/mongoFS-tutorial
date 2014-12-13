import java.io.File
import org.apache.commons.io.FileUtils
import play.api._
import play.libs.Akka
import akka.actor.Props
import org.slf4j.LoggerFactory
import me.lightspeed7.mongoFS.tutorial.util.MongoConfig

object Global extends GlobalSettings {

  override def onStart(app: Application) {

    val dbUri = Option(System.getenv("MONGOHQ_URL")).getOrElse("mongodb://localhost:27017/playground")
    MongoConfig.init(dbUri)

    println(MongoConfig.images.toString())
    println(MongoConfig.imageFS.toString())

    // bring up akka actors
        Akka.system.actorOf(Props[MediumThumbnail], "medium")
        Akka.system.actorOf(Props[SmallThumbnail], "small")
    //    Akka.system.actorOf(Props[LogEntryProducerActor], "logEntryProducer")
        Akka.system.actorOf(Props[UserChannelsActor], "channels")
    //    Akka.system.actorOf(Props[StatisticsActor], "statistics")
    //    Akka.system.actorOf(Props[ServerTickActor], "serverTick")

  }

  override def onStop(app: Application) {
    //
  }

}