package de.upb.cs.uc4.user.model.user

import com.fasterxml.jackson.annotation.JsonTypeInfo
import de.upb.cs.uc4.shared.client.configuration.{ ErrorMessageCollection, RegexCollection }
import de.upb.cs.uc4.shared.client.exceptions.SimpleError
import de.upb.cs.uc4.user.model.Role.Role
import de.upb.cs.uc4.user.model.{ Address, Role }
import play.api.libs.json.{ Format, JsResult, JsValue, Json }

import scala.concurrent.{ ExecutionContext, Future }

@JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
trait User {
  val username: String
  val enrollmentIdSecret: String
  val isActive: Boolean
  val role: Role
  val address: Address
  val firstName: String
  val lastName: String
  val email: String
  val phoneNumber: String
  val birthDate: String

  def copyUser(
      username: String = this.username,
      enrollmentIdSecret: String = this.enrollmentIdSecret,
      isActive: Boolean = this.isActive,
      role: Role = this.role,
      address: Address = this.address,
      firstName: String = this.firstName,
      lastName: String = this.lastName,
      email: String = this.email,
      phoneNumber: String = this.phoneNumber,
      birthDate: String = this.birthDate
  ): User

  def toPublic: User

  def trim: User = copyUser(
    username.trim, enrollmentIdSecret.trim, isActive, role, address.trim, firstName.trim, lastName.trim,
    email.trim, phoneNumber.trim, birthDate.trim
  )

  def clean: User = trim.copyUser(email = email.toLowerCase, phoneNumber = phoneNumber.replaceAll("\\s+", ""))

  /** Validates the object by checking predefined conditions like correct charsets, syntax, etc.
    * Returns a list of SimpleErrors[[SimpleError]]
    *
    * @return Filled Sequence of [[SimpleError]]
    */
  def validate(implicit ec: ExecutionContext): Future[Seq[SimpleError]] = {

    val usernameRegex = RegexCollection.User.usernameRegex
    val nameRegex = RegexCollection.Commons.nonEmpty100CharRegex
    val mailRegex = RegexCollection.User.mailRegex
    val phoneNumberRegex = RegexCollection.User.phoneNumberRegex
    val dateRegex = RegexCollection.Commons.dateRegex

    val usernameMessage = ErrorMessageCollection.User.usernameMessage
    val firstNameMessage = ErrorMessageCollection.User.firstNameMessage
    val lastNameMessage = ErrorMessageCollection.User.lastNameMessage
    val mailMessage = ErrorMessageCollection.User.mailMessage
    val phoneNumberMessage = ErrorMessageCollection.User.phoneNumberMessage
    val dateMessage = ErrorMessageCollection.Commons.dateMessage

    address.validate.map { addressErrors =>
      var errors = List[SimpleError]()

      if (!isActive) {
        errors :+= SimpleError(
          "isActive",
          "Creating or manipulating an inactive User is not allowed."
        )
      }
      else {
        if (!usernameRegex.matches(username)) {
          errors :+= SimpleError(
            "username",
            usernameMessage
          )
        }

        if (!Role.All.contains(role)) {
          errors :+= SimpleError("role", "Role must be one of " + Role.All + ".")
        }
        else {
          this match {
            case _: Student => if (role != Role.Student) {
              errors :+= SimpleError("role", "Role must be one of " + Role.All + ", and conform to the type of object.")
            }
            case _: Lecturer => if (role != Role.Lecturer) {
              errors :+= SimpleError("role", "Role must be one of " + Role.All + ", and conform to the type of object.")
            }
            case _: Admin => if (role != Role.Admin) {
              errors :+= SimpleError("role", "Role must be one of " + Role.All + ", and conform to the type of object.")
            }
          }
        }

        errors ++= addressErrors.map(error => SimpleError("address." + error.name, error.reason))

        if (!mailRegex.matches(email)) {
          errors :+= SimpleError("email", mailMessage)
        }
        if (!dateRegex.matches(birthDate)) {
          errors :+= SimpleError("birthDate", dateMessage)
        }
        if (!phoneNumberRegex.matches(phoneNumber)) {
          errors :+= SimpleError("phoneNumber", phoneNumberMessage)
        }
        if (!nameRegex.matches(firstName)) {
          errors :+= SimpleError("firstName", firstNameMessage)
        }
        if (!nameRegex.matches(lastName)) {
          errors :+= SimpleError("lastName", lastNameMessage)
        }
      }
      errors
    }
  }

  /** Validates the object by checking predefined conditions like correct charsets, syntax, ... that must only apply on object creation.
    * Returns a list of SimpleErrors[[SimpleError]]
    *
    * @return Filled Sequence of [[SimpleError]]
    */
  def validateOnCreation(implicit ec: ExecutionContext): Future[Seq[SimpleError]] = {
    this.validate.map { validationErrors =>
      var errors = validationErrors

      if (enrollmentIdSecret.nonEmpty) {
        errors :+= SimpleError("enrollmentIdSecret", "EnrollmentIdSecret must be empty.")
      }
      errors
    }
  }

  /** Compares the object against the user parameter to find out if fields, which should only be changed by users with elevated privileges, are different.
    * Returns a list of SimpleErrors[[SimpleError]]
    *
    * @param user to be checked
    * @return Filled Sequence of [[SimpleError]]
    */
  def checkProtectedFields(user: User): Seq[SimpleError] = {
    var errors = List[SimpleError]()

    if (firstName != user.firstName) {
      errors :+= SimpleError("firstName", "First name may not be manually changed.")
    }
    if (lastName != user.lastName) {
      errors :+= SimpleError("lastName", "Last name may not be manually changed.")
    }
    if (birthDate != user.birthDate) {
      errors :+= SimpleError("birthDate", "Birthdate may not be manually changed.")
    }
    errors
  }

  /** Compares the object against the user parameter to find out if fields, which cannot be changed, are different.
    * Returns a list of SimpleErrors[[SimpleError]]
    *
    * @param user to be checked
    * @return Filled Sequence of [[SimpleError]]
    */
  def checkUneditableFields(user: User): Seq[SimpleError] = {
    var errors = List[SimpleError]()

    if (role != user.role) {
      errors :+= SimpleError("role", "Role must not be changed.")
    }
    if (isActive != user.isActive) {
      errors :+= SimpleError("isActive", "IsActive must not be changed.")
    }

    if (enrollmentIdSecret != user.enrollmentIdSecret) {
      errors :+= SimpleError("enrollmentIdSecret", "enrollmentIdSecret must not be changed.")
    }

    errors
  }

  /** Creates a copy of this user, with most personal info deleted
    *
    * @return A new user, with (most) personal info deleted
    */
  def softDelete: User
}

object User {
  implicit val format: Format[User] = new Format[User] {
    override def reads(json: JsValue): JsResult[User] = {
      json("role").as[Role] match {
        case Role.Admin    => Json.fromJson[Admin](json)
        case Role.Student  => Json.fromJson[Student](json)
        case Role.Lecturer => Json.fromJson[Lecturer](json)
      }
    }

    override def writes(o: User): JsValue = {
      o match {
        case admin: Admin       => Json.toJson(admin)
        case student: Student   => Json.toJson(student)
        case lecturer: Lecturer => Json.toJson(lecturer)
      }
    }
  }
}