package fish.genius.uml.diagram

import java.awt.{Font, GraphicsEnvironment}
import java.awt.font.FontRenderContext

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
    fontFamilies: List[String] = List("TeX Gyre Heros", "Nimbus Sans", "Liberation Sans"),
    nameSize: Double = 8.0,
    captionSize: Double = 6.5,
    noteSize: Double = 6.0,
    labelSize: Double = 6.5,
    lineHeight: Double = 1.2,
    boxWidth: Double = 3.0,
    wideWidth: Double = 3.8,
    boxHeight: Double = 1.3,
    labelWidth: Double = 3.6,
    padding: Double = 0.16,
    iconSize: Double = 0.34,
    band: Double = 0.24,
    junction: Double = 0.3,
    header: Double = 0.6,
    gap: Double = 0.08):
    def lead(size: Double): Double = size * lineHeight * PT

  end Style

  final case class Options(
    style: Style = Style(),
    nodeSpacing: Double = 0.8,
    layerSpacing: Double = 1.1,
    containerPadding: Double = 0.35,
    maxPerRow: Int = 0)

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
    val measure = Measure(style.fontFamilies)
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
              bold = true,
            )
        .toMap
    for
      first <- laid(1)
      lines = names(first).values.map(_.size).maxOption.getOrElse(1)
      scene <- if lines > 1 then laid(lines) else Right(first)
    yield
      val headed   = names(scene)
      val tall     = header(math.max(1, lines))
      val byId     = byNode
      val placed   = scene.elements.map(e => e.key -> e).toMap
      val kinds    = graph.edges.map(e => e.id -> e.relationship).toMap
      val boxes    = scene.elements.flatMap: e =>
        byId.get(e.key).toList.flatMap(n =>
          element(e, n, sized.get(n.id), headed.get(e.key), tall, style)
        )
      val routes   = scene.relationships.flatMap: r =>
        for
          kind   <- kinds.get(r.key)
          source <- placed.get(r.source)
          target <- placed.get(r.target)
        yield
          val (dash, start, end) = Notation.ends(kind)
          val from               = r.start.fold(port(source, r.sourcePort))(p => (p.x, p.y))
          val to                 = r.end.fold(port(target, r.targetPort))(p => (p.x, p.y))
          val points             = from :: r.bends.map(p => (p.x, p.y)) ::: List(to)
          (r, points, Prim.Connector(points, dash, start, end))
      // what a label may not cover: the leaves, and the containers' headers
      val taken    = scene.elements.map: e =>
        val top = e.y + e.d / 2
        (e.x - e.w / 2, if e.container then top - tall else e.y - e.d / 2, e.x + e.w / 2, top)
      val said     = routes.flatMap:
        (
          r,
          points,
          _,
        ) =>
          r.label.toList.flatMap(l => label(l, labels.get(l.text), points, taken, style))
      val overflow = sized.values.map(_.overflow).sum
      Drawing(graph.name, boxes ++ routes.map(_._3) ++ said, scene, style, measure.family, overflow)
    end for

  end draw

  // the middle of a box's side, where a connector meets it
  private def port(e: SceneElement, side: PortName): Pt = side match
    case PortName.North => (e.x, e.y + e.d / 2)
    case PortName.South => (e.x, e.y - e.d / 2)
    case PortName.West  => (e.x - e.w / 2, e.y)
    case PortName.East  => (e.x + e.w / 2, e.y)

  // one line to set: its text, its size and whether it is bold
  final private case class Line(
    text: String,
    size: Double,
    bold: Boolean = false)

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
      val clear            = 2 * (style.padding + style.iconSize)
      val full             = 2 * style.padding
      def lines(w: Double) =
        measure.wrap(node.label, w - clear, style.nameSize).map(Line(_, style.nameSize)) ++
        node.caption.toList.flatMap(c =>
          measure.wrap(c, w - clear, style.captionSize).map(Line(_, style.captionSize))
        ) ++
        node.note.toList.flatMap(n =>
          measure.wrap(n, w - full, style.noteSize).map(Line(_, style.noteSize))
        )
      def room(l: Line)    = if l.size == style.noteSize then full else clear
      val narrow           = measure.wrap(node.label, style.boxWidth - clear, style.nameSize)
      val w0   = if node.note.nonEmpty || narrow.size > 2 then style.wideWidth else style.boxWidth
      val set  = lines(w0)
      val w    = (w0 :: set.map(l => measure.width(l.text, l.size) + room(l))).max
      val band = kind match
        case NodeKind.Element(s) if Notation.outline(s) == Notation.Outline.Banded => style.band
        case _                                                                     => 0.0
      val noteGap = if node.note.nonEmpty then style.gap else 0.0
      val h    =
        math.max(
          style.boxHeight,
          set.map(l => style.lead(l.size)).sum + noteGap + 2 * style.padding + band,
        )
      val overflow = set.count(l => measure.width(l.text, l.size) > w - room(l) + 1e-6)
      Box(Measure.round(w), Measure.round(h), set, overflow)

  private def labelOf(
    text: String,
    style: Style,
    measure: Measure,
  ): Label =
    val lines = measure.wrap(text, style.labelWidth, style.labelSize).map(Line(_, style.labelSize))
    val w     = lines.map(l => measure.width(l.text, l.size)).maxOption.getOrElse(0.0) + 0.1
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
  ): List[Prim] =
    lines.zipWithIndex
      .foldLeft((top, List.empty[Prim])):
        case ((y, done), (line, i)) =>
          val lead     = style.lead(line.size)
          val from     = if i == gapBefore then y - style.gap else y
          val baseline = from - lead / 2 - 0.35 * line.size * PT
          (from - lead, Prim.Text((x, baseline), line.text, line.size, line.bold, centred) :: done)
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
  ): List[Prim] =
    val lines                = laid.map(_.lines).getOrElse(List(Line(l.text, style.labelSize)))
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
    Prim.Box(cx - l.w / 2, cy - l.d / 2, l.w, l.d, 0.04, Ink.Paper, stroke = false) ::
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
  ): List[Prim] =
    val (x0, y0) = (e.x - e.w / 2, e.y - e.d / 2)
    val top      = y0 + e.d
    // a container's or grouping's name, bold, and its caption, in its header
    def heading  =
      val said = names.getOrElse(List(node.label)).map(Line(_, style.nameSize, bold = true)) ++
        node.caption.toList.map(Line(_, style.captionSize))
      stack(
        x0 + style.padding,
        top - (header - said.map(l => style.lead(l.size)).sum) / 2,
        said,
        style,
        centred = false,
      )
    node.kind match
      case NodeKind.AndJunction => List(Prim.Circle((e.x, e.y), e.w / 2, Ink.Line))
      case NodeKind.OrJunction  => List(Prim.Circle((e.x, e.y), e.w / 2, Ink.Paper))
      case NodeKind.Grouping => Prim.Box(x0, y0, e.w, e.d, 0.0, Ink.Empty, Dash.Dashed) :: heading
      case NodeKind.Element(shape) =>
        val fill    = Notation.colour(shape.group)
        val outline = Notation.outline(shape) match
          case Notation.Outline.Square  => List(Prim.Box(x0, y0, e.w, e.d, 0.0, fill))
          case Notation.Outline.Rounded => List(Prim.Box(x0, y0, e.w, e.d, 0.18, fill))
          case Notation.Outline.Banded  =>
            List(
              Prim.Box(x0, y0, e.w, e.d, 0.0, fill),
              Prim.Poly(List((x0, top - style.band), (x0 + e.w, top - style.band)), closed = false),
            )
          case Notation.Outline.Cut     =>
            val c = 0.14
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
              )
            )
        val s       = style.iconSize
        val icon    =
          place(Notation.icon(shape), x0 + e.w - style.padding - s, top - style.padding - s, s)
        val text    =
          if e.container then heading
          else
            val lines  = box.map(_.lines).getOrElse(List(Line(node.label, style.nameSize)))
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

  // an icon drawn in a unit square, placed at (x, y) and scaled to s
  private def place(
    prims: List[Prim],
    x: Double,
    y: Double,
    s: Double,
  ): List[Prim] =
    def p(pt: Pt): Pt = (x + pt._1 * s, y + pt._2 * s)
    prims.map:
      case Prim.Poly(points, closed, fill, dash)    => Prim.Poly(points.map(p), closed, fill, dash)
      case Prim.Circle(c, r, fill)                  => Prim.Circle(p(c), r * s, fill)
      case Prim.Ellipse(c, rx, ry, fill)            => Prim.Ellipse(p(c), rx * s, ry * s, fill)
      case Prim.Box(bx, by, w, h, r, fill, dash, k) =>
        Prim.Box(x + bx * s, y + by * s, w * s, h * s, r * s, fill, dash, k)
      case other                                    => other

end Diagram

// Text measured in the first of the families that is installed (else the JVM's own
// sans), in centimetres at a size in points, and wrapped greedily by words.
final class Measure(families: List[String]):

  val family: String =
    val installed =
      GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames.toSet
    families.find(installed).getOrElse(Font.SANS_SERIF)

  private val plain   = Font(family, Font.PLAIN, 100)
  private val heavy   = Font(family, Font.BOLD, 100)
  private val context = FontRenderContext(null, true, true)

  def width(
    text: String,
    size: Double,
    bold: Boolean = false,
  ): Double =
    (if bold then heavy else plain).getStringBounds(text, context).getWidth / 100.0 * size *
    Diagram.PT

  def wrap(
    text: String,
    room: Double,
    size: Double,
    bold: Boolean = false,
  ): List[String] =
    text.trim
      .split("\\s+")
      .toList
      .filter(_.nonEmpty)
      .foldLeft(List.empty[String]):
        case (Nil, word)                                                       => List(word)
        case (last :: done, word) if width(s"$last $word", size, bold) <= room =>
          s"$last $word" :: done
        case (lines, word)                                                     => word :: lines
      .reverse

end Measure

object Measure:
  def round(value: Double): Double = math.ceil(value * 100) / 100
