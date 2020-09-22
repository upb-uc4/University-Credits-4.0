package de.upb.cs.uc4.shared.client.exceptions

import play.api.libs.json.{ Format, Json }

object ErrorType extends Enumeration {
  type ErrorType = Value
  val Deserialization, JsonValidation, MalformedRefreshToken, MalformedLoginToken, UnexpectedEntity, MultipleAuthorization, //400
  BasicAuthorization, JwtAuthorization, RefreshTokenExpired, LoginTokenExpired, RefreshTokenMissing, //401
  NotEnoughPrivileges, OwnerMismatch, //403
  KeyNotFound, //404
  KeyDuplicate, //409
  Teapot, //418
  PathParameterMismatch, RefreshTokenSignatureInvalid, LoginTokenSignatureInvalid, //422
  Validation, UneditableFields, //422 In a DetailedError
  InternalServer, UndeserializableException, //500
  HLInternal, //In an InformativeError
  HLUnknownTransactionId, HLUnprocessableEntity, HLNotFound, HLConflict, HLUnprocessableLedgerState, //In a GenericError
  HLUnprocessableField, //In a DetailedError
  HLInvalidTransactionCall //In a TransactionError
  = Value

  implicit val format: Format[ErrorType] = Json.formatEnum(this)

  def All: Seq[ErrorType] = values.toSeq

  def getTitle(errorType: ErrorType): String = {
    errorType match {
      //HL errors are missing, but as they are given to us with a title, we do not need to find a fitting title
      //If not stated otherwise, the type is contained in a  GenericError
      //400
      case Deserialization => "Syntax of the provided json object was incorrect"
      case JsonValidation => "The provided json object did not validate" //In a DetailedError
      case MalformedRefreshToken => "The long term token is malformed"
      case MalformedLoginToken => "The login token is malformed"
      case UnexpectedEntity => "Expected another entity" //In an InformativeError
      case MultipleAuthorization => "Multiple authorization given"
      //401
      case BasicAuthorization => "Username and password combination does not exist"
      case JwtAuthorization => "Authorization token missing"
      case RefreshTokenMissing => "Refresh token missing"
      case RefreshTokenExpired => "Your long term session expired"
      case LoginTokenExpired => "Your session expired"
      //403
      case NotEnoughPrivileges => "Insufficient privileges for this action"
      case OwnerMismatch => "You are not allowed to modify the resource"
      //404
      case KeyNotFound => "Key value is not in use"
      //409
      case KeyDuplicate => "Key is already in use"
      //418
      case Teapot => "I'm a teapot"
      //422
      case PathParameterMismatch => "Parameter specified in path and in object do not match"
      case Validation => "Your request parameters did not validate" //In a DetailedError
      case UneditableFields => "Attempted to change uneditable fields" //In a DetailedError
      case RefreshTokenSignatureInvalid => "The long term token has a wrong signature"
      case LoginTokenSignatureInvalid => "The login term token has a wrong signature"
      //500
      case InternalServer => "An internal server error has occurred"
      case UndeserializableException => "Internal error while deserializing Exception"
      case HLInternal => "Hyperledger encountered an internal error" //In an InformativeError
    }
  }

}
