package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.HttpRequest
import colossus.service.CallbackExecutor
import com.webtrends.harness.command.{Command, CommandBean}
import com.webtrends.harness.component.colossus.{ExternalColossusRouteContainer, InternalColossusRouteContainer}

import scala.concurrent.Future

object RouteExposure extends Enumeration {
  type RouteExposure = Value
  val INTERNAL, EXTERNAL, BOTH = Value
}

trait ColossusCommand extends Command {
  import RouteExposure._
  implicit val exec = context.dispatcher
  implicit val callbackExecutor = CallbackExecutor(context.dispatcher, self)

  // Whether this route will be accessible on the internal, external, or both ports
  def routeExposure: RouteExposure
  // A partial function filled with cases matching endpoints
  def matchedRoutes: PartialFunction[HttpRequest, Future[ColossusResponse]]

  def addRoutes(): Unit = {
    routeExposure match {
      case INTERNAL =>
        InternalColossusRouteContainer.addRoute(commandName, matchedRoutes)
      case EXTERNAL =>
        ExternalColossusRouteContainer.addRoute(commandName, matchedRoutes)
      case BOTH =>
        ExternalColossusRouteContainer.addRoute(commandName, matchedRoutes)
        InternalColossusRouteContainer.addRoute(commandName, matchedRoutes)
    }
  }

  // We don't use execute in Colossus Component
  override def execute[T](bean: Option[CommandBean])(implicit evidence$1: Manifest[T]) = ???

  addRoutes()
}
