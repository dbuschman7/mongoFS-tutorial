package me.lightspeed7.mongoFS.tutorial

import java.io.File

import scala.concurrent.duration.DurationInt

import com.mongodb.BasicDBObject

import akka.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala }
import akka.event.{ ActorEventBus, LookupClassification }
import akka.routing.RoundRobinPool
import me.lightspeed7.mongoFS.tutorial.image.{ ImageService, Payload, ServerTime, ThumbnailService, UiImage }
import me.lightspeed7.mongoFS.tutorial.image.Image
import me.lightspeed7.mongoFS.tutorial.image.Image.fromMongoDB
import me.lightspeed7.mongoFS.tutorial.image.UiImage.fromImage
import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import me.lightspeed7.mongofs.MongoFile
import me.lightspeed7.mongofs.url.MongoFileUrl
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{ JsValue, Json }

object Actors {

  private[tutorial] val event = "image"
  private[tutorial] val statPool = 5 // actors
  private[tutorial] val thumbPool = 5 // actors
  private[tutorial] val statUpdateFreq = 5 // seconds

  private val system = ActorSystem("Image")

  //
  // Internal Actor State Classes
  // ///////////////////////////////
  case class Start(out: Concurrent.Channel[JsValue])
  case class Tick(foo: String = "foo")
  case class Load(listener: ActorRef)
  case class CreateImage(file: MongoFile)
  case class ThumbnailRequest(file: MongoFile, side: Int)
  case class UpdateImage(file: MongoFile, url: MongoFileUrl, side: Int)
  case class MessageEvent(channel: String, payload: Payload)

  //
  // Message Bus
  // ////////////////////////////////
  val EventBus = new LookupEventBus

  class LookupEventBus extends ActorEventBus with LookupClassification {
    type Event = MessageEvent
    type Classifier = String

    protected def mapSize(): Int = 10

    protected def classify(event: Event): Classifier = {
      event.channel
    }

    protected def publish(event: Event, subscriber: ActorRef): Unit = {
      subscriber ! event.payload
    }
  }

  //
  // Socket Listeners
  // ///////////////////////////////
  class Listener(name: String, out: Concurrent.Channel[JsValue]) extends Actor {

    val cancellable: akka.actor.Cancellable = //
      context.system.scheduler.schedule(1 second, 1 second, self, new Tick)

    def receive = {
      case t: Tick => out.push(Json.toJson(new ServerTime))
      case p: Payload => {
        println(s"Payload to client ${p}")
        out.push(Json.toJson(p)) //Pushing messages to Channel
      }
    }

    override def preStart() {
      super.preStart()
      EventBus.subscribe(self, event)
      println(s"Listener Ready - ${name}, path - ${self.path}")

    }

    override def postStop() {
      if (cancellable != null) {
        cancellable.cancel
      }
      EventBus.unsubscribe(self)
      super.postStop()
      println(s"Listener ShutDown - ${name}, path - ${self.path}")
    }
  }

  //
  // Statistics
  // //////////////////////////////////
  def statisticsRouter: ActorRef = system.actorOf(Props(classOf[Statistics], "statistics").withRouter(new RoundRobinPool(statPool)))

  class Statistics(name: String) extends Actor {

    val cancellable: akka.actor.Cancellable = //
      context.system.scheduler.schedule(5 seconds, 5 seconds, statisticsRouter, new Tick)

    def receive = {
      case img: Image => {
        println("Image processed")
        statisticsRouter ! img.imageUrl
        statisticsRouter ! img.mediumUrl
        statisticsRouter ! img.thumbUrl
      }
      case url: String => {
        println("Url processed")
        statisticsRouter ! Image.getMongoFile(url)
      }
      case m: MongoFile => {
        println("MongoFile processed")
        Statistics.totalBytes += m.getLength
        Statistics.totalFiles += 1
        Statistics.totalStorage += m.getStorageLength
      }
    }
  }

  object Statistics {

    private var totalBytes: Long = 0
    private var totalStorage: Long = 0
    private var totalFiles: Long = 0

  }

  //
  // Image Loader
  // /////////////////////////////
  class Loader(name: String) extends Actor {
    def receive = {
      case Load(listener) => {
        println(s"Loader Starting - ${name}, reporting to ${listener.path}")

        // FIX ME - blocking
        val iter = ImageService.list(new BasicDBObject("ts", -1))
        while (iter.hasNext()) {
          val current: Image = iter.next();
          val currentUI: UiImage = current
          println(s"Found image - ${currentUI.id}")
          listener ! Payload(Json.toJson(currentUI), event)
          statisticsRouter ! current
        }

        self ! PoisonPill
      }
    }

  }

  //
  // Thumbnailer Router Pool
  // ////////////////////////////////
  def thumbnailers: ActorRef = system.actorOf(Props(classOf[Thumbnailer], "thumbnailers").withRouter(new RoundRobinPool(thumbPool)))

  class Thumbnailer(name: String) extends Actor {
    val mediaType = "image/png"
    val mediumSide = 500 // pixels
    val thumbSide = 150 // pixels

    def determineUpdate(side: Int) = {
      if (side == mediumSide) {
        ImageService.updateMediumUrl _
      } else {
        ImageService.updateThumbUrl _
      }
    }

    def receive = {

      case CreateImage(file) => {
        ImageService.insert(file)
        thumbnailers ! ThumbnailRequest(file, mediumSide)
      }

      case ThumbnailRequest(file, side) => {
        val filename = new File(file.getFilename).getPath
        val idx = filename.lastIndexOf('.')
        val thumb = filename.substring(0, idx) + ".png"

        val writer = MongoConfig.imageFS.createNew(thumb, mediaType)
        val thumbnail = ThumbnailService.createThumbnail(file, writer, side)

        thumbnailers ! UpdateImage(file, thumbnail.getURL, side)
      }
      case UpdateImage(file, url, side) => {

        determineUpdate(side)(file.getId(), url)

        if (side == mediumSide) {
          thumbnailers ! ThumbnailRequest(file, thumbSide)
        } else {
          // update the clients with a new thumbnail
          val image = ImageService.find(file.getId().toString())
          image.map(img => {
            val currentUI: UiImage = img
            val payload = Payload(Json.toJson(currentUI), event)
            // println(s"Publishing Event - ${payload}")
            EventBus.publish(MessageEvent(event, payload))

          })
        }
      }

    }
  }

}
