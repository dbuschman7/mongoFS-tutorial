package me.lightspeed7.mongoFS.tutorial.image

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import scala.collection.JavaConversions.asScalaIterator
import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import com.mongodb.BasicDBObject
import akka.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala }
import akka.event.{ ActorEventBus, LookupClassification }
import akka.routing.RoundRobinPool
import me.lightspeed7.mongoFS.tutorial.image.Image.fromMongoDB
import me.lightspeed7.mongoFS.tutorial.image.UiImage.fromImage
import me.lightspeed7.mongofs.MongoFile
import me.lightspeed7.mongofs.url.MongoFileUrl
import play.Play
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{ JsValue, Json }
import org.mongodb.Document

object Actors {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  private val conf = Play.application().configuration()

  private val statPool = conf.getInt("akka.statistics.pool") // actors
  private val statUpdateFreq = conf.getLong("akka.statistics.updateFreq") // seconds

  private val thumbPool = conf.getInt("akka.thumbnailers.pool") // actors
  private val mediaType = conf.getString("akka.thumbnailers.mediaType") // "image/jpeg"
  private val mediumSide = conf.getInt("akka.thumbnailers.mediumSide") // pixels
  private val thumbSide = conf.getInt("akka.thumbnailers.thumbSide") // pixels

  private[mongoFS] val system = ActorSystem("Image")

  //
  // Internal Actor State Classes
  // ///////////////////////////////
  case class Start(out: Concurrent.Channel[JsValue])
  case class Load(listener: ActorRef)
  case class CreateImage(file: MongoFile)
  case class ThumbnailRequest(file: MongoFile, side: Int)
  case class UpdateImage(file: MongoFile, url: MongoFileUrl, side: Int)
  case class MessageEvent(channel: String, payload: Payload)

  private val Tick = "tick" // scheduler tick

  //
  // Message Bus
  // ////////////////////////////////
  private val EventBus = new LookupEventBus

  class LookupEventBus extends ActorEventBus with LookupClassification {
    type Event = MessageEvent
    type Classifier = String

    protected def mapSize(): Int = 4

    protected def classify(event: Event): Classifier = {
      event.channel
    }

    protected def publish(event: Event, subscriber: ActorRef): Unit = {
      subscriber ! event.payload
    }
  }

  //
  // Socket Listeners - ephemeral ( session )
  // ///////////////////////////////
  class Listener(name: String, out: Concurrent.Channel[JsValue]) extends Actor {

    def receive = {
      case p: Payload => {
        println(s"Payload to client ${p}")
        out.push(Json.toJson(p)) // Pushing messages to Channel
      }
    }

    override def preStart() {
      super.preStart()
      EventBus.subscribe(self, "payload")
      println(s"Listener Ready - ${name}, path - ${self.path}")

    }

    override def postStop() {
      EventBus.unsubscribe(self, "payload")
      super.postStop()
      println(s"Listener ShutDown - ${name}, path - ${self.path}")
    }
  }

  //
  // Statistics
  // //////////////////////////////////
  val statistics: ActorRef = system //
    .actorOf(Props(classOf[Statistics], "statistics"))

  // Server Tick to cause Statistic events
  private val duration = FiniteDuration(statUpdateFreq, TimeUnit.SECONDS)

  private val cancellable: akka.actor.Cancellable = //
    system.scheduler.schedule(duration, duration, statistics, Tick)

  class Statistics(name: String) extends Actor {

    def receive = {
      case Tick => {
        //       println("Statistics - tick")
        val stats: StatsData = ImageService.generateStats()
        val payload = Payload(Json.toJson(stats), "statistics")
        EventBus.publish(MessageEvent("payload", payload))
      }
    }

    override def preStart() {
      super.preStart()
      println(s"Statistics Ready - ${name}, path - ${self.path}")
    }

    override def postStop() {
      if (cancellable != null) {
        cancellable.cancel
      }
      super.postStop()
      println(s"Statistics ShutDown - ${name}, path - ${self.path}")
    }
  }

  //
  // Image Loader - ephemeral
  // /////////////////////////////
  class Loader(name: String) extends Actor {
    def receive = {
      case Load(listener) => {
        println(s"Loader Starting - ${name}, reporting to ${listener.path}")

        import scala.collection.JavaConversions._
        val list = ImageService.list(new BasicDBObject("ts", -1))

        list.foreach { obj =>
          val current: Image = obj // implicit conversion of - Image.fromMongoDB(obj)
          println(s"Image : $current")

          val currentUI: Try[UiImage] = current // implicit conversion with DB access
          currentUI.map { ui =>
            listener ! Payload(Json.toJson(ui), "image") // send only to one UI
          }
        }

        println(s"Loader Shutting Down - ${name}, reporting to ${listener.path}")
        self ! PoisonPill
      }
    }
  }

  //
  // Thumbnailer Router Pool
  // ////////////////////////////////
  def thumbnailers: ActorRef = system //
    .actorOf(Props(classOf[Thumbnailer], "thumbnailers") //
      .withDispatcher("akka.thumbnailers.pinned-dispatcher")
      .withRouter(new RoundRobinPool(thumbPool)))

  class Thumbnailer(name: String) extends Actor {

    def receive = {

      case CreateImage(file) => {
        for {
          id <- ImageService.insert(file)
          image = ImageService.find(id.toString())
        } yield {
          thumbnailers ! ThumbnailRequest(file, mediumSide)
        }
      }

      case ThumbnailRequest(file, side) => {
        val filename = new File(file.getFilename).getPath
        val idx = filename.lastIndexOf('.')
        val thumb = filename.substring(0, idx) + ".png"

        ImageService.createNew(thumb, mediaType).map { writer =>
          val thumbnail = ThumbnailService.createThumbnail(file, writer, side)
          thumbnailers ! UpdateImage(file, thumbnail.getURL, side)
        }
      }

      case UpdateImage(file, url, side) =>
        {
          determineUpdate(side)(file.getId(), url)

          if (side == mediumSide) {
            thumbnailers ! ThumbnailRequest(file, thumbSide)
          } else {
            // update the clients with a new thumbnail
            val image = ImageService.find(file.getId().toString())
            image.map(img => {
              val currentUI: Try[UiImage] = img // implicit conversion with DB access
              currentUI.map { image =>
                val payload = Payload(Json.toJson(image), "image")
                EventBus.publish(MessageEvent("payload", payload))
              }

            })
          }
        }

        def determineUpdate(side: Int) = {
          if (side == mediumSide) {
            ImageService.updateMediumUrl _
          } else {
            ImageService.updateThumbUrl _
          }
        }

    }
  }

}
