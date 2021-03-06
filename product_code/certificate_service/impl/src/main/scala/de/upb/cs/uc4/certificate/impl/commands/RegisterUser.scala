package de.upb.cs.uc4.certificate.impl.commands

import akka.actor.typed.ActorRef
import de.upb.cs.uc4.shared.server.messages.Confirmation

case class RegisterUser(username: String, enrollmentId: String, secret: String, replyTo: ActorRef[Confirmation]) extends CertificateCommand
