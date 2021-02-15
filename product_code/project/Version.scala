
object Version {

  private val versions: Map[String, String] = Map(
    "admission_service" -> "v0.17.2",
    "authentication_service" -> "v0.14.1",
    "certificate_service" -> "v0.16.2",
    "configuration_service" -> "v0.17.1",
    "course_service" -> "v0.17.1",
    "exam_service" -> "v0.18.1",
    "examreg_service" -> "v0.16.1",
    "examresult_service" -> "v0.18.1",
    "group_service" -> "v0.16.2",
    "hyperledger_api" -> "0.18.0",
    "matriculation_service" -> "v0.17.1",
    "operation_service" -> "v0.18.1",
    "user_service" -> "v0.17.1",
    "report_service" -> "v0.17.3"
  )

  /** Returns the version of a project
    *
    * @param project name of the project
    */
  def apply(project: String): String = versions(project)
}
