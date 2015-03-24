/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.example

import kamon.Kamon
import kamon.trace.Tracer
import kamon.util.Latency
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future}

trait KamonSupport {
  def counter(name: String) = Kamon.metrics.counter(name)
  def minMaxCounter(name: String) = Kamon.metrics.minMaxCounter(name)
  def histogram(name: String) = Kamon.metrics.histogram(name)
  def gauge[A](name: String)(thunk: => Long) = Kamon.metrics.gauge(name)(thunk)
  def time[A](name: String)(thunk: => A) = Latency.measure(Kamon.metrics.histogram(name))(thunk)
  def traceFuture[A](name:String)(future: => Future[A]):Future[A] = Tracer.withContext(Kamon.tracer.newContext(name)) {
    future.map(f => Tracer.currentContext.finish())(SameThreadExecutionContext)
    future
  }
}

/**
 * For small code blocks that don't need to be run on a separate thread.
 */
object SameThreadExecutionContext extends ExecutionContext {
  val logger = LoggerFactory.getLogger("SameThreadExecutionContext")

  override def execute(runnable: Runnable): Unit = runnable.run()
  override def reportFailure(t: Throwable): Unit = logger.error(t.getMessage, t)
}
