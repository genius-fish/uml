package fish.genius.uml.dsl.archimate

import zio.test.*

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.SkinParamProperty.*
import fish.genius.uml.dsl.archimate.Edge.*
import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.render.Emit

object ArchimateSpec extends ZIOSpecDefault:

  given ArchimateConfiguration = ArchimateConfiguration()

  def spec = suite("Archimate DSL")(
    test("renders a small business-context diagram"):
      val n      = block:
        uml:
          archimateDiagram:
            boundary(
              "Maintain rolling stock, fixed installations, energy and patrimony",
              shapeType = Some(StrategyCapability),
            ):
              val _ = shape(BusinessFunction, label("Manage masterdata", Some("Business Function")))
              val _ = shape(ApplicationComponent, label("Hello", Some("IT System")))
              boundary("Maintain, repair and revise", shapeType = Some(BusinessFunction)):
                val _ = shape(BusinessFunction, label("Repair"))
                val _ = shape(BusinessFunction, label("Manage work orders"))
                val _ = shape(BusinessFunction, label("Track warranty claims"))
            boundary("Manage information and knowledge", shapeType = Some(StrategyCapability)):
              val _ = shape(BusinessFunction, label("Manage information"))
      val output = Emit.emit(n)
      assertTrue(
        output.contains("@startuml"),
        output.contains("rectangle"),
        output.contains("archimate/business-function"),
        output.contains("archimate/application-component"),
        output.contains("Repair"),
        output.contains("Manage information"),
        output.contains("@enduml"),
      )
    ,
    test("relationship emits the right connector token"):
      val n      = block:
        uml:
          archimateDiagram:
            val a = shape(BusinessActor, label("Alice"))
            val b = shape(BusinessRole, label("Cashier"))
            relationship(Composition, Down, Some("composes"))(a)(b)
      val output = Emit.emit(n)
      assertTrue(
        output.contains("*-DOWN-"),
        output.contains(": \"composes\""),
      )
    ,
    test("defineStereoType emits a `skinparam rectangle<<NAME>>` block"):
      val n      = block:
        uml:
          archimateDiagram:
            val _ = defineStereoType(
              "phaseout",
              Some("phasing out!"),
              Some("#FF0000"),
              BorderColor("#FF0000"),
              BorderBold(),
              Shadowing(true),
            )
      val output = Emit.emit(n)
      assertTrue(
        output.contains("skinparam rectangle<<PHASEOUT>>"),
        output.contains("BorderColor #FF0000"),
        output.contains("BorderStyle bold"),
        output.contains("Shadowing true"),
      )
    ,
    test("legend emits the `legend bottom right` ... `endlegend` lines"):
      val n      = block:
        uml:
          archimateDiagram:
            val phaseout = defineStereoType("phaseout", Some("phasing out"), Some("#FF0000"))
            legend("Demo Legend", "#GhostWhite", List(phaseout))
      val output = Emit.emit(n)
      assertTrue(
        output.contains("legend bottom right"),
        output.contains("Demo Legend"),
        output.contains("endlegend"),
      )
    ,
    test("shape registry covers every Archimate element"):
      // Just enumerate them — if any is missing the call would fail.
      val n      = block:
        uml:
          archimateDiagram:
            ShapeType.*.foreach: st =>
              val _ = shape(st, label(st.toString))
      val output = Emit.emit(n)
      // 60+ rectangles emitted — pick a couple from each layer to spot-check.
      assertTrue(
        output.contains("archimate/application-component"),
        output.contains("archimate/strategy-capability"),
        output.contains("archimate/business-actor"),
        output.contains("archimate/technology-node"),
        output.contains("archimate/physical-facility"),
        output.contains("archimate/motivation-stakeholder"),
        output.contains("archimate/implementation-workpackage"),
      ),
  )

end ArchimateSpec
