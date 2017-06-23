package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.HttpCodes
import colossus.protocols.http.HttpMethod.{Get, Post, Put}
import colossus.protocols.http.UrlParsing._
import com.webtrends.harness.command.Command

import scala.concurrent.Future

case class TestResponse(response: String)

class TestCommand extends Command with ColossusCommand {
  override def commandName = "TestCommand"
  override def routeExposure = RouteExposure.EXTERNAL

  override def matchedRoutes = {
    case req @ Get on Root / "goober" / key =>
      Future.successful(ColossusResponse("getResp" + key, HttpCodes.OK, "text/plain"))
    case req @(Post | Put) on Root / "goober" =>
      Future.successful(ColossusResponse(TestResponse("someResponse"), HttpCodes.OK))
    case req @ Get on Root / "failure" =>
      Future.successful(ColossusResponse(new Exception("some exception"), HttpCodes.BAD_REQUEST, "text/plain"))
  }
}
