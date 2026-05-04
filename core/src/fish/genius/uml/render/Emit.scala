package fish.genius.uml.render

import fish.genius.uml.ast.PUmlNode

/** Pure, total tree → String fold. */
object Emit:

  /** Render a [[PUmlNode]] to PlantUML source. */
  def emit(node: PUmlNode): String =
    val sb = new StringBuilder
    write(sb, node)
    sb.toString

  private def write(sb: StringBuilder, node: PUmlNode): Unit = node match
    case PUmlNode.Statement(s) =>
      sb.append(s)
      sb.append('\n')

    case PUmlNode.Sequence(cs) =>
      cs.foreach(write(sb, _))

    case PUmlNode.Block(header, body) =>
      sb.append(header).append(" {\n")
      write(sb, body)
      sb.append("}\n")

    case PUmlNode.Diagram(kind, body) =>
      sb.append("@start").append(kind).append('\n')
      write(sb, body)
      sb.append("@end").append(kind).append('\n')

    case PUmlNode.Comment(t) =>
      sb.append("' ").append(t).append('\n')

end Emit
