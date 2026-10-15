# Release 1.19.0

**Release Date:** 2026-10-15

This release lets a relationship in a flat ArchiMate diagram carry its own colour. Set `SceneEdge.colour` to a hex value and the line, its arrowheads and the hop where it crosses another line are all drawn in that colour, in the TikZ, SVG and PNG writers alike. A dossier can use this to set a proposed flow apart from the existing landscape.

The TikZ writer now also handles the common arrows and math symbols that pdflatex's UTF-8 input rejects. A label such as "persoon ↔ rol" used to break the build and now compiles.

## Highlights

- **Coloured edges**: `SceneEdge.colour` draws a relationship's line and heads in a colour of its own, with the same result in every writer.
- **Symbols in pdflatex**: the TikZ writer writes ↔ → ← ↑ ↓ ⇒ ⇐ ⇔ ≤ ≥ ≠ ≈ ± × ÷ ∞ • ✓ ✗ as `\ensuremath{…}`.

## Breaking Changes

- `Prim.Connector` has a fifth field, `ink: Option[String] = None`. Code that builds a connector still compiles because the field has a default. Code that pattern-matches on it must now bind five arguments, for example `case Prim.Connector(ps, dash, start, end, ink)`.

## New Features

### An edge in a colour of its own

`SceneEdge` takes an optional `colour`, given as six hex digits (`#` allowed). The colour is passed through the layout to `SceneRelationship.colour` and then into the drawing as `Prim.Connector.ink`. It is registered in `Drawing.colours` as `am-fill-<hex>`, the same way a node's `fill` is, so every writer defines it.

```scala
val proposed = SceneEdge(
  source = SceneId("crm"),
  target = SceneId("billing"),
  relationship = RelationshipType.Flow,
  label = Some("klantgegevens"),
  colour = Some("#D9534F"), // line and heads drawn in this colour
)
```

The colour affects these parts of the drawing:

- **The line:** stroked in the colour in TikZ (`draw=…,fill=…`), SVG (`stroke`) and PNG.
- **The heads:** the outline is stroked in the colour, and a filled head is also filled in it. This uses a new `Role.Inked(layer)`.
- **Crossings:** the short hop drawn where the coloured line crosses another keeps its colour.

As with a node's `fill`, a colour that is not six hex digits is ignored, and the edge is drawn like any other line.

### Arrows and symbols escaped for pdflatex

`Tikz.escape` now converts these characters to math mode:

| Character | Written as |
|---|---|
| ↔ → ← ↑ ↓ | `\leftrightarrow` `\rightarrow` `\leftarrow` `\uparrow` `\downarrow` |
| ⇒ ⇐ ⇔ | `\Rightarrow` `\Leftarrow` `\Leftrightarrow` |
| ≤ ≥ ≠ ≈ | `\leq` `\geq` `\neq` `\approx` |
| ± × ÷ ∞ | `\pm` `\times` `\div` `\infty` |
| • ✓ ✗ | `\bullet` `\surd` `\times` |

Each one is wrapped in `\ensuremath{…}`, so it works in text and in math.

## Changelog

### Added
- `SceneEdge.colour` and `SceneRelationship.colour`: an optional hex colour for one relationship.
- `Prim.Connector.ink`: the colour layer a connector is drawn in.
- `Role.Inked(layer)`: an outline stroked in a layer's own colour, supported by `Look` (SVG and PNG) and `Tikz`.
- TikZ escaping of 19 arrows and math symbols that pdflatex's UTF-8 input does not know.
- DSL.md documents coloured edges and the symbol escaping.
- Tests: a coloured edge (connector, filled and open heads, SVG stroke, TikZ colour, the colour carried through the scene, and a colour that is not six hex digits), and a drawing with arrows and symbols compiled by pdflatex.

### Changed
- `Diagram` adds relationship colours to `Drawing.colours` next to node fills.
- `Heads.of` gives a coloured connector's heads `Role.Inked` and fills its filled heads in the connector's colour.

### Fixed
- In SVG and PNG, the heads of a coloured connector were outlined in the default line colour, and open heads were drawn entirely in it. They are now outlined in the connector's colour, as TikZ already did.
- The crossing hop on a coloured line lost its colour.
- pdflatex failed on labels that contained arrows or symbols such as "↔" (OBEYA-605).

## Module Changes

- **`scene`**: `SceneEdge` and `SceneRelationship` gain `colour`, and `SceneLayout` carries it from one to the other.
- **`diagram`**: `Notation` (`Prim.Connector.ink`, `Role.Inked`), `Diagram`, `Heads`, `Look`, `Tikz`, `Svg`, `Png` and `Activity` changed, with tests in `DiagramSpec`, `ActivitySpec` and `LegendSpec`.
- **`core`**, **`render`**, **`testkit`** and **`examples`** did not change.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-process 0.8.0, zio-json 0.7.3
- PlantUML 1.2026.2
- ELK 0.12.0 (xbase-lib 2.36.0)
- os-lib 0.11.8, sourcecode 0.4.4
