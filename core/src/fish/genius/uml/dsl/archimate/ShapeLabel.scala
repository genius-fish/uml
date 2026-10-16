package fish.genius.uml.dsl.archimate

/**
 * Font sizes for the caption lines under a box's name, carried as a `given` so a caller
 * sets them once for a whole view.
 *
 * `elkLayout` has no effect: every layout is ELK's since 2.0.0 (UML-1). It stays so
 * callers that set it keep compiling; it goes in a later major.
 */
final case class ArchimateConfiguration(
  technicalFontSize: Int = 12,
  descriptionFontSize: Int = 10,
  elkLayout: Boolean = false)

object ArchimateConfiguration:
  /** Sensible defaults, for callers that do not want to spell one out. */
  given default: ArchimateConfiguration = ArchimateConfiguration()

/**
 * What a box says: its name, an optional `[Type: details]` line and an optional
 * description. Plain values; how they are set in type is the diagram's concern.
 *
 * The configuration it was built under travels with it as [[config]].
 */
final case class ShapeLabel(
  name: String,
  componentType: Option[String] = None,
  componentTypeDetails: Option[String] = None,
  description: Option[String] = None,
)(
  using val config: ArchimateConfiguration)
