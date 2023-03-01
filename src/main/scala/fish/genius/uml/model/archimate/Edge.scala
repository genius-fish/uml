package fish.genius.uml.model.archimate

import fish.genius.uml.model.archimate.Edge.{EdgePrefix, EdgeSuffix}

object Edge {
  type EdgePrefix = String
  type EdgeSuffix = String
}
sealed trait Edge {

  def direction: String

  def asString(prefix: EdgePrefix, suffix: EdgeSuffix): String =
    s"$prefix$direction$suffix"
}

case object NoDirection extends Edge {
  override val direction: String = ""
}

case object Up extends Edge {
  override val direction: String = "UP"
}

case object Down extends Edge {
  override val direction: String = "DOWN"
}

case object Left extends Edge {
  override val direction: String = "LEFT"
}

case object Right extends Edge {
  override val direction: String = "RIGHT"
}
