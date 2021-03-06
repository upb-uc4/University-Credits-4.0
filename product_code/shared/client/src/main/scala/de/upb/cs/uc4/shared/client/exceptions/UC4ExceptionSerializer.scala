package de.upb.cs.uc4.shared.client.exceptions

import akka.util.ByteString
import com.lightbend.lagom.scaladsl.api.deser.{ DefaultExceptionSerializer, RawExceptionMessage }
import com.lightbend.lagom.scaladsl.api.transport.{ ExceptionMessage, MessageProtocol, TransportErrorCode, TransportException }
import play.api.libs.json.{ JsError, JsSuccess, Json }
import play.api.{ Environment, Mode }

import java.io.{ CharArrayWriter, PrintWriter }
import scala.util.control.NonFatal

class UC4ExceptionSerializer(environment: Environment) extends DefaultExceptionSerializer(environment) {

  override def serialize(exception: Throwable, accept: Seq[MessageProtocol]): RawExceptionMessage = {
    val (errorCode, message) = exception match {
      case ce: UC4Exception if environment.mode == Mode.Prod && ce.possibleErrorResponse.`type` == ErrorType.InternalServer =>
        (TransportErrorCode(ce.errorCode, 1003, "Error"), GenericError(ErrorType.InternalServer))

      case ce: UC4Exception => (TransportErrorCode(ce.errorCode, 1003, "Error"), ce.possibleErrorResponse)

      case te: TransportException =>
        (te.errorCode, te.exceptionMessage)

      case _ if environment.mode == Mode.Prod =>
        // By default, don't give out information about generic exceptions.
        (TransportErrorCode.InternalServerError, GenericError(ErrorType.InternalServer))

      case e =>
        // Ok to give out exception information in dev and test
        val writer = new CharArrayWriter
        e.printStackTrace(new PrintWriter(writer))
        val detail = writer.toString
        (TransportErrorCode.InternalServerError, new ExceptionMessage(s"${exception.getClass.getName}: ${exception.getMessage}", detail))
    }

    val messageBytes = message match {
      case custom: UC4Error => ByteString.fromString(Json.stringify(Json.toJson(custom)))

      case message: ExceptionMessage => ByteString.fromString(Json.stringify(Json.obj(
        "name" -> message.name,
        "detail" -> message.detail
      ))) //If it is not one of our custom types of Exceptions, it is one of the default exception

      case _ => ByteString("")
    }

    RawExceptionMessage(errorCode, MessageProtocol(Some("application/json"), None, None), messageBytes)
  }

  override def deserialize(message: RawExceptionMessage): Throwable = {
    val messageJson =
      try {
        Json.parse(message.message.iterator.asInputStream)
      }
      catch {
        case NonFatal(_) =>
          Json.obj()
      }

    //Check if the raw json contains the fields needed for a CustomException (type, title, invalidParams). If so, use our deserializer, if not, use default
    if ((messageJson \ "type").isDefined && (messageJson \ "title").isDefined) {
      val customError = Json.fromJson[UC4Error](messageJson) match {
        case JsSuccess(error, _) => error
        case JsError(_)          => throw UC4Exception.InternalDeserializationError
      }
      fromCodeAndMessageCustom(message.errorCode, customError)
    }
    else {
      //Default serializer for Exceptions with fields "name" and "detail"
      val jsonParseResult = for {
        name <- (messageJson \ "name").validate[String]
        detail <- (messageJson \ "detail").validate[String]
      } yield new ExceptionMessage(name, detail)
      val exceptionMessage = jsonParseResult match {
        case JsSuccess(m, _) => m
        case JsError(_)      => new ExceptionMessage("deserialization exception", message.message.utf8String)
      }
      fromCodeAndMessage(message.errorCode, exceptionMessage)
    }

  }

  /** Override this if you wish to deserialize your own custom Exceptions.
    *
    * The default implementation delegates to [[TransportException.fromCodeAndMessage()]], which will return a best match
    * Lagom built-in exception.
    *
    * @param transportErrorCode The transport error code.
    * @param exceptionMessage   The exception message.
    * @return The exception.
    */
  override def fromCodeAndMessage(
      transportErrorCode: TransportErrorCode,
      exceptionMessage: ExceptionMessage
  ): Throwable = TransportException.fromCodeAndMessage(transportErrorCode, exceptionMessage)

  /** Used to deserialize our CustomExceptions.
    *
    * @param transportErrorCode The transport error code.
    * @param customError        The detailed Error.
    * @return The exception.
    */
  def fromCodeAndMessageCustom(
      transportErrorCode: TransportErrorCode,
      customError: UC4Error
  ): Throwable = new UC4NonCriticalException(transportErrorCode.http, customError)
}

