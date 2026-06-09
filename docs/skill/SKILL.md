---
name: library-genius-uml
description: How to use genius-uml — Genius Fish's composable, type-safe PlantUML DSL and renderer for Scala 3. Apply when writing or reviewing Scala code that builds PlantUML diagrams (ArchiMate, sequence, activity) with the fish.genius.uml.* API (block/uml, archimateDiagram, sequenceDiagram, activityDiagram, PUmlEngine.renderSvg). Covers the module layout, the marker/AST model, every diagram DSL, the rendering pipeline, error conventions, and testing.
---

# genius-uml

A composable, type-safe PlantUML DSL and renderer for Scala 3. You build diagrams
by calling DSL helpers that emit a typed AST (`PUmlNode`); a pure renderer folds
that tree to PlantUML source, and a ZIO service drives the bundled PlantUML
engine to produce SVG/PNG/EPS/LaTeX.

Artifacts publish under `fish.genius` (e.g. `fish.genius::genius-uml-core`).
Source: <https://github.com/genius-fish/uml>. Stack: Scala 3.8.3, ZIO 2.1.25,
PlantUML 1.2025.2 (the jar ships with the `render` module).

## When to use this skill

Read this whenever you write or review Scala that imports `fish.genius.uml.*` —
building an ArchiMate, sequence, or activity diagram, choosing a render format,
or testing diagram output. It explains the conventions a casual reading might miss.

## The three guarantees (design philosophy)

Identical in spirit to its sibling `genius-latex`:

1. **Type safety via marker capabilities.** Sealed traits — `InUml`,
   `InArchimate`, `InSequence`, `InActivity`, `InBoundary`, `InBox`, `InGroup` —
   are introduced by producers and demanded by consumers through `using` clauses.
   Calling `shape(…)` outside `archimateDiagram { }`, or any domain helper outside
   `uml { }`, is a *compile error*.
2. **Escaping/sanitisation by construction.** PlantUML identifiers flow through
   the `Alias` opaque type, which sanitises arbitrary strings into safe aliases.
   Aliases are usually allocated for you (helpers return them) — you rarely build
   one by hand.
3. **Purity at the core.** `core` touches no filesystem and no subprocess —
   building a diagram is a pure `code → AST` function. Only `render` does IO,
   through ZIO with a typed `PUmlError` channel.

## The core mental model

- **`block(body): PUmlNode`** is the outermost entry point — runs `body` in a
  fresh, isolated `PUmlCtx` (a scope buffer + alias counters) and returns the AST.
- **`type PUml[A] = PUmlCtx ?=> A`** — DSL code that emits into the ambient
  context; the context and markers thread automatically through nested bodies.
- **`uml { … }`** wraps the body in `@startuml … @enduml` and introduces `InUml`.
  Inside it you open exactly one domain DSL: `archimateDiagram`, `sequenceDiagram`,
  or `activityDiagram`. (`diagram(kind) { … }` is the escape hatch for other
  PlantUML kinds — gantt, mindmap, wbs, …)
- **`PUmlNode`** is a small total enum: `Statement`, `Sequence`, `Block(header,
  body)`, `Diagram(kind, body)`, `Comment`. Every helper emits one.
- **`Emit.emit(node): String`** is the only renderer — pure, instant, no IO. Your
  fast feedback loop: inspect/test against the string; only call `PUmlEngine` when
  you need an actual image.

```scala
import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.archimate.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.render.{Emit, PUmlEngine}
import zio.*

given ArchimateConfiguration = ArchimateConfiguration()

val diagram = block:
  uml:
    archimateDiagram:
      val customer = shape(BusinessActor, label("Customer"))
      val store    = shape(ApplicationComponent, label("Web Store"))
      relationship(RelationshipType.Serving)(store)(customer)

println(Emit.emit(diagram))                          // pure String, no PlantUML
ZIO.scoped(PUmlEngine.renderSvg(diagram))            // real SVG
   .provide(PUmlEngine.live)
```

## Modules

| Module    | Depends on | Purpose |
| --------- | ---------- | ------- |
| `core`    | —          | Pure DSL + AST. No ZIO, no filesystem. |
| `render`  | `core`     | ZIO 2 service: AST → SVG/PNG/EPS/LaTeX via PlantUML. |
| `testkit` | `core`     | Golden-file & structural ZIO Test assertions. |
| `examples`| `core, render` | Runnable end-to-end showcases (archimate, sequence, activity). |

## Reference index — read the file that matches the task

- **`reference/core.md`** — the `PUmlNode` AST, `block`/`uml`/`diagram`,
  `PUmlCtx`/`PUml`, the marker system, primitives, `Alias`, skinparams & SVG icons.
- **`reference/archimate.md`** — `archimateDiagram`, `shape`, `relationship`,
  `boundary`, stereotypes, legends, and the `ShapeType` / `RelationshipType` /
  `ShapeGroup` / `Edge` enums.
- **`reference/sequence.md`** — `sequenceDiagram`, participants, `box`, `step`,
  the control groups (`alt`/`opt`/`loop`/`par`/…), autonumber, Teoz.
- **`reference/activity.md`** — `activityDiagram` (PlantUML Activity Beta):
  `start`/`stop`/`end`, `action`, `title`, `note`.
- **`reference/render.md`** — `PUmlEngine` & its layers, `PUmlConfig`,
  `DiagramOutput` persistence, `PUmlViewer`, the `Renderer` facade, and the
  `PUmlError` conventions.
- **`reference/testing.md`** — structural assertions, golden files, and rendering
  in tests without PlantUML.

## Top gotchas

- Develop against `Emit.emit(doc)` + `testkit` assertions — milliseconds, no jar.
- Helpers **return the `Alias`** they declare (`val a = shape(…)`); pass those
  aliases to `relationship`/`step`, don't hand-build identifiers.
- `relationship(type, edge, label)` and `step(title)` are **curried** —
  `relationship(Serving)(source)(target)`, `step("msg")(from)(to)`.
- ArchiMate needs a `given ArchimateConfiguration` in scope.
- `PUmlEngine.render*` paths live in a `Scope`-managed temp dir (deleted on close)
  — use `DiagramOutput.save(…)` to persist to `out/<format>/<category>/<name>`.
