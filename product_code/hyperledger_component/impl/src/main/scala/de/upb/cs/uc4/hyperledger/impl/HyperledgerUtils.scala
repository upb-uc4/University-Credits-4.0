package de.upb.cs.uc4.hyperledger.impl

import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.util.Timeout
import de.upb.cs.uc4.hyperledger.BuildInfo
import de.upb.cs.uc4.hyperledger.api.model.JsonHyperledgerVersion
import de.upb.cs.uc4.hyperledger.exceptions.traits.{ HyperledgerExceptionTrait, NetworkExceptionTrait, TransactionExceptionTrait }
import de.upb.cs.uc4.hyperledger.impl.commands.{ GetChaincodeVersion, HyperledgerBaseCommand }
import de.upb.cs.uc4.shared.client.exceptions._
import de.upb.cs.uc4.shared.server.messages.{ Accepted, Confirmation, Rejected }
import play.api.libs.json.{ JsResultException, Json }

import scala.concurrent.{ ExecutionContext, Future }

object HyperledgerUtils {

  implicit class ExceptionUtils(val exception: Throwable) {

    def toUC4Exception: UC4Exception = {
      exception match {
        case exp: HyperledgerExceptionTrait =>
          val message = exp.innerException.getMessage
          UC4Exception.InternalServerError(
            s"HyperledgerException thrown by ${exp.actionName}",
            if (message != null) message else exp.innerException.toString
          )
        case exp: NetworkExceptionTrait =>
          UC4Exception.InternalServerError(
            s"NetworkException with ${exp.toString}",
            exp.innerException.getMessage
          )
        case transactionEx: TransactionExceptionTrait =>
          try {
            val json = Json.parse(transactionEx.payload)
            try {
              errorMatching(json.as[UC4Error])
            }
            catch {
              case _: JsResultException =>
                UC4Exception.InternalServerError(
                  "Unknown UC4Error",
                  s"Hyperledger uses a new ErrorType ${json("type")}"
                )
            }
          }
          catch {
            case _: Throwable =>
              UC4Exception.InternalServerError(
                "Broken TransactionException",
                s"""id:      ${transactionEx.transactionName}
                   |payload: ${transactionEx.payload}
                   |""".stripMargin
              )
          }
        case ex: Throwable =>
          UC4Exception.InternalServerError("Unknown Internal Error", ex.getMessage, ex)
      }
    }
  }

  private def errorMatching(uc4Error: UC4Error): UC4Exception = {
    uc4Error match {
      case genericError: GenericError => genericError.`type` match {
        case ErrorType.HLUnknownTransactionId => new UC4CriticalException(500, genericError, null)
        case ErrorType.HLNotFound => new UC4NonCriticalException(404, genericError)
        case ErrorType.HLConflict => new UC4NonCriticalException(409, genericError)
        case ErrorType.HLUnprocessableLedgerState => new UC4CriticalException(500, genericError, null)
        case ErrorType.HLInsufficientApprovals => new UC4NonCriticalException(422, genericError)
        case ErrorType.HLParameterNumberError => new UC4CriticalException(500, genericError)
        case ErrorType.HLAccessDenied => new UC4NonCriticalException(403, genericError)
        case ErrorType.HLExecutionImpossible => new UC4NonCriticalException(422, genericError)

        //DEPRECATED
        case ErrorType.HLApprovalDenied => new UC4NonCriticalException(403, genericError)
        case ErrorType.HLRejectionDenied => new UC4NonCriticalException(403, genericError)
        case ErrorType.HLApprovalImpossible => new UC4NonCriticalException(422, genericError)
        case ErrorType.HLRejectionImpossible => new UC4NonCriticalException(422, genericError)
        case _ => UC4Exception.InternalServerError("Unknown GenericError", s"Hyperledger uses a new ErrorType ${genericError.`type`}")
      }
      case detailedError: DetailedError => detailedError.`type` match {
        case ErrorType.HLUnprocessableEntity => new UC4NonCriticalException(422, detailedError)
        case ErrorType.HLInvalidEntity => new UC4NonCriticalException(422, detailedError)
        case _ => UC4Exception.InternalServerError("Unknown DetailedError", s"Hyperledger uses a new ErrorType ${detailedError.`type`}")
      }
      case transactionError: TransactionError => transactionError.`type` match {
        case ErrorType.HLInvalidTransactionCall => new UC4CriticalException(500, transactionError, null)
        case _ => UC4Exception.InternalServerError("Unknown TransactionError", s"Hyperledger uses a new ErrorType ${transactionError.`type`}")
      }
    }
  }

  object VersionUtil {
    /** Create a response for a Hyperledger Version call
      *
      * @param ref     The reference to the Akka actor that should be asked for the version (Must be a Hyperledger Akka actor)
      * @param timeout for the actor
      * @param ec      the execution context
      * @return Hyperledger versions as a JSON object
      */
    def createHyperledgerVersionResponse(ref: EntityRef[HyperledgerBaseCommand])(implicit timeout: Timeout, ec: ExecutionContext): Future[JsonHyperledgerVersion] = {
      ref.askWithStatus[Confirmation](replyTo => GetChaincodeVersion(replyTo)).map {
        case Accepted(summary) =>
          JsonHyperledgerVersion(BuildInfo.version, summary)
        case Rejected(statusCode, reason) =>
          throw UC4Exception(statusCode, reason)
      }.recover {
        case ue: UC4Exception => throw ue
        case ex: Throwable    => throw UC4Exception.InternalServerError("Error while fetching version", "unknown exception", ex)
      }
    }

    /** Create a Hyperledger version response with only the API version filled in
      *
      * @param ec the execution context
      * @return Hyperledger versions as a JSON object. Chaincode version defaults to: "Service does not use chaincode."
      */
    def createHyperledgerAPIVersionResponse(chaincodeVersionMessage: String = "Service does not use chaincode")(implicit ec: ExecutionContext): Future[JsonHyperledgerVersion] = {
      Future.successful(JsonHyperledgerVersion(BuildInfo.version, chaincodeVersionMessage))
    }
  }

}
