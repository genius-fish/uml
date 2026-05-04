package fish.genius.uml.ast

/**
 * The pure PlantUML-source AST.
 *
 * Every DSL helper is a smart constructor that emits one of these into a
 * [[fish.genius.uml.dsl.PUmlCtx]]. The renderer
 * ([[fish.genius.uml.render.Emit]]) is a total fold from this ADT to a
 * `String` of PlantUML source.
 */
enum PUmlNode derives CanEqual:

  /** A single literal PlantUML line, written verbatim. */
  case Statement(value: String)

  /** A flat sequence of sibling nodes. */
  case Sequence(children: Vector[PUmlNode])

  /**
   * `header { body }` — a header line introducing a brace-delimited body.
   * Used by Archimate boundaries, sequence boxes, etc.
   */
  case Block(header: String, body: PUmlNode)

  /**
   * `@start{kind} body @end{kind}` — the top-level diagram envelope. The
   * default kind is `uml`; PlantUML supports `gantt`, `wbs`, `mindmap`, ...
   */
  case Diagram(kind: String, body: PUmlNode)

  /** `' text` — emitted on its own line. */
  case Comment(text: String)

end PUmlNode

object PUmlNode:

  /** Convenience: an empty sequence (renders to nothing). */
  val empty: PUmlNode = Sequence(Vector.empty)
