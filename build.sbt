
lazy val root = (project in file(".")).aggregate(influentJava, influentTransport)

lazy val influentSparkStreaming = (project in file("influent-spark-streaming"))
  .settings(commonSettings: _*)
  .settings(
    name := "influent-spark-streaming",
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-streaming" % "2.1.1" % "provided"
    ),
    run in Compile <<= Defaults.runTask(fullClasspath in Compile, mainClass in(Compile, run), runner in(Compile, run))
  ).dependsOn(influentJava)


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
  .settings(publishSettings: _*)
  .settings(
    name := "influent-transport"
  )

lazy val influentSparkStreamingSample = (project in file("influent-spark-streaming-sample"))
  .settings(commonSettings: _*)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.spark" %% "spark-streaming" % "2.1.1" % "provided"
    ),
    assemblyJarName in assembly := "influent-spark-streaming-sample.jar",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(includeScala = false),
    run in Compile <<= Defaults.runTask(fullClasspath in Compile, mainClass in(Compile, run), runner in(Compile, run))
  ).dependsOn(influentSparkStreaming)

lazy val influentJavaSample = (project in file("influent-java-sample"))
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    name := "influent-java-sample",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7"
    ),
    assemblyJarName in assembly := "influent-java-sample.jar"
  ).dependsOn(influentJava)

lazy val commonSettings = Seq(
  organization := "com.okumin",
  version := "0.2.0",
  scalaVersion := "2.11.8",
  fork in Test := true,
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
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (isSnapshot.value)
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
