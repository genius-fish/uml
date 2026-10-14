package fish.genius.uml.scene

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import zio.json.*
import zio.test.*

// The 3D scene with the default options is the scene 1.9.1 made: the flat-diagram
// options (footprint, headerAtBack, spreadPorts, labelSize) change nothing while they
// are off. The golden files were written by 1.9.1's own layout; SCENE_GOLDEN=dir writes
// this build's scenes there instead, to make new ones.
object SceneGoldenSpec extends ZIOSpecDefault:

  import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}

  private def leaf(id: String, shape: ShapeType) = SceneNode(SceneId(id), NodeKind.Element(shape), id)

  private def edge(
    id: String,
    from: String,
    to: String,
    kind: RelationshipType,
  ) =
    SceneEdge(SceneId(id), SceneId(from), SceneId(to), kind)

  // containers holding one small child, one holding several, a grouping and a junction:
  // what a container's least size decides
  private val nested = SceneGraph(
    "Nested",
    List(
      leaf("customer", ShapeType.BusinessActor),
      SceneNode(
        SceneId("crm"),
        NodeKind.Element(ShapeType.ApplicationComponent),
        "CRM",
        List(leaf("api", ShapeType.ApplicationInterface)),
      ),
      SceneNode(
        SceneId("hub"),
        NodeKind.Element(ShapeType.ApplicationComponent),
        "Hub",
        List(SceneNode(SceneId("join"), NodeKind.AndJunction, "join")),
      ),
      SceneNode(
        SceneId("erp"),
        NodeKind.Element(ShapeType.ApplicationComponent),
        "ERP",
        List(
          leaf("ledger", ShapeType.ApplicationFunction),
          leaf("billing", ShapeType.ApplicationProcess),
          leaf("orders", ShapeType.ApplicationDataObject),
        ),
      ),
      SceneNode(
        SceneId("platform"),
        NodeKind.Grouping,
        "Platform",
        List(leaf("k8s", ShapeType.TechnologyNode), leaf("pg", ShapeType.TechnologySystemSoftware)),
      ),
    ),
    List(
      edge("e1", "customer", "api", RelationshipType.Serving),
      edge("e2", "api", "join", RelationshipType.Flow),
      edge("e3", "join", "ledger", RelationshipType.Flow),
      edge("e4", "billing", "orders", RelationshipType.WriteAccess),
      edge("e5", "k8s", "crm", RelationshipType.Serving),
      edge("e6", "pg", "erp", RelationshipType.Serving),
      edge("e7", "ledger", "billing", RelationshipType.Triggering),
    ),
  )

  private val graphs = List("nested" -> nested)

  private def golden(name: String): Option[String] =
    Option(getClass.getResourceAsStream(s"/golden/$name.json"))
      .map(in =>
        try String(in.readAllBytes, StandardCharsets.UTF_8)
        finally in.close()
      )

  override def spec =
    suite("scene / the 3D scene with the default options")(
      graphs.map: (name, graph) =>
        test(s"$name: as 1.9.1 laid it out"):
          val json = SceneLayout.layout(graph).toOption.get.toJsonPretty
          sys.env.get("SCENE_GOLDEN").foreach: dir =>
            Files.createDirectories(Path.of(dir))
            Files.writeString(Path.of(dir, s"$name.json"), json, StandardCharsets.UTF_8)
          assertTrue(sys.env.contains("SCENE_GOLDEN") || golden(name).contains(json))
      *
    )

end SceneGoldenSpec
