package de.upb.cs.uc4.shared.client.exceptions

abstract class UC4Exception(
    val errorCode: Int,
    val possibleErrorResponse: UC4Error,
    val cause: Throwable
) extends Exception(possibleErrorResponse.title, cause, true, true) with UC4ExceptionSerializable {

  override def getMessage: String = super.getMessage + "\n" + possibleErrorResponse.toString
}

object UC4Exception {

  def apply(errorCode: Int, possibleErrorResponse: UC4Error, cause: Throwable = null): UC4Exception = {
    if (errorCode >= 500) {
      new UC4CriticalException(errorCode, possibleErrorResponse, cause)
    }
    else {
      new UC4NonCriticalException(errorCode, possibleErrorResponse)
    }
  }

  //304
  val NotModified = new UC4NonCriticalException(304, GenericError(ErrorType.NotModified))
  //400
  val DeserializationError = new UC4NonCriticalException(400, GenericError(ErrorType.Deserialization))
  def KafkaDeserializationError(expected: String, actual: String) = new UC4NonCriticalException(400, GenericError(ErrorType.KafkaDeserialization, s"Expected class type '$expected' but received '$actual''"))

  val AlreadyDeleted = new UC4NonCriticalException(400, GenericError(ErrorType.AlreadyDeleted))
  val MalformedRefreshToken = new UC4NonCriticalException(400, GenericError(ErrorType.MalformedRefreshToken))
  val MalformedLoginToken = new UC4NonCriticalException(400, GenericError(ErrorType.MalformedLoginToken))
  val MalformedFrontendSigningToken = new UC4NonCriticalException(400, GenericError(ErrorType.MalformedFrontendSigningToken))
  val MultipleAuthorizationError = new UC4NonCriticalException(400, GenericError(ErrorType.MultipleAuthorization))
  def QueryParameterError(invalidParams: SimpleError*) =
    new UC4NonCriticalException(400, DetailedError(ErrorType.QueryParameter, invalidParams))
  //401
  val JwtAuthorizationError = new UC4NonCriticalException(401, GenericError(ErrorType.JwtAuthorization))
  val RefreshTokenMissing = new UC4NonCriticalException(401, GenericError(ErrorType.RefreshTokenMissing))
  val BasicAuthorizationError = new UC4NonCriticalException(401, GenericError(ErrorType.BasicAuthorization))
  val RefreshTokenExpired = new UC4NonCriticalException(401, GenericError(ErrorType.RefreshTokenExpired))
  val LoginTokenExpired = new UC4NonCriticalException(401, GenericError(ErrorType.LoginTokenExpired))
  //403
  val NotEnoughPrivileges = new UC4NonCriticalException(403, GenericError(ErrorType.NotEnoughPrivileges))
  val OwnerMismatch = new UC4NonCriticalException(403, GenericError(ErrorType.OwnerMismatch))
  //404
  val NotFound = new UC4NonCriticalException(404, GenericError(ErrorType.KeyNotFound))
  val NotEnrolled = new UC4NonCriticalException(404, GenericError(ErrorType.NotEnrolled))
  //409
  val Duplicate = new UC4NonCriticalException(409, GenericError(ErrorType.KeyDuplicate))
  val AlreadyEnrolled = new UC4NonCriticalException(409, GenericError(ErrorType.AlreadyEnrolled))
  val RemovalNotAllowed = new UC4NonCriticalException(409, GenericError(ErrorType.RemovalNotAllowed))
  //415
  val UnsupportedMediaType = new UC4NonCriticalException(415, GenericError(ErrorType.UnsupportedMediaType))
  //422
  val ValidationTimeout = new UC4NonCriticalException(422, GenericError(ErrorType.ValidationTimeout))
  val PathParameterMismatch = new UC4NonCriticalException(422, GenericError(ErrorType.PathParameterMismatch))
  val RefreshTokenSignatureError = new UC4NonCriticalException(422, GenericError(ErrorType.RefreshTokenSignatureInvalid))
  val LoginTokenSignatureError = new UC4NonCriticalException(422, GenericError(ErrorType.LoginTokenSignatureInvalid))
  val FrontendSigningTokenSignatureError = new UC4NonCriticalException(422, GenericError(ErrorType.FrontendSigningTokenSignatureInvalid))
  val FrontendSigningTokenExpired = new UC4NonCriticalException(422, GenericError(ErrorType.FrontendSigningTokenExpired))
  //428
  val PreconditionRequired = new UC4NonCriticalException(428, GenericError(ErrorType.PreconditionRequired))
  //500
  val InternalDeserializationError = new UC4CriticalException(500, GenericError(ErrorType.UndeserializableException), null)

  def InternalServerError(name: String, reason: String, throwable: Throwable = null) =
    new UC4CriticalException(500, DetailedError(ErrorType.InternalServer, Seq(SimpleError(name, reason))), throwable)

  def InternalServerError(throwable: Throwable, invalidParams: SimpleError*) =
    new UC4CriticalException(500, DetailedError(ErrorType.InternalServer, invalidParams), throwable)

  //501
  val NotImplemented = new UC4NonCriticalException(501, GenericError(ErrorType.NotImplemented))
}
