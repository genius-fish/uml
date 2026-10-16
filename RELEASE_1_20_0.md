# Release 1.20.0

**Release Date:** 2026-10-16

genius-uml 1.20.0 lets a node in a flat ArchiMate diagram have an outline of its own. A new `Outline(dash, weight, colour)` on `SceneNode` sets the edge's dash, weight and colour and leaves the box's fill alone, so an element keeps its layer colour while its edge marks it out. The first use is a proposed element drawn with a heavier dashed edge. The channel is generic, so it can carry other meanings later, such as a heavier weight for criticality. TikZ, SVG and PNG all draw it, and so does the legend through a new `LegendItem.Outlined` sample.

This release also fixes the SVG writer. Until now a dotted shape or connector got two `stroke-width` attributes, which makes the SVG invalid XML. The width is now written once.

This release breaks code that matches on `Prim.Box` or `Prim.Poly`: both have a new last field, `pen`. A match over `LegendItem` that is exhaustive also needs a case for the new `Outlined`.

## Highlights

- **Node outlines**: `SceneNode.outline` draws a node's edge (or a container's frame) solid, dashed or dotted, heavier and in its own colour, and keeps the fill.
- **Outline legend item**: `LegendItem.Outlined` draws a sample box with that edge, either in a shape's layer with its icon or on paper.
- **Valid SVG for dotted lines**: each dotted line now has a single `stroke-width`.

## New Features

### Node outline

`Outline` and `Stroke` live in the `scene` module:

```scala
enum Stroke:
  case Solid, Dashed, Dotted

final case class Outline(
  dash: Stroke = Stroke.Dashed,
  weight: Double = 1.0,
  colour: Option[String] = None)
```

- `weight` multiplies the role's normal line weight. A weight that is not a positive number counts as 1, and anything above `Outline.MAX_WEIGHT` (4.0) counts as 4.
- Dash and dot lengths grow with the weight, so a heavier dashed edge keeps the same rhythm.
- `colour` takes six hex digits (with or without `#`) and replaces the edge's colour. Any other value is ignored, the same way an invalid `fill` is.
- The fill stays the layer's colour, or the node's `fill` when one is set.
- All four outline forms take an outline (square, rounded, banded, cut), and so do a container's frame and a grouping's frame.

```scala
val proposed = Outline(Stroke.Dashed, weight = 2.0, colour = Some("#D9534F"))

SceneNode(
  SceneId("crm"),
  NodeKind.Element(ShapeType.ApplicationComponent),
  "CRM",
  caption = Some("Application Component · voorgesteld"),
  outline = Some(proposed),
)
```

`SceneLayout` copies the outline into the scene's JSON as `SceneElement.outline`. The Blender and three.js builders ignore it for now.

### Outlined legend item

```scala
LegendItem.Outlined(proposed, "Proposed", shape = Some(ShapeType.ApplicationComponent))
LegendItem.Outlined(proposed, "Proposed") // a plain box on paper
```

The diagram defines the colours that legend samples use, so a colour that appears only in the legend still renders.

### Pen on drawing primitives

In the `diagram` module, a new `Pen(weight: Double = 1.0, ink: Option[String] = None)` (with `Pen.Normal`) sits on `Prim.Box` and `Prim.Poly`. TikZ, SVG and PNG apply it after the role's look, so it changes only the edge.

## Changelog

### Added
- `scene.Stroke` (`Solid`, `Dashed`, `Dotted`) with a JSON codec.
- `scene.Outline(dash, weight, colour)` with `effectiveWeight`, `validColour`, `MAX_WEIGHT` and a JSON codec.
- `SceneNode.outline` and `SceneElement.outline`, which the scene's JSON carries.
- `LegendItem.Outlined(outline, text, shape = None)`.
- `diagram.Pen(weight, ink)` and `Pen.Normal`.
- A `pen` field on `Prim.Box` and `Prim.Poly`, drawn by TikZ, SVG and PNG.
- Tests for the four outline forms and a container's frame (dash, pen, fill kept, TikZ, SVG and a PNG pixel check), the weight and colour bounds, the scene JSON round trip, the legend sample, and a heavier dotted outline (SVG parsed as XML, dot size and gap in SVG and TikZ, PNG).
- DSL.md sections on node outlines and the outlined legend item.

### Changed
- **Breaking:** `Prim.Box` and `Prim.Poly` have a new last field, `pen`. Code that pattern-matches on them needs one more argument (the same kind of change `Prim.Connector` had in 1.19.0).
- **Breaking:** `LegendItem` has a new case, `Outlined`. An exhaustive match over it needs one more case.
- Dash and dot lengths now grow with the pen's weight in TikZ, SVG and PNG.
- A dotted line's width is the width of its dots, scaled by the pen.
- `Look.weight(role)` and the dash constants (`DASH_ON`, `DASH_OFF`, `DOT_GAP`) moved to the `Look` object, so all three writers share them.

### Fixed
- SVG: a dotted shape or connector no longer gets two `stroke-width` attributes, so the SVG is valid XML.

## Module Changes

- **scene**: adds `Stroke`, `Outline`, `SceneNode.outline`, `SceneElement.outline` and `LegendItem.Outlined`. `SceneLayout` copies the outline into the scene.
- **diagram**: adds `Pen` and the `pen` field on `Prim.Box` and `Prim.Poly`. `Diagram`, `Activity`, `Tikz`, `Svg` and `Png` draw the outline and the legend sample. Contains the SVG stroke-width fix.
- **core**, **render**, **testkit** and **examples** are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, zio-process 0.8.0
- Eclipse ELK 0.12.0
- PlantUML 1.2026.2
- os-lib 0.11.8, sourcecode 0.4.4
- Source-incompatible for code that pattern-matches on `Prim.Box` or `Prim.Poly`, or matches `LegendItem` exhaustively. Code that builds them with named or default arguments still compiles.
