package me.lightspeed7.mongoFS.tutorial

import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import akka.actor.ActorSystem

object TimeoutFuture {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  def apply[A](timeout: FiniteDuration)(block: => A)(implicit system: ActorSystem): Future[A] = {
    val promise = Promise[A]()

    system.scheduler.scheduleOnce(timeout) {
      promise tryFailure new java.util.concurrent.TimeoutException
    }

    Future {
      try {
        promise success block
      } catch {
        case e: Throwable => promise failure e
      }
    }

    promise.future
  }
}
