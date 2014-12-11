import sbt._
import Keys._
import sbt.Keys._
import java.io.PrintWriter
import java.io.File
import play.Play.autoImport._
//import PlayKeys._
import sys.process.stringSeqToProcess

//import com.typesafe.sbt.SbtNativePackager._
//import NativePackagerKeys._

object ApplicationBuild extends Build {

  val appName = "tutorial"

  val branch = "git rev-parse --abbrev-ref HEAD".!!.trim
  val commit = "git rev-parse --short HEAD".!!.trim
  val buildTime = (new java.text.SimpleDateFormat("yyyyMMdd-HHmmss")).format(new java.util.Date())

  val major = 1
  val minor = 1
  val patch = 0
  val appVersion = s"$major.$minor.$patch-$commit"

  scalaVersion := "2.11.1"
  
//  val theScalaVersion = scala.util.Properties.versionString.substring(8)

  println()
  println(s"App Name      => ${appName}")
  println(s"App Version   => ${appVersion}")
  println(s"Git Branch    => ${branch}")
  println(s"Git Commit    => ${commit}")
  println(s"Scala Version => 2.11.1")
  println()

  val scalaBuildOptions = Seq("-unchecked", "-feature", "-language:reflectiveCalls", "-deprecation",
    "-language:implicitConversions", "-language:postfixOps", "-language:dynamics", "-language:higherKinds",
    "-language:existentials", "-language:experimental.macros", "-Xmax-classfile-name", "140")

  val appDependencies = Seq(
    "commons-io" % "commons-io" % "2.4",
    "org.webjars" %% "webjars-play" % "2.3.0" withSources (),
    "org.webjars" % "angularjs" % "1.2.23",
    "org.webjars" % "bootstrap" % "3.2.0",
    //    "org.webjars" % "d3js" % "3.4.11",
    "org.mongodb" % "mongo-java-driver" % "2.12.4",
    "me.lightspeed7" % "mongoFS" % "0.9.0",
    "com.github.athieriot" %% "specs2-embedmongo" % "0.7.0"
    )

  val root = Project("tutorial", file("."))
    .enablePlugins(play.PlayScala)
    .settings(scalacOptions ++= scalaBuildOptions)
    .settings(
      version := appVersion,
      libraryDependencies ++= appDependencies)

  println("Done")

}
