package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.{HttpCode, HttpCodes, HttpHeaders, HttpRequest}
import com.webtrends.harness.command.{BaseCommand, CommandBean}
import com.webtrends.harness.component.colossus.ExternalColossusRouteContainer
import org.json4s.{DefaultFormats, Formats}

import scala.concurrent.Future

case class ColossusResponse(body: AnyRef,
                            code: HttpCode = HttpCodes.OK,
                            responseType: String = "application/json",
                            headers: HttpHeaders = HttpHeaders.Empty)(implicit val formats: Formats = DefaultFormats)

trait ColossusCommand { this: BaseCommand =>
  def matchedRoutes: PartialFunction[HttpRequest, Future[ColossusResponse]]

  def addRoutes(): Unit = {
    ExternalColossusRouteContainer.addRoute(matchedRoutes)
  }

  // We don't use execute in Colossus Component
  override def execute[T](bean: Option[CommandBean])(implicit evidence$1: Manifest[T]) = ???

  addRoutes()
}
