package me.lightspeed7.mongoFS.tutorial

import java.util.Date

import scala.concurrent.duration.DurationInt

import akka.actor.{ Actor, ActorRef, ActorSystem, Props, actorRef2Scala }
import akka.event.{ ActorEventBus, LookupClassification }
import akka.routing.RoundRobinPool
import me.lightspeed7.mongoFS.tutorial.image.ImageService
import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import me.lightspeed7.mongofs.MongoFile
import me.lightspeed7.mongofs.url.MongoFileUrl
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{ JsString, JsValue, Json }

object `package` {

  val thumb = "thumbnail"
  //
  // Data Classes
  // ///////////////////////////////
  case class Start(out: Concurrent.Channel[JsValue])
  case class Tick(foo: String = "foo")

  case class ServerTime(data: String = new Date().toString(), target: String = "serverTime")
  implicit val serverTimeWrites = Json.writes[ServerTime]

  case class Payload(data: JsValue, target: String)
  implicit val payloadWrites = Json.writes[Payload]

  case class CreateImage(file: MongoFile)
  case class ThumbnailRequest(file: MongoFile, side: Int)
  case class UpdateImage(file: MongoFile, url: MongoFileUrl, side: Int)

  case class MessageEvent(val channel: String, val payload: Payload)

  //
  // Router Pool
  // ////////////////////////////////
  val system = ActorSystem("Test")
  val thumbnailers = system.actorOf(Props(classOf[Thumbnailer], "thumbnailers").withRouter(new RoundRobinPool(3)))

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
      case t: Tick    => out.push(Json.toJson(new ServerTime))
      case p: Payload => out.push(Json.toJson(p)) //Pushing messages to Websocket
    }

    override def preStart() {
      super.preStart()
      EventBus.subscribe(self, thumb)
      println("Listener Ready")

    }

    override def postStop() {
      println("Listener - Shutting Down")
      if (cancellable != null) {
        cancellable.cancel
      }
      EventBus.unsubscribe(self)
      super.postStop()
    }
  }

  //
  // Thumbnailer Actor
  // /////////////////////////////
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
        val writer = MongoConfig.imageFS.createNew(file.getFilename, mediaType)
        val thumbnail = ImageService.createThumbnail(file, writer, side)

        thumbnailers ! UpdateImage(file, thumbnail.getURL, side)
      }
      case UpdateImage(file, url, side) => {

        determineUpdate(side)(file.getId(), url)

        if (side == mediumSide) {
          thumbnailers ! ThumbnailRequest(file, thumbSide)
        } else {
          // update the clients with a new thumbnail
          val payload = Payload(JsString(file.getId().toString()), thumb)
          EventBus.publish(MessageEvent(thumb, payload))
        }
      }

    }
  }

}
