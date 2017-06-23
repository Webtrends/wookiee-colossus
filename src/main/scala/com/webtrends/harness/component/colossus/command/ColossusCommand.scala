package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.HttpRequest
import colossus.service.{Callback, CallbackExecutor}
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
  // Post processing after any route done for this class
  def postProcessing(resp: ColossusResponse): ColossusResponse = resp

  // Convenience method to get matchedRoutes result as a Callback
  def callbackResponse(req: HttpRequest): Callback[ColossusResponse] = {
    Callback.fromFuture(matchedRoutes(req))
  }

  def addRoutes(): Unit = {
    routeExposure match {
      case INTERNAL =>
        InternalColossusRouteContainer.addRoute(commandName, matchedRoutes.andThen(res => res.map(postProcessing)))
      case EXTERNAL =>
        ExternalColossusRouteContainer.addRoute(commandName, matchedRoutes.andThen(res => res.map(postProcessing)))
      case BOTH =>
        ExternalColossusRouteContainer.addRoute(commandName, matchedRoutes.andThen(res => res.map(postProcessing)))
        InternalColossusRouteContainer.addRoute(commandName, matchedRoutes.andThen(res => res.map(postProcessing)))
    }
  }

  // We don't use execute in Colossus Component
  override def execute[T](bean: Option[CommandBean])(implicit evidence$1: Manifest[T]) = ???

  addRoutes()
}
