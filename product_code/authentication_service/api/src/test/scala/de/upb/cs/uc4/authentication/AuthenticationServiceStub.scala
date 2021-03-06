package de.upb.cs.uc4.authentication

import akka.{ Done, NotUsed }
import com.lightbend.lagom.scaladsl.api.ServiceCall
import de.upb.cs.uc4.authentication.api.AuthenticationService
import de.upb.cs.uc4.authentication.model.{ AuthenticationUser, RefreshToken, Tokens }
import de.upb.cs.uc4.shared.client.JsonUsername

import scala.concurrent.Future

class AuthenticationServiceStub extends AuthenticationService {

  /** Sets the authentication data of a user */
  override def allowVersionNumber: ServiceCall[NotUsed, Done] = ServiceCall { _ => Future.successful(Done) }

  /** Changes the password of the given user */
  override def setAuthentication(): ServiceCall[AuthenticationUser, Done] = ServiceCall { _ => Future.successful(Done) }

  /** Allows PUT */
  override def changePassword(username: String): ServiceCall[AuthenticationUser, Done] = ServiceCall { _ => Future.successful(Done) }

  /** This Methods needs to allow a GET-Method */
  override def allowedPut: ServiceCall[NotUsed, Done] = ServiceCall { _ => Future.successful(Done) }

  /** Logs a user in and returns a refresh and a login token in the header */
  override def login: ServiceCall[NotUsed, Done] = ServiceCall { _ => Future.successful(Done) }

  /** Allows GET */
  override def allowedGet: ServiceCall[NotUsed, Done] = ServiceCall { _ => Future.successful(Done) }

  /** Generates a new login token from a refresh token */
  override def refresh: ServiceCall[NotUsed, JsonUsername] = ServiceCall { _ => Future.successful(JsonUsername("MOCK")) }

  /** Logs the user out */
  override def logout: ServiceCall[NotUsed, Done] = ServiceCall { _ => Future.successful(Done) }

  /** Logs a user in and returns a refresh and a login token in the body */
  override def loginMachineUser: ServiceCall[NotUsed, Tokens] = ServiceCall { _ => Future.successful(Tokens("Mock", "Mock")) }

  /** Generates a new login token from a refresh token in the bearer header */
  override def refreshMachineUser: ServiceCall[NotUsed, RefreshToken] = ServiceCall { _ => Future.successful(RefreshToken("Mock", "Mock")) }
}
