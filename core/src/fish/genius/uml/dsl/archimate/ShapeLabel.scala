package fish.genius.uml.dsl.archimate

import fish.genius.uml.dsl.SvgIcon

/**
 * Build-time configuration for an [[ShapeLabel]]: tweaks the type-/description
 * font sizes used inside Archimate shape captions.
 */
final case class ArchimateConfiguration(
  technicalFontSize: Int = 12,
  descriptionFontSize: Int = 10)

object ArchimateConfiguration:
  /** Sensible defaults — useful when callers don't want to spell one out. */
  given default: ArchimateConfiguration = ArchimateConfiguration()

/**
 * A stereotype attached to a shape so multiple shapes can share a styling
 * (e.g. `<<phaseout>>` for components on the way out).
 *
 * Usually constructed via `defineStereoType`, which also emits the matching
 * `skinparam rectangle<<...>>` block and registers the stereotype for
 * inclusion in a [[legend]].
 */
final case class ShapeStereoType(
  name: String,
  description: Option[String],
  legendColor: Option[String])

/**
 * Optional explicit fill colour for a single shape (overrides the layer's
 * default colour from [[ShapeGroup]]).
 */
final case class ShapeColor(hex: Option[String] = None)

/**
 * The composite caption rendered inside an Archimate shape: name, optional
 * `[Type: details]` line, optional description sub-line, optional inline
 * SVG-icon row.
 *
 * The font sizes for the type and description lines come from the implicit
 * [[ArchimateConfiguration]].
 */
final case class ShapeLabel(
  name: String,
  componentType: Option[String] = None,
  componentTypeDetails: Option[String] = None,
  description: Option[String] = None,
  tags: List[SvgIcon] = Nil,
)(
  using config: ArchimateConfiguration):

  private val tagsLabel: String =
    if tags.nonEmpty then
      "\\n\\n" + tags.iterator.map(icon => s"<$$${icon.name.value}>").mkString
    else ""

  private val descriptionLabel: String = description
    .map(s => s"\\n\\n <size:${config.descriptionFontSize}>$s</size>")
    .getOrElse("")

  private val typeDetailsLabel: String =
    componentTypeDetails.map(i => s": $i").getOrElse("")

  private val typeLabel: String = componentType
    .map(s => s"\\n\\n<size:${config.technicalFontSize}>[$s$typeDetailsLabel]</size>")
    .getOrElse("")

  /** The final caption string ready to be spliced into a PlantUML statement. */
  val output: String = s"$name$typeLabel$descriptionLabel$tagsLabel"

  override def toString: String = output

end ShapeLabel
