package com.webtrends.harness.component.colossus.http

import akka.util.ByteString
import colossus.protocols.http.{HttpBody, HttpBodyEncoder, HttpBodyEncoders}

trait Encoders {
  implicit object AnyRefEncoder extends HttpBodyEncoder[AnyRef] {
    import org.json4s.{DefaultFormats, Formats}

    implicit val formats: Formats = DefaultFormats

    override def contentType: Option[String] = None

    override def encode(data: AnyRef): HttpBody = HttpBody(data.toString)
  }

  implicit object ThrowableEncoder extends HttpBodyEncoder[Throwable] with HttpBodyEncoders {

    override def contentType: Option[String] = None

    def encode(t: Throwable): HttpBody = ByteStringEncoder.encode(ByteString(t.getMessage))
  }
}
