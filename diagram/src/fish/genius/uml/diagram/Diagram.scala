package fish.genius.uml.diagram

import java.awt.Font
import java.awt.font.FontRenderContext

import fish.genius.uml.scene.*

// An ArchiMate graph as a flat diagram: laid out by the same ELK pass as the 3D scene
// (SceneLayout), with each box sized to its name, and drawn as plain geometry (Prim)
// that Tikz and Svg write out. Units are centimetres, x to the right and y up.
object Diagram:

  // How the boxes and their names are measured. The font is the one the document sets
  // its diagrams in; a box is sized from its metrics, so a name fits its box.
  final case class Style(
    fontFamilies: List[String] = List("TeX Gyre Heros", "Nimbus Sans", "Liberation Sans"),
    fontSize: Double = 8.0,
    lineHeight: Double = 1.2,
    boxWidth: Double = 3.0,
    wideWidth: Double = 3.8,
    boxHeight: Double = 1.3,
    padding: Double = 0.16,
    iconSize: Double = 0.34,
    band: Double = 0.24,
    junction: Double = 0.3,
    header: Double = 0.6):
    def lineCm: Double = fontSize * lineHeight * PT

  final case class Options(
    style: Style = Style(),
    nodeSpacing: Double = 0.8,
    layerSpacing: Double = 1.1,
    containerPadding: Double = 0.35,
    maxPerRow: Int = 0)

  // What was drawn, and the scene it was drawn from; `overflow` counts the lines wider
  // than their box (a single word too long even for a widened box cannot happen).
  final case class Drawing(
    name: String,
    prims: List[Prim],
    scene: Scene,
    style: Style,
    overflow: Int)

  val PT: Double = 2.54 / 72.0

  // how much wider a name is set in bold than measured in the regular face
  private val BOLD = 1.1

  def draw(graph: SceneGraph, options: Options = Options()): Either[SceneError, Drawing] =
    val style   = options.style
    val measure = Measure(style)
    val names   = graph.nodes.flatMap(_.flatten).map(n => n.id -> n).toMap
    val sized   =
      names.values.filterNot(_.isContainer).map(n => n.id -> size(n, style, measure)).toMap
    // a container is at least as wide as its name in bold, beside its icon
    val headed  = names.values.filter(_.isContainer).map: n =>
      n.id -> (measure.width(n.label) * BOLD + 3 * style.padding + style.iconSize)
    .toMap
    val layout  = SceneLayout.Options(
      nodeSpacing = options.nodeSpacing,
      layerSpacing = options.layerSpacing,
      containerPadding = options.containerPadding,
      containerHeader = style.header,
      maxPerRow = options.maxPerRow,
      footprint =
        node => sized.get(node.id).map(s => (s.w, s.h)).orElse(headed.get(node.id).map(w => (w, 0.0))),
      headerAtBack = true,
      spreadPorts = true,
    )
    SceneLayout.layout(graph, layout).map: scene =>
      val kinds    = graph.edges.map(e => e.id -> e.relationship).toMap
      val boxes    = scene.elements.flatMap(e =>
        names.get(e.key).toList.flatMap(n => element(e, n, sized.get(n.id), style))
      )
      val lines    = scene.relationships.flatMap: r =>
        for
          kind   <- kinds.get(r.key)
          source <- scene.elements.find(_.key == r.source)
          target <- scene.elements.find(_.key == r.target)
        yield
          val (dash, start, end) = Notation.ends(kind)
          val from               = r.start.fold(port(source, r.sourcePort))(p => (p.x, p.y))
          val to                 = r.end.fold(port(target, r.targetPort))(p => (p.x, p.y))
          val points             = from :: r.bends.map(p => (p.x, p.y)) ::: List(to)
          Prim.Connector(points, dash, start, end)
      val overflow = sized.values.map(_.overflow).sum
      Drawing(graph.name, boxes ++ lines, scene, style, overflow)

  end draw

  // the middle of a box's side, where a connector meets it
  private def port(e: SceneElement, side: PortName): Pt = side match
    case PortName.North => (e.x, e.y + e.d / 2)
    case PortName.South => (e.x, e.y - e.d / 2)
    case PortName.West  => (e.x - e.w / 2, e.y)
    case PortName.East  => (e.x + e.w / 2, e.y)

  final private case class Size(
    w: Double,
    h: Double,
    lines: List[String],
    overflow: Int)

  // A box is boxWidth wide, or wideWidth when its name would take more than two lines,
  // or wider still for a single word that does not fit; as tall as its lines need.
  private def size(
    node: SceneNode,
    style: Style,
    measure: Measure,
  ): Size = node.kind match
    case NodeKind.AndJunction | NodeKind.OrJunction =>
      Size(style.junction, style.junction, Nil, 0)
    case kind                                       =>
      val room          = 2 * (style.padding + style.iconSize)
      def at(w: Double) = measure.wrap(node.label, w - room)
      val first         = at(style.boxWidth)
      val (w0, lines)   =
        if first.size > 2 then (style.wideWidth, at(style.wideWidth)) else (style.boxWidth, first)
      val widest        = lines.map(measure.width).maxOption.getOrElse(0.0)
      val w             = math.max(w0, widest + room)
      val band          = kind match
        case NodeKind.Element(s) if Notation.outline(s) == Notation.Outline.Banded => style.band
        case _                                                                     => 0.0
      val h = math.max(style.boxHeight, lines.size * style.lineCm + 2 * style.padding + 2 * band)
      Size(
        Measure.round(w),
        Measure.round(h),
        lines,
        lines.count(l => measure.width(l) > w - room + 1e-6),
      )

  private def element(
    e: SceneElement,
    node: SceneNode,
    size: Option[Size],
    style: Style,
  ): List[Prim] =
    val (x0, y0) = (e.x - e.w / 2, e.y - e.d / 2)
    val top      = y0 + e.d
    node.kind match
      case NodeKind.AndJunction    => List(Prim.Circle((e.x, e.y), e.w / 2, Ink.Line))
      case NodeKind.OrJunction     => List(Prim.Circle((e.x, e.y), e.w / 2, Ink.Paper))
      case NodeKind.Grouping       =>
        List(
          Prim.Box(x0, y0, e.w, e.d, 0.0, Ink.Empty, Dash.Dashed),
          Prim.Label(
            (x0 + style.padding, top - style.header / 2),
            List(node.label),
            centred = false,
            bold = true,
          ),
        )
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
        val label   =
          if e.container then
            Prim.Label(
              (x0 + style.padding, top - style.header / 2),
              List(node.label),
              centred = false,
              bold = true,
            )
          else
            val band =
              if Notation.outline(shape) == Notation.Outline.Banded then style.band / 2 else 0.0
            Prim.Label((e.x, e.y - band), size.map(_.lines).getOrElse(List(node.label)))
        outline ++ icon :+ label
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
      case Prim.Poly(points, closed, fill, dash) => Prim.Poly(points.map(p), closed, fill, dash)
      case Prim.Circle(c, r, fill)               => Prim.Circle(p(c), r * s, fill)
      case Prim.Ellipse(c, rx, ry, fill)         => Prim.Ellipse(p(c), rx * s, ry * s, fill)
      case Prim.Box(bx, by, w, h, r, fill, dash) =>
        Prim.Box(x + bx * s, y + by * s, w * s, h * s, r * s, fill, dash)
      case other                                 => other

end Diagram

// Text measured in the diagram's font, in centimetres, and wrapped greedily by words.
final class Measure(style: Diagram.Style):

  // the first of the style's families installed, else the JVM's own sans
  val family: String =
    val installed =
      java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames.toSet
    style.fontFamilies.find(installed).getOrElse(Font.SANS_SERIF)

  private val font    = Font(family, Font.PLAIN, 100)
  private val context = FontRenderContext(null, true, true)

  def width(text: String): Double =
    font.getStringBounds(text, context).getWidth / 100.0 * style.fontSize * Diagram.PT

  def wrap(text: String, room: Double): List[String] =
    text.trim.split("\\s+").toList.filter(_.nonEmpty).foldLeft(List.empty[String]):
      case (Nil, word)                                           => List(word)
      case (last :: done, word) if width(s"$last $word") <= room => s"$last $word" :: done
      case (lines, word)                                         => word :: lines
    .reverse

end Measure

object Measure:
  def round(value: Double): Double = math.ceil(value * 100) / 100
