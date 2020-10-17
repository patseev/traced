package tracer.modules.auth

import cats.effect.Sync
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import tofu.syntax.monadic._

object AuthEndpoints {

  def apply[F[_]: Sync](authApi: AuthApi[F]): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]; import dsl._

    HttpRoutes.of[F] {
      case POST -> Root / "register" =>
        authApi.register("email", "password") *> Ok()
      case POST -> Root / "confirm" =>
        authApi.confirm("email") *> Ok()
    }
  }

}
