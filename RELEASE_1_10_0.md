# Release 1.10.0

**Release Date:** 2026-10-14

This release adds a new module, `genius-uml-diagram`, which draws an ArchiMate `SceneGraph` as a flat diagram without PlantUML. It uses the same ELK layout as the 3D scene, so a diagram in a document and a 3D flyover show the same landscape in the same places. The drawing is plain geometry, written out as TikZ for LaTeX, as SVG, or as PNG through Java2D. Each box is sized to its text, measured in the diagram's font, and the three outputs place every line of text at the same position.

To support this, the `scene` module has new optional fields on nodes, edges and relationships, and four new layout options. With those options left at their defaults, the 3D scene is exactly what 1.9.1 produced. A new golden test checks this.

## Highlights

- **Flat ArchiMate diagrams**: `Diagram.draw(graph)` lays out a `SceneGraph` and returns a `Drawing`, which `Tikz`, `Svg` or `Png` write out.
- **ArchiMate 3.2 notation**: element types are drawn with square, rounded, cut-corner or banded outlines and an icon in the corner. Relationship types get their line style and end markers.
- **Boxes sized to their text**: each box shows a name, a caption and a note, measured in TeX Gyre Heros, Nimbus Sans or Liberation Sans. Container names that are too long wrap onto more lines, and the layout runs again.
- **One port per connector**: with the `spreadPorts` option, ELK gives each connector its own point along a side, so connectors meeting one side no longer run on top of each other.
- **Connector labels with reserved space**: ELK keeps room for each edge label. A label placed off its route is moved onto the route, where it covers no box.
- **3D scene unchanged**: `SceneGoldenSpec` checks a nested scene, laid out with the default options, against the output of 1.9.1.

## New Features

### Flat diagrams (`genius-uml-diagram`)

The new module depends on `scene`. `Diagram.draw` returns either a `SceneError` or a `Drawing`. Four writers turn a `Drawing` into output:

| Writer | Output | Needs |
|---|---|---|
| `Tikz.picture(drawing, palette)` | a `tikzpicture` in absolute centimetres | TikZ with `arrows.meta` (`Tikz.libraries`) |
| `Tikz.standalone(drawing, palette)` | a `standalone` document set in TeX Gyre Heros | pdflatex |
| `Svg.document(drawing, palette)` | an SVG document | nothing |
| `Png.image(drawing, dpi = 150, palette)` | PNG bytes, drawn with Java2D | nothing |

```scala
import fish.genius.uml.diagram.*

Diagram.draw(graph).map: drawing =>
  os.write.over(os.pwd / "landscape.tex", Tikz.standalone(drawing))
  os.write.over(os.pwd / "landscape.svg", Svg.document(drawing))
  os.write.over(os.pwd / "landscape.png", Png.image(drawing))
```

You can tune the layout and typography with `Diagram.Options` (node spacing, layer spacing, container padding, maximum boxes per row) and `Diagram.Style` (font families, font sizes, box widths, header height). `Drawing.overflow` counts the lines that are wider than their box. Only a single word longer than the widest box can cause this.

### What a box says

`SceneNode` has two new optional fields, `caption` (the element type and technology) and `note` (a line or two of description). `SceneEdge` has a new optional `label`. The 3D scene ignores all three.

```scala
SceneNode(
  id = SceneId("orders"),
  kind = NodeKind.Element(ShapeType.ApplicationComponent),
  label = "Order Service",
  caption = Some("Application component, Scala"),
  note = Some("Takes and confirms orders"),
)

SceneEdge(SceneId("e1"), SceneId("web"), SceneId("orders"), RelationshipType.Serving,
  label = Some("REST"))
```

A box is 3 cm wide, or 3.8 cm when it has a note or a name longer than two lines. It grows further for a single word that does not fit, and it is as tall as its lines need. The name and caption stay centred and keep clear of the icon. A container's name and caption sit in a header strip at its top.

### Notation and palettes

`Notation.outline`, `Notation.icon` and `Notation.ends` are total matches over `ShapeType` and `RelationshipType`. For example, composition is a filled diamond, aggregation an open diamond, realization a dashed line with an open triangle, serving an open arrowhead, and access a dotted line whose arrowhead points the way the data flows. A grouping is a dashed frame and a junction is a dot.

The layer colours are named `am-business`, `am-application`, `am-technology`, `am-physical`, `am-strategy`, `am-motivation` and `am-implementation`, plus `am-line` for lines and text. `Notation.palette` holds ArchiMate's suggested fills.

- The TikZ picture only *provides* these colours, so a document that already defines one keeps its own definition.
- A `palette` passed to a writer replaces the colours outright:

```scala
Svg.document(drawing, palette = Map("am-application" -> "#cfe8ff"))
```

### New scene layout options

`SceneLayout.Options` has four new options. All of them are off by default, which leaves the 3D scene unchanged:

- `footprint: SceneNode => Option[(Double, Double)]` sets the size of a leaf node in place of the fixed plinth.
- `headerAtBack: Boolean` puts a container's header at its top, for groupings too.
- `spreadPorts: Boolean` gives each connector its own ELK port along the side. `SceneRelationship.start` and `end` record where the connector leaves and arrives. This option does not keep floor space for passport panes, so leave it off for graphs that have passports.
- `labelSize: String => Option[(Double, Double)]` sets the size of a connector's label, so ELK keeps room for it. `SceneRelationship.label` (a new `SceneLabel`) records where the label went.

## Changelog

### Added
- New module `genius-uml-diagram` with `Diagram`, `Notation`, `Heads`, `Tikz`, `Svg` and `Png`.
- `Diagram.Style`, `Diagram.Options` and `Diagram.Drawing`, with the font measured from the first installed font family.
- `Png.image`, a PNG export that needs no LaTeX.
- `Notation.colours` validates a palette for all three writers: an entry counts only with a lower-case name and six hex digits (a leading `#` is allowed), so a malformed entry cannot break the SVG, LaTeX or PNG. Control characters in text become spaces.
- `SceneNode.caption`, `SceneNode.note` and `SceneEdge.label` (all optional).
- `SceneRelationship.start`, `SceneRelationship.end` and `SceneRelationship.label`, plus a new `SceneLabel` type with a JSON codec.
- New `SceneLayout.Options` fields: `footprint`, `headerAtBack`, `spreadPorts` and `labelSize`.
- `SceneGoldenSpec` with the `nested.json` golden file, which holds the default 3D layout to 1.9.1's output.
- `DiagramSpec`, covering:
  - the same output for the same input;
  - escaping of LaTeX and XML special characters;
  - palettes and junctions;
  - every arrowhead;
  - a header over two lines;
  - an empty drawing.
- A new DSL.md section, "Diagrams: ArchiMate drawn flat".
- New README and CLAUDE.md entries for the module.

### Changed
- `ElkPass` creates one ELK port per connector end when `spreadPorts` is on. Before, it shifted the connector ends after the layout ran.
- `ElkPass` adds edge labels to the ELK graph, centred and inline, when `labelSize` gives them a size.
- `SceneRoutes.finish` accepts start and end points placed by ELK.

## Module Changes

- **`diagram`** (`genius-uml-diagram`): new module, depends on `scene`.
- **`scene`** (`genius-uml-scene`): the new optional fields and layout options listed above. The default 3D output is unchanged.
- **`core`**, **`render`**, **`testkit`**, **`examples`**: no changes.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, zio-process 0.8.0
- Eclipse ELK 0.9.1
- PlantUML 1.2026.2
- os-lib 0.11.8, sourcecode 0.4.4
- Built with mill-release 0.5.0 and mill-scalafix 0.6.0

The new fields on `SceneNode`, `SceneEdge`, `SceneRelationship` and `SceneLayout.Options` all have defaults, so existing source code compiles unchanged. These are case classes, though, so their constructors, `copy` and `unapply` have changed, and code compiled against 1.9.1 must be recompiled. The scene's JSON gains the optional `start`, `end` and `label` fields on relationships, which are absent unless the flat-diagram options are on.

The flat diagram measures text in the installed fonts. On a machine with different fonts installed, box widths can differ by the width of a letter.
