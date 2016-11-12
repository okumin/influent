
lazy val root = (project in file(".")).aggregate(influentJava, influentTransport)

lazy val influentJava = (project in file("influent-java"))
  .settings(commonSettings: _*)
  .settings(
    name := "influent-java",
    libraryDependencies ++= Seq(
      "org.msgpack" % "msgpack-core" % "0.8.11"
    )
  ).dependsOn(influentTransport)

lazy val influentTransport = (project in file("influent-transport"))
  .settings(commonSettings: _*)
  .settings(
    name := "influent-transport"
  )

lazy val influentJavaSample = (project in file("influent-java-sample"))
  .settings(commonSettings: _*)
  .settings(
    name := "influent-java-sample",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7"
    )
  ).dependsOn(influentJava)

lazy val commonSettings = Seq(
  organization := "com.okumin",
  version := "0.1.0",
  scalaVersion := "2.11.8",
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % "1.7.21",
    "org.mockito" % "mockito-core" % "2.2.11" % "test",
    "org.scalatest" % "scalatest_2.11" % "3.0.0" % "test",
    "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
  )
)
