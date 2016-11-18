
lazy val root = (project in file(".")).aggregate(influentJava, influentTransport)

lazy val influentJava = (project in file("influent-java"))
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "influent-java",
    libraryDependencies ++= Seq(
      "org.msgpack" % "msgpack-core" % "0.8.11"
    )
  ).dependsOn(influentTransport)

lazy val influentTransport = (project in file("influent-transport"))
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    name := "influent-transport"
  )

lazy val influentJavaSample = (project in file("influent-java-sample"))
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
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
  javacOptions in (Compile, doc) ++= Seq("-locale", "en_US"),
  javacOptions in (Compile, compile) ++= Seq("-encoding", "UTF-8"),
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % "1.7.21",
    "org.mockito" % "mockito-core" % "2.2.11" % "test",
    "org.scalatest" % "scalatest_2.11" % "3.0.0" % "test",
    "org.scalacheck" %% "scalacheck" % "1.13.4" % "test"
  )
)

lazy val javaSettings = Seq(
  crossPaths := false,
  autoScalaLibrary := false
)

lazy val publishSettings = Seq(
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo <<= version { (v: String) =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := {
    <url>https://github.com/okumin/influent</url>
    <licenses>
      <license>
        <name>Apache 2 License</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.html</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:okumin/influent.git</url>
      <connection>scm:git:git@github.com:okumin/influent.git</connection>
    </scm>
    <developers>
      <developer>
        <id>okumin</id>
        <name>okumin</name>
        <url>http://okumin.com/</url>
      </developer>
    </developers>
  }
)
