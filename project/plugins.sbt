
// The Typesafe/Sonatype repositories
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"

scalaVersion := "2.10.4"

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.3.7")

addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.5.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.4")


// web plugins

// addSbtPlugin("com.typesafe.sbt" % "sbt-coffeescript" % "1.0.0")

// addSbtPlugin("com.typesafe.sbt" % "sbt-less" % "1.0.0")

// addSbtPlugin("com.typesafe.sbt" % "sbt-jshint" % "1.0.1")

//addSbtPlugin("com.typesafe.sbt" % "sbt-rjs" % "1.0.1")

//addSbtPlugin("com.typesafe.sbt" % "sbt-digest" % "1.0.0")

// addSbtPlugin("com.typesafe.sbt" % "sbt-mocha" % "1.0.0")
