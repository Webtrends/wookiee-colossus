package com.webtrends.harness.component.colossus.http

import colossus.protocols.http.{HttpRequest, _}
import com.webtrends.harness.component.colossus.command.ColossusResponse
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future


trait AllowedHeaders {
  def matching(reqHeaders: Seq[String]): Seq[String]
}

object AllowedHeaders {
  case object `*` extends AllowedHeaders {
    override def matching(reqHeaders: Seq[String]): Seq[String] = reqHeaders
  }

  final case class Matching(headers: String*) extends AllowedHeaders {
    override def matching(reqHeaders: Seq[String]): Seq[String] =
      reqHeaders.map(_.toLowerCase) intersect headers.map(_.toLowerCase)
  }
}


final case class Settings(
                           allowCredentials: Boolean = true,
                           allowedMethods: Seq[HttpMethod] = Seq.empty,
                           accessControlAllowHeaders: AllowedHeaders = AllowedHeaders.`*`)

trait CORS extends HttpBodyEncoders {
  def cors(s: Settings, inner: PartialFunction[HttpRequest, Future[ColossusResponse]])
  : PartialFunction[HttpRequest, Future[ColossusResponse]] = {
    case req =>
      (req.head.method, req.head.headers.firstValue("Origin"), req.head.headers.firstValue("Access-Control-Request-Method")) match {
        case (HttpMethod.Options, Some(origin), Some(requestMethod)) => // Case 1: pre-flight request
          Future.successful {
            val allowedMethods = s.allowedMethods match {
              case meth if meth.nonEmpty => meth
              case _ => Seq(req.head.method)
            }
            val res = ColossusResponse(HttpBody.NoBody, HttpCodes.OK, headers = HttpHeaders(
              HttpHeader("Access-Control-Allow-Origin", origin),
              HttpHeader("Access-Control-Allow-Credentials", s.allowCredentials.toString.toLowerCase),
              HttpHeader("Access-Control-Allow-Methods", allowedMethods.map(_.name.toUpperCase).mkString(","))
            ))
            val reqHeaders = req.head.headers
              .firstValue("Access-Control-Request-Headers")
              .map(_.split(","))
              .getOrElse(Array.empty)
              .map(_.trim)

            s.accessControlAllowHeaders.matching(reqHeaders) match {
              case Nil => res
              case matchingHeaders =>
                res.copy(headers = res.headers +
                  HttpHeader("Access-Control-Allow-Headers", matchingHeaders.mkString(", ")))(res.formats)
            }
          }
        case (_ , Some(origin), None) => // Case 2: actual CORS request
          inner(req).map(r => r.copy(headers = r.headers + HttpHeader("Access-Control-Allow-Origin", origin) +
              HttpHeader("Access-Control-Allow-Credentials", s.allowCredentials.toString.toLowerCase()))(r.formats))
        case _ => // Case 3: regular request
          inner(req)
      }
  }
}
