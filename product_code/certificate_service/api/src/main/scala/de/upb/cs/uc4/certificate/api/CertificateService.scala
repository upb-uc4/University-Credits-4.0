package de.upb.cs.uc4.certificate.api

import akka.{ Done, NotUsed }
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{ Descriptor, Service, ServiceCall }
import de.upb.cs.uc4.certificate.model.{ EncryptedPrivateKey, JsonCertificate, JsonEnrollmentId, PostMessageCSR }
import de.upb.cs.uc4.hyperledger.api.UC4HyperledgerService
import de.upb.cs.uc4.shared.client.JsonUsername
import de.upb.cs.uc4.shared.client.kafka.EncryptionContainer
import de.upb.cs.uc4.shared.client.message_serialization.CustomMessageSerializer

/** The CertificateService interface.
  *
  * This describes everything that Lagom needs to know about how to serve and
  * consume the CertificateService.
  */
trait CertificateService extends UC4HyperledgerService {
  /** Prefix for the path for the endpoints, a name/identifier for the service */
  override val pathPrefix = "/certificate-management"
  /** The name of the service */
  override val name = "certificate"

  /** Forwards the certificate signing request from the given user */
  def setCertificate(username: String): ServiceCall[PostMessageCSR, JsonCertificate]

  /** Returns the certificate of the given user */
  def getCertificate(username: String): ServiceCall[NotUsed, JsonCertificate]

  /** Returns the enrollment id of the given user */
  def getEnrollmentId(username: String): ServiceCall[NotUsed, JsonEnrollmentId]

  /** Returns the encrypted private key of the given user */
  def getPrivateKey(username: String): ServiceCall[NotUsed, EncryptedPrivateKey]

  /** Returns the username that matches the given enrollmentId */
  def getUsername(enrollmentId: String): ServiceCall[NotUsed, JsonUsername]

  // OPTIONS
  /** Allows POST */
  def allowedPost: ServiceCall[NotUsed, Done]

  /** Allows GET */
  def allowedGet: ServiceCall[NotUsed, Done]

  // TOPICS

  /** Publishes every user that is registered at hyperledger */
  def userEnrollmentTopic(): Topic[EncryptionContainer]

  final override def descriptor: Descriptor = {
    import Service._
    super.descriptor
      .addCalls(
        restCall(Method.POST, pathPrefix + "/certificates/:username", setCertificate _)(CustomMessageSerializer.jsValueFormatMessageSerializer, CustomMessageSerializer.jsValueFormatMessageSerializer),
        restCall(Method.GET, pathPrefix + "/certificates/:username/certificate", getCertificate _),
        restCall(Method.GET, pathPrefix + "/certificates/:username/enrollmentId", getEnrollmentId _),
        restCall(Method.GET, pathPrefix + "/certificates/:username/privateKey", getPrivateKey _),
        restCall(Method.GET, pathPrefix + "/certificates/:enrollmentId/username", getUsername _),
        restCall(Method.OPTIONS, pathPrefix + "/certificates/:username", allowedPost _),
        restCall(Method.OPTIONS, pathPrefix + "/certificates/:username/certificate", allowedGet _),
        restCall(Method.OPTIONS, pathPrefix + "/certificates/:username/enrollmentId", allowedGet _),
        restCall(Method.OPTIONS, pathPrefix + "/certificates/:username/privateKey", allowedGet _),
        restCall(Method.OPTIONS, pathPrefix + "/certificates/:enrollmentId/username", allowedGet _)
      )
      .addTopics(
        topic(CertificateService.REGISTRATION_TOPIC_NAME, userEnrollmentTopic _)
      )
      .withAutoAcl(true)
  }
}

object CertificateService {
  val REGISTRATION_TOPIC_NAME = "registration"
}