package fish.genius.uml.diagram

import java.util.Locale

// A Drawing as an SVG document, the same geometry as the TikZ picture: centimetres
// become CSS pixels (96 to the inch) and y turns to run down the page.
object Svg:

  private val PX     = 96 / 2.54
  private val MARGIN = 0.15

  def document(drawing: Diagram.Drawing, palette: Map[String, String] = Map.empty): String =
    val colours                = Notation.palette.toMap ++ palette
    val points                 = drawing.prims.flatMap(extent)
    val (minX, maxX)           = (points.map(_._1).min - MARGIN, points.map(_._1).max + MARGIN)
    val (minY, maxY)           = (points.map(_._2).min - MARGIN, points.map(_._2).max + MARGIN)
    def x(v: Double)           = f((v - minX) * PX)
    def y(v: Double)           = f((maxY - v) * PX)
    def len(v: Double)         = f(v * PX)
    def ink(fill: Ink): String = fill match
      case Ink.Empty       => "none"
      case Ink.Paper       => "#FFFFFF"
      case Ink.Line        => s"#${colours("am-line")}"
      case Ink.Layer(name) => s"#${colours.getOrElse(name, "FFFFFF")}"
    def dash(d: Dash): String  = d match
      case Dash.Solid  => ""
      case Dash.Dashed => """ stroke-dasharray="4 2.7""""
      case Dash.Dotted => """ stroke-dasharray="1.1 2.1""""
    val style                  = drawing.style
    val fontPx                 = style.fontSize * 96 / 72
    def prim(p: Prim): String  = p match
      case Prim.Poly(ps, closed, fill, d)       =>
        val tag = if closed then "polygon" else "polyline"
        s"""<$tag points="${ps.map(q => s"${x(q._1)},${y(q._2)}").mkString(" ")}" fill="${ink(
            fill
          )}" stroke-width="0.53"${dash(d)}/>"""
      case Prim.Circle(c, r, fill)              =>
        s"""<circle cx="${x(c._1)}" cy="${y(c._2)}" r="${len(r)}" fill="${ink(
            fill
          )}" stroke-width="0.53"/>"""
      case Prim.Ellipse(c, rx, ry, fill)        =>
        s"""<ellipse cx="${x(c._1)}" cy="${y(c._2)}" rx="${len(rx)}" ry="${len(ry)}" fill="${ink(
            fill
          )}" stroke-width="0.53"/>"""
      case Prim.Box(bx, by, w, h, r, fill, d)   =>
        s"""<rect x="${x(bx)}" y="${y(by + h)}" width="${len(w)}" height="${len(h)}" rx="${len(
            r
          )}" fill="${ink(fill)}"${dash(d)}/>"""
      case Prim.Label(at, lines, centred, bold) =>
        val lead   = fontPx * style.lineHeight
        val first  = (at._2 - maxY) * -PX - (lines.size - 1) * lead / 2 + fontPx * 0.35
        val anchor = if centred then "middle" else "start"
        val weight = if bold then """ font-weight="bold"""" else ""
        val spans  = lines.zipWithIndex.map: (line, i) =>
          s"""<tspan x="${x(at._1)}" y="${f(first + i * lead)}">${escape(line)}</tspan>"""
        s"""<text text-anchor="$anchor"$weight stroke="none" fill="#${colours(
            "am-line"
          )}">${spans.mkString}</text>"""
      case Prim.Connector(ps, d, start, end)    =>
        val marks = start.map(h => s""" marker-start="url(#${h.toString})"""").getOrElse("") +
          end.map(h => s""" marker-end="url(#${h.toString})"""").getOrElse("")
        s"""<polyline points="${ps.map(q => s"${x(q._1)},${y(q._2)}").mkString(
            " "
          )}" fill="none"${dash(d)}$marks/>"""
    val line                   = s"#${colours("am-line")}"
    val markers                = Head.values.toList.map(h => marker(h, line)).mkString("\n")
    List(
      s"""<svg xmlns="http://www.w3.org/2000/svg" width="${len(maxX - minX)}" height="${len(maxY -
          minY)}" viewBox="0 0 ${len(maxX - minX)} ${len(maxY - minY)}">""",
      s"<title>${escape(drawing.name)}</title>",
      s"<defs>\n$markers\n</defs>",
      s"""<g stroke="$line" stroke-width="0.67" stroke-linejoin="round" font-family="'TeX Gyre Heros', Helvetica, Arial, sans-serif" font-size="${f(
          fontPx
        )}">""",
      drawing.prims.map(prim).mkString("\n"),
      "</g>",
      "</svg>",
    ).mkString("\n")

  end document

  // the arrowheads, pointing along +x with the tip at the line's end
  private def marker(head: Head, line: String): String =
    val (w, h, body) = head match
      case Head.FilledDiamond =>
        (10.7, 6.7, s"""<polygon points="0,3.35 5.35,0 10.7,3.35 5.35,6.7" fill="$line"/>""")
      case Head.OpenDiamond   => (
          10.7,
          6.7,
          s"""<polygon points="0,3.35 5.35,0 10.7,3.35 5.35,6.7" fill="#FFFFFF" stroke="$line"/>""",
        )
      case Head.Dot => (4.7, 4.7, s"""<circle cx="2.35" cy="2.35" r="2.35" fill="$line"/>""")
      case Head.FilledTriangle =>
        (6.7, 5.3, s"""<polygon points="0,0 6.7,2.65 0,5.3" fill="$line"/>""")
      case Head.OpenTriangle   =>
        (9.3, 8.0, s"""<polygon points="0.5,0.5 8.8,4 0.5,7.5" fill="#FFFFFF" stroke="$line"/>""")
      case Head.Barb           => (
          5.3,
          6.7,
          s"""<polyline points="0,0 5.3,3.35 0,6.7" fill="none" stroke="$line" stroke-width="0.67"/>""",
        )
    val refX         = head match
      case Head.Dot => w / 2
      case _        => w
    s"""<marker id="${head.toString}" markerUnits="userSpaceOnUse" markerWidth="${f(w +
        1)}" markerHeight="${f(h + 1)}" refX="${f(refX)}" refY="${f(h /
        2)}" orient="auto-start-reverse" overflow="visible">$body</marker>"""

  end marker

  private def extent(p: Prim): List[Pt] = p match
    case Prim.Poly(ps, _, _, _)        => ps
    case Prim.Circle(c, r, _)          => List((c._1 - r, c._2 - r), (c._1 + r, c._2 + r))
    case Prim.Ellipse(c, rx, ry, _)    => List((c._1 - rx, c._2 - ry), (c._1 + rx, c._2 + ry))
    case Prim.Box(x, y, w, h, _, _, _) => List((x, y), (x + w, y + h))
    case Prim.Label(at, _, _, _)       => List(at)
    case Prim.Connector(ps, _, _, _)   => ps

  private def f(value: Double): String =
    String.format(Locale.ROOT, "%.2f", Double.box(value)).replaceAll("\\.?0+$", "")

  def escape(text: String): String =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

end Svg
