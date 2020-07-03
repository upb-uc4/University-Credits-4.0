package de.upb.cs.uc4.user.impl

import com.lightbend.lagom.scaladsl.playjson.JsonSerializer
import de.upb.cs.uc4.shared.SharedSerializerRegistry
import de.upb.cs.uc4.user.impl.actor.UserState
import de.upb.cs.uc4.user.impl.events.{OnUserCreate, OnUserDelete, OnUserUpdate}

import scala.collection.immutable.Seq


/**
  * Akka serialization, used by both persistence and remoting, needs to have
  * serializers registered for every type serialized or deserialized. While it's
  * possible to use any serializer you want for Akka messages, out of the box
  * Lagom provides support for JSON, via this registry abstraction.
  *
  * The serializers are registered here, and then provided to Lagom in the
  * application loader.
  */
object UserSerializerRegistry extends SharedSerializerRegistry {
  override def customSerializers: Seq[JsonSerializer[_]] = Seq(
    // state and events can use play-json, but commands should use jackson because of ActorRef[T] (see application.conf)
    //States
    JsonSerializer[UserState],
    //Events
    JsonSerializer[OnUserCreate],
    JsonSerializer[OnUserDelete],
    JsonSerializer[OnUserUpdate],
  )
}