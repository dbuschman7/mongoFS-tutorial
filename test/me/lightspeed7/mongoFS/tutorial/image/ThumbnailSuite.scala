package me.lightspeed7.mongoFS.tutorial.image

import akka.actor.Props
import org.junit._
import org.junit.Assert._
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import me.lightspeed7.mongoFS.tutorial._
import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import org.scalatest.junit._
import org.scalatestplus.play.PlaySpec
import play.libs.Akka
import scala.collection.mutable.ListBuffer
import scala.util.Try
import scala.io.Source
import akka.actor.ActorSystem

class ThumbnailSuite extends PlaySpec with AssertionsForJUnit {

  var sb: StringBuilder = _
  var lb: ListBuffer[String] = _

  @Before def initialize() {
    sb = new StringBuilder("ScalaTest is ")
    lb = new ListBuffer[String]

    MongoConfig.init("mongodb://localhost:27017/playground")

  }

  @Test def verifyEasy() { // Uses JUnit-style assertions
    sb.append("easy!")
    assertEquals("ScalaTest is easy!", sb.toString)
    assertTrue(lb.isEmpty)
    lb += "sweet"
    try {
      "verbose".charAt(-1)
      fail()
    } catch {
      case e: StringIndexOutOfBoundsException => // Expected
    }
  }

  @Test def verifyFun() { // Uses ScalaTest assertions
    sb.append("fun!")
    assert(sb.toString === "ScalaTest is fun!")
    assert(lb.isEmpty)
    lb += "sweeter"
    intercept[StringIndexOutOfBoundsException] {
      "concise".charAt(-1)
    }
  }

  @Ignore @Test def scaleTigger() {
    println(new File(".").getCanonicalPath)
    val source = MongoConfig.imageFS.upload(new File("test/resources/tigger.jpg"), "image/jpeg")

    val mediumSink = MongoConfig.imageFS.createNew(source.getURL.getFilePath, source.getURL.getMediaType)
    ImageService.createThumbnail(source, mediumSink, 500)

    val thumbSink = MongoConfig.imageFS.createNew(source.getURL.getFilePath, source.getURL.getMediaType)
    ImageService.createThumbnail(source, thumbSink, 150)
  }

  @Test def uploadTrigger() {

    println(new File(".").getCanonicalPath)
    val source = MongoConfig.imageFS.upload(new File("test/resources/tigger.jpg"), "image/jpeg")

    val system = ActorSystem("Test")

    val ref = system.actorOf(Props(classOf[Thumbnailer], "thumber"))
    ref ! CreateImage(source)

    assert(ref != null)

    system.shutdown()
  }
}