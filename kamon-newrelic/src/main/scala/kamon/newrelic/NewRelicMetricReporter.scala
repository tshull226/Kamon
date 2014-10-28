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

import akka.actor.{ Actor, ActorLogging, Props }
import akka.event.LoggingAdapter
import kamon.newrelic.MetricTranslator.TimeSliceMetrics
import kamon.newrelic.NewRelicCollector.Collector
import spray.http._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

class NewRelicMetricReporter extends Actor with NewRelicAgentSupport with ActorLogging {

  import NewRelicMetricReporter._
  import Retry._
  import context.dispatcher

  val system = context.system

  system.eventStream.subscribe(self, classOf[Collector])

  def receive: Receive = uninitialized

  def uninitialized: Receive = {
    case Collector(runId, collector) ⇒ {
      log.info("Agent initialized with runID: [{}] and collector: [{}]", runId, collector)
      context become reporting(runId, collector)
    }
    case everythingElse ⇒ //ignore
  }

  import kamon.newrelic.AgentJsonProtocol._

  def reporting(runId: Long, collector: String): Receive = {
    case metrics: TimeSliceMetrics ⇒ sendMetricData(runId, collector, metrics)
  }

  def sendMetricData(runId: Long, collector: String, metrics: TimeSliceMetrics) = {
    val query = ("method" -> "metric_data") +: ("run_id" -> runId.toString) +: baseQuery
    val sendMetricDataUri = Uri(s"http://$collector/agent_listener/invoke_raw_method").withQuery(query)

    withMaxAttempts(settings.MaxRetry, metrics, log) { currentMetrics ⇒
      compressedPipeline {
        log.info("Sending metrics to NewRelic collector")
        Post(sendMetricDataUri, MetricData(runId, currentMetrics))
      }
    }
  }
}

object NewRelicMetricReporter {
  case class AgentInfo(licenseKey: String, appName: String, host: String, pid: Int)
  case class MetricData(runId: Long, timeSliceMetrics: TimeSliceMetrics)

  def props: Props = Props(classOf[NewRelicMetricReporter])
}

object Retry {

  @volatile private var attempts: Int = 0
  @volatile private var pendingMetrics: Option[TimeSliceMetrics] = None

  def withMaxAttempts[T](maxRetry: Int, metrics: TimeSliceMetrics, log: LoggingAdapter)(block: TimeSliceMetrics ⇒ Future[T])(implicit executor: ExecutionContext): Unit = {

    val currentMetrics = metrics.merge(pendingMetrics)

    if (currentMetrics.metrics.nonEmpty) {
      block(currentMetrics) onComplete {
        case Success(_) ⇒
          pendingMetrics = None
          attempts = 0
        case Failure(NonFatal(_)) ⇒
          attempts += 1
          if (maxRetry > attempts) {
            log.info("Trying to send metrics to NewRelic collector, attempt [{}] of [{}]", attempts, maxRetry)
            pendingMetrics = Some(currentMetrics)
          } else {
            log.info("Max attempts achieved, proceeding to remove all pending metrics")
            pendingMetrics = None
            attempts = 0
          }
      }
    }
  }
}
