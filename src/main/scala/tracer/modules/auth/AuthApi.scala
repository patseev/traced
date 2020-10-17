package tracer.modules.auth

import cats.FlatMap
import derevo.derive
import derevo.tagless.applyK
import io.chrisdavenport.log4cats.Logger
import natchez.{Trace, TraceValue}
import tofu.{MonadThrow, Throws}
import tofu.higherKind.Mid
import tracer.modules.auth.repos.UserRepository
import tracer.modules.auth.services.EmailService
import tofu.syntax.monadic._
import tofu.syntax.monoid.TofuSemigroupOps
import tofu.syntax.raise.RaiseOps

@derive(applyK)
trait AuthApi[F[_]] {
  def register(email: String, password: String): F[Unit]
  def confirm(email: String): F[Unit]
}

object AuthApi {

  def create[F[_]: Trace: Logger: MonadThrow](
    userRepo: UserRepository[F],
    emailService: EmailService[F]
  ): AuthApi[F] = {
    val service = new Impl[F](userRepo, emailService)

    (Traces[F] |+| Logs[F]).attach(service)
  }

  private class Impl[F[_]: FlatMap: Throws](userRepo: UserRepository[F], emailService: EmailService[F])
      extends AuthApi[F] {

    def register(email: String, password: String): F[Unit] =
      userRepo.create(email, password) >>= emailService.sendRegistrationConfirmation

    def confirm(email: String): F[Unit] =
      userRepo.find(email).flatMap {
        case Some(user) => emailService.sendWelcome(user)
        case None       => new Exception("User is not found").raise
      }
  }

  private object Traces {

    def apply[F[_]: Trace]: AuthApi[Mid[F, *]] =
      new AuthApi[Mid[F, *]] {

        def register(email: String, password: String): Mid[F, Unit] =
          Trace[F].span(s"Registering a user $email")(_)

        def confirm(email: String): Mid[F, Unit] =
          Trace[F].span(s"Confirming a user $email")(_)
      }
  }

  private object Logs {

    def apply[F[_]: FlatMap](implicit logger: Logger[F]) =
      new AuthApi[Mid[F, *]] {

        def register(email: String, password: String): Mid[F, Unit] =
          action => logger.info(s"Register with $email") *> action <* logger.info(s"Registered with $email")

        def confirm(email: String): Mid[F, Unit] =
          action => logger.info(s"Confirming with $email") *> action <* logger.info("Confirmed with $email")
      }
  }
}
