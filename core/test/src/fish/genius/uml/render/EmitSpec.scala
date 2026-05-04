package fish.genius.uml.render

import zio.test.*

import fish.genius.uml.ast.PUmlNode

object EmitSpec extends ZIOSpecDefault:

  def spec = suite("Emit")(
    test("Statement emits the line followed by a newline"):
      val n = PUmlNode.Statement("Alice -> Bob: hi")
      assertTrue(Emit.emit(n) == "Alice -> Bob: hi\n")
    ,
    test("Sequence concatenates children in order with no separator"):
      val n = PUmlNode.Sequence(
        Vector(PUmlNode.Statement("a"), PUmlNode.Statement("b"))
      )
      assertTrue(Emit.emit(n) == "a\nb\n")
    ,
    test("Block wraps body in `header { ... }` lines"):
      val n = PUmlNode.Block("rectangle X", PUmlNode.Statement("foo"))
      assertTrue(Emit.emit(n) == "rectangle X {\nfoo\n}\n")
    ,
    test("Diagram wraps body in @startuml/@enduml"):
      val n = PUmlNode.Diagram("uml", PUmlNode.Statement("Alice -> Bob: hi"))
      assertTrue(Emit.emit(n) == "@startuml\nAlice -> Bob: hi\n@enduml\n")
    ,
    test("Diagram with custom kind wraps body in @startKIND/@endKIND"):
      val n = PUmlNode.Diagram("gantt", PUmlNode.Statement("[task]"))
      assertTrue(Emit.emit(n) == "@startgantt\n[task]\n@endgantt\n")
    ,
    test("Comment emits `' text` on its own line"):
      assertTrue(Emit.emit(PUmlNode.Comment("a remark")) == "' a remark\n")
    ,
    test("empty Sequence renders to empty string"):
      assertTrue(Emit.emit(PUmlNode.empty) == ""),
  )

end EmitSpec
