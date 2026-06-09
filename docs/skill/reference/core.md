# core — DSL foundations, AST, markers

Package `fish.genius.uml.dsl` unless noted. Everything builds a `PUmlNode`;
nothing here touches IO.

## The AST and the renderer

`PUmlNode` (in `fish.genius.uml.ast`) is the total enum every helper emits:

| Case | Renders to |
| --- | --- |
| `Statement(value: String)` | a single literal PlantUML line, verbatim |
| `Sequence(children: Vector[PUmlNode])` | a flat run of siblings |
| `Block(header: String, body: PUmlNode)` | `header { body }` |
| `Diagram(kind: String, body: PUmlNode)` | `@start<kind> body @end<kind>` |
| `Comment(text: String)` | `' text` on its own line |

`PUmlNode.empty` is the empty sequence. `fish.genius.uml.render.Emit.emit(node):
String` is the only renderer — a pure, total tree fold.

## Entry points

| Signature | Summary |
| --- | --- |
| `block(body: PUml[Unit]): PUmlNode` | run a body in a fresh scope; return the AST (the user-facing entry point) |
| `uml(body: (PUmlCtx, InUml) ?=> Unit): PUml[Unit]` | the `@startuml … @enduml` envelope; introduces `InUml` |
| `diagram(kind: String)(body: (PUmlCtx, InUml) ?=> Unit): PUml[Unit]` | `@start<kind> … @end<kind>` for non-UML kinds (gantt, mindmap, wbs, …) |

`PUmlCtx` is the scope buffer (carries `counters: Counters` for deterministic
alias allocation). `type PUml[A] = PUmlCtx ?=> A` is "DSL code that emits into the
ambient context". Helpers receive the context via `using`; user code never
constructs it.

## Marker capabilities

Sealed traits (in `object markers`) with package-private singletons — user code
cannot fabricate one. A producer binds the marker in the body it opens; consumers
demand it via `using`.

| Marker | Introduced by | Required by |
| --- | --- | --- |
| `InUml` | `uml { }` / `diagram(kind) { }` | every domain DSL helper |
| `InArchimate` | `archimateDiagram { }` | `shape`, `boundary`, `relationship`, … |
| `InBoundary` | archimate `boundary { }` | nested boundaries |
| `InSequence` | `sequenceDiagram { }` | participants, `step`, control groups |
| `InBox` | sequence `box { }` | participant declarations inside a box |
| `InGroup` | a sequence `alt`/`opt`/`loop`/… | internal group nesting |
| `InActivity` | `activityDiagram { }` | `action`, `start`, `stop`, `note`, … |

## Primitives

| Signature | Emits |
| --- | --- |
| `statement(value: String): PUml[Unit]` | a single literal line |
| `statements(text: String): PUml[Unit]` | one `Statement` per non-empty line |
| `comment(text: String): PUml[Unit]` | `' comment` |
| `expression(build: Alias => String): PUml[Alias]` | a header line built from a fresh alias; returns it |
| `expressionWithBody(build: Alias => String)(body): PUml[Alias]` | a `header { body }` block bound to a fresh alias |
| `headerBlock(header: String)(body): PUml[Unit]` | `header { body }` without an alias |
| `when(cond: Boolean)(body): PUml[Unit]` | conditionally evaluate the body |
| `emit(node: PUmlNode)(using PUmlCtx): Unit` | low-level: append an AST node |

## Alias

`Alias` is an opaque `String` — a PlantUML-safe identifier. You rarely build one:
domain helpers allocate and **return** aliases (`val a = shape(…)`), and you pass
those to `relationship` / `step`.

- `Alias(raw: String)` — sanitise an arbitrary string into a lower-case alias.
- `Alias.unsafe(value)` — trust an already-safe string.
- `Alias.fresh(using PUmlCtx)` — allocate a unique `a<index>` from the counter.
- `.value` — extract the underlying string.

## Skinparams & SVG icons (`dsl/skin.scala`)

`skinParam(stereotype: String, properties: SkinParamProperty*)(using PUmlCtx,
InUml)` emits a `skinparam <stereotype> { … }` block (no-op if no properties).

The `SkinParamProperty` enum covers the common typed knobs: `FontColor`,
`BorderColor`, `BackgroundColor`, `Color`, `FontSize(pt)`, `Shadowing(bool)`,
`StereotypeFontSize(pt)`, `BorderDashed/Dotted/Plain/Bold`,
`StereoTypeAlignmentLeft/Right/Center`, `RoundCorner(r)`, `DiagonalCorner(r)`,
`BorderThickness(pt)`, `EntrySeparatorColor(hex)`, and the escape hatch
`SkinProp(name, value)`. Each has `.name`, `.value`, `.line`.

`svgIcon(svg: String, name=None, description=None)(using PUmlCtx, InUml):
SvgIcon` registers an SVG sprite and returns a handle (`SvgIcon(name: Alias,
description: Option[String])`) you can attach to ArchiMate labels/legends.
