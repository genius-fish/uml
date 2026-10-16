# Release 2.0.0

**Release Date:** 2026-10-16

genius-uml 2.0.0 drops the PlantUML path. The library now has one pipeline: a `SceneGraph` is laid out by ELK and drawn natively as TikZ, SVG and PNG (Java2D) by the `diagram` module, or as a 3D scene for Blender and three.js by the `scene` module. Nothing shells out to PlantUML any more. The `genius-uml-render` artifact is gone, along with the PlantUML DSL and AST in `core` and the PlantUML assertions in `testkit`.

This is a major release because those removals break the public API. The ArchiMate vocabulary that townplanner builds against stays: `ShapeType`, `ShapeGroup`, `RelationshipType`, `ShapeLabel` and `ArchimateConfiguration`. They lost only their PlantUML-only members. `core` now has no dependencies at all.

## Highlights

- **One rendering pipeline**: `SceneGraph` → ELK layout → TikZ / SVG / PNG, and 3D from the same layout. PlantUML is no longer used anywhere.
- **`genius-uml-render` removed**: `PUmlEngine`, `Renderer` and their PlantUML and zio-process dependencies are gone.
- **`core` has zero dependencies**: it is now only the ArchiMate vocabulary, and `sourcecode` is no longer pulled in.
- **New `DiagramExample`**: one example writes a flat ArchiMate view as `.tex`, `.svg` and `.png`.
- **Safer `make release-prepare`**: it stages nothing, and it stops with a readable message when the tree is dirty.

## New Features

### DiagramExample: a flat ArchiMate view end to end

`DiagramExample` replaces the PlantUML showcases (`ArchimateExample`, `ActivityExample`, `SequenceExample`, `EnterpriseExample`). It builds a `SceneGraph`, draws it with `Diagram.draw`, and writes all three formats to `out/diagram/examples`:

```scala
import fish.genius.uml.diagram.*
import fish.genius.uml.scene.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.dsl.archimate.RelationshipType.*

val graph = SceneGraph(
  "Order handling",
  List(
    SceneNode(SceneId("customer"), NodeKind.Element(BusinessActor), "Customer", Nil),
    SceneNode(SceneId("handle"), NodeKind.Element(BusinessProcess), "Handle Order", Nil),
  ),
  List(
    SceneEdge(SceneId("customer>handle"), SceneId("customer"), SceneId("handle"), Assignment, None)
  ),
)

for
  drawing <- ZIO.fromEither(Diagram.draw(graph))
  _ <- ZIO.attemptBlocking:
    Files.writeString(out.resolve("order-handling.tex"), Tikz.standalone(drawing))
    Files.writeString(out.resolve("order-handling.svg"), Svg.document(drawing))
    Files.write(out.resolve("order-handling.png"), Png.image(drawing))
yield ()
```

```sh
./mill examples.runMain fish.genius.uml.examples.DiagramExample
make examples   # runs DiagramExample and SceneExample
```

## Breaking Changes

- **The `render` module (`genius-uml-render`) is removed.** That includes `PUmlEngine`, `PUmlConfig`, `Renderer`, `PUmlError`, `PUmlViewer`, `DiagramOutput` and `Workspace`. Use `Tikz`, `Svg` and `Png` from `genius-uml-diagram` instead.
- **The PlantUML DSL and AST are removed from `core`.** That includes:
  - `PUmlNode`, `PUmlCtx`, `Emit`, `Alias`, `Counters`;
  - the document, builder, marker and primitive helpers;
  - skinparams and sprites (`skin`);
  - the `archimate`, `sequence` and `activity` DSL helpers;
  - `Edge`.
- **The ArchiMate types keep their cases but lose their PlantUML members:**
  - `RelationshipType` no longer carries `prefix` / `suffix` connector tokens.
  - `ShapeGroup` no longer carries `defaultColor` / `prefix`.
  - `ShapeType` no longer has `icon` or `decorate`.
  - `ShapeLabel` no longer has `tags`, `output` or a markup `toString`. It now exposes the configuration it was built under as `config`.
  - `ShapeStereoType` and `ShapeColor` are removed.
- **`ArchimateConfiguration.elkLayout` now does nothing.** Every layout is ELK's. The field stays so that existing callers, such as `ArchimateConfiguration(elkLayout = true)`, keep compiling. It will be removed in a later major release.
- **`PUmlAssertions` is removed from `testkit`.** `testkit` no longer depends on `core`.
- **`Golden.assertMatches` now defaults to the `txt` extension** instead of `puml`. Pass `extension = "puml"` explicitly to keep existing fixtures.
- **The `make` targets `examples-eps`, `examples-png` and `examples-latex` are removed**, together with the `PLANTUML_AVAILABLE` and `PLANTUML_FORMAT` switches.

## Changelog

### Added
- `DiagramExample`, which writes a flat ArchiMate diagram as `.tex`, `.svg` and `.png`.
- `ShapeLabel.config`, which exposes the `ArchimateConfiguration` a label was built under.
- New skill reference pages `docs/skill/reference/diagram.md` and `docs/skill/reference/scene.md`.
- New spec cases in `ArchimateTypesSpec`: an element's name is unique within its layer, and a `ShapeLabel` keeps its configuration.

### Changed
- The `pomSettings` description now reads "ArchiMate diagrams for Scala 3: an ELK layout drawn as TikZ, SVG and PNG".
- `core` is now only the ArchiMate vocabulary and has no dependencies.
- The default golden-file extension in `testkit` changed from `puml` to `txt`.
- `make examples` now runs `DiagramExample` and `SceneExample`.
- `make release-prepare` now refuses a dirty tree before it starts. If `validate` reformats files, it stops and lists them instead of staging them silently with `git add -u`. Before, a reformat ended the release with "Uncommitted changes detected" (GFSDK-2).
- README, CLAUDE.md, DSL.md and `docs/skill` (`SKILL.md`, `core.md`, `testing.md`) now describe the SceneGraph → ELK → TikZ/SVG/PNG pipeline.

### Removed
- The `render` module and its dependencies: `net.sourceforge.plantuml:plantuml` 1.2026.2, `dev.zio::zio-process` 0.8.0 and the module's `os-lib` dependency.
- The `sourcecode` dependency of `core`.
- The PlantUML DSL, AST and emitter from `core`.
- `PUmlAssertions` from `testkit`.
- The PlantUML examples: `ArchimateExample`, `ActivityExample`, `SequenceExample`, `EnterpriseExample` and the `Renderer` helper.
- The HTML reference manual (`docs/genius-uml-reference-manual.html`) and the skill references `activity.md`, `archimate.md`, `render.md` and `sequence.md`.

### Fixed
- Core's label test now checks what the library promises (name uniqueness, the label's configuration) instead of the case class's `toString`.

## Module Changes

| Module | Artifact | Change |
|---|---|---|
| `core` | `genius-uml-core` | Shrunk to the ArchiMate vocabulary; PlantUML DSL/AST removed; no dependencies |
| `render` | `genius-uml-render` | **Removed** |
| `testkit` | `genius-uml-testkit` | `PUmlAssertions` removed; no longer depends on `core`; golden default `txt` |
| `scene` | `genius-uml-scene` | No code change |
| `diagram` | `genius-uml-diagram` | No code change; now the only flat renderer (TikZ, SVG, PNG) |
| `examples` | `genius-uml-examples` (not published) | Now depends on `core`, `scene` and `diagram`; PlantUML examples replaced by `DiagramExample` |

## Compatibility

- **Scala:** 3.8.3
- **ZIO:** 2.1.25
- **zio-json:** 0.7.3 (in `scene`)
- **Eclipse ELK:** 0.12.0, with `org.eclipse.xtext.xbase.lib` 2.36.0 (in `scene`)
- **os-lib:** 0.11.8 (in `testkit`)
- **Build:** Mill, with `fish.genius::mill-release` 0.5.0 and `mill-scalafix` 0.6.0
- **Migration:** code that only uses `ShapeType`, `ShapeGroup`, `RelationshipType`, `ShapeLabel` and `ArchimateConfiguration`, as townplanner does, compiles unchanged. Exceptions are code that reads the removed PlantUML members or `ShapeLabel.tags`. Code on `genius-uml-render` or the core DSL moves to `SceneGraph` plus `Diagram.draw` with `Tikz`, `Svg` or `Png`.
