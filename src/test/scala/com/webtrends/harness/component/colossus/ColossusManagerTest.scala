package com.webtrends.harness.component.colossus

import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import com.webtrends.harness.service.test.TestHarness
import com.webtrends.harness.service.test.config.TestConfig
import org.scalatest.MustMatchers
import akka.testkit.TestProbe
import org.scalatest._

class ColossusManagerTest extends WordSpec with MustMatchers {
  val sys = TestHarness(ColossusManagerTest.config, None, Some(Map("wookiee-colossus" -> classOf[ColossusManager])))
  implicit val system = TestHarness.system.get
  val colManager = sys.getComponent("wookiee-colossus").get

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
  }
}

object ColossusManagerTest {
  val config = TestConfig.conf(
    """
      |wookiee-colossus {
      |  // For healthcheck, metric, lb and other endpoints
      |  internal-server {
      |    enabled = true
      |    interface = 0.0.0.0
      |    http-port = 8080
      |  }
      |
      |  external-server {
      |    interface = 0.0.0.0
      |    http-port = 8082
      |  }
      |
      |  manager = "com.webtrends.harness.component.colossus.ColossusManager"
      |  enabled = true
      |  dynamic-component = true
      |}
    """.stripMargin)
}
