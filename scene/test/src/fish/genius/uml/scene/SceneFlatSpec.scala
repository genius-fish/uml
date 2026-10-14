package fish.genius.uml.scene

import zio.test.*

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}

// The options a flat diagram lays a graph out with (the 3D scene leaves them off, and
// SceneGoldenSpec holds that): each connector its own port, edges up the page handed
// to ELK reversed, rows folded to a width, and the compact placement.
object SceneFlatSpec extends ZIOSpecDefault:

  private def leaf(id: String, shape: ShapeType) = SceneNode(SceneId(id), NodeKind.Element(shape), id)

  private def edge(
    id: String,
    from: String,
    to: String,
    kind: RelationshipType,
  ) =
    SceneEdge(SceneId(id), SceneId(from), SceneId(to), kind)

  private val flat = SceneLayout.Options(spreadPorts = true, headerAtBack = true, compact = true)

  // the application layer stands under the business layer: its services serve up
  private val upward = SceneGraph(
    "Up",
    List(
      leaf("process", ShapeType.BusinessProcess),
      SceneNode(
        SceneId("app"),
        NodeKind.Element(ShapeType.ApplicationComponent),
        "app",
        List(leaf("service", ShapeType.ApplicationService), leaf("api", ShapeType.ApplicationInterface)),
      ),
    ),
    List(
      edge("serves", "service", "process", RelationshipType.Serving),
      edge("offers", "api", "process", RelationshipType.Serving),
    ),
  )

  private def on(
    p: ScenePoint,
    e: SceneElement,
    side: PortName,
  ) = side match
    case PortName.North => math.abs(p.y - (e.y + e.d / 2)) < 0.01 && math.abs(p.x - e.x) <= e.w / 2 + 0.01
    case PortName.South => math.abs(p.y - (e.y - e.d / 2)) < 0.01 && math.abs(p.x - e.x) <= e.w / 2 + 0.01
    case PortName.East  => math.abs(p.x - (e.x + e.w / 2)) < 0.01 && math.abs(p.y - e.y) <= e.d / 2 + 0.01
    case PortName.West  => math.abs(p.x - (e.x - e.w / 2)) < 0.01 && math.abs(p.y - e.y) <= e.d / 2 + 0.01

  private def orthogonal(points: List[ScenePoint]) =
    points.zip(points.drop(1)).forall((a, b) => math.abs(a.x - b.x) < 0.003 || math.abs(a.y - b.y) < 0.003)

  override def spec =
    suite("scene / laid out flat")(
      test("an edge up the page leaves its source's top for its target's bottom, straight on"):
        val scene = SceneLayout.layout(upward, flat).toOption.get
        val at    = scene.elements.map(e => e.key -> e).toMap
        assertTrue(
          scene.relationships.size == 2,
          scene.relationships.forall: r =>
            r.start.exists(on(_, at(r.source), r.sourcePort)) && r.end.exists(on(_, at(r.target), r.targetPort)) &&
            r.sourcePort == PortName.North && r.targetPort == PortName.South &&
            orthogonal(r.start.toList ++ r.bends ++ r.end.toList),
          // two connectors, two points on the process's bottom
          scene.relationships.flatMap(_.end).distinct.size == 2,
        )
      ,
      test("a row wider than the page folds"):
        val many   = SceneGraph("Many", (1 to 12).toList.map(i => leaf(s"n$i", ShapeType.ApplicationComponent)), Nil)
        val wide   = SceneLayout.layout(many, SceneLayout.Options(maxPerRow = 12)).toOption.get
        val folded = SceneLayout.layout(many, SceneLayout.Options(maxPerRow = 12, rowWidth = 9)).toOption.get
        assertTrue(wide.width > 9, folded.width <= 9 + 0.01)
      ,
      test("the compact placement lays a nested graph out, falling back where ELK cannot"):
        assertTrue(SceneLayout.layout(SceneChecks.large, flat).isRight),
    )

end SceneFlatSpec
