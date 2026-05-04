package fish.genius.uml.dsl

import fish.genius.uml.ast.PUmlNode
import fish.genius.uml.dsl.markers.InUml

/**
 * `skinparam <name> { ... }` blocks and their typed property values.
 *
 * Mirrors `SkinParamProperty` from the previous OO-style API: each variant
 * knows its PlantUML property name, value rendering, and (for style enums
 * like dashed/bold) the canonical key it maps to.
 */
enum SkinParamProperty:

  case FontColor(colorNameOrHex: String)
  case BorderColor(colorNameOrHex: String)
  case BackgroundColor(colorNameOrHex: String)
  case Color(colorNameOrHex: String)
  case FontSize(pt: Int)
  case Shadowing(shadow: Boolean)
  case StereotypeFontSize(pt: Int)
  case BorderDashed()
  case BorderDotted()
  case BorderPlain()
  case BorderBold()
  case StereoTypeAlignmentRight()
  case StereoTypeAlignmentLeft()
  case StereoTypeAlignmentCenter()
  case RoundCorner(radius: Int)
  case DiagonalCorner(radius: Int)
  case BorderThickness(pt: Int)
  case EntrySeparatorColor(hex: String)
  case SkinProp(n: String, v: String)

  def name: String = this match
    case FontColor(_)                => "FontColor"
    case BorderColor(_)              => "BorderColor"
    case BackgroundColor(_)          => "BackgroundColor"
    case Color(_)                    => "Color"
    case FontSize(_)                 => "FontSize"
    case Shadowing(_)                => "Shadowing"
    case StereotypeFontSize(_)       => "StereotypeFontSize"
    case BorderDashed()              => "BorderStyle"
    case BorderDotted()              => "BorderStyle"
    case BorderPlain()               => "BorderStyle"
    case BorderBold()                => "BorderStyle"
    case StereoTypeAlignmentRight()  => "StereotypeAlignment"
    case StereoTypeAlignmentLeft()   => "StereotypeAlignment"
    case StereoTypeAlignmentCenter() => "StereotypeAlignment"
    case RoundCorner(_)              => "RoundCorner"
    case DiagonalCorner(_)           => "DiagonalCorner"
    case BorderThickness(_)          => "BorderThickness"
    case EntrySeparatorColor(_)      => "EntrySeparator"
    case SkinProp(n, _)              => n

  def value: String = this match
    case FontColor(c)                => c
    case BorderColor(c)              => c
    case BackgroundColor(c)          => c
    case Color(c)                    => c
    case FontSize(pt)                => pt.toString
    case Shadowing(s)                => s.toString
    case StereotypeFontSize(pt)      => pt.toString
    case BorderDashed()              => "dashed"
    case BorderDotted()              => "dotted"
    case BorderPlain()               => "plain"
    case BorderBold()                => "bold"
    case StereoTypeAlignmentRight()  => "right"
    case StereoTypeAlignmentLeft()   => "left"
    case StereoTypeAlignmentCenter() => "center"
    case RoundCorner(r)              => r.toString
    case DiagonalCorner(r)           => r.toString
    case BorderThickness(pt)         => pt.toString
    case EntrySeparatorColor(hex)    => hex
    case SkinProp(_, v)              => v

  /** The PlantUML line `<name> <value>`. */
  def line: String = s"$name $value"

end SkinParamProperty

/**
 * Emit `skinparam <stereotype> { <props> }`.
 *
 * Calling with no properties is a no-op (no block is emitted). Mirrors the
 * behaviour of the previous OO-style `SkinParam` apply.
 */
def skinParam(
  stereotype: String,
  properties: SkinParamProperty*
)(
  using PUmlCtx,
  InUml,
): Unit =
  if properties.nonEmpty then
    emit(
      PUmlNode.Block(
        s"skinparam $stereotype",
        PUmlNode.Sequence(properties.iterator.map(p => PUmlNode.Statement(p.line)).toVector),
      )
    )

/**
 * A reference to an SVG sprite registered via [[svgIcon]]. The sprite is
 * emitted into the diagram preamble; the returned value remembers its alias
 * so it can be cited inside labels and legends.
 */
final case class SvgIcon(name: Alias, description: Option[String])

/**
 * Register an SVG sprite with the diagram and return an [[SvgIcon]] handle.
 *
 * @param svg          raw `<svg>...</svg>` markup
 * @param name         optional explicit alias; auto-generated when omitted
 * @param description  optional caption for use in legends
 */
def svgIcon(
  svg: String,
  name: Option[String] = None,
  description: Option[String] = None,
)(
  using PUmlCtx,
  InUml,
): SvgIcon =
  val iconName: Alias = name.map(Alias.apply).getOrElse(Alias.fresh)
  emit(PUmlNode.Statement(s"sprite $$${iconName.value} $svg"))
  SvgIcon(iconName, description)
