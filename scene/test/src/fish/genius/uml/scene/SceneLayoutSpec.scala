package fish.genius.uml.scene

import zio.json.*
import zio.test.*

import zio.Scope

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*

object SceneLayoutSpec extends ZIOSpecDefault:

  private def node(
    label: String,
    shape: ShapeType,
    children: SceneNode*
  ): SceneNode =
    SceneNode(SceneId(label), NodeKind.Element(shape), label, children.toList)

  private def edge(
    source: String,
    target: String,
    relationship: RelationshipType,
  ): SceneEdge =
    SceneEdge(SceneId(s"$source>$target"), SceneId(source), SceneId(target), relationship)

  // Four layers, a nested component and a grouping: enough for every rule.
  private val graph = SceneGraph(
    "Scene",
    List(
      node("Faster delivery", MotivationRequirement),
      node("Customer", BusinessActor),
      node("Handle Order", BusinessProcess),
      node(
        "Order System",
        ApplicationComponent,
        node("Order API", ApplicationComponent),
        node("Order Worker", ApplicationComponent),
      ),
      node("Order Intake", ApplicationService),
      node("Order", ApplicationDataObject),
      node("Application Server", TechnologyNode),
      node("Database", TechnologySystemSoftware),
      SceneNode(
        SceneId("Data Platform"),
        NodeKind.Grouping,
        "Data Platform",
        List(node("Message Broker", TechnologySystemSoftware)),
      ),
    ),
    List(
      edge("Customer", "Handle Order", Assignment),
      edge("Order Intake", "Handle Order", Serving),
      edge("Order API", "Order Intake", Realization),
      edge("Order Worker", "Order", Access),
      edge("Application Server", "Order System", Serving),
      edge("Database", "Order Worker", Serving),
      edge("Handle Order", "Faster delivery", Realization),
    ),
  )

  private def laid(g: SceneGraph): Scene =
    SceneLayout.layout(g).fold(error => Scene(s"failed: $error", 0, 0, Nil, Nil), identity)

  private lazy val scene = laid(graph)

  private def el(s: Scene, label: String): Option[SceneElement] = s.elements.find(_.name == label)

  private def y(label: String): Double = el(scene, label).map(_.y).getOrElse(Double.NaN)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("scene / SceneLayout")(
      test("every node gets a plinth, and no two plinths on one floor overlap"):
        assertTrue(scene.elements.size == graph.allNodes.size, SceneChecks.overlaps(scene).isEmpty)
      ,
      test("layers lie back to front: motivation, business, application, technology"):
        assertTrue(
          y("Faster delivery") > y("Handle Order"),
          y("Handle Order") > y("Order Intake"),
          y("Order Intake") > y("Application Server"),
        )
      ,
      test("a nested node stands inside its parent, one plinth higher"):
        val nested =
          for
            system <- el(scene, "Order System")
            api    <- el(scene, "Order API")
          yield system.container && api.parent.contains(system.key) && SceneChecks.inside(api, system) &&
          api.z == SceneLayout.PLINTH_HEIGHT
        assertTrue(nested.contains(true))
      ,
      test("a grouping holds its member"):
        val held =
          for
            group  <- el(scene, "Data Platform")
            broker <- el(scene, "Message Broker")
          yield group.shape == "grouping" && broker.parent.contains(group.key) && SceneChecks.inside(broker, group)
        assertTrue(held.contains(true))
      ,
      test("every connector leaves through a port that faces the other end"):
        assertTrue(scene.relationships.nonEmpty, SceneChecks.portsFacingAway(scene).isEmpty)
      ,
      test("every route is orthogonal from port to port and runs through no other plinth"):
        assertTrue(SceneChecks.diagonals(scene).isEmpty, SceneChecks.throughPlinths(scene).isEmpty)
      ,
      test("relationships carry the builder's names"):
        assertTrue(
          scene.relationships.map(_.kind).toSet == Set("assignment", "serving", "realization", "access")
        )
      ,
      test("unconnected nodes fold into rows instead of one long line"):
        val goals  = SceneGraph("Goals", (1 to 13).toList.map(i => node(f"Goal $i%02d", MotivationGoal)), Nil)
        val placed = laid(goals).elements
        assertTrue(placed.size == 13, placed.map(_.y).distinct.size >= 3, placed.map(_.x).distinct.size <= 4)
      ,
      test("a fan of leaves off one node folds into rows too"):
        val leaves = (1 to 30).toList.map(i => node(f"Deliverable $i%02d", ImplementationDeliverable))
        val fan    = SceneGraph(
          "Fan",
          node("Programme", ImplementationWorkPackage) :: leaves,
          leaves.map(l => edge("Programme", l.label, Realization)),
        )
        val placed = laid(fan)
        val rows   = placed.elements.filter(_.shape == "deliverable").map(_.y).distinct
        assertTrue(rows.size >= 3, placed.width < 30 * SceneLayout.PLINTH_WIDTH)
      ,
      test("a large graph with nesting, fan-outs and cross-container connectors stays clean"):
        val big = laid(SceneChecks.large)
        assertTrue(
          big.elements.size == SceneChecks.large.allNodes.size,
          big.elements.size > 100,
          big.relationships.size == SceneChecks.large.edges.size,
          SceneChecks.overlaps(big).isEmpty,
          SceneChecks.diagonals(big).isEmpty,
          SceneChecks.throughPlinths(big).isEmpty,
        )
      ,
      test("the scene round-trips through JSON, with the shape under \"type\" and lower-case ports"):
        val json = scene.toJsonPretty
        assertTrue(
          json.contains("\"type\" : \"business-actor\""),
          json.contains("\"sourcePort\" : \"south\"") || json.contains("\"sourcePort\" : \"north\""),
          json.fromJson[Scene] == Right(scene),
        )
      ,
      test("every shape and relationship type has its own name"):
        val shapes = ShapeType.values.toList.map(SceneNames.of)
        val kinds  = RelationshipType.values.toList.map(SceneNames.of)
        assertTrue(
          shapes.distinct.size == ShapeType.values.length,
          kinds.distinct.size == RelationshipType.values.length,
        )
      ,
      test("an empty graph is an empty scene"):
        assertTrue(SceneLayout.layout(SceneGraph("Empty", Nil, Nil)) == Right(Scene("Empty", 0, 0, Nil, Nil)))
      ,
      test("a graph with a duplicate id or a dangling edge is refused with every problem"):
        val broken   = SceneGraph(
          "Broken",
          List(node("A", BusinessActor), node("A", BusinessRole)),
          List(edge("A", "Nowhere", Serving)),
        )
        val problems = SceneLayout.layout(broken) match
          case Left(SceneError.InvalidGraph(found)) => found.toList
          case _                                    => Nil
        assertTrue(
          problems.contains(GraphProblem.DuplicateId(SceneId("A"))),
          problems.contains(GraphProblem.UnknownEndpoint(SceneId("A>Nowhere"), SceneId("Nowhere"))),
        )
      ,
      test("an id from untrusted input is never blank"):
        assertTrue(
          SceneId.validated("  ") == Left(GraphProblem.BlankId),
          SceneId.validated("crm").map(_.value) == Right("crm"),
        ),
    )

end SceneLayoutSpec
