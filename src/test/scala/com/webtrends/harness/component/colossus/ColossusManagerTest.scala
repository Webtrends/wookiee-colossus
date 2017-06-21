package com.webtrends.harness.component.colossus

import akka.testkit.TestProbe
import colossus.protocols.http.{HttpCodes, HttpRequest}
import colossus.testkit.HttpServiceSpec
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import com.webtrends.harness.service.test.TestHarness
import com.webtrends.harness.service.test.config.TestConfig

import scala.concurrent.duration.FiniteDuration

class ColossusManagerTest extends HttpServiceSpec {
  val th = TestHarness(ColossusManagerTest.config, None, Some(Map("wookiee-colossus" -> classOf[ColossusManager])))
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

  override def service = ColossusManager.getInternalServer
  override def requestTimeout = FiniteDuration(10000, "ms")
}

object ColossusManagerTest {
  val config = ConfigFactory.parseResources("resources/reference.conf").withFallback(TestConfig.conf("wookiee-colossus.service-name = \"test-service\""))
}
