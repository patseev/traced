package tracer.modules

import cats.data.Kleisli
import natchez.Span

package object auth {

  type Spanned[F[_], A] = Kleisli[F, Span[F], A]

}
