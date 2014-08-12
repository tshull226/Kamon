package kamon.influxdb

import kamon.influxdb.InfluxDB.{ TimeSeriesBatch, TimeSeries, DataPoint }
import spray.json._

object InfluxDBJsonProtocol extends DefaultJsonProtocol {

  implicit object DataPointJsonWriter extends JsonWriter[DataPoint] {
    def write(dataPoint: DataPoint): JsValue =
      JsArray(
        JsNumber(dataPoint.timestamp),
        JsNumber(dataPoint.value))
  }

  implicit def listWriter[T: JsonWriter] = new JsonWriter[List[T]] {
    def write(list: List[T]) = JsArray(list.map(_.toJson).toList)
  }

  implicit object TimeSeriesJsonWriter extends JsonWriter[TimeSeries] {
    def write(timeSeries: TimeSeries): JsValue =
      JsObject(
        "name" -> JsString(timeSeries.name),
        "columns" -> JsArray(
          JsString("time"),
          JsString("value")),
        "points" -> timeSeries.points.toJson)
  }

  implicit object TimeSeriesBatchJsonWriter extends JsonWriter[TimeSeriesBatch] {
    def write(batch: TimeSeriesBatch): JsValue =
      JsArray(batch.timeSeries.toJson)
  }

}
