package de.upb.cs.uc4.matriculation.impl.commands

import akka.actor.typed.ActorRef
import akka.pattern.StatusReply
import de.upb.cs.uc4.hyperledger.impl.ProposalWrapper
import de.upb.cs.uc4.hyperledger.impl.commands.HyperledgerProposalCommand
import de.upb.cs.uc4.matriculation.model.ImmatriculationData

case class GetProposalForAddMatriculationData(certificate: String, data: ImmatriculationData, replyTo: ActorRef[StatusReply[ProposalWrapper]])
  extends HyperledgerProposalCommand
