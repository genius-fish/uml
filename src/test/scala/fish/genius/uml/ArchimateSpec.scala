package fish.genius.uml

import fish.genius.io.{Command, Shell}
import fish.genius.lorem.SampleText
import fish.genius.uml.model.{Alias, Specification}
import fish.genius.uml.model.archimate.Archimate._
import fish.genius.uml.model.archimate.{
  ApplicationComponent,
  ApplicationDataObject,
  ArchimateConfiguration,
  Flow,
  NoDirection,
  RelationshipType,
  ShapeLabel,
  ShapeType,
  StrategyCapability,
  StrategyResource,
  Up
}
import org.scalatest.flatspec.AnyFlatSpec

import java.io.File
import javax.management.relation.RelationType

class ArchimateSpec extends AnyFlatSpec with CanPreview {
  it should "render system integration diagrams" in {
    implicit val config = ArchimateConfiguration()
    val diagram = new Specification {
      uml {
        archimate { archi =>
          archi.boundary(SampleText.title) {
            archi.boundary(SampleText.title, Some(SampleText.title)) {
              val components =
                ShapeType.*.map(shapeType =>
                  archi.shape(shapeType, randomLabel())
                )

              def sampleComponent: Alias =
                components(SampleText.int(components.length) - 1)

              RelationshipType.*.foreach(relationshipType =>
                archi.relationship(
                  relationshipType,
                  NoDirection,
                  Some(relationshipType.getClass.getSimpleName)
                )(sampleComponent)(
                  sampleComponent
                )
              )
            }
          }

        }

      }

    }

    println(diagram)
    val eps = Renderer.eps(diagram)
    assert(eps.nonEmpty)
    eps.foreach(preview)
  }

  def randomLabel()(implicit
      configuration: ArchimateConfiguration
  ): ShapeLabel = ShapeLabel(
    SampleText.title,
    Some(SampleText.word),
    Some(SampleText.title),
    Some(SampleText.description)
  )

}
