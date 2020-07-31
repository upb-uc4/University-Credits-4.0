package de.upb.cs.uc4.shared.client.exceptions

import play.api.libs.json.{Format, JsResult, JsValue}

trait CustomError{
  val `type`: String
  val title: String

}

object CustomError{
  implicit val format: Format[CustomError] = new Format[CustomError]{
    override def reads(json: JsValue): JsResult[CustomError] = {
      if (json.toString().contains("invalidParams")){
        DetailedError.format.reads(json)
      }else{
        GenericError.format.reads(json)
      }
    }
    override def writes(o: CustomError): JsValue = o match {
      case dErr: DetailedError ⇒ DetailedError.format.writes(dErr)
      case gErr: GenericError ⇒ GenericError.format.writes(gErr)
    }
  }

  def getTitle(`type` : String) : String = {
    `type` match{
      //400
      case "path parameter mismatch" => "Parameter specified in path and in object do not match"
      case "wrong object" => "Unexpected object"
      //401
      case "authorization error" => "Username and password combination does not exist"
      //403
      case "not enough privileges" => "Insufficient privileges for this action"
      case "owner mismatch" => "You are not allowed to modify the resource"
      //404
      case "key not found" => "Key value is not in use"
      //409
      case "key duplicate" => "Key is already in use"
      //418
      case "teapot" => "I'm a teapot"
      //422
      case "validation error" => "Your request parameters did not validate"
      case "uneditable fields" => "Attempted to change uneditable fields"
      //500
      case "undeserializable exception" => "Internal error while deserializing Exception"
      //???
      case _ => "Internal Server Error"

    }
  }
}