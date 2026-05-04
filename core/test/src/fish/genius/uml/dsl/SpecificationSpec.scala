package fish.genius.uml.dsl

import zio.test.*

import fish.genius.uml.render.Emit

object SpecificationSpec extends ZIOSpecDefault:

  def spec = suite("Specification (uml block)")(
    test("uml wraps body in @startuml / @enduml"):
      val n      = block:
        uml:
          statement("Alice -> Bob: Hello")
      val output = Emit.emit(n)
      assertTrue(
        output.startsWith("@startuml"),
        output.contains("Alice -> Bob: Hello"),
        output.trim.endsWith("@enduml"),
      )
    ,
    test("statement adds a literal PlantUML line"):
      val n      = block:
        uml:
          statement("skinparam monochrome true")
          statement("Alice -> Bob: test")
      val output = Emit.emit(n)
      assertTrue(
        output.contains("skinparam monochrome true"),
        output.contains("Alice -> Bob: test"),
      )
    ,
    test("expression generates an alias and emits the built line"):
      val n      = block:
        uml:
          val _ = expression(a => s"rectangle \"Box\" as ${a.value}")
      val output = Emit.emit(n)
      assertTrue(
        output.contains("rectangle \"Box\" as "),
        output.contains("@startuml"),
      )
    ,
    test("expressionWithBody wraps body in braces"):
      val n      = block:
        uml:
          expressionWithBody(a => s"package \"Pkg\" as ${a.value}"):
            statement("class Foo")
      val output = Emit.emit(n)
      assertTrue(
        output.contains("package \"Pkg\" as "),
        output.contains("{"),
        output.contains("class Foo"),
        output.contains("}"),
      )
    ,
    test("Alias.apply sanitises non-alphanumerics and lowercases"):
      val a = Alias("Hello World 123")
      // After digit replacement no digits should remain — purely lowercase letters.
      assertTrue(a.value.nonEmpty, a.value.forall(_.isLower))
    ,
    test("Alias.apply replaces digits with English words"):
      val a = Alias("test1")
      assertTrue(a.value.contains("one"))
    ,
    test("Alias.unsafe wraps a string verbatim"):
      val a = Alias.unsafe("MyAlias")
      assertTrue(a.value == "MyAlias")
    ,
    test("Alias.fresh allocates increasing values from the context counter"):
      val n   = block:
        uml:
          val first  = Alias.fresh
          val second = Alias.fresh
          statement(s"' first=${first.value} second=${second.value}")
      val out = Emit.emit(n)
      assertTrue(out.contains("first=a0"), out.contains("second=a1")),
  )

end SpecificationSpec
