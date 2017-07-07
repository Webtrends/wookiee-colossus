package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.{HttpBody, HttpCodes, HttpRequest}
import com.webtrends.harness.component.colossus.mock.MockColossusService
import org.scalatest.DoNotDiscover

@DoNotDiscover
class CommandRoutingTest extends MockColossusService {
  def commands = List(("TestCommand", classOf[TestCommand], List()),
    ("TestCommandBoth", classOf[TestCommandBoth], List("Input")))
  def wookieeService = None

  "Command routing" should {
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
      resp.head.headers.toSeq.exists(_.key.toLowerCase == "remote-address") mustEqual true
    }

    "be able to hit a BOTH command" in {
      val resp = returnResponse(HttpRequest.get("/both"))
      resp.code mustEqual HttpCodes.OK
      resp.body.toString() mustEqual "{\"response\":\"someResponseInput\"}"
    }

    "go through the anyref encoder" in {
      val resp = returnResponse(HttpRequest.get("/anyref"))
      resp.body.toString() mustEqual "anyref"
    }

    "get xml output" in {
      val resp = returnResponse(HttpRequest.get("/xml"))
      resp.body.toString() mustEqual "<>"
    }

    "get empty body" in {
      val resp = returnResponse(HttpRequest.get("/empty"))
      resp.body mustEqual HttpBody.NoBody
    }

    "return on total failure" in {
      val resp = returnResponse(HttpRequest.get("/notimpl"))
      resp.code mustEqual HttpCodes.INTERNAL_SERVER_ERROR
    }
  }
}
