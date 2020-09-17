package de.upb.cs.uc4.user.impl

import akka.stream.Materializer
import com.lightbend.lagom.scaladsl.api.transport.RequestHeader
import de.upb.cs.uc4.shared.client.exceptions.{ CustomException, ErrorType, GenericError }
import play.api.mvc.{ DefaultActionBuilder, PlayBodyParsers, Result, Results }
import play.api.routing.Router
import play.api.routing.sird._

import scala.concurrent.{ ExecutionContext, Future }

class ImageUploadRouter(action: DefaultActionBuilder, parser: PlayBodyParsers, userApplication: UserApplication) {
  private lazy val userService = userApplication.lagomServer.serviceBinding.service.asInstanceOf[UserServiceImpl]
  private lazy val config = userApplication.config

  private implicit val executionContext: ExecutionContext = userApplication.executionContext
  private implicit val materializer: Materializer = userApplication.materializer

  val router: Router = Router.from {
    case PUT(p"/user-management/users/$username<[^/]+>/image") =>
      action.async(parser.maxLength(config.getInt("uc4.image.maxSize"), parser.raw)) { request =>

        request.body match {
          case Left(_) =>
            Future.successful(Results.EntityTooLarge(GenericError(ErrorType.EntityTooLarge)))
          case Right(buffer) =>
            val serviceRequest = RequestHeader.Default.withHeaders(request.headers.headers)

            val filePath: String = buffer.asFile.getAbsolutePath

            try {
              userService.setImage(username).invokeWithHeaders(serviceRequest, filePath).map {
                case (header, _) => Results.Created.withHeaders(header.headers.toSeq: _*)
              }.recover(handleException)
            }
            catch handleException.andThen(result => Future.successful(result))
        }
      }
  }

  private def handleException: PartialFunction[Throwable, Result] = {
    case customException: CustomException =>
      new Results.Status(customException.getErrorCode.http)(customException.getPossibleErrorResponse)
    case _: Exception =>
      Results.InternalServerError(GenericError(ErrorType.InternalServer))
  }
}