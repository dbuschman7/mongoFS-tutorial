package me.lightspeed7.mongoFS.tutorial

import akka.pattern.CircuitBreaker
import scala.concurrent.duration._
import play.libs.Akka
import play.api.Logger

trait MongoCircuitBreaker {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  lazy val breaker =
    new CircuitBreaker(Akka.system.scheduler,
      maxFailures = 3,
      callTimeout = 10.seconds,
      resetTimeout = 10.seconds) //
      .onOpen(notifyMeOnOpen)
      .onClose(notifyMeOnClose)

  def notifyMeOnOpen(): Unit =
    Logger.warn("CircuitBreaker is now open, attempt reset in 10 seconds")

  def notifyMeOnClose(): Unit =
    Logger.warn("CircuitBreaker is now closed")

}
