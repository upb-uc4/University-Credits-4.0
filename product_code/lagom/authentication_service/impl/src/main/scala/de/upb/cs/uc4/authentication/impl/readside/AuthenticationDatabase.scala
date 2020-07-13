package de.upb.cs.uc4.authentication.impl.readside

import akka.Done
import de.upb.cs.uc4.shared.server.Hashing
import de.upb.cs.uc4.user.model.user.AuthenticationUser
import slick.dbio.Effect
import slick.jdbc.PostgresProfile.api._
import slick.lifted.ProvenShape
import slick.sql.FixedSqlAction

import scala.concurrent.{ExecutionContext, Future}

class AuthenticationDatabase(database: Database)(implicit ec: ExecutionContext) {

  class AuthenticationTable(tag: Tag) extends Table[String](tag, "uc4AuthenticationTable") {
    def username: Rep[String] = column[String]("username", O.PrimaryKey)

    override def * : ProvenShape[String] = username <>
      (username => username, (username: String) => Some(username))
  }

  val authenticationUsers = TableQuery[AuthenticationTable]

  def createTable(): FixedSqlAction[Unit, NoStream, Effect.Schema] = authenticationUsers.schema.createIfNotExists

  def getAll: Future[Seq[String]] =
    database.run(findAllQuery)

  def addAuthenticationUser(user: AuthenticationUser): DBIO[Done] = {
    findByUsernameQuery(user.username)
      .flatMap {
        case None => authenticationUsers += Hashing.sha256(user.username)
        case _    => DBIO.successful(Done)
      }
      .map(_ => Done)
      .transactionally
  }

  def removeAuthenticationUser(username: String): DBIO[Done] = {
    authenticationUsers
      .filter(_.username === Hashing.sha256(username))
      .delete
      .map(_ => Done)
      .transactionally
  }

  private def findAllQuery: DBIO[Seq[String]] = authenticationUsers.result

  private def findByUsernameQuery(username: String): DBIO[Option[String]] =
    authenticationUsers
      .filter(_.username === Hashing.sha256(username))
      .result
      .headOption
}
