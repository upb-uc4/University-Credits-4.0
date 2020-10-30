package de.upb.cs.uc4.matriculation.impl

import java.nio.charset.StandardCharsets
import java.util.{ Base64, Calendar }

import com.google.protobuf.ByteString
import com.lightbend.lagom.scaladsl.api.transport.RequestHeader
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.ServiceTest
import de.upb.cs.uc4.authentication.model.AuthenticationRole
import de.upb.cs.uc4.certificate.CertificateServiceStub
import de.upb.cs.uc4.hyperledger.HyperledgerUtils.JsonUtil.ToJsonUtil
import de.upb.cs.uc4.hyperledger.connections.traits.ConnectionMatriculationTrait
import de.upb.cs.uc4.hyperledger.exceptions.traits.TransactionExceptionTrait
import de.upb.cs.uc4.matriculation.api.MatriculationService
import de.upb.cs.uc4.matriculation.impl.actor.MatriculationBehaviour
import de.upb.cs.uc4.matriculation.model.{ ImmatriculationData, PutMessageMatriculation, SubjectMatriculation }
import de.upb.cs.uc4.shared.client.SignedTransactionProposal
import de.upb.cs.uc4.shared.client.exceptions.UC4Exception
import de.upb.cs.uc4.user.{ DefaultTestUsers, UserServiceStub }
import io.jsonwebtoken.{ Jwts, SignatureAlgorithm }
import org.hyperledger.fabric.gateway.impl.{ ContractImpl, GatewayImpl }
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpec
import play.api.libs.json.Json

import scala.language.reflectiveCalls

/** Tests for the MatriculationService */
class MatriculationServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll with DefaultTestUsers {

  private val server = ServiceTest.startServer(
    ServiceTest.defaultSetup.withCluster()
  ) { ctx =>
      new MatriculationApplication(ctx) with LocalServiceLocator {
        override lazy val userService: UserServiceStub = new UserServiceStub
        override lazy val certificateService: CertificateServiceStub = new CertificateServiceStub

        userService.resetToDefaults()

        var jsonStringList: Seq[String] = List()

        override def createActorFactory: MatriculationBehaviour = new MatriculationBehaviour(config) {
          override protected def createConnection: ConnectionMatriculationTrait = new ConnectionMatriculationTrait() {
            override def getMatriculationData(matId: String): String = {
              val mat = jsonStringList.find(json => json.contains(matId))
              if (mat.isDefined) {
                mat.get
              }
              else {
                throw new TransactionExceptionTrait() {
                  override val transactionName: String = "getMatriculationData"
                  override val payload: String =
                    """{
                    |  "type": "HLNotFound",
                    |  "title": "There is no MatriculationData for the given enrollmentId."
                    |}""".stripMargin
                }
              }
            }

            override def getProposalAddMatriculationData(jSonMatriculationData: String): Array[Byte] =
              jSonMatriculationData.getBytes

            override def getProposalAddEntriesToMatriculationData(enrollmentId: String, subjectMatriculationList: String): Array[Byte] =
              (enrollmentId + "#" + subjectMatriculationList).getBytes()

            override def submitSignedProposal(proposalBytes: Array[Byte], signature: ByteString): String = {
              new String(proposalBytes, StandardCharsets.UTF_8) match {
                case s"$enrollmentId#$subjectMatriculationList" =>
                  var data = Json.parse(jsonStringList.find(json => json.contains(enrollmentId)).get).as[ImmatriculationData]
                  val matriculationList = Json.parse(subjectMatriculationList).as[Seq[SubjectMatriculation]]

                  if (matriculationList.isEmpty) {
                    throw new TransactionExceptionTrait() {
                      override val transactionName: String = "addEntriesToMatriculationData"
                      override val payload: String =
                        """{
                          |  "type": "HLUnprocessableEntity",
                          |  "title": "The following parameters do not conform to the specified format.",
                          |  "invalidParams":[
                          |     {
                          |       "name":"matriculations",
                          |       "reason":"Matriculation status must not be empty"
                          |     }
                          |  ]
                          |}""".stripMargin
                    }
                  }

                  for (subjectMatriculation: SubjectMatriculation <- matriculationList) {
                    val optSubject = data.matriculationStatus.find(_.fieldOfStudy == subjectMatriculation.fieldOfStudy)

                    if (optSubject.isDefined) {
                      data = data.copy(matriculationStatus = data.matriculationStatus.map { subject =>
                        if (subject != optSubject.get) {
                          subject
                        }
                        else {
                          subject.copy(semesters = (subject.semesters :++ subjectMatriculation.semesters).distinct)
                        }
                      })
                    }
                    else {
                      data = data.copy(matriculationStatus = data.matriculationStatus :+ SubjectMatriculation(subjectMatriculation.fieldOfStudy, subjectMatriculation.semesters))
                    }
                  }

                  jsonStringList = jsonStringList.filter(json => !json.contains(enrollmentId)) :+ Json.stringify(Json.toJson(data))
                  ""

                case s"$jsonMatriculationData" =>
                  val matriculationData = Json.parse(jsonMatriculationData).as[ImmatriculationData]
                  if (matriculationData.matriculationStatus.isEmpty) {
                    throw new TransactionExceptionTrait() {
                      override val transactionName: String = "addEntriesToMatriculationData"
                      override val payload: String =
                        """{
                          |  "type": "HLUnprocessableEntity",
                          |  "title": "The following parameters do not conform to the specified format.",
                          |  "invalidParams":[
                          |     {
                          |       "name":"matriculations",
                          |       "reason":"Matriculation status must not be empty"
                          |     }
                          |  ]
                          |}""".stripMargin
                    }
                  }
                  jsonStringList :+= jsonMatriculationData
                  ""
              }
            }

            override def addMatriculationData(jsonMatriculationData: String): String = ""
            override def addEntriesToMatriculationData(enrollmentId: String, subjectMatriculationList: String): String = ""
            override def updateMatriculationData(jSonMatriculationData: String): String = ""
            override def getProposalUpdateMatriculationData(jSonMatriculationData: String): Array[Byte] = Array.emptyByteArray
            override def getProposalGetMatriculationData(enrollmentId: String): Array[Byte] = Array.emptyByteArray
            override val contractName: String = ""
            override val contract: ContractImpl = null
            override val gateway: GatewayImpl = null
            override val draftContract: ContractImpl = null
            override val draftGateway: GatewayImpl = null
          }
        }
      }
    }

  val client: MatriculationService = server.serviceClient.implement[MatriculationService]
  val certificate: CertificateServiceStub = server.application.certificateService

  override protected def afterAll(): Unit = server.stop()

  def createLoginToken(username: String = "admin"): String = {

    var role = AuthenticationRole.Admin

    if (username.contains("student")) {
      role = AuthenticationRole.Student
    }
    else if (username.contains("lecturer")) {
      role = AuthenticationRole.Lecturer
    }

    val time = Calendar.getInstance()
    time.add(Calendar.DATE, 1)

    val token =
      Jwts.builder()
        .setSubject("login")
        .setExpiration(time.getTime)
        .claim("username", username)
        .claim("authenticationRole", role.toString)
        .signWith(SignatureAlgorithm.HS256, "changeme")
        .compact()

    s"login=$token"
  }

  def addAuthorizationHeader(username: String = "admin"): RequestHeader => RequestHeader =
    header => header.withHeader("Cookie", createLoginToken(username))

  def prepare(matriculations: Seq[ImmatriculationData]): Unit = {
    server.application.jsonStringList ++= matriculations.map(_.toJson)
  }

  def cleanup(): Unit = {
    server.application.jsonStringList = List()
  }

  private def createSingleMatriculation(field: String, semester: String) = PutMessageMatriculation(Seq(SubjectMatriculation(field, Seq(semester))))
  private def asString(unsignedProposal: String) = new String(Base64.getDecoder.decode(unsignedProposal), StandardCharsets.UTF_8)

  "MatriculationService service" should {

    "add matriculation data for a student" in {
      val message = createSingleMatriculation("Computer Science", "SS2020")
      certificate.setup(student0.username)
      certificate.getEnrollmentId(student0.username).invoke().flatMap { jsonId =>
        val data = ImmatriculationData(jsonId.id, message.matriculation)

        client.getProposalAddMatriculationData(student0.username).handleRequestHeader(addAuthorizationHeader(student0.username))
          .invoke(message).map { proposal =>
            asString(proposal.unsignedProposal) should ===(data.toJson)
          }.andThen {
            case _ => cleanup()
          }
      }
    }

    "not add empty matriculation data for a student" in {
      client.getProposalAddMatriculationData(student0.username).handleRequestHeader(addAuthorizationHeader(student0.username))
        .invoke(createSingleMatriculation("", "")).failed.map { answer =>
          answer.asInstanceOf[UC4Exception].errorCode.http should ===(422)
        }.andThen {
          case _ => cleanup()
        }
    }

    "extend matriculation data of an already existing field of study" in {
      certificate.setup(student0.username)
      certificate.getEnrollmentId(student0.username).invoke().flatMap { jsonId =>
        prepare(Seq(
          ImmatriculationData(
            jsonId.id,
            Seq(SubjectMatriculation("Computer Science", Seq("SS2020")))
          )
        ))
        client.getProposalAddMatriculationData(student0.username).handleRequestHeader(addAuthorizationHeader(student0.username))
          .invoke(createSingleMatriculation("Computer Science", "WS2020/21")).flatMap {
            proposal =>
              client.addMatriculationData(student0.username).handleRequestHeader(addAuthorizationHeader(student0.username))
                .invoke(SignedTransactionProposal(proposal.unsignedProposal, "c2lnbmVk")).flatMap { _ =>
                  client.getMatriculationData(student0.username).handleRequestHeader(addAuthorizationHeader(student0.username)).invoke().map {
                    answer =>
                      answer.matriculationStatus should contain theSameElementsAs
                        Seq(SubjectMatriculation("Computer Science", Seq("SS2020", "WS2020/21")))
                  }
                }
          }.andThen {
            case _ => cleanup()
          }
      }
    }

    "extend matriculation data of a non-existing field of study" in {
      certificate.setup(student0.username)
      certificate.getEnrollmentId(student0.username).invoke().flatMap { jsonId =>
        prepare(Seq(
          ImmatriculationData(
            jsonId.id,
            Seq(SubjectMatriculation("Computer Science", Seq("SS2020", "WS2020/21")))
          )
        ))
        client.getProposalAddMatriculationData(student0.username).handleRequestHeader(addAuthorizationHeader(student0.username))
          .invoke(createSingleMatriculation("Mathematics", "WS2021/22")).flatMap { proposal =>
            client.addMatriculationData(student0.username).handleRequestHeader(addAuthorizationHeader(student0.username))
              .invoke(SignedTransactionProposal(proposal.unsignedProposal, "c2lnbmVk")).flatMap { _ =>
                client.getMatriculationData(student0.username).handleRequestHeader(addAuthorizationHeader(student0.username)).invoke().map {
                  answer =>
                    answer.matriculationStatus should contain theSameElementsAs
                      Seq(
                        SubjectMatriculation("Computer Science", Seq("SS2020", "WS2020/21")),
                        SubjectMatriculation("Mathematics", Seq("WS2021/22"))
                      )
                }
              }
          }.andThen {
            case _ => cleanup()
          }
      }
    }
  }
}
