package tracer.modules.auth.services

import cats.{Applicative, FlatMap, Monad}
import derevo.derive
import derevo.tagless.{applyK, functorK}
import io.chrisdavenport.log4cats.Logger
import natchez.Trace
import tofu.higherKind.Mid
import tracer.modules.auth.domain.User
import tofu.syntax.monadic._
import tofu.syntax.monoid.TofuSemigroupOps

@derive(applyK, functorK)
trait EmailService[F[_]] {
  def sendRegistrationConfirmation(user: User): F[Unit]
  def sendWelcome(user: User): F[Unit]
}

object EmailService {

  def create[F[_]: Monad: Trace: Logger]: EmailService[F] = {
    val service = new Impl[F]
    (Traces[F] |+| Logs[F]).attach(service)
  }

  private class Impl[F[_]: Applicative] extends EmailService[F] {
    def sendRegistrationConfirmation(user: User): F[Unit] = Applicative[F].unit

    def sendWelcome(user: User): F[Unit] = Applicative[F].unit
  }

  private object Traces {

    def apply[F[_]: Trace]: EmailService[Mid[F, *]] =
      new EmailService[Mid[F, *]] {

        def sendRegistrationConfirmation(user: User): Mid[F, Unit] =
          Trace[F].span(s"Sending registration email to user ${user.id}")(_)

        def sendWelcome(user: User): Mid[F, Unit] =
          Trace[F].span(s"Sending welcome email to user ${user.id}")(_)
      }
  }

  private object Logs {

    def apply[F[_]: FlatMap](implicit logger: Logger[F]): EmailService[Mid[F, *]] =
      new EmailService[Mid[F, *]] {

        def sendRegistrationConfirmation(user: User): Mid[F, Unit] =
          action =>
            logger.info(s"Sending confirmation email to ${user.id}") *> action <* logger.info(
              s"Confirmation email is sent to ${user.id}"
            )

        def sendWelcome(user: User): Mid[F, Unit] =
          action =>
            logger.info(s"Sending a welcome email to ${user.id}") *> action <* logger.info(
              s"Welcome emails is sent to ${user.id}"
            )
      }
  }
}
