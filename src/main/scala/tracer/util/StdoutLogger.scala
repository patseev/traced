package tracer.util

import cats.data.Kleisli
import cats.effect.Sync
import io.chrisdavenport.log4cats.Logger

object StdoutLogger {

  implicit def kleisliLogger[F[_]: Logger, A]: Logger[Kleisli[F, A, *]] = Logger[F].mapK(Kleisli.liftK)

  def apply[F[_]: Sync]: Logger[F] =
    new Logger[F] {
      def error(t: Throwable)(message: => String): F[Unit] = Sync[F].delay(println(s"${t.getMessage} $message"))

      def warn(t: Throwable)(message: => String): F[Unit] = Sync[F].delay(println(s"${t.getMessage} $message"))

      def info(t: Throwable)(message: => String): F[Unit] = Sync[F].delay(println(s"${t.getMessage} $message"))

      def debug(t: Throwable)(message: => String): F[Unit] = Sync[F].delay(println(s"${t.getMessage} $message"))

      def trace(t: Throwable)(message: => String): F[Unit] = Sync[F].delay(println(s"${t.getMessage} $message"))

      def error(message: => String): F[Unit] = Sync[F].delay(println(message))

      def warn(message: => String): F[Unit] = Sync[F].delay(println(message))

      def info(message: => String): F[Unit] = Sync[F].delay(println(message))

      def debug(message: => String): F[Unit] = Sync[F].delay(println(message))

      def trace(message: => String): F[Unit] = Sync[F].delay(println(message))
    }
}
