
object Version {

  private val versions: Map[String, String] = Map(
    "authentication_service" -> "v0.10.0",
    "certificate_service" -> "v0.10.2",
    "configuration_service" -> "v0.10.1",
    "course_service" -> "v0.10.0",
    "hyperledger_api" -> "v0.9.1",
    "matriculation_service" -> "v0.10.0",
    "user_service" -> "v0.9.3"
  )

  /** Returns the version of a project
    *
    * @param project name of the project
    */
  def apply(project: String): String = versions(project)
}
