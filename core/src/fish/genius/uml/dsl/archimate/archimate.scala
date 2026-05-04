package fish.genius.uml.dsl.archimate

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.markers.*

/**
 * The Archimate DSL: shapes, boundaries, relationships, stereotypes, legends.
 *
 * Calling `archimateDiagram { ... }` injects an [[InArchimate]] capability
 * into the body and emits the canonical Archimate skinparam preamble (rounded
 * corners, diagonal corners, default text alignment, etc.) so individual
 * `shape` calls render with the correct Archimate styling.
 *
 * The function is named `archimateDiagram` rather than `archimate` to avoid
 * a name clash with the surrounding `fish.genius.uml.dsl.archimate` package.
 */
def archimateDiagram(
  body: (PUmlCtx, InArchimate, ArchimateConfiguration) ?=> Unit
)(
  using PUmlCtx,
  InUml,
  ArchimateConfiguration,
): Unit =
  given InArchimate = markers.InArchimate
  archimatePreamble
  body

/**
 * Emit the Archimate skinparam header. Pulled out of [[archimate]] so callers
 * who already have an `archimate` block can re-emit if needed.
 */
def archimatePreamble(
  using PUmlCtx,
  InArchimate,
  ArchimateConfiguration,
): Unit =
  val cfg = summon[ArchimateConfiguration]
  statement(
    s"""!define TECHN_FONT_SIZE ${cfg.technicalFontSize}
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

end archimatePreamble

/**
 * Build a [[ShapeLabel]] succinctly. Convenience over the case-class
 * constructor.
 */
def label(
  name: String,
  componentType: Option[String] = None,
  componentTypeDetails: Option[String] = None,
  description: Option[String] = None,
  tags: List[SvgIcon] = Nil,
)(
  using ArchimateConfiguration
): ShapeLabel =
  ShapeLabel(name, componentType, componentTypeDetails, description, tags)

/** Build a [[ShapeColor]] from a hex string (with or without leading `#`). */
def color(hex: String): ShapeColor = ShapeColor(
  Some(if hex.startsWith("#") then hex else s"#$hex")
)

/**
 * Emit a single Archimate `rectangle ... <<archimate/...>> as <alias>` line
 * and return the alias so it can be referenced in [[relationship]] calls.
 */
def shape(
  shapeType: ShapeType,
  label: ShapeLabel,
  color: ShapeColor = ShapeColor(),
  stereoType: Option[ShapeStereoType] = None,
)(
  using PUmlCtx,
  InArchimate,
  ArchimateConfiguration,
): Alias =
  val decoratedLabel = shapeType.decorate(label)
  val fill           = color.hex.getOrElse(shapeType.group.defaultColor)
  val sterotypeTok   = stereoType.map(v => s"<<${v.name}>>").getOrElse("")
  expression(alias =>
    s"rectangle $fill \"$decoratedLabel\" <<${shapeType.icon}>>$sterotypeTok as $alias"
  )

end shape

/**
 * Define a reusable stereotype: emits its `skinparam rectangle<<NAME>> { ... }`
 * block and returns a [[ShapeStereoType]] handle that can be passed into
 * [[shape]] / [[boundary]] and rendered into a [[legend]].
 *
 * Mirrors the previous `Archimate.defineStereoType`.
 */
def defineStereoType(
  name: String,
  description: Option[String],
  legendColor: Option[String],
  skinParameters: SkinParamProperty*
)(
  using PUmlCtx,
  InArchimate,
): ShapeStereoType =
  val canonical = name.toUpperCase
  val st        = ShapeStereoType(canonical, description, legendColor)
  // Inline-emit the skinparam block (we're inside `archimate`, which means we
  // are inside `uml`, but `skinParam` requires `InUml` directly, which is
  // implied by `InArchimate` only by convention; emit the equivalent block
  // by hand here).
  if skinParameters.nonEmpty then
    headerBlock(s"skinparam rectangle<<$canonical>>"):
      skinParameters.foreach(p => statement(p.line))
  st

end defineStereoType

/**
 * Emit the Archimate legend block: a small inline table listing the
 * registered stereotypes and SVG-icon tags shipped via [[defineStereoType]] /
 * [[svgIcon]].
 */
def legend(
  title: String = "Legend",
  backgroundColorHexOrName: String = "#GhostWhite",
  stereoTypes: List[ShapeStereoType] = Nil,
  tags: List[SvgIcon] = Nil,
)(
  using PUmlCtx,
  InArchimate,
): Unit =
  // skinparam legend block
  headerBlock("skinparam legend"):
    statement(s"backgroundColor $backgroundColorHexOrName")
    statement(s"EntrySeparator $backgroundColorHexOrName")
  statement("legend bottom right")
  statement(s"<$backgroundColorHexOrName,$backgroundColorHexOrName>|        |= __${title}__ |")
  stereoTypes.foreach: entry =>
    statement(
      s"|<${entry.legendColor.getOrElse(backgroundColorHexOrName)}>         | ${entry.description
          .getOrElse(entry.name)}|"
    )
  tags.foreach: entry =>
    statement(
      s"|<$$${entry.name.value}>         | ${entry.description.getOrElse(entry.name.value)}|"
    )
  statement("endlegend")

end legend

/**
 * Emit an Archimate boundary: a `rectangle` with optional shape-type
 * stereotype and a body of nested shapes / boundaries / relationships.
 */
def boundary(
  label: String,
  boundaryType: Option[String] = None,
  shapeType: Option[ShapeType] = None,
  color: ShapeColor = ShapeColor(),
  stereoType: Option[ShapeStereoType] = None,
)(
  body: (PUmlCtx, InArchimate, InBoundary, ArchimateConfiguration) ?=> Unit
)(
  using PUmlCtx,
  InArchimate,
  ArchimateConfiguration,
): Alias =
  val cfg            = summon[ArchimateConfiguration]
  val techSize       = cfg.technicalFontSize
  val typeSuffix     = boundaryType
    .map(bt => s"\\n<size:$techSize>[$bt]</size>")
    .getOrElse("")
  val iconStereotype = shapeType.map(st => s"<<${st.icon}>>").getOrElse("")
  val classStereo    = shapeType
    .map(st => s"${st.group.prefix}-${st.name}")
    .getOrElse("boundary")
  val customStereo   = stereoType.map(v => s"<<${v.name}>>").getOrElse("")
  val fill           = color.hex
    .orElse(shapeType.map(_.group.defaultColor))
    .getOrElse("")
  val alias          = Alias.fresh
  val header         =
    s"rectangle \"==$label$typeSuffix\" $iconStereotype<<$classStereo>>$customStereo as $alias $fill"
      .trim
  emit(
    fish.genius.uml.ast.PUmlNode.Block(
      header,
      childBlock:
        given InBoundary = markers.InBoundary
        body,
    )
  )
  alias

end boundary

/**
 * Emit a relationship line `source <prefix><dir><suffix> target : "label"`.
 *
 * Returns a partially-applied function `source => target => Unit` mirroring
 * the original API, so callers can write
 * `relationship(Composition)(a)(b)` or build a function once and apply it
 * later to many `(source, target)` pairs.
 */
def relationship(
  ofType: RelationshipType,
  edge: Edge = Edge.NoDirection,
  label: Option[String] = None,
)(
  using PUmlCtx,
  InArchimate,
): Alias => Alias => Unit =
  source =>
    target =>
      val tok = edge.asString(ofType.prefix, ofType.suffix)
      statement(s"${source.value} $tok ${target.value} : \"${label.getOrElse("")}\"")

/**
 * Convenience wrapper: emits a one-off `skinparam <stereotype> { props }`
 * block from inside an `archimate { ... }` body. Equivalent to calling
 * [[skinParam]] at the diagram level, but typed for archimate scope so users
 * don't need to manually pop back up to `InUml`.
 */
def archimateSkinParam(
  stereotype: String,
  properties: SkinParamProperty*
)(
  using PUmlCtx,
  InArchimate,
): Unit =
  if properties.nonEmpty then
    headerBlock(s"skinparam $stereotype"):
      properties.foreach(p => statement(p.line))
