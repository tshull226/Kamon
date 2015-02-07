/*
 * =========================================================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
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

package kamon.metric.instrument

import org.HdrHistogram
import org.HdrHistogram.Recorder
import kamon.metric.instrument.Histogram.{ Snapshot, DynamicRange }

import scala.annotation.tailrec

trait Histogram extends Instrument {
  type SnapshotType = Histogram.Snapshot

  def record(value: Long)
  def record(value: Long, count: Long)
}

object Histogram {

  /**
   *  Scala API:
   *
   *  Create a new High Dynamic Range Histogram ([[kamon.metric.instrument.HdrHistogram]]) using the given
   *  [[kamon.metric.instrument.Histogram.DynamicRange]].
   */
  def apply(dynamicRange: DynamicRange): Histogram = new HdrHistogram(dynamicRange)

  /**
   *  Java API:
   *
   *  Create a new High Dynamic Range Histogram ([[kamon.metric.instrument.HdrHistogram]]) using the given
   *  [[kamon.metric.instrument.Histogram.DynamicRange]].
   */
  def create(dynamicRange: DynamicRange): Histogram = apply(dynamicRange)

  /**
   *  DynamicRange is a configuration object used to supply range and precision configuration to a
   *  [[kamon.metric.instrument.HdrHistogram]]. See the [[http://hdrhistogram.github.io/HdrHistogram/ HdrHistogram website]]
   *  for more details on how it works and the effects of these configuration values.
   *
   * @param lowestDiscernibleValue
   *    The lowest value that can be discerned (distinguished from 0) by the histogram.Must be a positive integer that
   *    is >= 1. May be internally rounded down to nearest power of 2.
   *
   * @param highestTrackableValue
   *    The highest value to be tracked by the histogram. Must be a positive integer that is >= (2 * lowestDiscernibleValue).
   *    Must not be larger than (Long.MAX_VALUE/2).
   *
   * @param precision
   *    The number of significant decimal digits to which the histogram will maintain value resolution and separation.
   *    Must be a non-negative integer between 1 and 3.
   */
  case class DynamicRange(lowestDiscernibleValue: Long, highestTrackableValue: Long, precision: Int)

  trait Record {
    def level: Long
    def count: Long
  }

  case class MutableRecord(var level: Long, var count: Long) extends Record

  trait Snapshot extends InstrumentSnapshot {
    def dynamicRange: DynamicRange
    def isEmpty: Boolean = numberOfMeasurements == 0
    def numberOfMeasurements: Long
    def min: Long
    def max: Long
    def sum: Long
    def percentile(percentile: Double): Long
    def recordsIterator: Iterator[Record]
    def merge(that: InstrumentSnapshot, context: CollectionContext): InstrumentSnapshot
    def merge(that: Histogram.Snapshot, context: CollectionContext): Histogram.Snapshot
  }

  object Snapshot {
    val empty = new Snapshot {
      override def dynamicRange: DynamicRange = DynamicRange(0, 0, 0)
      override def min: Long = 0L
      override def max: Long = 0L
      override def sum: Long = 0L
      override def percentile(percentile: Double): Long = 0L
      override def recordsIterator: Iterator[Record] = Iterator.empty
      override def merge(that: InstrumentSnapshot, context: CollectionContext): InstrumentSnapshot = that
      override def merge(that: Histogram.Snapshot, context: CollectionContext): Histogram.Snapshot = that
      override def numberOfMeasurements: Long = 0L
    }
  }
}

class HdrHistogram(dynamicRange: DynamicRange) extends Histogram {
  var intervalHistogram: HdrHistogram.Histogram = _
  val recorder = new Recorder(dynamicRange.lowestDiscernibleValue, dynamicRange.highestTrackableValue, dynamicRange.precision)

  def record(value: Long): Unit =
    recorder.recordValue(value)

  @tailrec final def record(value: Long, count: Long): Unit = {
    if (count > 0) {
      recorder.recordValue(value)
      record(value, count - 1)
    }
  }

  def cleanup: Unit = {}

  def collect(context: CollectionContext): Histogram.Snapshot = synchronized {
    import scala.collection.JavaConversions._
    import context.buffer

    buffer.clear()
    intervalHistogram = recorder.getIntervalHistogram(intervalHistogram)
    intervalHistogram.recordedValues().iterator().toIterator.foreach { histogramValue ⇒
      buffer.put(histogramValue.getValueIteratedTo)
      buffer.put(histogramValue.getCountAtValueIteratedTo)
    }

    buffer.flip()
    val counts = Array.ofDim[Long](buffer.limit())
    buffer.get(counts, 0, counts.length)

    new CompactHdrSnapshot(dynamicRange, intervalHistogram.getTotalCount, counts)
  }
}

class CompactHdrSnapshot(val dynamicRange: DynamicRange, val numberOfMeasurements: Long, counts: Array[Long]) extends Histogram.Snapshot {
  // The counts array has all recordings in pairs, one long for the value and one long for the count at that value.

  def min: Long = if (counts.length == 0) 0 else counts(0)
  def max: Long = if (counts.length == 0) 0 else counts(counts.length - 2)
  def sum: Long = recordsIterator.foldLeft(0L)((a, r) ⇒ a + (r.count * r.level))

  def percentile(p: Double): Long = {
    val records = recordsIterator
    val threshold = numberOfMeasurements * (p / 100D)
    var countToCurrentLevel = 0L
    var percentileLevel = 0L

    while (countToCurrentLevel < threshold && records.hasNext) {
      val record = records.next()
      countToCurrentLevel += record.count
      percentileLevel = record.level
    }

    percentileLevel
  }

  def merge(that: Histogram.Snapshot, context: CollectionContext): Snapshot =
    merge(that.asInstanceOf[InstrumentSnapshot], context)

  def merge(that: InstrumentSnapshot, context: CollectionContext): Histogram.Snapshot = that match {
    case thatSnapshot: CompactHdrSnapshot ⇒
      if (thatSnapshot.isEmpty) this else if (this.isEmpty) thatSnapshot else {
        import context.buffer
        buffer.clear()

        val selfIterator = recordsIterator
        val thatIterator = thatSnapshot.recordsIterator
        var thatCurrentRecord: Histogram.Record = null
        var mergedNumberOfMeasurements = 0L

        def nextOrNull(iterator: Iterator[Histogram.Record]): Histogram.Record = if (iterator.hasNext) iterator.next() else null
        def addToBuffer(value: Long, count: Long): Unit = {
          mergedNumberOfMeasurements += count
          buffer.put(value)
          buffer.put(count)
        }

        while (selfIterator.hasNext) {
          val selfCurrentRecord = selfIterator.next()

          // Advance that to no further than the level of selfCurrentRecord
          thatCurrentRecord = if (thatCurrentRecord == null) nextOrNull(thatIterator) else thatCurrentRecord
          while (thatCurrentRecord != null && thatCurrentRecord.level < selfCurrentRecord.level) {
            addToBuffer(thatCurrentRecord.level, thatCurrentRecord.count)
            thatCurrentRecord = nextOrNull(thatIterator)
          }

          // Include the current record of self and optionally merge if has the same level as thatCurrentRecord
          if (thatCurrentRecord != null && thatCurrentRecord.level == selfCurrentRecord.level) {
            addToBuffer(thatCurrentRecord.level, thatCurrentRecord.count + selfCurrentRecord.count)
            thatCurrentRecord = nextOrNull(thatIterator)
          } else {
            addToBuffer(selfCurrentRecord.level, selfCurrentRecord.count)
          }
        }

        // Include everything that might have been left from that
        if (thatCurrentRecord != null) addToBuffer(thatCurrentRecord.level, thatCurrentRecord.count)
        while (thatIterator.hasNext) {
          val record = thatIterator.next()
          addToBuffer(record.level, record.count)
        }

        buffer.flip()
        val compactRecords = Array.ofDim[Long](buffer.limit())
        buffer.get(compactRecords)

        new CompactHdrSnapshot(dynamicRange, mergedNumberOfMeasurements, compactRecords)
      }

    case other ⇒
      sys.error(s"Cannot merge a CompactHdrSnapshot with the incompatible [${other.getClass.getName}] type.")

  }

  def recordsIterator: Iterator[Histogram.Record] = new Iterator[Histogram.Record] {
    var currentIndex = 0
    val mutableRecord = Histogram.MutableRecord(0, 0)

    def hasNext: Boolean = currentIndex < counts.length

    def next(): Histogram.Record = {
      if (hasNext) {
        mutableRecord.level = counts(currentIndex)
        mutableRecord.count = counts(currentIndex + 1)
        currentIndex += 2

        mutableRecord
      } else {
        throw new IllegalStateException("The iterator has already been consumed.")
      }
    }
  }
}