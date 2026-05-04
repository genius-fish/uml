package fish.genius.uml.examples

import zio.*

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.SkinParamProperty.*
import fish.genius.uml.dsl.activity.*

/** A small activity diagram showing the title / start / actions / stop pattern. */
object ActivityExample extends ZIOAppDefault:

  private val doc = block:
    uml:
      skinParam(
        "Activity",
        FontColor("black"),
        BorderColor("steelblue"),
        BorderThickness(2),
      )
      activityDiagram:
        title("Order checkout")
        start()
        action("Add items to basket")
        action("Click checkout")
        action("Confirm payment")
        action("Receive confirmation")
        stop()

  def run: ZIO[Any, Throwable, Any] = Renderer.run("activity-example", doc)

end ActivityExample
