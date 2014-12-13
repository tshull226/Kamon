package kamon.dashboard

import kamon.metric.{MetricIdentity, MetricGroupSnapshot, MetricGroupIdentity, MetricSnapshot}
import kamon.metric.Subscriptions.TickMetricSnapshot
import kamon.metric.instrument.Histogram.Record
import kamon.metric.instrument.{Histogram, Counter}
import spray.json.{JsValue, JsonPrinter}
import java.lang. { StringBuilder => JavaStringBuilder }


trait ManualJsonMarshaller[T] {
  def marshall(value: T, builder: JavaStringBuilder): Unit
}

object ManualJsonMarshaller {
  implicit val stringMarshaller = new ManualJsonMarshaller[String] {
    override def marshall(value: String, builder: JavaStringBuilder): Unit =
      FakeHttpPrinter.renderString(value, builder)
  }

  implicit def iteratorMarshaller[T](implicit tMarshaller: ManualJsonMarshaller[T]) = new ManualJsonMarshaller[Iterator[T]] {
    override def marshall(iterator: Iterator[T], builder: JavaStringBuilder): Unit = {
      builder.append("[")
      iterator.foreach { value =>
        tMarshaller.marshall(value, builder)
        if(iterator.hasNext)
          builder.append(",")
      }
      builder.append("]")
    }
  }
}

object Marshalling {

  def marshall[T](value: T, builder: JavaStringBuilder)(implicit mjm: ManualJsonMarshaller[T]): Unit = {
    mjm.marshall(value, builder)
  }
}

object KamonMarshallers {
  implicit val HistogramRecordMarshaller = new ManualJsonMarshaller[Histogram.Record] {
    override def marshall(value: Record, builder: JavaStringBuilder): Unit =
      builder.append("""{"level":""")
        .append(value.level)
        .append(""","count":""")
        .append(value.count)
        .append("}")
  }

  implicit val MetricSnapshotMarshaller = new ManualJsonMarshaller[MetricSnapshot] {
    override def marshall(value: MetricSnapshot, builder: JavaStringBuilder): Unit = value match {
      case cs: Counter.Snapshot =>
        builder.append("""{"type":"counter","value":""").append(cs.count).append("}")

      case hs: Histogram.Snapshot =>
        builder.append("""{"type":"histogram","recordings":""")
        Marshalling.marshall(hs.recordsIterator, builder)
        builder.append("}")
    }
  }

  implicit val EntityMetricsMarshaller = new ManualJsonMarshaller[(MetricIdentity, MetricSnapshot)] {
    override def marshall(metric: (MetricIdentity, MetricSnapshot), builder: JavaStringBuilder): Unit = {
      val (identity, snapshot) = metric

      Marshalling.marshall(identity.name, builder)
      builder.append(":")
      Marshalling.marshall(snapshot, builder)
    }
  }

  implicit val EntityMarshaller = new ManualJsonMarshaller[(MetricGroupIdentity, MetricGroupSnapshot)] {
    override def marshall(group: (MetricGroupIdentity, MetricGroupSnapshot), builder: JavaStringBuilder): Unit = {
      val (identity, snapshot) = group

      builder.append("""{"name":""")
      Marshalling.marshall(identity.name, builder)
      builder.append(""","category":""")
      Marshalling.marshall(identity.category.name, builder)
      builder.append(""","metrics":{""")

      val metricsIterator = snapshot.metrics.iterator
      metricsIterator.foreach { metric =>
        Marshalling.marshall(metric, builder)
        if(metricsIterator.hasNext)
          builder.append(",")
      }
      builder.append("}}")

    }
  }


  implicit val TickMetricSnapshotMarshaller = new ManualJsonMarshaller[TickMetricSnapshot] {
    override def marshall(tick: TickMetricSnapshot, builder: JavaStringBuilder): Unit = {
      builder.append("""{"type":"metrics","payload":{"from":""")
        .append(tick.from.millis)
        .append(""","to":""")
        .append(tick.to.millis)
        .append(""","entities":""")

      Marshalling.marshall(tick.metrics.iterator, builder)
      builder.append("}}")
    }
  }
}

object FakeHttpPrinter extends JsonPrinter {
  override def print(x: JsValue, sb: JavaStringBuilder): Unit =
    sys.error("This fake printer is not supposed to be used for real world printing.")

  def renderString(value: String, builder: JavaStringBuilder): Unit = super.printString(value, builder)
}
