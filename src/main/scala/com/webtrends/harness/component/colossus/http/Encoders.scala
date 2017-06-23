package com.webtrends.harness.component.colossus.http

import akka.util.ByteString
import colossus.protocols.http.{HttpBody, HttpBodyEncoder, HttpBodyEncoders}

trait Encoders {
  implicit object AnyRefEncoder extends HttpBodyEncoder[AnyRef] {
    import org.json4s.jackson.Serialization.write
    import org.json4s.{DefaultFormats, Formats}

    implicit val formats: Formats = DefaultFormats

    override def encode(data: AnyRef): HttpBody = data match {
      case s: String => HttpBody(write(data))
      case x => HttpBody(data)
    }
  }

  implicit object ThrowableEncoder extends HttpBodyEncoder[Throwable] with HttpBodyEncoders {
    def encode(t: Throwable): HttpBody = ByteStringEncoder.encode(ByteString(t.getMessage))
  }
}
