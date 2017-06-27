package com.webtrends.harness.component.colossus

import akka.testkit.TestProbe
import colossus.protocols.http.{HttpCodes, HttpMethod, HttpRequest}
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.component.colossus.command.TestCommandBoth
import com.webtrends.harness.component.colossus.mock.MockColossusService
import com.webtrends.harness.health.{ComponentState, HealthComponent}
import com.webtrends.harness.service.messages.CheckHealth
import org.scalatest.DoNotDiscover

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.util.Success

@DoNotDiscover
class ColossusManagerTest extends MockColossusService {
  def commands = List(("TestCommandBothInt", classOf[TestCommandBoth], List("Input")))
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

    "be able to hit a BOTH command" in {
      val resp = returnResponse(HttpRequest.get("/goober"))
      resp.code mustEqual HttpCodes.OK
      resp.body.toString() mustEqual "{\"response\":\"someResponseInput\"}"
    }

    "IO init with metrics enabled" in {
      val conf = ConfigFactory.parseString(
        """
          |metric {
          | name = "test"
          | host = "host"
          | port = 4545
          |}
        """.stripMargin)
      ColossusManager.getIOSystem("IOTest", conf, metricsEnabled = true)
    }

    "get not started health if server not up" in {
      ColossusManager.serverHealth(None) onComplete {
        case Success(hc) =>
          hc.state.equals(ComponentState.CRITICAL) mustEqual true
      }
    }
  }

  override def service = ColossusManager.getInternalServer
  override def requestTimeout = FiniteDuration(10000, "ms")
}
