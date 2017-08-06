
lazy val root = (project in file(".")).aggregate(influentJava, influentTransport)

lazy val influentJava = (project in file("influent-java"))
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "influent-java",
    libraryDependencies ++= Seq(
      "org.msgpack" % "msgpack-core" % "0.8.13"
    )
  )
  .dependsOn(influentTransport)
  .enablePlugins(AutomateHeaderPlugin)

lazy val influentTransport = (project in file("influent-transport"))
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "influent-transport"
  )
  .enablePlugins(AutomateHeaderPlugin)

lazy val influentJavaSample = (project in file("influent-java-sample"))
  .settings(commonSettings: _*)
  .settings(javaSettings: _*)
  .settings(
    name := "influent-java-sample",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ),
    assemblyJarName in assembly := "influent-java-sample.jar"
  ).dependsOn(influentJava)
  .enablePlugins(AutomateHeaderPlugin)

lazy val commonSettings = Seq(
  organization := "com.okumin",
  version := "0.3.0",
  scalaVersion := "2.11.11",
  fork in Test := true,
  javacOptions in (Compile, doc) ++= Seq("-locale", "en_US"),
  javacOptions in (Compile, compile) ++= Seq("-encoding", "UTF-8"),
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % "1.7.25",
    "org.mockito" % "mockito-core" % "2.8.47" % "test",
    "org.scalatest" % "scalatest_2.11" % "3.0.3" % "test",
    "org.scalacheck" %% "scalacheck" % "1.13.5" % "test"
  ),
  // sbt-header settings
  organizationName := "okumin",
  startYear := Some(2016),
  licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))
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
