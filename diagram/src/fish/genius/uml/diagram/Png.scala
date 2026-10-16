package fish.genius.uml.diagram

import java.awt.{BasicStroke, RenderingHints}
import java.awt.geom.{Ellipse2D, Path2D, RoundRectangle2D}
import java.awt.image.{BufferedImage, ConvolveOp, Kernel}
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// A Drawing as a PNG with the look of the TikZ picture (Look), drawn with Java2D in the
// drawing's own typeface: boxes falling off from top to bottom on a blurred shadow,
// lines with rounded bends, gaps and labels on the field they lie on. For an export
// that has no LaTeX to compile with.
object Png:

  private val MARGIN = 0.2

  def image(
    drawing: Diagram.Drawing,
    dpi: Int = 150,
    palette: Map[String, String] = Map.empty,
  ): Array[Byte] =
    val c                        = Look.colours(palette ++ drawing.colours)
    val scale                    = dpi / 2.54
    val (minX, minY, maxX, maxY) = Extent.of(drawing, MARGIN)
    val (w, h) = (((maxX - minX) * scale).ceil.toInt, ((maxY - minY) * scale).ceil.toInt)
    val faces  = Measure(drawing.style.typeface)
    def x(v: Double): Double                     = (v - minX) * scale
    def y(v: Double): Double                     = (maxY - v) * scale
    def pt(points: Double): Float                = (points * dpi / 72).toFloat
    def colour(hex: String)                      = java.awt.Color(Integer.parseInt(hex, 16))
    def setup(g: java.awt.Graphics2D)            =
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING,
        RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
      )
      g.setRenderingHint(
        RenderingHints.KEY_FRACTIONALMETRICS,
        RenderingHints.VALUE_FRACTIONALMETRICS_ON,
      )
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    def stroke(
      width: Double,
      d: Dash,
      scale: Double = 1.0,
    )                                            = d match
      case Dash.Solid  => BasicStroke(pt(width), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      case Dash.Dashed => BasicStroke(
          pt(width),
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND,
          10f,
          Array(pt(Look.DASH_ON * scale), pt(Look.DASH_OFF * scale)),
          0f,
        )
      case Dash.Dotted => BasicStroke(
          pt(Look.DOT_LINE * scale),
          BasicStroke.CAP_ROUND,
          BasicStroke.JOIN_ROUND,
          10f,
          Array(0.01f, pt(Look.DOT_GAP * scale)),
          0f,
        )
    def path(
      ps: List[Pt],
      closed: Boolean,
      bend: Double = 0.0,
    )                                            =
      val shape = Path2D.Double()
      ps.headOption.foreach(p => shape.moveTo(x(p._1), y(p._2)))
      ps.indices.drop(1).foreach: i =>
        val v = ps(i)
        if bend > 0 && i < ps.size - 1 then
          val (a, b)                       = (ps(i - 1), ps(i + 1))
          def toward(from: Pt, to: Pt): Pt =
            val l = math.max(math.hypot(to._1 - from._1, to._2 - from._2), 1e-9)
            val k = math.min(bend, l / 2) / l
            (from._1 + (to._1 - from._1) * k, from._2 + (to._2 - from._2) * k)
          val (p1, p2)                     = (toward(v, a), toward(v, b))
          shape.lineTo(x(p1._1), y(p1._2))
          shape.quadTo(x(v._1), y(v._2), x(p2._1), y(p2._2))
        else shape.lineTo(x(v._1), y(v._2))
      if closed then shape.closePath()
      shape
    end path
    def shapeOf(p: Prim): Option[java.awt.Shape] = p match
      case Prim.Poly(ps, closed, _, _, _, _)          => Some(path(ps, closed))
      case Prim.Circle(cc, r, _, _)                   =>
        Some(Ellipse2D.Double(x(cc._1 - r), y(cc._2 + r), 2 * r * scale, 2 * r * scale))
      case Prim.Ellipse(cc, rx, ry, _, _)             =>
        Some(Ellipse2D.Double(x(cc._1 - rx), y(cc._2 + ry), 2 * rx * scale, 2 * ry * scale))
      case Prim.Box(bx, by, bw, bh, r, _, _, _, _, _) =>
        Some(RoundRectangle2D.Double(
          x(bx),
          y(by + bh),
          bw * scale,
          bh * scale,
          2 * r * scale,
          2 * r * scale,
        ))
      case _                                          => None

    // the shadows first, on a layer of their own, blurred and laid under the drawing
    val shade   = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val sg      = shade.createGraphics()
    setup(sg)
    drawing.prims.foreach:
      case p @ (Prim.Box(_, _, _, _, _, _, _, _, Role.Element(_), _) | Prim.Poly(
          _,
          true,
          _,
          _,
          Role.Element(_),
          _,
        )) =>
        sg.setColor(java.awt.Color(0f, 0f, 0f, Look.SHADOW_ALPHA.toFloat))
        val t = sg.getTransform
        sg.translate(pt(Look.SHADOW_X).toDouble, pt(Look.SHADOW_Y).toDouble)
        shapeOf(p).foreach(sg.fill)
        sg.setTransform(t)
      case _ => ()
    sg.dispose()
    val radius  = math.max(1, math.round(pt(Look.SHADOW_BLUR) / 1.5f))
    val size    = radius * 2 + 1
    val kernel  = Kernel(size, size, Array.fill(size * size)(1f / (size * size)))
    val blurred = ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null).filter(shade, null)

    val canvas              = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g                   = canvas.createGraphics()
    setup(g)
    g.setColor(colour(c.paper))
    g.fillRect(0, 0, w, h)
    g.drawImage(blurred, 0, 0, null)
    def paint(
      shape: java.awt.Shape,
      role: Role,
      fill: Ink,
      d: Dash,
      outline: Boolean,
      pen: Pen = Pen.Normal,
    ): Unit                 =
      val (look, inked) = c.look(role, fill)
      val edge          = look.map(e => pen.ink.fold(e)(c.of))
      role match
        case Role.Element(layer) if fill != Ink.Empty =>
          val b = shape.getBounds2D
          g.setPaint(java.awt.GradientPaint(
            0f,
            b.getMinY.toFloat,
            colour(c.top(layer)),
            0f,
            b.getMaxY.toFloat,
            colour(c.of(layer)),
          ))
          g.fill(shape)
        case _                                        => inked.foreach: hex =>
            g.setColor(colour(hex))
            g.fill(shape)
      end match
      if outline then
        edge.foreach: hex =>
          g.setColor(colour(hex))
          g.setStroke(stroke(c.weight(role) * pen.weight, d, pen.weight))
          g.draw(shape)
    end paint
    def outline(segments: List[Seg], evenOdd: Boolean) =
      val shape = Path2D.Double(if evenOdd then Path2D.WIND_EVEN_ODD else Path2D.WIND_NON_ZERO)
      segments.foreach:
        case Seg.Move(p) => shape.moveTo(x(p._1), y(p._2))
        case Seg.Line(p) => shape.lineTo(x(p._1), y(p._2))
        case Seg.Cubic(a, b, p) => shape.curveTo(x(a._1), y(a._2), x(b._1), y(b._2), x(p._1), y(p._2))
        case Seg.Close => shape.closePath()
      shape
    def draw(p: Prim): Unit = p match
      case Prim.Poly(ps, closed, fill, d, role, pen)                  =>
        paint(path(ps, closed), role, if closed then fill else Ink.Empty, d, outline = true, pen)
      case Prim.Circle(_, _, fill, role)                              =>
        shapeOf(p).foreach(paint(_, role, fill, Dash.Solid, outline = true))
      case Prim.Ellipse(_, _, _, fill, role)                          =>
        shapeOf(p).foreach(paint(_, role, fill, Dash.Solid, outline = true))
      case Prim.Box(_, _, _, _, _, fill, d, edge, role, pen)          =>
        shapeOf(p).foreach(paint(_, role, fill, d, edge, pen))
      case Prim.Text(at, text, size, weight, tone, centred, backdrop) =>
        val font  = faces.font(weight).deriveFont(pt(size))
        g.setFont(font)
        val width = font.getStringBounds(text, g.getFontRenderContext).getWidth
        val left  = x(at._1) - (if centred then width / 2 else 0)
        backdrop.foreach: on =>
          val m = font.getLineMetrics(text, g.getFontRenderContext)
          g.setColor(colour(c.ground(on)))
          g.fill(java.awt.geom.Rectangle2D.Double(
            left - pt(1.2),
            y(at._2) - m.getAscent,
            width + 2 * pt(1.2),
            m.getAscent + m.getDescent,
          ))
        g.setColor(colour(if tone == Emphasis.Muted then c.muted else c.text))
        g.drawString(text, left.toFloat, y(at._2).toFloat)
      case con @ Prim.Connector(ps, d, _, _, ink)                     =>
        g.setColor(colour(ink.fold(c.line)(c.of)))
        g.setStroke(stroke(Look.LINK_LINE, d))
        g.draw(path(ps, closed = false, Look.BEND * 2.54 / 72))
        Heads.of(con).foreach(draw)
      case Prim.Curve(segments, fill, evenOdd)                        =>
        g.setColor(colour(Logos.colour(fill)))
        g.fill(outline(segments, evenOdd))
      case Prim.Path(segments, fill, w, role, evenOdd)                =>
        val (look, inked) = c.look(role, fill)
        val shape         = outline(segments, evenOdd)
        inked.foreach: hex =>
          g.setColor(colour(hex))
          g.fill(shape)
        look.filter(_ => w > 0).foreach: hex =>
          g.setColor(colour(hex))
          g.setStroke(BasicStroke((w * scale).toFloat, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND))
          g.draw(shape)
      case Prim.Image(ix, iy, w, h, data, _)                          =>
        scala.util.Try(
          ImageIO.read(java.io.ByteArrayInputStream(java.util.Base64.getDecoder.decode(data)))
        )
          .toOption
          .flatMap(Option(_))
          .foreach: picture =>
            g.setRenderingHint(
              RenderingHints.KEY_INTERPOLATION,
              RenderingHints.VALUE_INTERPOLATION_BICUBIC,
            )
            g.drawImage(
              picture,
              x(ix).round.toInt,
              y(iy + h).round.toInt,
              (w * scale).round.toInt,
              (h * scale).round.toInt,
              null,
            )
    drawing.prims.foreach(draw)
    g.dispose()
    val out                 = ByteArrayOutputStream()
    ImageIO.write(canvas, "png", out)
    out.toByteArray

  end image

end Png
