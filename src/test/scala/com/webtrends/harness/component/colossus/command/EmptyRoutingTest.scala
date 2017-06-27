package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.{HttpCodes, HttpRequest}
import com.webtrends.harness.component.colossus.ColossusRouteContainer
import org.scalatest.{MustMatchers, WordSpec}
import scala.concurrent.ExecutionContext.Implicits.global

import scala.util.Success

class EmptyRoutingTest extends WordSpec with MustMatchers with ColossusRouteContainer {
  "Command routing with no routes" should {
    "when no routes defined in external, get not implemented back" in {
      getRouteFunction(HttpRequest.get("/healthcheck")) onComplete {
        case Success(resp) =>
          resp.code mustEqual HttpCodes.NOT_IMPLEMENTED
      }
    }
  }
}
