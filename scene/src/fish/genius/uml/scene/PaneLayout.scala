package fish.genius.uml.scene

// How large a pane is and where it stands, in scene units. The layout sizes each pane
// from its passport and keeps floor free for it; the Blender builder lays the same
// passport out with the same measures (its PANE table), so the two agree on the height
// without measuring text twice. SceneContractSpec holds the table to `metrics`, and the
// builder prints how far the panes it built are from the heights written here.
//
// Text is not measured: a line holds as many characters as fit at CHAR_WIDTH of the
// font size each, words wrapped greedily. The builder wraps the same way and reports
// any line that came out wider than its room.
object PaneLayout:

  val WIDTH: Double         = 2.1   // the pane, rim excluded
  val PAD: Double           = 0.1   // inside the rim, all round
  val BAND: Double          = 0.03  // the layer-coloured strip along the top
  val CHAR_WIDTH: Double    = 0.56  // a character, in font sizes
  val VALUE_COLUMN: Double  = 0.36  // where a row's value starts, as a share of the content
  val EYEBROW: Double       = 0.05
  val EYEBROW_LINE: Double  = 0.08
  val TITLE: Double         = 0.12
  val TITLE_LINE: Double    = 0.14
  val SUBTITLE: Double      = 0.055
  val SUBTITLE_LINE: Double = 0.08
  val TEXT: Double          = 0.055
  val TEXT_LINE: Double     = 0.08
  val LABEL: Double         = 0.04
  val LABEL_LINE: Double    = 0.07
  val FACT: Double          = 0.055
  val FACT_LINE: Double     = 0.085
  val CHIP: Double          = 0.09  // a chip's height; a mark's too
  val NOTE: Double          = 0.045
  val NOTE_LINE: Double     = 0.065
  val ROW_PAD: Double       = 0.025
  val BAR: Double           = 0.035 // the timeline's bar
  val TICKS: Double         = 0.07  // the years under it
  val GAP: Double           = 0.05  // between sections, a rule in the middle
  val FOOTER: Double        = 0.04
  val FOOTER_LINE: Double   = 0.06

  // where a pane stands
  val FLOOR_GAP: Double     = 0.3  // between the plinth and the pane's floor
  val STEM_Y: Double        = 0.35 // an upright pane beside the plinth stands this far back
  val STEM_ZONE: Double     = 0.3  // the floor its foot takes
  val UPRIGHT_LIFT: Double  = 0.42 // an upright pane's lower edge above the floor
  val BEHIND_LIFT: Double   = 1.25 // behind the plinth, above its sculpture
  val LECTERN_LIFT: Double  = 0.36
  val LECTERN_SLOPE: Double = 35.0 // degrees from the floor, the placard's slope
  val FLOAT_LIFT: Double    = 1.7  // a floating pane's lower edge
  val AUTO_FLAT: Double     = 56.0 // Auto lies flat above this camera elevation, in degrees

  // The measures by the names the builder's PANE table uses.
  val metrics: List[(String, Double)] = List(
    "width" -> WIDTH,
    "pad" -> PAD,
    "band" -> BAND,
    "charWidth" -> CHAR_WIDTH,
    "valueColumn" -> VALUE_COLUMN,
    "eyebrow" -> EYEBROW,
    "eyebrowLine" -> EYEBROW_LINE,
    "title" -> TITLE,
    "titleLine" -> TITLE_LINE,
    "subtitle" -> SUBTITLE,
    "subtitleLine" -> SUBTITLE_LINE,
    "text" -> TEXT,
    "textLine" -> TEXT_LINE,
    "label" -> LABEL,
    "labelLine" -> LABEL_LINE,
    "fact" -> FACT,
    "factLine" -> FACT_LINE,
    "chip" -> CHIP,
    "note" -> NOTE,
    "noteLine" -> NOTE_LINE,
    "rowPad" -> ROW_PAD,
    "bar" -> BAR,
    "ticks" -> TICKS,
    "gap" -> GAP,
    "footer" -> FOOTER,
    "footerLine" -> FOOTER_LINE,
    "floorGap" -> FLOOR_GAP,
    "stemY" -> STEM_Y,
    "stemZone" -> STEM_ZONE,
    "uprightLift" -> UPRIGHT_LIFT,
    "behindLift" -> BEHIND_LIFT,
    "lecternLift" -> LECTERN_LIFT,
    "lecternSlope" -> LECTERN_SLOPE,
    "floatLift" -> FLOAT_LIFT,
    "autoFlat" -> AUTO_FLAT,
  )

  private val CONTENT = WIDTH - 2 * PAD
  private val VALUES  = CONTENT * (1 - VALUE_COLUMN)

  // The lines a text breaks into at a font size in a width: greedy, by characters.
  def lines(
    text: String,
    size: Double,
    width: Double,
  ): List[String] =
    val room                   = math.max(1, (width / (size * CHAR_WIDTH)).toInt)
    val words                  = text.split("(?U)\\s+").toList.filter(_.nonEmpty)
    def length(s: String): Int = s.codePointCount(0, s.length)
    val (done, last)           = words.foldLeft((Vector.empty[String], "")): (acc, word) =>
      val (lines, line) = acc
      val longer        = if line.isEmpty then word else s"$line $word"
      if length(longer) > room && line.nonEmpty then (lines :+ line, word) else (lines, longer)
    val all                    = if last.nonEmpty then done :+ last else done
    if all.isEmpty then List("") else all.toList

  private def count(
    text: String,
    size: Double,
    width: Double,
  ): Int = lines(text, size, width).size

  // The pane's height for a passport, rim excluded.
  def height(passport: Passport): Double =
    val head     = BAND + PAD + EYEBROW_LINE + count(passport.title, TITLE, CONTENT) * TITLE_LINE +
      passport.subtitle.fold(0.0)(s => count(s, SUBTITLE, CONTENT) * SUBTITLE_LINE)
    val sections = passport.sections.map(s => GAP + heightOf(s)).sum
    val footer   = passport.footer.fold(0.0)(_ => GAP + FOOTER_LINE)
    SceneLayout.round(head + sections + footer + PAD)

  def heightOf(section: PassportSection): Double =
    def note(text: Option[String]): Double = text.fold(0.0)(n => count(n, NOTE, VALUES) * NOTE_LINE)
    section match
      case PassportSection.Text(body)           => count(body, TEXT, CONTENT) * TEXT_LINE
      case PassportSection.Facts(label, rows)   =>
        label.fold(0.0)(_ => LABEL_LINE) +
        rows.map(r => count(r.value, FACT, VALUES) * FACT_LINE).sum
      case PassportSection.Rated(_, chip)       => CHIP + note(chip.note) + ROW_PAD
      case PassportSection.Rating(_, _, text)   => CHIP + note(text) + ROW_PAD
      case PassportSection.Timeline(_, _, _, s) =>
        LABEL_LINE + BAR + TICKS + s.fold(0.0)(t => count(t, TEXT, CONTENT) * TEXT_LINE)

  // ── the floor a pane takes ─────────────────────────────────────────────────

  // How deep a pane reaches over the floor, away from the plinth (or, beside it, centred).
  // Auto keeps room for flat, the larger of the two it may become.
  private[scene] def floorDepth(
    orientation: PaneOrientation,
    side: PaneSide,
    h: Double,
  ): Double =
    val besides = side == PaneSide.Right || side == PaneSide.Left
    orientation match
      case PaneOrientation.Upright  => if besides then 0.0 else STEM_ZONE
      case PaneOrientation.Lectern  => h * math.cos(math.toRadians(LECTERN_SLOPE))
      case PaneOrientation.Flat     => h
      case PaneOrientation.Auto     => math.max(floorDepth(PaneOrientation.Upright, side, h), h)
      case PaneOrientation.Floating => 0.0

  // A leaf's node in ELK with its pane's floor: its size, and where the plinth lies in it
  // from the node's back-left corner (ELK's top-left).
  final private[scene] case class Reserve(
    w: Double,
    d: Double,
    left: Double,
    back: Double)

  private[scene] def reserve(
    orientation: PaneOrientation,
    side: PaneSide,
    h: Double,
    plinthW: Double,
    plinthD: Double,
  ): Option[Reserve] =
    val depth = floorDepth(orientation, side, h)
    Option.when(orientation != PaneOrientation.Floating):
      side match
        case PaneSide.Right  =>
          val d = math.max(plinthD, depth)
          Reserve(plinthW + FLOOR_GAP + WIDTH, d, 0, (d - plinthD) / 2)
        case PaneSide.Left   =>
          val d = math.max(plinthD, depth)
          Reserve(plinthW + FLOOR_GAP + WIDTH, d, FLOOR_GAP + WIDTH, (d - plinthD) / 2)
        case PaneSide.Behind =>
          val w = math.max(plinthW, WIDTH)
          Reserve(w, plinthD + FLOOR_GAP + depth, (w - plinthW) / 2, FLOOR_GAP + depth)
        case PaneSide.Front  =>
          val w = math.max(plinthW, WIDTH)
          Reserve(w, plinthD + FLOOR_GAP + depth, (w - plinthW) / 2, 0)

  end reserve

  // The floor a pane covers, relative to its plinth's centre: (x0, y0, x1, y1), y away from
  // the viewer. None when it takes no floor (floating).
  def footprint(
    pane: ScenePane,
    orientation: PaneOrientation,
    plinthW: Double,
    plinthD: Double,
  ): Option[(Double, Double, Double, Double)] =
    val depth    = floorDepth(orientation, pane.side, pane.h)
    val beside   = plinthW / 2 + FLOOR_GAP
    val behind   = plinthD / 2 + FLOOR_GAP
    val (y0, y1) =
      if orientation == PaneOrientation.Upright then (STEM_Y - STEM_ZONE / 2, STEM_Y + STEM_ZONE / 2)
      else (-depth / 2, depth / 2)
    Option.when(orientation != PaneOrientation.Floating):
      pane.side match
        case PaneSide.Right  => (beside, y0, beside + WIDTH, y1)
        case PaneSide.Left   => (-beside - WIDTH, y0, -beside, y1)
        case PaneSide.Behind => (-WIDTH / 2, behind, WIDTH / 2, behind + depth)
        case PaneSide.Front  => (-WIDTH / 2, -behind - depth, WIDTH / 2, -behind)

  end footprint

  // ── which side each pane takes ─────────────────────────────────────────────

  private def port(side: PaneSide): PortName = side match
    case PaneSide.Right  => PortName.East
    case PaneSide.Left   => PortName.West
    case PaneSide.Behind => PortName.North
    case PaneSide.Front  => PortName.South

  private def order(preferred: PaneSide): List[PaneSide] =
    val opposite = preferred match
      case PaneSide.Right  => PaneSide.Left
      case PaneSide.Left   => PaneSide.Right
      case PaneSide.Behind => PaneSide.Front
      case PaneSide.Front  => PaneSide.Behind
    (List(preferred, opposite) ++ List(PaneSide.Behind, PaneSide.Front, PaneSide.Right))
      .distinct

  // Only a leaf element shows a pane; a container, a grouping or a junction does not.
  private[scene] def shows(node: SceneNode): Boolean =
    node.passport.isDefined && !node.isContainer &&
    (node.kind match
      case NodeKind.Element(_) => true
      case _                   => false)

  // The side each pane stands on: the style's, unless a connector leaves the plinth there.
  private[scene] def sides(
    structure: SceneStructure,
    ports: Map[SceneId, Sides],
    style: PaneStyle,
  ): Map[SceneId, PaneSide] =
    val used = structure.edges
      .flatMap: edge =>
        ports.get(edge.id).toList.flatMap(s => List(edge.source -> s.from, edge.target -> s.to))
      .groupMap(_._1)(_._2)
    structure.nodes
      .filter(shows)
      .map: node =>
        val taken = used.getOrElse(node.id, Nil).toSet
        node.id -> order(style.side).find(side => !taken(port(side))).getOrElse(style.side)
      .toMap

  end sides

  // Each pane's floor, as ELK reserves it.
  private[scene] def reserves(
    structure: SceneStructure,
    ports: Map[SceneId, Sides],
    style: PaneStyle,
  ): Map[SceneId, Reserve] =
    sides(structure, ports, style).flatMap: (id, side) =>
      structure.byId.get(id).flatMap(_.passport).flatMap: passport =>
        reserve(
          style.orientation,
          side,
          height(passport),
          SceneLayout.PLINTH_WIDTH,
          SceneLayout.PLINTH_DEPTH,
        ).map(id -> _)

end PaneLayout
