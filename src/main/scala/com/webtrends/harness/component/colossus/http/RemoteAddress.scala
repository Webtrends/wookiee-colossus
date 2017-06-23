package com.webtrends.harness.component.colossus.http

import java.net.InetAddress

import colossus.protocols.http.HttpHeader
import com.webtrends.harness.command.Command
import com.webtrends.harness.component.colossus.command.{ColossusCommand, ColossusResponse}

trait RemoteAddress extends ColossusCommand { this: Command =>
  override def postProcessing(resp: ColossusResponse) = {
    super.postProcessing(resp)
    resp.copy(headers = resp.headers +
      HttpHeader("Remote-Address", InetAddress.getLocalHost.getHostAddress))(resp.formats)
  }
}
