import sbt._

object Dependencies {
  object Testing {
    private val _version = "3.2.14"
    final val * = Seq(
      "org.scalactic" %% "scalactic" % _version,
      "org.scalatest" %% "scalatest" % _version % Test
    )
  }
  object Lorem {
    final val * = Seq("fish.genius" %% "lorem" % "1.1.0" % Test)
  }

  object Uml {
    final val * = Seq("net.sourceforge.plantuml" % "plantuml" % "1.2023.1")
  }
  object GeniusFish {
    final val config = "fish.genius" %% "config" % "1.0.6"
    final val logging = "fish.genius" %% "logging" % "1.0.3"
    final val io = "fish.genius" %% "io" % "1.5.0"
    final val * = Seq(config, logging, io)
  }
}
