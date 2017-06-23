package com.webtrends.harness.component.colossus.command

import colossus.protocols.http.{HttpCode, HttpCodes, HttpHeaders}
import org.json4s.{DefaultFormats, Formats}

case class ColossusResponse(body: AnyRef,
                            code: HttpCode = HttpCodes.OK,
                            responseType: String = "application/json",
                            headers: HttpHeaders = HttpHeaders.Empty)(implicit val formats: Formats = DefaultFormats)

object ColossusResponse {
  def badRequest(message: Option[String] = None)(implicit formats: Formats = DefaultFormats) = {
    val mess = message.getOrElse("")
    ColossusResponse(mess, HttpCodes.BAD_REQUEST)
  }
}
