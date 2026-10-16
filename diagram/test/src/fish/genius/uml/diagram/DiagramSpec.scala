package fish.genius.uml.diagram

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.sys.process.{Process, ProcessLogger}

import zio.test.*

import zio.*

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.scene.*

object DiagramSpec extends ZIOSpecDefault:

  private def leaf(
    id: String,
    label: String,
    shape: ShapeType,
    caption: Option[String] = None,
    note: Option[String] = None,
  ) =
    SceneNode(SceneId(id), NodeKind.Element(shape), label, caption = caption, note = note)

  private def edge(
    id: String,
    source: String,
    target: String,
    kind: RelationshipType,
    label: Option[String] = None,
  ) =
    SceneEdge(SceneId(id), SceneId(source), SceneId(target), kind, label)

  // an L of 4 cm by 3 cm in blue: filled, its path would cover 6 cm²
  private def bent(head: Option[Head]) =
    val l = Prim.Connector(List((0.0, 3.0), (0.0, 0.0), (4.0, 0.0)), Dash.Solid, None, head, Some("am-fill-2f6db5"))
    Diagram.Drawing(
      "Bent",
      List(l),
      Scene("Bent", 4, 3, Nil, Nil),
      Diagram.Style(),
      "Nimbus Sans",
      0,
      Map("am-fill-2f6db5" -> "2F6DB5"),
    )

  private lazy val latexTools = List("pdflatex", "pdftoppm").forall: tool =>
    scala.util.Try(Process(Seq(tool, "-v")).!(ProcessLogger(_ => ()))).isSuccess

  // a drawing compiled by pdflatex and rasterised at 300 dpi: its blue pixels, and all of them
  private def bluePixels(d: Diagram.Drawing): (Int, Int) =
    val dir = Files.createTempDirectory("bent")
    try
      Files.writeString(dir.resolve("b.tex"), Tikz.standalone(d))
      val log = ProcessLogger(_ => ())
      Process(
        Seq("pdflatex", "-interaction=nonstopmode", "-halt-on-error", "b.tex"),
        dir.toFile,
      ).!(log)
      Process(Seq("pdftoppm", "-png", "-r", "300", "-singlefile", "b.pdf", "b"), dir.toFile).!(log)
      val png = javax.imageio.ImageIO.read(dir.resolve("b.png").toFile)
      val all = for x <- 0 until png.getWidth; y <- 0 until png.getHeight yield png.getRGB(x, y)
      val ink = all.count: rgb =>
        math.abs(((rgb >> 16) & 0xff) - 0x2f) < 24 && math.abs(((rgb >> 8) & 0xff) - 0x6d) < 24 &&
        math.abs((rgb & 0xff) - 0xb5) < 24
      (ink, all.size)
    finally
      scala.util.Using(Files.list(dir))(_.forEach { p => Files.deleteIfExists(p); () })
      Files.deleteIfExists(dir)
    end try

  end bluePixels

  // an application landscape: channels in a grouping, a CRM with its parts, the back
  // office, the data they keep and the platform under them
  val landscape: SceneGraph = SceneGraph(
    "Application landscape",
    List(
      leaf("customer", "Customer", ShapeType.BusinessActor),
      leaf("order", "Place an order", ShapeType.BusinessProcess),
      SceneNode(
        SceneId("channels"),
        NodeKind.Grouping,
        "Customer channels",
        List(
          leaf("shop", "Web Shop", ShapeType.ApplicationComponent),
          leaf("app", "Mobile App", ShapeType.ApplicationComponent),
        ),
      ),
      leaf("ordering", "Order Intake Service", ShapeType.ApplicationService),
      SceneNode(
        SceneId("crm"),
        NodeKind.Element(ShapeType.ApplicationComponent),
        "Customer Relationship Management",
        List(
          leaf("accounts", "Account Management", ShapeType.ApplicationFunction),
          leaf("api", "CRM REST API", ShapeType.ApplicationInterface),
        ),
        caption = Some("[IT System: Salesforce]"),
      ),
      leaf(
        "erp",
        "SAP S/4HANA Finance & Logistics",
        ShapeType.ApplicationComponent,
        Some("[IT System: SAP]"),
        Some("Books every order and runs the invoice cycle; the ledger of record."),
      ),
      leaf("orderdata", "Order", ShapeType.ApplicationDataObject, Some("[Data Object]")),
      leaf("billing", "Invoice run", ShapeType.ApplicationProcess),
      leaf("k8s", "Kubernetes Platform", ShapeType.TechnologyNode),
      leaf("pg", "PostgreSQL", ShapeType.TechnologySystemSoftware),
    ),
    List(
      edge("e1", "customer", "order", RelationshipType.Assignment),
      edge("e2", "ordering", "order", RelationshipType.Serving),
      edge("e3", "shop", "ordering", RelationshipType.Realization),
      edge("e4", "app", "ordering", RelationshipType.Realization),
      edge("e5", "shop", "api", RelationshipType.Flow, Some("creates accounts [REST]")),
      edge("e6", "crm", "erp", RelationshipType.Flow, Some("customer master data")),
      edge("e7", "erp", "orderdata", RelationshipType.WriteAccess),
      edge("e8", "billing", "orderdata", RelationshipType.ReadAccess),
      edge("e9", "erp", "billing", RelationshipType.Assignment),
      edge("e10", "k8s", "crm", RelationshipType.Serving),
      edge("e11", "pg", "erp", RelationshipType.Serving),
      edge("e12", "k8s", "pg", RelationshipType.Aggregation),
    ),
  )

  // building blocks stamped with what realizes them, an API and a health on a system,
  // one box set apart in its own colour, and the legend for the stamps
  val badged: SceneGraph =
    val glyphs = Glyph.values.toList
    val sbbs   = glyphs.take(12).zipWithIndex.map((g, i) => Badge(g, s"Solution ${i + 1}"))
    SceneGraph(
      "Badged",
      List(
        leaf("abb1", "Identity", ShapeType.ApplicationService).copy(badges = sbbs.take(3)),
        leaf("abb2", "Payments", ShapeType.ApplicationService).copy(badges = sbbs.slice(3, 9)),
        leaf("abb3", "Notifications", ShapeType.ApplicationService).copy(badges = sbbs.drop(9)),
        leaf("api", "Order API", ShapeType.ApplicationComponent, Some("[Container: Java]"))
          .copy(badges =
            List(Badge(Glyph.Globe, "Public API"), Badge(Glyph.Padlock, "Internal API"))
          ),
        leaf("sick", "Legacy Billing", ShapeType.ApplicationComponent)
          .copy(badges = List(Badge(Glyph.Warning, "Degraded"), Badge(Glyph.Failure, "Failing"))),
        leaf("new", "Fare product", ShapeType.BusinessObject, Some("[Business Object, new]"))
          .copy(fill = Some("#F4C542")),
      ),
      List(
        edge("b1", "abb1", "abb2", RelationshipType.Serving),
        edge("b2", "abb2", "abb3", RelationshipType.Serving),
        edge("b3", "api", "abb2", RelationshipType.Realization),
        edge("b4", "sick", "new", RelationshipType.WriteAccess),
      ),
      legend = Some(Legend("Realized by (SBB)", sbbs)),
    )

  end badged

  private def overlaps(a: SceneElement, b: SceneElement): Boolean =
    math.abs(a.x - b.x) * 2 < a.w + b.w - 1e-6 && math.abs(a.y - b.y) * 2 < a.d + b.d - 1e-6

  private def onBox(label: SceneLabel, boxes: List[SceneElement]): Boolean =
    boxes.exists(e =>
      math.abs(e.x - label.x) * 2 < e.w + label.w - 1e-6 &&
      math.abs(e.y - label.y) * 2 < e.d + label.d - 1e-6
    )

  private lazy val drawing = Diagram.draw(landscape).toOption.get

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("diagram / an ArchiMate graph drawn flat")(
      test("every box fits what it says, no two leaves overlap, and every relationship is drawn"):
        val leaves = drawing.scene.elements.filterNot(_.container)
        // the ERP box has a caption and a note: it is wide, and taller than a bare box
        val erp    = drawing.scene.elements.find(_.key == SceneId("erp")).get
        assertTrue(
          drawing.overflow == 0,
          leaves.combinations(2).forall { case List(a, b) => !overlaps(a, b); case _ => true },
          drawing.scene.relationships.size == landscape.edges.size,
          // every relationship, and the short pieces that bridge where lines cross
          drawing.prims.count(_.isInstanceOf[Prim.Connector]) >= landscape.edges.size,
          erp.w >= Diagram.Style().wideWidth,
          erp.d > Diagram.Style().boxHeight,
        )
      ,
      test("a chain runs down the page by default, and left to right when asked"):
        val chain                    = SceneGraph(
          "chain",
          (1 to 5).toList.map(i => leaf(s"n$i", s"Step $i", ShapeType.ApplicationComponent)),
          (1 to 4).toList.map(i => edge(s"e$i", s"n$i", s"n${i + 1}", RelationshipType.Flow)),
        )
        def span(d: Diagram.Drawing) =
          val es = d.scene.elements
          (es.map(e => e.x + e.w).max - es.map(_.x).min, es.map(e => e.y + e.d).max - es.map(_.y).min)
        val (downW, downH)           = span(Diagram.draw(chain).toOption.get)
        val right                    = Diagram.Options(direction = SceneLayout.Direction.Right)
        val (rightW, rightH)         = span(Diagram.draw(chain, right).toOption.get)
        assertTrue(downH > downW, rightW > rightH)
      ,
      test("laid out to the right, a fork and a join leave east and enter west"):
        val forked = SceneGraph(
          "fork and join",
          List("a", "b", "c", "d", "e", "f").map(i => leaf(i, s"Box $i", ShapeType.ApplicationComponent)),
          List("b", "c", "d", "e").map(i => edge(s"a$i", "a", i, RelationshipType.Flow)) ++
          List("b", "c", "d", "e").map(i => edge(s"${i}f", i, "f", RelationshipType.Flow)),
        )
        val right  = Diagram.draw(forked, Diagram.Options(direction = SceneLayout.Direction.Right))
          .toOption.get
        val rels   = right.scene.relationships
        val xOf    = right.scene.elements.map(e => e.key -> e.x).toMap
        assertTrue(
          rels.size == 8,
          rels.forall(r => r.sourcePort == PortName.East && r.targetPort == PortName.West),
          rels.forall(r => xOf(r.source) < xOf(r.target)),
        )
      ,
      test("laid out to the right, a reply runs back west to east, routed round no box"):
        val reply = SceneGraph(
          "request and reply",
          List("a", "b", "c").map(i => leaf(i, s"Box $i", ShapeType.ApplicationComponent)),
          List(
            edge("ab", "a", "b", RelationshipType.Flow),
            edge("bc", "b", "c", RelationshipType.Flow),
            edge("ca", "c", "a", RelationshipType.Flow),
          ),
        )
        val right = Diagram.draw(reply, Diagram.Options(direction = SceneLayout.Direction.Right))
          .toOption.get
        val xOf   = right.scene.elements.map(e => e.key -> e.x).toMap
        val back  = right.scene.relationships.find(_.key == SceneId("ca")).get
        val ahead = right.scene.relationships.filterNot(_.key == SceneId("ca"))
        assertTrue(
          ahead.forall(r => r.sourcePort == PortName.East && r.targetPort == PortName.West),
          xOf(SceneId("a")) < xOf(SceneId("b")) && xOf(SceneId("b")) < xOf(SceneId("c")),
          back.sourcePort == PortName.West && back.targetPort == PortName.East,
          right.overflow == 0,
        )
      ,
      test("laid out to the right, a nested landscape keeps every box apart and every line"):
        val right  = Diagram.draw(landscape, Diagram.Options(direction = SceneLayout.Direction.Right))
          .toOption.get
        val leaves = right.scene.elements.filterNot(_.container)
        assertTrue(
          right.overflow == 0,
          leaves.combinations(2).forall { case List(a, b) => !overlaps(a, b); case _ => true },
          right.scene.relationships.size == landscape.edges.size,
        )
      ,
      test("each connector has its own end: no two meet a box at the same point"):
        val ends = drawing.scene.relationships.flatMap(r => r.start.toList ++ r.end.toList)
        assertTrue(
          ends.size == 2 * landscape.edges.size,
          ends.groupBy(p => (math.rint(p.x * 100), math.rint(p.y * 100))).forall(_._2.size == 1),
        )
      ,
      test("a connector's label sits in room the layout kept, on no box"):
        val labels = drawing.scene.relationships.flatMap(_.label)
        assertTrue(
          labels.map(_.text).toSet == Set("customer master data", "creates accounts [REST]"),
          labels.forall(l => !onBox(l, drawing.scene.elements.filterNot(_.container))),
        )
      ,
      test("a container's header is at the top: its children stand below its name and caption"):
        val crm    = drawing.scene.elements.find(_.key == SceneId("crm")).get
        val parts  = drawing.scene.elements.filter(_.parent.contains(SceneId("crm")))
        val top    = crm.y + crm.d / 2
        // the scene rounds to thousandths
        val header = Diagram.Style().header + Diagram.Style().lead(Diagram.Style().captionSize)
        assertTrue(parts.nonEmpty, parts.forall(p => p.y + p.d / 2 <= top - header + 2e-3))
      ,
      test("TikZ, SVG and PNG write the same drawing, with names escaped"):
        val tikz = Tikz.picture(drawing, Map("am-application" -> "C2EFFF"))
        val svg  = Svg.document(drawing)
        val png  = Png.image(drawing)
        sys.env.get("DIAGRAM_OUT").map(Path.of(_)).foreach: dir =>
          Files.createDirectories(dir)
          Files.writeString(dir.resolve("landscape.tex"), Tikz.standalone(drawing), StandardCharsets.UTF_8)
          Files.writeString(dir.resolve("landscape.svg"), svg, StandardCharsets.UTF_8)
          Files.write(dir.resolve("landscape.png"), png)
        assertTrue(
          tikz.contains("\\begin{tikzpicture}"),
          tikz.contains("\\definecolor{am-application}{HTML}{C2EFFF}"),
          tikz.contains("\\providecolor{am-business}"),
          tikz.contains("Finance \\&") || tikz.contains("\\& Logistics"),
          svg.contains("&amp;"),
          svg.startsWith("<svg") && svg.endsWith("</svg>"),
          "&(?!amp;|lt;|gt;|quot;)".r.findFirstIn(svg).isEmpty,
          png.take(4).toList == List(0x89, 'P', 'N', 'G').map(_.toByte),
          // the SVG stands on its paper, so patches cut in it never show as blocks
          Svg.document(drawing, Map("am-paper" -> "FBF6EC")).contains("""fill="#FBF6EC"/>"""),
        )
      ,
      test("drawings made at the same time are the same drawing"):
        import scala.concurrent.{Await, ExecutionContext, Future}
        import scala.concurrent.duration.*
        given ExecutionContext = ExecutionContext.global
        val one  = Diagram.draw(badged).toOption.get.prims
        val many = Await.result(
          Future.sequence((1 to 16).map(_ => Future(Diagram.draw(badged).toOption.get.prims))),
          2.minutes,
        )
        assertTrue(many.forall(_ == one))
      ,
      test("the same graph gives the same drawing"):
        assertTrue(Diagram.draw(landscape).toOption.map(_.prims) == Option(drawing.prims))
      ,
      test("an edge in a colour of its own draws its line and its head in it"):
        val g     = SceneGraph(
          "coloured",
          List(
            leaf("a", "A", ShapeType.ApplicationComponent),
            leaf("b", "B", ShapeType.ApplicationComponent),
            leaf("c", "C", ShapeType.ApplicationComponent),
          ),
          List(
            SceneEdge(SceneId("ab"), SceneId("a"), SceneId("b"), RelationshipType.Flow, colour = Some("#2F6DB5")),
            edge("bc", "b", "c", RelationshipType.Flow),
            SceneEdge(SceneId("ca"), SceneId("c"), SceneId("a"), RelationshipType.Realization, colour = Some("#2F6DB5")),
            SceneEdge(SceneId("ac"), SceneId("a"), SceneId("c"), RelationshipType.Serving, colour = Some("abc")),
          ),
        )
        val d     = Diagram.draw(g).toOption.get
        val cons  = d.prims.collect { case c: Prim.Connector => c }
        val heads = cons.flatMap(Heads.of)
        val svg   = Svg.document(d)
        val tikz  = Tikz.standalone(d)
        assertTrue(
          cons.count(_.ink.contains("am-fill-2f6db5")) >= 2,
          // a colour that is not six hex digits draws as any other line
          cons.count(_.ink.isEmpty) >= 2,
          // filled and open heads alike are outlined in the colour, a filled one filled in it
          heads.exists {
            case p: Prim.Poly => p.fill == Ink.Layer("am-fill-2f6db5"); case _ => false
          },
          heads.exists {
            case p: Prim.Poly => p.fill == Ink.Paper && p.role == Role.Inked("am-fill-2f6db5")
            case _            => false
          },
          // two lines and their two heads stroked in the colour
          svg.split("stroke=\"#2F6DB5\"").length - 1 >= 4,
          svg.contains("stroke=\"#2F6DB5\""),
          tikz.contains("draw=am-fill-2f6db5") && tikz.contains("{am-fill-2f6db5}{HTML}{2F6DB5}"),
          d.scene.relationships.find(_.key == SceneId("ab")).flatMap(_.colour).contains("#2F6DB5"),
        )
      ,
      test("a node with an outline of its own keeps its fill and draws its edge that way"):
        val proposed = Outline(Stroke.Dashed, 2.0, Some("#D9534F"))
        def outlined(
          id: String,
          shape: ShapeType,
          fill: Option[String] = None,
        )            =
          leaf(id, id.toUpperCase, shape).copy(outline = Some(proposed), fill = fill)
        val g        = SceneGraph(
          "proposed",
          List(
            outlined("square", ShapeType.ApplicationComponent),
            outlined("rounded", ShapeType.ApplicationService, fill = Some("#FFE699")),
            outlined("banded", ShapeType.ApplicationDataObject),
            outlined("cut", ShapeType.MotivationGoal),
            leaf("plain", "Plain", ShapeType.ApplicationComponent),
            SceneNode(
              SceneId("frame"),
              NodeKind.Element(ShapeType.ApplicationComponent),
              "Frame",
              List(leaf("inner", "Inner", ShapeType.ApplicationFunction)),
              outline = Some(Outline(Stroke.Dotted)),
            ),
          ),
          Nil,
        )
        val d        = Diagram.draw(g).toOption.get
        val pen      = Pen(2.0, Some("am-fill-d9534f"))
        val edges    = d.prims.collect:
          case b: Prim.Box if b.pen == pen  => (b.dash, b.fill, b.role)
          case p: Prim.Poly if p.pen == pen => (p.dash, p.fill, p.role)
        val frame    = d.prims.collectFirst { case b @ Prim.Box(_, _, _, _, _, _, _, _, Role.Container(_), _) => b }
        val plain    = d.prims.collect { case b @ Prim.Box(_, _, _, _, _, _, _, _, Role.Element(_), _) => b }
          .filter(_.pen == Pen.Normal)
        val svg      = Svg.document(d)
        val tikz     = Tikz.standalone(d)
        val png      = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(Png.image(d, 100)))
        val red      = (0 until png.getWidth by 2).exists: x =>
          (0 until png.getHeight).exists: y =>
            val rgb = png.getRGB(x, y)
            math.abs(((rgb >> 16) & 0xff) - 0xd9) < 10 && math.abs(((rgb >> 8) & 0xff) - 0x53) < 10 &&
            math.abs((rgb & 0xff) - 0x4f) < 10
        assertTrue(
          // the four outline forms, each dashed in the pen, in its layer's fill or its own
          edges.size == 4,
          edges.forall(_._1 == Dash.Dashed),
          edges.map(_._2).toSet ==
          Set(Ink.Layer("am-application"), Ink.Layer("am-fill-ffe699"), Ink.Layer("am-motivation")),
          edges.forall(e =>
            e._3 == Role.Element("am-application") || e._3 == Role.Element("am-fill-ffe699") ||
            e._3 == Role.Element("am-motivation")
          ),
          // a container's frame drawn the same way; a node without one as before
          frame.exists(f => f.dash == Dash.Dotted && f.pen == Pen.Normal),
          plain.nonEmpty && plain.forall(_.dash == Dash.Solid),
          d.colours.get("am-fill-d9534f").contains("D9534F"),
          // twice the box's 0.55pt, and the dash twice as long, in CSS pixels
          svg.contains("stroke=\"#D9534F\" stroke-width=\"1.47\""),
          svg.contains("stroke-dasharray=\"7.47 4.8\""),
          tikz.contains("draw=am-fill-d9534f,line width=1.1pt"),
          tikz.contains("dash pattern=on 5.6pt off 3.6pt"),
          tikz.contains("{am-fill-d9534f}{HTML}{D9534F}"),
          red,
        )
      ,
      test("a heavier dotted outline grows its dots and their gaps, and the SVG stays well-formed XML"):
        val g    = SceneGraph(
          "dotted",
          List(leaf("a", "A", ShapeType.ApplicationComponent).copy(outline = Some(Outline(Stroke.Dotted, 2.0)))),
          List(),
        )
        val d    = Diagram.draw(g).toOption.get
        val svg  = Svg.document(d)
        val xml  = scala.util.Try(
          javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(java.io.ByteArrayInputStream(svg.getBytes(StandardCharsets.UTF_8)))
        )
        val tikz = Tikz.standalone(d)
        val png  = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(Png.image(d, 100)))
        assertTrue(
          xml.isSuccess,
          svg.contains("stroke-width=\"2.53\" filter=\"url(#shadow)\" stroke-dasharray=\"0 5.07\""),
          tikz.contains("dash pattern=on 0pt off 3.8pt,line width=1.9pt"),
          !tikz.contains("line width=1.1pt"),
          png.getWidth > 0,
        )
      ,
      test("an outline's weight is kept between 0 and 4, and a colour that is not hex is left out"):
        val cases = List(
          Outline(weight = -1) -> Pen.Normal,
          Outline(weight = Double.NaN) -> Pen.Normal,
          Outline(weight = 9) -> Pen(4.0),
          Outline(colour = Some("red")) -> Pen.Normal,
          Outline(colour = Some("abcdef")) -> Pen(1.0, Some("am-fill-abcdef")),
        )
        val g     = SceneGraph(
          "kept",
          cases.zipWithIndex.map((c, i) => leaf(s"n$i", s"N$i", ShapeType.ApplicationComponent).copy(outline = Some(c._1))),
          Nil,
        )
        val d     = Diagram.draw(g).toOption.get
        val pens  = d.prims.collect { case b @ Prim.Box(_, _, _, _, _, _, _, _, Role.Element(_), _) => b.pen }
        assertTrue(
          cases.map(_._2).forall(pens.contains),
          !d.colours.keySet.exists(_.contains("red")),
        )
      ,
      test("a coloured connector with a bend writes no fill on its TikZ \\draw"):
        val line = Tikz.standalone(bent(Some(Head.FilledTriangle))).linesIterator
          .find(_.startsWith("\\draw[am link")).getOrElse("")
        assertTrue(line.nonEmpty, line.contains("draw=am-fill-2f6db5"), !line.contains("fill="))
      ,
      // compiled, so it proves what the PDF shows; reported as ignored where pdflatex or
      // pdftoppm is missing, never passed in silence
      {
        val compiled =
          test("compiled, a coloured bend fills nothing, and a filled or an open head is drawn in its colour"):
            val (filled, all) = bluePixels(bent(Some(Head.FilledTriangle)))
            val (open, _)     = bluePixels(bent(Some(Head.OpenTriangle)))
            val (bare, _)     = bluePixels(bent(None))
            // a line and a head, never the plane under the L (82017 pixels on 1.20.0); the
            // filled head adds blue of its own, the open one only its outline
            assertTrue(filled < all / 10, open < all / 10, bare > 200, filled - bare > 60, open - bare > 60)
        if latexTools then compiled else compiled @@ TestAspect.ignore
      },
      test("arrows and symbols are written as math, so pdflatex compiles them"):
        val arrows = SceneGraph(
          "Arrows",
          List(
            leaf("a", "persoon \u2194 rol", ShapeType.ApplicationComponent),
            leaf("b", "\u2264 5 \u2192 \u2713", ShapeType.ApplicationComponent),
          ),
          Nil,
        )
        val tex    = Tikz.standalone(Diagram.draw(arrows).toOption.get)
        val latex  = scala.util.Try(Process(Seq("pdflatex", "--version")).!!)
          .isSuccess
        val built  =
          !latex || {
            val dir = java.nio.file.Files.createTempDirectory("arrows")
            java.nio.file.Files.writeString(dir.resolve("a.tex"), tex)
            Process(
              Seq("pdflatex", "-interaction=nonstopmode", "-halt-on-error", "a.tex"),
              dir.toFile,
            ).!(ProcessLogger(_ => ())) == 0
          }
        assertTrue(
          tex.contains("\\ensuremath{\\leftrightarrow}"),
          tex.contains("\\ensuremath{\\leq}") && tex.contains("\\ensuremath{\\surd}"),
          !tex.contains("\u2194"),
          built,
        )
      ,
      test("text is escaped for LaTeX and XML, and control characters do not get through"):
        val odd  = SceneGraph(
          "Odd <names> & \"quotes\"",
          List(
            leaf("a", "100% of $5 #1 a_b {c} ~d ^e \\f", ShapeType.ApplicationComponent),
            leaf("b", "x < y > z \u0007bell", ShapeType.ApplicationComponent),
          ),
          Nil,
        )
        val d    = Diagram.draw(odd).toOption.get
        val tikz = Tikz.picture(d)
        val svg  = Svg.document(d)
        assertTrue(
          // the name breaks over lines, so each special on its own
          List(
            "100\\%",
            "\\$5",
            "\\#1",
            "a\\_b",
            "\\{c\\}",
            "\\textasciitilde{}d",
            "\\textasciicircum{}e",
            "\\textbackslash{}f",
          ).forall(tikz.contains),
          !tikz.contains("\u0007"),
          svg.contains("x &lt; y &gt; z  bell"),
          svg.contains("Odd &lt;names&gt; &amp; &quot;quotes&quot;"),
          !svg.contains("\u0007"),
        )
      ,
      test("a palette value that is not a colour is left out, a valid one with # is taken"):
        val palette = Map("am-application" -> "#c2f0ff", "am-business" -> "yellow", "x\"y" -> "000000")
        val tikz    = Tikz.picture(drawing, palette)
        val svg     = Svg.document(drawing, palette)
        assertTrue(
          tikz.contains("\\definecolor{am-application}{HTML}{C2F0FF}"),
          tikz.contains("\\providecolor{am-business}{HTML}{FFFFB5}"),
          !tikz.contains("yellow") && !tikz.contains("x\"y"),
          svg.contains("#C2F0FF") && !svg.contains("yellow"),
          Png.image(drawing, 30, palette).nonEmpty,
        )
      ,
      test("a junction is a dot, and every head is drawn for SVG and PNG"):
        val joined = SceneGraph(
          "Joined",
          List(
            leaf("a", "A", ShapeType.ApplicationComponent),
            SceneNode(SceneId("j"), NodeKind.OrJunction, "j"),
            leaf("b", "B", ShapeType.ApplicationComponent),
          ),
          List(edge("e1", "a", "j", RelationshipType.Flow), edge("e2", "j", "b", RelationshipType.Flow)),
        )
        val d      = Diagram.draw(joined).toOption.get
        val dot    = d.prims.collect { case c: Prim.Circle => c }.find(_.fill == Ink.Paper)
        val heads  = Head.values.toList.map(h => h -> Heads.at(h, (1.0, 0.0), (0.0, 0.0)))
        assertTrue(
          dot.exists(c => math.abs(c.r - Diagram.Style().junction / 2) < 1e-6),
          heads.forall((_, prims) => prims.nonEmpty),
          // every head ends on the tip, along the line it closes
          heads.forall((_, prims) =>
            prims.forall:
              case Prim.Poly(ps, _, _, _, _, _) => ps.forall(_._1 <= 1.0 + 1e-9)
              case Prim.Circle(c, r, _, _)      => c._1 + r <= 1.0 + 1e-9
              case _                            => true
          ),
        )
      ,
      test("a container name too long for its header breaks over two lines, and the header grows"):
        val long = SceneGraph(
          "Long",
          List(
            SceneNode(
              SceneId("c"),
              NodeKind.Element(ShapeType.ApplicationComponent),
              "An application platform with a remarkably long name indeed",
              List(leaf("x", "X", ShapeType.ApplicationFunction)),
            )
          ),
          Nil,
        )
        val d    = Diagram.draw(long).toOption.get
        val bold = d.prims.collect { case t: Prim.Text if t.weight == Weight.SemiBold => t }
        val c    = d.scene.elements.find(_.key == SceneId("c")).get
        val x    = d.scene.elements.find(_.key == SceneId("x")).get
        assertTrue(
          bold.size >= 2,
          bold.map(_.text).mkString(" ") == "An application platform with a remarkably long name indeed",
          x.y + x.d / 2 <=
          c.y + c.d / 2 - Diagram.Style().header - Diagram.Style().lead(Diagram.Style().nameSize) + 2e-3,
        )
      ,
      test("a box grows to hold its badges, which stand at its foot in its own colour"):
        val d     = Diagram.draw(badged).toOption.get
        val plain = Diagram.draw(badged.copy(nodes = badged.nodes.map(_.copy(badges = Nil))))
          .toOption.get
        def box(drawing: Diagram.Drawing, id: String) = drawing.scene.elements.find(_.key == SceneId(id)).get
        val payments                                  = box(d, "abb2")
        val marks                                     = d.prims.collect:
          case p @ Prim.Poly(ps, true, Ink.Line, _, Role.Mark("am-application"), _)
            if ps.forall(q => q._2 < payments.y - payments.d / 2 + 0.5) &&
            ps.forall(q => math.abs(q._1 - payments.x) < payments.w / 2) => p
        val words                                     = d.prims.collect:
          case Prim.Text(at, _, _, _, _, _, _)
            if math.abs(at._1 - payments.x) < payments.w / 2 &&
            math.abs(at._2 - payments.y) < payments.d / 2 => at._2
        val tall                                      = Diagram.draw(SceneGraph(
          "Tall",
          List(leaf("t", "Payments", ShapeType.ApplicationService, note = Some("A note " * 12))
            .copy(badges = List(Badge(Glyph.Star, "S")))),
          Nil,
        )).toOption.get
        val short                                     = Diagram.draw(SceneGraph(
          "Tall",
          List(leaf("t", "Payments", ShapeType.ApplicationService, note = Some("A note " * 12))),
          Nil,
        )).toOption.get
        assertTrue(
          box(d, "abb2").d >= box(plain, "abb2").d,
          box(tall, "t").d > box(short, "t").d,
          marks.flatMap(_.points.map(_._2)).max < words.min - 0.05,
          box(d, "abb2").w >= 6 * Diagram.Style().badgeSize,
          marks.nonEmpty,
          d.overflow == 0,
        )
      ,
      test("a box with a fill is drawn in it, as a layer of its own every writer defines"):
        val d    = Diagram.draw(badged).toOption.get
        val name = "am-fill-f4c542"
        assertTrue(
          d.colours == Map(name -> "F4C542"),
          d.prims.exists:
            case Prim.Box(_, _, _, _, _, _, _, _, Role.Element(`name`), _) => true
            case _                                                         =>
              false
          ,
          Tikz.picture(d).contains(s"\\definecolor{$name}{HTML}{F4C542}"),
          Svg.document(d).contains("#F4C542"),
          Png.image(d).nonEmpty,
          Diagram.fillName("not a colour").isEmpty,
        )
      ,
      test("a filled container is drawn in its colour, and a label on it stands on that colour"):
        val inner = SceneGraph(
          "Filled",
          List(
            SceneNode(
              SceneId("sys"),
              NodeKind.Element(ShapeType.ApplicationComponent),
              "Billing",
              List(
                leaf("a", "Invoicing", ShapeType.ApplicationComponent),
                leaf("b", "Ledger", ShapeType.ApplicationComponent),
              ),
              fill = Some("C8E6C9"),
              badges = List(Badge(Glyph.Star, "never drawn on a container")),
            )
          ),
          List(edge("f", "a", "b", RelationshipType.Flow, Some("books"))),
        )
        val d     = Diagram.draw(inner).toOption.get
        val name  = "am-fill-c8e6c9"
        assertTrue(
          d.prims.exists:
            case Prim.Box(_, _, _, _, _, _, _, _, Role.Container(`name`), _) => true
            case _                                                           =>
              false
          ,
          d.prims.exists:
            case Prim.Box(_, _, _, _, _, _, _, _, Role.Patch(Role.Container(`name`)), _) => true
            case _                                                                       =>
              false
          ,
          // a container carries no badges: its signs stay off its header
          !d.prims.exists:
            case Prim.Poly(_, true, Ink.Line, _, Role.Mark(`name`), _) => true
            case _                                                     => false,
        )
      ,
      test("the legend names every badge, in a frame that covers no box"):
        val d      = Diagram.draw(badged).toOption.get
        val said   = d.prims.collect { case Prim.Text(at, text, _, _, _, _, _) => (at, text) }
        val legend = said.filter((_, t) => t.startsWith("Solution ") || t == "Realized by (SBB)")
        val none   = Diagram.draw(badged.copy(legend = None)).toOption.get
        val frame  = d.prims.collectFirst { case b @ Prim.Box(_, _, _, _, _, Ink.Paper, _, true, Role.Plain, _) =>
          b
        }.get
        def apart(e: SceneElement) =
          e.x + e.w / 2 <= frame.x || e.x - e.w / 2 >= frame.x + frame.w ||
          e.y + e.d / 2 <= frame.y || e.y - e.d / 2 >= frame.y + frame.h
        assertTrue(
          legend.size == 13,
          legend.forall((at, _) =>
            at._1 >= frame.x && at._1 <= frame.x + frame.w && at._2 >= frame.y && at._2 <= frame.y + frame.h
          ),
          d.scene.elements.forall(apart),
          !none.prims.exists:
            case Prim.Text(_, "Realized by (SBB)", _, _, _, _, _) => true
            case _                                                =>
              false,
        )
      ,
      test("every glyph is drawn inside its unit square"):
        val inside = Glyph.values.toList.forall: g =>
          val ps = Notation.glyph(g).flatMap:
            case Prim.Poly(points, _, _, _, _, _)       => points
            case Prim.Circle(c, r, _, _)                => List((c._1 - r, c._2 - r), (c._1 + r, c._2 + r))
            case Prim.Ellipse(c, rx, ry, _, _)          => List((c._1 - rx, c._2 - ry), (c._1 + rx, c._2 + ry))
            case Prim.Box(x, y, w, h, _, _, _, _, _, _) => List((x, y), (x + w, y + h))
            case _                                      => Nil
          ps.nonEmpty && ps.forall(p => p._1 >= -1e-9 && p._1 <= 1 + 1e-9 && p._2 >= -1e-9 && p._2 <= 1 + 1e-9)
        assertTrue(inside)
      ,
      test("an empty drawing still has an extent"):
        val empty = Diagram.Drawing("Empty", Nil, Scene("Empty", 0, 0, Nil, Nil), Diagram.Style(), "Nimbus Sans", 0)
        assertTrue(Extent.of(empty, 0.1) == (0.0, 0.0, 1.0, 1.0), Svg.document(empty).startsWith("<svg")),
    )

end DiagramSpec
