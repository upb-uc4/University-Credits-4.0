package de.upb.cs.uc4.shared.client.operation

import play.api.libs.json.{ Format, Json }

case class ApprovalList(user: Seq[String], groups: Seq[String])

object ApprovalList {
  implicit val format: Format[ApprovalList] = Json.format
}