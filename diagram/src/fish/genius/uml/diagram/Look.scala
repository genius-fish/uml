package fish.genius.uml.diagram

// The look of a drawing in numbers, shared by the writers so the PDF, the SVG and the
// PNG agree: how much of a layer's colour goes into each part, mixed the way xcolor
// mixes `a!p!b` (p per cent of a, the rest b), and the weights and shadow of the lines.
object Look:

  // per cent of the layer's colour in each part, the rest text (an edge) or paper (a fill)
  val EDGE           = 50 // a box's edge, against text
  val TOP            = 72 // the top of a box's fall-off, against paper
  val CONTAINER_FILL = 42 // a container's field, against paper
  val CONTAINER_EDGE = 58 // a container's edge, against text
  val MARK_EDGE      = 38 // an icon's or band's line, against text
  val MARK_LIGHT     = 28 // an icon's light fill, against paper
  val GROUP_FILL     = 5  // a grouping's field: muted against paper

  // line weights in points
  val BOX_LINE       = 0.55
  val CONTAINER_LINE = 0.5
  val MARK_LINE      = 0.4
  val LINK_LINE      = 0.6
  val DOT_LINE       = 0.95
  val BEND           = 2.2 // a connector's bend radius, points

  // a box's shadow: offset right and down, blurred, at this opacity
  val SHADOW_X     = 0.8
  val SHADOW_Y     = 1.0
  val SHADOW_BLUR  = 0.8
  val SHADOW_ALPHA = 0.17

  // xcolor's a!p!b on hex colours
  def mix(
    a: String,
    percent: Int,
    b: String,
  ): String =
    def rgb(hex: String) = (0 to 2).map(i => Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16))
    rgb(a).zip(rgb(b)).map((x, y) => math.round((x * percent + y * (100 - percent)) / 100.0).toInt)
      .map(v => f"$v%02X").mkString

  // a palette's colours resolved, with the derived parts of a role
  final case class Colours(palette: Map[String, String]):
    private val c                = palette
    def of(name: String): String = c.getOrElse(name, "FFFFFF")
    val line: String             = of("am-line")
    val text: String             = of("am-text")
    val muted: String            = of("am-muted")
    val paper: String            = of("am-paper")

    def ink(fill: Ink): Option[String] = fill match
      case Ink.Empty       => None
      case Ink.Paper       => Some(paper)
      case Ink.Line        => Some(line)
      case Ink.Layer(name) => Some(of(name))

    def edge(layer: String): String      = mix(of(layer), EDGE, text)
    def top(layer: String): String       = mix(of(layer), TOP, paper)
    def field(layer: String): String     = mix(of(layer), CONTAINER_FILL, paper)
    def fieldEdge(layer: String): String = mix(of(layer), CONTAINER_EDGE, text)
    def markEdge(layer: String): String  = mix(of(layer), MARK_EDGE, text)
    def markLight(layer: String): String = mix(of(layer), MARK_LIGHT, paper)
    val groupFill: String                = mix(muted, GROUP_FILL, paper)

    // the fill under a patch or a text's backdrop: the field it lies on
    def ground(role: Role): String = role match
      case Role.Container(layer) => field(layer)
      case Role.Group            => groupFill
      case Role.Element(layer)   => of(layer)
      case _                     => paper

    // a shape's edge and fill by what it is part of (Element's fill is its fall-off)
    def look(role: Role, fill: Ink): (Option[String], Option[String]) = role match
      case Role.Element(layer)   => (Some(edge(layer)), ink(fill))
      case Role.Container(layer) => (Some(fieldEdge(layer)), Some(field(layer)))
      case Role.Group            => (Some(muted), Some(groupFill))
      case Role.Mark(layer)      =>
        val inked = fill match
          case Ink.Paper => Some(markLight(layer))
          case Ink.Line  => Some(markEdge(layer))
          case other     => ink(other)
        (Some(markEdge(layer)), inked)
      case Role.Halo             => (None, Some(paper))
      case Role.Patch(on)        => (None, Some(ground(on)))
      case Role.Plain            => (Some(line), ink(fill))

    def weight(role: Role): Double = role match
      case Role.Element(_)   => BOX_LINE
      case Role.Container(_) => CONTAINER_LINE
      case Role.Mark(_)      => MARK_LINE
      case _                 => 0.5

  end Colours

  def colours(palette: Map[String, String]): Colours = Colours(Notation.colours(palette).toMap)

end Look
