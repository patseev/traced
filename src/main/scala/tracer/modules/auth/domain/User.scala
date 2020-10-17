package tracer.modules.auth.domain

import java.util.UUID

import scala.util.control.NoStackTrace

case class User(id: UUID, email: String, password: String, isConfirmed: Boolean)

case class EmailIsAlreadyTaken(email: String) extends NoStackTrace
