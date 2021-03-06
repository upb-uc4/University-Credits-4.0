package de.upb.cs.uc4.matriculation.impl.actor

import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.pattern.StatusReply
import com.typesafe.config.Config
import de.upb.cs.uc4.hyperledger.connections.cases.ConnectionMatriculation
import de.upb.cs.uc4.hyperledger.connections.traits.ConnectionMatriculationTrait
import de.upb.cs.uc4.hyperledger.impl.commands.{ HyperledgerBaseCommand, HyperledgerCommand, HyperledgerReadCommand, HyperledgerWriteCommand }
import de.upb.cs.uc4.hyperledger.impl.{ HyperledgerActor, HyperledgerActorObject, ProposalWrapper }
import de.upb.cs.uc4.matriculation.impl.commands._
import de.upb.cs.uc4.matriculation.model.ImmatriculationData
import de.upb.cs.uc4.shared.client.JsonUtility._

class MatriculationBehaviour(val config: Config) extends HyperledgerActor[ConnectionMatriculationTrait] {

  /** Creates the connection to the chaincode */
  override protected def createConnection: ConnectionMatriculationTrait =
    ConnectionMatriculation(adminUsername, channel, chaincode, walletPath, networkDescriptionPath)

  /** Gets called every time when the actor receives a command
    * Errors which this method will thrown will be handled accordingly
    * if the command implements [[HyperledgerReadCommand]] or the
    * [[HyperledgerWriteCommand]].
    *
    * @param connection the current active connection
    * @param command which should get executed
    */
  override protected def applyCommand(connection: ConnectionMatriculationTrait, command: HyperledgerCommand[_]): Unit = command match {

    case GetProposalForAddEntriesToMatriculationData(certificate, enrollmentId, matriculation, replyTo) =>
      replyTo ! StatusReply.success(ProposalWrapper(connection.getProposalAddEntriesToMatriculationData(certificate, enrollmentId = enrollmentId, subjectMatriculationList = matriculation.toJson)))

    case GetProposalForAddMatriculationData(certificate, data, replyTo) =>
      replyTo ! StatusReply.success(ProposalWrapper(connection.getProposalAddMatriculationData(certificate, jSonMatriculationData = data.toJson)))

    case GetMatriculationData(matriculationId, replyTo) =>
      replyTo ! StatusReply.success(connection.getMatriculationData(matriculationId).fromJson[ImmatriculationData])
  }

  /** The companion object */
  override val companionObject: HyperledgerActorObject = MatriculationBehaviour
}

object MatriculationBehaviour extends HyperledgerActorObject {
  /** The EntityTypeKey of this actor */
  override val typeKey: EntityTypeKey[HyperledgerBaseCommand] = EntityTypeKey[HyperledgerBaseCommand]("uc4matriculation")
}
