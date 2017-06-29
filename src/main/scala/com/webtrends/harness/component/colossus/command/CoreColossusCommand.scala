package com.webtrends.harness.component.colossus.command

import akka.pattern.ask
import colossus.protocols.http.HttpMethod.Get
import colossus.protocols.http.{HttpCodes, HttpRequest}
import colossus.protocols.http.UrlParsing._
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.command.Command
import com.webtrends.harness.component.{ComponentHelper, ComponentRequest}
import com.webtrends.harness.component.messages.StatusRequest
import com.webtrends.harness.health.{ApplicationHealth, ComponentState, HealthRequest, HealthResponseType}
import org.joda.time.{DateTime, DateTimeZone}
import org.json4s.DefaultFormats
import org.json4s.ext.{EnumNameSerializer, JodaTimeSerializers}

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

class CoreColossusCommand extends Command with ColossusCommand with ComponentHelper {
  implicit val system = context.system
  implicit val executionContext = context.dispatcher
  override def commandName = CoreColossusCommand.CommandName

  implicit val formats = (DefaultFormats ++ JodaTimeSerializers.all) + new EnumNameSerializer(ComponentState)

  val healthActor = system.actorSelection(HarnessConstants.HealthFullName)
  val serviceActor = system.actorSelection(HarnessConstants.ServicesFullName)

  override def matchedRoutes: PartialFunction[HttpRequest, Future[ColossusResponse]] = {
    case req @ Get on Root / "healthcheck" =>
      (healthActor ? HealthRequest(HealthResponseType.FULL)).mapTo[ApplicationHealth] map { ah =>
        ColossusResponse(ah)
      }
    case req @ Get on Root / "healthcheck" / "full" =>
      (healthActor ? HealthRequest(HealthResponseType.FULL)).mapTo[ApplicationHealth] map { ah =>
        ColossusResponse(ah)
      }
    case req @ Get on Root / "healthcheck" / "lb" =>
      (healthActor ? HealthRequest(HealthResponseType.LB)).mapTo[String] map { ah =>
        ColossusResponse(ah)
      }
    case req @ Get on Root / "healthcheck" / "nagios" =>
      (healthActor ? HealthRequest(HealthResponseType.NAGIOS)).mapTo[String] map { ah =>
        ColossusResponse(ah)
      }
    case req @ Get on Root / "ping" =>
      Future.successful(ColossusResponse("pong: "
        .concat(new DateTime(System.currentTimeMillis(), DateTimeZone.UTC).toString)))
    case req @ Get on Root / "metrics" =>
      val p = Promise[ColossusResponse]()
      componentRequest[StatusRequest, String]("wookiee-metrics", ComponentRequest(StatusRequest("string"))) onComplete {
        case Success(s) => p success ColossusResponse(s.resp)
        case Failure(f) => p success ColossusResponse(f.getMessage, HttpCodes.INTERNAL_SERVER_ERROR)
      }
      p.future
  }

  override def routeExposure = RouteExposure.INTERNAL
}

object CoreColossusCommand {
  val CommandName = "CoreColossusCommand"
}