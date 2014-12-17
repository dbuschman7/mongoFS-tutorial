package me.lightspeed7.mongoFS.tutorial

import play.api.libs.iteratee.Concurrent
import akka.actor.Actor
import play.api.libs.json.JsValue
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import java.util.Date
import play.api.libs.json._

object `package` {

  case class Start(out: Concurrent.Channel[JsValue])
  case class Tick(foo:String = "foo")

  case class ServerTime(data: String = new Date().toString(), target: String = "serverTime")
  implicit val serverTimeWrites = Json.writes[ServerTime]

  case class Payload(data: JsValue, target: String = "thumbnail")
  implicit val payloadWrites = Json.writes[Payload]

  class Listener(name: String, out: Concurrent.Channel[JsValue]) extends Actor {

    val cancellable: akka.actor.Cancellable = context.system.scheduler.schedule(1 second, 1 second, self, new Tick)

    def receive = {
      case t: Tick    => out.push(Json.toJson(new ServerTime)) 
      case p: Payload => out.push(Json.toJson(p)) //Pushing messages to Websocket
    }

    override def preStart() {
      super.preStart()
      println("Listener Ready")
    }

    override def postStop() {
      println("Listener - Shutting Down")
      if (cancellable != null) {
        cancellable.cancel
      }
      super.postStop()
    }
  }

}