package fish.genius.uml

import fish.genius.uml.model.Specification
import fish.genius.uml.model.archimate.ActivityDiagram.activityDiagram
import org.scalatest.flatspec.AnyFlatSpec

class ActivityDiagramSpec extends AnyFlatSpec with CanPreview {
  it should "render activity diagrams" in {
    val diagram = new Specification {
      uml {
        activityDiagram { activity =>
          activity.title("activity diagram")
          activity.start()
          activity.action("Hello")
          activity.action("World")
          activity.stop()
        }
      }
    }

    val eps = Renderer.eps(diagram)
    eps.map(_.getAbsolutePath).foreach(println)
    assert(eps.nonEmpty)
    eps.foreach(preview)
  }

}
