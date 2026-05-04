package fish.genius.uml.dsl

import zio.test.*

import fish.genius.uml.dsl.SkinParamProperty.*
import fish.genius.uml.render.Emit

object SkinSpec extends ZIOSpecDefault:

  def spec = suite("skin DSL")(
    test("skinParam emits a `skinparam <name> { props }` block"):
      val n      = block:
        uml:
          skinParam(
            "Activity",
            FontColor("red"),
            BorderThickness(5),
            BorderColor("blue"),
          )
      val output = Emit.emit(n)
      assertTrue(
        output.contains("skinparam Activity {"),
        output.contains("FontColor red"),
        output.contains("BorderThickness 5"),
        output.contains("BorderColor blue"),
      )
    ,
    test("skinParam with no properties emits nothing"):
      val n      = block:
        uml:
          skinParam("Activity")
      val output = Emit.emit(n)
      // Only @startuml/@enduml — no skinparam line at all.
      assertTrue(!output.contains("skinparam"))
    ,
    test("border-style enum cases all map to BorderStyle"):
      assertTrue(
        BorderDashed().name == "BorderStyle",
        BorderDotted().name == "BorderStyle",
        BorderPlain().name == "BorderStyle",
        BorderBold().name == "BorderStyle",
        BorderDashed().value == "dashed",
        BorderDotted().value == "dotted",
        BorderPlain().value == "plain",
        BorderBold().value == "bold",
      )
    ,
    test("svgIcon emits a `sprite $name <svg>` line and returns its alias"):
      val n      = block:
        uml:
          val _ = svgIcon("<svg></svg>", name = Some("api"))
      val output = Emit.emit(n)
      assertTrue(output.contains("sprite $api <svg></svg>")),
  )

end SkinSpec
