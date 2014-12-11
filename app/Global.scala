import java.io.File
import org.apache.commons.io.FileUtils
import play.api._
import play.libs.Akka
import akka.actor.Props
//import actors.LogEntryProducerActor
//import actors.ElasticsearchActor
//import actors.UserChannelsActor
//import actors.StatisticsActor
//import actors.ServerTickActor
//import actors.TailableCursorActor
import utils.MongoConfig

object Global extends GlobalSettings {

  override def onStart(app: Application) {

    val dbUri = Option(System.getenv("MONGOHQ_URL")).getOrElse("mongodb://localhost:27017/playground")
    MongoConfig.init(dbUri)

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