# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

`genius-uml` draws ArchiMate views, sequences and processes for Scala 3: a `SceneGraph` laid out
by ELK and drawn natively as TikZ, SVG and PNG (Java2D), or in 3D for Blender. Built with Mill.

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
./mill diagram.test                        # run one module's tests
./mill diagram.test.compile                # compile only one module's tests
./mill diagram.reformat                    # format a single module
./mill diagram.test.reformat               # format that module's test sources
./mill diagram.fix                         # scalafix a single module
```

ZIO Test has no per-test CLI filter here; narrow to a spec by running its module's
test suite (`./mill diagram.test`) and reading the named `+`/`-` lines in the output.

Running the examples (each is a `ZIOAppDefault`):

```sh
./mill examples.runMain fish.genius.uml.examples.DiagramExample  # out/diagram/examples: .tex .svg .png
./mill examples.runMain fish.genius.uml.examples.SceneExample    # out/scene/examples: JSON + builder
make examples                                                     # both
```

## Architecture

The pipeline is **`SceneGraph` → ELK layout (`SceneLayout`) → geometry (`Prim`) → TikZ / SVG /
PNG**, and from the same layout **→ 3D `Scene` (JSON) → Blender / three.js**. Nothing shells out
to a renderer; the only external program is Blender, for the 3D stills and videos.

### Modules (`build.mill`)

- **`core`** — the ArchiMate vocabulary (`fish.genius.uml.dsl.archimate`): `ShapeType`,
  `ShapeGroup`, `RelationshipType`, `ShapeLabel`, `ArchimateConfiguration`. **Zero
  dependencies.** The scene and the diagram match on its enums exhaustively, so a new case
  does not compile until both draw it. townplanner builds against these types (it passes
  `ArchimateConfiguration(elkLayout = true)`, which no longer does anything): change their
  shape only in a major release.
- **`scene`** — depends on `core` (for `ShapeType` and `RelationshipType`), ELK and zio-json.
  Lays an ArchiMate `SceneGraph` out as a 3D `Scene` (`SceneLayout`) and ships the Blender
  builder and three.js shapes as resources (`SceneBuilder`). Its own rules are in DSL.md,
  "Scenes: ArchiMate in 3D". The builder in `scene/resources/` and the layout's JSON are one
  contract: a field renamed in `Scene.scala` is renamed in `archimate3d_blender.py`.
  `SceneContractSpec` pins it, and checks the builder's options against DSL.md (handed in
  by `scene.test.forkEnv`). A passport's pane is sized twice from one table: `PaneLayout`
  (the layout keeps floor for it) and the builder's `PANE` (it lays the pane out); the
  spec holds the two equal, and the builder prints how far a pane it built is from the
  layout's height. `SceneBlenderSpec` runs the builder headless and is ignored
  unless `BLENDER_AVAILABLE=1` (`BLENDER=…` names the binary): run it after any change to
  the builder, `BLENDER_AVAILABLE=1 make test`. It needs a Blender that runs EEVEE headless
  (5.x). The builder measures what it builds (connector ends on ports, label overlap, a
  flight's framing and route gap) and prints it; the spec asserts those lines, so a visual
  claim gets a printed measure before it gets a test.
- **`diagram`** — depends on `scene`. Draws a `SceneGraph` as a flat ArchiMate diagram with the
  scene's ELK layout (the four flat-diagram options of `SceneLayout.Options`), boxes sized to
  what they say in the diagram's font, and the notation as plain geometry (`Prim`), written by
  `Tikz`, `Svg` and `Png` (Java2D). `Sequence` and `Activity` draw sequences and BPMN
  processes in the same look, as the same `Diagram.Drawing`. DSL.md, "Diagrams: ArchiMate drawn flat". Set
  `DIAGRAM_OUT=dir` when running `DiagramSpec` to get the sample as `.tex`, `.svg` and `.png`.
- **`testkit`** — no module deps. `Golden.assertMatches(id, actual, resourcesDir, extension)`
  compares a rendered string against `<module>/test/resources/golden/<id>.<extension>`
  (default `txt`); `ZIO_TEST_UPDATE_GOLDEN=1` regenerates the fixtures instead of failing.
  Workspace root resolves via `MILL_WORKSPACE_ROOT`.
- **`examples`** — depends on `core`, `scene` and `diagram`. `DiagramExample` and
  `SceneExample`, runnable end-to-end; output goes to the gitignored `out/`.

## Jira

Work on this repo is tracked in the team-managed Jira project **UML** on the `geniusfish` site
(https://geniusfish.atlassian.net/browse/UML). Search there before creating a ticket, put the
key in every commit message, and use fude's Jira tools to write to it. `.gf-standards.json`
points the review and architecture-plan tooling at the same project.

## Conventions

- **Formatting/linting are enforced.** `.scalafmt.conf` (maxColumn 100; 120 for `**/test/**`
  and `*Spec.scala`) and `.scalafix.conf` (`OrganizeImports`, `RemoveUnused`, etc.). New files
  often need `./mill <module>.reformat` after writing. Run `make validate` before committing.
- Scala 3 significant-indentation / new-syntax style (scalafmt rewrites enforce it); `package.scala`
  files are exempt from optional-brace removal.
- Versioning is derived from git tags in `build.mill` (`git describe`): a tagged commit publishes
  that version, otherwise `-SNAPSHOT`. Do not hardcode versions.
- Git commit trailer used in this repo: `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`.
