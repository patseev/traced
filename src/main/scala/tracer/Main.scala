package tracer

import cats.effect.{ConcurrentEffect, ExitCode, Resource, Sync, Timer}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import monix.eval.{Task, TaskApp}
import natchez.log.Log
import org.http4s.HttpApp
import tracer.modules.auth.{AuthApi, AuthEndpoints, Spanned}
import com.ovoenergy.effect.natchez.http4s.server.{Configuration, TraceMiddleware}
import tracer.modules.auth.repos.UserRepository
import tracer.modules.auth.services.EmailService
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.syntax.kleisli._
import tracer.util.StdoutLogger
import tracer.util.StdoutLogger._

import scala.concurrent.ExecutionContext.global

object Main extends TaskApp {

  def run(args: List[String]): Task[ExitCode] = {
    implicit val logTask: Logger[Task] = StdoutLogger[Task]
    wiring[Task].use(startServer[Task])
  }

  /** Machinery for creation of entities, that are "trace-aware"
    *  Note that all these entities run in Kleisli[F, Span[F]], meaning that they need to have access to current span
    *  to execute an operation
    */
  def wiring[F[_]: Sync: Logger]: Resource[F, AuthApi[Spanned[F, *]]] =
    Resource.liftF {
      for {
        userRepo <- UserRepository.create[F] // repo is in memory, initialized via Ref[F]
        emailService = EmailService.create[Spanned[F, *]]
        authApi      = AuthApi.create[Spanned[F, *]](userRepo, emailService)
      } yield authApi
    }

  def startServer[F[_]: ConcurrentEffect: Timer: Logger](authApi: AuthApi[Spanned[F, *]]): F[ExitCode] = {
    val authEndpoints = AuthEndpoints[Spanned[F, *]](authApi).orNotFound

    /** Tracing Http4s middleware that is capable of intercepting span ids via headers, and sending out generated spans
      *
      * It operates on Routes, that need to have "Span" to operate (line 1 of the method), and it provides the span -
      * it takes it from the header or creates a new "root" span if it wasn't passed
      */
    val routes: HttpApp[F] = TraceMiddleware[F](Log.entryPoint[F]("App"), Configuration.default())(authEndpoints)

    BlazeServerBuilder[F](global)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(routes)
      .serve
      .compile
      .lastOrError
  }
}
