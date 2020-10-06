package de.upb.cs.uc4.authentication.impl

import akka.Done
import akka.cluster.sharding.typed.scaladsl.Entity
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcPersistenceComponents
import com.lightbend.lagom.scaladsl.persistence.slick.SlickPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.JsonSerializerRegistry
import com.lightbend.lagom.scaladsl.server.{ LagomApplicationContext, LagomServer }
import com.softwaremill.macwire.wire
import de.upb.cs.uc4.authentication.api.AuthenticationService
import de.upb.cs.uc4.authentication.impl.actor.{ AuthenticationBehaviour, AuthenticationState }
import de.upb.cs.uc4.authentication.impl.commands.DeleteAuthentication
import de.upb.cs.uc4.authentication.impl.readside.{ AuthenticationDatabase, AuthenticationEventProcessor }
import de.upb.cs.uc4.authentication.model.JsonUsername
import de.upb.cs.uc4.shared.client.kafka.EncryptionContainer
import de.upb.cs.uc4.shared.server.kafka.KafkaEncryptionComponent
import de.upb.cs.uc4.shared.server.messages.Confirmation
import de.upb.cs.uc4.shared.server.{ Hashing, UC4Application }
import de.upb.cs.uc4.user.api.UserService
import play.api.db.HikariCPComponents

import scala.concurrent.Future
import scala.concurrent.duration._

abstract class AuthenticationApplication(context: LagomApplicationContext)
  extends UC4Application(context)
  with SlickPersistenceComponents
  with JdbcPersistenceComponents
  with HikariCPComponents
  with LagomKafkaComponents
  with KafkaEncryptionComponent {

  private implicit val timeout: Timeout = Timeout(5.seconds)

  // Create ReadSide
  lazy val database: AuthenticationDatabase = wire[AuthenticationDatabase]
  lazy val processor: AuthenticationEventProcessor = wire[AuthenticationEventProcessor]

  // Bind the service that this server provides
  override lazy val lagomServer: LagomServer = serverFor[AuthenticationService](wire[AuthenticationServiceImpl])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry: JsonSerializerRegistry = AuthenticationSerializerRegistry

  // Initialize the sharding of the Aggregate. The following starts the aggregate Behavior under
  // a given sharding entity typeKey.
  clusterSharding.init(
    Entity(AuthenticationState.typeKey)(
      entityContext => AuthenticationBehaviour.create(entityContext)
    )
  )

  lazy val userService: UserService = serviceClient.implement[UserService]

  userService
    .userDeletedTopic()
    .subscribe
    .atLeastOnce(
      Flow.fromFunction[EncryptionContainer, Future[Done]] { container =>
        val json = kafkaEncryptionUtility.decrypt[JsonUsername](container)
        clusterSharding.entityRefFor(AuthenticationState.typeKey, Hashing.sha256(json.username))
          .ask[Confirmation](replyTo => DeleteAuthentication(replyTo)).map(_ => Done)
      }
        .mapAsync(8)(done => done)
    )
}

object AuthenticationApplication {
  val offset: String = "UC4Authentication"
}

