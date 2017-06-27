package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.HttpMethod.{Get, Post, Put}
import colossus.protocols.http.UrlParsing._
import colossus.protocols.http.{HttpBody, HttpBodyEncoder, HttpCodes, HttpHeaders}
import com.webtrends.harness.command.Command
import com.webtrends.harness.component.colossus.http.Encoders

import scala.concurrent.Future

case class TestResponse(response: String)
class TestRef extends AnyRef {
  override def toString = "anyref"
}

class TestCommand extends Command with ColossusCommand {
  override def commandName = "TestCommand"
  override def routeExposure = RouteExposure.EXTERNAL

  override def matchedRoutes = {
    case req @ Get on Root / "goober" / key =>
      val remote = req.head.headers.toSeq.find(h => h.key.toLowerCase == "remote-address")
      Future.successful(ColossusResponse("getResp" + key, HttpCodes.OK, "text/plain", HttpHeaders(remote.get)))
    case req @(Post | Put) on Root / "goober" =>
      Future.successful(ColossusResponse(TestResponse("someResponse"), HttpCodes.OK))
    case req @ Get on Root / "failure" =>
      Future.successful(ColossusResponse(new Exception("some exception"), HttpCodes.BAD_REQUEST, "text/plain"))
    case req @ Get on Root / "xml" =>
      Future.successful(ColossusResponse("<>", HttpCodes.OK, "text/xml"))
    case req @ Get on Root / "empty" =>
      Future.successful(ColossusResponse("", HttpCodes.OK))
    case req @ Get on Root / "notimpl" =>
      Future { execute(None) }
  }
}

class TestCommandBoth(input: String) extends Command with ColossusCommand with Encoders {
  override def commandName = "TestCommandBoth"
  override def routeExposure = RouteExposure.BOTH

  implicit object TestRefEncoder extends HttpBodyEncoder[TestRef] {
    override def encode(data: TestRef): HttpBody = AnyRefEncoder.encode(data)
  }

  override def matchedRoutes = {
    case req @ Get on Root / "goober" =>
      Future.successful(ColossusResponse(TestResponse("someResponse" + input), HttpCodes.OK))
    case req @ Get on Root / "anyref" =>
      Future.successful(ColossusResponse(HttpBody(new TestRef), HttpCodes.OK))
  }
}