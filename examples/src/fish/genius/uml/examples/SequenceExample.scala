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

        val user = actor("User")
        box("Front-end", Some("#DAEEFF")):
          val _ = participant("Browser")
        val api  = participant("API")
        box("Back-end", Some("#DDFFDD")):
          val _ = control("Service")
          val _ = database("Postgres")

        val service = control("Service")
        val db      = database("Postgres")

        step("Click checkout")(user)(api)
        loopGroup(Some("for each item"))
        step("Reserve inventory")(api)(service)
        step("Update stock")(service)(db)
        endGroup()
        altGroup(Some("payment ok"))
        step("Confirm order")(api)(user)
        elseGroup(Some("payment failed"))
        step("Show error")(api)(user)
        endGroup()

  def run: ZIO[Any, Throwable, Any] = Renderer.run("sequence-example", doc)

end SequenceExample
