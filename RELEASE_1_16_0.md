# Release 1.16.0

**Release Date:** 2026-10-15

Flat ArchiMate diagrams can now show real product logos. A `Badge` or a leaf `SceneNode` can carry a `Logo`, either a vector logo made of filled SVG path outlines or an embedded PNG/JPEG picture. All three writers (SVG, PNG and TikZ) draw it in its own colours, as a badge at a box's foot, in the legend, or in a leaf box's top left corner. Logos replace the geometric glyphs where one is supplied, which also removes the glyph wrap seen past roughly twelve badges.

Logo input is treated as untrusted. `Logos.fromSvg` reads a safe subset of SVG and refuses DTDs, documents over 4 MB and group nesting deeper than 64 levels. `Logos.fromPicture` accepts only real PNG or JPEG data of at most 16 million pixels. Every writer accepts only six-digit hex colours and base64 picture data, so a hostile fill or a hand-built `Logo` cannot break the SVG output or crash the TikZ and PNG writers.

## Highlights

- **Logos in native diagrams**: badges, legend entries and leaf boxes can show a vector or bitmap logo instead of a glyph.
- **SVG import**: `Logos.fromSvg` turns an SVG icon into a `Logo.Vector`. It reads paths, basic shapes, inherited fill and fill-rule, and group and shape transforms.
- **Full SVG path parser**: `SvgPath.parse` reads `M L H V C S Q T A Z` in absolute and relative form, and converts quadratics and arcs to cubics.
- **Hardened input handling**: only validated colours and base64 PNG/JPEG data reach a writer, with size and depth limits on everything it parses.

## New Features

### Logos on badges, legends and boxes

`scene` adds a `Logo` type and an optional `logo` field on `Badge` and `SceneNode`, defaulted and placed last:

```scala
import fish.genius.uml.scene.*

val brand: Logo = Logo.Vector(
  minX = 0, minY = 0, width = 24, height = 24,
  shapes = List(
    LogoShape("M12 2 L22 22 H2 Z", fill = "e34f26"),
    LogoShape("M12 8 L17 18 H7 Z M12 11 L14 16 H10 Z", fill = "ffffff", evenOdd = true),
  ),
)

val badge = Badge(Glyph.Circle, "Hosted on Brand", logo = Some(brand))

val node = SceneNode(
  id = SceneId("app"),
  kind = /* … */,
  label = "Billing",
  badges = List(badge),
  logo = Some(brand),          // drawn in the leaf's top left corner
)
```

A logo badge is 1.4 times the glyph's height and up to three times as wide as it is high. The legend centres each logo or glyph in a column as wide as the widest entry. A leaf's logo is as high as the icon in the opposite corner, and the name keeps clear of it. Containers show no logo.

### Reading logos from SVG and pictures

```scala
import fish.genius.uml.diagram.Logos

val fromSvg: Either[String, Logo]     = Logos.fromSvg(svgDocument)
val fromPng: Either[String, Logo]     = Logos.fromPicture(pngBytes, "image/png")
```

`fromSvg` fills shapes and does not stroke them, so an outline icon drawn with strokes comes out as solid shapes. It draws `path`, `rect`, `circle`, `ellipse`, `polygon` and `polyline`, applying `translate`, `scale`, `rotate` and `matrix`. A fill it cannot read as a colour (`currentColor`, a gradient, an unknown name) becomes black. It does not draw opacity, `display`, clip paths, masks or text. `fromPicture` detects the format from the bytes, not the declared MIME type, and reads the size from the header before decoding.

### TikZ pictures as companion files

TikZ output draws vector logos as native `\path` curves. A picture logo becomes an `\includegraphics` of a file whose name comes from a hash of its content. `Tikz.files(drawing)` returns each file's name and bytes, and the caller writes them next to the `.tex` it compiles:

```scala
Files.writeString(dir.resolve("diagram.tex"), Tikz.standalone(drawing, palette))
Tikz.files(drawing).foreach((name, bytes) => Files.write(dir.resolve(name), bytes))
```

## Changelog

### Added
- `scene`: `Logo` (`Logo.Vector`, `Logo.Picture`, with `aspect`), `LogoShape`, `Badge.logo` and `SceneNode.logo`.
- `diagram`: `Prim.Curve` (filled outline, optional even-odd rule) and `Prim.Image` (embedded base64 PNG/JPEG).
- `diagram`: the `Seg` enum (`Move`, `Line`, `Cubic`, `Close`) with `Seg.shift` and `Seg.points`.
- `diagram`: `SvgPath.parse`, a full SVG path-data parser that returns segments or an error.
- `diagram`: `Logos.place`, `Logos.width`, `Logos.fromSvg` and `Logos.fromPicture`.
- `diagram`: `Tikz.fileOf` and `Tikz.files` for the picture files a TikZ drawing includes.
- SVG writer: `<path>` and embedded `<image>` output (`href` and `xlink:href`), with the `xmlns:xlink` namespace on the root element.
- PNG writer: Java2D rendering of curves and decoded pictures.
- `LogoSpec`, which covers parsing, fitting, box logos, badges and legend, all three writers, both readers, determinism and hostile input. The diagram gallery gains a `logos` sample.
- DSL.md: a "Logos" section that documents the model, the supported SVG subset and its limits.

### Changed
- Badges with a logo draw the logo in place of their glyph, and the legend aligns logos and glyphs in one column.
- A leaf node's name is placed to keep clear of its logo.
- The bounding-box computation (`Extent`) and activity-diagram shifting now account for curves and images.

### Fixed
- An SVG fill is read as a colour only when it is `#rrggbb`, `#rgb`, a well-formed `rgb(…)` or a known colour name, and anything else becomes black. A crafted fill can no longer inject markup into the SVG output or crash the TikZ and PNG writers.
- A `Logo` built directly is sanitised before drawing: curve colours must be six hex digits, and picture data must be base64 PNG or JPEG. The SVG writer escapes picture data.
- `Logos.fromSvg` refuses documents with a DTD (no external entities), documents over 4 MB, and nesting deeper than 64 groups, which prevents a stack overflow.
- `Logos.fromPicture` refuses anything but PNG and JPEG (GIF included), and pictures over 16 million pixels before decoding them.

## Module Changes

- **scene**: `SceneGraph.scala`, which adds the logo model and the logo fields on `Badge` and `SceneNode`.
- **diagram**: `Diagram.scala` (badge, legend and box-logo layout), `Logos.scala` (new), `Notation.scala` (new `Prim` cases and `Seg`), and `Svg.scala`, `Png.scala`, `Tikz.scala` and `Activity.scala` (rendering and shifting of the new primitives).
- **core**, **render**, **testkit**, **examples**: unchanged.

## Compatibility

- Scala 3.8.3, built with Mill and `fish.genius::mill-release` 0.5.0.
- Key dependencies: ZIO 2.1.25, zio-json 0.7.3, ELK 0.12.0, PlantUML 1.2026.2, os-lib 0.11.8, sourcecode 0.4.4.
- Source compatible for most callers: the new `logo` parameters on `Badge` and `SceneNode` are defaulted and come last.
- Code that pattern-matches exhaustively on `Prim` must handle the new `Prim.Curve` and `Prim.Image` cases.
- Not binary compatible with 1.15.0: the constructor, `apply` and `copy` signatures of `Badge` and `SceneNode` changed, so dependants must recompile against 1.16.0.
