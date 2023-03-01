package fish.genius.uml.model.archimate

import fish.genius.uml.model.{
  Alias,
  CanBuildSpecification,
  SpecificationBuilder,
  archimate
}

class Archimate()(implicit
    specificationBuilder: SpecificationBuilder,
    archimateConfiguration: ArchimateConfiguration
) extends CanBuildSpecification {
  private def init(): Unit = {
    statement(
      s"""!define TECHN_FONT_SIZE ${archimateConfiguration.technicalFontSize}
         |!define ELEMENT_FONT_COLOR #FFFFFF
         |
         |skinparam defaultTextAlignment center
         |skinparam wrapWidth 250
         |skinparam maxMessageSize 150
         |skinparam StereotypeAlignment right
         |
         |skinparam Arrow {
         |    Color #000000
         |    FontColor #000000
         |    FontSize 12
         |}
         |skinparam Folder<<grouping>> {
         |    Shadowing true
         |    StereotypeFontSize 0
         |    FontColor #444444
         |    BorderColor #444444
         |    BorderStyle dashed
         |}
         |skinparam Folder<<group>> {
         |    Shadowing true
         |    StereotypeFontSize 0
         |    FontColor #444444
         |    BackgroundColor lightgrey
         |}
         |skinparam rectangle<<boundary>> {
         |    Shadowing false
         |    StereotypeFontSize 0
         |    FontColor #444444
         |    BorderColor #444444
         |    BorderStyle dashed
         |}
         |skinparam node {
         |  StereotypeAlignment right
         |}
         |skinparam cloud {
         |  StereotypeAlignment right
         |}
         |skinparam frame {
         |  StereotypeAlignment right
         |}
         |skinparam archimate {
         |  StereotypeAlignment right
         |  RoundCorner<<strategy-capability>> 25
         |  RoundCorner<<strategy-course-of-action>> 25
         |  RoundCorner<<strategy-value-stream>> 25
         |  RoundCorner<<business-process>> 25
         |  RoundCorner<<business-event>> 25
         |  RoundCorner<<business-function>> 25
         |  RoundCorner<<business-interaction>> 25
         |  RoundCorner<<business-event>> 25
         |  RoundCorner<<business-service>> 25
         |  RoundCorner<<application-function>> 25
         |  RoundCorner<<application-interaction>> 25
         |  RoundCorner<<application-process>> 25
         |  RoundCorner<<application-event>> 25
         |  RoundCorner<<application-service>> 25
         |  RoundCorner<<technology-function>> 25
         |  RoundCorner<<technology-process>> 25
         |  RoundCorner<<technology-interaction>> 25
         |  RoundCorner<<technology-event>> 25
         |  RoundCorner<<technology-infra-service>> 25
         |  RoundCorner<<implementation-workpackage>> 25
         |  RoundCorner<<implementation-event>> 25
         |  DiagonalCorner<<motivation-stakeholder>> 12
         |  DiagonalCorner<<motivation-driver>> 12
         |  DiagonalCorner<<motivation-assessment>> 12
         |  DiagonalCorner<<motivation-goal>> 12
         |  DiagonalCorner<<motivation-outcome>> 12
         |  DiagonalCorner<<motivation-principle>> 12
         |  DiagonalCorner<<motivation-requirement>> 12
         |  DiagonalCorner<<motivation-constraint>> 12
         |}
         |skinparam usecase {
         |  BorderColor #000000
         |}""".stripMargin
    )
  }

  def label(
      name: String,
      componentType: Option[String] = None,
      componentTypeDetails: Option[String] = None,
      description: Option[String] = None
  ): ShapeLabel =
    ShapeLabel(name, componentType, componentTypeDetails, description)

  def color(hex: String): ShapeColor = ShapeColor(
    Some(
      if (hex.startsWith("#")) hex else (s"#$hex")
    )
  )

  type ArchetypeColor = String
  type ArchetypePrefix = String
  type Archetype = String

  private val _shape
      : ArchetypeColor => ArchetypePrefix => Archetype => ShapeColor => ShapeLabel => Alias =
    defaultColor =>
      archetypePrefix =>
        archetype =>
          color =>
            label =>
              expression(alias =>
                s"archimate ${color.hex
                    .getOrElse(defaultColor)} \"$label\" <<$archetypePrefix-$archetype>> as $alias"
              )

  private val shapes = for {
    shapeType <- ShapeType.*
    s <- Some(
      _shape(shapeType.group.defaultColor)(shapeType.group.prefix)(
        shapeType.name
      )
    )
  } yield (shapeType, s)

  def shape(
      shapeType: ShapeType,
      label: ShapeLabel,
      color: ShapeColor = ShapeColor()
  ): Alias = shapes
    .find(t => t._1 == shapeType)
    .map(t => t._2)
    .map(f => f(color))
    .map(f => f(shapeType.decorate(label)))
    .get

  def boundary(label: String, boundaryType: Option[String] = None)(
      body: => Any
  ): Alias =
    expressionWithBody(alias =>
      s"rectangle \"==$label${boundaryType.map(bt => "\\n<size:" + archimateConfiguration.technicalFontSize + ">[" + bt + "]</size>").getOrElse("")}\" <<boundary>> as $alias"
    )(body)

  type RelationshipLabel = Option[String]
  type EdgePrefix = String
  type EdgeSuffix = String
  type Relationship = Alias => Alias => Unit
  type NamedRelationship = RelationshipLabel => Relationship
  type DirectedRelationship = Edge => NamedRelationship

  private val _relationship
      : EdgePrefix => EdgeSuffix => Edge => NamedRelationship = {
    edgePrefix => edgeSuffix => edge => label => source => target =>
      statement(
        s"$source ${edge.asString(edgePrefix, edgeSuffix)} $target : \"${label.getOrElse("")}\""
      )
  }

  private val relationships: List[(RelationshipType, Edge, NamedRelationship)] =
    for {
      relationshipType <- RelationshipType.*
      direction <- List(NoDirection, Up, Down, archimate.Left, archimate.Right)
      r <- Some(
        _relationship(relationshipType.prefix)(relationshipType.suffix)(
          direction
        )
      )
    } yield (relationshipType, direction, r)

  def relationship(
      ofType: RelationshipType,
      edge: Edge = NoDirection,
      label: RelationshipLabel = None
  ): Relationship = relationships
    .find(t => t._1 == ofType && t._2 == edge)
    .map(_._3)
    .map(nr => nr(label))
    .get
}

object Archimate extends CanBuildSpecification {
  def archimate(
      body: Archimate => Any
  )(implicit
      specificationBuilder: SpecificationBuilder,
      archimateConfiguration: ArchimateConfiguration
  ): Archimate = {
    val archi = new Archimate()
    archi.init()
    body.apply(archi)
    archi
  }
}

case class ArchimateConfiguration(
    technicalFontSize: Int = 12,
    descriptionFontSize: Int = 10
)
case class ShapeColor(hex: Option[String] = None)
case class ShapeLabel(
    name: String,
    componentType: Option[String] = None,
    componentTypeDetails: Option[String] = None,
    description: Option[String] = None
)(implicit config: ArchimateConfiguration) {

  private val descriptionLabel: String = description
    .map(s => s"\\n\\n <size:${config.descriptionFontSize}>$s</size>")
    .getOrElse("")

  private val typeDetailsLabel: String =
    componentTypeDetails.map(i => s": $i").getOrElse("")

  private val typeLabel: String = componentType
    .map(s =>
      s"\\n\\n<size:${config.technicalFontSize}>[$s$typeDetailsLabel]</size>"
    )
    .getOrElse("")

  val output: String =
    s"$name$typeLabel$descriptionLabel"

  override def toString: String = output
}
