package fish.genius.uml.diagram

import java.awt.{BasicStroke, RenderingHints}
import java.awt.geom.{Ellipse2D, Path2D, RoundRectangle2D}
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

// A Drawing as a PNG, drawn with Java2D: the same geometry and the same font as the
// TikZ picture, for an export that has no LaTeX to compile it with.
object Png:

  private val MARGIN = 0.15

  def image(
    drawing: Diagram.Drawing,
    dpi: Int = 150,
    palette: Map[String, String] = Map.empty,
  ): Array[Byte] =
    val colours                  = Notation.palette.toMap ++ palette
    val scale                    = dpi / 2.54
    val (minX, minY, maxX, maxY) = Extent.of(drawing, MARGIN)
    val (w, h) = (((maxX - minX) * scale).ceil.toInt, ((maxY - minY) * scale).ceil.toInt)
    val canvas = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
    val g      = canvas.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(
      RenderingHints.KEY_FRACTIONALMETRICS,
      RenderingHints.VALUE_FRACTIONALMETRICS_ON,
    )
    g.setColor(java.awt.Color.WHITE)
    g.fillRect(0, 0, w, h)
    def x(v: Double): Double                   = (v - minX) * scale
    def y(v: Double): Double                   = (maxY - v) * scale
    def pt(points: Double)                     = (points * dpi / 72).toFloat
    def colour(hex: String)                    = java.awt.Color(Integer.parseInt(hex, 16))
    val line                                   = colour(colours("am-line"))
    def ink(fill: Ink): Option[java.awt.Color] = fill match
      case Ink.Empty       => None
      case Ink.Paper       => Some(java.awt.Color.WHITE)
      case Ink.Line        => Some(line)
      case Ink.Layer(name) => Some(colour(colours.getOrElse(name, "FFFFFF")))
    def stroke(width: Double, d: Dash)         =
      val dashes = d match
        case Dash.Solid  => null
        case Dash.Dashed => Array(pt(3), pt(2))
        case Dash.Dotted => Array(pt(0.8), pt(1.6))
      BasicStroke(pt(width), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, dashes, 0f)
    def path(ps: List[Pt], closed: Boolean)    =
      val shape = Path2D.Double()
      ps.headOption.foreach(p => shape.moveTo(x(p._1), y(p._2)))
      ps.drop(1).foreach(p => shape.lineTo(x(p._1), y(p._2)))
      if closed then shape.closePath()
      shape
    def paint(
      shape: java.awt.Shape,
      fill: Ink,
      width: Double,
      d: Dash,
      outline: Boolean = true,
    ): Unit                                    =
      ink(fill).foreach: c =>
        g.setColor(c)
        g.fill(shape)
      if outline then
        g.setColor(line)
        g.setStroke(stroke(width, d))
        g.draw(shape)
    def draw(p: Prim): Unit                    = p match
      case Prim.Poly(ps, closed, fill, d)             => paint(path(ps, closed), fill, 0.4, d)
      case Prim.Circle(c, r, fill)                    =>
        paint(
          Ellipse2D.Double(x(c._1 - r), y(c._2 + r), 2 * r * scale, 2 * r * scale),
          fill,
          0.4,
          Dash.Solid,
        )
      case Prim.Ellipse(c, rx, ry, fill)              =>
        paint(
          Ellipse2D.Double(x(c._1 - rx), y(c._2 + ry), 2 * rx * scale, 2 * ry * scale),
          fill,
          0.4,
          Dash.Solid,
        )
      case Prim.Box(bx, by, bw, bh, r, fill, d, edge) =>
        val shape = RoundRectangle2D.Double(
          x(bx),
          y(by + bh),
          bw * scale,
          bh * scale,
          2 * r * scale,
          2 * r * scale,
        )
        paint(shape, fill, 0.5, d, edge)
      case Prim.Text(at, text, size, bold, centred)   =>
        val font  =
          java.awt.Font(
            drawing.family,
            if bold then java.awt.Font.BOLD else java.awt.Font.PLAIN,
            100,
          ).deriveFont(pt(size))
        g.setFont(font)
        g.setColor(line)
        val width = font.getStringBounds(text, g.getFontRenderContext).getWidth
        g.drawString(text, (x(at._1) - (if centred then width / 2 else 0)).toFloat, y(at._2).toFloat)
      case c @ Prim.Connector(ps, d, _, _)            =>
        paint(path(ps, closed = false), Ink.Empty, 0.5, d)
        Heads.of(c).foreach(draw)
    drawing.prims.foreach(draw)
    g.dispose()
    val out                                    = ByteArrayOutputStream()
    ImageIO.write(canvas, "png", out)
    out.toByteArray

  end image

end Png
