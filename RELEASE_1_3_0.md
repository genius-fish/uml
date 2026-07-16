# Release 1.3.0

**Release Date:** 2026-07-16

This release makes the render pipeline trustworthy for automation: the live `PUmlEngine` now detects PlantUML syntax errors up front and fails with a typed `PUmlError.InvalidSource` instead of silently writing PlantUML's error page as a "successful" image. It also fixes the Archimate `Assignment` relationship, which emitted an invalid PlantUML arrow token that would have triggered exactly that failure mode.

On the layout side, Archimate diagrams can now opt into the Eclipse Layout Kernel (ELK) bundled with the PlantUML jar — usually a significant improvement for large diagrams — and a new `EnterpriseExample` showcases it with a multi-layer online-retail landscape. Release engineering and CI were hardened as well: publishing now happens before the git tag is pushed, so a failed publish leaves nothing public.

## Highlights

- **Fail-fast rendering** - PlantUML syntax errors now surface as a typed `PUmlError.InvalidSource` (with line numbers) instead of an error-page image
- **Opt-in ELK layout for Archimate** - `ArchimateConfiguration(elkLayout = true)` emits `!pragma layout elk` for tidier large-diagram layouts
- **Assignment relationship fixed** - the `Assignment` edge emitted invalid PlantUML (`@@-` / `->>`); it now renders correctly
- **EnterpriseExample** - a deliberately large multi-layer Archimate showcase exercising boundaries, stereotypes, legends and ~25 cross-layer relationships

## New Features

### Fail on PlantUML syntax errors

The live `PUmlEngine` now inspects the parsed PlantUML blocks before rendering. If PlantUML found a syntax error, rendering fails with the new `PUmlError.InvalidSource` case — including line positions and error details — rather than writing PlantUML's red error page to disk as if the render succeeded.

```scala
PUmlEngine
  .renderBytes(invalidDoc)
  .catchSome:
    case PUmlError.InvalidSource(details) =>
      ZIO.logError(s"Bad diagram source: $details")
```

This makes it safe to gate CI or document pipelines on render success.

### Opt-in ELK layout engine for Archimate diagrams

`ArchimateConfiguration` gains an `elkLayout` flag (default `false`). When enabled, the Archimate preamble emits `!pragma layout elk`, switching PlantUML from GraphViz/Smetana to the bundled Eclipse Layout Kernel. ELK usually produces tidier layouts for large Archimate diagrams, at the cost of ignoring some GraphViz-specific hints (including `Edge` direction hints).

```scala
given ArchimateConfiguration = ArchimateConfiguration(elkLayout = true)

val doc = block:
  uml:
    archimateDiagram:
      // shapes, boundaries, relationships ...
```

### EnterpriseExample

A new runnable showcase (`fish.genius.uml.examples.EnterpriseExample`) builds a fictional online-retail landscape spanning the motivation, business, application and technology layers, with nested boundaries, three custom stereotypes, a legend and ~25 cross-layer relationships. It renders with ELK by default; set `UML_LAYOUT=graphviz` to compare with the classic engine.

```sh
PLANTUML_AVAILABLE=1 ./mill examples.runMain fish.genius.uml.examples.EnterpriseExample
```

## Changelog

### Added
- `PUmlError.InvalidSource(message)` — typed error for PlantUML syntax failures, raised by the live engine before any image is written
- `ArchimateConfiguration.elkLayout` flag emitting `!pragma layout elk` in the Archimate preamble
- `EnterpriseExample` — large multi-layer Archimate showcase in the `examples` module
- GitHub Actions CI workflow running format check, scalafix, compile and the full test suite on pushes and PRs to `main`
- `make release-preflight` — checks repo.genius.fish for an already-published version before publishing

### Changed
- **Behavior change:** rendering source that PlantUML rejects now fails with `PUmlError.InvalidSource` instead of producing an error-page image; pipelines that relied on always getting an output file must handle this error
- Release flow is now atomic: `make release-finalize` tags locally, runs the remote pre-flight check, publishes, and only then pushes the tag — a failed publish leaves nothing public (recover with `make release-abort`)
- `mill-release` build plugin upgraded 0.4.0 → 0.5.0

### Fixed
- `RelationshipType.Assignment` emitted an invalid PlantUML arrow token (`@@-` prefix); corrected to `0-`, so Archimate assignment relationships (`0-->>`) render properly

## Module Changes

- **core** — `ArchimateConfiguration.elkLayout`, ELK pragma emission in `archimatePreamble`, `Assignment` relationship token fix
- **render** — new `PUmlError.InvalidSource`, syntax-error detection in the live `PUmlEngine`
- **examples** — new `EnterpriseExample`
- **testkit** — unchanged
- Build/CI — atomic release flow in `Makefile`, new `ci.yml` workflow, `mill-release` 0.5.0

## Compatibility

- Scala 3.8.3, built with Mill
- ZIO 2.1.25, zio-process 0.8.0 (render/testkit modules only; `core` remains dependency-free apart from sourcecode 0.4.4)
- PlantUML 1.2026.2, os-lib 0.11.8
- No public API removals; the only behavioral change is that invalid PlantUML source now fails the render instead of silently producing an error-page image
