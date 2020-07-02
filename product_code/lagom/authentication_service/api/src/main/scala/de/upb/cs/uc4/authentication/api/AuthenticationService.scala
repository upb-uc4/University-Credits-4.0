package de.upb.cs.uc4.authentication.api

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Descriptor, Service, ServiceCall}
import de.upb.cs.uc4.authentication.model.AuthenticationRole.AuthenticationRole

/** The AuthenticationService interface.
  *
  * This describes everything that Lagom needs to know about how to serve and
  * consume the AuthenticationService.
  */
trait AuthenticationService extends Service {
  /** Prefix for the path for the endpoints, a name/identifier for the service */
  val pathPrefix = "/authentication-management"

  /** Checks if the username and password pair exists */
  def check(user: String, pw: String): ServiceCall[NotUsed, (String, AuthenticationRole)]

  /** Returns role of the given user */
  def getRole(username: String): ServiceCall[NotUsed, AuthenticationRole]

  final override def descriptor: Descriptor = {
    import Service._
    named("authentication")
      .withCalls(
        restCall(Method.GET, pathPrefix + "/users?user&pw", check _),
        restCall(Method.GET, pathPrefix + "/role/:username", getRole _)
      )
  }
}
