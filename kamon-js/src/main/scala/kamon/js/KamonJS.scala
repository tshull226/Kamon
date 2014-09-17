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

object KamonJS extends ExtensionId[KamonJS] with ExtensionIdProvider {
  override def lookup(): ExtensionId[_ <: Extension] = KamonJS
  override def createExtension(system: ExtendedActorSystem): KamonJSExtension = new KamonJSExtension(system)
}

class KamonJSExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val log = Logging(system, classOf[KamonJSExtension])
  log.info("Starting the Kamon(JS) extension")

  val jsConfig = system.settings.config.getConfig("kamon.js")
}


