package controllers

import java.util.UUID
import scala.concurrent.Future
import akka.actor.{ Props, actorRef2Scala }
import play.api.libs.EventSource
import play.api.libs.iteratee.{ Concurrent, Enumeratee }
import play.api.libs.json.JsValue
import play.api.mvc.{ Action, AnyContent, Controller }
import play.libs.Akka
import akka.actor.PoisonPill
import org.webjars.WebJarAssetLocator

object Application extends Controller {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def index: Action[AnyContent] = Action {
    val uuid = UUID.randomUUID().toString()
    Ok(views.html.index(uuid))
  }

  def images: Action[AnyContent] = Action {
    val uuid = UUID.randomUUID().toString()
    Ok(views.html.images(uuid))
  }

  def files: Action[AnyContent] = Action {
    val uuid = UUID.randomUUID().toString()
    Ok(views.html.files(uuid))
  }

  //
}
