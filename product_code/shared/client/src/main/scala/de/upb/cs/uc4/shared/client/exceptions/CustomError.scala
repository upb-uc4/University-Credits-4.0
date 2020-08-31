package de.upb.cs.uc4.shared.client.exceptions

import de.upb.cs.uc4.shared.client.exceptions.ErrorType.ErrorType
import play.api.libs.json.{ Format, JsResult, JsValue, Json }

trait CustomError {
  val `type`: ErrorType
  val title: String

}

object CustomError {
  implicit val format: Format[CustomError] = new Format[CustomError] {
    override def reads(json: JsValue): JsResult[CustomError] = json match {
      case json if (json \ "invalidParams").isDefined => Json.fromJson[DetailedError](json)
      case json if (json \ "transactionId").isDefined => Json.fromJson[TransactionError](json)
      case json if (json \ "information").isDefined => Json.fromJson[InformativeError](json)
      case json => Json.fromJson[GenericError](json)
    }

    override def writes(o: CustomError): JsValue = o match {
      case dErr: DetailedError    => Json.toJson(dErr)
      case gErr: GenericError     => Json.toJson(gErr)
      case tErr: TransactionError => Json.toJson(tErr)
      case iErr: InformativeError => Json.toJson(iErr)
    }
  }
}
