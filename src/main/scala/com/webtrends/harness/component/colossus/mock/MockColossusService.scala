package com.webtrends.harness.component.colossus.mock

import colossus.protocols.http.{HttpHeaders, HttpMethod, HttpRequest, HttpRequestHead, HttpResponse, HttpVersion}
import colossus.testkit.HttpServiceSpec
import com.typesafe.config.ConfigFactory
import com.webtrends.harness.component.colossus.ColossusManager
import com.webtrends.harness.component.colossus.command.ColossusCommand
import com.webtrends.harness.service.Service
import com.webtrends.harness.service.test.TestHarness
import com.webtrends.harness.service.test.config.TestConfig

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration

trait MockColossusService extends HttpServiceSpec {
  val th = TestHarness(config, wookieeService, Some(Map("wookiee-colossus" -> classOf[ColossusManager])))
  val colManager = th.getComponent("wookiee-colossus").get

  // Config for component, override if you want to put in your own
  def config = ConfigFactory.parseResources("resources/reference.conf")
    .withFallback(TestConfig.conf("wookiee-colossus.service-name = \"test-service\""))

  // List of command names and their associated command classes
  def commands: List[(String, Class[_ <: ColossusCommand], List[Any])]

  // Service to start up, or None if we don't want to start one
  def wookieeService: Option[Map[String, Class[_ <: Service]]]

  // Automatically exclude commands with the same name as others in the suite from being recreated
  def noDuplicates: Boolean = true

  // Override this is trying to hit internal endpoints
  override def service = ColossusManager.getExternalServer

  // Command timeout
  override def requestTimeout = FiniteDuration(10000, "ms")

  // Use this method to get a response from any command you've registered, main work horse of class
  def returnResponse(request: HttpRequest): HttpResponse = {
    Await.result(client().send(request), requestTimeout)
  }

  // Convenience method to get an HttpRequestHead for requests
  def getHead(url: String, method: HttpMethod = HttpMethod.Get, headers: HttpHeaders = HttpHeaders.Empty): HttpRequestHead = {
    HttpRequestHead(method, url, HttpVersion.`1.1`, headers)
  }

  commands.foreach { c =>
    MockColossusService.createdCommands synchronized {
      if (!noDuplicates || !MockColossusService.createdCommands.contains(c._1)) {
        MockColossusService.createdCommands += c._1
        if (c._3.nonEmpty) colManager ! c else colManager ! (c._1, c._2)
      }
    }
  }
}

object MockColossusService {
  val createdCommands = mutable.HashSet[String]()
}
