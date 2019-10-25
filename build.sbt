name := "telegram-email-gate-bot"

version := "0.1"

scalaVersion := "2.12.10"

val sttpVersion = "1.6.4"
val circleVersion = "0.11.1"

val coreDependencies = Seq(
  "com.typesafe" % "config" % "1.2.1"
)

val sttpDependencies = Seq(
  "com.softwaremill.sttp" %% "core" % sttpVersion,
  "com.softwaremill.sttp" %% "circe" % sttpVersion,
  "com.softwaremill.sttp" %% "okhttp-backend" % sttpVersion
  )

val sttpClientDependencies = Seq(
  "com.softwaremill.sttp.client" %% "core" % "2.0.0-M7"
)

val circeDependencies = Seq(
  "io.circe" %% "circe-core" % circleVersion,
  "io.circe" %% "circe-generic" % circleVersion,
  "io.circe" %% "circe-generic-extras" % circleVersion,
  "io.circe" %% "circe-parser" % circleVersion,
  "io.circe" %% "circe-literal" % circleVersion
  )

val telegramBotDependency = Seq(
  "com.bot4s" %% "telegram-core" % "4.4.0-RC1"
)

val metricsDependencies = Seq(
  "nl.grons" %% "metrics4-scala" % "4.0.1",
  "nl.grons" %% "metrics4-akka_a25" % "4.1.1",
  "nl.grons" %% "metrics4-scala-hdr" % "4.1.1"
  )

val Http4sVersion = "0.20.11"

val http4sDependencies = Seq(
  "org.http4s"     %% "http4s-blaze-server" % Http4sVersion,
  "org.http4s"     %% "http4s-circe"        % Http4sVersion,
  "org.http4s"     %% "http4s-dsl"          % Http4sVersion,
  "ch.qos.logback" %  "logback-classic"     % "1.2.1",
  // Optional for auto-derivation of JSON codecs
  "io.circe" %% "circe-generic" % circleVersion,
  "io.circe" %% "circe-literal" % circleVersion,
  "org.scalactic" %% "scalactic" % "3.0.1",
  "org.scalatest" %% "scalatest" % "3.0.1" % "test"
  )

val courierDependencies = Seq(
  "com.github.daddykotex" %% "courier" % "2.0.0"
)

val cacheDependencies = Seq(
  "com.github.cb372" %% "scalacache-core" % "0.28.0",
  "com.github.cb372" %% "scalacache-guava" % "0.28.0"
)

libraryDependencies ++= coreDependencies ++
                        sttpDependencies ++
                        sttpClientDependencies ++
                        circeDependencies ++
                        telegramBotDependency ++
                        cacheDependencies ++
                        metricsDependencies ++
                        http4sDependencies ++
                        courierDependencies

// ##
enablePlugins(JavaAppPackaging)
// Disable javadoc packaging
mappings in (Compile, packageDoc) := Seq()

// docker packaging configuration
enablePlugins(DockerPlugin)
enablePlugins(AshScriptPlugin)
mainClass in Compile := Some("t.bot.emailgate.Application")
dockerBaseImage      := "openjdk:8-jre-alpine"

// workaround for https://github.com/sbt/sbt-native-packager/issues/1202
daemonUserUid in Docker := None
daemonUser in Docker    := "daemon"
