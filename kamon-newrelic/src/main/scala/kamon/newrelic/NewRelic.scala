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

package kamon.newrelic

import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit.{ MILLISECONDS ⇒ milliseconds }

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.UserMetrics.{ UserCounters, UserGauges, UserHistograms, UserMinMaxCounters }
import kamon.metric.{ Metrics, TickMetricSnapshotBuffer, TraceMetrics }

import scala.concurrent.duration._

class NewRelicExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val config = system.settings.config.getConfig("kamon.newrelic")

  val collectionContext = Kamon(Metrics)(system).buildDefaultCollectionContext
  val metricsListener = system.actorOf(Props[NewRelicMetricsListener], "kamon-newrelic")
  val apdexT: Double = config.getDuration("apdexT", milliseconds) / 1E3 // scale to seconds.

  Kamon(Metrics)(system).subscribe(TraceMetrics, "*", metricsListener, permanently = true)

  // Subscribe to all user metrics
  Kamon(Metrics)(system).subscribe(UserHistograms, "*", metricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(UserCounters, "*", metricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(UserMinMaxCounters, "*", metricsListener, permanently = true)
  Kamon(Metrics)(system).subscribe(UserGauges, "*", metricsListener, permanently = true)

  object Settings {
    val AppName = config.getString("app-name")
    val LicenseKey = config.getString("license-key")

    val RetryDelay = FiniteDuration(config.getDuration("retry-delay", milliseconds), milliseconds)
    val MaxRetry = config.getInt("max-retry")
    implicit val Dispatcher = system.dispatchers.lookup(config.getString("dispatcher"))
  }

}

class NewRelicMetricsListener extends Actor with ActorLogging {
  log.info("Starting the Kamon(NewRelic) extension")

  val collector = context.actorOf(NewRelicCollector.props, "collector")
  val agent = context.actorOf(NewRelicMetricReporter.props, "agent")
  val translator = context.actorOf(MetricTranslator.props(agent), "translator")
  val buffer = context.actorOf(TickMetricSnapshotBuffer.props(1 minute, translator), "metric-buffer")

  def receive = {
    case tick: TickMetricSnapshot ⇒ buffer.forward(tick)
  }
}

object NewRelic extends ExtensionId[NewRelicExtension] with ExtensionIdProvider {

  def lookup(): ExtensionId[_ <: Extension] = NewRelic
  def createExtension(system: ExtendedActorSystem): NewRelicExtension = new NewRelicExtension(system)

  case class Metric(name: String, scope: Option[String], callCount: Long, total: Double, totalExclusive: Double,
      min: Double, max: Double, sumOfSquares: Double) {

    def merge(that: Metric): Metric = {
      Metric(name, scope,
        callCount + that.callCount,
        total + that.total,
        totalExclusive + that.totalExclusive,
        math.min(min, that.min),
        math.max(max, that.max),
        sumOfSquares + that.sumOfSquares)
    }
  }

  case class Error(timestamp:Long, errorMessage: String, stackTrace: Option[Seq[String]], customParams: Option[Map[String, String]], requestUri: String)

  object Error {
    def apply(error:akka.event.Logging.Error, customParams: Option[Map[String, String]], requestUri: String) = {
      def cause = {
        if(error.cause == akka.event.Logging.Error.NoCause) None
        else Some(error.cause.getStackTrace.map(element => s"\t${element.toString}").toSeq)
      }
      new Error(error.timestamp, error.message.toString, cause, customParams, s"/$requestUri")
    }
  }
}