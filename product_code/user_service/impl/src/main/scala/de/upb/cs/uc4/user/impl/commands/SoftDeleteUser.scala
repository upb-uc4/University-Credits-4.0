package de.upb.cs.uc4.user.impl.commands

import akka.actor.typed.ActorRef
import de.upb.cs.uc4.shared.server.messages.Confirmation

case class SoftDeleteUser(replyTo: ActorRef[Confirmation]) extends UserCommand
