package fish.genius.uml.dsl

import fish.genius.uml.ast.PUmlNode
import fish.genius.uml.dsl.markers.*

/**
 * Top-level diagram envelope: `@startuml ... @enduml`.
 *
 * Inside `uml { ... }` the `InUml` capability is in scope, which every domain
 * DSL (`archimate`, `sequence`, `activity`) requires. Calling those helpers
 * outside `uml { ... }` is a compile error.
 *
 * Example:
 * {{{
 *   block:
 *     uml:
 *       statement("Alice -> Bob: hi")
 * }}}
 */
def uml(body: (PUmlCtx, InUml) ?=> Unit): PUml[Unit] =
  given InUml = markers.InUml
  emit(PUmlNode.Diagram("uml", PUmlCtx.captureWith[InUml](body)))

/**
 * `@start{kind} ... @end{kind}` — same as [[uml]] but with a custom kind so
 * non-UML diagrams (gantt, mindmap, wbs, …) can also be authored.
 */
def diagram(kind: String)(body: (PUmlCtx, InUml) ?=> Unit): PUml[Unit] =
  given InUml = markers.InUml
  emit(PUmlNode.Diagram(kind, PUmlCtx.captureWith[InUml](body)))
