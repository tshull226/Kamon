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

import akka.actor.{ Actor, ActorLogging }
import akka.event.Logging.{ Error, InitializeLogger, LoggerInitialized }
import com.newrelic.api.agent.{ NewRelic ⇒ NR }
import kamon.newrelic.Agent.MetricData
import kamon.newrelic.MetricTranslator.TimeSliceMetrics
import kamon.newrelic.NewRelicCollector.Collector
import kamon.trace.TraceContextAware
import spray.http.Uri
import spray.httpx.{ RequestBuilding, ResponseTransformation, SprayJsonSupport }

import scala.concurrent.duration._

class NewRelicErrorLogger extends Actor with NewRelicAgentSupport with ActorLogging {

  import NewRelicErrorLogger._
  import AgentJsonProtocol._
  import context.dispatcher

  val system = context.system

  system.eventStream.subscribe(self, classOf[Collector])

  def receive: Receive = uninitialized

  def uninitialized: Receive = {
    case InitializeLogger(_) ⇒
      log.info("New Relic Error Logger registered, expecting collector...")
      sender ! LoggerInitialized
      context become expectingCollector
  }

  def expectingCollector: Receive = {
    case Collector(runId, collector, id) ⇒
      log.info("New Relic Error Logger initialized with runID: [{}] and collector: [{}]", runId, collector)
      context.system.scheduler.schedule(0 second, 1 minute, self, FlushErrors)
      context become ready(runId, collector)
  }

  def ready(runId: Long, collector: String): Receive = {
    case error @ Error(cause, logSource, logClass, message) ⇒ notifyError(runId, collector, error)
    case FlushErrors                                        ⇒
    case anythingElse                                       ⇒
  }

  def notifyError(runId: Long, collector: String, error: Error): Unit = {
    val params = new java.util.HashMap[String, String]()

    val ctx = error.asInstanceOf[TraceContextAware].traceContext

    for (c ← ctx) {
      params.put("TraceToken", c.token)
    }

    if (error.cause == Error.NoCause) {
      NR.noticeError(error.message.toString, params)
    } else {
      NR.noticeError(error.cause, params)
    }

  }

  def sendErrors(runId: Long, collector: String, errors: TimeSliceMetrics) = {
    val query = ("method" -> "error_data") +: ("run_id" -> runId.toString) +: baseQuery
    val sendErrorsUri = Uri(s"http://$collector/agent_listener/invoke_raw_method").withQuery(query)

    compressedPipeline {
      log.info("Sending Erors to NewRelic collector")
      Post(sendErrorsUri, MetricData(runId, errors))
    }
  }
}

object NewRelicErrorLogger {
  case object FlushErrors
}
