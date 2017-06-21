/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:16 PM
 */
package com.webtrends.harness.component.colossus

import akka.actor.ActorSystem
import colossus.IOSystem
import colossus.core.server.Server.ServerInfo
import colossus.core.server.ServerStatus.Bound
import colossus.core.{InitContext, ServerContext, ServerRef, ServerSettings}
import colossus.metrics.{MetricReporterConfig, MetricSystem, OpenTsdbSender}
import colossus.protocols.http.HttpHeaders
import colossus.protocols.http.server.{HttpServer, Initializer, RequestHandler}
import colossus.service.ServiceConfig
import com.webtrends.harness.command.{Command, CommandHelper}
import com.webtrends.harness.component.Component
import com.webtrends.harness.component.colossus.command.CoreColossusCommand
import com.webtrends.harness.component.colossus.handle.HttpRequestHandler
import com.webtrends.harness.health.{ComponentState, HealthComponent}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future, Promise}
import scala.util.{Failure, Success, Try}

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
  override def receive: Receive = super.receive orElse {
    // Add a command to our http routing
    case (s: String, c: Class[_]) if classOf[Command].isAssignableFrom(c) =>
      addCommand[Command](s, c.asInstanceOf[Class[Command]])
  }

  override def start = {
    init()
    super.start
  }


  override def stop = {
    ColossusManager.internalServerRef.foreach(_.die())
    ColossusManager.externalServerRef.foreach(_.die())
    super.stop
  }

  def init(): Unit = {
    val colConfig = config.getConfig(ColossusManager.ComponentName)
    val serviceName = colConfig.getString("service-name")
    val metricsEnabled = Try(colConfig.getBoolean("metric.enabled")).getOrElse(false)
    val metricsName = Try(colConfig.getString("metric.name")).getOrElse(serviceName)
    val metricsHost = Try(colConfig.getString("metric.host"))
      .getOrElse(if (metricsEnabled) throw new Exception("Must set metric.host if using metrics") else "")
    val metricsPort = Try(colConfig.getInt("metric.port")).getOrElse(4242)
    val serverConfig = colConfig.getConfig("server")

    val internalServerSettings =
      ServerSettings.extract(serverConfig.withFallback(colConfig.getConfig("internal-server")))
    val externalServerSettings =
      ServerSettings.extract(serverConfig.withFallback(colConfig.getConfig("external-server")))
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

    ColossusManager.internalServerRef = Some(HttpServer.start(serviceName + "_internal",
      internalServerSettings)(serverInit(serviceConfig, internal = true)))
    ColossusManager.externalServerRef = Some(HttpServer.start(serviceName + "_external",
      externalServerSettings)(serverInit(serviceConfig, internal = false)))
    addCommand(CoreColossusCommand.CommandName, classOf[CoreColossusCommand])
  }

  override def getHealth: Future[HealthComponent] = {
    val intHealth = ColossusManager.internalServerRef.map(serverHealth).getOrElse(notStartedHealth)
    val extHealth = ColossusManager.externalServerRef.map(serverHealth).getOrElse(notStartedHealth)
    val p = Promise[HealthComponent]()
    Future.sequence(List(intHealth, extHealth)) onComplete {
      case Success(succ) =>
        p success HealthComponent(self.path.toString, details = "Colossus Component Up", components = succ)
      case Failure(f) =>
        p success HealthComponent(self.path.toString, ComponentState.CRITICAL, "Could not get health of servers")
    }
    p.future
  }

  private val notStartedHealth = Future.successful(
    HealthComponent(self.path.toString, ComponentState.CRITICAL, "could not find colossus server"))
  private def serverHealth: ServerRef => Future[HealthComponent] = { serverRef =>
    serverRef
      .info()
      .map {
        case ServerInfo(openConnections, Bound) =>
          HealthComponent(
            serverRef.name.idString,
            ComponentState.NORMAL,
            s"colossus server: ServerInfo(openConnections=$openConnections, status=$Bound)"
          )
        case info =>
          HealthComponent(
            serverRef.name.idString,
            ComponentState.CRITICAL,
            s"colossus server: ServerInfo(openConnections=${info.openConnections}, status=${info.status})"
          )
      }
  }

  private def serverInit(serviceConfig: ServiceConfig, internal: Boolean): InitContext => Initializer = { init =>
    new Initializer(init) {
      override val defaultHeaders: HttpHeaders = HttpHeaders()
      override def onConnect: (ServerContext) => RequestHandler = serverContext => new HttpRequestHandler(
        serverContext, serviceConfig, internal
      )
    }
  }
}

object ColossusManager {
  val ComponentName = "wookiee-colossus"
  protected[colossus] var internalServerRef: Option[ServerRef] = None
  protected[colossus] var externalServerRef: Option[ServerRef] = None

  def getInternalServer = internalServerRef.getOrElse(throw new IllegalStateException("Internal Colossus server not initialized"))
  def getExternalServer = externalServerRef.getOrElse(throw new IllegalStateException("External Colossus server not initialized"))
}