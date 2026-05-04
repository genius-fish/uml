package fish.genius.uml.dsl.archimate

object Edge:
  type EdgePrefix = String
  type EdgeSuffix = String

/**
 * Direction of an Archimate relationship arrow. Renders into the middle of
 * a relationship's PlantUML connector token (e.g. `-DOWN->>`, `..UP.>`).
 */
enum Edge(val direction: String):

  case NoDirection extends Edge("")
  case Up extends Edge("UP")
  case Down extends Edge("DOWN")
  case Left extends Edge("LEFT")
  case Right extends Edge("RIGHT")

  def asString(prefix: Edge.EdgePrefix, suffix: Edge.EdgeSuffix): String =
    s"$prefix$direction$suffix"

