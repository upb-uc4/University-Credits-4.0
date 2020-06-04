package de.upb.cs.uc4.impl

import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef}
import akka.util.Timeout
import akka.{Done, NotUsed}
import com.lightbend.lagom.scaladsl.server.{LocalServiceLocator, ServerServiceCall}
import com.lightbend.lagom.scaladsl.api.transport.{BadRequest, ExceptionMessage, MessageProtocol, ResponseHeader, TransportErrorCode}
import com.lightbend.lagom.scaladsl.api.transport.ResponseHeader
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.persistence.ReadSide
import com.lightbend.lagom.scaladsl.persistence.cassandra.CassandraSession
import de.upb.cs.uc4.api.CourseService
import de.upb.cs.uc4.impl.actor.CourseState
import de.upb.cs.uc4.impl.commands.{CourseCommand, CreateCourse, GetCourse, UpdateCourse}
import de.upb.cs.uc4.impl.readside.CourseEventProcessor
import de.upb.cs.uc4.model.Course
import de.upb.cs.uc4.shared.messages.{Accepted, Confirmation, Rejected}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

/**
  * Implementation of the Universitycredits4Service.
  */
class CourseServiceImpl(clusterSharding: ClusterSharding,
                        readSide: ReadSide, processor: CourseEventProcessor, cassandraSession: CassandraSession)
                       (implicit ec: ExecutionContext) extends CourseService {
  readSide.register(processor)

  /**
    * Looks up the entity for the given ID.
    */
  private def entityRef(id: Long): EntityRef[CourseCommand] =
    clusterSharding.entityRefFor(CourseState.typeKey, id.toString)

  implicit val timeout: Timeout = Timeout(5.seconds)

  /*override def getAllCourses: ServiceCall[NotUsed, Source[Course, NotUsed]] = ServiceCall{ _ =>
    val response = cassandraSession.select("SELECT id FROM courses ;")
      .map(row => row.getLong("id")).mapAsync(8)(findCourseByCourseId().invoke(_))

    Future.successful(response)
  }*/

  /** @inheritdoc */
  override def getAllCourses: ServiceCall[NotUsed, Seq[Course]] = ServiceCall{ _ =>
    cassandraSession.selectAll("SELECT id FROM courses ;")
      .map( seq => seq
        .map(row => row.getLong("id"))       //Future[Seq[Long]]
        .map(findCourseByCourseId().invoke(_))).    //Future[Seq[Future[Course]]]
      flatMap(seq => Future.sequence(seq))          //Future[Seq[Course]]
  }

  /** @inheritdoc */
  override def addCourse(): ServiceCall[Course, Done] = ServerServiceCall {
    (requestheader,courseToAdd) =>

    // Look up the sharded entity (aka the aggregate instance) for the given ID.
    val ref = entityRef(courseToAdd.courseId)

    ref.ask[Confirmation](replyTo => CreateCourse(courseToAdd, replyTo))
      .map {
        case Accepted => {/*
          val sucCode : Int = 201
          val mProtocol: MessageProtocol = MessageProtocol.empty
          val strList : List[(String,String)] =  List[(String,String)](("1","Operation Succesfull"))*/
          (ResponseHeader.Ok,Done)
        }
        case Rejected(reason) =>
        {
          val erCode : Int = 409
          val mProtocol: MessageProtocol = MessageProtocol.empty
          val strList : List[(String,String)] =  List[(String,String)](("1",reason))
          (ResponseHeader(erCode,mProtocol,strList),Done)

         // (ResponseHeader(404,MessageProtocol.empty,List[(String,String)]("ups","no")),Done)
        }
          //throw new BadRequest(TransportErrorCode.BadRequest, new ExceptionMessage("Id already in use", reason), new Throwable)
      }

  }

  /** @inheritdoc */
  override def deleteCourse(): ServiceCall[Long, Done] = ???

  /** @inheritdoc */
  override def findCourseByCourseId(): ServiceCall[Long, Course] = ServiceCall{ id =>
    entityRef(id).ask[Course](replyTo => GetCourse(replyTo))
  }

  /*
  /** @inheritdoc */
  override def findCoursesByCourseName(): ServiceCall[String, Source[Course, NotUsed]] = ServiceCall{ name =>
    getAllCourses.invoke().map(_.filter(course => course.name == name))
  }

  /** @inheritdoc */
  override def findCoursesByLecturerId(): ServiceCall[Long, Source[Course, NotUsed]] = ServiceCall{ lecturerId =>
    getAllCourses.invoke().map(_.filter(course => course.lecturerId == lecturerId))
  }*/

  override def findCoursesByCourseName(): ServiceCall[String, Seq[Course]] = ServiceCall{ name =>
    getAllCourses.invoke().map(_.filter(course => course.courseName == name))
  }

  /** @inheritdoc */
  override def findCoursesByLecturerId(): ServiceCall[Long, Seq[Course]] = ServiceCall{ lecturerId =>
    getAllCourses.invoke().map(_.filter(course => course.lecturerId == lecturerId))
  }

  /** @inheritdoc */
  override def updateCourse(): ServiceCall[Course, Done] = ServiceCall {
    courseToChange =>
      // Look up the sharded entity (aka the aggregate instance) for the given ID.
      val ref = entityRef(courseToChange.courseId)

      ref.ask[Confirmation](replyTo => UpdateCourse(courseToChange, replyTo))
        .map {
          case Accepted => Done
          case Rejected(reason) => throw new BadRequest(TransportErrorCode.BadRequest,
            new ExceptionMessage("Id does not exist", reason), new Throwable)
        }
  }
}
