/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.js

import akka.actor._
import akka.event.Logging
import akka.io.IO
import kamon.Kamon
import kamon.Kamon.Extension
import kamon.js.KamonJS.Metric
import spray.can.Http
import spray.json.DefaultJsonProtocol
import spray.routing
import spray.routing.HttpService

object KamonJS extends ExtensionId[KamonJSExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = KamonJS

  override def createExtension(system: ExtendedActorSystem): KamonJSExtension = new KamonJSExtension(system)

  case class Metric(id: String, label: String, value: Long)

  object MetricProtocol extends DefaultJsonProtocol {
    implicit val metricFormat = jsonFormat3(Metric)
  }
}

class KamonJSExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[KamonJSExtension])
  log.info("Starting the Kamon(JS) extension")

  val jsConfig = system.settings.config.getConfig("kamon.js")

  val interface = jsConfig.getString("interface")
  val port = jsConfig.getInt("port")

  val service = system.actorOf(Props[MetricReceiver], "kamon-dashboard-service")
  IO(Http)(system) ! Http.Bind(service, interface, port)
}

class MetricReceiver extends Actor with KamonJSMetricService {
  def actorRefFactory = context

  def receive = runRoute(metricRoute)
}

trait KamonJSMetricService extends HttpService {
  import kamon.js.KamonJS.MetricProtocol._

  val metricRoute = {
    path("metrics") {
      post {
        entity(as[Metric]) { metric ⇒
          recordMetric(metric)
        }
      }
    }
  }

  def recordMetric(metric: Metric): routing.Route = complete("")
}