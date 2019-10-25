package t.bot.emailgate

import cats.effect.{ContextShift, IO}
import com.typesafe.config.ConfigFactory
import slogging.LazyLogging

object Application extends App with LazyLogging {

  implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.Implicits.global)

  val program = for {
    _ <- SelfCheck
      .mainIo(ConfigFactory.load().getString("self.check.url"))(
        r => IO.pure(logger.info(s"self check status: $r"))
      )
      .start
    _ <- EmailGateBot.io.start
    exitCode <- RestService.run(Nil)
  } yield {
    println(s"Exited with code: $exitCode")
  }
  program.unsafeRunSync()
}

trait Instrumented
    extends nl.grons.metrics4.scala.InstrumentedBuilder
    with nl.grons.metrics4.scala.DefaultInstrumented
