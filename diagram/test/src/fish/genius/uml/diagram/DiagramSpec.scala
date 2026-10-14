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
          drawing.prims.count(_.isInstanceOf[Prim.Connector]) == landscape.edges.size,
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
        ),
    )

end DiagramSpec
