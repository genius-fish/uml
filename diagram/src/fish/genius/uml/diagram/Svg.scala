package fish.genius.uml.diagram

import java.util.Locale

// A Drawing as an SVG document, the same geometry as the TikZ picture: centimetres
// become CSS pixels (96 to the inch) and y turns to run down the page. The heads are
// drawn as shapes (Heads), so a viewer without marker support shows them too.
object Svg:

  private val PX     = 96 / 2.54
  private val MARGIN = 0.15

  def document(drawing: Diagram.Drawing, palette: Map[String, String] = Map.empty): String =
    val colours                  = Notation.colours(palette).toMap
    val (minX, minY, maxX, maxY) = Extent.of(drawing, MARGIN)
    def x(v: Double)             = f((v - minX) * PX)
    def y(v: Double)             = f((maxY - v) * PX)
    def len(v: Double)           = f(v * PX)
    def points(ps: List[Pt])     = ps.map(q => s"${x(q._1)},${y(q._2)}").mkString(" ")
    val line                     = s"#${colours("am-line")}"
    def ink(fill: Ink): String   = fill match
      case Ink.Empty       => "none"
      case Ink.Paper       => "#FFFFFF"
      case Ink.Line        => line
      case Ink.Layer(name) => s"#${colours.getOrElse(name, "FFFFFF")}"
    def dash(d: Dash): String    = d match
      case Dash.Solid  => ""
      case Dash.Dashed => """ stroke-dasharray="4 2.7""""
      case Dash.Dotted => """ stroke-dasharray="1.1 2.1""""
    def prim(p: Prim): String    = p match
      case Prim.Poly(ps, closed, fill, d)             =>
        val tag = if closed then "polygon" else "polyline"
        s"""<$tag points="${points(ps)}" fill="${ink(fill)}" stroke-width="0.53"${dash(d)}/>"""
      case Prim.Circle(c, r, fill)                    =>
        s"""<circle cx="${x(c._1)}" cy="${y(c._2)}" r="${len(r)}" fill="${ink(
            fill
          )}" stroke-width="0.53"/>"""
      case Prim.Ellipse(c, rx, ry, fill)              =>
        s"""<ellipse cx="${x(c._1)}" cy="${y(c._2)}" rx="${len(rx)}" ry="${len(ry)}" fill="${ink(
            fill
          )}" stroke-width="0.53"/>"""
      case Prim.Box(bx, by, w, h, r, fill, d, stroke) =>
        val outline = if stroke then "" else """ stroke="none""""
        s"""<rect x="${x(bx)}" y="${y(by + h)}" width="${len(w)}" height="${len(h)}" rx="${len(
            r
          )}" fill="${ink(fill)}"$outline${dash(d)}/>"""
      case Prim.Text(at, text, size, bold, centred)   =>
        val anchor = if centred then "middle" else "start"
        val weight = if bold then """ font-weight="bold"""" else ""
        s"""<text x="${x(at._1)}" y="${y(at._2)}" font-size="${f(size * 96 /
            72)}" text-anchor="$anchor"$weight stroke="none" fill="$line">${escape(text)}</text>"""
      case c @ Prim.Connector(ps, d, _, _)            =>
        (s"""<polyline points="${points(ps)}" fill="none"${dash(d)}/>""" ::
        Heads.of(c).map(prim)).mkString("\n")
    List(
      s"""<svg xmlns="http://www.w3.org/2000/svg" width="${len(maxX - minX)}" height="${len(maxY -
          minY)}" viewBox="0 0 ${len(maxX - minX)} ${len(maxY - minY)}">""",
      s"<title>${escape(drawing.name)}</title>",
      s"""<g stroke="$line" stroke-width="0.67" stroke-linejoin="round" font-family="'${escape(
          drawing.family
        ).replace("'", "")}', Helvetica, Arial, sans-serif">""",
      drawing.prims.map(prim).mkString("\n"),
      "</g>",
      "</svg>",
    ).mkString("\n")

  end document

  private def f(value: Double): String =
    String.format(Locale.ROOT, "%.2f", Double.box(value)).replaceAll("\\.?0+$", "")

  def escape(text: String): String =
    Notation.clean(text).replace(
      "&",
      "&amp;",
    ).replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

end Svg

// The rectangle a drawing covers, with a margin round it: (minX, minY, maxX, maxY).
object Extent:

  def of(drawing: Diagram.Drawing, margin: Double): (Double, Double, Double, Double) =
    val points = drawing.prims.flatMap:
      case Prim.Poly(ps, _, _, _)           => ps
      case Prim.Circle(c, r, _)             => List((c._1 - r, c._2 - r), (c._1 + r, c._2 + r))
      case Prim.Ellipse(c, rx, ry, _)       => List((c._1 - rx, c._2 - ry), (c._1 + rx, c._2 + ry))
      case Prim.Box(x, y, w, h, _, _, _, _) => List((x, y), (x + w, y + h))
      case Prim.Text(at, _, _, _, _)        => List(at)
      case Prim.Connector(ps, _, _, _)      => ps
    if points.isEmpty then (0.0, 0.0, 1.0, 1.0)
    else
      (
        points.map(_._1).min - margin,
        points.map(_._2).min - margin,
        points.map(_._1).max + margin,
        points.map(_._2).max + margin,
      )

  end of

end Extent
