# Release 1.17.0

**Release Date:** 2026-10-15

This release turns the flat-diagram legend from a list of badges into a full key for the diagram. `Legend` now takes optional sections, each with its own heading, which can explain element types, relationships, badges and logos. Each item is drawn with the same notation the diagram uses: a small box in its layer's colour with the type's icon, a short connector with the relationship's dash and arrowheads, a badge glyph, or a logo.

The legend is also smaller now and placed after the diagram's own layout, in the space the drawing leaves free. It goes into a free corner when one exists. Otherwise it stands beside a tall drawing or under a wide one, so a legend never makes a tall diagram taller or a wide diagram wider.

## Highlights

- **Legend sections**: `LegendSection(title, items)` groups element types, relationships, badges and logos, each under its own heading.
- **Notation-true legend items**: element types, relationships and logos are drawn the way they appear in the diagram, by the TikZ, SVG and PNG writers alike.
- **Placement that keeps the diagram's size**: the legend goes in a free corner, beside a tall drawing or under a wide one, and never grows the drawing's long side.
- **A more compact legend**: the legend is drawn at 0.8 of the diagram's sizes.

## New Features

### Legend sections

`Legend` gains a third parameter, `sections`, which defaults to `Nil`, so existing `Legend(title, entries)` calls still compile. Each `LegendSection` holds `LegendItem`s:

- `LegendItem.Mark(badge)`: a badge's glyph or logo, with the badge's label.
- `LegendItem.Shape(shapeType, text)`: an element type, drawn as a small box in its layer's colour with its icon.
- `LegendItem.Line(relationshipType, text)`: a relationship, drawn as a short connector with its dash and ends.
- `LegendItem.LogoMark(logo, text)`: a logo.

```scala
import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.scene.*

val legend = Legend(
  "Legend",
  Nil,
  List(
    LegendSection(
      "Elements",
      List(
        LegendItem.Shape(ShapeType.BusinessActor, "Business Actor"),
        LegendItem.Shape(ShapeType.ApplicationComponent, "Application Component"),
      ),
    ),
    LegendSection(
      "Relationships",
      List(
        LegendItem.Line(RelationshipType.Serving, "Serving"),
        LegendItem.Line(RelationshipType.Realization, "Realization"),
      ),
    ),
    LegendSection("Signs", List(LegendItem.Mark(Badge(Glyph.Globe, "Public API")))),
  ),
)

val graph = SceneGraph("Shop", nodes, edges, legend = Some(legend))
Diagram.draw(graph)
```

The legend is drawn in a framed box: the title first, then the badges (`entries`), then each section under its heading. Within a group, items are laid out in as many columns as fit. Each mark is centred in a column as wide as the widest mark in that group. Empty sections are left out, and a legend with nothing in it is not drawn. The 3D scene still ignores the legend.

### Legend placement

`Diagram.draw` places the legend after the diagram's layout, trying these positions in order:

1. A corner of the drawing (bottom right, bottom left, top right, then top left) that no box, frame, label patch or line passes through, made as wide as the corner allows.
2. Beside a drawing that is taller than it is wide, aligned to the top and as narrow as the drawing's height allows. A long legend gets more columns (past the drawing's width if needed) so that it stays within the drawing's height.
3. Under a drawing that is wider than it is tall, as low as the drawing's width allows.

## Changelog

### Added
- `LegendSection` and `LegendItem` (`Mark`, `Shape`, `Line`, `LogoMark`) in the `scene` module.
- `Legend.sections`, which defaults to `Nil`.
- Legend rendering for element types, relationships and logos in all three flat-diagram writers (TikZ, SVG, PNG).
- `LegendSpec`, which covers each item kind, section order, empty and badge-only legends, the writers, determinism and the placement rules over four graphs.
- A legend fixture in the diagram gallery.

### Changed
- The legend is drawn at 0.8 of the diagram's sizes: the title at caption size, headings and labels at 0.8 of note size, with smaller badges, swatches and lines.
- The legend is no longer always drawn under the diagram, left-aligned with it. It now goes in a free corner, or beside or under the drawing depending on the drawing's shape.
- DSL.md, "Diagrams: ArchiMate drawn flat", documents legend sections and the placement rule.

### Fixed
- A long legend beside a tall drawing no longer hangs below it: it gets more columns until it fits the drawing's height.

## Module Changes

- **scene**: `Legend` has the new `sections` parameter, and `LegendSection` and `LegendItem` are added.
- **diagram**: legend layout, the new item kinds, scaling and placement in `Diagram.draw`.
- **core**, **render**, **testkit** and **examples** are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, os-lib 0.11.8, sourcecode 0.4.4
- PlantUML 1.2026.2
- Eclipse ELK 0.12.0
- Built with mill-release 0.5.0 and mill-scalafix 0.6.0

Existing code still compiles: `Legend(title, entries)` works unchanged because `sections` has a default. The release is not binary compatible for `Legend`, though: the parameter changes the signatures of the case class's generated `apply`, `copy` and `unapply`, so code compiled against 1.16.0 that builds a `Legend` must be recompiled. Diagrams that already had a legend will look different, because the legend is smaller and placed differently.
