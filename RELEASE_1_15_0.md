# Release 1.15.0

**Release Date:** 2026-10-15

The native `diagram` module can now draw three things that only the PlantUML path could draw before: badges on a box, an explicit fill colour per node, and a legend under the diagram. A leaf node can carry a row of small glyph badges at its foot, and the box grows to hold them. A node can be given a hex `fill` that draws it as a layer of its own. A `SceneGraph` can carry a `Legend` that explains what the badges mean. The SVG, PNG and TikZ writers all render these three features the same way.

The `scene` module moves to ELK 0.12.0 and now runs layouts one at a time. ELK's compaction shared state between layouts running concurrently, so the same graph could come out differently under concurrency. Layouts are now serialised and each gets a fresh engine, so a flat graph gives the same scene however many layouts run at once. One known gap remains: a nested graph where ELK cannot apply model order can still vary between runs (APP-2232).

## Highlights

- **Badges on boxes**: a leaf node takes a list of `Badge(glyph, label)`, drawn as solid glyphs in the box's mark colour.
- **Per-node fill**: a node's `fill` (six hex digits) draws the box as a layer of its own, with its edge, shading and icon all derived from that colour.
- **Legends**: `SceneGraph.legend` is drawn under the diagram in a framed box, laid out in as many columns as fit the drawing's width.
- **Deterministic concurrent layout**: ELK layouts are serialised, so concurrent renders of the same graph are identical.
- **ELK 0.12.0**: upgraded from 0.9.1.

## New Features

### Badges

`SceneNode` gains a `badges: List[Badge]` field. The badges are drawn in a row centred at the foot of a leaf box, in that box's mark colour. The box grows by one row when its text leaves no room, and widens to fit the row. Containers do not draw badges, which keeps their header clear.

`Glyph` has sixteen cases:

- Twelve geometric glyphs to tell things apart: `Circle`, `Square`, `TriangleUp`, `TriangleDown`, `Diamond`, `Star`, `Hexagon`, `Cross`, `Pentagon`, `Ring`, `Ellipse` and `Chevron`.
- Four glyphs with a fixed meaning: `Globe` for a public API, `Padlock` for a closed API, `Warning` for degraded health and `Failure` for failing health.

```scala
import fish.genius.uml.scene.*

val api = SceneNode(
  id = SceneId("orders-api"),
  kind = NodeKind.Element(ShapeType.ApplicationInterface),
  label = "Orders API",
  badges = List(
    Badge(Glyph.Globe, "Public API"),
    Badge(Glyph.Warning, "Degraded"),
  ),
)
```

### Fill colour

`SceneNode` gains `fill: Option[String]`, a six-digit hex colour (a leading `#` is allowed). A filled box is drawn as a layer named `am-fill-<hex>`, with its edge, shading and icon derived from the fill. The colour travels in the new `Drawing.colours` map, and `Svg`, `Png` and `Tikz` all define it. In TikZ it is emitted with `\definecolor`. A filled container also uses its fill as the background for any connector label or crossing gap drawn on it. A value that is not a valid hex colour is ignored, and the node keeps its layer's colour.

```scala
val highlighted = api.copy(fill = Some("#FFD54F"))
```

### Legend

`SceneGraph` gains `legend: Option[Legend]`, where `Legend(title, entries: List[Badge])` explains what the badges stand for. The legend is drawn under the diagram, left-aligned with it, in a framed box: first the title, then each glyph with its label, in as many columns as fit the drawing's width. A graph with no legend, or with a legend that has no entries, draws nothing extra.

```scala
val graph = SceneGraph(
  name = "Order landscape",
  nodes = List(highlighted),
  edges = Nil,
  legend = Some(Legend("Legend", List(
    Badge(Glyph.Globe, "Public API"),
    Badge(Glyph.Padlock, "Closed API"),
    Badge(Glyph.Warning, "Degraded"),
  ))),
)

Diagram.draw(graph).map(drawing => Svg.document(drawing))
```

The 3D scene (`SceneLayout`, the Blender builder) ignores badges, fill and legend.

## Changelog

### Added
- `Glyph` enum, `Badge(glyph, label)` and `Legend(title, entries)` in `fish.genius.uml.scene`.
- `SceneNode.fill` and `SceneNode.badges`, both defaulted and placed last.
- `SceneGraph.legend`, defaulted and placed last.
- `Notation.glyph`, which draws each glyph inside a unit square.
- `Diagram.Style.badgeSize` (default `0.24`) and `Diagram.Drawing.colours` (fill colours for the writers to define).
- A DSL.md section, "Badges, a fill of its own, and the legend".
- `DiagramSpec` tests:
  - sixteen identical layouts run concurrently produce the same drawing;
  - a box grows to hold its badges;
  - filled boxes and filled containers are drawn in their own colour, in every writer;
  - the legend is placed under the drawing with an entry per badge;
  - every glyph stays inside its unit square.

### Changed
- ELK upgraded from 0.9.1 to 0.12.0.
- `org.eclipse.xtext.xbase.lib` 2.36.0 is now a dependency of `scene`: ELK's option classes need it, but ELK's poms do not declare it.
- `ElkPass` serialises layouts and creates a fresh `RecursiveGraphLayoutEngine` for each one. The layered algorithm is still registered only once.
- `Svg`, `Png` and `Tikz` merge `Drawing.colours` into the palette they are given.
- DSL.md now documents the ELK version, why layouts run one at a time, and the determinism nested graphs still lack (APP-2232).

### Fixed
- The same graph laid out several times at once could produce different scenes, because ELK's compaction shared state between concurrent layouts.

## Module Changes

- **`scene`**: new model types (`Glyph`, `Badge`, `Legend`), new fields on `SceneNode` and `SceneGraph`, ELK 0.12.0 plus xbase.lib, serialised layouts in `ElkPass`.
- **`diagram`**: badges, fills and legends in `Diagram`, glyph drawing in `Notation`, fill colours handled by `Svg`, `Png` and `Tikz`; new tests in `DiagramSpec` and an updated `DiagramGallery`.
- **`core`**, **`render`**, **`testkit`** and **`examples`** are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, zio-process 0.8.0
- ELK 0.12.0 (was 0.9.1), Eclipse Xtext xbase.lib 2.36.0 (new)
- PlantUML 1.2026.2, os-lib 0.11.8, sourcecode 0.4.4
- Build: Mill with `fish.genius::mill-release` 0.5.0 and `mill-scalafix` 0.6.0

Source-compatible for callers that build `SceneNode` and `SceneGraph` with named or positional arguments, because every new field has a default and comes last. Code that pattern-matches these case classes positionally, or calls `unapply` on them, must account for the extra fields.
