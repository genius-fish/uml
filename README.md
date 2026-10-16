# genius-uml

ArchiMate views, sequences and processes for Scala 3, laid out by ELK and drawn natively
as TikZ (for LaTeX), SVG and PNG, or in 3D for Blender and three.js.

A view is a value, a `SceneGraph`. `SceneLayout` lays it out with the Eclipse Layout
Kernel; `Diagram.draw` turns the layout into plain geometry, which `Tikz`, `Svg` and
`Png` (Java2D) write out. The same layout feeds the 3D scene, so a document's diagram
and a flyover show the same landscape in the same places.

## Modules

| Module     | Purpose |
| ---------- | ------- |
| `core`     | The ArchiMate vocabulary: element, layer and relationship types, box labels. No dependencies. |
| `scene`    | `SceneGraph`, the ELK layout, and the 3D scene with its Blender builder: stills, highlights, passports on glass panes, and flyovers through a flow as video. |
| `diagram`  | The graph drawn flat (`Diagram`), plus sequence (`Sequence`) and activity (`Activity`) diagrams in the same look, written as TikZ, SVG or PNG. |
| `testkit`  | Golden-file assertions for ZIO Test. |
| `examples` | `DiagramExample` and `SceneExample`, runnable. |

## Stack

- Mill 1.1.5
- Scala 3.8.3
- ELK 0.12.0
- ZIO 2.1.25 (scene's JSON, testkit)

## Build

```sh
make compile        # ./mill __.compile
make test           # ./mill __.test.testForked
make fmt            # ./mill __.reformat
make fix            # ./mill __.fix       (scalafix)
make validate       # fix + fmt + compile + test
make examples       # runs DiagramExample and SceneExample into out/
make publishLocal   # to ~/.ivy2/local
```

## Quick start

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
  os.write.over(os.pwd / "web-store.tex", Tikz.standalone(drawing)) // pdflatex
  os.write.over(os.pwd / "web-store.svg", Svg.document(drawing))
  os.write.over(os.pwd / "web-store.png", Png.image(drawing))
```

`Tikz.picture` gives the bare `tikzpicture` to set inside a document of your own.
[DSL.md](DSL.md) is the reference: the graph, the look, badges, logos, legends, the
notation, the layout options, sequences, activities and the 3D scene.

## Testing

`testkit` compares a rendered string against a checked-in fixture:

```scala
import fish.genius.uml.testkit.Golden

test("the landscape's TikZ matches the golden"):
  Golden.assertMatches(
    id           = "landscape",
    actual       = Tikz.picture(drawing),
    resourcesDir = Golden.moduleResources("diagram"),
    extension    = "tex",
  )
```

Set `ZIO_TEST_UPDATE_GOLDEN=1` to regenerate the fixtures.

## License

MIT, see [LICENSE](LICENSE).
