package t.bot.emailgate

import cats.effect.{IO, Timer}
import com.softwaremill.sttp.Response
import com.softwaremill.sttp.okhttp.OkHttpFutureBackend
import slogging.LazyLogging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

object SelfCheck extends LazyLogging {

  def mainIo(url: String)(f: Response[String] => IO[Unit] = _ => IO.pure()): IO[Unit] = {
    implicit val ec = ExecutionContext.global
    implicit val timer: Timer[IO] = IO.timer(ec)
    import cats.implicits._
    implicit val backend = OkHttpFutureBackend()

    def printFoo: IO[Response[String]] = {
      import com.softwaremill.sttp._
      IO.fromFuture(IO(sttp.get(uri"$url").send()))
    }

    def printRepeatedly: IO[Unit] =
      printFoo.map(f) >> IO.sleep(1 minute) >> IO.suspend(printRepeatedly)

    val app = for {
      _ <- IO.delay(logger.info(s"Starting self check with url: $url"))
      _ <- printRepeatedly
      _ <- IO.delay(logger.info("Shutting down..."))
    } yield ()

    app
  }
}
