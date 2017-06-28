package com.webtrends.harness.component.colossus

import colossus.protocols.http.{HttpCodes, HttpRequest}
import com.webtrends.harness.component.colossus.command.ColossusResponse

import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

object ExternalColossusRouteContainer extends ColossusRouteContainer
object InternalColossusRouteContainer extends ColossusRouteContainer

trait ColossusRouteContainer {
  private val routes = TrieMap[String, PartialFunction[HttpRequest, Future[ColossusResponse]]]()
  private var routeFunction: PartialFunction[HttpRequest, Future[ColossusResponse]] = {
    case _ =>
      Future.successful(ColossusResponse("No endpoints defined", HttpCodes.NOT_IMPLEMENTED, "text/plain"))
  }

  // Call this to add your route handler to the list of handlers
  def addRoute(commandName: String, route: PartialFunction[HttpRequest, Future[ColossusResponse]]) = {
    if (!routes.contains(commandName)) {
      routes.put(commandName, route)
      routeFunction = if (routes.size == 1) {
        routes.values.head
      } else routes.values.tail.foldLeft(routes.values.head)((a, b) => a.orElse(b))
    }
  }

  // Returns the route function we use to evaluate requests
  def getRouteFunction: PartialFunction[HttpRequest, Future[ColossusResponse]] = routeFunction
}
