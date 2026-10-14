package fish.genius.uml.diagram

import java.util.Locale

// A Drawing as an SVG document with the look of the TikZ picture (Look): a box in its
// layer's colour falling off from top to bottom on a soft shadow, a container lighter
// behind what it holds, lines with rounded bends, gaps and labels on the field they
// lie on. Centimetres become CSS pixels (96 to the inch) and y turns to run down the
// page. The heads are drawn as shapes (Heads), so a viewer without marker support
// shows them too.
object Svg:

  private val PX     = 96 / 2.54
  private val PT     = 96.0 / 72.0
  private val MARGIN = 0.15

  def document(drawing: Diagram.Drawing, palette: Map[String, String] = Map.empty): String =
    val c                             = Look.colours(palette)
    val measure                       = Measure(drawing.style.typeface)
    val (minX, minY, maxX, maxY)      = Extent.of(drawing, MARGIN)
    def x(v: Double)                  = f((v - minX) * PX)
    def y(v: Double)                  = f((maxY - v) * PX)
    def len(v: Double)                = f(v * PX)
    def hex(h: String)                = s"#$h"
    def points(ps: List[Pt])          = ps.map(q => s"${x(q._1)},${y(q._2)}").mkString(" ")
    def dash(d: Dash): String         = d match
      case Dash.Solid  => ""
      case Dash.Dashed => s""" stroke-dasharray="${f(2.8 * PT)} ${f(1.8 * PT)}""""
      case Dash.Dotted =>
        s""" stroke-dasharray="0 ${f(1.9 * PT)}" stroke-width="${f(Look.DOT_LINE * PT)}""""
    val layers                        = drawing.prims.collect:
      case Prim.Box(_, _, _, _, _, _, _, _, Role.Element(layer)) => layer
      case Prim.Poly(_, _, _, _, Role.Element(layer))            => layer
    .distinct
    def paint(
      role: Role,
      fill: Ink,
      stroke: Boolean = true,
    ): String                         =
      val (edge, inked) = c.look(role, fill)
      val filled        = role match
        case Role.Element(layer) if fill != Ink.Empty => s"url(#fall-$layer)"
        case _                                        => inked.map(hex).getOrElse("none")
      val drawn         = if stroke then
        edge.map(e => s""" stroke="${hex(e)}" stroke-width="${f(c.weight(role) * PT)}"""").getOrElse(
          """ stroke="none""""
        )
      else """ stroke="none""""
      val shadow        = role match
        case Role.Element(_)   => """ filter="url(#shadow)""""
        case Role.Container(_) => """ filter="url(#faint)""""
        case _                 => ""
      s"""fill="$filled"$drawn$shadow"""
    end paint
    // a polyline with its bends rounded, as TikZ's rounded corners draws it
    def rounded(ps: List[Pt]): String =
      val r     = Look.BEND * 2.54 / 72
      val steps = ps.indices.drop(1).dropRight(1).map: i =>
        val (a, v, b) = (ps(i - 1), ps(i), ps(i + 1))
        def toward(
          from: Pt,
          to: Pt,
          d: Double,
        ): Pt         =
          val l = math.max(math.hypot(to._1 - from._1, to._2 - from._2), 1e-9)
          val k = math.min(d, l / 2) / l
          (from._1 + (to._1 - from._1) * k, from._2 + (to._2 - from._2) * k)
        val (p1, p2)  = (toward(v, a, r), toward(v, b, r))
        s"L ${x(p1._1)} ${y(p1._2)} Q ${x(v._1)} ${y(v._2)} ${x(p2._1)} ${y(p2._2)}"
      (s"M ${x(ps.head._1)} ${y(ps.head._2)}" +: steps :+
      s"L ${x(ps.last._1)} ${y(ps.last._2)}").mkString(" ")
    end rounded
    def prim(p: Prim): String         = p match
      case Prim.Poly(ps, closed, fill, d, role)                       =>
        val tag              = if closed then "polygon" else "polyline"
        val (fillAttr, rest) = paint(role, if closed then fill else Ink.Empty) -> dash(d)
        s"""<$tag points="${points(ps)}" $fillAttr$rest/>"""
      case Prim.Circle(cc, r, fill, role)                             =>
        s"""<circle cx="${x(cc._1)}" cy="${y(cc._2)}" r="${len(r)}" ${paint(role, fill)}/>"""
      case Prim.Ellipse(cc, rx, ry, fill, role)                       =>
        s"""<ellipse cx="${x(cc._1)}" cy="${y(cc._2)}" rx="${len(rx)}" ry="${len(ry)}" ${paint(
            role,
            fill,
          )}/>"""
      case Prim.Box(bx, by, w, h, r, fill, d, stroke, role)           =>
        s"""<rect x="${x(bx)}" y="${y(by + h)}" width="${len(w)}" height="${len(h)}" rx="${len(
            r
          )}" ${paint(role, fill, stroke)}${dash(d)}/>"""
      case Prim.Text(at, text, size, weight, tone, centred, backdrop) =>
        val anchor = if centred then "middle" else "start"
        val face   = weight match
          case Weight.SemiBold => """ font-weight="600""""
          case Weight.Medium   => """ font-weight="500""""
          case Weight.Italic   => """ font-style="italic""""
          case Weight.Regular  => ""
        val ink    = if tone == Emphasis.Muted then c.muted else c.text
        val patch  = backdrop.map: on =>
          val w   = measure.width(text, size, weight)
          val pad = 1.2 * 2.54 / 72
          val x0  = if centred then at._1 - w / 2 - pad else at._1 - pad
          val y0  = at._2 - size * 0.28 * 2.54 / 72
          s"""<rect x="${x(x0)}" y="${y(y0 + size * 1.02 * 2.54 / 72)}" width="${len(w +
              2 * pad)}" height="${len(size * 1.02 * 2.54 / 72)}" fill="${hex(
              c.ground(on)
            )}" stroke="none"/>"""
        patch.getOrElse("") +
        s"""<text x="${x(at._1)}" y="${y(at._2)}" font-size="${f(size *
            PT)}" text-anchor="$anchor"$face stroke="none" fill="${hex(ink)}">${escape(
            text
          )}</text>"""
      case con @ Prim.Connector(ps, d, _, _)                          =>
        (s"""<path d="${rounded(ps)}" fill="none" stroke="${hex(
            c.line
          )}" stroke-width="${f(Look.LINK_LINE * PT)}"${dash(d)}/>""" ::
        Heads.of(con).map(prim)).mkString("\n")
    val defs                          =
      layers.map(l =>
        s"""<linearGradient id="fall-$l" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${hex(
            c.top(l)
          )}"/><stop offset="1" stop-color="${hex(c.of(l))}"/></linearGradient>"""
      ) ++
      List(
        s"""<filter id="shadow" x="-10%" y="-10%" width="130%" height="140%"><feDropShadow dx="${f(
            Look.SHADOW_X * PT
          )}" dy="${f(Look.SHADOW_Y * PT)}" stdDeviation="${f(Look.SHADOW_BLUR * PT /
            2)}" flood-color="#000" flood-opacity="${Look.SHADOW_ALPHA}"/></filter>""",
        s"""<filter id="faint" x="-5%" y="-5%" width="115%" height="120%"><feDropShadow dx="${f(
            0.6 * PT
          )}" dy="${f(0.8 * PT)}" stdDeviation="${f(0.3 *
            PT)}" flood-color="#000" flood-opacity="0.08"/></filter>""",
      )
    List(
      s"""<svg xmlns="http://www.w3.org/2000/svg" width="${len(maxX - minX)}" height="${len(maxY -
          minY)}" viewBox="0 0 ${len(maxX - minX)} ${len(maxY - minY)}">""",
      s"<title>${escape(drawing.name)}</title>",
      s"<defs>${defs.mkString}</defs>",
      // the page the diagram stands on, which its gaps and label patches are cut in
      s"""<rect x="0" y="0" width="${len(maxX - minX)}" height="${len(maxY - minY)}" fill="${hex(
          c.paper
        )}"/>""",
      s"""<g stroke-linejoin="round" stroke-linecap="round" font-family="'${escape(
          drawing.family
        ).replace("'", "")}', Montserrat, Helvetica, Arial, sans-serif">""",
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
      case Prim.Poly(ps, _, _, _, _)     => ps
      case Prim.Circle(c, r, _, _)       => List((c._1 - r, c._2 - r), (c._1 + r, c._2 + r))
      case Prim.Ellipse(c, rx, ry, _, _) => List((c._1 - rx, c._2 - ry), (c._1 + rx, c._2 + ry))
      case Prim.Box(x, y, w, h, _, _, _, _, _) => List((x, y), (x + w, y + h))
      case Prim.Text(at, _, _, _, _, _, _)     => List(at)
      case Prim.Connector(ps, _, _, _)         => ps
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
