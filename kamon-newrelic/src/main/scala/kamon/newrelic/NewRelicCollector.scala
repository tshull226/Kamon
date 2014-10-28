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

import akka.actor.{ Props, Actor, ActorLogging }
import akka.event.{ LookupClassification, ActorEventBus }
import spray.http.Uri
import spray.json.JsArray
import spray.json.lenses.JsonLenses._

import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

class NewRelicCollector extends Actor with ActorLogging with NewRelicAgentSupport {

  import context.dispatcher
  import AgentJsonProtocol._
  import NewRelicCollector._

  val system = context.system

  val bus = new MessageBus

  self ! Initialize

  def receive: Receive = {
    case Initialize ⇒ {
      connectToCollector onComplete {
        case Success(agent) ⇒ {
          log.info("Agent initialized with runID: [{}] and collector: [{}]", agent.runId, agent.collector)
          bus.publish(Collector(agent.runId, agent.collector))

        }
        case Failure(NonFatal(reason)) ⇒ self ! InitializationFailed(reason)
      }
    }
    case InitializationFailed(reason) ⇒ {
      log.info("Initialization failed: {}, retrying in {} seconds", reason.getMessage, settings.RetryDelay.toSeconds)
      context.system.scheduler.scheduleOnce(settings.RetryDelay, self, Initialize)
    }
    case everythingElse ⇒ //ignore
  }

  def connectToCollector: Future[Initialized] = for {
    collector ← selectCollector
    runId ← connect(collector, agentInfo)
  } yield Initialized(runId, collector)

  def selectCollector: Future[String] = {
    val query = ("method" -> "get_redirect_host") +: baseQuery
    val getRedirectHostUri = Uri("http://collector.newrelic.com/agent_listener/invoke_raw_method").withQuery(query)

    compressedToJsonPipeline {
      Post(getRedirectHostUri, JsArray())

    } map { json ⇒
      json.extract[String]('return_value)
    }
  }

  def connect(collectorHost: String, connect: Agent.AgentInfo): Future[Long] = {
    log.debug("Connecting to NewRelic Collector [{}]", collectorHost)

    val query = ("method" -> "connect") +: baseQuery
    val connectUri = Uri(s"http://$collectorHost/agent_listener/invoke_raw_method").withQuery(query)

    compressedToJsonPipeline {
      Post(connectUri, connect)

    } map { json ⇒
      json.extract[Long]('return_value / 'agent_run_id)
    }
  }
}

object NewRelicCollector {
  case class Initialize()
  case class Initialized(runId: Long, collector: String)
  case class Collector(runId: Long, collector: String, id: String = "collector")
  case class InitializationFailed(reason: Throwable)
  case class AgentInfo(licenseKey: String, appName: String, host: String, pid: Int)

  def props: Props = Props(classOf[NewRelicCollector])

  class MessageBus extends ActorEventBus with LookupClassification {

    type Event = Collector
    type Classifier = String

    protected def mapSize(): Int = 10

    protected def classify(event: Event): Classifier = {
      event.id
    }

    protected def publish(event: Event, subscriber: Subscriber): Unit = {
      subscriber ! event
    }
  }
}

