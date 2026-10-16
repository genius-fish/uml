# Release 1.22.0

**Release Date:** 2026-10-16

This release adds four visual encodings to the flat ArchiMate diagram (`diagram`), for gap, heat and integration views. Badges can now carry a short text, boxes can be drawn faded, lines can carry a weight, and legends can show colour swatches. Each encoding has a default, so existing callers draw exactly as before.

The `scene` model's `SceneGraph` types gained new fields, and the drawing primitives gained a weight and a role. All new fields have defaults, so existing source still compiles. Code compiled against 1.21.0 and code that pattern-matches these case classes positionally must be recompiled or updated (see Breaking Changes).

## Highlights

- **Badge text**: a short word after a badge's sign (a phase-out date, a risk code), which the box's row counts and caps at 12 characters.
- **Faded boxes**: an element drawn paler, as one a gap analysis removes, with its text still meeting WCAG contrast on every ground it stands on.
- **Weighted lines**: an edge drawn up to 2.5 times its normal width, with its heads scaled to match.
- **Legend swatches, faded and weighted items**: three new `LegendItem` cases, for a heat scale, a faded sample and a weighted line.

## New Features

### Badge text

`Badge` takes an optional `text`, which is set right after the sign at the caption's size in the muted ink. The box widens to fit it, and the next badge stands a little further off so each sign reads with its own text. Blank text counts as none. Text longer than `Badge.MAX_TEXT` (12 code points) is cut and ends in an ellipsis, so one badge cannot push its box out of its row. The legend still names a badge by its label alone.

```scala
Badge(Glyph.PhaseOut, "Phase-out", text = Some("06/2027"))
```

### Faded boxes

A `SceneNode` with `faded = true` is drawn paler:

- **Fill:** 38% of its layer (or of its own `fill`) against paper.
- **Edge, icon and signs:** a lighter ink.
- **Text:** the lightest mix of the text colour that keeps 4.5:1 contrast for the name and note, and 3:1 for the caption and badge text. This holds on the fill, on the top of its fall-off and on a container's field.

Each writer derives these colours from its own palette. The colours are named `am-fade-<part>-<layer>`. TikZ emits them as xcolor mixes after the layer colours, so a document that defines its own layer colours fades them too. If no mix of a palette's text colour holds the contrast, the text is drawn in black or white.

When a container is faded, its frame, field and header fade, but the nodes inside it keep their own look. Groupings, junctions, logos and tags are never faded.

```scala
SceneNode(SceneId("legacy-crm"), NodeKind.Element(ShapeType.ApplicationComponent), "Legacy CRM",
  faded = true)
```

### Weighted lines

`SceneEdge.weight` (1 by default) draws the line that many times its normal width, for example to show an integration's volume:

- A weight that is not a positive number counts as 1, and a weight above 2.5 counts as 2.5.
- Dashes and dots grow with the line.
- Heads grow half as fast as the line, by a factor of `(1 + weight) / 2`.
- TikZ scales the arrows.meta tips; SVG and PNG scale the drawn heads.

In the drawing, the weight is `Prim.Connector.weight`.

```scala
SceneEdge(SceneId("e1"), SceneId("orders"), SceneId("billing"), RelationshipType.Flow, weight = 2.4)
```

### New legend items

- `LegendItem.Faded(shape, text)`: a faded sample box, in the shape's layer with its icon, or on paper when no shape is given.
- `LegendItem.Weighted(relationship, weight, text)`: a line of that relationship at that weight, half as long again as a plain one.
- `LegendItem.Swatch(fill, text, shape = None)`: a small box in that fill, for example one step of a heat scale. Swatches are listed in the order given. A fill that is not six hex digits leaves the box out and keeps the text.

## Changelog

### Added
- `Badge.text`, `Badge.shownText` and `Badge.MAX_TEXT` (12).
- `SceneNode.faded`, and faded colour derivation in `Look.Colours.fade` / `fading`, with the WCAG contrast check `Look.contrast`.
- `SceneEdge.weight` and `Prim.Connector.weight`, plus `Look.linkWeight` (caps the weight at 2.5) and `Look.headScale`.
- `Diagram.Drawing.faded`: the layers a faded box or legend item is drawn from, whose colours each writer derives.
- `Role.Faint`, so marks on a faded box keep a mark's line weight in their own ink.
- `LegendItem.Faded`, `LegendItem.Weighted` and `LegendItem.Swatch`.
- `Heads.at` takes a `scale` parameter.
- `EncodingSpec`, which covers all four encodings. It includes badge text truncation and a faded box whose outline has its own colour and keeps that colour for its edge.
- DSL.md sections on badge text, faded boxes, weighted lines and the new legend items.

### Changed
- The TikZ, SVG and PNG writers emit the faded colours and weighted lines and heads.
- When a weighted line is redrawn where it crosses another line, the redrawn piece keeps its weight.
- The 3D scene leaves out faded boxes and edge weights, as it already does for other flat-diagram-only encodings.

## Breaking Changes

All new fields have defaults, so existing source compiles unchanged. These changes break binary compatibility and positional pattern matches:

- `Badge` has a new last field, `text`.
- `SceneNode` has a new last field, `faded`.
- `SceneEdge` has a new last field, `weight`.
- `Prim.Connector` has a new last field, `weight`, so a positional pattern `Prim.Connector(ps, dash, start, end, ink)` needs a sixth argument.
- `Role` has a new case, `Faint`.
- `LegendItem` has new cases `Faded`, `Weighted` and `Swatch`, so exhaustive matches over it need the new cases.

## Module Changes

- **scene**: `SceneGraph.scala` (`Badge`, `SceneNode`, `SceneEdge`, `LegendItem`).
- **diagram**: `Diagram`, `Look`, `Notation`, `Heads`, `Activity`, `Tikz`, `Svg` and `Png`, plus the tests `EncodingSpec`, `ActivitySpec`, `LegendSpec` and `DiagramGallery`.
- **core**, **render**, **testkit** and **examples** are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-process 0.8.0, zio-json 0.7.3
- PlantUML 1.2026.2
- Eclipse ELK 0.12.0 (xbase.lib 2.36.0)
- os-lib 0.11.8, sourcecode 0.4.4
