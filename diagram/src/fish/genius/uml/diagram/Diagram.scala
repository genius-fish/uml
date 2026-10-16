package fish.genius.uml.diagram

import fish.genius.uml.dsl.archimate.ShapeType
import fish.genius.uml.scene.*

// An ArchiMate graph as a flat diagram: laid out by the same ELK pass as the 3D scene
// (SceneLayout), with each box sized to what it says, and drawn as plain geometry (Prim)
// that Tikz, Svg and Png write out. Units are centimetres, x to the right and y up.
//
// A box says its name, under it its caption (the type, and its technology) and then
// its note (a line or two about it), each in its own size; a connector says its label.
// Every line is placed here on its baseline, so the three writers set the same text in
// the same place.
object Diagram:

  // The font the document sets its diagrams in, and the sizes in points; a box is sized
  // from the font's metrics, so what it says fits.
  final case class Style(
    typeface: Typeface = Typeface.heros,
    nameSize: Double = 7.6,
    captionSize: Double = 6.0,
    noteSize: Double = 5.8,
    labelSize: Double = 6.0,
    lineHeight: Double = 1.22,
    boxWidth: Double = 3.0,
    wideWidth: Double = 3.8,
    boxHeight: Double = 1.3,
    labelWidth: Double = 3.6,
    padding: Double = 0.18,
    iconSize: Double = 0.32,
    band: Double = 0.24,
    junction: Double = 0.16,
    header: Double = 0.6,
    gap: Double = 0.08,
    badgeSize: Double = 0.24):
    def lead(size: Double): Double = size * lineHeight * PT

  end Style

  // pageWidth is the width a diagram should keep within, in centimetres: a row wider
  // folds, so the diagram is printed at its own size rather than scaled down; direction
  // Right lays the layers out left to right (a chain drawn wide, for a slide) and folds no row
  final case class Options(
    style: Style = Style(),
    nodeSpacing: Double = 0.8,
    layerSpacing: Double = 1.1,
    containerPadding: Double = 0.35,
    maxPerRow: Int = 0,
    pageWidth: Double = 13.0,
    direction: SceneLayout.Direction = SceneLayout.Direction.Down)

  // What was drawn, and the scene it was drawn from; `overflow` counts the lines wider
  // than their room, which only a single word longer than the widest box can make.
  final case class Drawing(
    name: String,
    prims: List[Prim],
    scene: Scene,
    style: Style,
    family: String,
    overflow: Int,
    colours: Map[String, String] = Map.empty,
    // the layers a faded box or legend item is drawn from, whose faded colours each writer
    // derives from the palette it draws with (Look.Colours.fade)
    faded: List[String] = Nil)

  val PT: Double = 2.54 / 72.0

  def draw(graph: SceneGraph, options: Options = Options()): Either[SceneError, Drawing] =
    val style   = options.style
    val measure = Measure(style.typeface)
    val nodes   = graph.nodes.flatMap(_.flatten)
    val byNode  = nodes.map(n => n.id -> n).toMap
    val sized   = nodes.filterNot(_.isContainer).map(n => n.id -> boxOf(n, style, measure)).toMap
    val labels  = graph.edges.flatMap(_.label).map(t => t -> labelOf(t, style, measure)).toMap
    val caption = if nodes.exists(n => n.isContainer && n.caption.nonEmpty) then
      style.lead(style.captionSize)
    else 0.0
    def header(names: Int) = style.header + (names - 1) * style.lead(style.nameSize) + caption
    def laid(names: Int)   =
      SceneLayout.layout(
        graph,
        SceneLayout.Options(
          nodeSpacing = options.nodeSpacing,
          layerSpacing = options.layerSpacing,
          containerPadding = options.containerPadding,
          containerHeader = header(names),
          maxPerRow = options.maxPerRow,
          footprint = node => sized.get(node.id).map(b => (b.w, b.h)),
          headerAtBack = true,
          spreadPorts = true,
          labelSize = text => labels.get(text).map(l => (l.w, l.h)),
          compact = true,
          rowWidth = options.pageWidth,
          direction = options.direction,
        ),
      )
    // ELK sizes a container from its children, so a long name may not fit beside its
    // icon: it then takes as many lines as the narrowest such container needs, and the
    // layout runs again with every header that much taller
    def names(scene: Scene): Map[SceneId, List[String]] =
      scene.elements
        .filter(_.container)
        .flatMap: e =>
          byNode.get(e.key).map: n =>
            e.key -> measure.wrap(
              n.label,
              e.w - 3 * style.padding - style.iconSize - tagRoom(n, style, measure),
              style.nameSize,
              Weight.SemiBold,
            )
        .toMap
    for
      first <- laid(1)
      lines = names(first).values.map(_.size).maxOption.getOrElse(1)
      scene <- if lines > 1 then laid(lines) else Right(first)
    yield
      val headed             = names(scene)
      val tall               = header(math.max(1, lines))
      val byId               = byNode
      val placed             = scene.elements.map(e => e.key -> e).toMap
      val kinds              = graph.edges.map(e => e.id -> e.relationship).toMap
      val weights            = graph.edges.map(e => e.id -> Look.linkWeight(e.weight)).toMap
      val boxes              = scene.elements.flatMap: e =>
        byId.get(e.key).toList.flatMap(n =>
          element(e, n, sized.get(n.id), headed.get(e.key), tall, style, measure)
        )
      val laidOut            = scene.relationships.flatMap: r =>
        for
          kind   <- kinds.get(r.key)
          source <- placed.get(r.source)
          target <- placed.get(r.target)
        yield
          val from = r.start.fold(port(source, r.sourcePort))(p => (p.x, p.y))
          val to   = r.end.fold(port(target, r.targetPort))(p => (p.x, p.y))
          (r, kind, straighten(from :: r.bends.map(p => (p.x, p.y)) ::: List(to)))
      val spread             = nudge(laidOut.map(_._3))
      val routes             = laidOut.zip(spread).map:
        case ((r, kind, _), points) =>
          val (dash, start, end) = Notation.ends(kind)
          val weight             = weights.getOrElse(r.key, 1.0)
          (r, points, Prim.Connector(points, dash, start, end, r.colour.flatMap(fillName), weight))
      // what a label may not cover: the leaves, and the containers' headers
      val taken              = scene.elements.map: e =>
        val top = e.y + e.d / 2
        (e.x - e.w / 2, if e.container then top - tall else e.y - e.d / 2, e.x + e.w / 2, top)
      // the field a point lies on: the innermost container or grouping round it, or the page
      val fields             = scene.elements.filter(_.container).flatMap: e =>
        byNode.get(e.key).map: n =>
          val role = n.kind match
            // a filled container's field is its own colour, as its box is drawn, and a
            // faded one's its faded colour
            case NodeKind.Element(shape) => Role.Container(drawnLayer(n, shape))
            case _                       => Role.Group
          (e, role)
      def field(p: Pt): Role =
        fields
          .filter((e, _) => math.abs(p._1 - e.x) <= e.w / 2 && math.abs(p._2 - e.y) <= e.d / 2)
          .minByOption((e, _) => e.w * e.d)
          .fold(Role.Halo)(_._2)
      val signs              = graph.edges.flatMap(e => e.sign.map(e.id -> _)).toMap
      val said               = routes.flatMap:
        (
          r,
          points,
          _,
        ) =>
          val words =
            r.label.toList.flatMap(l => label(l, labels.get(l.text), points, taken, style, field))
          words ++ signs.get(r.key).toList.flatMap(g =>
            signOnLine(g, points, words, r.colour, style, field)
          )
      val gaps               = crossings(routes.map(r => (r._2, r._3)), field)
      val overflow           = sized.values.map(_.overflow).sum
      // drawn back to front: containers and groupings, then the boxes, then the lines
      // between them, then every word, and the connectors' labels on top
      def isText(p: Prim)    = p.isInstanceOf[Prim.Text]
      def isField(p: Prim)   = p match
        case Prim.Box(_, _, _, _, _, _, _, _, Role.Container(_), _) => true
        case Prim.Poly(_, _, _, _, Role.Group, _)                   => true
        case _                                                      => false
      val (backs, rest)      = boxes.filterNot(isText).partition(isField)
      val ordered = backs ++ rest ++ routes.map(_._3) ++ gaps ++ boxes.filter(isText) ++ said
      val keyed   = graph.legend
        .filter(l => l.entries.nonEmpty || l.sections.exists(_.items.nonEmpty))
        .toList
        .flatMap(legend(_, ordered, style, measure))
      val colours =
        (nodes.flatMap(n => n.fill.flatMap(h => fillName(h).map(_ -> hexOf(h)))) ++
          nodes.flatMap(n => outlineColour(n.outline)) ++
          legendColours(graph.legend) ++
          nodes.flatMap(n => n.tag.toList.flatMap(tagColours)) ++
          scene.relationships.flatMap(r => r.colour.flatMap(h => fillName(h).map(_ -> hexOf(h)))))
          .toMap
      val faded   = (nodes.filter(_.faded).flatMap(n =>
        n.kind match
          case NodeKind.Element(shape) => List(layerOf(n, shape))
          case _                       => Nil
      ) ++ legendFaded(graph.legend)).distinct
      Drawing(graph.name, ordered ++ keyed, scene, style, measure.family, overflow, colours, faded)
    end for

  end draw

  // A sign at the middle of a line, on a disc of the field it lies on so the line stops
  // short of it; when the line has a label, on the line just before it (back along the
  // route from the point nearest the label, by as much of the label as lies along it), so
  // the two read together and the sign stays on its line whichever way the line runs. On a
  // line in a colour of its own the sign is in that colour too.
  private def signOnLine(
    glyph: Glyph,
    points: List[Pt],
    words: List[Prim],
    colour: Option[String],
    style: Style,
    field: Pt => Role,
  ): List[Prim] =
    val s      = style.badgeSize
    val halo   = s * SIGN_HALO
    val total  = length(points)
    val centre = words.flatMap(extent).reduceOption((a, b) =>
      (math.min(a._1, b._1), math.min(a._2, b._2), math.max(a._3, b._3), math.max(a._4, b._4))
    ) match
      case Some((x0, y0, x1, y1)) =>
        val (t, (dx, dy)) = nearest(points, ((x0 + x1) / 2, (y0 + y1) / 2))
        val along         = math.abs(dx) * (x1 - x0) / 2 + math.abs(dy) * (y1 - y0) / 2
        // on a route too short for it before the label, after it; failing both, halfway
        val before        = t - along - GAP - halo
        val after         = t + along + GAP + halo
        at(
          points,
          if before >= halo then before else if after <= total - halo then after else total / 2,
        )
      case None                   => at(points, total / 2)
    val ink    = colour.flatMap(fillName)
    val role   = ink.fold(Role.Plain)(Role.Inked(_))
    val sign   = place(Notation.glyph(glyph), centre._1 - s / 2, centre._2 - s / 2, s, role).map:
      case p: Prim.Path =>
        ink.fold(p)(i => if p.fill == Ink.Line then p.copy(fill = Ink.Layer(i)) else p)
      case other        => other
    Prim.Circle(centre, halo, Ink.Paper, Role.Patch(field(centre))) :: sign

  end signOnLine

  private def length(points: List[Pt]): Double =
    points.zip(points.drop(1)).map((a, b) => math.hypot(b._1 - a._1, b._2 - a._2)).sum

  // the point a distance d along a route
  private[diagram] def at(points: List[Pt], d: Double): Pt =
    points.zip(points.drop(1)).foldLeft((d, Option.empty[Pt])):
      case ((left, found @ Some(_)), _) => (left, found)
      case ((left, None), (a, b))       =>
        val l = math.hypot(b._1 - a._1, b._2 - a._2)
        if left <= l && l > 0 then
          (0.0, Some((a._1 + (b._1 - a._1) * left / l, a._2 + (b._2 - a._2) * left / l)))
        else (left - l, None)
    ._2.getOrElse(points.lastOption.getOrElse((0.0, 0.0)))

  // how far along a route its point nearest p lies, and the direction of the stretch there
  private def nearest(points: List[Pt], p: Pt): (Double, (Double, Double)) =
    points.zip(points.drop(1)).foldLeft((0.0, (Double.MaxValue, 0.0, (1.0, 0.0)))):
      case ((from, best), (a, b)) =>
        val (vx, vy) = (b._1 - a._1, b._2 - a._2)
        val l        = math.hypot(vx, vy)
        if l <= 0 then (from, best)
        else
          val k = math.max(0.0, math.min(l, ((p._1 - a._1) * vx + (p._2 - a._2) * vy) / l))
          val q = (a._1 + vx * k / l, a._2 + vy * k / l)
          val g = math.hypot(p._1 - q._1, p._2 - q._2)
          (from + l, if g < best._1 then (g, from + k, (vx / l, vy / l)) else best)
    ._2 match
      case (_, t, dir) => (t, dir)

  // the disc a sign on a line stands on, against the sign's size
  private val SIGN_HALO = 0.62

  private val GAP      = 0.06
  private val ICON_AIR = 0.08
  private val STEP     = 0.1

  // A route without the tiny steps ELK leaves where two stretches nearly line up,
  // which a rounded bend turns into a kink. The part of the route on one side of such
  // a step moves across by its width, its end sliding that little along the side of
  // its box (only a step square to the route's first, or last, stretch moves that
  // end, so the end moves along its side, never off it), and the step is gone. Each
  // round removes a point, so it ends.
  private[diagram] def straighten(points: List[Pt]): List[Pt] =
    def square(a: (Pt, Pt), b: (Pt, Pt)): Boolean =
      val (u, v) = ((a._2._1 - a._1._1, a._2._2 - a._1._2), (b._2._1 - b._1._1, b._2._2 - b._1._2))
      math.abs(u._1 * v._1 + u._2 * v._2) < 1e-9
    val n                                         = points.size
    val fixed                                     = (1 to n - 3).iterator
      .filter(i =>
        math.hypot(points(i + 1)._1 - points(i)._1, points(i + 1)._2 - points(i)._2) < STEP
      )
      .flatMap: i =>
        val step     = (points(i), points(i + 1))
        val (dx, dy) = (step._2._1 - step._1._1, step._2._2 - step._1._2)
        if square(step, (points(0), points(1))) then
          Some(points.take(i + 1).map(p => (p._1 + dx, p._2 + dy)) ::: points.drop(i + 2))
        else if square(step, (points(n - 2), points(n - 1))) then
          Some(points.take(i + 1) ::: points.drop(i + 2).map(p => (p._1 - dx, p._2 - dy)))
        else None
      .nextOption()
    fixed.fold(points)(straighten)

  end straighten

  private val APART = 0.12
  private val SAME  = 0.04
  private val SHORT = 0.08

  // Lines that run on top of one another moved apart. Inner stretches of different
  // routes that lie on the same line (within SAME) and overlap are spread across it,
  // APART from each other, centred where they were, ordered by where their routes come
  // from and go to, so they part with as few crossings as can be. A route's first and
  // last stretch only lengthen or shorten, so its ends stay where they are; a group
  // whose spreading would shorten a neighbouring stretch below SHORT, or turn it
  // round, or that holds two stretches of one route, is left as it is.
  private[diagram] def nudge(routes: List[List[Pt]]): List[List[Pt]] =
    final case class Stretch(
      route: Int,
      at: Int,
      upright: Boolean,
      line: Double,
      lo: Double,
      hi: Double,
      side: Double)
    val stretches                        = routes.zipWithIndex.flatMap: (ps, r) =>
      (1 until ps.size - 2).toList.flatMap: i =>
        val (before, a, b, after) = (ps(i - 1), ps(i), ps(i + 1), ps(i + 2))
        if math.abs(a._1 - b._1) < 1e-6 && math.abs(a._2 - b._2) > 1e-6 then
          List(Stretch(
            r,
            i,
            upright = true,
            a._1,
            math.min(a._2, b._2),
            math.max(a._2, b._2),
            before._1 + after._1,
          ))
        else if math.abs(a._2 - b._2) < 1e-6 && math.abs(a._1 - b._1) > 1e-6 then
          List(Stretch(
            r,
            i,
            upright = false,
            a._2,
            math.min(a._1, b._1),
            math.max(a._1, b._1),
            before._2 + after._2,
          ))
        else Nil
        end if
    def together(a: Stretch, b: Stretch) =
      a.upright == b.upright && a.route != b.route && math.abs(a.line - b.line) < SAME &&
      a.lo < b.hi - SAME && b.lo < a.hi - SAME
    // the stretches that share a line, gathered transitively
    val groups = stretches.foldLeft(List.empty[List[Stretch]]): (found, st) =>
      val (joined, apart) = found.partition(_.exists(together(_, st)))
      (st :: joined.flatten) :: apart
    // a shift keeps the stretches either side of the moved one at least SHORT long and
    // pointing the way they did
    def keeps(
      ps: List[Pt],
      i: Int,
      upright: Boolean,
      line: Double,
    ): Boolean =
      def along(p: Pt)                   = if upright then p._1 else p._2
      val before                         = along(ps(i)) - along(ps(i - 1))
      val after                          = along(ps(i + 2)) - along(ps(i + 1))
      val nb                             = line - along(ps(i - 1))
      val na                             = along(ps(i + 2)) - line
      def kept(was: Double, now: Double) =
        math.abs(was) < 1e-6 && math.abs(now) < 1e-6 || was * now > 0 && math.abs(now) >= SHORT
      kept(before, nb) && kept(after, na)
    val shifts = groups
      .filter(g => g.size > 1 && g.map(_.route).distinct.size == g.size)
      .flatMap: group =>
        val ordered = group.sortBy(st => (st.side, st.route))
        val centre  = group.map(_.line).sum / group.size
        val placed  =
          ordered.zipWithIndex.map((st, k) => (st, centre + (k - (group.size - 1) / 2.0) * APART))
        if placed.forall((st, line) => keeps(routes(st.route), st.at, st.upright, line)) then
          placed.map((st, line) => (st.route, st.at, st.upright, line))
        else Nil
    routes.zipWithIndex.map: (ps, r) =>
      shifts.filter(_._1 == r).foldLeft(ps.toVector): (pts, shift) =>
        val (_, i, upright, line) = shift
        def moved(p: Pt)          = if upright then (line, p._2) else (p._1, line)
        pts.updated(i, moved(pts(i))).updated(i + 1, moved(pts(i + 1)))
      .toList

  end nudge

  // Where two connectors cross, the upright one is broken: a patch of the field under
  // the crossing cuts it, and a short piece of the level one is drawn again over it,
  // as a printed diagram breaks the line that passes under.
  private[diagram] def crossings(routes: List[(List[Pt], Prim)], field: Pt => Role): List[Prim] =
    def segments(points: List[Pt]) = points.zip(points.drop(1))
    def level(a: Pt, b: Pt)        = math.abs(a._2 - b._2) < 1e-6 && math.abs(a._1 - b._1) > 1e-6
    def upright(a: Pt, b: Pt)      = math.abs(a._1 - b._1) < 1e-6 && math.abs(a._2 - b._2) > 1e-6
    def within(
      v: Double,
      a: Double,
      b: Double,
    )                              = v > math.min(a, b) + GAP * 2 && v < math.max(a, b) - GAP * 2
    val found                      =
      for
        ((across, acrossPrim), i) <- routes.zipWithIndex
        ((down, _), j)            <- routes.zipWithIndex
        if i != j
        (a, b)                    <- segments(across) if level(a, b)
        (c, d)                    <- segments(down) if upright(c, d)
        if within(c._1, a._1, b._1) && within(a._2, c._2, d._2)
      yield (c._1, a._2, acrossPrim)
    found.distinctBy(c => (math.rint(c._1 * 1000), math.rint(c._2 * 1000))).flatMap:
      case (x, y, Prim.Connector(_, dash, _, _, ink, weight)) =>
        List(
          Prim.Box(
            x - GAP,
            y - GAP,
            2 * GAP,
            2 * GAP,
            0.0,
            Ink.Paper,
            stroke = false,
            role = Role.Patch(field((x, y))),
          ),
          Prim.Connector(List((x - GAP * 1.6, y), (x + GAP * 1.6, y)), dash, None, None, ink, weight),
        )
      case _                                                  => Nil

  end crossings

  // the middle of a box's side, where a connector meets it
  private def port(e: SceneElement, side: PortName): Pt = side match
    case PortName.North => (e.x, e.y + e.d / 2)
    case PortName.South => (e.x, e.y - e.d / 2)
    case PortName.West  => (e.x - e.w / 2, e.y)
    case PortName.East  => (e.x + e.w / 2, e.y)

  // one line to set: its text, its size, its weight and its tone
  final private[diagram] case class Line(
    text: String,
    size: Double,
    weight: Weight = Weight.Regular,
    tone: Emphasis = Emphasis.Main)

  final private case class Box(
    w: Double,
    h: Double,
    lines: List[Line],
    overflow: Int)

  final private case class Label(
    w: Double,
    h: Double,
    lines: List[Line])

  // A box is boxWidth wide, or wideWidth when it has a note or its name would take more
  // than two lines, or wider still for a single word that does not fit. The name and
  // caption keep clear of the icon on both sides, so they stay centred; the note, lower
  // down, has the box's whole width. It is as tall as its lines need.
  private def boxOf(
    node: SceneNode,
    style: Style,
    measure: Measure,
  ): Box = node.kind match
    case NodeKind.AndJunction | NodeKind.OrJunction =>
      Box(style.junction, style.junction, Nil, 0)
    case kind                                       =>
      // the name and caption keep clear of the icon, and of a little air beside it
      // a logo in the top left corner keeps the name as clear as the icon does
      val corner           = math.max(style.iconSize, logoWidth(node, style))
      // they wrap as they would without a tag, and the box grows by the room the tag
      // takes beside the icon, on both sides so they stay centred (APP-2261)
      val wrapAt           = 2 * (style.padding + corner + ICON_AIR)
      val clear            =
        2 * (style.padding + math.max(
          style.iconSize + tagRoom(node, style, measure),
          logoWidth(node, style),
        ) +
        ICON_AIR)
      val full             = 2 * style.padding
      def lines(w: Double) =
        measure
          .wrap(node.label, w - wrapAt, style.nameSize, Weight.Medium)
          .map(Line(_, style.nameSize, Weight.Medium)) ++
        node.caption.toList.flatMap(c =>
          measure.wrap(
            c,
            w - wrapAt,
            style.captionSize,
          ).map(Line(_, style.captionSize, tone = Emphasis.Muted))
        ) ++
        node.note.toList.flatMap(n =>
          measure.wrap(n, w - full, style.noteSize).map(Line(_, style.noteSize))
        )
      def room(l: Line)    = if l.size == style.noteSize then full else clear
      val narrow = measure.wrap(node.label, style.boxWidth - wrapAt, style.nameSize, Weight.Medium)
      val w0     = if node.note.nonEmpty || narrow.size > 2 then style.wideWidth else style.boxWidth
      val set    = lines(w0)
      val badged = badgeRow(node, style, measure)
      val w      =
        (w0 :: badged._1 :: set.map(l =>
          measure.width(l.text, l.size, l.weight) + room(l)
        )).max
      val band   = kind match
        case NodeKind.Element(s) if Notation.outline(s) == Notation.Outline.Banded => style.band
        case _                                                                     => 0.0
      val noteGap = if node.note.nonEmpty then style.gap else 0.0
      val h      =
        math.max(
          style.boxHeight,
          set.map(l => style.lead(l.size)).sum + noteGap + 2 * style.padding + band + badged._2,
        )
      val overflow = set.count(l => measure.width(l.text, l.size, l.weight) > w - room(l) + 1e-6)
      Box(Measure.round(w), Measure.round(h), set, overflow)

  private def labelOf(
    text: String,
    style: Style,
    measure: Measure,
  ): Label =
    val lines = measure
      .wrap(text, style.labelWidth, style.labelSize, Weight.Italic)
      .map(Line(_, style.labelSize, Weight.Italic, Emphasis.Muted))
    val w = lines.map(l => measure.width(l.text, l.size, l.weight)).maxOption.getOrElse(0.0) + 0.14
    Label(Measure.round(w), Measure.round(lines.map(l => style.lead(l.size)).sum + 0.06), lines)

  // lines stacked from `top` down, each centred on x (or starting from it) on its
  // baseline; a gap goes before the line at `gapBefore`, where the note starts
  private[diagram] def stack(
    x: Double,
    top: Double,
    lines: List[Line],
    style: Style,
    centred: Boolean,
    gapBefore: Int = -1,
    backdrop: Option[Role] = None,
  ): List[Prim] =
    lines.zipWithIndex
      .foldLeft((top, List.empty[Prim])):
        case ((y, done), (line, i)) =>
          val lead     = style.lead(line.size)
          val from     = if i == gapBefore then y - style.gap else y
          val baseline = from - lead / 2 - 0.35 * line.size * PT
          (
            from - lead,
            Prim.Text(
              (x, baseline),
              line.text,
              line.size,
              line.weight,
              line.tone,
              centred,
              backdrop,
            ) :: done,
          )
      ._2
      .reverse

  // A label in the room ELK kept for it, unless that is off the route (ELK places the
  // label of a connector that crosses a container's edge in another frame): then at
  // the first spot along the route's stretches, longest first, where it covers no box
  // and no container's header.
  private def label(
    l: SceneLabel,
    laid: Option[Label],
    route: List[Pt],
    taken: List[(Double, Double, Double, Double)],
    style: Style,
    field: Pt => Role,
  ): List[Prim] =
    val lines                =
      laid.map(_.lines).getOrElse(List(Line(l.text, style.labelSize, Weight.Italic, Emphasis.Muted)))
    val stretches            = route.zip(route.drop(1))
    def near(
      p: Pt,
      a: Pt,
      b: Pt,
    ): Double                =
      val x = math.max(math.min(a._1, b._1), math.min(p._1, math.max(a._1, b._1)))
      val y = math.max(math.min(a._2, b._2), math.min(p._2, math.max(a._2, b._2)))
      math.hypot(p._1 - x, p._2 - y)
    def free(c: Pt): Boolean =
      taken.forall(
        (
          x0,
          y0,
          x1,
          y1,
        ) =>
          c._1 + l.w / 2 <= x0 || c._1 - l.w / 2 >= x1 || c._2 + l.d / 2 <= y0 ||
          c._2 - l.d / 2 >= y1
      )
    val spots                =
      stretches
        .sortBy((a, b) => -math.hypot(a._1 - b._1, a._2 - b._2))
        .flatMap((a, b) =>
          List(
            0.5, 0.3, 0.7, 0.15, 0.85,
          ).map(t => (a._1 + (b._1 - a._1) * t, a._2 + (b._2 - a._2) * t))
        )
    val (cx, cy)             =
      if stretches.exists((a, b) => near((l.x, l.y), a, b) <= ON_ROUTE) then (l.x, l.y)
      else spots.find(free).orElse(spots.headOption).getOrElse((l.x, l.y))
    Prim.Box(
      cx - l.w / 2,
      cy - l.d / 2,
      l.w,
      l.d,
      0.06,
      Ink.Paper,
      stroke = false,
      role = Role.Patch(field((cx, cy))),
    ) ::
    stack(cx, cy + l.d / 2 - 0.03, lines, style, centred = true)

  end label

  private val ON_ROUTE = 0.25

  private def element(
    e: SceneElement,
    node: SceneNode,
    box: Option[Box],
    names: Option[List[String]],
    header: Double,
    style: Style,
    measure: Measure,
  ): List[Prim] =
    val (x0, y0) = (e.x - e.w / 2, e.y - e.d / 2)
    val top      = y0 + e.d
    val said     =
      names.getOrElse(List(node.label)).map(Line(_, style.nameSize, Weight.SemiBold)) ++
      node.caption.toList.map(Line(_, style.captionSize, tone = Emphasis.Muted))
    val saidH    = said.map(l => style.lead(l.size)).sum
    // a container's or grouping's name and caption, in its header
    def heading(
      x: Double,
      room: Double,
      on: Role,
    )            =
      stack(x, top - (room - saidH) / 2, said, style, centred = false, backdrop = Some(on))
    node.kind match
      case NodeKind.AndJunction    => List(Prim.Circle((e.x, e.y), e.w / 2, Ink.Line))
      case NodeKind.OrJunction     => List(Prim.Circle((e.x, e.y), e.w / 2, Ink.Paper))
      // a grouping is a dashed frame with its name on a tab at the top left
      case NodeKind.Grouping       =>
        val tabH = math.min(header, saidH + 2 * style.padding * 0.7)
        val tabW = math.min(
          e.w,
          said.map(l => measure.width(l.text, l.size, l.weight)).max + 2 * style.padding,
        )
        Prim.Poly(
          List(
            (x0, y0),
            (x0 + e.w, y0),
            (x0 + e.w, top - tabH),
            (x0 + tabW, top - tabH),
            (x0 + tabW, top),
            (x0, top),
          ),
          closed = true,
          Ink.Empty,
          node.outline.fold(Dash.Dashed)(o => dashOf(o.dash)),
          Role.Group,
          penOf(node.outline),
        ) :: heading(x0 + style.padding, tabH, Role.Group)
      case NodeKind.Element(shape) =>
        val layer = drawnLayer(node, shape)
        val fill  = Ink.Layer(layer)
        val role  = if e.container then Role.Container(layer) else Role.Element(layer)
        val dash  = node.outline.fold(Dash.Solid)(o => dashOf(o.dash))
        // a faded box's edge is its faded edge, unless its outline has a colour of its own
        val faint = Option.when(node.faded)(layerOf(node, shape))
        val pen   =
          val own = penOf(node.outline)
          faint.fold(own)(l => own.copy(ink = own.ink.orElse(Some(Look.faded(Look.Faded.Edge, l)))))
        // its marks (the icon, a band, its signs) faint, and its text paler
        val mark  = faint.fold(Role.Mark(layer))(l => Role.Faint(Look.faded(Look.Faded.Mark, l)))
        def toned(lines: List[Line]) = faint.fold(lines)(l => lines.map(fadeLine(_, l)))
        def frame(radius: Double)    =
          Prim.Box(x0, y0, e.w, e.d, radius, fill, dash, role = role, pen = pen)
        val outline                  = Notation.outline(shape) match
          case Notation.Outline.Square  => List(frame(SQUARE))
          case Notation.Outline.Rounded => List(frame(ROUNDED))
          case Notation.Outline.Banded  =>
            List(
              frame(SQUARE),
              Prim.Poly(
                List((x0, top - style.band), (x0 + e.w, top - style.band)),
                closed = false,
                role = mark,
              ),
            )
          case Notation.Outline.Cut     =>
            val c = 0.12
            List(
              Prim.Poly(
                List(
                  (x0 + c, y0),
                  (x0 + e.w - c, y0),
                  (x0 + e.w, y0 + c),
                  (x0 + e.w, top - c),
                  (x0 + e.w - c, top),
                  (x0 + c, top),
                  (x0, top - c),
                  (x0, y0 + c),
                ),
                closed = true,
                fill,
                dash,
                role,
                pen,
              )
            )
        val s                        = style.iconSize
        val icon                     =
          place(
            Notation.icon(shape),
            x0 + e.w - style.padding - s,
            top - style.padding - s,
            s,
            mark,
          )
        val text                     =
          if e.container then
            stack(
              x0 + style.padding,
              top - (header - saidH) / 2,
              toned(said),
              style,
              centred = false,
              backdrop = Some(Role.Container(layer)),
            )
          else
            val lines  = toned(
              box.map(_.lines).getOrElse(List(Line(node.label, style.nameSize, Weight.Medium)))
            )
            val band   =
              if Notation.outline(shape) == Notation.Outline.Banded then style.band else 0.0
            val noteAt =
              if node.note.nonEmpty then lines.indexWhere(_.size == style.noteSize) else -1
            val height = lines.map(l => style.lead(l.size)).sum +
              (if noteAt >= 0 then style.gap else 0.0)
            val row    = badgeRow(node, style, measure)._2
            stack(
              e.x,
              e.y - band / 2 + row / 2 + height / 2,
              lines,
              style,
              centred = true,
              gapBefore = noteAt,
            )
        val badges                   =
          if e.container then Nil
          else
            val words =
              faint.fold(Emphasis.Muted)(l => Emphasis.Inked(Look.faded(Look.Faded.Muted, l)))
            stamp(node.badges, e.x, y0 + style.padding, style, measure, mark, words)
        // a leaf's logo in its top left corner, as high as the icon
        val logo                     = node.logo.filterNot(_ => e.container).toList.flatMap: l =>
          Logos.place(l, x0 + style.padding, top - style.padding - s, logoWidth(node, style), s)
        // its tag beside the icon, on the icon's middle
        val tag                      = node.tag.flatMap(_.valid).toList.flatMap: (code, fill) =>
          val (w, h) = tagSize(code, style, measure)
          drawTag(
            code,
            fill,
            x0 + e.w - style.padding - s - ICON_AIR - w,
            top - style.padding - s / 2 - h / 2,
            style,
            measure,
          )
        outline ++ icon ++ logo ++ tag ++ text ++ badges
    end match

  end element

  private val BADGE_GAP      = 0.06
  // between a badge's mark and its text
  private val BADGE_TEXT_GAP = 0.04
  // between a badge with text and the next one
  private val BADGE_WORD_GAP = 0.16
  private val LEGEND_GAP     = 0.5
  // a logo badge stands this much higher than a glyph, since a logo has more to show
  private val LOGO_BADGE     = 1.4

  // the width a leaf's corner logo takes, as high as the icon
  private def logoWidth(node: SceneNode, style: Style): Double =
    node.logo.filterNot(_ => node.isContainer).fold(0.0)(Logos.width(_, style.iconSize))

  // a badge's mark: its logo when it has one, at the logo badge's height and up to three
  // times as wide, else its glyph; (width, height)
  private def mark(b: Badge, style: Style): (Double, Double) =
    b.logo.fold((style.badgeSize, style.badgeSize)): l =>
      val h = style.badgeSize * LOGO_BADGE
      (Logos.width(l, h), h)

  // the mark drawn from (x, y), its lower left corner
  private def drawMark(
    b: Badge,
    x: Double,
    y: Double,
    style: Style,
    role: Role,
  ): List[Prim] =
    val (w, h) = mark(b, style)
    b.logo.fold(place(Notation.glyph(b.glyph), x, y, style.badgeSize, role))(l =>
      Logos.place(l, x, y, w, h)
    )

  // the room between each badge and the next: wider after one with text, so each sign
  // reads with its own text rather than its neighbour's
  private def gaps(badges: List[Badge]): List[Double] =
    badges.dropRight(1).map(b => if badgeText(b).isDefined then BADGE_WORD_GAP else BADGE_GAP)

  // a badge's text as it is set, when it has one that says anything
  private def badgeText(b: Badge): Option[String] = b.shownText

  // the width a badge takes in a box's row: its mark, and its text after it
  private def badgeWidth(
    b: Badge,
    style: Style,
    measure: Measure,
  ): Double =
    mark(b, style)._1 +
    badgeText(b).fold(0.0)(t => BADGE_TEXT_GAP + measure.width(t, style.captionSize, Weight.Regular))

  // The room a box's badges take: the width of their row (with the padding either
  // side), and the height it adds to the box; nothing when it has none.
  private def badgeRow(
    node: SceneNode,
    style: Style,
    measure: Measure,
  ): (Double, Double) =
    if node.badges.isEmpty then (0.0, 0.0)
    else
      val widths = node.badges.map(badgeWidth(_, style, measure))
      (
        widths.sum + gaps(node.badges).sum + 2 * style.padding,
        node.badges.map(mark(_, style)._2).max + style.gap,
      )

  // badges in a row centred on x, standing on y: a glyph in its box's mark colour, a
  // logo in its own, and a badge's text after its mark at the caption's size, its capitals
  // centred on the mark, in `words` (the muted ink, or a faded box's)
  private def stamp(
    badges: List[Badge],
    x: Double,
    y: Double,
    style: Style,
    measure: Measure,
    role: Role,
    words: Emphasis,
  ): List[Prim] =
    val widths = badges.map(badgeWidth(_, style, measure))
    val width  = widths.sum + gaps(badges).sum
    val starts =
      widths.zip(gaps(badges) :+ 0.0).scanLeft(x - width / 2)((at, wg) => at + wg._1 + wg._2)
    badges.zip(starts).flatMap: (b, at) =>
      val (w, h) = mark(b, style)
      drawMark(b, at, y, style, role) ++ badgeText(b).toList.map: t =>
        val baseline = y + h / 2 - CAP_HEIGHT * style.captionSize * PT / 2
        Prim.Text(
          (at + w + BADGE_TEXT_GAP, baseline),
          t,
          style.captionSize,
          tone = words,
          centred = false,
        )

  end stamp

  // a faded box's layer: what its own colours are named after (Look.Colours.fade)
  private def layerOf(node: SceneNode, shape: ShapeType): String =
    node.fill.flatMap(fillName).getOrElse(Notation.layer(shape.group))

  // the layer a box is drawn in: its own, or its faded fill
  private def drawnLayer(node: SceneNode, shape: ShapeType): String =
    val layer = layerOf(node, shape)
    if node.faded then Look.faded(Look.Faded.Fill, layer) else layer

  // a line of a faded box, in its faded text or its faded muted ink
  private def fadeLine(line: Line, layer: String): Line = line.tone match
    case Emphasis.Muted    => line.copy(tone = Emphasis.Inked(Look.faded(Look.Faded.Muted, layer)))
    case Emphasis.Main     => line.copy(tone = Emphasis.Inked(Look.faded(Look.Faded.Text, layer)))
    case Emphasis.Inked(_) => line

  // the layers a legend's faded items are drawn from: a shape's, or the paper's
  private def legendFaded(legend: Option[Legend]): List[String] =
    legend.toList.flatMap(_.sections).flatMap(_.items).collect:
      case LegendItem.Faded(shape, _) => fadedLegendLayer(shape)

  private def fadedLegendLayer(shape: Option[ShapeType]): String =
    shape.fold("am-paper")(sh => Notation.layer(sh.group))

  // A tag: its code in capitals at a small size (small caps, which no font is asked for,
  // so every writer sets the same letters), each character centred in a cell as wide as
  // the widest figure or capital (tabular figures, likewise, and so a column of verdicts,
  // I beside M, has pills of one width), in a pill as high as the caps with air round them,
  // its ends round; a character wider still (a symbol) widens its own tag's cells
  private def tagText(style: Style): Double = style.nameSize * TAG_TEXT

  private def tagCell(
    code: String,
    style: Style,
    measure: Measure,
  ): Double =
    val size = tagText(style)
    ((('0' to '9') ++ ('A' to 'Z')).map(_.toString) ++ characters(code))
      .map(c => measure.width(c, size, Weight.SemiBold)).max

  // a code's characters, whole: a symbol outside the basic plane is one, not two halves
  private def characters(code: String): List[String] =
    code.codePoints.toArray.toList.map(cp => new String(Character.toChars(cp)))

  // (width, height) of a tag's pill
  private[diagram] def tagSize(
    code: String,
    style: Style,
    measure: Measure,
  ): (Double, Double) =
    val size = tagText(style)
    val h    = (CAP_HEIGHT * size + 2 * TAG_AIR_Y) * PT
    val w    = characters(code).size * tagCell(code, style, measure) + 2 * TAG_AIR_X * PT
    (Measure.round(math.max(w, h)), Measure.round(h))

  // the room a node's tag takes beside its icon; nothing without one
  private def tagRoom(
    node: SceneNode,
    style: Style,
    measure: Measure,
  ): Double =
    node.tag.flatMap(_.validCode).fold(0.0)(c => tagSize(c, style, measure)._1 + ICON_AIR)

  // the pill from (x, y), its lower left corner, and its code
  private def drawTag(
    code: String,
    fill: String,
    x: Double,
    y: Double,
    style: Style,
    measure: Measure,
  ): List[Prim] =
    val (w, h)   = tagSize(code, style, measure)
    val cell     = tagCell(code, style, measure)
    val size     = tagText(style)
    val name     = fillName(fill).get
    val ink      = fillName(tagInk(fill)).get
    val chars    = characters(code)
    val left     = x + (w - chars.size * cell) / 2
    val baseline = y + h / 2 - CAP_HEIGHT * size * PT / 2
    Prim.Box(x, y, w, h, h / 2, Ink.Layer(name), role = Role.Inked(name)) ::
    chars.zipWithIndex.map: (c, i) =>
      Prim.Text(
        (left + (i + 0.5) * cell, baseline),
        c,
        size,
        Weight.SemiBold,
        Emphasis.Inked(name = ink),
      )

  end drawTag

  // the ink a tag's code is set in on its fill: the text colour or paper, whichever stands
  // out more, and black or white when neither reaches 4.5:1 (one of them always does)
  private[diagram] def tagInk(fill: String): String =
    val dark  = Notation.palette.toMap.apply("am-text")
    val light = Notation.palette.toMap.apply("am-paper")
    val best  = List(dark, light).maxBy(contrast(fill, _))
    if contrast(fill, best) >= 4.5 then best
    else List("000000", "FFFFFF").maxBy(contrast(fill, _))

  // WCAG 2's contrast ratio between two colours (hex)
  private[diagram] def contrast(a: String, b: String): Double = Look.contrast(a, b)

  // a tag's fill and ink, as layers of their own every writer defines
  private def tagColours(tag: CornerTag): List[(String, String)] =
    tag.valid.toList.flatMap((_, fill) =>
      List(fill, tagInk(fill)).flatMap(h => fillName(h).map(_ -> h))
    )

  private val TAG_TEXT   = 0.72 // a tag's code against the name's size
  private val CAP_HEIGHT = 0.72 // a capital's height against its size
  private val TAG_AIR_X  = 1.8  // points either side of the code
  private val TAG_AIR_Y  = 1.5  // points above and under the caps

  // A fill colour as the name of a layer of its own, so a box in it is drawn as any
  // other (its edge, its fall-off and its icon mixed from it); None for what is not a
  // colour.
  private[diagram] def fillName(hex: String): Option[String] =
    Option.when(hex.stripPrefix("#").matches("[0-9A-Fa-f]{6}"))(s"am-fill-${hexOf(hex).toLowerCase}")

  private def hexOf(hex: String): String = hex.stripPrefix("#").toUpperCase

  // a node's own outline as the drawing's dash and pen; its colour a layer of its own,
  // as a fill is
  private[diagram] def dashOf(stroke: Stroke): Dash = stroke match
    case Stroke.Solid  => Dash.Solid
    case Stroke.Dashed => Dash.Dashed
    case Stroke.Dotted => Dash.Dotted

  private def outlineColour(outline: Option[Outline]): Option[(String, String)] =
    outline.flatMap(_.validColour).flatMap(h => fillName(h).map(_ -> h))

  // the colours a legend's outline samples are drawn in
  private def legendColours(legend: Option[Legend]): List[(String, String)] =
    legend.toList.flatMap(_.sections).flatMap(_.items).flatMap:
      case LegendItem.Outlined(o, _, _)  => outlineColour(Some(o))
      case LegendItem.Tagged(t, _)       => tagColours(t)
      case LegendItem.Swatch(fill, _, _) => fillName(fill).map(_ -> hexOf(fill)).toList
      case _                             => Nil

  private[diagram] def penOf(outline: Option[Outline]): Pen =
    outline.fold(Pen.Normal)(o => Pen(o.effectiveWeight, o.validColour.flatMap(fillName)))

  // The legend, smaller than what it explains (LEGEND_SCALE), placed where the drawing
  // keeps room for it: in a corner of the drawing that nothing is drawn in, if one is
  // free; else beside a drawing that is taller than wide, as narrow as its height allows,
  // or under a wider one, as low as its width allows; so it never makes a tall drawing
  // taller or a wide one wider. Inside, its title, its badges, then each section under
  // its heading, the items of each in columns, every mark centred in a column as wide as
  // the widest of its own.
  private def legend(
    l: Legend,
    drawn: List[Prim],
    style: Style,
    measure: Measure,
  ): List[Prim] =
    val small        = style.copy(badgeSize = style.badgeSize * LEGEND_SCALE)
    val labelSize    = style.noteSize * LEGEND_SCALE
    val headSize     = style.noteSize * LEGEND_SCALE
    val titleSize    = style.captionSize
    val pad          = style.padding * LEGEND_SCALE
    val bounds       = drawn.flatMap(extent)
    val (minX, maxX) =
      if bounds.isEmpty then (0.0, 0.0) else (bounds.map(_._1).min, bounds.map(_._3).max)
    val (minY, maxY) =
      if bounds.isEmpty then (0.0, 0.0) else (bounds.map(_._2).min, bounds.map(_._4).max)
    // what the legend may not cover: boxes, frames, marks, label patches, and the lines
    val taken        = drawn.flatMap:
      case Prim.Connector(ps, _, _, _, _, _) =>
        ps.zip(ps.drop(1)).map((a, b) =>
          (math.min(a._1, b._1), math.min(a._2, b._2), math.max(a._1, b._1), math.max(a._2, b._2))
        )
      case _: Prim.Text                      => Nil
      case p                                 => extent(p).toList
    val groups       =
      Option.when(l.entries.nonEmpty)((None, l.entries.map(LegendItem.Mark(_)))).toList ++
      l.sections.filter(_.items.nonEmpty).map(s => (Some(s.title), s.items))
    final case class Group(
      heading: Option[String],
      items: List[LegendItem],
      markW: Double,
      markH: Double,
      entryW: Double,
      rowH: Double)
    val measured     = groups.map: (heading, items) =>
      val marks = items.map(itemMark(_, small, measure))
      val markW = marks.map(_._1).max
      val markH = marks.map(_._2).max
      Group(
        heading,
        items,
        markW,
        markH,
        items.map(i => markW + 0.1 + measure.width(i.label, labelSize, Weight.Regular)).max + 0.22,
        math.max(markH, style.lead(labelSize)) + BADGE_GAP * LEGEND_SCALE,
      )
    val titleW       = measure.width(l.title, titleSize, Weight.SemiBold)
    val headW        = groups.flatMap(_._1).map(measure.width(_, headSize, Weight.SemiBold))
    val titleH       = style.lead(titleSize)
    val headH        = style.lead(headSize) + style.gap * LEGEND_SCALE
    // the legend laid out within `room` of width: its size, and how to draw it from its
    // top left corner
    def laid(room: Double): (Double, Double, (Double, Double) => List[Prim]) =
      val columns = measured.map(g =>
        math.max(1, math.min(g.items.size, ((room - 2 * pad) / g.entryW).toInt))
      )
      val heights = measured.zip(columns).map: (g, c) =>
        g.heading.fold(0.0)(_ => headH) + ((g.items.size + c - 1) / c) * g.rowH
      val w = (titleW :: headW ++ measured.zip(columns).map((g, c) => c * g.entryW)).max + 2 * pad
      val h = titleH + heights.map(_ + style.gap * LEGEND_SCALE).sum + 2 * pad
      def draw(left: Double, top: Double): List[Prim] =
        val frame = Prim.Box(left, top - h, w, h, SQUARE, Ink.Paper, role = Role.Plain)
        val title = stack(
          left + pad,
          top - pad,
          List(Line(l.title, titleSize, Weight.SemiBold)),
          style,
          centred = false,
        )
        val body  =
          measured.zip(columns).zip(heights).foldLeft((top - pad - titleH, List.empty[Prim])):
            case ((yStart, done), ((g, cols), height)) =>
              val heading = g.heading.toList.flatMap: t =>
                stack(
                  left + pad,
                  yStart - style.gap * LEGEND_SCALE,
                  List(Line(t, headSize, Weight.SemiBold, Emphasis.Muted)),
                  style,
                  centred = false,
                )
              val start   = yStart - g.heading.fold(0.0)(_ => headH)
              val items   = g.items.zipWithIndex.flatMap: (item, i) =>
                val (row, column) = (i / cols, i % cols)
                val x             = left + pad + column * g.entryW
                val yTop          = start - row * g.rowH
                val (mw, mh)      = itemMark(item, small, measure)
                drawItem(item, x + (g.markW - mw) / 2, yTop - (g.markH + mh) / 2, small, measure) ++
                stack(
                  x + g.markW + 0.1,
                  yTop - (g.markH - style.lead(labelSize)) / 2,
                  List(Line(item.label, labelSize)),
                  style,
                  centred = false,
                )
              (yStart - height - style.gap * LEGEND_SCALE, done ++ heading ++ items)
        frame :: title ++ body._2
      end draw
      (w, h, draw)
    end laid
    val (drawW, drawH) = (maxX - minX, maxY - minY)
    val narrowest                                                            = laid(0)._1
    val widths = List(drawW, drawW * 0.7, drawW * 0.5, drawW * 0.35, narrowest)
      .filter(_ >= narrowest).distinct
    def free(
      x0: Double,
      y0: Double,
      x1: Double,
      y1: Double,
    ): Boolean =
      x0 >= minX - 1e-9 && x1 <= maxX + 1e-9 && y0 >= minY - 1e-9 && y1 <= maxY + 1e-9 &&
      taken.forall(
        (
          a,
          b,
          c,
          d,
        ) =>
          x1 + LEGEND_AIR <= a || x0 - LEGEND_AIR >= c || y1 + LEGEND_AIR <= b ||
          y0 - LEGEND_AIR >= d
      )
    // a free corner: bottom right, bottom left, top right, top left; the widest that fits
    val corner = (for
      (right, bottom) <- List((true, true), (false, true), (true, false), (false, false))
      room            <- widths
    yield
      val (w, h, draw) = laid(room)
      val left         = if right then maxX - w else minX
      val top          = if bottom then minY + h else maxY
      Option.when(free(left, top - h, left + w, top))(draw(left, top))
    ).flatten.headOption
    corner.getOrElse:
      if drawH > drawW then
        // beside: as narrow as fits the drawing's height, its top on the drawing's top;
        // a long legend takes more columns, wider than the drawing if it must, before it
        // would hang below it
        val widest = narrowest * (groups.map(_._2.size).maxOption.getOrElse(1) + 1)
        val rooms  = Iterator.iterate(narrowest)(_ + narrowest / 2).takeWhile(_ <= widest).toList
        val room   = rooms.find(r => laid(r)._2 <= drawH).getOrElse(widest)
        laid(room)._3(maxX + LEGEND_GAP, maxY)
      else
        // under: as low as fits the drawing's width
        laid(math.max(drawW, narrowest))._3(minX, minY - LEGEND_GAP)

  end legend

  private val LEGEND_SCALE = 0.8
  private val LEGEND_AIR   = 0.15

  // the rectangle a prim covers: (x0, y0, x1, y1)
  private def extent(p: Prim): Option[(Double, Double, Double, Double)] =
    def of(ps: List[Pt]) =
      Option.when(ps.nonEmpty)((
        ps.map(_._1).min,
        ps.map(_._2).min,
        ps.map(_._1).max,
        ps.map(_._2).max,
      ))
    p match
      case Prim.Poly(ps, _, _, _, _, _)  => of(ps)
      case Prim.Circle(c, r, _, _)       => Some((c._1 - r, c._2 - r, c._1 + r, c._2 + r))
      case Prim.Ellipse(c, rx, ry, _, _) => Some((c._1 - rx, c._2 - ry, c._1 + rx, c._2 + ry))
      case Prim.Box(x, y, w, h, _, _, _, _, _, _) => Some((x, y, x + w, y + h))
      case Prim.Text(at, _, _, _, _, _, _)        => Some((at._1, at._2, at._1, at._2))
      case Prim.Connector(ps, _, _, _, _, _)      => of(ps)
      case Prim.Curve(segments, _, _)             => of(segments.flatMap(Seg.points))
      // its ink reaches half its line beyond its points
      case Prim.Path(segments, _, w, _, _)        =>
        of(segments.flatMap(Seg.points)).map(
          (
            a,
            b,
            c,
            d,
          ) => (a - w / 2, b - w / 2, c + w / 2, d + w / 2)
        )
      case Prim.Image(x, y, w, h, _, _)           => Some((x, y, x + w, y + h))
    end match

  end extent

  private val SHAPE_W    = 0.5 * LEGEND_SCALE
  private val SHAPE_H    = 0.32 * LEGEND_SCALE
  private val LINE_W     = 0.7 * LEGEND_SCALE
  // a weighted line's sample against a plain one's, and the widest head's span in points
  private val WEIGHTED_W = 1.5
  private val HEAD_SPAN  = 5.4

  // a legend item's mark: (width, height)
  private def itemMark(
    item: LegendItem,
    style: Style,
    measure: Measure,
  ): (Double, Double) = item match
    case LegendItem.Mark(b)            => mark(b, style)
    case LegendItem.Tagged(tag, _)     =>
      tag.validCode.fold((0.0, 0.0))(tagSize(_, style, measure))
    case LegendItem.Shape(_, _)        => (SHAPE_W, SHAPE_H)
    case LegendItem.Outlined(_, _, _)  => (SHAPE_W, SHAPE_H)
    case LegendItem.Faded(_, _)        => (SHAPE_W, SHAPE_H)
    case LegendItem.Swatch(fill, _, _) =>
      if fillName(fill).isDefined then (SHAPE_W, SHAPE_H) else (0.0, 0.0)
    case LegendItem.Line(_, _)         => (LINE_W, style.badgeSize)
    // longer, so its heads, grown with it, leave some line between them; as high as they are
    case LegendItem.Weighted(_, w, _)  =>
      (LINE_W * WEIGHTED_W, math.max(style.badgeSize, HEAD_SPAN * Look.headScale(w) * PT))
    case LegendItem.LogoMark(logo, _)  =>
      val h = style.badgeSize * LOGO_BADGE
      (Logos.width(logo, h), h)

  // a legend item's mark drawn from (x, y), its lower left corner
  private def drawItem(
    item: LegendItem,
    x: Double,
    y: Double,
    style: Style,
    measure: Measure,
  ): List[Prim] = item match
    case LegendItem.Mark(b)                      => drawMark(b, x, y, style, Role.Mark("am-line"))
    case LegendItem.Tagged(tag, _)               =>
      tag.valid.toList.flatMap((code, fill) => drawTag(code, fill, x, y, style, measure))
    case LegendItem.Shape(shape, _)              =>
      val layer  = Notation.layer(shape.group)
      val radius =
        if Notation.outline(shape) == Notation.Outline.Rounded then ROUNDED / 2 else SQUARE
      val s      = SHAPE_H * 0.62
      Prim.Box(x, y, SHAPE_W, SHAPE_H, radius, Ink.Layer(layer), role = Role.Element(layer)) ::
      place(Notation.icon(shape), x + (SHAPE_W - s) / 2, y + (SHAPE_H - s) / 2, s, Role.Mark(layer))
    case LegendItem.Outlined(outline, _, shape)  =>
      val dash = dashOf(outline.dash)
      val pen  = penOf(Some(outline))
      shape match
        case Some(sh) =>
          val layer  = Notation.layer(sh.group)
          val radius =
            if Notation.outline(sh) == Notation.Outline.Rounded then ROUNDED / 2 else SQUARE
          val s      = SHAPE_H * 0.62
          Prim.Box(
            x,
            y,
            SHAPE_W,
            SHAPE_H,
            radius,
            Ink.Layer(layer),
            dash,
            role = Role.Element(layer),
            pen = pen,
          ) ::
          place(Notation.icon(sh), x + (SHAPE_W - s) / 2, y + (SHAPE_H - s) / 2, s, Role.Mark(layer))
        case None     =>
          List(Prim.Box(
            x,
            y,
            SHAPE_W,
            SHAPE_H,
            SQUARE,
            Ink.Paper,
            dash,
            role = Role.Plain,
            pen = pen,
          ))
      end match
    case LegendItem.Line(relationship, _)        =>
      val (dash, start, end) = Notation.ends(relationship)
      val mid                = y + style.badgeSize / 2
      List(Prim.Connector(List((x, mid), (x + LINE_W, mid)), dash, start, end))
    case LegendItem.Weighted(relationship, w, _) =>
      val (dash, start, end) = Notation.ends(relationship)
      val (mw, mh)           = itemMark(item, style, measure)
      val mid                = y + mh / 2
      List(Prim.Connector(
        List((x, mid), (x + mw, mid)),
        dash,
        start,
        end,
        weight = Look.linkWeight(w),
      ))
    case LegendItem.Faded(shape, _)              =>
      val base   = fadedLegendLayer(shape)
      val layer  = Look.faded(Look.Faded.Fill, base)
      val faint  = Role.Faint(Look.faded(Look.Faded.Mark, base))
      val radius =
        shape.filter(Notation.outline(_) == Notation.Outline.Rounded).fold(SQUARE)(_ => ROUNDED / 2)
      val s      = SHAPE_H * 0.62
      Prim.Box(
        x,
        y,
        SHAPE_W,
        SHAPE_H,
        radius,
        Ink.Layer(layer),
        role = Role.Element(layer),
        pen = Pen(ink = Some(Look.faded(Look.Faded.Edge, base))),
      ) :: shape.toList.flatMap(sh =>
        place(Notation.icon(sh), x + (SHAPE_W - s) / 2, y + (SHAPE_H - s) / 2, s, faint)
      )
    case LegendItem.Swatch(fill, _, shape)       =>
      fillName(fill).toList.flatMap: layer =>
        val radius = shape.filter(Notation.outline(_) == Notation.Outline.Rounded).fold(SQUARE)(_ =>
          ROUNDED / 2
        )
        val s      = SHAPE_H * 0.62
        Prim.Box(x, y, SHAPE_W, SHAPE_H, radius, Ink.Layer(layer), role = Role.Element(layer)) ::
        shape.toList.flatMap(sh =>
          place(Notation.icon(sh), x + (SHAPE_W - s) / 2, y + (SHAPE_H - s) / 2, s, Role.Mark(layer))
        )
    case LegendItem.LogoMark(logo, _)            =>
      val (w, h) = itemMark(item, style, measure)
      Logos.place(logo, x, y, w, h)

  // an icon drawn in a unit square, placed at (x, y), scaled to s, as a mark of its box
  private[diagram] def place(
    prims: List[Prim],
    x: Double,
    y: Double,
    s: Double,
    role: Role,
  ): List[Prim] =
    def p(pt: Pt): Pt = (x + pt._1 * s, y + pt._2 * s)
    prims.map:
      case Prim.Poly(points, closed, fill, dash, _, pen) =>
        Prim.Poly(points.map(p), closed, fill, dash, role, pen)
      case Prim.Circle(c, r, fill, _)                    => Prim.Circle(p(c), r * s, fill, role)
      case Prim.Ellipse(c, rx, ry, fill, _) => Prim.Ellipse(p(c), rx * s, ry * s, fill, role)
      case Prim.Box(bx, by, w, h, r, fill, dash, k, _, pen) =>
        Prim.Box(x + bx * s, y + by * s, w * s, h * s, r * s, fill, dash, k, role, pen)
      case Prim.Path(segments, fill, w, _, evenOdd)         =>
        Prim.Path(segments.map(Seg.map(_, p)), fill, w * s, role, evenOdd)
      case other                                            => other

  end place

  // corner radii: a hint of a curve on structure, a clear one on behaviour
  private val SQUARE  = 0.04
  private val ROUNDED = 0.16

end Diagram
