package org.kamon.jvm.agent

import org.objectweb.asm.{MethodVisitor, ClassVisitor}

class ClassPrinter(api:Int,cv:ClassVisitor) extends ClassVisitor(api:Int, cv:ClassVisitor) {

  override def visit(version: Int, access: Int, name: String, signature: String, superName: String, interfaces: Array[String]): Unit = {
    println(s"$name extends $superName {");
    super.visit(version, access, name, signature, superName, interfaces)
  }

  override def visitMethod(access: Int, name: String, desc: String, signature: String, exceptions: Array[String]): MethodVisitor = {
    println(s" $name$desc");
    super.visitMethod(access, name, desc, signature, exceptions)
  }

  override def visitEnd(): Unit = {
    println("}");
    super.visitEnd()
  }
}