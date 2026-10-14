# Release 1.11.0

**Release Date:** 2026-10-14

This release makes the flat ArchiMate diagrams of the `diagram` module look right in print. Boxes are filled in their layer's colour with a slight fall-off from top to bottom. Each box has an edge a shade darker than its fill and a soft, short shadow. Containers are drawn as lighter fields, groupings as dashed frames with a tab, and connectors as fine lines with rounded bends, arrowheads matched to the relationship, and a gap where one line passes under another. The TikZ, SVG and PNG writers now share one set of numbers (`Look`), so all three outputs agree. A new `Typeface` sets the face and measures boxes from the same font files LaTeX uses: TeX Gyre Heros by default, Montserrat for Innovenso documents.

Dense diagrams also lay out better in flat mode. An edge that runs upward against the layers no longer loops around its ports. An edge crossing a container's boundary enters on the side that faces it. Overlapping stretches of different routes are pushed apart, and a row wider than the page folds instead of shrinking the whole diagram. The 3D scene and its golden output are unchanged.

## Highlights

- **Print-quality look**: layer-coloured boxes with a fall-off, a darker edge and a soft shadow; container fields; dashed groupings; rounded connectors with `arrows.meta` heads and gaps at crossings. TikZ, SVG and PNG draw the same look.
- **`Typeface`**: boxes are measured from the font files LaTeX sets them in, found with `kpsewhich`. The two built-in faces are `Typeface.heros` and `Typeface.montserrat`.
- **Fewer loops and bundles**: upward edges are routed straight, container ports are placed freely, and overlapping route stretches are spread apart.
- **No shrinking on the page**: `SceneLayout.Options.rowWidth` and `Diagram.Options.pageWidth` (13 cm) fold a row that is wider than the page.
- **Compact layout**: `SceneLayout.Options.compact` places nodes with network simplex. Where ELK fails on a nested graph, it falls back to the plain layout.

## New Features

### Typefaces

`Diagram.Style` now takes a `Typeface` instead of a list of font families. A typeface carries the LaTeX preamble, the font selection for each weight, and the font files that boxes are measured with. If TeX is not installed, the installed families stand in.

```scala
import fish.genius.uml.diagram.*

val options = Diagram.Options(
  style = Diagram.Style(typeface = Typeface.montserrat)
)
// Tikz.standalone(drawing) loads the face's own preamble (\usepackage[lining]{montserrat})
```

### Roles, weights and emphasis on primitives

Each `Prim` says what it is part of through a `role: Role`: `Plain`, `Element(layer)`, `Container(layer)`, `Group`, `Mark(layer)`, `Halo` or `Patch(on)`. The writers style each primitive by its role. `Prim.Text` takes a `weight: Weight` (`Regular`, `Medium`, `SemiBold`, `Italic`) and a `tone: Emphasis` (`Main`, `Muted`). An optional `backdrop` lets a line of text stand on a patch of its field, so a line underneath never runs through the letters. The palette adds `am-text`, `am-muted` and `am-paper`.

### Flat-layout options

```scala
import fish.genius.uml.scene.SceneLayout

val flat = SceneLayout.Options(
  spreadPorts = true, // also reverses upward edges for ELK and frees container ports
  compact = true,     // network simplex plus compaction, with a fallback to the plain layout
  rowWidth = 13.0,    // fold a row wider than the page (0 means no limit)
)
```

After the layout, `Diagram` tidies the routes in two ways. It straightens a tiny step where two stretches nearly line up. It also spreads apart the stretches of different routes that lie on top of one another, in the order the routes part. A spread is skipped when it would shorten a neighbouring stretch below 0.08 cm, reverse a stretch, or separate two stretches of the same route.

## Breaking Changes

- `Diagram.Style.fontFamilies` is replaced by `Diagram.Style.typeface: Typeface`. The default sizes changed: `nameSize` 7.6, `captionSize` 6.0, `noteSize` 5.8, `labelSize` 6.0, `lineHeight` 1.22, `padding` 0.18, `iconSize` 0.32, `junction` 0.16.
- `Prim.Text(bold: Boolean)` is replaced by `weight: Weight` and `tone: Emphasis`, and it gains `backdrop: Option[Role]`.
- Every `Prim` shape (`Poly`, `Circle`, `Ellipse`, `Box`) gains a trailing `role: Role` field, so a positional pattern on one needs one more field.
- The arrowheads `Heads` draws for SVG and PNG match the new TikZ sizes.
- `Measure` is now `private[diagram]`, and the new `Look` is internal to the module.
- `Tikz.libraries` now loads `shadows.blur` as well as `arrows.meta`. A document that embeds `Tikz.picture` must load both libraries.
- `Tikz.standalone` now takes its preamble from the drawing's typeface, and its border grows from 4 pt to 6 pt.

## Changelog

### Added
- `Typeface`, with `Typeface.heros` and `Typeface.montserrat`. Boxes are measured from the TeX font files found by `kpsewhich`, which is given at most five seconds.
- `Role`, `Weight` and `Emphasis` in `Notation`, a `role` field on every `Prim` shape, and `Notation.layer(group)`.
- The palette colours `am-text`, `am-muted` and `am-paper`.
- `SceneLayout.Options.compact` and `SceneLayout.Options.rowWidth`.
- `Diagram.Options.pageWidth` (13 cm by default).
- Route tidying after the layout: `straighten` for tiny steps and `nudge` for overlapping stretches.
- `DiagramGallery`, which renders the test diagrams for visual review.
- `DiagramRoutesSpec`, which tests `straighten` and `nudge` on hand-made routes: they stay orthogonal, keep their ends, are idempotent, and refuse a spread that would reverse a stretch. It also tests `Look.mix`.
- `SceneFlatSpec` cases: an upward edge uses its own ports, a row wider than `rowWidth` folds, and `compact` lays out a large nested graph.

### Changed
- TikZ, SVG and PNG share one look: the fall-off, the darker edge, the soft shadow, rounded bends, `arrows.meta` heads sized per relationship, and round dots for access.
- Drawing runs back to front: fields, boxes, lines, words, labels.
- Container headings and connector labels stand on a patch of the field they lie on.
- Icons are drawn in the edge colour, and names keep a little space beside their icon.
- With `spreadPorts`, an upward edge is handed to ELK reversed and turned back afterwards, and container ports are free.
- `DSL.md` documents the look, the typeface and the new layout options.

### Fixed
- Upward edges in flat diagrams no longer loop around inverted ports.
- Communication graphs no longer funnel every flow down one container wall.
- Wide diagrams are no longer scaled below a legible size when printed.

## Module Changes

- **`diagram`**: `Diagram`, `Heads`, `Look` (new), `Notation`, `Png`, `Svg`, `Tikz`, `Typeface` (new), and their tests.
- **`scene`**: `ElkPass` and `SceneLayout` (flat mode only), and `SceneFlatSpec`.
- **`core`**, **`render`**, **`testkit`** and **`examples`**: unchanged.

## Compatibility

- Scala 3.8.3
- ELK 0.9.1, zio-json 0.7.3 (`scene`)
- ZIO 2.1.25, zio-process 0.8.0, os-lib 0.11.8, PlantUML 1.2026.2 (`render`)
- sourcecode 0.4.4 (`core`)
- Built with Mill and `fish.genius::mill-release` 0.5.0
- Optional: a TeX installation with `kpsewhich`, plus `tgheros` or `montserrat`, for exact box measurement. Without TeX, the installed font families are used.
