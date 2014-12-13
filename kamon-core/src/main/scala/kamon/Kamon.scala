/* =========================================================================================
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
package kamon

import akka.actor
import scala.util.Success
import akka.actor._
import kamon.ModuleSupervisor.CreateModule

import scala.concurrent.{Promise, Future}

object Kamon {
  trait Extension extends akka.actor.Extension
  def apply[T <: Extension](key: ExtensionId[T])(implicit system: ActorSystem): T = key(system)
}

trait ModuleSupervisorExtension  extends Kamon.Extension {
  def createModule(name: String, props: Props): Future[ActorRef]
}

class ModuleSupervisorExtensionImpl(system: ExtendedActorSystem) extends  ModuleSupervisorExtension {
  private val supervisor = system.actorOf(Props[ModuleSupervisor], "kamon")

  def createModule(name: String, props: Props): Future[ActorRef] = {
    val modulePromise = Promise[ActorRef]()
    supervisor ! CreateModule(name, props, modulePromise)
    modulePromise.future
  }
}

class ModuleSupervisor extends Actor with ActorLogging {

  def receive = {
    case CreateModule(name, props, childPromise) => createChildModule(name, props, childPromise)
  }

  def createChildModule(name: String, props: Props, childPromise: Promise[ActorRef]): Unit = {
    context.child(name).map { alreadyAvailableModule =>
      log.warning("Received a request to create module [{}] but the module is already available, returning the existent one.")
      childPromise.complete(Success(alreadyAvailableModule))

    } getOrElse {
      childPromise.complete(Success(context.actorOf(props, name)))
    }
  }
}

object ModuleSupervisor extends ExtensionId[ModuleSupervisorExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: actor.Extension] = ModuleSupervisor
  def createExtension(system: ExtendedActorSystem): ModuleSupervisorExtension = new ModuleSupervisorExtensionImpl(system)

  case class CreateModule(name: String, props: Props, childPromise: Promise[ActorRef])
}
