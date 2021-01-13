package de.upb.cs.uc4.operation.model

import play.api.libs.json.{ Format, Json }

object OperationDataState extends Enumeration {
  type OperationDataState = Value
  val PENDING, FINISHED, REJECTED = Value

  implicit val format: Format[OperationDataState] = Json.formatEnum(this)

  def All: Seq[OperationDataState] = values.toSeq
}
