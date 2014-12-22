package controllers

import java.util.UUID

import akka.actor.Props
import me.lightspeed7.mongoFS.tutorial.{ Listener, Loader, Load }
import play.api.libs.EventSource
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{ Concurrent, Enumeratee }
import play.api.libs.json.JsValue
import play.api.mvc.{ Action, AnyContent, Controller }
import play.libs.Akka
import scala.concurrent._

object Application extends Controller {

  def index: Action[AnyContent] = Action {
    val uuid = UUID.randomUUID().toString()
    Ok(views.html.index(uuid))
  }

  def sse(uuid: String) = Action.async { req =>

    Future {
      println(req.remoteAddress + " - SSE connected")
      val (out, channel) = Concurrent.broadcast[JsValue]

      // create unique actor for each uuid
      println(s"Creating Listener - ${uuid}")
      val listener = Akka.system.actorOf(Props(classOf[Listener], uuid, channel))

      def connDeathWatch(addr: String): Enumeratee[JsValue, JsValue] =
        Enumeratee.onIterateeDone { () => println(addr + " - SSE disconnected") }

      val result =
        Ok.feed(out
          &> Concurrent.buffer(300)
          &> connDeathWatch(req.remoteAddress)
          &> EventSource()).as("text/event-stream")

      Akka.system.actorOf(Props(classOf[Loader], "Loader" + uuid)) ! Load(listener)
      result
    }

  }
  //

}
