# Release 1.11.1

**Release Date:** 2026-10-14

This patch release fixes how flat ArchiMate diagrams look when exported as SVG. The `diagram` module cuts the gaps where lines cross, and the patches under labels and headings, in the diagram's paper colour (`am-paper`). The SVG output had no background of its own. When the SVG sat on a page of a different colour, for example a cream-paper diagram on a white slide, those gaps and patches showed up as visible blocks.

`Svg.document` now draws a full-size rectangle in the paper colour before anything else. The SVG now has the same background as the PNG writer, which already drew one. The public API does not change.

## Highlights

- **SVG diagrams carry their own paper background**: crossing gaps and label patches blend in on any host page, as they already did in the PNG output.

## Changelog

### Fixed
- `Svg.document` now emits a `<rect>` covering the whole `viewBox` in the palette's paper colour, right after `<defs>` and before the drawing group. Without it, the paper-coloured gaps at line crossings and the patches under labels and headings showed as blocks on any background that was not the paper colour.

### Changed
- `DiagramSpec` checks that an SVG rendered with `am-paper -> FBF6EC` contains the `fill="#FBF6EC"` background rectangle.

## Module Changes

- **`diagram`**: `Svg.scala` gets the background rectangle, and `DiagramSpec.scala` gets the new assertion.
- `core`, `render`, `testkit`, `scene` and `examples` are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, zio-process 0.8.0
- Eclipse ELK 0.9.1
- PlantUML 1.2026.2
- os-lib 0.11.8, sourcecode 0.4.4
- The source and binary API are compatible with 1.11.0.
