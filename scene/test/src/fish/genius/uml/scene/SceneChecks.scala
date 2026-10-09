package fish.genius.uml.scene

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.dsl.archimate.ShapeType.*

// What a good scene is, as checks that list what is wrong, and a large graph to run
// them on.
object SceneChecks:

  private val TOLERANCE = 0.02

  def inside(child: SceneElement, parent: SceneElement): Boolean =
    child.x - child.w / 2 >= parent.x - parent.w / 2 - 1e-6 &&
    child.x + child.w / 2 <= parent.x + parent.w / 2 + 1e-6 &&
    child.y - child.d / 2 >= parent.y - parent.d / 2 - 1e-6 &&
    child.y + child.d / 2 <= parent.y + parent.d / 2 + 1e-6

  def overlaps(scene: Scene): List[(String, String)] =
    scene.elements.groupBy(_.parent).values.toList.flatMap: floor =>
      floor.combinations(2).toList.collect:
        case List(a, b)
          if math.abs(a.x - b.x) < (a.w + b.w) / 2 - 1e-6 && math.abs(a.y - b.y) < (a.d + b.d) / 2 - 1e-6 =>
          (a.name, b.name)

  def port(e: SceneElement, side: PortName): (Double, Double) = side match
    case PortName.North => (e.x, e.y + e.d / 2)
    case PortName.South => (e.x, e.y - e.d / 2)
    case PortName.East  => (e.x + e.w / 2, e.y)
    case PortName.West  => (e.x - e.w / 2, e.y)

  private def ends(scene: Scene, r: SceneRelationship): Option[(SceneElement, SceneElement)] =
    scene.elements.find(_.key == r.source).zip(scene.elements.find(_.key == r.target))

  def route(scene: Scene, r: SceneRelationship): List[(Double, Double)] =
    ends(scene, r).toList.flatMap: (s, t) =>
      port(s, r.sourcePort) :: r.bends.map(p => (p.x, p.y)) ::: List(port(t, r.targetPort))

  def portsFacingAway(scene: Scene): List[SceneId] =
    scene.relationships.filterNot: r =>
      ends(scene, r).exists: (s, t) =>
        r.sourcePort match
          case PortName.South => t.y < s.y
          case PortName.North => t.y > s.y
          case PortName.East  => t.x > s.x
          case PortName.West  => t.x < s.x
    .map(_.key)

  def diagonals(scene: Scene): List[SceneId] =
    scene.relationships
      .filterNot: r =>
        val points = route(scene, r)
        points.zip(points.drop(1)).forall { case ((x0, y0), (x1, y1)) =>
          math.abs(x0 - x1) < TOLERANCE || math.abs(y0 - y1) < TOLERANCE
        }
      .map(_.key)

  // a route through the plinth of an element that is neither of its ends nor holds them
  def throughPlinths(scene: Scene): List[(SceneId, String)] =
    def holders(id: SceneId): Set[SceneId] =
      Iterator
        .iterate(scene.elements.find(_.key == id))(_.flatMap(e => e.parent.flatMap(p => scene.elements.find(_.key == p))))
        .takeWhile(_.isDefined)
        .flatten
        .map(_.key)
        .toSet
    scene.relationships.flatMap: r =>
      val own    = holders(r.source) ++ holders(r.target)
      val points = route(scene, r)
      scene.elements
        .filterNot(e => e.container || own.contains(e.key))
        .filter: e =>
          val box = OrthogonalRouter.Box(e.x, e.y, e.w - 0.1, e.d - 0.1)
          points.zip(points.drop(1)).exists { case ((x0, y0), (x1, y1)) => box.crosses(x0, y0, x1, y1) }
        .map(e => (r.key, e.name))

  end throughPlinths

  // About a hundred nodes per scale over every layer: containers with children,
  // groupings, a fan of thirty leaves off one work package, connectors between containers.
  lazy val large: SceneGraph = generated(1)

  def generated(scale: Int, withJunctions: Boolean = true): SceneGraph =
    def node(
      id: String,
      shape: ShapeType,
      children: List[SceneNode] = Nil,
    )                =
      SceneNode(SceneId(id), NodeKind.Element(shape), id, children)
    def edge(
      source: String,
      target: String,
      relationship: RelationshipType,
    )                =
      SceneEdge(SceneId(s"$source>$target"), SceneId(source), SceneId(target), relationship)
    val goals        = (1 to 14 * scale).toList.map(i => node(s"goal$i", MotivationGoal))
    val capabilities = (1 to 8 * scale).toList.map(i => node(s"cap$i", StrategyCapability))
    val processes    = (1 to 10 * scale).toList.map(i => node(s"proc$i", BusinessProcess))
    val systems      = (1 to 6 * scale).toList.map: i =>
      node(s"sys$i", ApplicationComponent, (1 to 3).toList.map(j => node(s"sys$i.c$j", ApplicationComponent)))
    val services     = (1 to 8 * scale).toList.map(i => node(s"svc$i", ApplicationService))
    val platform     = SceneNode(
      SceneId("platform"),
      NodeKind.Grouping,
      "Platform",
      (1 to 5 * scale).toList.map(i => node(s"node$i", TechnologyNode, List(node(s"node$i.os", TechnologySystemSoftware)))),
    )
    val deliverables = (1 to 30 * scale).toList.map(i => node(s"del$i", ImplementationDeliverable))
    val programme    = node("programme", ImplementationWorkPackage)
    val junctions    =
      if withJunctions then (1 to scale).toList.map(i => SceneNode(SceneId(s"or$i"), NodeKind.OrJunction, s"or$i"))
      else Nil
    val nodes        =
      goals ++ capabilities ++ processes ++ systems ++ services ++ List(platform, programme) ++ deliverables ++
      junctions
    val edges        =
      processes.zipWithIndex.map((p, i) =>
        edge(p.label, s"cap${i % capabilities.size + 1}", RelationshipType.Realization)
      ) ++
      services.zipWithIndex.map((s, i) => edge(s.label, s"proc${i % processes.size + 1}", RelationshipType.Serving)) ++
      systems.zipWithIndex.flatMap((s, i) =>
        s.children.zipWithIndex.map((c, j) =>
          edge(c.label, s"svc${(j * 3 + i) % services.size + 1}", RelationshipType.Realization)
        )
      ) ++
      (1 to 5 * scale).toList.map(i =>
        edge(s"node$i.os", s"sys${i % systems.size + 1}.c${i % 3 + 1}", RelationshipType.Serving)
      ) ++
      capabilities.zipWithIndex.map((c, i) => edge(c.label, s"goal${i + 1}", RelationshipType.Realization)) ++
      deliverables.map(d => edge("programme", d.label, RelationshipType.Realization)) ++
      List(
        edge("sys1.c1", "sys2.c2", RelationshipType.Flow),
        edge("sys3.c3", "sys5.c1", RelationshipType.Triggering),
      ) ++
      processes.sliding(2).collect { case List(a, b) => edge(a.label, b.label, RelationshipType.Triggering) }.toList ++
      junctions.flatMap(j =>
        List(
          edge(s"proc$scale", j.label, RelationshipType.Triggering),
          edge(j.label, "proc1", RelationshipType.Triggering),
        )
      )
    SceneGraph(s"Generated x$scale", nodes, edges)
  end generated

end SceneChecks
