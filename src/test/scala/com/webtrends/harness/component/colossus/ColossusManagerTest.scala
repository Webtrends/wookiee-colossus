package com.webtrends.harness.component.colossus

import akka.testkit.TestProbe
import colossus.protocols.http.{HttpCodes, HttpRequest}
import colossus.testkit.HttpServiceSpec
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import com.webtrends.harness.service.test.TestHarness
import com.webtrends.harness.service.test.config.TestConfig

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

class ColossusManagerTest extends HttpServiceSpec {
  val th = TestHarness(ColossusManagerTest.config, None, Some(Map("wookiee-colossus" -> classOf[ColossusManager])))
  //implicit val thSystem = TestHarness.system.get
  val colManager = th.getComponent("wookiee-colossus").get

  "ColossusManager" should {
    "be ready" in {
      val probe = TestProbe()
      probe.send(colManager, CheckHealth)
      probe.expectMsgPF() {
        case health: HealthComponent  =>
          health.state mustEqual ComponentState.NORMAL
        case _ =>
          false mustEqual true
      }
    }

    "handle health check request" in {
      expectCode(HttpRequest.get("/healthcheck"), HttpCodes.OK)
    }

    "handle not found case" in {
      expectCode(HttpRequest.get("/nonexistent"), HttpCodes.NOT_FOUND)
    }
  }

  override def service = ColossusManager.getServer
  override def requestTimeout = FiniteDuration(10000, "ms")
}

object ColossusManagerTest {
  val config = TestConfig.conf(
    """
      |wookiee-colossus {
      |  service-name = "TestColossus"
      |  metric {
      |      enabled = false
      |      name = "Colossus"
      |      host = "graph.host.name"
      |      port = 4242
      |    }
      |
      |    server {
      |      port = 9888
      |      max-connections = 10000
      |      max-idle-time = 3 seconds
      |      highwater-max-idle-time = 3 seconds
      |      shutdown-timeout = 5 seconds
      |      tcp-backlog-size = 10000
      |      low-watermark-percentage = 0.5
      |      high-watermark-percentage = 0.9
      |      slow-start {
      |        enabled = false
      |        initial = 10000
      |        duration = 1 second
      |      }
      |      binding-retry {
      |        type = "NONE"
      |      }
      |      delegator-creation-policy {
      |        wait-time = 5 seconds
      |        retry-policy.type = "NONE"
      |      }
      |    }
      |
      |    service.default {
      |      request-timeout = 10 seconds
      |      request-metrics = false
      |      request-buffer-size = 100
      |      log-errors = true
      |      max-request-size = 50 MB
      |    }
      |
      |  manager = "com.webtrends.harness.component.colossus.ColossusManager"
      |  enabled = true
      |  dynamic-component = true
      |}
    """.stripMargin)
}
