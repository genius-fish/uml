# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`genius-uml` is a composable, type-safe PlantUML DSL and renderer for Scala 3, built with Mill.

## Commands

The `Makefile` wraps the common Mill tasks. `__` is Mill's wildcard for "all modules".

```sh
make compile          # ./mill __.compile
make test             # ./mill __.test.testForked   — run all tests
make fmt              # ./mill __.reformat           — scalafmt
make fix              # ./mill __.fix                — scalafix
make validate         # fix + fmt + compile + test (run before committing)
make publishLocal     # publish to ~/.ivy2/local
```

Working with a single module or test (Mill targets, not wrapped by the Makefile):

```sh
./mill render.test                         # run one module's tests
./mill render.test.compile                 # compile only one module's tests
./mill render.reformat                     # format a single module
./mill render.test.reformat                # format that module's test sources
./mill render.fix                          # scalafix a single module
```

ZIO Test has no per-test CLI filter here; narrow to a spec by running its module's
test suite (`./mill render.test`) and reading the named `+`/`-` lines in the output.

Running the examples (each is a `ZIOAppDefault`). Without env vars they only print
PlantUML source to stdout; `PLANTUML_AVAILABLE=1` also renders real files via the
bundled PlantUML jar:

```sh
PLANTUML_AVAILABLE=1 ./mill examples.runMain fish.genius.uml.examples.ArchimateExample
make examples        # renders every example as SVG (also: examples-eps/-png/-latex)
```

`PLANTUML_FORMAT=svg|eps|png|latex|latex_no_preamble` selects the output format.

## Architecture

The pipeline is **DSL → immutable `PUmlNode` AST → PlantUML `String` → rendered file**.

### Modules (`build.mill`)

- **`core`** — the DSL and AST. **Zero runtime dependencies**: no ZIO, no os-lib, no
  PlantUML jar (only `sourcecode`). Anything touching the filesystem, ZIO, or PlantUML
  belongs in `render`, not here. This boundary is the most important architectural
  constraint in the repo.
- **`render`** — depends on `core`. ZIO 2 service that turns the AST into SVG/PNG/EPS/
  LaTeX via the PlantUML jar.
- **`testkit`** — depends on `core`. Golden-file and structural assertions for ZIO Test.
- **`examples`** — depends on `core` + `render`. Runnable end-to-end showcases.

### DSL capture (the non-obvious part — `core/.../dsl/`)

The DSL is built on Scala 3 **context functions** plus a per-scope mutable builder, not a
global or a monad:

- `block(body)` allocates a fresh `PUmlCtx` (a mutable `ArrayBuffer[PUmlNode]` + a
  `Counters` for deterministic alias generation), supplies it as a `given`, runs the body,
  and collapses the buffer into one `PUmlNode`. Each `block` is an isolated scope, so
  concurrent renders are race-free by construction.
- Helpers like `shape(...)`, `relationship(...)`, `step(...)` take the `PUmlCtx` implicitly,
  build an AST node, and append it to the enclosing context's buffer.
- **Marker capabilities** (`InUml`, `InArchimate`, `InSequence`, `InActivity`, ...) are
  sealed traits introduced as `given`s by the wrapper functions (`uml`, `archimateDiagram`,
  `sequenceDiagram`, `activityDiagram`) and required via `using` on the helpers. This
  enforces nesting **at compile time** — e.g. calling `shape(...)` outside an
  `archimateDiagram { ... }` fails to compile ("no given instance of type InArchimate").

To add a new diagram dialect: define its marker, a wrapper that injects the marker `given`
and wraps the collected body in a `PUmlNode.Diagram(kind, ...)`, and helper functions that
emit AST nodes into the context.

### AST and Emit

- `PUmlNode` (`core/.../ast/`) is a small ADT: `Statement`, `Sequence`, `Block(header, body)`,
  `Diagram(kind, body)`, `Comment`.
- `Emit.emit(node): String` (`core/.../render/Emit.scala`) is a pure total fold over the AST
  that produces PlantUML source. **`Emit` lives in `core`**, so source generation needs no
  rendering dependencies; `render` calls it before handing the string to PlantUML.

### Render path (`render/.../`)

- `PUmlEngine` is a ZIO service with two layers: **`live`** (feeds `Emit.emit` output to
  PlantUML's `SourceStringReader`, writing into a scoped temp `Workspace` deleted when the
  `Scope` closes) and **`test`** (writes only the `.puml` source, skipping the PlantUML jar —
  used by unit tests that assert on source, not pixels).
- `PUmlConfig` carries the default format and optional workspace root. `Renderer` is a thin
  facade (`renderedAsSvg`, etc.).
- `DiagramOutput.save(...)` copies a render out of the scoped temp dir to a stable, gitignored
  location: **`out/<format>/<category>/<name>.<suffix>`** where `<format>` is `svg|png|eps|tex`
  and `<category>` is `test` (use `DiagramOutput.Test`) or `examples` (`DiagramOutput.Examples`).
  Root is `MILL_WORKSPACE_ROOT`, falling back to cwd. Tests and examples persist their diagrams
  here; the raw engine output is otherwise transient.

### Testkit

- `Golden.assertMatches(id, actual, resourcesDir)` compares emitted source against a checked-in
  fixture at `<module>/test/resources/golden/<id>.puml`. Set `ZIO_TEST_UPDATE_GOLDEN=1` to
  regenerate fixtures instead of failing. Workspace root resolves via `MILL_WORKSPACE_ROOT`.
- `PUmlAssertions` provides ZIO Test combinators and `PUmlNode` extension methods for
  structural assertions over the AST (no PlantUML invocation).

## Conventions

- **Formatting/linting are enforced.** `.scalafmt.conf` (maxColumn 100; 120 for `**/test/**`
  and `*Spec.scala`) and `.scalafix.conf` (`OrganizeImports`, `RemoveUnused`, etc.). New files
  often need `./mill <module>.reformat` after writing. Run `make validate` before committing.
- Scala 3 significant-indentation / new-syntax style (scalafmt rewrites enforce it); `package.scala`
  files are exempt from optional-brace removal.
- Versioning is derived from git tags in `build.mill` (`git describe`): a tagged commit publishes
  that version, otherwise `-SNAPSHOT`. Do not hardcode versions.
- Git commit trailer used in this repo: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
