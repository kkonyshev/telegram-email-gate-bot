package t.bot.emailgate

import cats.effect.{ContextShift, IO}

object Application extends App {

  implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.Implicits.global)

  val program = for {
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
