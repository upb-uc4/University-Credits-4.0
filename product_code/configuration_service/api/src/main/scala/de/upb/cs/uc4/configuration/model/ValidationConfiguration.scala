package de.upb.cs.uc4.configuration.model

import de.upb.cs.uc4.configuration.model.ValidationConfiguration._
import de.upb.cs.uc4.shared.client.configuration.RegexCollection
import play.api.libs.json.{ Format, Json }

case class ValidationConfiguration(
    authenticationUser: AuthenticationUserRegex,
    postMessageCSR: PostMessageCSRRegex,
    course: CourseRegex,
    user: UserRegex,
    lecturer: LecturerRegex,
    address: AddressRegex
)

object ValidationConfiguration {
  case class AuthenticationUserRegex(username: String, password: String)
  object AuthenticationUserRegex {
    implicit val format: Format[AuthenticationUserRegex] = Json.format
  }

  case class PostMessageCSRRegex(certificateSigningRequest: String)
  object PostMessageCSRRegex {
    implicit val format: Format[PostMessageCSRRegex] = Json.format
  }

  case class CourseRegex(courseName: String, startDate: String, endDate: String, ects: String, lecturerId: String, maxParticipants: String, courseDescription: String)
  object CourseRegex {
    implicit val format: Format[CourseRegex] = Json.format
  }

  case class UserRegex(username: String, firstName: String, lastName: String, email: String, phoneNumber: String, birthDate: String)
  object UserRegex {
    implicit val format: Format[UserRegex] = Json.format
  }

  case class LecturerRegex(freeText: String, researchArea: String)
  object LecturerRegex {
    implicit val format: Format[LecturerRegex] = Json.format
  }

  case class AddressRegex(street: String, houseNumber: String, city: String)
  object AddressRegex {
    implicit val format: Format[AddressRegex] = Json.format
  }

  def build: ValidationConfiguration = {
    ValidationConfiguration(
      AuthenticationUserRegex(
        RegexCollection.AuthenticationUser.usernameRegex.regex,
        RegexCollection.AuthenticationUser.passwordRegex.regex
      ),
      PostMessageCSRRegex(RegexCollection.PostMessageCSR.csrRegex.regex),
      CourseRegex(
        RegexCollection.Commons.nameRegex.regex,
        RegexCollection.Commons.dateRegex.regex,
        RegexCollection.Commons.dateRegex.regex,
        RegexCollection.Course.ectsRegex.regex,
        RegexCollection.Commons.nameRegex.regex,
        RegexCollection.Course.maxParticipantsRegex.regex,
        RegexCollection.Commons.longTextRegex.regex
      ),
      UserRegex(
        RegexCollection.User.usernameRegex.regex,
        RegexCollection.Commons.nameRegex.regex,
        RegexCollection.Commons.nameRegex.regex,
        RegexCollection.User.mailRegex.regex,
        RegexCollection.User.phoneNumberRegex.regex,
        RegexCollection.Commons.dateRegex.regex
      ),
      LecturerRegex(
        RegexCollection.Commons.longTextRegex.regex,
        RegexCollection.Lecturer.researchAreaRegex.regex
      ),
      AddressRegex(
        RegexCollection.Address.nameRegex.regex,
        RegexCollection.Address.houseNumberRegex.regex,
        RegexCollection.Address.nameRegex.regex
      )
    )

  }
  implicit val format: Format[ValidationConfiguration] = Json.format
}