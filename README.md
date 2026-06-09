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

## Rendered output

`PUmlEngine.render` writes into a scoped temp workspace that is deleted when
the `Scope` closes — copy the file out if you want to keep it. `DiagramOutput`
does exactly that, persisting renders to a stable, well-known location:

```
out/<format>/<category>/<name>.<suffix>
```

- `<format>` — the file suffix without the dot: `svg`, `png`, `eps`, `tex`.
- `<category>` — `test` for diagrams produced by the test suites,
  `examples` for the runnable examples (`DiagramOutput.Test` /
  `DiagramOutput.Examples`).
- `<root>` — the Mill workspace root (`MILL_WORKSPACE_ROOT`), falling back to
  the current working directory.

```scala
import fish.genius.uml.render.{DiagramOutput, PUmlEngine}
import net.sourceforge.plantuml.FileFormat
import zio.*

// Renders and persists to <root>/out/svg/examples/web-store.svg
val program = ZIO.scoped(
  DiagramOutput.save(diagram, DiagramOutput.Examples, "web-store", FileFormat.SVG)
)
```

So the test suites leave their diagrams under `out/svg/test`, `out/png/test`,
`out/eps/test`, … and the examples under `out/svg/examples`,
`out/png/examples`, …. The whole `out/` tree is git-ignored.

Run an example and have it render (needs the PlantUML jar, which ships with the
`render` module):

```sh
PLANTUML_AVAILABLE=1 ./mill examples.runMain \
  fish.genius.uml.examples.ArchimateExample
# PLANTUML_FORMAT=png|eps|svg|latex|latex_no_preamble selects the format
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
