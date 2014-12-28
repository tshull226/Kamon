/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.dashboard

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.io.{Tcp, IO}
import com.typesafe.config.Config
import kamon.metric.{ActorMetrics, Metrics}
import kamon.{NanoInterval, ModuleSupervisor, Kamon}
import spray.can.Http

trait DashboardExtension extends Kamon.Extension

object Dashboard extends ExtensionId[DashboardExtension] with ExtensionIdProvider {
  override def lookup = Dashboard
  override def createExtension(system: ExtendedActorSystem) = new DashboardExtensionImpl(system)
}

case class DashboardSettings(listenInterface: String, listenPort: Int, cachedInterval: NanoInterval)

object DashboardSettings {
  def fromConfig(globalConfig: Config): DashboardSettings = {
    val config = globalConfig.getConfig("kamon.dashboard")

    DashboardSettings(
      config getString "listen-interface",
      config getInt "listen-port",
      new NanoInterval(config getDuration("cached-interval", TimeUnit.NANOSECONDS))
    )
  }
}


class DashboardExtensionImpl(system: ExtendedActorSystem) extends DashboardExtension {
  Kamon(ModuleSupervisor)(system).createModule("dashboard", Props[DashboardLauncher])
}


class DashboardLauncher extends Actor with ActorLogging {
  val dashboardSettings = DashboardSettings.fromConfig(context.system.settings.config)

  val snapshotsCache = subscribedToMetrics(context.actorOf(MarshalledSnapshotCache.props(dashboardSettings), "snapshots-cache"))
  val httpService = context.actorOf(HttpService.props(snapshotsCache, dashboardSettings), "http-service")

  IO(Http)(context.system) ! Http.Bind(httpService, dashboardSettings.listenInterface, dashboardSettings.listenPort)

  def receive = {
    case bound: Http.Bound =>
      log.info("Bound Kamon(Dashboard) to {}:{}", dashboardSettings.listenInterface, dashboardSettings.listenPort)
    case Tcp.CommandFailed(b: Http.Bind) =>
      log.info("Failed to bind Kamon(Dashboard) to {}:{}", dashboardSettings.listenInterface, dashboardSettings.listenPort)
  }

  def subscribedToMetrics(subscriber: ActorRef): ActorRef = {
    Kamon(Metrics)(context.system).subscribe(ActorMetrics, "*", subscriber, permanently = true)
    subscriber
  }
}