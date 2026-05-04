# Release 1.2.0

**Release Date:** 2026-05-04

This release is a ground-up rewrite of `genius-uml`. The original sbt /
Scala 2.13 single-module project (preserved on the `archive/sbt-original`
branch) is replaced with a Mill 1.1.5 / Scala 3.8.3 / ZIO 2.1.25 multi-module
build, and the runtime API moves from a mutable OO `Specification` builder
to a typed, composable, context-function-driven DSL whose structural
constraints (`InUml`, `InArchimate`, `InSequence`, `InActivity`, `InBoundary`,
`InBox`, `InGroup`) are checked by the compiler. Every diagram type the
previous version supported — Archimate, sequence, activity — is covered, the
Archimate surface adds the full set of stereotype, legend, and SVG-sprite
helpers, and the renderer is now a ZIO 2 service with both a live PlantUML
engine and a test engine.

Because the public API is entirely new, callers on `0.x` / `1.1.0` cannot
upgrade in place; the old code remains available on
`archive/sbt-original`.

## Highlights

- **Mill / Scala 3 / ZIO 2 build** with four modules (`core`, `render`,
  `testkit`, `examples`) replacing the previous single sbt module.
- **Context-function DSL** with compile-time-checked structural markers —
  e.g. calling `shape(...)` outside an `archimateDiagram { ... }` block, or
  `step(...)` outside a `sequenceDiagram { ... }` block, is a compile error.
- **Pure AST + total fold renderer** — diagrams are values of type
  `PUmlNode` produced by `block { ... }`; `Emit.emit` is a pure
  `PUmlNode → String` function with no IO.
- **ZIO `PUmlEngine` service** with a `live` layer (PlantUML
  `SourceStringReader` to SVG / PNG / EPS / LaTeX) and a `test` layer that
  captures `.puml` source without invoking the renderer.
- **Diagram-as-data testkit** — `PUmlAssertions` lets tests assert on the
  AST directly (`countStatement`, `hasBlock`, `statementsContaining`,
  `diagramKinds`); `Golden.assertMatches` provides `ZIO_TEST_UPDATE_GOLDEN`-
  driven golden-file matching.
- **Make targets for every output format** — `make examples`,
  `examples-eps`, `examples-png`, `examples-latex` run the same example
  apps and emit the chosen format.

## New Features

### Context-function PlantUML DSL

A `block { uml { ... } }` body composes statements into a `PUmlNode` tree.
Domain DSLs (`archimateDiagram`, `sequenceDiagram`, `activityDiagram`)
require an `InUml` capability that is only in scope inside a `uml { ... }`
envelope; element-level helpers in turn require their domain's marker:

```scala
import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.archimate.*
import fish.genius.uml.dsl.archimate.ShapeType.*

given ArchimateConfiguration = ArchimateConfiguration()

val doc = block:
  uml:
    archimateDiagram:
      val customer = shape(BusinessActor, label("Customer"))
      val store    = shape(ApplicationComponent, label("Web store"))
      relationship(RelationshipType.Serving)(store)(customer)
```

The same pattern covers all three diagram families:

- `sequenceDiagram` — `actor`, `participant`, `boundary`, `control`,
  `entity`, `database`, `collections`, `queue` (each accepts an optional
  colour); `step`, `autonumber`, `teozRenderingEngine`; control structures
  `altGroup`, `elseGroup`, `loopGroup`, `optGroup`, `parGroup`,
  `breakGroup`, `criticalGroup`, `group`, `endGroup`; and a `box` helper
  bracketed by `box "..." color` ... `end box`.
- `activityDiagram` — `title`, `start`, `action`, `stop`, `end`, `note`.
- `archimateDiagram` — every Archimate 3 element across the seven layers
  (Application, Strategy, Business, Technology, Physical, Motivation,
  Implementation), all 15 relationship types (`Composition`,
  `Aggregation`, `Assignment`, `Serving`, `Flow`, `Specialization`,
  `Association`, `DirectedAssociation`, `Realization`, `Triggering`,
  `Access`, `ReadAccess`, `WriteAccess`, `ReadWriteAccess`, `Influence`)
  and five edge directions (`NoDirection`, `Up`, `Down`, `Left`, `Right`),
  plus `boundary`, `defineStereoType`, `legend`, `archimateSkinParam`, and
  `archimatePreamble` helpers.

### ZIO 2 render service

`PUmlEngine` is a ZIO service whose `live` layer wraps PlantUML's
`SourceStringReader.outputImage` and renders into a scope-managed temp
workspace. Every supported PlantUML output format is reachable from the
`Renderer` facade:

```scala
import fish.genius.uml.render.{PUmlEngine, Renderer}
import zio.*

val program = ZIO.scoped:
  Renderer.svg(doc)         // also: .png, .eps, .latex, .latexFull
program.provide(PUmlEngine.live)
```

Extension methods are also available directly on `PUmlNode`:
`doc.renderedAsSvg()`, `doc.renderedAsEps()`, `doc.renderedAsPng()`,
`doc.renderedAsLatex()`. `PUmlEngine.test` writes a `.puml` source file
without invoking the renderer, so unit tests can run on minimal
classpaths.

### Structural test assertions and golden-file matching

```scala
import fish.genius.uml.testkit.PUmlAssertions.*
import fish.genius.uml.testkit.Golden

assertTrue(
  doc.countStatement("Alice") == 2,
  doc.hasBlock("rectangle"),
  doc.diagramKinds == List("uml"),
)

Golden.assertMatches(
  id           = "archimate-minimal",
  actual       = Emit.emit(doc),
  resourcesDir = Golden.moduleResources("core"),
  extension    = "puml",
)
```

`Golden.shouldUpdate` reads `ZIO_TEST_UPDATE_GOLDEN` so a test run with the
env var set regenerates fixtures instead of failing the assertion.

### Multi-format example runner

The `examples` module ships three runnable `ZIOAppDefault` apps —
`ArchimateExample`, `SequenceExample`, `ActivityExample` — that print the
PlantUML source unconditionally and, when `PLANTUML_AVAILABLE=1`, also
render through the live engine. The output format is selected by
`PLANTUML_FORMAT` (`svg` default, plus `eps`, `png`, `latex`,
`latex_no_preamble`). The Makefile exposes `examples`, `examples-eps`,
`examples-png`, `examples-latex` targets that loop the example list with
the right format set.

## Breaking Changes

The entire public API is replaced. Callers on `1.1.0` cannot upgrade
without rewriting their diagram code:

- The OO entry point `new Specification { uml { archimate { archi => ... }
  } }` is gone. Replace with `block { uml { archimateDiagram { ... } } }`
  using top-level helpers from `fish.genius.uml.dsl.*` and
  `fish.genius.uml.dsl.archimate.*`.
- Domain entry-point functions are renamed to avoid colliding with their
  containing sub-packages: `archimate` → `archimateDiagram`, `sequence` →
  `sequenceDiagram`, `activity` → `activityDiagram`.
- The Maven artifact ids change. Where `1.1.0` shipped a single
  `fish.genius:uml`, `1.2.0` ships
  `fish.genius:genius-uml-core`, `genius-uml-render`,
  `genius-uml-testkit`, and `genius-uml-examples`.
- The render service has moved to ZIO 2 (`UmlService` /
  `Renderer.{svg, eps, latex, ...}` / `UmlError` →
  `PUmlEngine` / `Renderer.{svg, png, eps, latex, latexFull}` /
  `PUmlError`); previews now go through `PUmlViewer.open(os.Path)` and
  `os-lib` paths replace `java.io.File`.
- The minimum Scala version is now 3.8.3; 2.13 is no longer supported.

The original `1.1.0` codebase is preserved on the `archive/sbt-original`
branch for projects that need to keep using it.

## Changelog

### Added

- Mill 1.1.5 multi-module build (`core`, `render`, `testkit`, `examples`).
- `PUmlNode` AST + total `Emit.emit` fold rendering to PlantUML source.
- Context-function DSL with marker traits (`InUml`, `InArchimate`,
  `InBoundary`, `InSequence`, `InBox`, `InGroup`, `InActivity`).
- `Alias` opaque type with sanitisation and a per-block `Counters`
  generator that produces globally-unique aliases across nested scopes.
- Full Archimate surface (`shape`, `boundary`, `relationship`,
  `defineStereoType`, `legend`, `archimateSkinParam`, `label`, `color`,
  `archimatePreamble`).
- Sequence-diagram surface (eight participant types, `box`, control-flow
  groups, `step`, `autonumber`, `teozRenderingEngine`).
- Activity-diagram surface (`title`, `start`, `action`, `stop`, `end`,
  `note`).
- `SkinParamProperty` enum + `skinParam` helper, and `svgIcon` for inline
  SVG sprites.
- `PUmlEngine` ZIO service with `live` (PlantUML 1.2026.2) and `test`
  layers, plus the `PUmlConfig`, `Workspace`, `PUmlError` model.
- `Renderer` facade exposing `svg`, `png`, `eps`, `latex`, `latexFull`,
  and `render(format)`, with `renderedAs*` extension methods on
  `PUmlNode`.
- `PUmlViewer.open` for OS-default-viewer preview.
- `testkit` module with `Golden` (env-var-driven golden file matcher) and
  `PUmlAssertions` (structural matchers on `PUmlNode`).
- `examples` module with `ArchimateExample`, `SequenceExample`,
  `ActivityExample` ZIO apps and a shared `Renderer.run` that respects
  `PLANTUML_AVAILABLE` and `PLANTUML_FORMAT`.
- Makefile targets `examples`, `examples-eps`, `examples-png`,
  `examples-latex`, `examples-clean`, plus `compile`, `fmt`, `fix`, `test`,
  `validate`, `publishLocal`.
- Live integration tests for SVG and EPS rendering against the bundled
  PlantUML jar (no external binaries required).

### Changed

- Build moved from sbt + Scala 2.13.10 + PlantUML 1.2023.1 to Mill 1.1.5
  + Scala 3.8.3 + PlantUML 1.2026.2.
- `box` (sequence) is bracketed by `box "..." color` ... `end box` (no
  braces) and now returns the body's value so participant aliases declared
  inside the box remain available afterwards.
- Auto-generated aliases are unique across nested scopes — `PUmlCtx`'s
  `Counters` is shared between a parent context and any `childBlock` it
  spawns.

### Fixed

- Sequence-diagram `box` helper no longer emits a brace-delimited body,
  which caused PlantUML 1.2025.x and 1.2026.x to refuse the source with a
  syntax error.

## Module Changes

All four modules are new in this release:

- `core` — pure DSL + AST + emitter, depends only on `sourcecode`.
- `render` — ZIO 2 PlantUML engine, depends on `core`, `zio`,
  `zio-process`, `os-lib`, `net.sourceforge.plantuml:plantuml:1.2026.2`.
- `testkit` — `Golden` + `PUmlAssertions`, depends on `core`, `zio`,
  `zio-test`, `os-lib`.
- `examples` — runnable showcases, depends on `core` and `render`.

`core/test`, `render/test`, and `testkit/test` together ship 50 tests
across 11 spec classes (`Emit`, `Specification`, `primitives`, `skin`,
`activity`, `sequence`, `Archimate`, `Renderer`, live `Live PlantUML
rendering`, `Golden`, `PUmlAssertions`).

## Compatibility

Built against:

- Mill 1.1.5
- Scala 3.8.3
- ZIO 2.1.25
- ZIO Process 0.8.0
- os-lib 0.11.8
- sourcecode 0.4.4
- PlantUML 1.2026.2

Scala 2.x is no longer supported. Maven coordinates are
`fish.genius:genius-uml-{core,render,testkit}:1.2.0`; the `examples`
module is intentionally not published.
