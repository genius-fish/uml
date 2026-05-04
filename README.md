# genius-uml

A composable, type-safe PlantUML DSL and renderer for Scala 3.

## Modules

| Module     | Purpose                                                                |
| ---------- | ---------------------------------------------------------------------- |
| `core`     | Pure DSL + AST. No ZIO, no filesystem, no subprocess.                  |
| `render`   | ZIO 2 service that turns AST into SVG / PNG / EPS / LaTeX via PlantUML.|
| `testkit`  | Golden-file and structural assertions for ZIO Test.                    |
| `examples` | Runnable end-to-end showcases (Archimate, sequence, activity).         |

## Stack

- Mill 1.1.5
- Scala 3.8.3
- ZIO 2.1.25
- PlantUML 1.2025.2

## Build

```sh
make compile        # ./mill __.compile
make test           # ./mill __.test.testForked
make fmt            # ./mill __.reformat
make fix            # ./mill __.fix       (scalafix)
make validate       # fix + fmt + compile + test
make publishLocal   # to ~/.ivy2/local
```

## Quick start

```scala
import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.archimate.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.render.{Emit, PUmlEngine}
import zio.*

given ArchimateConfiguration = ArchimateConfiguration()

val diagram = block:
  uml:
    archimate:
      val customer  = shape(BusinessActor, label("Customer"))
      val webStore  = shape(ApplicationComponent, label("Web Store"))
      relationship(RelationshipType.Serving)(webStore)(customer)

// 1) Inspect the rendered PlantUML source as a String:
println(Emit.emit(diagram))

// 2) Render to a real SVG (requires the PlantUML jar on the classpath):
val program = ZIO.scoped(PUmlEngine.renderSvg(diagram))
Unsafe.unsafe(implicit u => Runtime.default.unsafe.run(
  program.provide(PUmlEngine.live)
))
```

## Testing

Add `testkit` to a test module's deps and you get:

```scala
import fish.genius.uml.testkit.PUmlAssertions.*
import fish.genius.uml.testkit.Golden

assertTrue(
  diagram.countStatement("@startuml") == 1,
  diagram.statementsContaining("rectangle").nonEmpty,
)

// Golden-file assertion. Set ZIO_TEST_UPDATE_GOLDEN=1 to regenerate:
test("a small archimate diagram matches the golden"):
  Golden.assertMatches(
    id           = "archimate-minimal",
    actual       = Emit.emit(diagram),
    resourcesDir = Golden.moduleResources("core"),
    extension    = "puml",
  )
```

## License

MIT — see [LICENSE](LICENSE).
