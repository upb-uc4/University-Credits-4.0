package de.upb.cs.uc4.user.impl

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.typed.PersistenceId
import de.upb.cs.uc4.shared.server.messages.{ Accepted, Confirmation, Rejected }
import de.upb.cs.uc4.user.impl.actor.UserBehaviour
import de.upb.cs.uc4.user.impl.commands._
import de.upb.cs.uc4.user.model.user._
import de.upb.cs.uc4.user.model.{ Address, Role }
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.util.UUID

/** Tests for the CourseState */
class UserStateSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${UUID.randomUUID().toString}"
    """) with AnyWordSpecLike with Matchers {

  //Test users
  val address: Address = Address("ExampleStreet", "42a", "13337", "ExampleCity", "Germany")

  val student0: Student = Student("student0", "c3R1ZGVudHN0dWRlbnQ=", isActive = true, Role.Student, address, "firstName", "LastName", "example@mail.de", "+49123456789", "1990-12-11", "", "7421769")
  val lecturer0: Lecturer = Lecturer("lecturer0", "bGVjdHVyZXJsZWN0dXJlcg==", isActive = true, Role.Lecturer, address, "firstName", "LastName", "example@mail.de", "+49123456789", "1991-12-11", "Heute kommt der kleine Gauss dran.", "Mathematics")
  val admin0: Admin = Admin("admin0", "YWRtaW5hZG1pbg==", isActive = true, Role.Admin, address, "firstName", "LastName", "example1@mail.de", "+49123456789", "1992-12-11")
  val admin1: Admin = Admin("admin0", "YWRtaW5hZG1pbg==", isActive = true, Role.Admin, address, "firstNameDifferent", "LastNameDifferent", "example2@mail.de", "+49123456789", "1992-12-11")

  val emptyLecturer: Lecturer = Lecturer("lecturer0", "", isActive = true, Role.Lecturer, address, "", "", "", "", "", "", "") //name for update test

  "UserState" should {

    //GET
    "get non-existing user" in {
      val probe = createTestProbe[Option[User]]()
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-1")))
      ref ! GetUser(probe.ref)
      probe.expectMessage(None)
    }

    //ADD
    "add a user" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-2")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! CreateUser(student0, "governmentIdStudent", probe1.ref)
      probe1.expectMessageType[Accepted]

      val probe2 = createTestProbe[Option[User]]()
      ref ! GetUser(probe2.ref)
      probe2.expectMessage(Some(student0))
    }

    "throw an internal error when adding an already existing user" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-3")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! CreateUser(admin0, "governmentIdAdmin", probe1.ref)
      probe1.expectMessageType[Accepted]

      val probe2 = createTestProbe[Confirmation]()
      ref ! CreateUser(admin1, "governmentIdAdmin", probe2.ref)
      val message = probe2.receiveMessage()
      assert(message.isInstanceOf[Rejected] && message.asInstanceOf[Rejected].statusCode == 500)
    }

    //UPDATE
    "throw an internal error when updating a non-existing user" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-4")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! UpdateUser(lecturer0, probe1.ref)
      val message = probe1.receiveMessage()
      assert(message.isInstanceOf[Rejected] && message.asInstanceOf[Rejected].statusCode == 500)
    }

    "update an existing user" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-5")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! CreateUser(admin0, "governmentIdAdmin", probe1.ref)
      probe1.expectMessageType[Accepted]

      val probe2 = createTestProbe[Confirmation]()
      ref ! UpdateUser(admin1, probe2.ref)
      probe2.expectMessageType[Accepted]

      val probe3 = createTestProbe[Option[User]]()
      ref ! GetUser(probe3.ref)
      probe3.expectMessage(Some(admin1))
    }

    "update the latestImmatriculation of a student" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-5A")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! CreateUser(student0, "governmentIdStudent", probe1.ref)
      probe1.expectMessageType[Accepted]

      val probe2 = createTestProbe[Confirmation]()
      ref ! UpdateLatestMatriculation("SS2020", probe2.ref)
      probe2.expectMessageType[Accepted]

      val probe3 = createTestProbe[Option[User]]()
      ref ! GetUser(probe3.ref)
      probe3.expectMessage(Some(student0.copy(latestImmatriculation = "SS2020")))
    }

    "update the latestImmatriculation of a student a second time successful" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-5B")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! CreateUser(student0, "governmentIdStudent", probe1.ref)
      probe1.expectMessageType[Accepted]

      val probe2 = createTestProbe[Confirmation]()
      ref ! UpdateLatestMatriculation("SS2020", probe2.ref)
      probe2.expectMessageType[Accepted]

      val probe3 = createTestProbe[Confirmation]()
      ref ! UpdateLatestMatriculation("WS2020/21", probe3.ref)
      probe3.expectMessageType[Accepted]

      val probe4 = createTestProbe[Option[User]]()
      ref ! GetUser(probe4.ref)
      probe4.expectMessage(Some(student0.copy(latestImmatriculation = "WS2020/21")))
    }

    "update the latestImmatriculation of a student a second time not successful" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-5C")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! CreateUser(student0, "governmentIdStudent", probe1.ref)
      probe1.expectMessageType[Accepted]

      val probe2 = createTestProbe[Confirmation]()
      ref ! UpdateLatestMatriculation("SS2020", probe2.ref)
      probe2.expectMessageType[Accepted]

      val probe3 = createTestProbe[Confirmation]()
      ref ! UpdateLatestMatriculation("SS2019", probe3.ref)
      probe3.expectMessageType[Accepted]

      val probe4 = createTestProbe[Option[User]]()
      ref ! GetUser(probe4.ref)
      probe4.expectMessage(Some(student0.copy(latestImmatriculation = "SS2020")))
    }

    //DELETE
    "not force delete a non-existing user" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-6")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! ForceDeleteUser(probe1.ref)
      probe1.expectMessageType[Rejected]
    }

    "force delete an existing user" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-7")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! CreateUser(lecturer0, "governmentIdLecturer", probe1.ref)
      probe1.expectMessageType[Accepted]

      val probe2 = createTestProbe[Confirmation]()
      ref ! ForceDeleteUser(probe2.ref)
      probe2.expectMessageType[Accepted]

      val probe3 = createTestProbe[Option[User]]()
      ref ! GetUser(probe3.ref)
      probe3.expectMessage(None)
    }

    "not soft delete a non-existing user" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-8")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! SoftDeleteUser(probe1.ref)
      probe1.expectMessageType[Rejected]
    }

    "soft delete an existing user" in {
      val ref = spawn(UserBehaviour.create(PersistenceId("fake-type-hint", "fake-id-9")))

      val probe1 = createTestProbe[Confirmation]()
      ref ! CreateUser(lecturer0, "GovID", probe1.ref)
      probe1.expectMessageType[Accepted]

      val probe2 = createTestProbe[Confirmation]()
      ref ! SoftDeleteUser(probe2.ref)
      probe2.expectMessageType[Accepted]

      val probe3 = createTestProbe[Option[User]]()
      ref ! GetUser(probe3.ref)
      probe3.expectMessage(Some(lecturer0.softDelete))
    }
  }
}
