package fish.genius.uml.diagram

// The look of a drawing in numbers, shared by the writers so the PDF, the SVG and the
// PNG agree: how much of a layer's colour goes into each part, mixed the way xcolor
// mixes `a!p!b` (p per cent of a, the rest b), and the weights and shadow of the lines.
private[diagram] object Look:

  // per cent of the layer's colour in each part, the rest text (an edge) or paper (a fill)
  val EDGE           = 50 // a box's edge, against text
  val TOP            = 72 // the top of a box's fall-off, against paper
  val CONTAINER_FILL = 42 // a container's field, against paper
  val CONTAINER_EDGE = 58 // a container's edge, against text
  val MARK_EDGE      = 38 // an icon's or band's line, against text
  val MARK_LIGHT     = 28 // an icon's light fill, against paper
  val GROUP_FILL     = 5  // a grouping's field: muted against paper

  // A faded box (SceneNode.faded): its fill so much of its layer against paper, its edge
  // and its marks so much of their usual colour against paper; its text as light as it can
  // be while it keeps FADE_TEXT (its name, its note) or FADE_MUTED (its caption, a badge's
  // text) contrast on everything it stands on. Those are WCAG's 4.5 and 3, and a little,
  // so xcolor's own rounding of a mix cannot take them under.
  val FADE_FILL  = 38
  val FADE_EDGE  = 45
  val FADE_MARK  = 45
  val FADE_TEXT  = 4.55
  val FADE_MUTED = 3.05

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

  // a role's line weight in points, before a pen multiplies it
  def weight(role: Role): Double = role match
    case Role.Element(_)   => BOX_LINE
    case Role.Container(_) => CONTAINER_LINE
    case Role.Mark(_)      => MARK_LINE
    case Role.Faint(_)     => MARK_LINE
    case _                 => 0.5

  // a line's weight as drawn: one that is not a positive number counts as 1, and one above
  // MAX_LINK_WEIGHT as that, so a heavy line never swallows its labels and heads
  val MAX_LINK_WEIGHT = 2.5

  def linkWeight(weight: Double): Double =
    if weight.isNaN || weight <= 0 then 1.0 else math.min(weight, MAX_LINK_WEIGHT)

  // how much a heavier line's heads grow: half as fast as the line, so a head still
  // stands out from the line it ends (1.0 → 1, 2.4 → 1.7)
  def headScale(weight: Double): Double = (1.0 + linkWeight(weight)) / 2

  // a dash's on and off lengths in points, which grow with the pen so a heavier
  // dashed edge keeps its rhythm; a dot's line weight
  val DASH_ON  = 2.8
  val DASH_OFF = 1.8
  val DOT_GAP  = 1.9

  // the parts of a faded box, each a colour of its own by name, derived from its layer
  enum Faded derives CanEqual:
    case Fill, Edge, Mark, Text, Muted

  def faded(part: Faded, layer: String): String = s"am-fade-${part.toString.toLowerCase}-$layer"

  // A colour derived from the palette: its name, the xcolor mix it is (so a document that
  // defines its own layer colours fades them too), or none when it is a colour of its own,
  // and its value.
  final case class Derived(
    name: String,
    mix: Option[String],
    hex: String)

  // WCAG 2's contrast ratio between two colours (hex)
  def contrast(a: String, b: String): Double =
    def luminance(hex: String) =
      val List(r, g, bl) = (0 to 2).toList.map(i =>
        Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16) / 255.0
      )
        .map(c => if c <= 0.03928 then c / 12.92 else math.pow((c + 0.055) / 1.055, 2.4))
      0.2126 * r + 0.7152 * g + 0.0722 * bl
    val (la, lb)               = (luminance(a), luminance(b))
    (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05)

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
      case Role.Inked(layer)     => (Some(of(layer)), ink(fill))
      case Role.Faint(colour)    =>
        val inked = fill match
          case Ink.Line => Some(of(colour))
          case other    => ink(other)
        (Some(of(colour)), inked)

    def weight(role: Role): Double = Look.weight(role)

    // A faded layer's five colours, from this palette. Its text is the lightest mix of the
    // text colour into paper that keeps its contrast on the fill, on the top of the fill's
    // fall-off, and on the field it makes as a container; failing all (a palette whose text
    // is poor on its own colours), black or white, whichever stands out more.
    def fade(layer: String): List[Derived] =
      val fill    = mix(of(layer), FADE_FILL, paper)
      val grounds = List(fill, mix(fill, TOP, paper), mix(fill, CONTAINER_FILL, paper))
      def lightest(
        part: Faded,
        ink: String,
        least: Double,
      ): Derived  = (0 to 100)
        .map(p => (p, mix(of(ink), p, paper)))
        .find((_, hex) => grounds.forall(contrast(hex, _) >= least)) match
        case Some((p, hex)) => Derived(faded(part, layer), Some(s"$ink!$p!am-paper"), hex)
        case None           =>
          val hex = List("000000", "FFFFFF").maxBy(h => grounds.map(contrast(h, _)).min)
          Derived(faded(part, layer), None, hex)
      List(
        Derived(faded(Faded.Fill, layer), Some(s"$layer!$FADE_FILL!am-paper"), fill),
        Derived(
          faded(Faded.Edge, layer),
          Some(s"$layer!$EDGE!am-text!$FADE_EDGE!am-paper"),
          mix(edge(layer), FADE_EDGE, paper),
        ),
        Derived(
          faded(Faded.Mark, layer),
          Some(s"$layer!$MARK_EDGE!am-text!$FADE_MARK!am-paper"),
          mix(markEdge(layer), FADE_MARK, paper),
        ),
        lightest(Faded.Text, "am-text", FADE_TEXT),
        lightest(Faded.Muted, "am-muted", FADE_MUTED),
      )
    end fade

    // these colours with the faded layers' own added
    def fading(layers: List[String]): Colours =
      Colours(palette ++ layers.distinct.flatMap(fade).map(d => d.name -> d.hex))

  end Colours

  def colours(palette: Map[String, String]): Colours = Colours(Notation.colours(palette).toMap)

end Look
