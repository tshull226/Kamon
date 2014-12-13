package kamon.dashboard

import akka.actor.{Terminated, Props, ActorRef, Actor}
import kamon.MilliTimestamp
import kamon.metric.Subscriptions.TickMetricSnapshot
import MarshalledSnapshotCache._
import java.lang.{StringBuilder => JStringBuilder}


class MarshalledSnapshotCache(maxCacheSize: Int) extends Actor {
  var cache: Vector[MarshalledSnapshot] = Vector.empty
  var subscribers: Vector[ActorRef] = Vector.empty

  def receive = {
    case tick: TickMetricSnapshot     => dispatchTick(tick)
    case Subscribe(subscriber, since) => subscribe(subscriber, since)
    case Terminated(subscriber)       => unsubscribe(subscriber)
  }

  def subscribe(newSubscriber: ActorRef, since: MilliTimestamp): Unit = {
    subscribers = subscribers :+ context.watch(newSubscriber)

    cache.filter(_.to > since).foreach { cachedSnapshot =>
      newSubscriber ! cachedSnapshot
    }
  }

  def unsubscribe(subscriber: ActorRef): Unit = {
    subscribers = subscribers.filterNot(_ == subscriber)
  }

  def dispatchTick(tick: TickMetricSnapshot): Unit = {
    val snapshot = marshalTick(tick)
    if(cache.size < maxCacheSize)
      cache = cache :+ snapshot
    else
      cache = cache.tail :+ snapshot

    subscribers.foreach(_ ! snapshot)
  }

  def marshalTick(tick: TickMetricSnapshot): MarshalledSnapshot = {
    import KamonMarshallers._
    val builder = new JStringBuilder()
    Marshalling.marshall(tick, builder)

    MarshalledSnapshot(tick.from, tick.to, builder.toString)
  }
}

object MarshalledSnapshotCache {
  def props(maxCacheSize: Int): Props = Props(new MarshalledSnapshotCache(maxCacheSize))

  case class Subscribe(subscriber: ActorRef, since: MilliTimestamp)
  case class MarshalledSnapshot(from: MilliTimestamp, to: MilliTimestamp, snapshotData: String)
}