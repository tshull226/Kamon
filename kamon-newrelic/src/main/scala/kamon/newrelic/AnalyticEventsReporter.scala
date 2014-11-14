package kamon.newrelic

import java.util.concurrent.TimeUnit

import akka.actor.{ Props, ActorLogging, Actor }
import akka.pattern.pipe
import akka.io.IO
import akka.util.Timeout
import kamon.Kamon
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.TraceMetrics.TraceMetricsSnapshot
import kamon.metric.{Scale, TraceMetrics, TickMetricSnapshotBuffer, Metrics}
import kamon.newrelic.AnalyticEventsReporter._
import kamon.newrelic.MetricReporter.UnexpectedStatusCodeException
import spray.can.Http
import spray.http.Uri
import spray.httpx.SprayJsonSupport
import scala.concurrent.Future
import scala.concurrent.duration._

class AnalyticEventsReporter(settings: Agent.Settings, runID: Long, baseUri: Uri) extends Actor
with ClientPipelines with ActorLogging with SprayJsonSupport {
  import JsonProtocol._
  import context.dispatcher

  val metricDataQuery = ("method" -> "analytic_event_data") +: ("run_id" -> runID.toString) +: baseUri.query
  val metricDataUri = baseUri.withQuery(metricDataQuery)

  implicit val operationTimeout = Timeout(30 seconds)
  val metricsExtension = Kamon(Metrics)(context.system)
  val collectionContext = metricsExtension.buildDefaultCollectionContext
  val collectorClient = compressedPipeline(IO(Http)(context.system))

  val subscriber = {
    val tickInterval = context.system.settings.config.getDuration("kamon.metrics.tick-interval", TimeUnit.MILLISECONDS)
    if (tickInterval == 60000)
      self
    else
      context.actorOf(TickMetricSnapshotBuffer.props(1 minute, self), "metric-buffer")
  }

  // Subscribe to Trace Metrics
  metricsExtension.subscribe(TraceMetrics, "*", subscriber, permanently = true)

  def receive = reporting(None)

  def reporting(pendingMetrics: Option[TimeSliceMetrics]): Receive = {
    case tick @ TickMetricSnapshot(from, to, metrics) ⇒
      val tickEvents = generateEvents(tick)
      if(tickEvents.nonEmpty) {
        val batch = AnalyticEventBatch(runID, tickEvents)
        pipe(sendAnalyticEvents(batch)) to self

        log.debug("Sending [{}] analytic events to New Relic for the time slice between {} and {}.", tickEvents.size, from, to)
      } else
        log.debug("No analytic events received for the time slice between {} and {}.", from, to)

    case PostSucceeded ⇒
      context become (reporting(None))

    case PostFailed(reason) ⇒
      log.error(reason, "Analytic events POST to the New Relic collector failed, events will be accumulated with the next tick.")
  }

  def sendAnalyticEvents(batch: AnalyticEventBatch): Future[AnalyticEventPostResult] = {

    import spray.json._
    log.info(batch.toJson.toString(NewRelicJsonPrinter))

    collectorClient {
      Post(metricDataUri, batch)(sprayJsonMarshaller(AnalyticEventBatchJsonFormat, NewRelicJsonPrinter))

    } map { response ⇒
      if (response.status.isSuccess)
        PostSucceeded
      else
        PostFailed(new UnexpectedStatusCodeException(s"Received unsuccessful status code [${response.status.value}] from collector."))
    } recover { case t: Throwable ⇒ PostFailed(t) }
  }


  def generateEvents(tick: TickMetricSnapshot): Vector[AnalyticEvent] = {
    val eventTimestamp = tick.from

    tick.metrics.collect {
      case (TraceMetrics(traceName), snapshot: TraceMetricsSnapshot) =>
        for(
          record <- snapshot.elapsedTime.recordsIterator;
          repetition <- 1L to record.count
        ) yield AnalyticEvent("Custom/" + traceName, eventTimestamp, Scale.convert(snapshot.elapsedTime.scale, Scale.Unit, record.level))
        
    }.flatten.toVector
  }
}

object AnalyticEventsReporter {
  case class AnalyticEventBatch(runID: Long, events: Vector[AnalyticEvent])
  case class AnalyticEvent(name: String, timestamp: Long, duration: Double)

  sealed trait AnalyticEventPostResult
  case object PostSucceeded extends AnalyticEventPostResult
  case class PostFailed(reason: Throwable) extends AnalyticEventPostResult

  def props(settings: Agent.Settings, runID: Long, baseUri: Uri): Props =
    Props(new AnalyticEventsReporter(settings, runID, baseUri))

}


