package t.bot.emailgate

import cats.effect._
import cats.implicits._
import io.circe._
import org.http4s._
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.implicits._
import org.http4s.server.blaze.BlazeServerBuilder

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global

object RestService extends IOApp with Instrumented {
  implicit val cs: ContextShift[IO] = IO.contextShift(global)

  val helloWorldService = HttpRoutes
    .of[IO] {
      case GET -> Root / "metrics" => getMetrics()
    }
    .orNotFound

  def getMetrics() = {

    val isHealthy = registry.runHealthChecks().values().asScala.toList.forall(_.isHealthy)
    val hitsCount = metricRegistry.counter("EmailGateBot.hits").getCount
    Ok(
      Json.obj("isHealthy" -> Json.fromBoolean(isHealthy), "hitsCount" -> Json.fromLong(hitsCount))
    )

  }

  def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(
        sys.env
          .getOrElse[String](
            "http.port",
            com.typesafe.config.ConfigFactory.load().getString("http.port")
          )
          .toInt,
        "0.0.0.0"
      )
      .withHttpApp(helloWorldService)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)

}
