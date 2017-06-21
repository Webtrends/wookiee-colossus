/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:16 PM
 */
package com.webtrends.harness.component.colossus

import akka.actor.ActorSystem
import colossus.IOSystem
import colossus.core.{ServerContext, ServerRef, ServerSettings}
import colossus.metrics.{MetricReporterConfig, MetricSystem, OpenTsdbSender}
import colossus.protocols.http.server.{HttpServer, Initializer, RequestHandler}
import colossus.protocols.http.{HttpHeaders, _}
import colossus.service.{Callback, ServiceConfig}
import com.webtrends.harness.command.CommandHelper
import com.webtrends.harness.component.Component
import com.webtrends.harness.component.colossus.command.CoreColossusCommand
import org.json4s.Formats
import org.json4s.jackson.Serialization

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.Try
import scala.concurrent.ExecutionContext.Implicits

class ColossusManager(name:String) extends Component(name) with CommandHelper {
  implicit val system: ActorSystem = context.system
  implicit val ec: ExecutionContextExecutor = system.dispatcher

  /**
   * We add super.receive because if you override the receive message from the component
   * and then do not include super.receive it will not handle messages from the
   * ComponentManager correctly and basically not start up properly
   *
   * @return
   */
  override def receive: Receive = super.receive

  override def start = {
    init()
    super.start
  }

  def init(): Unit = {
    val colConfig = config.getConfig(ColossusManager.ComponentName)
    val serviceName = colConfig.getString("service-name")
    val metricsEnabled = Try(colConfig.getBoolean("metric.enabled")).getOrElse(false)
    val metricsName = Try(colConfig.getString("metric.name")).getOrElse(serviceName)
    val metricsHost = Try(colConfig.getString("metric.host"))
      .getOrElse(if (metricsEnabled) throw new Exception("Must set metric.host if using metrics") else "")
    val metricsPort = Try(colConfig.getInt("metric.port")).getOrElse(4242)
    val serverSettings = ServerSettings.extract(colConfig.getConfig("server"))
    val serviceConfig = ServiceConfig.load(colConfig.getConfig("service.default"))

    implicit val io: IOSystem = if (metricsEnabled) {
      val colossusMetricSystem = MetricSystem(metricsName)
      val colossusMetricReporterConfig = MetricReporterConfig(Seq(OpenTsdbSender(
        metricsHost, metricsPort
      )))
      colossusMetricSystem.collectionIntervals.get(1.minute).foreach(_.report(colossusMetricReporterConfig))
      IOSystem(metricsName, config, Some(colossusMetricSystem))
    } else {
      IOSystem()
    }

    ColossusManager.serverRef = Some(HttpServer.start(serviceName, serverSettings) { init =>
      new Initializer(init) {
        override val defaultHeaders: HttpHeaders = HttpHeaders()
        override def onConnect: (ServerContext) => RequestHandler = serverContext => new HttpRequestHandler(
          serverContext,
          serviceConfig
        )
      }
    })
    addCommand(CoreColossusCommand.CommandName, classOf[CoreColossusCommand])
  }
}

object ColossusManager {
  val ComponentName = "wookiee-colossus"
  protected[colossus] var serverRef: Option[ServerRef] = None

  def getServer = serverRef.getOrElse(throw new IllegalStateException("Colossus server not initialized"))
}

class HttpRequestHandler(context: ServerContext,
                         config: ServiceConfig)(implicit execution: ExecutionContextExecutor = Implicits.global)
  extends RequestHandler(context, config) {
  import HttpBody._
  val serialization = Serialization

  override protected def handle: PartialFunction[HttpRequest, Callback[HttpResponse]] = {
    ExternalColossusRouteContainer.getRouteFunction.andThen[Callback[HttpResponse]]({ colResp =>
      Callback.fromFuture(colResp map { resp =>
        HttpResponse(HttpResponseHead(HttpVersion.`1.1`,
          resp.code, resp.headers), marshall(resp.body, resp.formats, resp.responseType))
      })
    }).orElse[HttpRequest, Callback[HttpResponse]] { case req: HttpRequest =>
      Callback.successful(HttpResponse(HttpResponseHead(HttpVersion.`1.1`,
        HttpCodes.NOT_FOUND, HttpHeaders.Empty), s"This endpoint, ${req.head.path}, was not found."))
    }
  }

  def marshall(body: AnyRef, fmt: Formats, responseType: String) = responseType match {
    case "text/plain" => body.toString
    case _ => serialization.write(body)(fmt)
  }
}