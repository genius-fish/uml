package fish.genius.uml.examples

import zio.*

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.SkinParamProperty.*
import fish.genius.uml.dsl.archimate.*
import fish.genius.uml.dsl.archimate.Edge.*
import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*

/**
 * A small Archimate diagram showcasing shapes, boundaries, stereotypes,
 * legends, and relationships across multiple Archimate layers.
 */
object ArchimateExample extends ZIOAppDefault:

  given ArchimateConfiguration = ArchimateConfiguration()

  private val doc = block:
    uml:
      archimateDiagram:
        val phaseout = defineStereoType(
          "phaseout",
          Some("phasing out"),
          Some("#FF8888"),
          BorderColor("#FF0000"),
          BorderBold(),
          Shadowing(true),
        )

        legend("Domain legend", stereoTypes = List(phaseout))

        boundary("Customer-facing", shapeType = Some(StrategyCapability)):
          val customer = shape(BusinessActor, label("Customer"))
          val store    =
            shape(ApplicationComponent, label("Web store", Some("IT system")))
          val payment  =
            shape(
              ApplicationComponent,
              label("Legacy payment", Some("IT system"), description = Some("being retired")),
              stereoType = Some(phaseout),
            )

          relationship(Serving, Right, Some("uses"))(store)(customer)
          relationship(Composition)(store)(payment)

        boundary("Back-office", shapeType = Some(StrategyCapability)):
          val orders = shape(ApplicationComponent, label("Order management"))
          val crm    = shape(ApplicationComponent, label("CRM"))
          relationship(Flow, Down, Some("orders"))(crm)(orders)

  def run: ZIO[Any, Throwable, Any] = Renderer.run("archimate-example", doc)

end ArchimateExample
