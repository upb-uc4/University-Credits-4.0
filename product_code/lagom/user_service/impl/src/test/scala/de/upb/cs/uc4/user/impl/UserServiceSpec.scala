package de.upb.cs.uc4.user.impl

import java.util.Base64
import java.util.concurrent.TimeUnit

import akka.Done
import akka.stream.scaladsl.Source
import akka.stream.testkit.scaladsl.TestSink
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.api.transport.{NotFound, RequestHeader, TransportException}
import com.lightbend.lagom.scaladsl.server.LocalServiceLocator
import com.lightbend.lagom.scaladsl.testkit.{ServiceTest, TestTopicComponents}
import de.upb.cs.uc4.authentication.api.AuthenticationService
import de.upb.cs.uc4.authentication.model.AuthenticationRole
import de.upb.cs.uc4.user.api.UserService
import de.upb.cs.uc4.user.model.post.{PostMessageAdmin, PostMessageLecturer, PostMessageStudent}
import de.upb.cs.uc4.user.model.user.{Admin, AuthenticationUser, Lecturer, Student}
import de.upb.cs.uc4.user.model.{Address, JsonUsername, Role}
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Minutes, Span}
import org.scalatest.wordspec.AsyncWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

/** Tests for the CourseService
  * All tests need to be started in the defined order
  */
class UserServiceSpec extends AsyncWordSpec with Matchers with BeforeAndAfterAll with Eventually {

  private val server = ServiceTest.startServer(
    ServiceTest.defaultSetup
      .withJdbc()
  ) { ctx =>
    new UserApplication(ctx) with LocalServiceLocator with TestTopicComponents {
      override lazy val authenticationService: AuthenticationService =
        (_: String, _: String) => ServiceCall { _ => Future.successful("admin", AuthenticationRole.Admin) }
    }
  }

  val client: UserService = server.serviceClient.implement[UserService]
  val authenticationTopic: Source[AuthenticationUser, _] = client.userAuthenticationTopic().subscribe.atMostOnceSource
  val deletionTopic: Source[JsonUsername, _] = client.userDeletedTopic().subscribe.atMostOnceSource

  override protected def afterAll(): Unit = server.stop()

  def addAuthorizationHeader(): RequestHeader => RequestHeader = { header =>
    header.withHeader("Authorization", "Basic " + Base64.getEncoder.encodeToString("MOCK:MOCK".getBytes()))
  }

  //Test users
  val address: Address = Address("Deppenstraße", "42a", "1337", "Entenhausen", "Nimmerland")
  val authenticationUser: AuthenticationUser = AuthenticationUser("MOCK", "MOCK", AuthenticationRole.Admin)

  val student0: Student = Student("student0", Role.Student, address, "Hans", "Wurst", "Haesslich", "hans.wurst@mail.de", "1992-12-10", "IN", "421769", 9000, List())
  val lecturer0: Lecturer = Lecturer("lecturer0", Role.Lecturer, address, "Graf", "Wurst", "Haesslich", "graf.wurst@mail.de", "1996-12-11", "Ich bin bloed", "Genderstudies")
  val admin0: Admin = Admin("admin0", Role.Admin, address, "Dieter", "Wurst", "Haesslich", "dieter.wurst@mail.de", "1996-12-11")
  val admin1: Admin = Admin("lecturer0", Role.Admin, address, "Lola", "Wurst", "Haesslich", "lola.wurst@mail.de", "1996-12-11")


  /** Tests only working if the whole instance is started */
  "UserService service" should {

    "get all users with default users" in {
      eventually(timeout(Span(2, Minutes))) {
        client.getAllUsers.handleRequestHeader(addAuthorizationHeader()).invoke().map { answer =>

          answer.admins should have size 1
          answer.lecturer should have size 1
          answer.students should have size 1
        }
      }
    }

    "publish new AuthenticationUser" in {
      val authUser = authenticationTopic.runWith(TestSink.probe(server.actorSystem))(server.materializer).request(1)
        .expectNext(FiniteDuration(2, TimeUnit.MINUTES))

      Seq(
        AuthenticationUser("admin", "admin", AuthenticationRole.Admin),
        AuthenticationUser("lecturer", "lecturer", AuthenticationRole.Lecturer),
        AuthenticationUser("student", "student", AuthenticationRole.Student)
      ) should contain (authUser)
    }

    "add a student" in {
      client.addStudent().handleRequestHeader(addAuthorizationHeader()).invoke(PostMessageStudent(authenticationUser, student0)).map { answer =>
        answer should ===(Done)
      }
    }

    "add a lecturer" in {
      client.addLecturer().handleRequestHeader(addAuthorizationHeader()).invoke(PostMessageLecturer(authenticationUser, lecturer0)).map { answer =>
        answer should ===(Done)
      }
    }

    "add an admin" in {
      client.addAdmin().handleRequestHeader(addAuthorizationHeader()).invoke(PostMessageAdmin(authenticationUser, admin0)).map { answer =>
        answer should ===(Done)
      }
    }

    "delete a non-existing user" in {
      client.deleteUser("WurstAG").handleRequestHeader(addAuthorizationHeader()).invoke().failed.map {
        answer =>
          answer.asInstanceOf[TransportException].errorCode.http should ===(404)
      }
    }

    "find a non-existing student" in {
      client.getStudent("WurstAG").handleRequestHeader(addAuthorizationHeader()).invoke().failed.map { answer =>
        answer shouldBe a[NotFound]
      }
    }

    "find a non-existing lecturer" in {
      client.getLecturer("WurstAG").handleRequestHeader(addAuthorizationHeader()).invoke().failed.map { answer =>
        answer shouldBe a[NotFound]
      }
    }

    "find a non-existing admin" in {
      client.getAdmin("WurstAG").handleRequestHeader(addAuthorizationHeader()).invoke().failed.map { answer =>
        answer shouldBe a[NotFound]
      }
    }

    "update a non-existing student" in {
      client.updateStudent(student0.username).handleRequestHeader(addAuthorizationHeader())
        .invoke(student0.copy(username = "Guten Abend")).failed.map { answer =>
        answer.asInstanceOf[TransportException].errorCode.http should ===(404)
      }
    }

    "update a non-existing lecturer" in {
      client.updateLecturer(lecturer0.username).handleRequestHeader(addAuthorizationHeader())
        .invoke(lecturer0.copy(username = "Guten Abend")).failed.map { answer =>
        answer.asInstanceOf[TransportException].errorCode.http should ===(404)
      }
    }

    "update a non-existing admin" in {
      client.updateAdmin(admin0.username).handleRequestHeader(addAuthorizationHeader())
        .invoke(admin0.copy(username = "Guten Abend")).failed.map { answer =>
        answer.asInstanceOf[TransportException].errorCode.http should ===(404)
      }
    }

    "add an already existing user" in {
      client.addAdmin().handleRequestHeader(addAuthorizationHeader())
        .invoke(PostMessageAdmin(authenticationUser, admin1)).failed.map { answer =>
        answer.asInstanceOf[TransportException].errorCode.http should ===(409)
      }
    }

    "delete a user" in {
      client.deleteUser(student0.username).handleRequestHeader(addAuthorizationHeader()).invoke().flatMap { _ =>
        client.getStudent(student0.username).handleRequestHeader(addAuthorizationHeader()).invoke().failed
      }.map { answer =>
        answer.asInstanceOf[TransportException].errorCode.http should ===(404)
      }
    }

    "update a user" in {
      client.updateAdmin(admin0.username).handleRequestHeader(addAuthorizationHeader())
        .invoke(admin0.copy(firstName = "KLAUS")).flatMap { _ =>
        client.getAdmin(admin0.username).handleRequestHeader(addAuthorizationHeader()).invoke()
      }.map { answer =>
        answer.firstName shouldBe "KLAUS"
      }
    }

    "get a role of a user" in {
      client.getRole(lecturer0.username).handleRequestHeader(addAuthorizationHeader()).invoke().map { answer =>
        answer.role shouldBe Role.Lecturer
      }
    }
  }
}
