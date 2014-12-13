package kamon.dashboard

import akka.actor._
import akka.io.Tcp.ConnectionClosed
import kamon.MilliTimestamp
import kamon.dashboard.MarshalledSnapshotCache.{MarshalledSnapshot, Subscribe}
import spray.http.CacheDirectives.`no-cache`
import spray.http.HttpHeaders.{RawHeader, `Cache-Control`}
import spray.http._
import spray.routing.{Directives, HttpServiceActor, Route}
import SseClient._


trait HttpApi extends Directives {
  private var sseClientCount = 0

  def buildRoute(cache: ActorRef)(implicit refFactory: ActorRefFactory): Route = {
    get {
      path("event-stream") { ctx =>
        subscribeSseClient(ctx.responder, new MilliTimestamp(0), cache)

      } ~
      path("") {
        getFromResource("dashboard-webapp/index.html")

      // Everything else must be requests for static content.
      } ~ getFromResourceDirectory("dashboard-webapp")

    }
  }

  def subscribeSseClient(connection: ActorRef, since: MilliTimestamp, cache: ActorRef)(implicit refFactory: ActorRefFactory): Unit = {
    sseClientCount += 1

    val sseClient = refFactory.actorOf(SseClient.props(connection), "sse-client-" + sseClientCount)
    cache ! Subscribe(sseClient, since)
  }
}

class HttpService(sseSubscriptions: ActorRef) extends HttpServiceActor with HttpApi {
  def receive = runRoute(buildRoute(sseSubscriptions))
}

object HttpService {
  def props(snapshotsCache: ActorRef): Props = Props(new HttpService(snapshotsCache))
}


class SseClient(connection: ActorRef) extends Actor with ActorLogging {
  initStream(connection)

  def receive = {
    case MarshalledSnapshot(_, to, data)  => pushData(to.millis.toString, data)
    case closedEvent: ConnectionClosed    => close(closedEvent.getErrorCause)
  }

  def pushData(id: String, data: String): Unit = {
    log.info("Packet Size: "+ data.length)
    log.info("Packet Data ==>\n"+ formatEvent(id, data))
    connection ! MessageChunk(formatEvent(id, data))
  }

  def formatEvent(id: String, data: String): String = "id: " + id + "\ndata: " + data + "\n\n"

  def initStream(connection: ActorRef): Unit = {
    val startMessage = HttpEntity(`text/event-stream`, formatEvent("0", "{}"))
    val firstChunk = ChunkedResponseStart(HttpResponse(status = StatusCodes.OK, entity = startMessage)
      .withHeaders(`Access-Control-Allow-Origin-From-Everywhere`, `Cache-Control`(`no-cache`)))

    connection ! firstChunk
  }

  def close(reason: String): Unit = {
    if(log.isDebugEnabled) {
      log.debug("Shutting down SseClient due to: {}", reason)
    }

    context stop self
  }
}

object SseClient {
  val `Access-Control-Allow-Origin-From-Everywhere` = RawHeader("Access-Control-Allow-Origin", "*")
  val `text/event-stream` = ContentType(MediaType.custom("text", "event-stream"), HttpCharsets.`UTF-8`)

  def props(connection: ActorRef): Props = Props(new SseClient(connection))
}
