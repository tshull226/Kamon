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

package kamon.js

import akka.actor._
import akka.event.Logging
import kamon.Kamon
import kamon.Kamon.Extension
import org.atmosphere.config.service.{Disconnect, Ready, ManagedService}
import org.atmosphere.cpr._
import org.atmosphere.nettosphere.{Config, Nettosphere}

object KamonJS extends ExtensionId[KamonJSExtension] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = KamonJS
  override def createExtension(system: ExtendedActorSystem): KamonJSExtension = new KamonJSExtension(system)
}

class KamonJSExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[KamonJSExtension])
  log.info("Starting the Kamon(JS) extension")

  val jsConfig = system.settings.config.getConfig("kamon.js")
}

object A {
  val server = new Nettosphere.Builder().config(
    new Config.Builder()
      .host("127.0.0.1")
      .port(8080)
      .resource(classOf[Searcher])
  .build())
  .build();
  server.start();
}


@ManagedService(path = "/search")
class Searcher {
  private var factory: BroadcasterFactory = null
  private lazy val system: ActorSystem = ActorSystem.create("atmoDemo")

  @Ready
  def onReady(r: AtmosphereResource) {
    factory = r.getAtmosphereConfig.getBroadcasterFactory
  }

  @Disconnect
  def onDisconnect(event: AtmosphereResourceEvent) {
  }

  @org.atmosphere.config.service.Message
  def onMessage(m: String) {
    org.slf4j.LoggerFactory.getLogger(classOf[Searcher]).info(s"Received message: $m")
    val b: Broadcaster = factory.lookup("/search")
    // create a search actor and send it the  message
    (system actorOf Props(classOf[SearchActor], b)) ! m

  }
}

class SearchActor(b: Broadcaster) extends Actor {

  def receive = {
    // prepend message with a breaking bad quote and send to originator
    case msg: String => b.broadcast(s"Fat Stax, yo! $msg")
  }
}
