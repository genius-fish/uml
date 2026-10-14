# Release 1.13.0

**Release Date:** 2026-10-14

This release adds native activity diagrams with swimlanes to `genius-uml-diagram`. `Activity.draw` lays out a process directly as the module's `Prim` geometry, without PlantUML. The `Tikz`, `Svg` and `Png` writers then render it in the same print look as the flat ArchiMate diagrams (`Diagram`) and sequence diagrams, so a document can set its processes and its architecture views in one style.

The layout is layered and runs from top to bottom with one column per lane. It handles exclusive, parallel, inclusive and event-based gateways, typed tasks and events, loops back, default flows and notes. Tasks narrow until the drawing fits within the page width. The release is additive: no existing API changed.

## Highlights

- **Activity diagrams with swimlanes** – `Activity.draw(graph, options)` returns a `Diagram.Drawing` that the existing TikZ, SVG and PNG writers can render.
- **BPMN-style notation in the ArchiMate look** – tasks get kind icons taken from ArchiMate shapes, events show their trigger glyphs, gateways show their kind marks, and lanes carry their performer's icon.
- **Layered layout with clean routing** – loops are set aside and routed back up round the right, branches leave a gateway from the corner facing their target, and crossings are broken the way `Diagram` breaks them.

## New Features

### Activity diagrams (`fish.genius.uml.diagram.Activity`)

You describe a process as a `Graph` of `Lane`s, `Node`s and `Flow`s and draw it with `Activity.draw`:

```scala
import fish.genius.uml.diagram.*
import fish.genius.uml.diagram.Activity.*
import fish.genius.uml.dsl.archimate.ShapeType

val drawing = Activity.draw(
  Graph(
    "Handle an order",
    List(Lane("sales", "Sales desk", Some(ShapeType.BusinessRole))),
    List(
      Node("start", "Order placed", Kind.Start(Trigger.Message), Some("sales")),
      Node("check", "Check the order", Kind.Work(Task.User), Some("sales"), Some("[CRM]")),
      Node("ok", "Complete?", Kind.Gateway(Gate.Exclusive), Some("sales")),
      Node("done", "Order accepted", Kind.End(Trigger.Plain), Some("sales")),
    ),
    List(
      Flow("start", "check"),
      Flow("check", "ok"),
      Flow("ok", "done", Some("yes")),
      Flow("ok", "check", Some("no"), default = true),
    ),
  )
)
```

The model:

- **`Lane(id, name, shape)`**: each lane is a column with its name in a header and, when `shape` is given, the icon of its performer (an ArchiMate `ShapeType`) in its layer's colour.
  - A node in no lane, or in a lane that is not listed, stands in a column of its own after the lanes.
  - A graph without lanes has one column and no header.
- **`Node(id, label, kind, lane, detail, notes)`**: `detail` is the line under a task's name (the systems that support it), and `notes` stand beside the node in a bracket, tied to it by a dotted line.
- **`Kind`** is one of the following:
  - `Work(task)`: a rounded box in the business colour.
  - `Start(trigger)`: a thin circle.
  - `Intermediate(trigger)`: a double circle.
  - `End(trigger)`: a thick circle.
  - `Gateway(gate)`: a diamond with its question above its right side.
- **`Task`** sets a task's icon:
  - `User` shows an actor, `Service` a component, `Script` an artifact, `Rule` a principle and `Call` a process.
  - `Manual` and `Plain` have no icon.
  - `Sub` adds a small plus at the bottom of the box.
- **`Trigger`** sets an event's glyph: `Plain`, `Message`, `Timer`, `Error`, `Signal` or `Terminate`.
- **`Gate`** sets a gateway's mark: `Exclusive` (X), `Parallel` (+), `Inclusive` (O) or `EventBased`.
- **`Flow(from, to, label, default)`**: a fine line with an arrowhead and its condition at its start. A `default` flow has a short stroke across its start.
- **`Options(style, pageWidth = 13.0, taskWidth = 2.8)`**: tasks wrap narrower (`taskWidth`, then 2.4 cm, then 2.0 cm) until the drawing fits within `pageWidth`. If no width fits, the narrowest drawing is used.

How the layout works:

- **Loops:** a walk from the start nodes finds the flows that close a loop, and those flows are set aside.
- **Rows:** each node is placed on the row given by its longest path from a start.
- **Tracks:** where one row holds more than one node in a lane, each node gets its own track in that lane, ordered under the nodes it comes from.
- **Flows:**
  - A flow goes straight down when it can.
  - Otherwise it goes down, across in a gap and down again, or round the right of whatever is in the way.
  - A loop goes back up round the right and comes into its target from the right.
  - Flows that merge into a gateway come in at a corner no outgoing branch has taken.
- **Scale:** the walk and the ranking recurse once per node of a chain. That suits processes of a few hundred steps, not tens of thousands.

## Changelog

### Added
- `Activity` drawing in `genius-uml-diagram`, with `Graph`, `Lane`, `Node`, `Flow`, `Kind`, `Task`, `Trigger`, `Gate`, `Options` and `Activity.draw`. It returns a `Diagram.Drawing` that `Tikz`, `Svg` and `Png` can render.
- DSL.md section "Activities: a process with swimlanes" documents the model, the notation and the layout.
- `ActivitySpec` checks the following:
  - every flow has an arrowhead and every node is drawn in its own shape;
  - no flow runs through a node it does not join;
  - nodes do not overlap and each stands in its lane's column;
  - the flow runs down the page and loops go back up round the right;
  - a branch leaves a gateway from the corner facing its target, with its condition there;
  - merges come in where no branch leaves;
  - notes are drawn in brackets;
  - tasks narrow to fit the page width;
  - duplicate flows are drawn once, and flows to unknown nodes are dropped;
  - an empty process still draws;
  - a task can loop to itself;
  - lanes show their performer's icon;
  - output is deterministic, and TikZ, SVG and PNG all write it.
- `DiagramGallery` gains a sample "Handle an order" process across three lanes. It has a choice with a default flow, a parallel split and join, a loop back, events with triggers and a note.

### Changed
- `Diagram.crossings` is now `private[diagram]`, so `Activity` breaks crossing flows the same way `Diagram` does. There is no change to the public API.

## Module Changes

- **`genius-uml-diagram`**: new `Activity` drawing; `Diagram` shares its crossing logic within the package.
- **Documentation**: new section in `DSL.md`.
- `core`, `render`, `scene` and `testkit`: unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, zio-process 0.8.0
- os-lib 0.11.8, sourcecode 0.4.4
- PlantUML 1.2026.2
- Eclipse ELK 0.9.1
- Built with Mill and `fish.genius::mill-release` 0.5.0

The release is source- and binary-compatible with 1.12.0, since it only adds API.
