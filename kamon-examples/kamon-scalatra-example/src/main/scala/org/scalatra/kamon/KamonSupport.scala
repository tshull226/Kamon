package org.scalatra.kamon

import kamon.Kamon
import kamon.trace.Tracer
import kamon.util.Latency

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait KamonSupport {
  def counter(name: String) = Kamon.metrics.counter(name)
  def minMaxCounter(name: String) = Kamon.metrics.minMaxCounter(name)
  def histogram(name: String) = Kamon.metrics.histogram(name)
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
  override def execute(runnable: Runnable): Unit = runnable.run()
  override def reportFailure(t: Throwable): Unit = println(t.getMessage)
}