package de.upb.cs.uc4.authentication.impl.commands

import akka.actor.typed.ActorRef
import de.upb.cs.uc4.shared.server.messages.Confirmation
import de.upb.cs.uc4.user.model.user.AuthenticationUser

case class SetAuthentication(user: AuthenticationUser, replyTo: ActorRef[Confirmation]) extends AuthenticationCommand
