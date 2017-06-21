package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.{HttpCodes, HttpRequest}
import colossus.testkit.HttpServiceSpec
import com.webtrends.harness.component.colossus.{ColossusManager, ColossusManagerTest}
import com.webtrends.harness.service.test.TestHarness

import scala.concurrent.duration.FiniteDuration

class CommandRoutingTest extends HttpServiceSpec {
  val th = TestHarness(ColossusManagerTest.config, None, Some(Map("wookiee-colossus" -> classOf[ColossusManager])))
  val colManager = th.getComponent("wookiee-colossus").get
  colManager ! ("TestCommand", classOf[TestCommand])

  "Command routing" should {
    "handle a get request" in {
      expectCodeAndBody(HttpRequest.get("/goober/Value"), HttpCodes.OK, "getRespValue")
    }

    "handle a put request" in {
      expectCodeAndBody(HttpRequest.put("/goober"), HttpCodes.OK, """{"response":"someResponse"}""")
    }

    "handle a post request" in {
      expectCodeAndBody(HttpRequest.post("/goober"), HttpCodes.OK, """{"response":"someResponse"}""")
    }

    "can't hit health check on external server" in {
      expectCode(HttpRequest.get("/healthcheck"), HttpCodes.NOT_FOUND)
    }
  }

  override def service = ColossusManager.getExternalServer
  override def requestTimeout = FiniteDuration(10000, "ms")
}
