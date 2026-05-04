package fish.genius.uml.testkit

import java.io.IOException

import zio.test.*

import zio.*

object GoldenSpec extends ZIOSpecDefault:

  def spec = suite("Golden")(
    test("update=true creates the golden file"):
      ZIO.scoped:
        for
          tmp     <- ZIO.acquireRelease(
            ZIO.attempt(os.temp.dir(prefix = "golden-test-")).orDie
          )(p => ZIO.attempt(os.remove.all(p)).orDie)
          result  <- Golden.assertMatches("first", "hello", tmp, update = true)
          written <- ZIO.attempt(os.read(tmp / "golden" / "first.puml")).orDie
        yield result && assertTrue(written == "hello")
    ,
    test("update=false against missing file fails with FileNotFoundException"):
      ZIO.scoped:
        for
          tmp <- ZIO.acquireRelease(
            ZIO.attempt(os.temp.dir(prefix = "golden-test-")).orDie
          )(p => ZIO.attempt(os.remove.all(p)).orDie)
          err <- Golden.assertMatches("missing", "anything", tmp, update = false).flip
        yield assert(err)(Assertion.isSubtype[IOException](Assertion.anything))
    ,
    test("update=false against an existing file passes when bytes match"):
      ZIO.scoped:
        for
          tmp    <- ZIO.acquireRelease(
            ZIO.attempt(os.temp.dir(prefix = "golden-test-")).orDie
          )(p => ZIO.attempt(os.remove.all(p)).orDie)
          _      <- Golden.assertMatches("ok", "expected", tmp, update = true)
          result <- Golden.assertMatches("ok", "expected", tmp, update = false)
        yield result
    ,
    test("update=false against an existing file produces a non-passing result when bytes differ"):
      ZIO.scoped:
        for
          tmp    <- ZIO.acquireRelease(
            ZIO.attempt(os.temp.dir(prefix = "golden-test-")).orDie
          )(p => ZIO.attempt(os.remove.all(p)).orDie)
          _      <- Golden.assertMatches("nope", "expected", tmp, update = true)
          result <- Golden.assertMatches("nope", "actual", tmp, update = false)
        yield assertTrue(!result.isSuccess),
  )

end GoldenSpec
