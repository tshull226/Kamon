/*
 * =========================================================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
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

package akka.kamon.instrumentation

import akka.actor._
import akka.dispatch.{ Envelope, MessageDispatcher }
import akka.routing.RoutedActorCell
import kamon.Kamon
import kamon.akka.{ RouterMetrics, ActorMetrics }
import kamon.akka.{ AkkaExtension, Akka }
import kamon.metric.Entity
import kamon.trace._
import org.aspectj.lang.{ ProceedingJoinPoint, JoinPoint }
import org.aspectj.lang.annotation._
import scala.collection.mutable.{ Map }

@Aspect
class ActorCellInstrumentation {

  @Pointcut("execution(akka.actor.ActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, parent)")
  def actorCellCreation(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @After("actorCellCreation(cell, system, ref, props, dispatcher, parent)")
  def afterCreation(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {
    def isRootSupervisor(path: String): Boolean = path.length == 0 || path == "user" || path == "system"

    val pathString = ref.path.elements.mkString("/")
    val actorEntity = Entity(system.name + "/" + pathString, ActorMetrics.category)

    if (!isRootSupervisor(pathString) && Kamon.metrics.shouldTrack(actorEntity)) {
      val actorMetricsRecorder = Kamon.metrics.entity(ActorMetrics, actorEntity)
      val cellMetrics = cell.asInstanceOf[ActorCellMetrics]

      cellMetrics.entity = actorEntity
      cellMetrics.recorder = Some(actorMetricsRecorder)
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.invoke(*)) && this(cell) && args(envelope)")
  def invokingActorBehaviourAtActorCell(cell: ActorCell, envelope: Envelope) = {}

  @Around("invokingActorBehaviourAtActorCell(cell, envelope)")
  def aroundBehaviourInvoke(pjp: ProceedingJoinPoint, cell: ActorCell, envelope: Envelope): Any = {
    val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    val timestampBeforeProcessing = System.nanoTime()
    val contextAndTimestamp = envelope.asInstanceOf[TimestampedTraceContextAware]
    //getting the message and sender from the envelope
    val Envelope(message, sender) = envelope
    //used to determine if a new actor sent a message
    val origLength = cellMetrics.messagesReceived.size
    //keeping track of the number of messages received from each actor
    cellMetrics.messagesReceived(sender) = cellMetrics.messagesReceived.getOrElse(sender, 0) + 1
    //monitoring the objects reachable from messages sent to the actor
    cellMetrics.reachableObjectsReceived = cellMetrics.reachableObjectsReceived ++ FieldAnalysisHelper.findAllReachingObjects(message)
    //recording if a new actor sent a message to this actor
    if (origLength < cellMetrics.messagesReceived.size) {
      cellMetrics.recorder.map {
        _.numActorsReceivedFrom.increment()
      }
    }
    try {
      Tracer.withContext(contextAndTimestamp.traceContext) {
        pjp.proceed()
      }
    } finally {
      val processingTime = System.nanoTime() - timestampBeforeProcessing
      val timeInMailbox = timestampBeforeProcessing - contextAndTimestamp.captureNanoTime

      cellMetrics.recorder.map { am ⇒
        am.processingTime.record(processingTime)
        am.timeInMailbox.record(timeInMailbox)
        am.mailboxSize.decrement()
        //this is the total number of messages received (and processed)
        am.messagesProcessed.increment()
      }

      // In case that this actor is behind a router, record the metrics for the router.
      envelope.asInstanceOf[RouterAwareEnvelope].routerMetricsRecorder.map { rm ⇒
        rm.processingTime.record(processingTime)
        rm.timeInMailbox.record(timeInMailbox)
      }
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInActorCell(cell: ActorCell, envelope: Envelope): Unit = {}

  @After("sendMessageInActorCell(cell, envelope)")
  def afterSendMessageInActorCell(cell: ActorCell, envelope: Envelope): Unit = {
    val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellMetrics.recorder.map(_.mailboxSize.increment())
    //getting the message and sender from the envelope
    val Envelope(message, sender) = envelope
    //used to determine if a new actor sent a message
    val origLength = cellMetrics.messagesSent.size
    //keeping track of the number of mssages sent to each actor
    cellMetrics.messagesSent(sender) = cellMetrics.messagesSent.getOrElse(sender, 0) + 1
    cellMetrics.recorder.map { am ⇒
      //this is the total number of messages sent
      am.messagesSent.increment()
      if (origLength < cellMetrics.messagesSent.size) am.numActorsSentTo.increment()
    }
    //monitoring the objects reachable from messages sent from the actor
    cellMetrics.reachableObjectsSent = cellMetrics.reachableObjectsSent ++ FieldAnalysisHelper.findAllReachingObjects(message)
  }

  @Pointcut("execution(* akka.actor.ActorCell.stop()) && this(cell)")
  def actorStop(cell: ActorCell): Unit = {}

  @After("actorStop(cell)")
  def afterStop(cell: ActorCell): Unit = {
    val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellMetrics.recorder.map { _ ⇒
      Kamon.metrics.removeEntity(cellMetrics.entity)
    }

    // The Stop can't be captured from the RoutedActorCell so we need to put this piece of cleanup here.
    if (cell.isInstanceOf[RoutedActorCell]) {
      val routedCellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]
      routedCellMetrics.routerRecorder.map { _ ⇒
        Kamon.metrics.removeEntity(routedCellMetrics.routerEntity)
      }
    }
  }

  @Pointcut("execution(* akka.actor.ActorCell.handleInvokeFailure(..)) && this(cell)")
  def actorInvokeFailure(cell: ActorCell): Unit = {}

  @Before("actorInvokeFailure(cell)")
  def beforeInvokeFailure(cell: ActorCell): Unit = {
    val cellWithMetrics = cell.asInstanceOf[ActorCellMetrics]
    cellWithMetrics.recorder.map(_.errors.increment())

    // In case that this actor is behind a router, count the errors for the router as well.
    val envelope = cell.currentMessage.asInstanceOf[RouterAwareEnvelope]
    if (envelope ne null) {
      // The ActorCell.handleInvokeFailure(..) method is also called when a failure occurs
      // while processing a system message, in which case ActorCell.currentMessage is always
      // null.
      envelope.routerMetricsRecorder.map(_.errors.increment())
    }
  }

}

object FieldAnalysisHelper {

  def checkIfNotPrimitive(value: Any): Boolean = {
    var result: Boolean = false
    (value) match {
      case _: Unit    ⇒ result = false
      case _: Boolean ⇒ result = false
      case _: Byte    ⇒ result = false
      case _: Char    ⇒ result = false
      case _: Short   ⇒ result = false
      case _: Int     ⇒ result = false
      case _: Long    ⇒ result = false
      case _: Float   ⇒ result = false
      case _: Double  ⇒ result = false
      case _: AnyRef  ⇒ result = true
    }
    result
  }

  def findAllReachingObjects(initial: Any): Set[Any] = {
    var result = Set[Any]()
    var itemList = List[Any]()
    itemList = itemList :+ initial
    while (!itemList.isEmpty) {
      val item = itemList.head
      itemList = itemList.tail

      if ((!(result.contains(item))) && checkIfNotPrimitive(item)) {
        result = result + item

        for (field ← item.getClass().getDeclaredFields()) {
          field.setAccessible(true)
          itemList = itemList :+ initial
        }
      }
    }
    result
  }

  def writeAllInfoToFile(cellMetrics: ActorCellMetrics): Unit = {
    //should import some stuff here
    //need config info for the file location
    val akkaExtension = Kamon.extension(Akka)
    val location = akkaExtension.writeActorInfoFileLocation
    //want to not try to print out anything for null locations
    //have some general info here
    var actorName = ""
    //TODO need to normalize these actor names so there are no slashes
    //TODO also probably should use ActorRef.path.name here as will (just not sure how to get that)
    try {
      actorName = cellMetrics.entity.name
    } catch {
      case _: Throwable ⇒ return //want to catch everything in the easiest way possible
    }
    println("Actor Name: " + actorName)
    val writeToFile = location != "none"
    if (writeToFile) createFoldersInPath(location + "/" + actorName)

    println("what I intend to write to a file")
    val messageMetrics = createMessageMetrics(cellMetrics)
    val objectsTouched = createObjectTouchedLog(cellMetrics)
    print(messageMetrics)
    print(objectsTouched)

    if (writeToFile) {
      writeMessageToFile(location + "/" + actorName + "/messageMetrics.txt", messageMetrics)
    }
  }

  private def writeMessageToFile(path: String, message: String): Unit = {
    //this is simply a placeholder right now...
  }

  private def createFoldersInPath(path: String): Unit = {
    import java.io.File

    val folders = path.split("/")
    var name = ""
    var first = true
    for (spot ← folders) {
      name = if (first) { first = false; spot } else "/" + spot
      val dir = new File(name)
      if (!dir.isDirectory()) {
        //need to create the folder
        dir.mkdir() //should create the dir
      }
    }
  }

  private def createObjectTouchedLog(cellMetrics: ActorCellMetrics): String = {
    var message = ""
    message += "\n\nBreakdown of Objects Touched\n\n"
    message += "\n\nMessages Received\n\n"
    for ((key, value) ← cellMetrics.valuesReceived) {
      message += value.createMessageInfo()
    }

    message += "\n\nMessages Sent\n\n"
    for ((key, value) ← cellMetrics.valuesSent) {
      message += value.createMessageInfo()
    }

    message
  }

  private def createMessageMetrics(cellMetrics: ActorCellMetrics): String = {
    var message = ""

    //TODO need to align these messages
    message += "total number of objects(and fields) that can be touched by messages: %d\n\n".format((cellMetrics.reachableObjectsSent ++ cellMetrics.reachableObjectsReceived).size)
    message += "\n\nMessages Sent\n"
    val messagesSent = cellMetrics.messagesSent
    message += "number of messages sent %d\n".format(messagesSent.map { _._2 }.sum)
    message += "number of different actors messages were sent to: %d\n".format(messagesSent.size)
    message += "number of objects(and fields) that can be touched by messages sent: %d\n".format(cellMetrics.reachableObjectsSent.size)
    message += "Message Sent Breakdown\n"
    for ((act, num) ← messagesSent) {
      message += "Actor Name:%s Number sent: %d\n".format(act.path.name, num)
    }

    message += "\n\nMessages Received\n"
    val messagesReceived = cellMetrics.messagesReceived
    message += "number of messages received %d\n".format(messagesReceived.map { _._2 }.sum)
    message += "number of different actors messages were received from: %d\n".format(messagesReceived.size)
    message += "number of objects(and fields) that can be touched by messages received: %d\n".format(cellMetrics.reachableObjectsReceived.size)
    message += "Message Received Breakdown\n"
    for ((act, num) ← messagesReceived) {
      message += "Actor Name:%s Number received: %d\n".format(act.path.name, num)
    }

    //this is what I'm returning
    message
  }

}

//TODO need to adjust this message
//really want to keep some information about the object
class MessageRecord(obj: Any) {
  case class MessageInfo(kind: ReadWrite.ReadWrite, number: Int, time: Long)
  var log = List[MessageInfo]()
  var status = ReadWrite.Unused
  //want to also 
  def this(obj: Any, kind: ReadWrite.ReadWrite, number: Int) = {
    this(obj)
    addEntry(kind, number)
  }
  def addEntry(kind: ReadWrite.ReadWrite, number: Int) = {
    updateStatus(kind)
    log = log :+ MessageInfo(kind, number, System.currentTimeMillis())
  }
  def printLog: Unit = {
    println(createMessageInfo())
  }
  private def updateStatus(value: ReadWrite.ReadWrite): Unit = {
    status = value match {
      case ReadWrite.Read   ⇒ if (status == ReadWrite.Write) { status } else { ReadWrite.Read }
      case ReadWrite.Write  ⇒ ReadWrite.Write
      case ReadWrite.Unused ⇒ status //this doesn't even make sense
    }
  }
  def createMessageInfo(): String = {
    var message = ""
    message += "\n\nNew Log Entry\n"
    message += "log object: %s, status: %s\n".format(obj, status)
    for (entry ← log) {
      entry match {
        case MessageInfo(kind, num, time) ⇒ {
          message += "kind: %s, num: %d, time: %d\n".format(kind, num, time)
        }
      }
    }
    message
  }
}

@Aspect
class RoutedActorCellInstrumentation {

  @Pointcut("execution(akka.routing.RoutedActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, routeeProps, supervisor)")
  def routedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {}

  @After("routedActorCellCreation(cell, system, ref, props, dispatcher, routeeProps, supervisor)")
  def afterRoutedActorCellCreation(cell: RoutedActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, routeeProps: Props, supervisor: ActorRef): Unit = {
    val routerEntity = Entity(system.name + "/" + ref.path.elements.mkString("/"), RouterMetrics.category)

    if (Kamon.metrics.shouldTrack(routerEntity)) {
      val cellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]

      cellMetrics.routerEntity = routerEntity
      cellMetrics.routerRecorder = Some(Kamon.metrics.entity(RouterMetrics, routerEntity))
    }
  }

  @Pointcut("execution(* akka.routing.RoutedActorCell.sendMessage(*)) && this(cell) && args(envelope)")
  def sendMessageInRouterActorCell(cell: RoutedActorCell, envelope: Envelope) = {}

  @Around("sendMessageInRouterActorCell(cell, envelope)")
  def aroundSendMessageInRouterActorCell(pjp: ProceedingJoinPoint, cell: RoutedActorCell, envelope: Envelope): Any = {
    val cellMetrics = cell.asInstanceOf[RoutedActorCellMetrics]
    val timestampBeforeProcessing = System.nanoTime()
    val contextAndTimestamp = envelope.asInstanceOf[TimestampedTraceContextAware]

    try {
      Tracer.withContext(contextAndTimestamp.traceContext) {

        // The router metrics recorder will only be picked up if the message is sent from a tracked router.
        RouterAwareEnvelope.dynamicRouterMetricsRecorder.withValue(cellMetrics.routerRecorder) {
          pjp.proceed()
        }
      }
    } finally {
      cellMetrics.routerRecorder map { routerRecorder ⇒
        routerRecorder.routingTime.record(System.nanoTime() - timestampBeforeProcessing)
      }
    }
  }
}

object ReadWrite extends Enumeration {
  type ReadWrite = Value
  val Unused, Read, Write = Value
}

trait ActorCellMetrics {
  var entity: Entity = _
  var recorder: Option[ActorMetrics] = None
  var messagesSent = Map[ActorRef, Int]()
  var messagesReceived = Map[ActorRef, Int]()
  var valuesSent = Map[Any, MessageRecord]() //used to log the use of values from records sent out
  var valuesReceived = Map[Any, MessageRecord]() //used to log the use of values from records received
  var reachableObjectsSent = Set[Any]() //used to store all values that the actor can touch from records sent
  var reachableObjectsReceived = Set[Any]() //used to store all values that the actor can touch from records received
}

trait RoutedActorCellMetrics {
  var routerEntity: Entity = _
  var routerRecorder: Option[RouterMetrics] = None
}

trait RouterAwareEnvelope {
  def routerMetricsRecorder: Option[RouterMetrics]
}

object RouterAwareEnvelope {
  import scala.util.DynamicVariable
  private[kamon] val dynamicRouterMetricsRecorder = new DynamicVariable[Option[RouterMetrics]](None)

  def default: RouterAwareEnvelope = new RouterAwareEnvelope {
    val routerMetricsRecorder: Option[RouterMetrics] = dynamicRouterMetricsRecorder.value
  }
}

@Aspect
class MetricsIntoActorCellsMixin {

  @DeclareMixin("akka.actor.ActorCell")
  def mixinActorCellMetricsToActorCell: ActorCellMetrics = new ActorCellMetrics {}

  @DeclareMixin("akka.routing.RoutedActorCell")
  def mixinActorCellMetricsToRoutedActorCell: RoutedActorCellMetrics = new RoutedActorCellMetrics {}

}

@Aspect
class TraceContextIntoEnvelopeMixin {

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinTraceContextAwareToEnvelope: TimestampedTraceContextAware = TimestampedTraceContextAware.default

  @DeclareMixin("akka.dispatch.Envelope")
  def mixinRouterAwareToEnvelope: RouterAwareEnvelope = RouterAwareEnvelope.default

  @Pointcut("execution(akka.dispatch.Envelope.new(..)) && this(ctx)")
  def envelopeCreation(ctx: TimestampedTraceContextAware): Unit = {}

  @After("envelopeCreation(ctx)")
  def afterEnvelopeCreation(ctx: TimestampedTraceContextAware with RouterAwareEnvelope): Unit = {
    // Necessary to force the initialization of ContextAware at the moment of creation.
    ctx.traceContext
    ctx.routerMetricsRecorder
  }
}
/*
  this is so that all actor cells will flush its information when the actor shuts down
  *note that this was heavily copied from the actorCellCreation pointcut above (i just changed 'execution' to 'initialization')
*/
@Aspect
class EnableWriteToFileOnShutdown {
  @Pointcut("initialization(akka.actor.ActorCell.new(..)) && this(cell) && args(system, ref, props, dispatcher, parent)")
  def actorCellInitialization(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {}

  @After("actorCellInitialization(cell, system, ref, props, dispatcher, parent)")
  def afterInitialization(cell: ActorCell, system: ActorSystem, ref: ActorRef, props: Props, dispatcher: MessageDispatcher, parent: ActorRef): Unit = {
    sys.addShutdownHook {
      val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
      FieldAnalysisHelper.writeAllInfoToFile(cellMetrics)
    }
  }
}

@Aspect
class MonitorMessageValues {

  //TODO add pointcut for bs method that I am going to use to pass info to this pointcut
  //TODO to know what the dummy methods real name is just throw an error in it...

  /*
   * using this to give a unique number to all recording of all gets
   * note this is possible because the aspect is a singleton object
   */
  //use getAndIncrement() to modify this variable
  val orderingCount = new java.util.concurrent.atomic.AtomicInteger(0)
  val threadMapping = new java.util.concurrent.ConcurrentHashMap[Thread, ActorCellMetrics]

  //For recording the actor cell at reception of a message and linking it with a thread number
  @Pointcut("execution(* akka.actor.ActorCell.receiveMessage(*)) && target(cell)")
  def actorReceiveStart(cell: ActorCell): Unit = {}

  @Before("actorReceiveStart(cell)")
  def beforeActorReceiveStart(cell: ActorCell): Unit = {
    val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    val threadNum = Thread.currentThread()
    threadMapping.put(threadNum, cellMetrics)
  }

  @After("actorReceiveStart(cell)")
  def afterActorReceiveStart(cell: ActorCell): Unit = {
    val threadNum = Thread.currentThread()
    threadMapping.remove(threadNum)
  }
  /*
  @Around("actorReceiveStart(cell)")
  def aroundActorReceiveStart(pjp: ProceedingJoinPoint, cell: ActorCell): Unit = {
    throw new NullPointerException("definitely gets in here")
    val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
    val threadNum = Thread.currentThread()
    threadMapping.put(threadNum, cellMetrics)
    try {
      pjp.proceed()
    } finally {
      //want to remove the marker from the aspect
      threadMapping.remove(threadNum)
    }
  }
  */

  @Pointcut("get(* *) && target(obj)")
  def objFieldGet(obj: Any): Unit = {}
  //@Pointcut("get(* *) && this(cell) && target(obj)")
  //@Pointcut("get(* *) && target(obj) && if()")
  //def objFieldGet(obj: Any): Boolean = {
  //  if (threadMapping.containsKey(Thread.currentThread())) { true } else { false }
  //}

  //@Before("objFieldGet(cell, obj)")
  //@Before("objFieldGet(obj) && !withincode(* akka.kamon.instrumentation.MonitorMessageValues.monitorGetFieldAccess(..))")
  @Before("objFieldGet(obj) && !cflow(within(akka.kamon.instrumentation.MonitorMessageValues))")
  def monitorGetFieldAccess(obj: Any): Unit = {
    //look at this to see if there is a ActorCell associated with this thread
    val threadNum = Thread.currentThread()
    if (!threadMapping.containsKey(threadNum)) {
      return
    }

    val cellMetrics: ActorCellMetrics = threadMapping.get(threadNum)

    if (cellMetrics.reachableObjectsReceived.contains(obj)) {
      updateMessageInfo(cellMetrics.valuesReceived, obj, ReadWrite.Read)
    }
    if (cellMetrics.reachableObjectsSent.contains(obj)) {
      updateMessageInfo(cellMetrics.valuesSent, obj, ReadWrite.Read)
    }
  }

  //@Pointcut("set(* *) && target(obj)")
  //TODO get this working right
  //@Pointcut("set(* *)")
  def objFieldSet(): Unit = {}

  //@After("objFieldSet(obj) && !cflow(within(akka.kamon.instrumentation.MonitorMessageValues))")
  //@After("objFieldSet() && !cflow(within(akka.kamon.instrumentation.MonitorMessageValues))")
  def monitorSetFieldAccess(): Unit = {
    //def monitorSetFieldAccess(): Unit = {
    //look at this to see if there is a ActorCell associated with this thread
    return //test when I am doing nothing in it...
    /*
    val threadNum = Thread.currentThread()
    if (!threadMapping.containsKey(threadNum)) {
      return
    }

    val cellMetrics: ActorCellMetrics = threadMapping.get(threadNum)

    if (cellMetrics.reachableObjectsReceived.contains(obj)) {
      updateMessageInfo(cellMetrics.valuesReceived, obj, ReadWrite.Write)
    }
    if (cellMetrics.reachableObjectsSent.contains(obj)) {
      updateMessageInfo(cellMetrics.valuesSent, obj, ReadWrite.Write)
    }
    */

  }

  private def updateMessageInfo(record: Map[Any, MessageRecord], obj: Any, value: ReadWrite.ReadWrite): Unit = {
    if (!record.contains(obj)) {
      record(obj) = new MessageRecord(obj)
    }
    val num = orderingCount.getAndIncrement()
    record(obj).addEntry(value, num)
  }
}

//will to this later...
@Aspect
class MonitorActorStateChange {
  @Pointcut("get(* ActorCell+) && this(cell) && target(obj)")
  def actorFieldGet(cell: ActorCell, obj: Any): Unit = {}

  @Before("actorFieldGet(cell, obj)")
  def monitorActorFieldGetAccess(cell: ActorCell, obj: Any, jp: JoinPoint): Unit = {

    //don't want to do anything right now
    //val cellMetrics = cell.asInstanceOf[ActorCellMetrics]
  }

}
