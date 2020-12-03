package de.upb.cs.uc4.admission.impl.commands

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import de.upb.cs.uc4.admission.model.CourseAdmission
import de.upb.cs.uc4.hyperledger.commands.HyperledgerReadCommand

case class GetCourseAdmissions(
    username: Option[String],
    courseId: Option[String],
    moduleId: Option[String],
    replyTo: ActorRef[StatusReply[Seq[CourseAdmission]]]
)
  extends HyperledgerReadCommand[Seq[CourseAdmission]]