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
    wire[Task].use(startServer[Task])
  }

  /** Creation of "trace-aware" services */
  def wire[F[_]: Sync: Logger]: Resource[F, AuthApi[Spanned[F, *]]] =
    Resource.liftF {
      for {
        userRepo <- UserRepository.create[F] // repo is in memory, initialized via Ref[F]
        emailService = EmailService.create[Spanned[F, *]]
        authApi      = AuthApi.create[Spanned[F, *]](userRepo, emailService)
      } yield authApi
    }

  def startServer[F[_]: ConcurrentEffect: Timer: Logger](authApi: AuthApi[Spanned[F, *]]): F[ExitCode] = {
    // Endpoints are "trace - aware", during execution of the request, there will be a parent span in scope
    val authEndpoints = AuthEndpoints[Spanned[F, *]](authApi).orNotFound

    // Http4s middleware, that either gets the span id from headers or creates a new root span, if headers were empty
    val routes: HttpApp[F] = TraceMiddleware[F](Log.entryPoint[F]("App"), Configuration.default())(authEndpoints)

    BlazeServerBuilder[F](global)
      .bindHttp(8080, "0.0.0.0")
      .withHttpApp(routes)
      .serve
      .compile
      .lastOrError
  }
}
