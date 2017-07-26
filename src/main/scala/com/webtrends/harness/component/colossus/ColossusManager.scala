/*
 * Copyright (c) 2014. Webtrends (http://www.webtrends.com)
 * @author cuthbertm on 11/20/14 12:16 PM
 */
package com.webtrends.harness.component.colossus

import akka.actor.{ActorSystem, Props}
import colossus.IOSystem
import colossus.core.server.Server.ServerInfo
import colossus.core.server.ServerStatus.Bound
import colossus.core.{InitContext, ServerContext, ServerRef, ServerSettings}
import colossus.metrics.{MetricReporterConfig, MetricSystem, OpenTsdbSender}
import colossus.protocols.http.HttpHeaders
import colossus.protocols.http.server.{HttpServer, Initializer, RequestHandler}
import colossus.service.ServiceConfig
import com.typesafe.config.Config
import com.webtrends.harness.command.{Command, CommandHelper}
import com.webtrends.harness.component.Component
import com.webtrends.harness.component.colossus.ColossusManager._
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
    case (s: String, c: Class[_], args: List[Any]) if classOf[Command].isAssignableFrom(c) =>
      addCommandWithProps(s, Props(c, args :_*))
  }

  override def start = {
    init()
    addCommand(CoreColossusCommand.CommandName, classOf[CoreColossusCommand])
    super.start
  }

  def init(): Unit = {
    val colConfig = config.getConfig(ComponentName)
    val serviceName = colConfig.getString("service-name")
    val metricsEnabled = Try(colConfig.getBoolean("metric.enabled")).getOrElse(false)
    val serverConfig = colConfig.getConfig("server")
    val intServerConfig = colConfig.getConfig("internal-server")
    val extServerConfig = colConfig.getConfig("external-server")

    val internalServerSettings = ServerSettings.extract(intServerConfig.withFallback(serverConfig))
    val internalServiceConfig = ServiceConfig.load(intServerConfig.withFallback(colConfig.getConfig("service.default")))

    val externalServerSettings = ServerSettings.extract(extServerConfig.withFallback(serverConfig))
    val externalServiceConfig = ServiceConfig.load(extServerConfig.withFallback(colConfig.getConfig("service.default")))

    implicit val io: IOSystem = getIOSystem(serviceName, colConfig, metricsEnabled)

    internalServerRef = Some(HttpServer.start(serviceName + "_internal",
      internalServerSettings)(serverInit(internalServiceConfig, internal = true)))
    externalServerRef = Some(HttpServer.start(serviceName + "_external",
      externalServerSettings)(serverInit(externalServiceConfig, internal = false)))
  }

  override def stop = {
    ColossusManager.internalServerRef.foreach(_.die())
    ColossusManager.externalServerRef.foreach(_.die())
    super.stop
  }

  override def getHealth: Future[HealthComponent] = {
    val intHealth = serverHealth(ColossusManager.internalServerRef)
    val extHealth = serverHealth(ColossusManager.externalServerRef)
    val p = Promise[HealthComponent]()
    Future.sequence(List(intHealth, extHealth)) onComplete {
      case Success(succ) =>
        p success HealthComponent(ComponentName, details = "Colossus Component Up", components = succ)
      case Failure(f) =>
        p success HealthComponent(ComponentName, ComponentState.CRITICAL, "Could not get health of servers")
    }
    p.future
  }
}

object ColossusManager {
  val ComponentName = "wookiee-colossus"
  protected[colossus] var internalServerRef: Option[ServerRef] = None
  protected[colossus] var externalServerRef: Option[ServerRef] = None

  def getInternalServer = internalServerRef.get
  def getExternalServer = externalServerRef.get

  def getIOSystem(serviceName: String, config: Config,
                          metricsEnabled: Boolean)(implicit system: ActorSystem): IOSystem = {
    if (metricsEnabled) {
      val metricsName = Try(config.getString("metric.name")).getOrElse(serviceName)
      val metricsHost = config.getString("metric.host")
      val metricsPort = config.getInt("metric.port")
      val colossusMetricSystem = MetricSystem(metricsName)
      val colossusMetricReporterConfig = MetricReporterConfig(Seq(OpenTsdbSender(
        metricsHost, metricsPort
      )))
      colossusMetricSystem.collectionIntervals.get(1.minute).foreach(_.report(colossusMetricReporterConfig))
      IOSystem(metricsName, config, Some(colossusMetricSystem))
    } else IOSystem()
  }

  def serverHealth(serverRef: Option[ServerRef])(implicit ec: ExecutionContextExecutor): Future[HealthComponent] = serverRef match {
    case Some(ref) => ref.info()
      .map {
        case ServerInfo(openConnections, Bound) =>
          HealthComponent(
            ref.name.idString,
            ComponentState.NORMAL,
            s"colossus server: ServerInfo(openConnections=$openConnections, status=$Bound)"
          )
        case info =>
          HealthComponent(
            ref.name.idString,
            ComponentState.CRITICAL,
            s"colossus server: ServerInfo(openConnections=${info.openConnections}, status=${info.status})"
          )
      }
    case None => notStartedHealth
  }

  private val notStartedHealth = Future.successful(
    HealthComponent(ComponentName, ComponentState.CRITICAL, "could not find colossus server"))

  private def serverInit(serviceConfig: ServiceConfig, internal: Boolean): InitContext => Initializer = { init =>
    new Initializer(init) {
      override val defaultHeaders: HttpHeaders = HttpHeaders()
      override def onConnect: (ServerContext) => RequestHandler = serverContext => new HttpRequestHandler(
        serverContext, serviceConfig, internal
      )
    }
  }
}