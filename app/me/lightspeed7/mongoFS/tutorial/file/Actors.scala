package me.lightspeed7.mongoFS.tutorial.file

import java.util.concurrent.atomic.AtomicLong

import scala.collection.JavaConversions.asScalaIterator

import org.mongodb.Document

import akka.actor.{ Actor, ActorRef, ActorSystem, PoisonPill, Props, actorRef2Scala }
import akka.event.{ ActorEventBus, LookupClassification }
import me.lightspeed7.mongoFS.tutorial.file.UiFile.fromMongoDB
import me.lightspeed7.mongoFS.tutorial.image.{ Payload, StatsData }
import me.lightspeed7.mongofs.MongoFile
import play.Play
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.{ JsValue, Json }

object Actors {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  private val conf = Play.application().configuration()

  private[mongoFS] val system = ActorSystem("File")

  //
  // Internal Actor State Classes
  // ///////////////////////////////
  case class Start(out: Concurrent.Channel[JsValue])
  case class Load(listener: ActorRef)
  case class MessageEvent(channel: String, payload: Payload)

  val Tick = "tick" // scheduler tick

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
    .actorOf(Props(classOf[Statistics], "statistics")) //

  class Statistics(name: String) extends Actor {

    def receive = {
      case Tick => {
        val payload = Payload(Json.toJson(Statistics.generate()), "statistics")
        EventBus.publish(MessageEvent("payload", payload))
      }
    }

    override def preStart() {
      super.preStart()
      println(s"Statistics(Files) Ready - ${name}, path - ${self.path}")
    }

    override def postStop() {
      super.postStop()
      println(s"Statistics(Files) ShutDown - ${name}, path - ${self.path}")
    }
  }

  private object Statistics {

    private var totalBytes: AtomicLong = new AtomicLong(0)
    private var totalStorage: AtomicLong = new AtomicLong(0)
    private var totalFiles: AtomicLong = new AtomicLong(0)

    def generate(): StatsData = {
      FileService.generateStats()
    }

    def accumulate(m: MongoFile) = {
      totalFiles.addAndGet(1)
      totalBytes.addAndGet(m.getLength)
      totalStorage.addAndGet(m.getStorageLength)
    }
  }

  //
  // Mongo File Updater
  // ////////////////////////////
  val updater: ActorRef = system //
    .actorOf(Props(classOf[Updater], "updater")) //

  class Updater(name: String) extends Actor {
    def receive = {
      case m: MongoFile => {
        val current: UiFile = m // implicit conversion of - Image.fromMongoDB(obj)
        val payload = Payload(Json.toJson(current), "file") // send only to one UI
        EventBus.publish(MessageEvent("payload", payload))

        statistics ! Tick
      }
    }
  }

  //
  // Files Loader - ephemeral
  // /////////////////////////////
  class Loader(name: String) extends Actor {
    def receive = {
      case Load(listener) => {
        println(s"Loader Starting - ${name}, reporting to ${listener.path}")

        import scala.collection.JavaConversions._
        val list = FileService.list(new Document("ts", -1))

        list.foreach { obj =>

          val current: UiFile = obj // implicit conversion of - Image.fromMongoDB(obj)
          listener ! Payload(Json.toJson(current), "file") // send only to one UI
        }
        statistics ! Tick

        self ! PoisonPill
      }
    }
  }

}
