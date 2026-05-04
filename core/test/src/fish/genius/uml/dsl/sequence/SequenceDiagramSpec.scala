package fish.genius.uml.dsl.sequence

import zio.test.*

import fish.genius.uml.dsl.*
import fish.genius.uml.render.Emit

object SequenceDiagramSpec extends ZIOSpecDefault:

  def spec = suite("SequenceDiagram")(
    test("renders actors, boxes, groups and steps"):
      val n      = block:
        uml:
          sequenceDiagram:
            autonumber()
            teozRenderingEngine()
            val a  = actor("Alice")
            box("Backend", Some("#00FF00")):
              ()
            val p  = participant("Bob")
            box("DB box", Some("#FF0000")):
              ()
            val db = database("Postgres")
            val c  = control("Controller")
            step("Hello")(a)(p)
            loopGroup(None)
            step("Persist")(p)(db)
            step("Reply")(db)(a)
            endGroup()
            step("Notify")(a)(c)
      val output = Emit.emit(n)
      assertTrue(
        output.contains("autonumber"),
        output.contains("!pragma teoz true"),
        output.contains("actor \"Alice\""),
        output.contains("participant \"Bob\""),
        output.contains("database \"Postgres\""),
        output.contains("control \"Controller\""),
        output.contains("loop"),
        output.contains("end"),
        output.contains("Hello"),
        output.contains("Persist"),
        output.contains("Reply"),
        output.contains("Notify"),
      )
    ,
    test("box emits `box \"title\" color` ... `end box` around the body"):
      val n      = block:
        uml:
          sequenceDiagram:
            val _ = box("Front-end", Some("#1234AB")):
              participant("Web")
      val output = Emit.emit(n)
      assertTrue(
        output.contains("box \"Front-end\" #1234AB"),
        !output.contains("box \"Front-end\" #1234AB {"),
        output.contains("participant \"Web\""),
        output.contains("end box"),
      )
    ,
    test("box returns the body's result so participants can be captured"):
      val n      = block:
        uml:
          sequenceDiagram:
            val web = box("Front-end"):
              participant("Web")
            val api = participant("API")
            step("Hello")(web)(api)
      val output = Emit.emit(n)
      assertTrue(
        output.contains("box \"Front-end\""),
        output.contains("participant \"Web\""),
        output.contains("end box"),
        output.contains("participant \"API\""),
      ),
  )

end SequenceDiagramSpec
