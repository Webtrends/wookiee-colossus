package com.webtrends.harness.component.colossus.command

import java.net.InetAddress

import colossus.protocols.http.{HttpCodes, HttpHeader, HttpRequest}
import com.webtrends.harness.component.colossus.mock.MockColossusService

class CommandRoutingTest extends MockColossusService {
  def commands = List(("TestCommand", classOf[TestCommand], List()),
    ("TestCommandBoth", classOf[TestCommandBoth], List()))
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

    "encode failures correctly" in {
      val resp = returnResponse(HttpRequest.get("/failure"))
      resp.code mustEqual HttpCodes.BAD_REQUEST
      resp.body.toString() mustEqual "some exception"
    }

    "bad response set as expected" in {
      val bad = ColossusResponse.badRequest(None)
      bad.code mustEqual HttpCodes.BAD_REQUEST
    }

    "put remote address on the response" in {
      val resp = returnResponse(HttpRequest.get("/goober/Value"))
      resp.head.headers.toSeq must contain(HttpHeader("Remote-Address", InetAddress.getLocalHost.getHostAddress))
    }

    "be able to hit a BOTH command" in {
      val resp = returnResponse(HttpRequest.get("/goober"))
      resp.code mustEqual HttpCodes.OK
    }
  }
}