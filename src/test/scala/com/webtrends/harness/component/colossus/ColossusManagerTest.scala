package com.webtrends.harness.component.colossus

import akka.testkit.TestProbe
import colossus.protocols.http.{HttpCodes, HttpMethod, HttpRequest}
import com.webtrends.harness.component.colossus.mock.MockColossusService
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth

import scala.concurrent.duration.FiniteDuration

class ColossusManagerTest extends MockColossusService {
  def commands = List()
  def wookieeService = None

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

    "use head creator" in {
      getHead("/url").method mustEqual HttpMethod.Get
    }
  }

  override def service = ColossusManager.getInternalServer
  override def requestTimeout = FiniteDuration(10000, "ms")
}
