package com.webtrends.harness.component.colossus.handle

import colossus.core.{AliveState, ServerContext}
import colossus.protocols.http.{HttpBody, HttpCodes, HttpHeader, HttpHeaders, HttpRequest, HttpResponse, HttpResponseHead, HttpVersion, RequestHandler}
import colossus.service.{Callback, ServiceConfig}
import com.webtrends.harness.component.colossus.http.Encoders
import com.webtrends.harness.component.colossus.{ColossusRouteContainer, ExternalColossusRouteContainer, InternalColossusRouteContainer}
import org.json4s.Formats
import org.json4s.jackson.Serialization

import scala.concurrent.ExecutionContext.Implicits
import scala.concurrent.ExecutionContextExecutor

class HttpRequestHandler(context: ServerContext,
                         config: ServiceConfig,
                         internal: Boolean)(implicit execution: ExecutionContextExecutor = Implicits.global)
  extends RequestHandler(context, config) with Encoders {
  import HttpBody._
  val serialization: Serialization.type = Serialization
  val container: ColossusRouteContainer = if (internal) InternalColossusRouteContainer else ExternalColossusRouteContainer

  // Main routing workhorse, goes through all routes we've currently registered
  override protected def handle: PartialFunction[HttpRequest, Callback[HttpResponse]] = {
    addRemoteHost.andThen(container.getRouteFunction.andThen[Callback[HttpResponse]]({ colResp =>
      Callback.fromFuture(colResp map { resp =>
        HttpResponse(HttpResponseHead(HttpVersion.`1.1`,
          resp.code, resp.headers + HttpHeader("Content-Type", resp.responseType)),
          marshall(resp.body, resp.formats, resp.responseType))
      })
    }).orElse[HttpRequest, Callback[HttpResponse]] { case req: HttpRequest =>
      Callback.successful(HttpResponse(HttpResponseHead(HttpVersion.`1.1`,
        HttpCodes.NOT_FOUND, HttpHeaders.Empty), s"This endpoint, ${req.head.path}, was not found."))
    })
  }

  // All marshalling takes place here, using the Formats found on the response (or DefaultFormats)
  def marshall(body: AnyRef, fmt: Formats, responseType: String): HttpBody = {
    body match {
      case t: Throwable => HttpBody(t)
      case hb: HttpBody => hb
      case _ =>
        responseType.split(";")(0).trim match {
          case "text/plain" | "text/xml" =>
            HttpBody(body.toString)
          case _ => body match {
            case s: String if s.isEmpty => HttpBody.NoBody
            case s: String => HttpBody(s)
            case _ => HttpBody(serialization.write(body)(fmt))
          }
        }
    }
  }

  def addRemoteHost: PartialFunction[HttpRequest, HttpRequest] = {
    case req =>
      connection.connectionState match {
        case state: AliveState => state
          .endpoint
          .remoteAddress
          .map(_.getHostString)
          .map(hostString => req.withHeader("Remote-Address", hostString)).getOrElse(req)
        case _ => req
      }
  }
}
