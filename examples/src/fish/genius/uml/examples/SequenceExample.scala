package fish.genius.uml.examples

import zio.*

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.sequence.*

/** A sequence diagram showing actors, boxes, message steps, and group blocks. */
object SequenceExample extends ZIOAppDefault:

  private val doc = block:
    uml:
      sequenceDiagram:
        autonumber()
        teozRenderingEngine()

        val user             = actor("User")
        val browser          = box("Front-end", Some("#DAEEFF")):
          participant("Browser")
        val api              = participant("API")
        val (service, db)    = box("Back-end", Some("#DDFFDD")):
          (control("Service"), database("Postgres"))

        step("Open store")(user)(browser)
        step("Click checkout")(browser)(api)
        loopGroup(Some("for each item"))
        step("Reserve inventory")(api)(service)
        step("Update stock")(service)(db)
        endGroup()
        altGroup(Some("payment ok"))
        step("Confirm order")(api)(browser)
        elseGroup(Some("payment failed"))
        step("Show error")(api)(browser)
        endGroup()

  def run: ZIO[Any, Throwable, Any] = Renderer.run("sequence-example", doc)

end SequenceExample
