package kamon.influxdb

import akka.actor
import akka.actor.{Actor, ExtendedActorSystem, ExtensionIdProvider, ExtensionId}
import kamon.Kamon
import kamon.influxdb.InfluxDB.{DataPoint, TimeSeries, TimeSeriesBatch}
import kamon.metric.instrument.{Histogram, Counter}
import kamon.metric.{MetricSnapshot, MetricGroupSnapshot, MetricGroupIdentity}
import kamon.metric.Subscriptions.TickMetricSnapshot
import spray.httpx.{SprayJsonSupport, ResponseTransformation, RequestBuilding}

object InfluxDB extends ExtensionId[InfluxDBExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = InfluxDB
  def createExtension(system: ExtendedActorSystem): InfluxDBExtension = new InfluxDBExtension(system)

  case class TimeSeriesBatch(timeSeries: List[TimeSeries])
  case class TimeSeries(name: String, points: List[DataPoint])
  case class DataPoint(timestamp: Long, value: Long)
}

class InfluxDBExtension(system: ExtendedActorSystem) extends Kamon.Extension {

}

class InfluxDBReporter extends Actor with RequestBuilding with ResponseTransformation with SprayJsonSupport {
  import spray.client.pipelining._
  import context.system

  val influxApiPipeline = sendReceive

  def receive = {
    case tick: TickMetricSnapshot =>
  }

  def sendToInfluxDB(tick: TickMetricSnapshot): Unit = {
    import InfluxDBJsonProtocol._

    tick.metrics.foreach {
      case (group, snapshot) =>
        snapshot.metrics.foreach {
          case (metric, metricSnapshot) =>
            influxApiPipeline {
              Post("http://localhost:8080/db/kamon/series?u=kamon&p=kamon")
            }
        }
    }
  }

  def toTimeSeriesBatch(group: MetricGroupIdentity, snapshot: MetricGroupSnapshot): TimeSeriesBatch = {
    //snapshot.metrics
  }

  def metricSnapshotToDataPoints(timestamp: Long, snapshot: MetricSnapshot): List[DataPoint] = snapshot match {
    case cs: Counter.Snapshot => List(DataPoint(timestamp, cs.count))
    case hs: Histogram.Snapshot => hs.recordsIterator.flatMap { record =>
      for(repetition <- 1 to record.count) yield DataPoint(timestamp, record.level)
    } toList
  }
}
