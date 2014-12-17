package controllers

import play.api._
import play.api.mvc._
import play.api.libs.iteratee.Concurrent
import play.libs.Akka
import me.lightspeed7.mongoFS.tutorial._
import play.api.libs.iteratee.Iteratee
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsValue
import akka.actor.ActorRef
import java.util.UUID
import akka.actor.ActorSelection
import play.api.libs.EventSource
import play.api.libs.iteratee.Enumeratee
import akka.actor.Props

object Application extends Controller {


  def index: Action[AnyContent] = Action {
    val uuid = UUID.randomUUID().toString()
    Ok(views.html.index(uuid))
  }

  def sse(uuid: String) = Action { req =>
    println(req.remoteAddress + " - SSE connected")
    
    val (out, channel) = Concurrent.broadcast[JsValue]
    
    // create unique actor for each uuid
    println(s"Creating Listener - ${uuid}")
    Akka.system.actorOf(Props(classOf[Listener], uuid, channel))
     
    def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
      Enumeratee.onIterateeDone { () => println(addr + " - SSE disconnected") }

    Ok.feed(out
      &> Concurrent.buffer(300)
      &> connDeathWatch(req.remoteAddress)
      &> EventSource()).as("text/event-stream")

  }
}