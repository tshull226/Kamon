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
package kamon.newrelic

import java.util.concurrent.TimeUnit

import kamon.newrelic.NewRelicErrorReporter.ErrorData
import spray.json._
import kamon.newrelic.NewRelicMetricReporter._

import scala.util.parsing.json.JSONObject

object AgentJsonProtocol extends DefaultJsonProtocol {

  implicit object ConnectJsonWriter extends RootJsonWriter[AgentInfo] {
    def write(obj: AgentInfo): JsValue =
      JsArray(
        JsObject(
          "agent_version" -> JsString("3.1.0"),
          "app_name" -> JsArray(JsString(obj.appName)),
          "host" -> JsString(obj.host),
          "identifier" -> JsString(s"java:${obj.appName}"),
          "language" -> JsString("java"),
          "pid" -> JsNumber(obj.pid)))
  }

  implicit def seqWriter[T: JsonWriter] = new JsonWriter[Seq[T]] {
    def write(seq: Seq[T]) = JsArray(seq.map(_.toJson).toVector)
  }

  implicit object MetricDetailWriter extends JsonWriter[NewRelic.Metric] {
    def write(obj: NewRelic.Metric): JsValue = {
      JsArray(
        JsObject(
          "name" -> JsString(obj.name) // TODO Include scope
          ),
        JsArray(
          JsNumber(obj.callCount),
          JsNumber(obj.total),
          JsNumber(obj.totalExclusive),
          JsNumber(obj.min),
          JsNumber(obj.max),
          JsNumber(obj.sumOfSquares)))
    }
  }

  implicit object MetricDataWriter extends RootJsonWriter[MetricData] {
    def write(obj: MetricData): JsValue =
      JsArray(
        JsNumber(obj.runId),
        JsNumber(obj.timeSliceMetrics.from),
        JsNumber(obj.timeSliceMetrics.to),
        obj.timeSliceMetrics.metrics.values.toSeq.toJson)
  }

  implicit object ErrorDetailWriter extends JsonWriter[NewRelic.Error] {
    def write(obj: NewRelic.Error): JsValue = {
      JsArray(
        JsNumber(obj.timestamp / 1000L),
        JsString(s"OtherTransaction/GET:/error"),
        JsString("SEARCH_REQUEST_VALIDATION: cannot fly in the past - invalid departure date: 2013-09-02"),
        JsString("SEARCH_REQUEST_VALIDATION: cannot fly in the past - invalid departure date: 2013-09-02"),

//        JsString(obj.errorMessage),
//        JsString(obj.errorMessage),
//        JsString(obj.errorMessage),
        JsObject(
          "stack_trace" -> JsArray(JsString("  Stacktrace line 1"),JsString("  Stacktrace line 2"),JsString("  Stacktrace line 3"),JsString("  Stacktrace line 4")),// obj.stackTrace.toJson,
          "custom_params" -> JsObject("a" -> JsString("b")),//.customParams.toJson,
          "request_uri" -> JsString("/GET:/error"))//"/prism-akka.actor.default-dispatcher-13"))
      )
    }
  }

  implicit object ErrorDataWriter extends RootJsonWriter[ErrorData] {
    def write(obj: ErrorData): JsValue =
      JsArray(
        JsNumber(obj.runId.toInt),
        obj.errors.toJson)

  }
}

