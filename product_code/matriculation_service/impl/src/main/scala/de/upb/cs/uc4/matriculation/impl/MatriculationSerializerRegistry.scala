package de.upb.cs.uc4.matriculation.impl

import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import de.upb.cs.uc4.hyperledger.api.model.{ SignedProposal, SignedTransaction, UnsignedProposal, UnsignedTransaction }
import de.upb.cs.uc4.matriculation.model.{ ImmatriculationData, SubjectMatriculation }
import de.upb.cs.uc4.shared.server.SharedSerializerRegistry

import scala.collection.immutable.Seq

/** Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object MatriculationSerializerRegistry extends SharedSerializerRegistry {
  override def customSerializers: Seq[JsonSerializer[_]] = Seq( // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    //Data
    JsonSerializer[ImmatriculationData],
    JsonSerializer[SubjectMatriculation],
    JsonSerializer[UnsignedProposal],
    JsonSerializer[UnsignedTransaction],
    JsonSerializer[SignedProposal],
    JsonSerializer[SignedTransaction]
  )
}
