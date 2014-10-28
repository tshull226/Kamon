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

import akka.actor.ActorSystem
import kamon.newrelic.NewRelicMetricReporter.AgentInfo
import spray.client.pipelining._
import spray.http.{ HttpRequest, HttpResponse }
import spray.http.Uri.Query
import spray.httpx.{ SprayJsonSupport, ResponseTransformation, RequestBuilding }
import spray.httpx.encoding.Deflate
import spray.json.JsValue
import spray.json._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global //TODO remove this

trait NewRelicAgentSupport extends RequestBuilding with ResponseTransformation with SprayJsonSupport {

  implicit def system: ActorSystem

  lazy val settings = NewRelic(system).Settings

  lazy val agentInfo = {
    //Name has the format of pid@host
    val runtimeBean = ManagementFactory.getRuntimeMXBean.getName.split('@')
    val Pid = runtimeBean(0).toInt
    val Host = runtimeBean(1)

    AgentInfo(settings.LicenseKey, settings.AppName, Host, Pid)
  }

  lazy val baseQuery = Query(
    "license_key" -> agentInfo.licenseKey,
    "marshal_format" -> "json",
    "protocol_version" -> "12")

  def toJson(response: HttpResponse): JsValue = response.entity.asString.parseJson
  def compressedPipeline: HttpRequest ⇒ Future[HttpResponse] = encode(Deflate) ~> sendReceive
  def compressedToJsonPipeline: HttpRequest ⇒ Future[JsValue] = compressedPipeline ~> toJson
}