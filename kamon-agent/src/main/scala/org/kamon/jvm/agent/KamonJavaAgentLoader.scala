/* ===================================================
 * Copyright © 2013-2014 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */

package org.kamon.jvm

import java.lang.management.ManagementFactory

object KamonJavaAgentLoader {

  private val jarFilePath = ""

  def loadAgent():Unit = {
    println("dynamically loading javaagent")
    val nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName()
    val p = nameOfRunningVM.indexOf('@')
    val pid = nameOfRunningVM.substring(0, p)

    try {
//      VirtualMachine vm = VirtualMachine.attach(pid)
//      vm.loadAgent(jarFilePath, "")
//      vm.detach()
    } catch {
      case e: Exception => throw new RuntimeException(e)
    }
  }
 }
