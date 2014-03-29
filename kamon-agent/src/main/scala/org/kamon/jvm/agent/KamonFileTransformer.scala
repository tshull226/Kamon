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

package org.kamon.jvm.agent

import java.lang.instrument.ClassFileTransformer
import java.security.ProtectionDomain
import org.objectweb.asm.{Opcodes, ClassWriter, ClassReader}
;
class KamonFileTransformer extends ClassFileTransformer {
  override def transform(loader: ClassLoader, className: String, classBeingRedefined: Class[_], protectionDomain: ProtectionDomain, bytes: Array[Byte]): Array[Byte] = className match {

    // Intercept the call to the class Stuff
    case name if name.equals("org/kamon/jvm/agent/MyUser") => {
      val reader = new ClassReader(bytes);
      val writer = new ClassWriter(reader, 0);
      val visitor = new ClassPrinter(Opcodes.ASM5,writer);
      reader.accept(visitor, 0);
      return writer.toByteArray();
    }
    case _ => null
  }
}
