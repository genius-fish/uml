# Release 1.2.3

**Release Date:** 2026-06-09

This release centres on making rendered diagrams land in a predictable, inspectable place on disk. The new `DiagramOutput` helper in the `render` module routes every rendered file to a stable `out/<format>/<category>/<name>.<suffix>` tree instead of dumping it into the current working directory, and both the test suites and the runnable examples now use it. Filesystem failures during the copy-out step are now surfaced as proper `PUmlError.Internal` values rather than escaping as raw exceptions.

The remainder of the release is documentation and build/release tooling: a complete DSL reference (`DSL.md`), an HTML reference manual, a `library-genius-uml` Claude Code skill with a sync workflow, adoption of `mill-release` for git-tag-driven versioning with `Makefile` release targets, and a full set of code-review reports under `reviews/`.

## Highlights

- **Stable diagram output layout** — Rendered diagrams are persisted under `out/<format>/<category>/<name>.<suffix>`, cleanly separating `test` from `examples` output.
- **`DiagramOutput` helper** — A new, hardened API in the `render` module that copies scoped-temp renders out to a durable location and maps I/O failures into the `PUmlError` channel.
- **Reference documentation** — New `DSL.md`, an HTML reference manual, and a structured `docs/skill/` reference set covering core, archimate, sequence, activity, render, and testing.
- **Release tooling** — Adopted `fish.genius::mill-release` for git-tag versioning plus `Makefile` release targets and a skill-doc sync pipeline.

## New Features

### `DiagramOutput`

The new `render` module object `DiagramOutput` provides a single place that decides where rendered diagrams live. The engine still renders into a scoped temp workspace that is deleted when the `Scope` closes; `DiagramOutput.save` copies the result out to a stable path that outlives the scope.

The layout is `<root>/out/<format>/<category>/<name><suffix>`, where `<root>` is `MILL_WORKSPACE_ROOT` (falling back to the current working directory), `<format>` is the file suffix without the leading dot (`svg`, `png`, `eps`, `tex`, ...), and `<category>` is either `DiagramOutput.Test` or `DiagramOutput.Examples`.

```scala
import fish.genius.uml.render.{DiagramOutput, PUmlEngine}
import net.sourceforge.plantuml.FileFormat

ZIO.scoped:
  for
    dest <- DiagramOutput.save(doc, DiagramOutput.Examples, "my-diagram", FileFormat.SVG)
    _    <- Console.printLine(s"written to $dest")
  yield ()
.provide(PUmlEngine.live)
```

Filesystem failures during the copy are captured and surfaced as `PUmlError.Internal` instead of leaking as untyped exceptions.

## Changelog

### Added
- `DiagramOutput` in the `render` module: `save`, `path`, `dir`, `formatDir`, `root`, and the `Test`/`Examples` category constants.
- `DSL.md` DSL reference and an HTML reference manual (`docs/genius-uml-reference-manual.html`).
- `library-genius-uml` Claude Code skill (`docs/skill/`) with reference pages for core, archimate, sequence, activity, render, and testing, plus a `scripts/sync-skill-docs.sh` sync script and a `sync-skill-docs.yml` workflow.
- `Makefile` release targets and adoption of `fish.genius::mill-release` for git-tag-derived versioning.
- A full set of code-review reports under `reviews/`.
- `CLAUDE.md` project guidance.

### Changed
- Examples now persist rendered output via `DiagramOutput.save` under `out/<format>/examples/` instead of copying into the current working directory.
- `README.md` documents the `out/<format>/<category>` diagram layout.
- Minor formatting cleanup in `SequenceExample.scala` and `Edge.scala` (scalafmt).

### Fixed
- `DiagramOutput.copyOut` creates parent directories and maps filesystem errors to `PUmlError.Internal`, so render-time I/O failures are reported through the typed error channel rather than thrown.

## Module Changes

- **`render`** — Added `DiagramOutput`; new integration/renderer test coverage for the output path.
- **`examples`** — `Renderer` and `SequenceExample` updated to use `DiagramOutput`.
- **`core`** — Whitespace-only change to `archimate/Edge.scala`.

## Compatibility

- **Scala:** 3.8.3
- **ZIO:** 2.1.25 (`zio-process` 0.8.0)
- **PlantUML:** 1.2026.2
- **os-lib:** 0.11.8
- **sourcecode:** 0.4.4

No breaking API changes. The `core` module remains free of ZIO, os-lib, and the PlantUML jar; all new functionality lives in `render`.
