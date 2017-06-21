package com.webtrends.harness.component.colossus

import java.util.Collections

import colossus.protocols.http.{HttpCodes, HttpRequest}
import com.webtrends.harness.component.colossus.command.ColossusResponse

import scala.collection.JavaConverters._
import scala.concurrent.Future

object ExternalColossusRouteContainer {
  private val routes = Collections.synchronizedSet[PartialFunction[HttpRequest, Future[ColossusResponse]]](
    new java.util.HashSet[PartialFunction[HttpRequest, Future[ColossusResponse]]]())

  private var routeFunction: PartialFunction[HttpRequest, Future[ColossusResponse]] = {
    case _ =>
      Future.successful(ColossusResponse("No endpoints defined", HttpCodes.NOT_IMPLEMENTED, "text/plain"))
  }

  // Call this to add your route handler to the list of handlers
  def addRoute(route: PartialFunction[HttpRequest, Future[ColossusResponse]]) = {
    routes.add(route)
    routeFunction = if (routes.size() == 1) {
      routes.asScala.head
    } else routes.asScala.tail.foldLeft(routes.asScala.head)((a, b) => a.orElse(b))
  }

  // Returns the route function we use to evaluate requests
  def getRouteFunction: PartialFunction[HttpRequest, Future[ColossusResponse]] = routeFunction
}
