package me.lightspeed7.mongoFS.tutorial.image

import org.junit.Assert._
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.scalatest.junit._
import scala.collection.mutable.ListBuffer
import org.scalatestplus.play.PlaySpec
import java.io.File
import me.lightspeed7.mongoFS.tutorial.util.MongoConfig
import java.io.FileInputStream
import scala.util.Try
import scala.io.Source

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

  @Test def scaleTigger() {
    println(new File(".").getCanonicalPath)
    val source = MongoConfig.imageFS.upload(new File("test/resources/tigger.jpg"), "image/jpeg")

    val mediumSink = MongoConfig.imageFS.createNew(source.getURL.getFilePath, source.getURL.getMediaType)
    ThumbnailGenerator.createThumbnail(source, mediumSink, 500)

    val thumbSink = MongoConfig.imageFS.createNew(source.getURL.getFilePath, source.getURL.getMediaType)
    ThumbnailGenerator.createThumbnail(source, thumbSink, 150)

  }
}