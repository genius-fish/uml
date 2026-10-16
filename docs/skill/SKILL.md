---
name: library-genius-uml
description: How to use genius-uml — Genius Fish's Scala 3 library that draws ArchiMate views, sequence diagrams and BPMN processes natively. A SceneGraph is laid out by ELK and drawn as TikZ (LaTeX), SVG or PNG (Java2D), or as a 3D scene for Blender and three.js. Apply when writing or reviewing Scala that uses fish.genius.uml.* (ShapeType, RelationshipType, SceneGraph, SceneLayout, Diagram.draw, Sequence.draw, Activity.draw, Tikz, Svg, Png, SceneBuilder). Covers the modules, the ArchiMate vocabulary, the graph, the layout, the writers, the 3D scene and testing.
---

# genius-uml

ArchiMate views, sequences and processes for Scala 3, drawn natively. A view is a
value, a `SceneGraph`; `SceneLayout` lays it out with the Eclipse Layout Kernel (ELK);
`Diagram.draw` turns the layout into plain geometry (`Prim`) that `Tikz`, `Svg` and
`Png` write out. The same layout feeds a 3D `Scene` that the Blender builder (or
three.js) builds, so a document's diagram and a flyover show the same landscape in the
same places. No renderer is installed or shelled out to; only the 3D stills and videos
need Blender.

```
SceneGraph → SceneLayout (ELK) → Diagram.Drawing → Tikz | Svg | Png
                               → Scene (JSON)    → Blender | three.js
```

Artifacts publish under `fish.genius` (`genius-uml-core`, `-scene`, `-diagram`,
`-testkit`). Source: <https://github.com/genius-fish/uml>. Stack: Scala 3.8.3, ELK
0.12.0, ZIO 2.1.25.

## When to use this skill

Whenever you write or review Scala that imports `fish.genius.uml.*`: mapping a model
onto ArchiMate element and relationship types, building a `SceneGraph`, drawing it for
a document or a slide, drawing a sequence or a process, building a 3D scene, or testing
any of these.

## Modules

| Module    | Depends on | Holds |
| --------- | ---------- | ----- |
| `core`    | nothing | `ShapeType`, `ShapeGroup`, `RelationshipType`, `ShapeLabel`, `ArchimateConfiguration` |
| `scene`   | `core`, ELK, zio-json | `SceneGraph`, `SceneLayout`, the 3D `Scene`, `SceneBuilder` (Blender, three.js) |
| `diagram` | `scene` | `Diagram`, `Sequence`, `Activity`, and the writers `Tikz`, `Svg`, `Png` |
| `testkit` | ZIO Test, os-lib | `Golden` |

## The core mental model

- **Values in, values out.** A `SceneGraph` is nodes (`SceneNode`, nested by
  `children`) and edges (`SceneEdge`), keyed by `SceneId`. Every draw is a pure
  function returning `Either[SceneError, …]`; nothing does IO except your own write.
- **Exhaustive vocabulary.** `ShapeType` and `RelationshipType` are enums the scene
  and the diagram match on totally: a new case does not compile until it is drawn.
- **One layout, several writers.** `Diagram.draw(graph, options)` returns a
  `Diagram.Drawing`; `Tikz.picture` / `Tikz.standalone`, `Svg.document` and
  `Png.image` write the same drawing, text on the same baselines.

```scala
import fish.genius.uml.diagram.*
import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.scene.*

val graph = SceneGraph(
  "Web store",
  List(
    SceneNode(SceneId("customer"), NodeKind.Element(BusinessActor), "Customer"),
    SceneNode(SceneId("store"), NodeKind.Element(ApplicationComponent), "Web Store",
      caption = Some("[IT System: Next.js]")),
  ),
  List(SceneEdge(SceneId("serves"), SceneId("store"), SceneId("customer"), Serving)),
)

Diagram.draw(graph).map: drawing =>
  os.write.over(os.pwd / "web-store.tex", Tikz.standalone(drawing))
  os.write.over(os.pwd / "web-store.svg", Svg.document(drawing))
  os.write.over(os.pwd / "web-store.png", Png.image(drawing))
```

## Reference index — read the file that matches the task

- **`reference/core.md`**: the ArchiMate vocabulary (`ShapeType`, `ShapeGroup`,
  `RelationshipType`, `ShapeLabel`, `ArchimateConfiguration`).
- **`reference/diagram.md`**: the graph's fields, `Diagram.draw` and its options, the
  writers and palettes, `Sequence.draw`, `Activity.draw`.
- **`reference/scene.md`**: the 3D scene: `SceneLayout`, the JSON contract,
  `SceneBuilder`, the Blender builder and its options.
- **`reference/testing.md`**: `Golden` and how the repo tests drawings.

The repo's `DSL.md` is the full reference, section for section.

## Top gotchas

- Ids are yours: an edge to a `SceneId` that is not in the graph, or an id used twice, is an error
  (`SceneError.InvalidGraph`, every problem at once), not a silent drop.
- Nesting is the caller's decision: a node's `children` draw inside it as a container.
- Boxes are measured in the first installed font of `Style.fontFamilies`; a machine
  with other fonts can differ by a letter's width. Pin the font in tests that compare
  geometry.
- `Tikz.picture` needs TikZ's `arrows.meta` (`Tikz.libraries`); `Tikz.standalone`
  compiles on its own with pdflatex.
- `ArchimateConfiguration.elkLayout` does nothing; every layout is ELK's.
