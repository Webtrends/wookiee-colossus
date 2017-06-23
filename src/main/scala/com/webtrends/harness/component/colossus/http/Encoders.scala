package com.webtrends.harness.component.colossus.http

import akka.util.ByteString
import colossus.protocols.http.{HttpBody, HttpBodyEncoder, HttpBodyEncoders}

trait Encoders {
  implicit object ThrowableEncoder extends HttpBodyEncoder[Throwable] with HttpBodyEncoders {
    def encode(t: Throwable): HttpBody = ByteStringEncoder.encode(ByteString(t.getMessage))
  }
}
