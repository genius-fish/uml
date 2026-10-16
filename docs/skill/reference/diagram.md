# diagram — drawn flat: TikZ, SVG, PNG

Package `fish.genius.uml.diagram` (the `genius-uml-diagram` module, on top of
`scene`). DSL.md, "Diagrams: ArchiMate drawn flat", has every detail.

## The graph

`SceneGraph(name, nodes, edges, flows = Nil, …)` from `fish.genius.uml.scene`.

- `SceneNode(id, kind, label, children, passport, caption, note, fill, badges, logo,
  outline, tag, faded)`: `kind` is `NodeKind.Element(shapeType)`, `Grouping`,
  `AndJunction` or `OrJunction`. `caption` is the type line (`[IT System: SAP]`),
  `note` a line or two under it. `badges` are signs at the box's foot, `logo` a brand
  mark, `tag` a corner code, `fill` a colour of its own, `faded` draws it pale.
- `SceneEdge(id, source, target, relationship, label, colour, …)`: a relationship
  between two node ids.

## Drawing

```scala
Diagram.draw(graph, Diagram.Options()): Either[SceneError, Diagram.Drawing]
```

`Options(style, nodeSpacing, layerSpacing, containerPadding, maxPerRow, pageWidth,
direction)`. `pageWidth` (cm, default 13) folds a wide row rather than scaling the
diagram down; `direction = SceneLayout.Direction.Right` lays a chain out left to
right for a slide. `Drawing.overflow` counts lines wider than their room.

## Writers

| Writer | Output |
| --- | --- |
| `Tikz.picture(drawing, palette)` | a `tikzpicture` in absolute cm; needs `Tikz.libraries` |
| `Tikz.standalone(drawing, palette)` | a `standalone` document, pdflatex |
| `Svg.document(drawing, palette)` | an SVG document |
| `Png.image(drawing, dpi, palette)` | PNG bytes, drawn with Java2D |

A `palette` (`Map[String, String]`, colour name to hex) overrides the layer colours
(`am-business`, `am-application`, `am-paper`, …) for one output.

## Sequences and activities

- `Sequence.draw(Sequence.Graph(name, lifelines, steps), options)`: parts as heads
  with dashed lifelines, `Step.Send` messages (`Kind.Call`, `Reply`, `Signal`) and
  `Step.Loop` frames.
- `Activity.draw(Activity.Graph(name, lanes, nodes, flows), options)`: a BPMN process
  in swimlanes at the Analytic conformance level (events, gateways, tasks and their
  markers, sub-processes, data, message flows).

Both return a `Diagram.Drawing` for the same writers.
