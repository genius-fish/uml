# Release 1.14.0

**Release Date:** 2026-10-15

This release brings the flat activity diagram in the `diagram` module up to BPMN's Analytic conformance level. `Activity.draw` now draws every BPMN event trigger, in both catching and throwing form. It also draws boundary events on a task's edge, expanded sub-processes, transactions and event sub-processes, task markers, data objects and data stores, messages between pools (black-box pools included), and compensation associations. Groups are still not drawn.

The layout also improves: a node now stands directly under the node in its lane that it comes from, wherever there is room, so straight runs stay straight. Most of the new model fields have defaults, so existing graphs still compile. Pattern matches on `Kind.Start` and `Kind.Intermediate`, and exhaustive matches on the extended enums, need updating (see Breaking Changes).

## Highlights

- **BPMN Analytic level in activity diagrams**: the full trigger set, boundary events, expanded containers, markers, data, messages and associations.
- **Expanded sub-processes**: a `Kind.Container` lays out the nodes it holds inside its frame and takes its place in the flow as one block. Containers can nest.
- **Messages between pools**: a dashed line from an open circle to an open head, which may end at a pool drawn as a black box.
- **Straighter layouts**: a node is placed under its predecessor in the same lane when it fits.

## Breaking Changes

- `Activity.Kind.Start(trigger)` is now `Start(trigger, interrupting = true)` and `Activity.Kind.Intermediate(trigger)` is now `Intermediate(trigger, throwing = false)`. Constructor calls still compile, but a pattern with a single field, such as `case Kind.Start(t) =>`, does not. Use `case Kind.Start(t, _) =>`.
- `Activity.Kind`, `Activity.Task`, `Activity.Trigger`, `Activity.Gate` and `Head` gained cases. Exhaustive matches on them in client code are no longer exhaustive.
- `Activity.Node` and `Activity.Graph` gained defaulted fields. Code that calls them by name or position still compiles, but the release is not binary compatible with 1.13.0, so recompile against it.

## New Features

### Every event trigger, caught and thrown

`Trigger` now covers the full BPMN set: `Plain`, `Message`, `Timer`, `Error`, `Signal`, `Terminate`, `Escalation`, `Cancel`, `Compensation`, `Conditional`, `Link`, `Multiple` and `ParallelMultiple`. The original cases keep their ordinals.

A catching event draws its trigger open. A throwing event draws it filled: that means an intermediate event with `throwing = true`, and every end event. A start with `interrupting = false`, which is how an event sub-process starts, is drawn dashed.

```scala
import fish.genius.uml.diagram.Activity.*

Node("notify", "Notify the warehouse", Kind.Intermediate(Trigger.Signal, throwing = true), Some("sales"))
Node("cancel", "Cancellation requested", Kind.Start(Trigger.Message, interrupting = false), parent = Some("onCancel"))
```

### Boundary events

A `Kind.Boundary(trigger, interrupting)` node sits on the bottom edge of the node named by its `host`. It is drawn as a double circle, dashed when it does not interrupt. It takes its host's row, and its outgoing flows leave from below. A host with several boundary events grows wide enough to hold them side by side. A boundary event whose host is missing is drawn as an ordinary node.

```scala
Node("late", "48 hours", Kind.Boundary(Trigger.Timer, interrupting = false), Some("sales"), host = Some("confirm"))
```

### Expanded sub-processes, transactions and event sub-processes

A `Kind.Container(frame)` holds every node whose `parent` points to it. The frame shows its type:

- `Frame.Sub`: a solid frame.
- `Frame.Transaction`: a double frame.
- `Frame.Event`: a dotted frame.

The container lays out its contents on its own, inside its lane, and appears in the flow as one block with its name at the top left.

- Containers nest.
- An event sub-process that no flow reaches is placed under everything else.
- A container with no nodes is drawn collapsed, as a task with the plus marker.
- A `parent` that is not a container, or a cycle of parents, is ignored.
- A flow between a node inside a container and one outside it is not drawn, because BPMN does not allow it.

```scala
Node("pack", "Pack and ship", Kind.Container(Frame.Sub), Some("warehouse"))
Node("pick", "Pick items", Kind.Work(Task.Manual), Some("warehouse"),
  markers = List(Marker.Parallel), parent = Some("pack"))
```

### Task kinds and markers

`Task` gains `Send` (a filled envelope) and `Receive` (an open envelope). A node's new `markers` list draws markers at the task's foot, beside the sub-process plus. The markers are `Loop`, `Parallel` and `Sequential` multi-instance, `Compensation` and `AdHoc`.

### Gateways

`Gate` gains `Complex`, drawn as an asterisk, and `ParallelEventBased`, drawn as a plus inside a circle.

### Data objects and data stores

`Node.data` takes a list of `Datum(label, store = false, output = false)`. Each datum stands in a column beside its task, as a page with a folded corner or, with `store = true`, as a cylinder. A dotted line ties it to the task, with its head at the task when the task reads the data and at the data when the task writes it (`output = true`). Data attached to a container is not drawn.

### Messages and associations

`Graph.messages` takes `Message(from, to, label)` values. A message runs between pools as a dashed line from an open circle (the new `Head.OpenDot`) to an open head, leaving from the side of its source that faces its target, with its label over the middle. Either end may be a lane with no nodes: that lane is drawn as a black-box pool, a narrow column whose near edge the message meets. A message to or from a node inside a container meets the outermost frame that is drawn.

`Graph.associations` takes `Association(from, to)` values, from a compensation boundary event to the task that compensates it. Each is drawn as a dotted line with a head, and the compensating task is placed under the event's host.

```scala
Graph(
  "Ship an order",
  lanes, nodes, flows,
  messages = List(Message("confirm", "customer", Some("Confirmation"))),
  associations = List(Association("failed", "refund")),
)
```

## Changelog

### Added
- `Activity.Trigger`: `Escalation`, `Cancel`, `Compensation`, `Conditional`, `Link`, `Multiple`, `ParallelMultiple`.
- `Activity.Gate`: `Complex`, `ParallelEventBased`.
- `Activity.Task`: `Send`, `Receive`.
- `Activity.Marker` (`Loop`, `Parallel`, `Sequential`, `Compensation`, `AdHoc`) and `Node.markers`.
- `Activity.Kind.Boundary(trigger, interrupting)` and `Node.host`.
- `Activity.Kind.Container(frame)`, `Activity.Frame` (`Sub`, `Transaction`, `Event`) and `Node.parent`.
- `Activity.Datum` and `Node.data`.
- `Activity.Message` and `Graph.messages`, `Activity.Association` and `Graph.associations`.
- `Head.OpenDot`, drawn by `Heads` and by the TikZ writer.
- `Kind.Start(..., interrupting)` and `Kind.Intermediate(..., throwing)`.
- An Analytic-level "Ship an order" sample in `DiagramGallery`, and 14 `ActivitySpec` cases covering every new element and its edge cases.
- DSL.md, "Diagrams: ArchiMate drawn flat": documentation for every new element.

### Changed
- Throwing events and every end event draw their trigger filled. The `Error` and `Signal` glyphs are now closed shapes, so they can be filled.
- A node is placed under the node in its lane that it comes from, when it fits, which keeps straight runs straight.
- Placing nodes on rows and detecting loops now take compensation associations into account, alongside flows.

## Module Changes

- **`diagram`** (`genius-uml-diagram`): `Activity`, `Heads`, `Notation` (`Head`) and `Tikz`.
- No changes to `core`, `render`, `testkit`, `scene` or `examples`.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25
- Eclipse ELK 0.9.1
- zio-json 0.7.3
- PlantUML 1.2026.2
- os-lib 0.11.8, sourcecode 0.4.4
- Built with Mill using `fish.genius::mill-release` 0.5.0.
- Source compatible with 1.13.0, except for the pattern matches listed under Breaking Changes. Not binary compatible with 1.13.0 for `diagram`.
