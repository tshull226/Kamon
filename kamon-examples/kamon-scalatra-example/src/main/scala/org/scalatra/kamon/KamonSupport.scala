package org.scalatra.kamon

import kamon.Kamon
import kamon.trace.Tracer
import kamon.util.Latency

trait KamonSupport {
  def counter(name: String) = Kamon.metrics.counter(name)
  def minMaxCounter(name: String) = Kamon.metrics.minMaxCounter(name)
  def histogram(name: String) = Kamon.metrics.histogram(name)
  def time[A](name: String)(thunk: => A) = Latency.measure(Kamon.metrics.histogram(name))(thunk)
  def withTrace[A](name:String)(thunk: => A) = Tracer.withContext(Kamon.tracer.newContext(name)) {
    try thunk finally Tracer.currentContext.finish()
  }
}
