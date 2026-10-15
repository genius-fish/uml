package fish.genius.uml.diagram

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

import zio.test.*

import zio.Scope

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.scene.*

object LogoSpec extends ZIOSpecDefault:

  private def close(a: Pt, b: Pt) = math.abs(a._1 - b._1) < 1e-6 && math.abs(a._2 - b._2) < 1e-6

  // a square with a square hole, cut by the even-odd rule, in a 24 × 24 box
  val framed: Logo = Logo.Vector(
    0,
    0,
    24,
    24,
    List(LogoShape("M2 2h20v20H2zM8 8v8h8V8z", "#1F6FEB", evenOdd = true)),
  )

  // a wordmark three times as wide as high
  val wide: Logo = Logo.Vector(0, 0, 60, 20, List(LogoShape("M0 0H60V20H0Z", "C0392B")))

  // a 4 × 2 pixel picture
  val picture: Logo =
    val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB)
    image.setRGB(0, 0, 0xff0000)
    val out   = ByteArrayOutputStream()
    ImageIO.write(image, "png", out)
    Logo.Picture(Base64.getEncoder.encodeToString(out.toByteArray), "image/png", 4, 2)

  private def leaf(id: String, label: String) =
    SceneNode(SceneId(id), NodeKind.Element(ShapeType.ApplicationComponent), label)

  val logos: SceneGraph = SceneGraph(
    "Logos",
    List(
      leaf("pay", "Payments").copy(logo = Some(framed)),
      leaf("crm", "Customer Relationship Management").copy(logo = Some(wide)),
      leaf("abb", "Identity").copy(badges =
        List(
          Badge(Glyph.Circle, "Keycloak", Some(framed)),
          Badge(Glyph.Square, "Legacy LDAP"),
          Badge(Glyph.Diamond, "Scanned", Some(picture)),
        )
      ),
    ),
    List(
      SceneEdge(SceneId("e1"), SceneId("crm"), SceneId("pay"), RelationshipType.Flow),
      SceneEdge(SceneId("e2"), SceneId("abb"), SceneId("crm"), RelationshipType.Serving),
    ),
    legend = Some(
      Legend(
        "Realized by",
        List(
          Badge(Glyph.Circle, "Keycloak", Some(framed)),
          Badge(Glyph.Square, "Legacy LDAP"),
          Badge(Glyph.Diamond, "Scanned", Some(picture)),
        ),
      )
    ),
  )

  private lazy val drawing = Diagram.draw(logos).toOption.get

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("diagram / logos")(
      test("path data reads absolute and relative moves, lines, H and V, and Z"):
        val segs = SvgPath.parse("M2 2h20v20H2zm6 6l8 0L16 16z").toOption.get
        assertTrue(
          segs.head == Seg.Move((2, 2)),
          segs(1) == Seg.Line((22, 2)),
          segs(2) == Seg.Line((22, 22)),
          segs(3) == Seg.Line((2, 22)),
          segs(4) == Seg.Close,
          // after Z the current point is the subpath's start, so m6 6 is (8, 8)
          segs(5) == Seg.Move((8, 8)),
          segs(6) == Seg.Line((16, 8)),
          segs(7) == Seg.Line((16, 16)),
        )
      ,
      test("a smooth cubic reflects the last control; a quadratic becomes a cubic"):
        val s = SvgPath.parse("M0 0C1 2 3 2 4 0S7 -2 8 0").toOption.get
        val q = SvgPath.parse("M0 0Q2 2 4 0T8 0").toOption.get
        assertTrue(
          s(2) == Seg.Cubic((5, -2), (7, -2), (8, 0)),
          q(1) match
            case Seg.Cubic(a, b, to) => close(a, (4.0 / 3, 4.0 / 3)) && close(b, (8.0 / 3, 4.0 / 3)) && to == (4.0, 0.0)
            case _                   =>
              false
          ,
          // T reflects (2, 2) through (4, 0): its control is (6, -2)
          q(2) match
            case Seg.Cubic(a, _, to) => close(a, (4 + 2.0 / 3 * 2, -4.0 / 3)) && to == (8.0, 0.0)
            case _                   => false,
        )
      ,
      test("an arc becomes cubics that end where it ends, its flags read even when packed"):
        val half    = SvgPath.parse("M0 0A5 5 0 0 1 10 0").toOption.get
        val packed  = SvgPath.parse("M0 0a5 5 0 0110 0").toOption.get
        val cubics  = half.collect { case c: Seg.Cubic => c }
        // the half circle from (0,0) to (10,0) swept clockwise in SVG passes through (5, -5)
        val through = cubics.map(_.to)
        assertTrue(
          cubics.size == 2,
          close(cubics.last.to, (10, 0)),
          through.exists(p => close(p, (5, -5))),
          packed == half,
        )
      ,
      test("path data that cannot be read says why"):
        assertTrue(
          SvgPath.parse("2 2 L 4 4").isLeft,
          SvgPath.parse("M 2").isLeft,
          SvgPath.parse("M0 0 X 1 1").isLeft,
          SvgPath.parse("M0 0 L 1 1 Z 3").isLeft,
        )
      ,
      test("a logo fits its box, centred, the right way up, in its own colour"):
        val prims = Logos.place(wide, 0, 0, 3, 3)
        val pts   = prims.collect { case Prim.Curve(segs, fill, _) => (segs.flatMap(Seg.points), fill) }
        val xs    = pts.flatMap(_._1.map(_._1))
        val ys    = pts.flatMap(_._1.map(_._2))
        val holed = Logos.place(framed, 0, 0, 1, 1).collect { case c: Prim.Curve => c }
        assertTrue(
          pts.map(_._2) == List("C0392B"),
          // three times as wide as high: 3 wide, 1 high, centred on the box's middle
          close((xs.min, xs.max), (0, 3)),
          close((ys.min, ys.max), (1, 2)),
          holed.forall(_.evenOdd),
          // SVG's y runs down: the frame's top edge (y = 2) lands near the box's top
          holed.flatMap(_.segments.flatMap(Seg.points)).map(_._2).max > 0.9,
          Logos.width(wide, 1) == 3.0,
          Logos.width(Logo.Vector(0, 0, 100, 10, Nil), 1) == 3.0,
        )
      ,
      test("a leaf's logo stands in its top left corner, and its name keeps clear of it"):
        val crm   = drawing.scene.elements.find(_.key == SceneId("crm")).get
        val plain = Diagram.draw(logos.copy(nodes = logos.nodes.map(_.copy(logo = None)))).toOption.get
          .scene.elements.find(_.key == SceneId("crm")).get
        val curve = drawing.prims.collect:
          case Prim.Curve(segs, "C0392B", _) => segs.flatMap(Seg.points)
        val xs    = curve.flatten.map(_._1)
        val ys    = curve.flatten.map(_._2)
        assertTrue(
          curve.size == 1,
          xs.min >= crm.x - crm.w / 2,
          xs.max < crm.x,
          ys.max <= crm.y + crm.d / 2,
          ys.min > crm.y,
          // a wide logo widens the room the name keeps clear: the box is not narrower
          crm.w >= plain.w,
          drawing.overflow == 0,
        )
      ,
      test("a badge with a logo is its logo; one without keeps its glyph; the legend shows the same"):
        val images = drawing.prims.collect { case i: Prim.Image => i }
        val blue   = drawing.prims.collect { case c @ Prim.Curve(_, "1F6FEB", _) => c }
        val glyphs = drawing.prims.collect { case p @ Prim.Poly(_, true, Ink.Line, _, Role.Mark(_)) => p }
        val words  = drawing.prims.collect { case Prim.Text(_, t, _, _, _, _, _) => t }
        assertTrue(
          // Payments' corner logo, Keycloak's badge and its legend entry
          blue.size == 3,
          // Scanned's picture on the badge and in the legend
          images.size == 2,
          images.forall(i => math.abs(i.w / i.h - 2.0) < 1e-6),
          // Legacy LDAP's square glyph on the badge and in the legend
          glyphs.size >= 2,
          List("Keycloak", "Legacy LDAP", "Scanned").forall(words.contains),
        )
      ,
      test("SVG, PNG and TikZ draw the curves and the pictures"):
        val svg   = Svg.document(drawing)
        val tikz  = Tikz.picture(drawing)
        val files = Tikz.files(drawing)
        val png   = ImageIO.read(java.io.ByteArrayInputStream(Png.image(drawing)))
        assertTrue(
          svg.contains("""fill="#1F6FEB" fill-rule="evenodd""""),
          svg.contains("data:image/png;base64,"),
          tikz.contains("fill={rgb,255:red,31;green,111;blue,235}"),
          tikz.contains("even odd rule"),
          tikz.contains(".. controls") || tikz.contains("-- cycle"),
          tikz.contains("\\includegraphics[width="),
          files.size == 1,
          files.keySet.forall(n => tikz.contains(n) && n.endsWith(".png")),
          files.values.forall(bytes => ImageIO.read(java.io.ByteArrayInputStream(bytes)).getWidth == 4),
          png.getWidth > 0,
        )
      ,
      test("an SVG logo is read with its view box, its shapes and their fills"):
        val icon                            =
          """<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>X</title><path d="M0 0h24v24H0z"/></svg>"""
        val group                           = """<svg xmlns="http://www.w3.org/2000/svg" width="40" height="20">
          <defs><linearGradient id="g"/></defs>
          <g fill="#c00" transform="translate(10 0)">
            <rect x="0" y="0" width="10" height="10"/>
            <circle cx="5" cy="15" r="5" style="fill:#00ff00"/>
            <rect x="20" y="0" width="5" height="5" fill="none"/>
          </g>
          <polygon points="0,0 4,0 4,4" fill="rgb(0, 0, 255)" fill-rule="evenodd"/>
        </svg>"""
        val one                             = Logos.fromSvg(icon)
        val many                            = Logos.fromSvg(group)
        def shapes(l: Either[String, Logo]) = l.toOption.toList.flatMap:
          case Logo.Vector(_, _, _, _, s) => s
          case _                          => Nil
        val rect = shapes(many).find(_.fill == "CC0000").flatMap(s => SvgPath.parse(s.path).toOption)
        assertTrue(
          one.exists { case Logo.Vector(0, 0, 24, 24, List(LogoShape(_, "000000", false))) => true; case _ => false },
          many.exists { case Logo.Vector(0, 0, 40, 20, _) => true; case _ => false },
          shapes(many).map(_.fill) == List("CC0000", "00FF00", "0000FF"),
          shapes(many).last.evenOdd,
          // the group's translate moved the rectangle 10 to the right
          rect.exists(_.head == Seg.Move((10, 0))),
        )
      ,
      test("an SVG that is not one, has no size, nothing to draw, or a DTD is refused"):
        val dtd =
          """<?xml version="1.0"?><!DOCTYPE svg [<!ENTITY x SYSTEM "file:///etc/passwd">]><svg viewBox="0 0 1 1"><text>&x;</text></svg>"""
        assertTrue(
          Logos.fromSvg("not xml").isLeft,
          Logos.fromSvg("""<html viewBox="0 0 1 1"><path d="M0 0h1v1z"/></html>""").isLeft,
          Logos.fromSvg("""<svg><path d="M0 0h1v1z"/></svg>""").isLeft,
          Logos.fromSvg("""<svg viewBox="0 0 1 1"><path d="M0 0h1v1z" fill="none"/></svg>""").isLeft,
          Logos.fromSvg(dtd).isLeft,
        )
      ,
      test("a picture logo knows its size; what is not a picture is refused"):
        val bytes = picture match
          case Logo.Picture(data, _, _, _) => Base64.getDecoder.decode(data)
          case _                           => Array.emptyByteArray
        assertTrue(
          Logos.fromPicture(bytes, "image/png").exists(_.aspect == 2.0),
          Logos.fromPicture("nope".getBytes, "image/png").isLeft,
        )
      ,
      test("the same graph with logos gives the same drawing"):
        assertTrue(Diagram.draw(logos).toOption.get.prims == drawing.prims),
    )

end LogoSpec
