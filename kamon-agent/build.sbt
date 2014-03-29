name := "kamon-agent"
 
version := "1.0"
 
scalaVersion := "2.10.3"

libraryDependencies += "org.ow2.asm" % "asm" % "5.0.1"

packageOptions in (Compile, packageBin) +=
  Package.ManifestAttributes("Premain-Class" -> "org.kamon.jvm.agent.KamonAgent",
                             "Agent-Class" -> "org.kamon.jvm.agent.KamonAgent",
                             "Can-Redefine-Classes" -> "true",
                             "Can-Retransform-Classes" -> "true")