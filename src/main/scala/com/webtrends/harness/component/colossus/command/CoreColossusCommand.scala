package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.HttpMethod.Get
import colossus.protocols.http.HttpRequest
import colossus.protocols.http.UrlParsing._
import com.webtrends.harness.HarnessConstants
import com.webtrends.harness.command.Command
import com.webtrends.harness.health.{ApplicationHealth, ComponentState, HealthRequest, HealthResponseType}
import org.json4s.DefaultFormats
import org.json4s.ext.{EnumNameSerializer, JodaTimeSerializers}
import akka.pattern.ask

import scala.concurrent.Future

class CoreColossusCommand extends Command with ColossusCommand {
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
  }
}

object CoreColossusCommand {
  val CommandName = "CoreColossusCommand"
}