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

import akka.actor.Actor
import akka.event.Logging.{ Error, InitializeLogger, LoggerInitialized }
import kamon.newrelic.NewRelicCollector.Collector
import kamon.trace.TraceContextAware
import spray.http.Uri

import scala.concurrent.duration._

class NewRelicErrorReporter extends Actor with NewRelicAgentSupport {

  import settings.Dispatcher

  import AgentJsonProtocol._
  import NewRelicErrorReporter._

  context.system.eventStream.subscribe(self, classOf[Collector])

  val errors = Seq.newBuilder[NewRelic.Error]

  def receive: Receive = uninitialized

  def uninitialized: Receive = {
    case InitializeLogger(_) ⇒
      sender ! LoggerInitialized
      context become expectingCollector
  }

  def expectingCollector: Receive = {
    case Collector(runId, collector) ⇒
      log.info("New Relic Error Logger initialized with runID: [{}] and collector: [{}]", runId, collector)
      context.system.scheduler.schedule(1 minute, 1 minute, self, FlushErrors)
      context become ready(runId, collector)
  }

  def ready(runId: Long, collector: String): Receive = {
    case error @ Error(cause, logSource, logClass, message) ⇒ processError(error)
    case FlushErrors                                        ⇒ sendErrors(runId, collector)
    case anythingElse                                       ⇒
  }

  def processError(error: Error): Unit = {
    val params = Map.newBuilder[String, String]

    val ctx = error.asInstanceOf[TraceContextAware].traceContext

    for (c ← ctx) {
      params += ("TraceToken" -> c.token)
    }

    if (error.cause == Error.NoCause) {
      errors += NewRelic.Error(Seq(error.message.toString), None, Some(params.result()), ctx.map(_.name).getOrElse("unknown"))
    } else {
      errors += NewRelic.Error(Seq(error.message.toString), Some(error.cause), Some(params.result()), ctx.map(_.name).getOrElse("unknown"))
    }
  }

  def sendErrors(runId: Long, collector: String) = {
    val query = ("method" -> "error_data") +: ("run_id" -> runId.toString) +: baseQuery
    val sendErrorsUri = Uri(s"http://$collector/agent_listener/invoke_raw_method").withQuery(query)

    compressedPipeline {
      log.info("Sending Errors to NewRelic collector")
      Post(sendErrorsUri, ErrorData(runId, errors.result()))
    }
  }
}

object NewRelicErrorReporter {
  case object FlushErrors
  case class ErrorData(runId: Long, errors: Seq[NewRelic.Error])

  implicit def throwable2Seq(t: Throwable): Seq[String] = {
    Seq.empty[String]
  }
}
