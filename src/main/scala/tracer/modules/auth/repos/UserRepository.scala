package tracer.modules.auth.repos

import java.util.UUID

import cats.data.Kleisli
import cats.effect.Sync
import cats.{FlatMap, Monad}
import cats.effect.concurrent.Ref
import derevo.derive
import derevo.tagless.applyK
import io.chrisdavenport.log4cats.Logger
import natchez.Trace
import tofu.higherKind.Mid
import tracer.modules.auth.domain.User
import tofu.syntax.monadic._
import tofu.syntax.monoid.TofuSemigroupOps
import tracer.modules.auth.Spanned
import tracer.util.StdoutLogger._

@derive(applyK)
trait UserRepository[F[_]] {
  def create(email: String, password: String): F[User]
  def find(email: String): F[Option[User]]
  def confirm(user: User): F[Unit]
}

object UserRepository {

  def create[F[_]: Sync: Logger]: F[UserRepository[Spanned[F, *]]] =
    Ref.of[F, List[User]](Nil).map { storage =>
      val service = new Impl[Spanned[F, *]](storage.mapK(Kleisli.liftK))

      (Traces[Spanned[F, *]] |+| Logs[Spanned[F, *]]).attach(service)
    }

  private class Impl[F[_]: Monad](storage: Ref[F, List[User]]) extends UserRepository[F] {

    def create(email: String, password: String): F[User] = {
      val newUser = User(UUID.randomUUID(), email, password, isConfirmed = false)
      storage.update(_.appended(newUser)).as(newUser)
    }

    def find(email: String): F[Option[User]] =
      storage.get.map(_.find(_.email == email))

    def confirm(user: User): F[Unit] =
      storage.get.flatMap { users =>
        storage.set(users.filterNot(_.id == user.id).appended(user.copy(isConfirmed = true)))
      }
  }

  private object Traces {

    def apply[F[_]: Trace]: UserRepository[Mid[F, *]] =
      new UserRepository[Mid[F, *]] {

        def create(email: String, password: String): Mid[F, User] =
          Trace[F].span(s"Creating user with email $email")(_)

        def find(email: String): Mid[F, Option[User]] =
          Trace[F].span(s"Finding user with email $email")(_)

        def confirm(user: User): Mid[F, Unit] =
          Trace[F].span(s"Confirming user ${user.id}")(_)
      }
  }

  private object Logs {

    def apply[F[_]: FlatMap](implicit logger: Logger[F]): UserRepository[Mid[F, *]] =
      new UserRepository[Mid[F, *]] {

        def create(email: String, password: String): Mid[F, User] =
          action =>
            logger.info(s"Creating user with $email") *> action.flatTap(res => logger.info(s"Created user $res"))

        def find(email: String): Mid[F, Option[User]] =
          action =>
            logger.info(s"Finding user by $email") *> action.flatTap(res => logger.info(s"Found user by $email - $res"))

        def confirm(user: User): Mid[F, Unit] =
          action =>
            logger.info(s"Confirming user ${user.id}") *> action.flatTap(_ => logger.info(s"Confirmed user ${user.id}"))
      }
  }
}
