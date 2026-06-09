# testing — testkit

Package `fish.genius.uml.testkit` (the `genius-uml-testkit` module). The pure-AST
design makes testing fast: assert against the rendered string or the tree, no
PlantUML jar required. Built on ZIO Test.

## Structural assertions

Extension methods on `PUmlNode` (use with `assertTrue`):

| Method | Returns |
| --- | --- |
| `countStatement(needle): Int` | statements whose text contains `needle` |
| `hasBlock(headerNeedle): Boolean` | whether some block header contains `headerNeedle` |
| `statements: List[String]` | all statements in document order |
| `statementsContaining(needle): List[String]` | statements containing `needle` |
| `diagramKinds: List[String]` | all diagram kinds in order (e.g. `List("uml")`) |

ZIO Test `Assertion[PUmlNode]` combinators (use with `assert(value)(…)`):
`containsStatement(needle)`, `containsBlock(headerNeedle)`,
`statementCountIs(needle, expected)`, `hasDiagramKind(kind)`.

```scala
import fish.genius.uml.testkit.PUmlAssertions.*

assertTrue(
  diagram.countStatement("@startuml") == 1,
  diagram.statementsContaining("rectangle").nonEmpty,
  diagram.diagramKinds == List("uml"),
)
```

## Golden files

`Golden.assertMatches` compares a rendered string to
`<resourcesDir>/golden/<id>.<extension>` (default extension `puml`):

```scala
import fish.genius.uml.testkit.Golden

test("a small archimate diagram matches the golden"):
  Golden.assertMatches(
    id           = "archimate-minimal",
    actual       = Emit.emit(diagram),
    resourcesDir = Golden.moduleResources("core"),
    extension    = "puml",
  )
```

Signature: `assertMatches(id, actual, resourcesDir, extension="puml",
update=shouldUpdate)`. Set `ZIO_TEST_UPDATE_GOLDEN=1` to (re)generate the golden
after an intentional output change (`Golden.shouldUpdate` reads it; when set, the
file is written and the assertion passes vacuously). `Golden.moduleResources(module)`
resolves the per-module test-resources dir; `Golden.workspaceRoot` honours
`MILL_WORKSPACE_ROOT` (else cwd).

## Rendering in tests without PlantUML

Provide `PUmlEngine.test` instead of `live` to exercise the render path without
invoking PlantUML — it writes the `.puml` source to a scoped temp file. Most
tests don't even need that: assert on `Emit.emit(doc)` directly.
