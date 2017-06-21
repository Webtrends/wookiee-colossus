package com.webtrends.harness.component.colossus.handle

import colossus.core.ServerContext
import colossus.protocols.http.server.RequestHandler
import colossus.protocols.http.{HttpBody, HttpCodes, HttpHeaders, HttpRequest, HttpResponse, HttpResponseHead, HttpVersion}
import colossus.service.{Callback, ServiceConfig}
import com.webtrends.harness.component.colossus.{ExternalColossusRouteContainer, InternalColossusRouteContainer}
import org.json4s.Formats
import org.json4s.jackson.Serialization

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.ExecutionContextExecutor

class HttpRequestHandler(context: ServerContext,
                         config: ServiceConfig,
                         internal: Boolean)(implicit execution: ExecutionContextExecutor = Implicits.global)
  extends RequestHandler(context, config) {
  import HttpBody._
  val serialization = Serialization
  val container = if (internal) InternalColossusRouteContainer else ExternalColossusRouteContainer

  // Main routing workhorse, goes through all routes we've currently registered
  override protected def handle: PartialFunction[HttpRequest, Callback[HttpResponse]] = {
    container.getRouteFunction.andThen[Callback[HttpResponse]]({ colResp =>
      Callback.fromFuture(colResp map { resp =>
        HttpResponse(HttpResponseHead(HttpVersion.`1.1`,
          resp.code, resp.headers), marshall(resp.body, resp.formats, resp.responseType))
      })
    }).orElse[HttpRequest, Callback[HttpResponse]] { case req: HttpRequest =>
      Callback.successful(HttpResponse(HttpResponseHead(HttpVersion.`1.1`,
        HttpCodes.NOT_FOUND, HttpHeaders.Empty), s"This endpoint, ${req.head.path}, was not found."))
    }
  }

  // All marshalling takes place here, using the Formats found on the response (or DefaultFormats)
  def marshall(body: AnyRef, fmt: Formats, responseType: String) = responseType match {
    case "text/plain" => body.toString
    case _ => serialization.write(body)(fmt)
  }
}
