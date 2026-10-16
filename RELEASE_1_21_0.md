# Release 1.21.0

**Release Date:** 2026-10-16

This release extends the flat ArchiMate diagram (`diagram`) with signs for the townplanner work that comes next. Every badge glyph is now drawn by a single sign family (`Signs`). Each sign is a vector path on a 24-unit grid with one line weight, so TikZ, SVG and PNG draw the same outline. There are eleven new glyphs: gap add, modify and remove, phase-out, risk, decision, and the API styles REST, GraphQL, gRPC, event and batch. A sign can now also stand on an edge, and a box can carry a corner tag: a short code in a pill of its own colour.

Swimlane (activity) diagrams also lay out better. A task's name now takes at most two lines, and the task widens to make that fit. Each lane is as wide as its widest row, where it used to be the number of tracks times the widest node.

## Highlights

- **One sign family**: every `Glyph` is drawn as a stroked or filled vector path with round caps and joins and the same weight, sized optically. The weight scales with the sign: 0.57 pt at badge size and 0.45 pt in the legend.
- **New signs**: `GapAdd`, `GapModify`, `GapRemove`, `PhaseOut`, `Risk`, `Decision`, `Rest`, `GraphQl`, `Grpc`, `Event` and `Batch`.
- **Edge signs**: `SceneEdge.sign` puts a glyph at the middle of a line, on a disc of the background so the line stops short of it.
- **Corner tags**: `SceneNode.tag` (`CornerTag(code, fill)`) draws a pill of one to three characters at a box's top right, with ink that keeps WCAG 4.5:1 contrast on the fill.
- **Better swimlane layout**: task names keep to two lines, and lanes size to their widest row.

## New Features

### Edge signs

An edge can carry a `Glyph` at the middle of its line. On a labelled line, the sign sits on the line just before the label so the two read together. Where the route is too short for that, it stands after the label, or halfway along when neither side has room. It takes the line's own colour when the line has one. The legend does not list an edge sign by itself, so give it a `Badge` there as for any other sign.

```scala
SceneEdge(SceneId("e1"), SceneId("shop"), SceneId("api"), RelationshipType.Flow,
  label = Some("orders"), sign = Some(Glyph.Rest))
```

### Corner tags

A node's `tag` is a short code in a small pill beside the icon at the box's top right. On a container it sits in the header. Typical uses are a verdict (I, T, M, E) or a classification.

- The code is set in capitals at 0.72 of the name's size. Each character gets a cell as wide as the widest figure or capital, so a column of verdicts has pills of one width.
- The ink is the text colour or paper, whichever stands out more on the fill. When neither keeps 4.5:1 contrast, it falls back to black or white.
- The box grows on both sides to make room, so the tag never covers the name, the caption, the icon or a logo.
- A tag is left out when its code is blank or longer than three characters, or when its fill is not six hex digits.

```scala
SceneNode(SceneId("crm"), NodeKind.Element(ShapeType.ApplicationComponent), "CRM",
  tag = Some(CornerTag("T", "#2E7D32")))
```

A legend can show a tag with `LegendItem.Tagged(tag, text)`. The scene's JSON carries tags on `SceneElement.tag`.

### Swimlane layout

- A task whose name would wrap to three or more lines widens, in 0.1 cm steps up to 6 cm, until the name takes two lines.
- Names are set at `Style.nameSize`, the size of box text in the other diagrams.
- Each node in a row gets a slot as wide as it needs, including its data, notes and event names. The lane's spare room is shared out between the slots.
- `Options.pageWidth` is now a target rather than a hard limit: a process with many long-named tasks side by side can come out wider.

## Changelog

### Added
- `Signs`: the badge glyphs redrawn as one family of vector paths on a shared grid.
- New `Glyph` cases: `GapAdd`, `GapModify`, `GapRemove`, `PhaseOut`, `Risk`, `Decision`, `Rest`, `GraphQl`, `Grpc`, `Event`, `Batch`.
- `SceneEdge.sign: Option[Glyph]`, drawn at the middle of the edge.
- `CornerTag(code, fill)` with `validCode`, `validFill` and `valid`, plus `SceneNode.tag` and `SceneElement.tag`.
- `LegendItem.Tagged(tag, text)`.
- `Prim.Path`: a filled and/or stroked outline whose line width is in centimetres and scales with the sign. Supported by the TikZ, SVG and PNG writers.
- `Emphasis.Inked(name)` for text in a named colour of its own.
- `Seg.map` helper.
- `DiagramGallery` test sample, and tests for signs, tags, edge-sign placement and swimlane widths.

### Changed
- **Breaking:** `Prim` has a new case, `Path`; `Glyph` has eleven new cases (the old ones keep their ordinals); `LegendItem` has a new case, `Tagged`. A match over any of them that lists every case needs the new ones.
- **Breaking:** `Emphasis` has a case with a parameter, `Inked(name)`, so it is no longer a plain enum: `Emphasis.values` and `Emphasis.valueOf` are gone, and a match on `Main` and `Muted` alone is no longer exhaustive.
- **Breaking:** `SceneNode` and `SceneElement` have a new last field, `tag`, and `SceneEdge` a new last field, `sign`. Code that builds them by name or position still compiles, but a pattern `SceneNode(...)`, `SceneEdge(...)` or `SceneElement(...)` with the old number of fields needs one more, and every jar compiled against 1.20 must be recompiled.
- `Notation.glyph` now delegates to `Signs.of`. Existing glyphs are redrawn at one stroke weight and with optical sizes.
- Activity diagrams now size task widths so names keep to two lines, and size lanes by their widest row instead of tracks times the widest node.
- DSL.md now documents the sign family, edge signs, corner tags and the new swimlane widths.

### Fixed
- On a labelled vertical line, a sign now sits on the route instead of floating half a label's width beside it.
- On a short route, a sign no longer covers the label or the source box: it moves after the label, or halfway along.
- Corner-tag codes are now counted and drawn by code point after uppercasing. Before, a character outside the basic plane was split in two, and ß expanded to SS after the length check had run.
- Corner-tag codes are now uppercased with `Locale.ROOT`, so a Turkish default locale gives the same output as any other.
- A path's extent now includes half its line width, so a sign at the drawing's edge is no longer clipped.

## Module Changes

- **`genius-uml-scene`**: new `Glyph` cases, `CornerTag`, `SceneNode.tag`, `SceneEdge.sign`, `SceneElement.tag` and `LegendItem.Tagged`. `SceneLayout` passes the tag through to the 3D scene's JSON, but the 3D builder does not draw tags or edge signs.
- **`genius-uml-diagram`**: new `Signs`, `Prim.Path` and `Emphasis.Inked`. Edge-sign and corner-tag rendering in `Diagram`, path support in `Tikz`, `Svg` and `Png`, and the new `Activity` layout.
- `core`, `render`, `testkit` and `examples` are unchanged.

## Compatibility

- Scala 3.8.3, ZIO 2.1.25, zio-json 0.7.3, ELK 0.12.0, PlantUML 1.2026.2, os-lib 0.11.8, sourcecode 0.4.4.
- Existing call sites still compile, because the new fields on `SceneNode`, `SceneEdge` and `SceneElement` have defaults. The release is not binary compatible with 1.20.x, though: code compiled against those case classes' `apply` and `copy` must be recompiled.
- A `match` over `Glyph`, `LegendItem`, `Prim` or `Emphasis` that lists every case and has no wildcard needs the new cases added.
- Diagram output changes visually: all glyphs are redrawn, and activity diagrams with long task names or uneven rows come out at different widths.
