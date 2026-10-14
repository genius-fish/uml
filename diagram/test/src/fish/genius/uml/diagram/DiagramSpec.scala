package fish.genius.uml.diagram

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

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
      test("the same graph gives the same drawing"):
        assertTrue(Diagram.draw(landscape).toOption.map(_.prims) == Option(drawing.prims))
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
              case Prim.Poly(ps, _, _, _, _) => ps.forall(_._1 <= 1.0 + 1e-9)
              case Prim.Circle(c, r, _, _)   => c._1 + r <= 1.0 + 1e-9
              case _                         => true
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
      test("an empty drawing still has an extent"):
        val empty = Diagram.Drawing("Empty", Nil, Scene("Empty", 0, 0, Nil, Nil), Diagram.Style(), "Nimbus Sans", 0)
        assertTrue(Extent.of(empty, 0.1) == (0.0, 0.0, 1.0, 1.0), Svg.document(empty).startsWith("<svg")),
    )

end DiagramSpec
