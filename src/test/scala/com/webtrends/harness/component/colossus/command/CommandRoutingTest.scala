package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.{HttpCodes, HttpRequest}
import com.webtrends.harness.component.colossus.mock.MockColossusService

class CommandRoutingTest extends MockColossusService {
  def commands = List(("TestCommand", classOf[TestCommand], List()))
  def wookieeService = None

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

    "use response getter" in {
      val resp = returnResponse(HttpRequest.get("/goober/Value"))
      resp.body.toString() mustEqual "getRespValue"
      resp.code mustEqual HttpCodes.OK
    }
  }
}
