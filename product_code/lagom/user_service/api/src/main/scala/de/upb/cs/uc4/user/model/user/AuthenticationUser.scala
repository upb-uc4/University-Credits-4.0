package de.upb.cs.uc4.user.model.user

import de.upb.cs.uc4.user.model.Role.Role
import play.api.libs.json.{Format, Json}

/** Used to be sent to the AuthenticationService. Includes only relevant data for that service. */
case class AuthenticationUser(username: String, password: String, role: Role)

object AuthenticationUser {
  implicit val format: Format[AuthenticationUser] = Json.format
}
