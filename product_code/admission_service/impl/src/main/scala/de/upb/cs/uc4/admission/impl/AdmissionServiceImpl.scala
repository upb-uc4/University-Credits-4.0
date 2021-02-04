package de.upb.cs.uc4.admission.impl

import akka.cluster.sharding.typed.scaladsl.{ ClusterSharding, EntityRef }
import akka.stream.Materializer
import akka.util.Timeout
import akka.{ Done, NotUsed }
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.{ MessageProtocol, RequestHeader, ResponseHeader }
import com.lightbend.lagom.scaladsl.server.ServerServiceCall
import com.typesafe.config.Config
import de.upb.cs.uc4.admission.api.AdmissionService
import de.upb.cs.uc4.admission.impl.actor.{ AdmissionBehaviour, AdmissionsWrapper }
import de.upb.cs.uc4.admission.impl.commands.{ GetCourseAdmissions, GetExamAdmissions, GetProposalForAddAdmission, GetProposalForDropAdmission }
import de.upb.cs.uc4.admission.model.{ AbstractAdmission, CourseAdmission, DropAdmission, ExamAdmission }
import de.upb.cs.uc4.authentication.model.AuthenticationRole
import de.upb.cs.uc4.certificate.api.CertificateService
import de.upb.cs.uc4.course.api.CourseService
import de.upb.cs.uc4.examreg.api.ExamregService
import de.upb.cs.uc4.hyperledger.api.model.{ JsonHyperledgerVersion, UnsignedProposal }
import de.upb.cs.uc4.hyperledger.impl.commands.HyperledgerBaseCommand
import de.upb.cs.uc4.hyperledger.impl.{ HyperledgerUtils, ProposalWrapper }
import de.upb.cs.uc4.matriculation.api.MatriculationService
import de.upb.cs.uc4.operation.api.OperationService
import de.upb.cs.uc4.operation.model.JsonOperationId
import de.upb.cs.uc4.shared.client.exceptions._
import de.upb.cs.uc4.shared.server.ServiceCallFactory._
import org.slf4j.{ Logger, LoggerFactory }
import play.api.Environment

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext, Future, TimeoutException }

/** Implementation of the MatriculationService */
class AdmissionServiceImpl(
    clusterSharding: ClusterSharding,
    matriculationService: MatriculationService,
    examregService: ExamregService,
    courseService: CourseService,
    certificateService: CertificateService,
    operationService: OperationService,
    override val environment: Environment
)(implicit ec: ExecutionContext, config: Config, materializer: Materializer)
  extends AdmissionService {

  /** Looks up the entity for the given ID */
  private def entityRef: EntityRef[HyperledgerBaseCommand] =
    clusterSharding.entityRefFor(AdmissionBehaviour.typeKey, AdmissionBehaviour.entityId)

  implicit val timeout: Timeout = Timeout(config.getInt("uc4.timeouts.hyperledger").milliseconds)

  lazy val validationTimeout: FiniteDuration = config.getInt("uc4.timeouts.validation").milliseconds

  private final val log: Logger = LoggerFactory.getLogger(classOf[AdmissionServiceImpl])

  /** Returns course admissions */
  override def getCourseAdmissions(username: Option[String], courseId: Option[String], moduleId: Option[String]): ServiceCall[NotUsed, Seq[CourseAdmission]] =
    identifiedAuthenticated(AuthenticationRole.All: _*) { (authUser, role) =>
      ServerServiceCall { (header, _) =>

        val future = role match {
          case AuthenticationRole.Admin =>
            Future.successful(Done)

          case AuthenticationRole.Student if username.isDefined && username.get.trim == authUser =>
            Future.successful(Done)
          case AuthenticationRole.Student =>
            throw UC4Exception.OwnerMismatch

          case AuthenticationRole.Lecturer if courseId.isDefined =>
            courseService.findCourseByCourseId(courseId.get).handleRequestHeader(addAuthenticationHeader(header)).invoke().map { course =>
              if (course.lecturerId != authUser) {
                throw UC4Exception.OwnerMismatch
              }
              Done
            }
          case _ =>
            throw UC4Exception.NotEnoughPrivileges
        }

        val enrollmentFuture = if (username.isDefined) {
          certificateService.getEnrollmentId(username.get).handleRequestHeader(addAuthenticationHeader(header)).invoke().map(jsonId => Some(jsonId.id))
        }
        else {
          Future.successful(None)
        }

        future.flatMap { _ =>
          enrollmentFuture.flatMap { enrollmentId =>
            entityRef.askWithStatus[AdmissionsWrapper](replyTo => GetCourseAdmissions(enrollmentId, courseId, moduleId, replyTo)).map {
              admissionsWrapper: AdmissionsWrapper =>
                admissionsWrapper.admissions.map {
                  _.asInstanceOf[CourseAdmission]
                }
            }.map {
              courseAdmissions =>
                createETagHeader(header, courseAdmissions)
            }.recover(handleException("Get course admission"))
          }
        }
      }
    }

  /** Returns exam admissions */
  override def getExamAdmissions(username: Option[String], admissionIDs: Option[String], examIDs: Option[String]): ServiceCall[NotUsed, Seq[ExamAdmission]] =
    identifiedAuthenticated(AuthenticationRole.All: _*) { (authUser, role) =>
      ServerServiceCall { (header, _) =>

        if (role != AuthenticationRole.Admin && (username.isEmpty || username.get.trim != authUser)) {
          throw UC4Exception.OwnerMismatch
        }

        val optEnrollmentId = username match {
          case Some(id) =>
            certificateService.getEnrollmentId(id).handleRequestHeader(addAuthenticationHeader(header)).invoke().map(jsonId => Some(jsonId.id))
          case None =>
            Future.successful(None)
        }

        optEnrollmentId.flatMap { id =>
          entityRef.askWithStatus[AdmissionsWrapper](replyTo => GetExamAdmissions(id, admissionIDs.map(_.trim.split(",")), examIDs.map(_.trim.split(",")), replyTo)).map {
            admissions =>
              (ResponseHeader(200, MessageProtocol.empty, List()), admissions.admissions.map(_.asInstanceOf[ExamAdmission]))
          }.recover(handleException("getExamAdmission proposal failed"))
        }
      }
    }

  /** Gets a proposal for adding a course admission */
  override def getProposalAddAdmission: ServiceCall[AbstractAdmission, UnsignedProposal] = identifiedAuthenticated(AuthenticationRole.Student) {
    (authUser, _) =>
      {
        ServerServiceCall { (header, admissionRaw) =>
          val admissionTrimmed = admissionRaw.trim

          val validationList = try {
            Await.result(admissionTrimmed.validateOnCreation, validationTimeout)
          }
          catch {
            case _: TimeoutException => throw UC4Exception.ValidationTimeout
            case e: Exception        => throw UC4Exception.InternalServerError("Validation Error", e.getMessage)
          }

          certificateService.getEnrollmentId(authUser).handleRequestHeader(addAuthenticationHeader(header)).invoke().flatMap {
            jsonId =>

              val admission = admissionTrimmed.copyAdmission(enrollmentId = jsonId.id)

              admission match {
                case courseAdmission: CourseAdmission => getProposalAddCourseAdmission(authUser, header, validationList, courseAdmission)
                case examAdmission: ExamAdmission     => getProposalAddExamAdmission(authUser, header, validationList, examAdmission)
              }
          }
        }
      }
  }

  private def getProposalAddCourseAdmission(authUser: String, header: RequestHeader, validationOnCreateList: Seq[SimpleError], courseAdmission: CourseAdmission): Future[(ResponseHeader, UnsignedProposal)] = {

    var validationList = validationOnCreateList

    certificateService.getEnrollmentId(authUser).handleRequestHeader(addAuthenticationHeader(header)).invoke().flatMap { jsonId =>

      val courseAdmissionFinalized = courseAdmission.copy(enrollmentId = jsonId.id, timestamp = LocalDateTime.now.format(DateTimeFormatter.ISO_DATE_TIME))

      courseService.findCourseByCourseId(courseAdmissionFinalized.courseId).handleRequestHeader(addAuthenticationHeader(header)).invoke().flatMap {
        course =>
          if (!course.moduleIds.contains(courseAdmissionFinalized.moduleId)) {
            validationList :+= SimpleError("moduleId", "CourseId can not be attributed to module with the given moduleId.")
            validationList :+= SimpleError("courseId", "The module with the given moduleId can not be attributed to the course with the given courseId.")
          }

          matriculationService.getMatriculationData(authUser).handleRequestHeader(addAuthenticationHeader(header)).invoke().flatMap {
            data =>
              examregService.getExaminationRegulations(Some(data.matriculationStatus.map(_.fieldOfStudy).mkString(",")), Some(true))
                .handleRequestHeader(addAuthenticationHeader(header)).invoke().flatMap {
                  regulations =>
                    if (!regulations.flatMap(_.modules).map(_.id).contains(courseAdmissionFinalized.moduleId) && !validationList.map(_.name).contains("moduleId")) {
                      validationList :+= SimpleError("moduleId", "The given moduleId can not be attributed to an active exam regulation.")
                    }

                    if (validationList.nonEmpty) {
                      throw new UC4NonCriticalException(422, DetailedError(ErrorType.Validation, validationList))
                    }
                    else {
                      certificateService.getCertificate(authUser).handleRequestHeader(addAuthenticationHeader(header)).invoke().flatMap {
                        certificate =>
                          entityRef.askWithStatus[ProposalWrapper](replyTo => GetProposalForAddAdmission(certificate.certificate, courseAdmissionFinalized, replyTo)).map {
                            proposalWrapper =>
                              operationService.addToWatchList(authUser).handleRequestHeader(addAuthenticationHeader(header)).invoke(JsonOperationId(proposalWrapper.operationId))
                                .recover {
                                  case ex: UC4Exception if ex.possibleErrorResponse.`type` == ErrorType.OwnerMismatch =>
                                  case throwable: Throwable =>
                                    log.error("Exception in addToWatchlist addCourseAdmission", throwable)
                                }
                              (ResponseHeader(200, MessageProtocol.empty, List()), UnsignedProposal(proposalWrapper.proposal))
                          }.recover(handleException("Creation of add courseAdmission proposal failed"))
                      }
                    }
                }
          }
      }
    }
  }

  private def getProposalAddExamAdmission(authUser: String, header: RequestHeader, validationList: Seq[SimpleError], examAdmission: ExamAdmission): Future[(ResponseHeader, UnsignedProposal)] = {

    if (validationList.nonEmpty) {
      throw new UC4NonCriticalException(422, DetailedError(ErrorType.Validation, validationList))
    }
    else {
      certificateService.getCertificate(authUser).handleRequestHeader(addAuthenticationHeader(header)).invoke().flatMap {
        certificate =>
          entityRef.askWithStatus[ProposalWrapper](replyTo => GetProposalForAddAdmission(certificate.certificate, examAdmission, replyTo)).map {
            proposalWrapper =>
              operationService.addToWatchList(authUser).handleRequestHeader(addAuthenticationHeader(header)).invoke(JsonOperationId(proposalWrapper.operationId))
                .recover {
                  case ex: UC4Exception if ex.possibleErrorResponse.`type` == ErrorType.OwnerMismatch =>
                  case throwable: Throwable =>
                    log.error("Exception in addToWatchlist addExamAdmission", throwable)
                }
              (ResponseHeader(200, MessageProtocol.empty, List()), UnsignedProposal(proposalWrapper.proposal))
          }.recover(handleException("Creation of add examAdmission proposal failed"))
      }
    }
  }

  /** Gets a proposal for dropping a admission */
  override def getProposalDropAdmission: ServiceCall[DropAdmission, UnsignedProposal] = identifiedAuthenticated(AuthenticationRole.Student) {
    (authUser, _) =>
      ServerServiceCall {
        (header, dropAdmissionRaw) =>
          {
            val dropAdmission = dropAdmissionRaw.trim
            val validationList = try {
              Await.result(dropAdmission.validateOnCreation, validationTimeout)
            }
            catch {
              case _: TimeoutException => throw UC4Exception.ValidationTimeout
              case e: Exception        => throw UC4Exception.InternalServerError("Validation Error", e.getMessage)
            }

            if (validationList.nonEmpty) {
              throw new UC4NonCriticalException(422, DetailedError(ErrorType.Validation, validationList))
            }

            certificateService.getCertificate(authUser).handleRequestHeader(addAuthenticationHeader(header)).invoke().flatMap { certificate =>
              entityRef.askWithStatus[ProposalWrapper](replyTo => GetProposalForDropAdmission(certificate.certificate, dropAdmission, replyTo)).map {
                proposalWrapper =>
                  operationService.addToWatchList(authUser).handleRequestHeader(addAuthenticationHeader(header)).invoke(JsonOperationId(proposalWrapper.operationId))
                    .recover {
                      case ex: UC4Exception if ex.possibleErrorResponse.`type` == ErrorType.OwnerMismatch =>
                      case throwable: Throwable =>
                        log.error("Exception in addToWatchlist dropAdmission", throwable)
                    }
                  (ResponseHeader(200, MessageProtocol.empty, List()), UnsignedProposal(proposalWrapper.proposal))
              }.recover(handleException("Creation of drop Admission proposal failed"))
            }
          }
      }
  }

  /** Allows GET */
  override def allowedGet: ServiceCall[NotUsed, Done] = allowedMethodsCustom("GET")

  /** Allows POST */
  override def allowedPost: ServiceCall[NotUsed, Done] = allowedMethodsCustom("POST")

  /** This Methods needs to allow a GET-Method */
  override def allowVersionNumber: ServiceCall[NotUsed, Done] = allowedMethodsCustom("GET")

  /** Get the version of the Hyperledger API and the version of the chaincode the service uses */
  override def getHlfVersions: ServiceCall[NotUsed, JsonHyperledgerVersion] = ServiceCall { _ =>
    HyperledgerUtils.VersionUtil.createHyperledgerVersionResponse(entityRef)
  }
}
