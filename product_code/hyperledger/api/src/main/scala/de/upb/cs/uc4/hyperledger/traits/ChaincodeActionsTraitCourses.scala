package de.upb.cs.uc4.hyperledger.traits

import de.upb.cs.uc4.hyperledger.exceptions.TransactionErrorException

/**
 * Trait to provide explicit access to chaincode transactions regarding courses
 */
protected trait ChaincodeActionsTraitCourses extends ChaincodeActionsTraitInternal {

  /**
   * Executes the "addCourse" query.
   * @param jSonCourse Information about the course to add.
   * @throws Exception if chaincode throws an exception.
   * @return Success_state
   */
  @throws[Exception]
  final def addCourse(jSonCourse : String) : String = {
    wrapTransactionResult("addCourse", this.internalSubmitTransaction("addCourse", jSonCourse))
  }

  /**
   * Submits the "deleteCourseById" query.
   * @param courseId courseId to delete course
   * @throws Exception if chaincode throws an exception.
   * @return success_state
   */
  @throws[Exception]
  final def deleteCourseById(courseId : String) : String = {
    wrapTransactionResult("deleteCourseById", this.internalSubmitTransaction("deleteCourseById", courseId))
  }

  /**
   * Submits the "updateCourseById" query.
   * @param courseId courseId to update course
   * @param jSonCourse courseInfo to update to
   * @throws Exception if chaincode throws an exception.
   * @return success_state
   */
  @throws[Exception]
  final def updateCourseById(courseId : String, jSonCourse : String) : String = {
    wrapTransactionResult("updateCourseById", this.internalSubmitTransaction("updateCourseById", courseId, jSonCourse))
  }

  /**
   * Executes the "getCourses" query.
   * @throws Exception if chaincode throws an exception.
   * @return List of courses represented by their json value.
   */
  @throws[Exception]
  final def getAllCourses() : String = {
    val result = wrapTransactionResult("getAllCourses", this.internalEvaluateTransaction("getAllCourses"))

    // check specific error
    if (!result.startsWith("[") || !result.endsWith("]")) throw new TransactionErrorException("getAllCourses", result)
    else return result
  }

  /**
   * Executes the "getCourseById" query.
   * @param courseId courseId to get course information
   * @throws Exception if chaincode throws an exception.
   * @return JSon Course Object
   */
  @throws[Exception]
  final def getCourseById(courseId : String) : String = {
    val result = wrapTransactionResult("getCourseById", this.internalEvaluateTransaction("getCourseById", courseId))

    // check specific error
    if (result == "null") throw new TransactionErrorException("getCourseById", result)
    else return result
  }
}
