package fish.genius.uml.dsl.activity

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.markers.*

/**
 * The PlantUML "Activity Beta" DSL: titled flows of actions, branched by
 * `if` / `while` constructs and bracketed by `start`/`stop`.
 *
 * `activityDiagram { ... }` injects an [[InActivity]] capability so the body
 * can use `start`, `action`, `stop` etc. without having to thread the marker
 * manually. Named `activityDiagram` to avoid a name clash with the
 * surrounding `fish.genius.uml.dsl.activity` package.
 */
def activityDiagram(
  body: (PUmlCtx, InActivity) ?=> Unit
)(
  using PUmlCtx,
  InUml,
): Unit =
  given InActivity = markers.InActivity
  body

/** Emit `title <text>`. */
def title(
  value: String
)(
  using PUmlCtx,
  InActivity,
): Unit =
  statement(s"title $value \n")

/** Emit a single action step `:value;`. */
def action(
  name: String
)(
  using PUmlCtx,
  InActivity,
): Unit =
  statement(s":$name;")

/** Emit `start`. */
def start(
)(
  using PUmlCtx,
  InActivity,
): Unit =
  statement("start")

/** Emit `stop`. */
def stop(
)(
  using PUmlCtx,
  InActivity,
): Unit =
  statement("stop")

/** Emit `end`. */
def end(
)(
  using PUmlCtx,
  InActivity,
): Unit =
  statement("end")

/** Emit a free-form note attached to the previous action. */
def note(
  side: String,
  body: String,
)(
  using PUmlCtx,
  InActivity,
): Unit =
  statement(s"note $side")
  statement(body)
  statement("end note")
