package de.upb.cs.uc4.user.model.user

import de.upb.cs.uc4.shared.client.exceptions.SimpleError
import de.upb.cs.uc4.user.model.Address
import de.upb.cs.uc4.user.model.Role.Role
import play.api.libs.json.{Format, Json}

case class Student(username: String,
                   role: Role,
                   address: Address,
                   firstName: String,
                   lastName: String,
                   picture: String,
                   email: String,
                   birthDate: String,
                   immatriculationStatus: String,
                   matriculationId: String,
                   semesterCount: Int,
                   fieldsOfStudy: List[String]) extends User {

  def trim: Student = {
    copy(username.trim, role, address.trim, firstName.trim, lastName.trim,
      picture.trim, email.trim, birthDate.trim, immatriculationStatus.trim, matriculationId.trim)
  }

  def clean: Student = {
    trim.copy(email = email.toLowerCase)
  }

  /** @inheritdoc */
  override def validate: Seq[SimpleError] = {
    val fos = List("Computer Science","Philosophy","Media Sciences", "Economics", "Mathematics", "Physics", "Chemistry",
      "Education", "Sports Science", "Japanology", "Spanish Culture", "Pedagogy", "Business Informatics", "Linguistics")

    var errors = super.validate.asInstanceOf[List[SimpleError]]
    if(matriculationId.isEmpty) {
      errors :+= SimpleError("matriculationId", "Matriculation ID must not be empty.")
    }else{
      if(!(matriculationId forall Character.isDigit) || !(matriculationId.toInt > 0) || !(matriculationId.toInt < 10000000)) {
        errors :+= SimpleError("matriculationId", "Matriculation ID must be an integer between 1 and 9999999.")
      }
    }
    if(!(semesterCount > 0)) {
      errors :+= SimpleError("semesterCount", "Semester count must be a positive integer.")
    }
    if(!(fieldsOfStudy forall fos.contains)) {
      errors :+= SimpleError("fieldsOfStudy", "Fields of Study must be one of [..].")
    }
    errors
  }


  /** 
    * Compares the object against the user parameter to find out if fields, which should only be changed by users with elevated privileges, are different.
    * Returns a list of [[SimpleError]]
    * 
    * @param user 
    * @return Filled Sequence of [[SimpleError]]
    */
  override def checkEditableFields (user: User): Seq[SimpleError] = {
    if(!user.isInstanceOf[Student]){
      throw new Exception("Tried to parse a non-Student as Student.")
    }
    val student = user.asInstanceOf[Student]

    var errors = List[SimpleError]()
   
    errors ++= super.checkEditableFields(user)
    
    if (immatriculationStatus != student.immatriculationStatus){
      errors :+= SimpleError("immatriculationStatus", "Immatriculation status may not be manually changed.")
    }
    if (matriculationId != student.matriculationId){
      errors :+= SimpleError("matriculationId", "Matriculation ID may not be manually changed.")
    }
    if (semesterCount != student.semesterCount){
      errors :+= SimpleError("semesterCount", "Number of semesters may not be manually changed.")
    }
    if (fieldsOfStudy != student.fieldsOfStudy){
      errors :+= SimpleError("fieldsOfStudy", "Fields of study may not be manually changed.")
    }
    errors
  }
}

object Student {
  implicit val format: Format[Student] = Json.format
}
