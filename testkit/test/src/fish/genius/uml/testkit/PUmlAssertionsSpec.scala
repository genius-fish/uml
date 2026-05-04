package fish.genius.uml.testkit

import zio.test.*

import fish.genius.uml.ast.PUmlNode
import fish.genius.uml.dsl.*
import fish.genius.uml.testkit.PUmlAssertions.*

object PUmlAssertionsSpec extends ZIOSpecDefault:

  private val sample: PUmlNode = block:
    uml:
      statement("Alice -> Bob: Hello")
      headerBlock("rectangle Outer"):
        statement("Alice -> Bob: Inner")

  def spec = suite("PUmlAssertions")(
    test("countStatement counts substring occurrences across the tree"):
      assertTrue(
        sample.countStatement("Alice") == 2,
        sample.countStatement("Hello") == 1,
        sample.countStatement("Inner") == 1,
        sample.countStatement("absent") == 0,
      )
    ,
    test("hasBlock matches block headers by substring"):
      assertTrue(
        sample.hasBlock("rectangle"),
        !sample.hasBlock("not-here"),
      )
    ,
    test("statementsContaining returns matching lines in order"):
      val matches = sample.statementsContaining("Alice")
      assertTrue(matches == List("Alice -> Bob: Hello", "Alice -> Bob: Inner"))
    ,
    test("diagramKinds lists every wrapping diagram in document order"):
      val nested = block:
        diagram("uml")(statement("A"))
        diagram("gantt")(statement("B"))
      assertTrue(nested.diagramKinds == List("uml", "gantt"))
    ,
    test("Assertion combinator API also works"):
      assert(sample)(containsStatement("Alice"))
        && assert(sample)(containsBlock("rectangle"))
        && assert(sample)(statementCountIs("Alice", 2))
        && assert(sample)(hasDiagramKind("uml")),
  )

end PUmlAssertionsSpec
