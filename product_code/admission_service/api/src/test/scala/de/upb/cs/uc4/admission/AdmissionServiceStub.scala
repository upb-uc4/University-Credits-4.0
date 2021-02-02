package de.upb.cs.uc4.admission

import akka.{ Done, NotUsed }
import com.lightbend.lagom.scaladsl.api.ServiceCall
import de.upb.cs.uc4.admission.api.AdmissionService
import de.upb.cs.uc4.admission.model.{ CourseAdmission, DropAdmission, ExamAdmission }
import de.upb.cs.uc4.shared.client.{ JsonHyperledgerVersion, SignedProposal, SignedTransaction, UnsignedProposal, UnsignedTransaction }

import scala.concurrent.Future

class AdmissionServiceStub extends AdmissionService {

  protected var courseAdmissions: Seq[CourseAdmission] = Seq()
  protected var examAdmissions: Seq[ExamAdmission] = Seq()


  def reset(): Unit = {
    courseAdmissions = Seq()
    examAdmissions = Seq()
  }

  def addCourseAdmission(courseAdmission: CourseAdmission): Unit = {
    courseAdmissions :+= courseAdmission
  }

  def addExamAdmission(examAdmission: ExamAdmission): Unit = {
    examAdmissions :+= examAdmission
  }

  /** Returns course admissions */
  override def getCourseAdmissions(username: Option[String], courseId: Option[String], moduleId: Option[String]): ServiceCall[NotUsed, Seq[CourseAdmission]] = ServiceCall {
    _ => Future.successful(courseAdmissions)
  }

  /** Gets a proposal for adding a course admission */
  override def getProposalAddCourseAdmission: ServiceCall[CourseAdmission, UnsignedProposal] = ServiceCall {
    _ => Future.successful(UnsignedProposal(""))
  }

  /** Returns exam admissions */
  override def getExamAdmissions(username: Option[String], admissionIDs: Option[String], examIDs: Option[String]): ServiceCall[NotUsed, Seq[ExamAdmission]] = ServiceCall {
    _ => Future.successful(examAdmissions)
  }

  /** Gets a proposal for adding a exam admission */
  override def getProposalAddExamAdmission: ServiceCall[ExamAdmission, UnsignedProposal] = ServiceCall {
    _ => Future.successful(UnsignedProposal(""))
  }

  /** Gets a proposal for dropping a admission */
  override def getProposalDropAdmission: ServiceCall[DropAdmission, UnsignedProposal] = ServiceCall {
    _ => Future.successful(UnsignedProposal(""))
  }


  /** Allows GET */
  override def allowedGet: ServiceCall[NotUsed, Done] = ServiceCall {
    _ => Future.successful(Done)
  }

  /** Allows POST */
  override def allowedPost: ServiceCall[NotUsed, Done] = ServiceCall {
    _ => Future.successful(Done)
  }

  /** Get the version of the Hyperledger API and the version of the chaincode the service uses */
  override def getHlfVersions: ServiceCall[NotUsed, JsonHyperledgerVersion] = ServiceCall {
    _ => Future.successful(JsonHyperledgerVersion("undefined", "undefined"))
  }

  /** This Methods needs to allow a GET-Method */
  override def allowVersionNumber: ServiceCall[NotUsed, Done] = ServiceCall {
    _ => Future.successful(Done)
  }
}
