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

import akka.actor._
import akka.io.{Tcp, IO}
import kamon.metric.{ActorMetrics, Metrics}
import kamon.{ModuleSupervisor, Kamon}
import spray.can.Http

trait DashboardExtension extends Kamon.Extension

object Dashboard extends ExtensionId[DashboardExtension] with ExtensionIdProvider {
  override def lookup = Dashboard
  override def createExtension(system: ExtendedActorSystem) = new DashboardExtensionImpl(system)
}


class DashboardExtensionImpl(system: ExtendedActorSystem) extends DashboardExtension {
  Kamon(ModuleSupervisor)(system).createModule("kamon-dashboard", Props[DashboardLauncher])
}


class DashboardLauncher extends Actor with ActorLogging {
  val config = context.system.settings.config.getConfig("kamon.dashboard")
  val listenInterface = config getString "listen-interface"
  val listenPort = config getInt "listen-port"
  val maxCacheSize = config getInt "max-cache-size"

  val snapshotsCache = subscribedToMetrics(context.actorOf(MarshalledSnapshotCache.props(maxCacheSize), "snapshots-cache"))
  val httpService = context.actorOf(HttpService.props(snapshotsCache), "http-service")

  IO(Http)(context.system) ! Http.Bind(httpService, listenInterface, listenPort)

  def receive = {
    case bound: Http.Bound =>
      log.info("Bound Kamon(Dashboard) to {}:{}", listenInterface, listenPort)
    case Tcp.CommandFailed(b: Http.Bind) =>
      log.info("Failed to bind Kamon(Dashboard) to {}:{}", listenInterface, listenPort)
  }

  def subscribedToMetrics(subscriber: ActorRef): ActorRef = {
    Kamon(Metrics)(context.system).subscribe(ActorMetrics, "*", subscriber, permanently = true)
    subscriber
  }
}