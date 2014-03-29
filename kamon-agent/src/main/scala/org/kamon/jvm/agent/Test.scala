package org.kamon.jvm.agent

object Test extends App{
  println("main method invoked");
  println("userName: {}", new MyUser().getName())
}

class MyUser {
  def getName():String = {
    "foo";
  }

}
