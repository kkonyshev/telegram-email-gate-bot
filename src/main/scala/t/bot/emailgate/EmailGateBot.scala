package t.bot.emailgate

import java.net.URL
import java.nio.channels.Channels
import java.nio.file.{Files, Path}
import java.time.{Instant, LocalDateTime, ZoneId}
import java.util.UUID

import cats.implicits._
import cats.effect.{ContextShift, IO}
import cats.instances.future._
import cats.syntax.functor._
import com.bot4s.telegram.api.declarative.Commands
import com.bot4s.telegram.future.Polling
import com.bot4s.telegram.methods.{GetFile, SendMessage}
import com.bot4s.telegram.models.ChatId.Chat
import com.bot4s.telegram.models.{Document, Message}
import com.typesafe.config.ConfigFactory
import courier.{Content, Envelope, Mailer, Multipart, Text}
import javax.mail.internet.InternetAddress
import scalacache._
import scalacache.guava._
import scalacache.modes.try_._
import t.bot.emailgate.EmailGateBot._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class EmailGateBot(token: String, mailer: Mailer)
    extends AbstractBot(token) with Polling with Commands[Future] with Instrumented {
  implicit val cs: ContextShift[IO] =
    IO.contextShift(scala.concurrent.ExecutionContext.Implicits.global)
  val hits = metricRegistry.counter("EmailGateBot.hits")

  implicit val catsCache: Cache[String] = GuavaCache(CacheConfig.defaultCacheConfig)

  val cc_list = scala.collection.mutable.Set.empty[InternetAddress]

  healthCheck("EmailGateBot") {}

  onCommand('cc) { implicit msg =>
    withArgs { args =>
      implicit val cache = cc_list

      if (args.isEmpty) replyMd("No arguments provided.").map(_ => Future.successful(unit))
      else {
        args.head match {
          case "list" =>
            replyMd(s"current cc list: ${cc_list.map(_.getAddress).mkString}")
              .map(_ => Future.successful(unit))
          case "add" if args.size == 2 =>
            cc_list += new InternetAddress(args(1))
            replyMd(s"new cc list: ${cc_list.map(_.getAddress).mkString}")
              .map(_ => Future.successful(unit))
          case "clear" =>
            cc_list.clear()
            replyMd(s"cc list is empty").map(_ => Future.successful(unit))
          case op @ _ =>
            replyMd(s"unknown operation $op").map(_ => Future.successful(unit))
        }
      }
    }
  }

  onMessage({ implicit msg =>
    if (msg.message.text.nonEmpty && msg.message.text.get.startsWith("/")) {
      logger.info("ignoring command")
      Future.successful(unit)
    } else {
      get(msg.from.get.id).get match {
        case Some(_) =>
          replyMd(
            text = s"Who-who-who, Bro! Stop spamming... your limit is 1 message per 1 minute.",
            replyToMessageId = Some(msg.messageId)
          ).map(_ => unit)
        case _ =>
          toContent(msg) match {
            case Some(c) =>
              put(msg.from.get.id)("ops", ttl = Some(1.minute))
              processTextMessage(c).andThen({
                case Failure(_) => remove(msg.from.get.id)
              })
            case None =>
              replyMd(
                text = s"Unsupported message type ot user undefined, please send text message",
                replyToMessageId = Some(msg.messageId)
              ).map(_ => unit)
          }
      }
    }
  })

  def downloadFile(token: String, fileToDownload: String): Option[Path] = {
    val tmpFile = Files.createTempFile(UUID.randomUUID().toString, "bot")
    tmpFile.toFile.deleteOnExit()
    val urlToDownload = s"https://api.telegram.org/file/bot$token/$fileToDownload"
    logger.info(s"urlToDownload: $urlToDownload")

    val url = new URL(urlToDownload)
    val readableByteChannel = Channels.newChannel(url.openStream())

    import java.io.FileOutputStream
    val fileOutputStream = new FileOutputStream(tmpFile.toFile)

    fileOutputStream.getChannel
      .transferFrom(readableByteChannel, 0, Long.MaxValue)

    Some(tmpFile)
  }

  def toContent(message: Message): Option[Content] = {
    if (message.text.isDefined) {
      Some(Text(message.text.get))
    } else if (message.document.isDefined) {
      val doc = message.document.get
      val d = fetchFile(List((doc.fileName.get, doc.fileId)))
      attachFiles(d)
    } else if (message.photo.nonEmpty) {
      val docList = message.photo.map(f => f.map(p => (p.fileId, p.fileId))).getOrElse(Nil).toList
      val d = fetchFile(docList)
      attachFiles(d)
    } else {
      None
    }
  }

  private def attachFiles(d: IO[List[(Path, String)]]) = {
    val fRes = d.attempt.unsafeRunSync() match {
      case Right(v) if v.nonEmpty =>
        logger.info(s"success file downloading: $v")
        def attach(files: List[(Path, String)], mp: Multipart = Multipart()): Multipart =
          files match {
            case file :: tail => attach(tail, mp.attach(file._1.toFile, file._2.some))
            case Nil => mp
          }
        attach(v).some
      case Left(error) =>
        logger.error("error file downloading", error)
        None
    }
    fRes
  }

  private def fetchFile(docs: List[(String, String)]): IO[List[(Path, String)]] = {
    docs
      .map(doc => {
        val d = for {
          name <- IO.pure(doc._1.some)
          f <- IO.fromFuture(IO(request(GetFile(doc._2))))
        } yield {
          val path = f.filePath
          logger.info(s"file: $path")
          val file = downloadFile(token, path.get)
          logger.info(s"local file: $file")
          (file, name)
        }
        d.map(f => (f._1, f._2).mapN((a, b) => (a, b)))
      })
      .parSequence
      .map(_.flatten.toList)
  }

  def processTextMessage(content: Content)(implicit msg: Message): Future[Unit] = {
    val sendProcess = for {
      _ <- IO.fromFuture(
        IO(replyMd(text = s"Processing...", replyToMessageId = Some(msg.messageId)))
      )
      res <- IO
        .fromFuture(
          IO(
            mailer(
              Envelope
                .from(EmailGateBot.from)
                .to(EmailGateBot.to: _*)
                .cc(cc_list.toList: _*)
                .subject(msg.toSubject)
                .content(content)
            ) andThen {
              case Success(_) =>
                logger.info(s"Envelope sent")
              case Failure(error) =>
                logger.error(s"Envelope not set", error)
            }
          )
        )
        .attempt
    } yield {
      res match {
        case Right(_) =>
          request(
            SendMessage(
              chatId = Chat(msg.source),
              replyToMessageId = Some(msg.messageId),
              text = s"Message has been successfully sent"
            )
          )
        case Left(error) =>
          request(
            SendMessage(
              chatId = Chat(msg.source),
              replyToMessageId = Some(msg.messageId),
              text = s"Error occurred, message has not been sent..."
            )
          )
          logger.warn(s"error sending message ($msg)", error)
      }
      logger.info(s"message has been sent: $res")

    }
    sendProcess.unsafeToFuture()
  }
}

object EmailGateBot {

  def io: IO[Unit] = {
    val api_token = ConfigFactory.load().getString("bot.api_token")
    val bot = new EmailGateBot(api_token, mailer)
    IO.fromFuture(IO(bot.run()))
  }

  val mailHost = ConfigFactory.load().getString("mail.host")
  val mailPort = ConfigFactory.load().getInt("mail.port")
  val mailAuth = ConfigFactory.load().getBoolean("mail.auth")
  val username = ConfigFactory.load().getString("mail.username")
  val password = ConfigFactory.load().getString("mail.password")
  val startTls = ConfigFactory.load().getBoolean("mail.startTls")
  val ssl = ConfigFactory.load().getBoolean("mail.ssl")

  val fromEmail = ConfigFactory.load().getString("mail.from.email")
  val fromName = ConfigFactory.load().getString("mail.from.name")

  val toList = ConfigFactory.load().getString("mail.to.list").split(",").toList

  val from = new InternetAddress(fromEmail, fromName)
  val to = toList.map(t => new InternetAddress(t))

  def mailer: Mailer = {

    val mailer = Mailer(mailHost, mailPort)
      .auth(mailAuth)
      .as(username, password)
      .startTls(startTls)
      .ssl(ssl)()

    mailer
  }

  implicit class MsgFromOps(val message: Message) extends AnyVal {
    def toSubject: String =
      s"New message from ${message.from.get.username.getOrElse("_undefined_")} on ${LocalDateTime.ofInstant(
        Instant.ofEpochSecond(message.date),
        ZoneId.of("UTC")
      )} (msgId=${message.messageId})"
  }
}
