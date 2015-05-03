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
    itemList :+ initial
    while (!itemList.isEmpty) {
      val item = itemList.head
      itemList = itemList.tail

      if (!(result contains item) && checkIfNotPrimitive(item)) {
        result = result + item
        for (field ← item.getClass().getDeclaredFields()) {
          field.setAccessible(true)
          itemList :+ field.get(item)
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
    //have some general info here
    val actorName = cellMetrics.entity.name
    val writeToFile = location != "none"
    if (writeToFile) createFoldersInPath(location + "/" + actorName)

    println("what I intend to write to a file")
    val messageMetrics = createMessageMetrics(cellMetrics)
    print(messageMetrics)

    if (writeToFile) {
      writeMessageToFile(location + "/" + actorName + "/messageMetrics.txt", messageMetrics)
    }
  }

  private def writeMessageToFile(path: String, message: String):Unit = {
    //this is simply a placeholder right now...
  }

  private def createFoldersInPath(path: String): Unit = {
    import java.io.File

    val folders = path.split("/")
    var name = ""
    var first = true
    for(spot <- folders){
      name = if (first) { first = false; spot } else "/" + spot
      val dir = new File(name)
      if(!dir.isDirectory()){
        //need to create the folder
        dir.mkdir() //should create the dir
      }
    }
  }

  private def createMessageMetrics(cellMetrics: ActorCellMetrics): String = {
    var message = ""

    message += "\n\nMessages Sent\n"
    val messagesSent = cellMetrics.messagesSent
    message += "number of messages sent %d\n".format(messagesSent.map{_._2}.sum)
    message += "number of different actors messages were sent to: %d\n".format(messagesSent.size)
    message += "number of objects(and fields) that can be touched by messages sent: %d\n".format(cellMetrics.reachableObjectsSent.size)
    message += "Message Sent Breakdown\n"
    for((act, num) <- messagesSent){
      message += "Actor Name:%s Number sent: %d\n".format(act.path.name, num)
    }

    message += "\n\nMessages Received\n"
    val messagesReceived = cellMetrics.messagesReceived
    message += "number of messages received %d\n".format(messagesReceived.map{_._2}.sum)
    message += "number of different actors messages were received from: %d\n".format(messagesReceived.size)
    message += "number of objects(and fields) that can be touched by messages received: %d\n".format(cellMetrics.reachableObjectsReceived.size)
    message += "Message Received Breakdown\n"
    for((act, num) <- messagesReceived){
      message += "Actor Name:%s Number received: %d\n".format(act.path.name, num)
    }

    message += "total number of objects(and fields) that can be touched by messages: %d\n".format((cellMetrics.reachableObjectsSent ++ cellMetrics.reachableObjectsReceived).size)

    //this is what I'm returning
    message
  }

}

//really want to keep some information about the object
class MessageRecord(obj: Any) {
  case class MessageInfo(kind: ReadWrite.ReadWrite, time: Long)
  var log = List[MessageInfo]()
  var status = ReadWrite.Unused
  //want to also 
  def this(obj: Any, kind: ReadWrite.ReadWrite) = {
    this(obj)
    addEntry(kind)
  }
  def addEntry(kind: ReadWrite.ReadWrite) = {
    log = log :+ MessageInfo(kind, System.nanoTime())
  }
  def printLog: Unit = {
    //eventually going to print everything here...
    println("this is the current message")
    println(obj)
    for(entry <- log){
      entry match{
        case MessageInfo(kind, time) => {
          println("kind " + "time ")
        }
        case _ => {
          println("BUG")
          return
        }
      }
    }
  }
  private def createMessageInfo: String = {
    for(entry <- log){
      entry match{
        case MessageInfo(kind, time) => {
          println("kind " + "time ")
        }
        case _ => {
          println("BUG")
          return "FAIL"
        }
      }
    }
    "placeholder"
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
  /*
   * using this to give a unique number to all recording of all gets
   * note this is possible because the aspect is a singleton object
   */
  var orderingCount = 0
  //TODO probably need to add a !withincode(monitor) to prevent an infinite loop
  @Pointcut("get(* *) && this(cell) && target(obj)")
  def objFieldGet(cell: ActorCell, obj: Any): Unit = {}

  @Before("objFieldGet(cell, obj)")
  def monitorGetFieldAccess(cell: ActorCell, obj: Any, jp: JoinPoint): Unit = {
    //not sure if I need join point

    //TODO will probably want to use StackTraceElements[] stackTraceElements = Thread.currentThread.getStackTrace
    // or something like this

    //don't want to do anything right now
    //val cellMetrics = cell.asInstanceOf[ActorCellMetrics]


    //need to update both lists
    //setStateOfMessage(cellMetrics.valuesReceived, obj, ReadWrite.Read)
    //setStateOfMessage(cellMetrics.valuesSent, obj, ReadWrite.Read)
  }

  //I'm just going to work on 1 at a time (it is unnecessary to fiddle with both)
  /*
  @Pointcut("set(* *)) this(cell) && target(obj) && args(args)")
  def objFieldSet(cell:ActorRef, obj: Any, args: Any): Unit = {}

  @Before("objFieldSet(cell, obj, args)")
  def monitorSetFieldAccess(cell: ActorRef, obj: Any, args: Any, jp: JoinPoint): Unit = {
  }
  */

  //need to do something different with this
  def setStateOfMessage(entry: Map[Any, ReadWrite.ReadWrite], message: Any, value: ReadWrite.ReadWrite): Unit = {
    if (entry.contains(message)) {
      val previous = entry(message)
      //want the strictest value seen at any point
      entry(message) = (previous) match {
        case ReadWrite.Unused ⇒ value
        case ReadWrite.Read   ⇒ if (value == ReadWrite.Write) { value } else { ReadWrite.Read }
        case ReadWrite.Write  ⇒ previous
      }
    }
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
