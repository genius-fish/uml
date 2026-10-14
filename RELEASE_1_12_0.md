# Release 1.12.0

**Release Date:** 2026-10-14

This release adds native sequence diagrams to `genius-uml-diagram`. The new `Sequence.draw` lays out a sequence of messages between parts and draws it as the module's `Prim` geometry, in the same print look as the flat ArchiMate diagrams that `Diagram` draws. The existing `Tikz`, `Svg` and `Png` writers output it unchanged, so a document's sequence diagrams match its ArchiMate views without going through PlantUML.

The layout keeps diagrams readable on the page. Columns are spaced as far apart as their labels need, labels stay off the edges of fields, lifelines break where text crosses them, and heads and labels wrap narrower until the diagram fits `Options.pageWidth`. The output of `Diagram` itself has not changed.

## Highlights

- **Native sequence diagrams**: `Sequence.draw(graph, options)` returns a `Diagram.Drawing` that you write with `Tikz`, `Svg` or `Png`.
- **One visual style with ArchiMate views**: heads use the layer colour and icon of their ArchiMate shape, and the parts of one system share a field.
- **Fits the page**: labels and heads wrap until the diagram fits the configured page width.

## New Features

### Sequence diagrams (`fish.genius.uml.diagram.Sequence`)

You describe a sequence as a `Graph` of `Lifeline`s and `Step`s and draw it with `Sequence.draw`:

```scala
import fish.genius.uml.diagram.*
import fish.genius.uml.diagram.Sequence.*
import fish.genius.uml.dsl.archimate.ShapeType

val shop = Some(Group("Web Shop", Some(ShapeType.ApplicationComponent)))
val drawing = Sequence.draw(
  Graph(
    "Checkout",
    List(
      Lifeline("customer", "Customer", ShapeType.BusinessActor, Some("Business Actor")),
      Lifeline("api", "Order API", ShapeType.ApplicationComponent, Some("[Container: Scala]"), shop),
      Lifeline("db", "Order Store", ShapeType.ApplicationComponent, None, shop),
    ),
    List(
      Step.Send("customer", "api", "Place the order", Kind.Call, Some("HTTPS {Order}")),
      Step.Loop("until stored", List(Step.Send("api", "db", "Store it"))),
      Step.Send("api", "customer", "Order confirmed", Kind.Reply),
    ),
  )
)
os.write.over(os.pwd / "checkout.tex", Tikz.standalone(drawing))
```

- **Parts (`Lifeline`)**: each part has a head box in its shape's layer colour, with the icon in the corner, the name and an optional caption. A fine dashed lifeline in the box's edge colour runs below it. Adjacent parts in the same `Group` share a field: a container of the group's shape, or a dashed grouping when the group has no shape. The group's name appears in the field's header.
- **Messages (`Step.Send`)**: each message is a numbered label above a horizontal line, with an optional `detail` line (technology, payload) set below it in a quieter italic.
  - `Kind.Call` is solid with a filled arrowhead.
  - `Kind.Reply` is dashed with an open arrowhead.
  - `Kind.Signal` is solid with an open arrowhead.
  - A message from a part to itself turns back on its own lifeline.
  - A message to an unknown part id is left out.
- **Loops (`Step.Loop`)**: a loop is a frame round its messages, tagged *loop* with its condition beside the tag. Nested frames sit further in, and an empty loop is left out.
- **Options**: `style` (the shared `Diagram.Style`), `pageWidth` (default 13.0), `numbered` (default `true`), `messageSize`, `detailSize` and `labelWidth`.

## Changelog

### Added
- `Sequence` in `genius-uml-diagram`, with `Graph`, `Lifeline`, `Group`, `Step.Send`, `Step.Loop`, `Kind` (`Call`, `Reply`, `Signal`), `Options` and `draw`.
- Layout rules for sequence diagrams:
  - Column spacing is set by the labels, shortest spans first.
  - Labels keep off the edges of fields.
  - Lifelines break under text.
  - Labels and heads wrap until the diagram fits `pageWidth`.
- `SequenceSpec`, which covers numbering and arrowheads per kind, vertical order, label placement, loop frames and nesting, self and unknown messages, grouped fields, page-width wrapping, deterministic output through TikZ, SVG and PNG, and layer colours.
- Sequence examples in the `DiagramGallery` test sample.
- A DSL.md section, "Sequences: messages between parts".

### Changed
- `Diagram`'s internal `Line`, `stack` and `place` helpers are now `private[diagram]`, so `Sequence` can share them. The public API and `Diagram`'s output are unchanged.

## Module Changes

- **`genius-uml-diagram`** (`diagram`): new `Sequence` drawing, internal helpers shared with `Diagram`, new tests.
- **Documentation**: `DSL.md`.
- `core`, `render`, `scene` and `testkit` are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-process 0.8.0, zio-json 0.7.3
- PlantUML 1.2026.2
- Eclipse ELK 0.9.1
- os-lib 0.11.8, sourcecode 0.4.4
- Built with Mill, using `fish.genius::mill-release` 0.5.0 and `mill-scalafix` 0.6.0

No breaking changes: the release only adds API, and `Diagram`'s output is unchanged.
