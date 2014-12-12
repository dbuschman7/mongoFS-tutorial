import java.io.File
import org.apache.commons.io.FileUtils
import play.api._
import play.libs.Akka
import akka.actor.Props
import utils.MongoConfig
import org.slf4j.LoggerFactory

object Global extends GlobalSettings {

  override def onStart(app: Application) {

    val dbUri = Option(System.getenv("MONGOHQ_URL")).getOrElse("mongodb://localhost:27017/playground")
    MongoConfig.init(dbUri)

//    val logger: org.slf4j.Logger = LoggerFactory.getLogger(classOf[MongoConfig]);
    println(MongoConfig.images.toString())
    println(MongoConfig.medium.toString())
    println(MongoConfig.thumbs.toString())

    // bring up akka actors
    //    Akka.system.actorOf(Props[TailableCursorActor], "search")
    //    Akka.system.actorOf(Props[LogEntryProducerActor], "logEntryProducer")
    //    Akka.system.actorOf(Props[UserChannelsActor], "channels")
    //    Akka.system.actorOf(Props[StatisticsActor], "statistics")
    //    Akka.system.actorOf(Props[ServerTickActor], "serverTick")

  }

  override def onStop(app: Application) {
    //
  }

}