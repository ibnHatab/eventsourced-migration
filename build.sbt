organization := "org.eligosource"

name := "akka-persistence-migration"

version := "0.1"

scalaVersion := "2.10.2"

scalacOptions ++= Seq("-feature", "-language:postfixOps")

resolvers += "Akka Snapshots" at "http://repo.akka.io/snapshots/"

libraryDependencies += "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3-SNAPSHOT"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.3-SNAPSHOT" % "test"

libraryDependencies += "org.scalatest" %% "scalatest" % "2.0" % "test"
