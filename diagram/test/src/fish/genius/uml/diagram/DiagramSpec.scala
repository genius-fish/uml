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
  ) =
    SceneNode(SceneId(id), NodeKind.Element(shape), label)

  private def edge(
    id: String,
    source: String,
    target: String,
    kind: RelationshipType,
  ) =
    SceneEdge(SceneId(id), SceneId(source), SceneId(target), kind)

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
      ),
      leaf("erp", "SAP S/4HANA Finance & Logistics", ShapeType.ApplicationComponent),
      leaf("orderdata", "Order", ShapeType.ApplicationDataObject),
      leaf("billing", "Invoice run", ShapeType.ApplicationProcess),
      leaf("k8s", "Kubernetes Platform", ShapeType.TechnologyNode),
      leaf("pg", "PostgreSQL", ShapeType.TechnologySystemSoftware),
    ),
    List(
      edge("e1", "customer", "order", RelationshipType.Assignment),
      edge("e2", "ordering", "order", RelationshipType.Serving),
      edge("e3", "shop", "ordering", RelationshipType.Realization),
      edge("e4", "app", "ordering", RelationshipType.Realization),
      edge("e5", "shop", "api", RelationshipType.Flow),
      edge("e6", "crm", "erp", RelationshipType.Flow),
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

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("diagram / an ArchiMate graph drawn flat")(
      test("every box fits its name, no two leaves overlap, and every relationship is drawn"):
        val drawing = Diagram.draw(landscape).toOption.get
        val leaves  = drawing.scene.elements.filterNot(_.container)
        // the long ERP name widens or wraps its box
        val erp     = drawing.scene.elements.find(_.key == SceneId("erp")).get
        assertTrue(
          drawing.overflow == 0,
          leaves.combinations(2).forall { case List(a, b) => !overlaps(a, b); case _ => true },
          drawing.prims.count(_.isInstanceOf[Prim.Connector]) == landscape.edges.size,
          erp.d > Diagram.Style().boxHeight || erp.w > Diagram.Style().boxWidth,
        )
      ,
      test("a container's header is at the top: its children stand below its name"):
        val drawing = Diagram.draw(landscape).toOption.get
        val crm     = drawing.scene.elements.find(_.key == SceneId("crm")).get
        val parts   = drawing.scene.elements.filter(_.parent.contains(SceneId("crm")))
        val top     = crm.y + crm.d / 2
        assertTrue(parts.nonEmpty, parts.forall(p => p.y + p.d / 2 <= top - Diagram.Style().header + 1e-6))
      ,
      test("TikZ and SVG write the same drawing, with names escaped"):
        val drawing = Diagram.draw(landscape).toOption.get
        val tikz    = Tikz.picture(drawing)
        val svg     = Svg.document(drawing)
        sys.env.get("DIAGRAM_OUT").map(Path.of(_)).map: dir =>
          Files.createDirectories(dir)
          Files.writeString(dir.resolve("landscape.tex"), Tikz.standalone(drawing), StandardCharsets.UTF_8)
          Files.writeString(dir.resolve("landscape.svg"), svg, StandardCharsets.UTF_8)
        assertTrue(
          tikz.contains("\\begin{tikzpicture}"),
          tikz.contains("S/4HANA Finance \\&") || tikz.contains("\\& Logistics"),
          svg.contains("&amp;"),
          svg.startsWith("<svg") && svg.endsWith("</svg>"),
          "&(?!amp;|lt;|gt;|quot;)".r.findFirstIn(svg).isEmpty,
        ),
    )

end DiagramSpec
