package de.upb.cs.uc4.hyperledger.impl.commands

import akka.Done
import akka.actor.typed.ActorRef

import scala.util.Try

case class Write(transactionId: String, params: Seq[String], replyTo: ActorRef[Try[Done]]) extends HyperLedgerCommand
