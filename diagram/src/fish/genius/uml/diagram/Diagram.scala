package fish.genius.uml.diagram

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
    gap: Double = 0.08):
    def lead(size: Double): Double = size * lineHeight * PT

  end Style

  // pageWidth is the width a diagram should keep within, in centimetres: a row wider
  // folds, so the diagram is printed at its own size rather than scaled down
  final case class Options(
    style: Style = Style(),
    nodeSpacing: Double = 0.8,
    layerSpacing: Double = 1.1,
    containerPadding: Double = 0.35,
    maxPerRow: Int = 0,
    pageWidth: Double = 13.0)

  // What was drawn, and the scene it was drawn from; `overflow` counts the lines wider
  // than their room, which only a single word longer than the widest box can make.
  final case class Drawing(
    name: String,
    prims: List[Prim],
    scene: Scene,
    style: Style,
    family: String,
    overflow: Int)

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
              e.w - 3 * style.padding - style.iconSize,
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
          (r, points, Prim.Connector(points, dash, start, end))
      // what a label may not cover: the leaves, and the containers' headers
      val taken              = scene.elements.map: e =>
        val top = e.y + e.d / 2
        (e.x - e.w / 2, if e.container then top - tall else e.y - e.d / 2, e.x + e.w / 2, top)
      // the field a point lies on: the innermost container or grouping round it, or the page
      val fields             = scene.elements.filter(_.container).flatMap: e =>
        byNode.get(e.key).map: n =>
          val role = n.kind match
            case NodeKind.Element(shape) => Role.Container(Notation.layer(shape.group))
            case _                       => Role.Group
          (e, role)
      def field(p: Pt): Role =
        fields
          .filter((e, _) => math.abs(p._1 - e.x) <= e.w / 2 && math.abs(p._2 - e.y) <= e.d / 2)
          .minByOption((e, _) => e.w * e.d)
          .fold(Role.Halo)(_._2)
      val said               = routes.flatMap:
        (
          r,
          points,
          _,
        ) =>
          r.label.toList.flatMap(l => label(l, labels.get(l.text), points, taken, style, field))
      val gaps               = crossings(routes.map(r => (r._2, r._3)), field)
      val overflow           = sized.values.map(_.overflow).sum
      // drawn back to front: containers and groupings, then the boxes, then the lines
      // between them, then every word, and the connectors' labels on top
      def isText(p: Prim)    = p.isInstanceOf[Prim.Text]
      def isField(p: Prim)   = p match
        case Prim.Box(_, _, _, _, _, _, _, _, Role.Container(_)) => true
        case Prim.Poly(_, _, _, _, Role.Group)                   => true
        case _                                                   => false
      val (backs, rest)      = boxes.filterNot(isText).partition(isField)
      val ordered = backs ++ rest ++ routes.map(_._3) ++ gaps ++ boxes.filter(isText) ++ said
      Drawing(graph.name, ordered, scene, style, measure.family, overflow)
    end for

  end draw

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
  private def crossings(routes: List[(List[Pt], Prim)], field: Pt => Role): List[Prim] =
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
      case (x, y, Prim.Connector(_, dash, _, _)) =>
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
          Prim.Connector(List((x - GAP * 1.6, y), (x + GAP * 1.6, y)), dash, None, None),
        )
      case _                                     => Nil

  end crossings

  // the middle of a box's side, where a connector meets it
  private def port(e: SceneElement, side: PortName): Pt = side match
    case PortName.North => (e.x, e.y + e.d / 2)
    case PortName.South => (e.x, e.y - e.d / 2)
    case PortName.West  => (e.x - e.w / 2, e.y)
    case PortName.East  => (e.x + e.w / 2, e.y)

  // one line to set: its text, its size, its weight and its tone
  final private case class Line(
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
      val clear            = 2 * (style.padding + style.iconSize + ICON_AIR)
      val full             = 2 * style.padding
      def lines(w: Double) =
        measure
          .wrap(node.label, w - clear, style.nameSize, Weight.Medium)
          .map(Line(_, style.nameSize, Weight.Medium)) ++
        node.caption.toList.flatMap(c =>
          measure.wrap(
            c,
            w - clear,
            style.captionSize,
          ).map(Line(_, style.captionSize, tone = Emphasis.Muted))
        ) ++
        node.note.toList.flatMap(n =>
          measure.wrap(n, w - full, style.noteSize).map(Line(_, style.noteSize))
        )
      def room(l: Line)    = if l.size == style.noteSize then full else clear
      val narrow = measure.wrap(node.label, style.boxWidth - clear, style.nameSize, Weight.Medium)
      val w0     = if node.note.nonEmpty || narrow.size > 2 then style.wideWidth else style.boxWidth
      val set    = lines(w0)
      val w      = (w0 :: set.map(l => measure.width(l.text, l.size, l.weight) + room(l))).max
      val band   = kind match
        case NodeKind.Element(s) if Notation.outline(s) == Notation.Outline.Banded => style.band
        case _                                                                     => 0.0
      val noteGap = if node.note.nonEmpty then style.gap else 0.0
      val h      =
        math.max(
          style.boxHeight,
          set.map(l => style.lead(l.size)).sum + noteGap + 2 * style.padding + band,
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
  private def stack(
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
          Dash.Dashed,
          Role.Group,
        ) :: heading(x0 + style.padding, tabH, Role.Group)
      case NodeKind.Element(shape) =>
        val layer   = Notation.layer(shape.group)
        val fill    = Ink.Layer(layer)
        val role    = if e.container then Role.Container(layer) else Role.Element(layer)
        val outline = Notation.outline(shape) match
          case Notation.Outline.Square => List(Prim.Box(x0, y0, e.w, e.d, SQUARE, fill, role = role))
          case Notation.Outline.Rounded =>
            List(Prim.Box(x0, y0, e.w, e.d, ROUNDED, fill, role = role))
          case Notation.Outline.Banded  =>
            List(
              Prim.Box(x0, y0, e.w, e.d, SQUARE, fill, role = role),
              Prim.Poly(
                List((x0, top - style.band), (x0 + e.w, top - style.band)),
                closed = false,
                role = Role.Mark(layer),
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
                role = role,
              )
            )
        val s       = style.iconSize
        val icon    =
          place(
            Notation.icon(shape),
            x0 + e.w - style.padding - s,
            top - style.padding - s,
            s,
            Role.Mark(layer),
          )
        val text    =
          if e.container then heading(x0 + style.padding, header, Role.Container(layer))
          else
            val lines  =
              box.map(_.lines).getOrElse(List(Line(node.label, style.nameSize, Weight.Medium)))
            val band   =
              if Notation.outline(shape) == Notation.Outline.Banded then style.band else 0.0
            val noteAt =
              if node.note.nonEmpty then lines.indexWhere(_.size == style.noteSize) else -1
            val height = lines.map(l => style.lead(l.size)).sum +
              (if noteAt >= 0 then style.gap else 0.0)
            stack(e.x, e.y - band / 2 + height / 2, lines, style, centred = true, gapBefore = noteAt)
        outline ++ icon ++ text
    end match

  end element

  // an icon drawn in a unit square, placed at (x, y), scaled to s, as a mark of its box
  private def place(
    prims: List[Prim],
    x: Double,
    y: Double,
    s: Double,
    role: Role,
  ): List[Prim] =
    def p(pt: Pt): Pt = (x + pt._1 * s, y + pt._2 * s)
    prims.map:
      case Prim.Poly(points, closed, fill, dash, _) =>
        Prim.Poly(points.map(p), closed, fill, dash, role)
      case Prim.Circle(c, r, fill, _)               => Prim.Circle(p(c), r * s, fill, role)
      case Prim.Ellipse(c, rx, ry, fill, _) => Prim.Ellipse(p(c), rx * s, ry * s, fill, role)
      case Prim.Box(bx, by, w, h, r, fill, dash, k, _) =>
        Prim.Box(x + bx * s, y + by * s, w * s, h * s, r * s, fill, dash, k, role)
      case other                                       => other

  end place

  // corner radii: a hint of a curve on structure, a clear one on behaviour
  private val SQUARE  = 0.04
  private val ROUNDED = 0.16

end Diagram
