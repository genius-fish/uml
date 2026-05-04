package fish.genius.uml.dsl.activity

import zio.test.*

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.SkinParamProperty.*
import fish.genius.uml.render.Emit

object ActivityDiagramSpec extends ZIOSpecDefault:

  def spec = suite("ActivityDiagram")(
    test("renders a complete activity diagram"):
      val n      = block:
        uml:
          skinParam(
            "Activity",
            FontColor("red"),
            BorderThickness(5),
            BorderColor("blue"),
          )
          activityDiagram:
            title("activity diagram")
            start()
            action("Hello")
            action("World")
            stop()
      val output = Emit.emit(n)
      assertTrue(
        output.contains("@startuml"),
        output.contains("title activity diagram"),
        output.contains("start"),
        output.contains(":Hello;"),
        output.contains(":World;"),
        output.contains("stop"),
        output.contains("@enduml"),
      )
  )

end ActivityDiagramSpec
