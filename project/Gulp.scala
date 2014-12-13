import sbt._
import sbt.Keys._

object Gulp {
  lazy val gulpBuild = taskKey[Unit]("Invoke the gulp build process to pack the dashboard webapp.")

  val gulpSettings = Seq(
    gulpBuild := {
      val log = streams.value.log
      println("Trying to run in: " + gulpWorkingFolder(baseDirectory.value))
      val exitCode = Process("gulp" :: "sbt-build" :: Nil, gulpWorkingFolder(baseDirectory.value)) ! log

      require(exitCode == 0, "Gulp build didn't complete correctly.")


    },
    compile in Compile <<= (compile in Compile) dependsOn gulpBuild
  )

  def gulpWorkingFolder(projectBase: sbt.File): sbt.File = projectBase / "src" / "main" / "angular" / "tools"
}