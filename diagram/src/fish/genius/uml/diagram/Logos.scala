package fish.genius.uml.diagram

import scala.util.Try

import fish.genius.uml.scene.{Logo, LogoShape}

// A logo drawn in the box it is given: scaled to fit, centred, its own colours kept.
// A vector logo becomes Curves (SVG path syntax read by SvgPath, y turned to run up), a
// picture an Image.
object Logos:

  // the width a logo takes at height h, at most `widest` times as wide as high
  def width(
    logo: Logo,
    h: Double,
    widest: Double = 3.0,
  ): Double =
    h * math.min(logo.aspect, widest)

  // the logo fitted in the box from (x, y), w wide and h high
  def place(
    logo: Logo,
    x: Double,
    y: Double,
    w: Double,
    h: Double,
  ): List[Prim] =
    val s        = math.min(w / logo.aspect, h)
    val (fw, fh) = (s * logo.aspect, s)
    val (x0, y0) = (x + (w - fw) / 2, y + (h - fh) / 2)
    logo match
      case Logo.Vector(minX, minY, vw, _, shapes) =>
        val k             = fw / vw
        def at(p: Pt): Pt = (x0 + (p._1 - minX) * k, y0 + fh - (p._2 - minY) * k)
        shapes.flatMap: shape =>
          SvgPath.parse(shape.path).toOption.filter(_.nonEmpty).map: segments =>
            Prim.Curve(
              segments.map:
                case Seg.Move(p)        => Seg.Move(at(p))
                case Seg.Line(p)        => Seg.Line(at(p))
                case Seg.Cubic(a, b, p) => Seg.Cubic(at(a), at(b), at(p))
                case Seg.Close          =>
                  Seg.Close
              ,
              colour(shape.fill),
              shape.evenOdd,
            )
      // only base64 data and a picture's own kinds: a Picture can be built directly
      case Logo.Picture(data, mime, _, _)         =>
        Option.when(Base64Data.matches(data) && (mime == "image/png" || mime == "image/jpeg"))(
          Prim.Image(x0, y0, fw, fh, data, mime)
        ).toList
    end match

  end place

  // A logo read from an SVG document: its paths, rectangles, circles, ellipses, polygons
  // and polylines, filled as the document (or the group round them) says, through the
  // translate, scale, rotate and matrix transforms of the groups and the shapes. A
  // document whose DTD or entities would reach outside it is refused, as is one with
  // nothing to draw.
  def fromSvg(svg: String): Either[String, Logo] = SvgLogo.read(svg)

  // A logo from a PNG or JPEG picture, measured in pixels.
  // A PNG or a JPEG, by what it is rather than what it is called: its size is read
  // from its header, and a picture of more than MAX_PIXELS is refused before it is
  // decoded.
  def fromPicture(bytes: Array[Byte], mime: String): Either[String, Logo] =
    val _ = mime
    Try {
      val in = javax.imageio.ImageIO.createImageInputStream(java.io.ByteArrayInputStream(bytes))
      val readers = javax.imageio.ImageIO.getImageReaders(in)
      try
        if !readers.hasNext then None
        else
          val reader = readers.next()
          try
            reader.setInput(in)
            val format = reader.getFormatName.toLowerCase
            Some((format, reader.getWidth(0), reader.getHeight(0)))
          finally reader.dispose()
      finally in.close()
    }.toOption.flatten.toRight("not a picture this can read (PNG or JPEG)").flatMap:
      case (format, w, h) =>
        val kind = format match
          case "png"          => Some("image/png")
          case "jpeg" | "jpg" => Some("image/jpeg")
          case _              => None
        for
          mime <- kind.toRight(s"a $format picture is not a logo this can draw (PNG or JPEG)")
          _    <- Either.cond(
            w > 0 && h > 0 && w.toLong * h <= MAX_PIXELS,
            (),
            "the picture is larger than a logo should be",
          )
        yield Logo.Picture(java.util.Base64.getEncoder.encodeToString(bytes), mime, w, h)

  end fromPicture

  private val MAX_PIXELS = 16_000_000L

  private val Base64Data = "^[A-Za-z0-9+/]*={0,2}$".r
  private val Hex        = "^[0-9A-Fa-f]{6}$".r

  // a colour as six hex digits, black for anything else (a Logo can be built directly)
  private[diagram] def colour(fill: String): String =
    val h = fill.stripPrefix("#")
    if Hex.matches(h) then h.toUpperCase else "000000"

end Logos

// SVG path data (M L H V C S Q T A Z, absolute and relative) as moves, lines and cubic
// curves in absolute coordinates; a quadratic curve and an arc become cubics. Left,
// with the command it stopped at, when the data cannot be read.
object SvgPath:

  def parse(d: String): Either[String, List[Seg]] =
    Try(read(d)).toEither.left.map(e => Option(e.getMessage).getOrElse(e.toString))

  private val Token = """[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""".r

  private val Foreign = """[^MmLlHhVvCcSsQqTtAaZz0-9eE.+\-,\s]""".r

  private def read(d: String): List[Seg] =
    Foreign.findFirstIn(d).foreach(c => throw IllegalArgumentException(s"'$c' is not path data"))
    var tokens                    = Token.findAllIn(d).toVector
    var i                         = 0
    val out                       = scala.collection.mutable.ListBuffer.empty[Seg]
    var cur                       = (0.0, 0.0)
    var start                     = (0.0, 0.0)
    var lastC2: Option[Pt]        = None // the second control of the last cubic, for S
    var lastQ: Option[Pt]         = None // the control of the last quadratic, for T
    var cmd                       = ' '
    def isCmd(t: String)          = t.length == 1 && t.head.isLetter
    def num(): Double             =
      if i >= tokens.size || isCmd(tokens(i)) then
        throw IllegalArgumentException(s"a number is missing after '$cmd'")
      val v = tokens(i).toDouble
      i += 1
      v
    // the arc flags may run together with what follows ("a1 1 0 011 1")
    def flag(): Boolean           =
      if i >= tokens.size then throw IllegalArgumentException("an arc flag is missing")
      val t = tokens(i)
      if t == "0" || t == "1" then
        i += 1
        t == "1"
      else if t.startsWith("0") || t.startsWith("1") then
        tokens = tokens.updated(i, t.drop(1))
        t.head == '1'
      else throw IllegalArgumentException(s"'$t' is not an arc flag")
    def quad(q: Pt, to: Pt): Unit =
      val c1 = (cur._1 + 2.0 / 3 * (q._1 - cur._1), cur._2 + 2.0 / 3 * (q._2 - cur._2))
      val c2 = (to._1 + 2.0 / 3 * (q._1 - to._1), to._2 + 2.0 / 3 * (q._2 - to._2))
      out += Seg.Cubic(c1, c2, to)
    while i < tokens.size do
      if isCmd(tokens(i)) then
        cmd = tokens(i).head
        i += 1
      else if cmd == ' ' then throw IllegalArgumentException("path data starts without a command")
      val rel      = cmd.isLower
      def pt(): Pt =
        val (x, y) = (num(), num())
        if rel then (cur._1 + x, cur._2 + y) else (x, y)
      cmd.toUpper match
        case 'M'   =>
          cur = pt(); start = cur
          out += Seg.Move(cur)
          // further pairs after a move are lines
          cmd = if rel then 'l' else 'L'
          lastC2 = None; lastQ = None
        case 'L'   =>
          cur = pt(); out += Seg.Line(cur); lastC2 = None; lastQ = None
        case 'H'   =>
          val x = num()
          cur = (if rel then cur._1 + x else x, cur._2); out += Seg.Line(cur)
          lastC2 = None; lastQ = None
        case 'V'   =>
          val y = num()
          cur = (cur._1, if rel then cur._2 + y else y); out += Seg.Line(cur)
          lastC2 = None; lastQ = None
        case 'C'   =>
          val (c1, c2, to) = (pt(), pt(), pt())
          out += Seg.Cubic(c1, c2, to); cur = to; lastC2 = Some(c2); lastQ = None
        case 'S'   =>
          val c1       = lastC2.fold(cur)(c => (2 * cur._1 - c._1, 2 * cur._2 - c._2))
          val (c2, to) = (pt(), pt())
          out += Seg.Cubic(c1, c2, to); cur = to; lastC2 = Some(c2); lastQ = None
        case 'Q'   =>
          val (q, to) = (pt(), pt())
          quad(q, to); cur = to; lastQ = Some(q); lastC2 = None
        case 'T'   =>
          val q  = lastQ.fold(cur)(c => (2 * cur._1 - c._1, 2 * cur._2 - c._2))
          val to = pt()
          quad(q, to); cur = to; lastQ = Some(q); lastC2 = None
        case 'A'   =>
          val (rx, ry, rot)  = (num(), num(), num())
          val (large, sweep) = (flag(), flag())
          val to             = pt()
          out ++= arc(cur, rx, ry, rot, large, sweep, to)
          cur = to; lastC2 = None; lastQ = None
        case 'Z'   =>
          out += Seg.Close; cur = start; lastC2 = None; lastQ = None
          // a Z takes no numbers: the next token must be a command
          if i < tokens.size && !isCmd(tokens(i)) then
            throw IllegalArgumentException("a number follows Z")
        case other => throw IllegalArgumentException(s"unknown path command '$other'")
      end match
    end while
    out.toList
  end read

  // an elliptical arc (SVG's endpoint form) as cubics, a quarter turn at most each
  private def arc(
    from: Pt,
    rx0: Double,
    ry0: Double,
    rotation: Double,
    large: Boolean,
    sweep: Boolean,
    to: Pt,
  ): List[Seg] =
    if from == to then Nil
    else if rx0 == 0 || ry0 == 0 then List(Seg.Line(to))
    else
      val phi        = math.toRadians(rotation)
      val (cos, sin) = (math.cos(phi), math.sin(phi))
      val dx         = (from._1 - to._1) / 2
      val dy         = (from._2 - to._2) / 2
      val x1         = cos * dx + sin * dy
      val y1         = -sin * dx + cos * dy
      var (rx, ry)   = (math.abs(rx0), math.abs(ry0))
      val lambda     = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry)
      if lambda > 1 then
        rx *= math.sqrt(lambda); ry *= math.sqrt(lambda)
      val num        = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
      val den        = rx * rx * y1 * y1 + ry * ry * x1 * x1
      val coef       = (if large == sweep then -1 else 1) * math.sqrt(math.max(0.0, num / den))
      val cx1        = coef * rx * y1 / ry
      val cy1        = -coef * ry * x1 / rx
      val cx         = cos * cx1 - sin * cy1 + (from._1 + to._1) / 2
      val cy         = sin * cx1 + cos * cy1 + (from._2 + to._2) / 2
      def angle(
        ux: Double,
        uy: Double,
        vx: Double,
        vy: Double,
      )              =
        math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
      val t1         = angle(1, 0, (x1 - cx1) / rx, (y1 - cy1) / ry)
      var dt         = angle((x1 - cx1) / rx, (y1 - cy1) / ry, (-x1 - cx1) / rx, (-y1 - cy1) / ry)
      if !sweep && dt > 0 then dt -= 2 * math.Pi
      else if sweep && dt < 0 then dt += 2 * math.Pi
      val n          = math.max(1, math.ceil(math.abs(dt) / (math.Pi / 2)).toInt)
      val step       = dt / n
      val k          = 4.0 / 3 * math.tan(step / 4)
      def point(t: Double): Pt =
        val (x, y) = (rx * math.cos(t), ry * math.sin(t))
        (cos * x - sin * y + cx, sin * x + cos * y + cy)
      def deriv(t: Double): Pt =
        val (x, y) = (-rx * math.sin(t), ry * math.cos(t))
        (cos * x - sin * y, sin * x + cos * y)
      (0 until n).toList.map: j =>
        val (a, b)   = (t1 + j * step, t1 + (j + 1) * step)
        val (pa, pb) = (point(a), point(b))
        val (da, db) = (deriv(a), deriv(b))
        val end      = if j == n - 1 then to else pb
        Seg.Cubic(
          (pa._1 + k * da._1, pa._2 + k * da._2),
          (pb._1 - k * db._1, pb._2 - k * db._2),
          end,
        )

end SvgPath

// The subset of SVG a logo is read from, into absolute path data.
private object SvgLogo:

  import org.w3c.dom.{Element, Node}

  // a 2D affine transform: x' = a x + c y + e, y' = b x + d y + f
  final private case class Affine(
    a: Double,
    b: Double,
    c: Double,
    d: Double,
    e: Double,
    f: Double):
    def apply(p: Pt): Pt = (a * p._1 + c * p._2 + e, b * p._1 + d * p._2 + f)

    def andThen(inner: Affine): Affine = // this ∘ inner
      Affine(
        a * inner.a + c * inner.b,
        b * inner.a + d * inner.b,
        a * inner.c + c * inner.d,
        b * inner.c + d * inner.d,
        a * inner.e + c * inner.f + e,
        b * inner.e + d * inner.f + f,
      )

  end Affine

  private val Identity = Affine(1, 0, 0, 1, 0, 0)

  private val Named = Map(
    "black" -> "000000",
    "white" -> "FFFFFF",
    "red" -> "FF0000",
    "green" -> "008000",
    "blue" -> "0000FF",
    "yellow" -> "FFFF00",
    "orange" -> "FFA500",
    "gray" -> "808080",
    "grey" -> "808080",
  )

  // what a logo may be: so many characters, groups so deep
  private val MAX_CHARS = 4 * 1024 * 1024
  private val MAX_DEPTH = 64

  def read(svg: String): Either[String, Logo] =
    for
      _    <- Either.cond(svg.length <= MAX_CHARS, (), "the SVG is larger than a logo should be")
      root <- document(svg)
      box  <- viewBox(root)
      shapes = walk(root, Identity, Some("000000"), evenOdd = false, depth = 0)
      logo <- Either.cond(shapes.nonEmpty, box(shapes), "the SVG has nothing to draw")
    yield logo

  private def document(svg: String): Either[String, Element] =
    Try {
      val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      factory.setXIncludeAware(false)
      factory.setExpandEntityReferences(false)
      factory.setNamespaceAware(false)
      val builder = factory.newDocumentBuilder()
      builder.setErrorHandler(null)
      builder.parse(org.xml.sax.InputSource(java.io.StringReader(svg))).getDocumentElement
    }.toEither.left.map(e => s"not an SVG document: ${e.getMessage}")
      .filterOrElse(_.getTagName.endsWith("svg"), "not an SVG document: its root is not <svg>")

  // the view box the shapes are drawn in: the viewBox, else the width and height
  private def viewBox(root: Element): Either[String, List[LogoShape] => Logo] =
    val numbers = attr(root, "viewBox").map(_.trim.split("[\\s,]+").toList.flatMap(_.toDoubleOption))
    def size(name: String) =
      attr(root, name).flatMap(v => "^[0-9.]+".r.findFirstIn(v)).flatMap(_.toDoubleOption)
    (numbers, size("width"), size("height")) match
      case (Some(List(x, y, w, h)), _, _) if w > 0 && h > 0 => Right(Logo.Vector(x, y, w, h, _))
      case (_, Some(w), Some(h)) if w > 0 && h > 0          => Right(Logo.Vector(0, 0, w, h, _))
      case _ => Left("the SVG says neither its viewBox nor its size")

  private def attr(e: Element, name: String): Option[String] =
    Option(e.getAttribute(name)).filter(_.nonEmpty).orElse(style(e).get(name))

  private def style(e: Element): Map[String, String] =
    Option(e.getAttribute("style")).toList.flatMap(_.split(";").toList).flatMap: decl =>
      decl.split(":", 2).toList match
        case List(k, v) => List(k.trim -> v.trim)
        case _          => Nil
    .toMap

  // a fill: Some(hex) to paint, None for none; an inherited one when the shape says nothing
  private def fillOf(e: Element, inherited: Option[String]): Option[String] =
    attr(e, "fill").map(_.trim) match
      case None                                    => inherited
      case Some("none")                            => None
      case Some(v) if v.matches("#[0-9A-Fa-f]{6}") => Some(v.drop(1).toUpperCase)
      case Some(v) if v.matches("#[0-9A-Fa-f]{3}") =>
        Some(v.drop(1).flatMap(c => s"$c$c").toUpperCase)
      case Some(v) if v.startsWith("rgb(")         =>
        // three components, each a number 0-255 or a percentage; what is missing is 0
        val parts = v.drop(4).takeWhile(_ != ')').split(",").toList.map(_.trim).map: c =>
          if c.endsWith("%") then c.stripSuffix("%").toDoubleOption.map(_ * 255 / 100)
          else c.toDoubleOption
        val rgb   = (parts.map(_.getOrElse(0.0)) ++ List(0.0, 0.0, 0.0)).take(3)
        Some(rgb.map(x => f"${math.max(0, math.min(255, x.round.toInt))}%02X").mkString)
      // a named colour this knows, else black (currentColor, a gradient, an unknown name)
      case Some(v)                                 => Some(Named.getOrElse(v.toLowerCase, "000000"))

  private def transformOf(e: Element): Affine =
    val Call = """(matrix|translate|scale|rotate)\s*\(([^)]*)\)""".r
    attr(e, "transform").fold(Identity): t =>
      Call.findAllMatchIn(t).foldLeft(Identity): (acc, m) =>
        val n    = m.group(2).trim.split("[\\s,]+").toList.flatMap(_.toDoubleOption)
        val step = (m.group(1), n) match
          case ("matrix", List(a, b, c, d, e, f)) => Affine(a, b, c, d, e, f)
          case ("translate", List(x))             => Affine(1, 0, 0, 1, x, 0)
          case ("translate", List(x, y))          => Affine(1, 0, 0, 1, x, y)
          case ("scale", List(k))                 => Affine(k, 0, 0, k, 0, 0)
          case ("scale", List(x, y))              => Affine(x, 0, 0, y, 0, 0)
          case ("rotate", a :: rest)              =>
            val (cs, sn) = (math.cos(math.toRadians(a)), math.sin(math.toRadians(a)))
            val turn     = Affine(cs, sn, -sn, cs, 0, 0)
            rest match
              case List(cx, cy) =>
                Affine(1, 0, 0, 1, cx, cy).andThen(turn).andThen(Affine(1, 0, 0, 1, -cx, -cy))
              case _            => turn
          case _                                  => Identity
        acc.andThen(step)

  end transformOf

  private def walk(
    node: Element,
    outer: Affine,
    inherited: Option[String],
    evenOdd: Boolean,
    depth: Int,
  ): List[LogoShape] =
    if depth > MAX_DEPTH then return Nil
    val here              = outer.andThen(transformOf(node))
    val fill              = fillOf(node, inherited)
    val rule              = attr(node, "fill-rule").map(_ == "evenodd").getOrElse(evenOdd)
    def num(name: String) = attr(node, name).flatMap(_.toDoubleOption).getOrElse(0.0)
    def shape(d: String): List[LogoShape] =
      fill.toList.flatMap: hex =>
        SvgPath.parse(d).toOption.filter(_.nonEmpty).toList.map: segs =>
          LogoShape(write(segs.map(transformed(_, here))), hex, rule)
    val name                              = node.getTagName.toLowerCase.split(":").last
    name match
      case "path"                 => attr(node, "d").toList.flatMap(shape)
      case "rect"                 =>
        val (x, y, w, h) = (num("x"), num("y"), num("width"), num("height"))
        if w > 0 && h > 0 then shape(s"M$x $y H${x + w} V${y + h} H$x Z") else Nil
      case "circle"               =>
        val (cx, cy, r) = (num("cx"), num("cy"), num("r"))
        if r > 0 then
          shape(s"M${cx - r} $cy A$r $r 0 1 0 ${cx + r} $cy A$r $r 0 1 0 ${cx - r} $cy Z")
        else Nil
      case "ellipse"              =>
        val (cx, cy, rx, ry) = (num("cx"), num("cy"), num("rx"), num("ry"))
        if rx > 0 && ry > 0 then
          shape(s"M${cx - rx} $cy A$rx $ry 0 1 0 ${cx + rx} $cy A$rx $ry 0 1 0 ${cx - rx} $cy Z")
        else Nil
      case "polygon" | "polyline" =>
        attr(node, "points").toList.flatMap: pts =>
          val n = pts.trim.split("[\\s,]+").toList.flatMap(_.toDoubleOption)
          if n.size >= 4 then shape("M" + n.grouped(2).map(_.mkString(" ")).mkString(" L") + " Z")
          else Nil
      // what is not drawn, or not a container of what is
      case "defs" | "clippath" | "mask" | "symbol" | "title" | "desc" | "metadata" | "style" => Nil
      case _                      =>
        val kids = node.getChildNodes
        (0 until kids.getLength).toList.map(kids.item).collect { case e: Element => e }
          .flatMap(walk(_, here, fill, rule, depth + 1))
    end match

  end walk

  private def transformed(seg: Seg, t: Affine): Seg = seg match
    case Seg.Move(p)        => Seg.Move(t(p))
    case Seg.Line(p)        => Seg.Line(t(p))
    case Seg.Cubic(a, b, p) => Seg.Cubic(t(a), t(b), t(p))
    case Seg.Close          => Seg.Close

  private def write(segs: List[Seg]): String =
    def n(v: Double) =
      java.lang.String.format(java.util.Locale.ROOT, "%.4f", Double.box(v)).replaceAll("\\.?0+$", "")
    def p(pt: Pt)    = s"${n(pt._1)} ${n(pt._2)}"
    segs.map:
      case Seg.Move(to)        => s"M${p(to)}"
      case Seg.Line(to)        => s"L${p(to)}"
      case Seg.Cubic(a, b, to) => s"C${p(a)} ${p(b)} ${p(to)}"
      case Seg.Close           => "Z"
    .mkString(" ")

end SvgLogo
