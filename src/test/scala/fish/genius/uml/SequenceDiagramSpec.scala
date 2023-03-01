package fish.genius.uml

import fish.genius.lorem.SampleText
import fish.genius.uml.model.Specification
import fish.genius.uml.model.archimate.ActivityDiagram.activityDiagram
import fish.genius.uml.model.archimate.SequenceDiagram.sequenceDiagram
import org.scalatest.flatspec.AnyFlatSpec

class SequenceDiagramSpec extends AnyFlatSpec with CanPreview {
  it should "render sequence diagrams" in {
    val diagram = new Specification {
      uml {
        sequenceDiagram { sequence =>
          sequence.autonumber()
          val actor = sequence.actor(SampleText.title)
          sequence.box(SampleText.title, Some("#00FF00"))
          val participant = sequence.participant(SampleText.title)
          sequence.box(SampleText.title, Some("#FF0000"))
          val database = sequence.database(SampleText.title)
          val control = sequence.control(SampleText.title)
          sequence.endBox()
          sequence.endBox()

          sequence.step(SampleText.title)(actor)(participant)
          sequence.loopGroup(None)
          sequence.step(SampleText.title)(participant)(database)
          sequence.step(SampleText.title)(database)(actor)
          sequence.endGroup()
          sequence.step(SampleText.title)(actor)(control)
        }
      }
    }

    val eps = Renderer.eps(diagram)
    eps.map(_.getAbsolutePath).foreach(println)
    assert(eps.nonEmpty)
    eps.foreach(preview)
  }

}
