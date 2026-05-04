package fish.genius.uml.dsl

import zio.test.*

import fish.genius.uml.render.Emit

object PrimitivesSpec extends ZIOSpecDefault:

  def spec = suite("primitives")(
    test("statement emits its value with a trailing newline"):
      val n = block:
        statement("hello")
      assertTrue(Emit.emit(n) == "hello\n")
    ,
    test("comment emits `' text` on its own line"):
      val n = block:
        comment("a remark")
      assertTrue(Emit.emit(n) == "' a remark\n")
    ,
    test("statements emits one line per non-empty input line"):
      val n = block:
        statements("a\nb\nc")
      assertTrue(Emit.emit(n) == "a\nb\nc\n")
    ,
    test("headerBlock wraps body in `header { ... }`"):
      val n = block:
        headerBlock("rectangle X"):
          statement("foo")
      assertTrue(Emit.emit(n) == "rectangle X {\nfoo\n}\n")
    ,
    test("when emits the body when cond is true"):
      val n = block:
        when(true)(statement("yes"))
      assertTrue(Emit.emit(n) == "yes\n")
    ,
    test("when emits nothing when cond is false"):
      val n = block:
        when(false)(statement("no"))
      assertTrue(Emit.emit(n) == ""),
  )

end PrimitivesSpec
