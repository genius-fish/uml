package fish.genius.uml.scene

import fish.genius.uml.dsl.archimate.ShapeGroup

// What a graph holds, as the layout needs it: every node with its container, the
// connectors that can be drawn, and the partition each node goes in.
final private[scene] case class SceneStructure(
  roots: List[SceneNode],
  nodes: List[SceneNode],
  parentOf: Map[SceneId, SceneId],
  edges: List[SceneEdge],
  partition: Map[SceneId, Int]):
  val byId: Map[SceneId, SceneNode] = nodes.map(node => node.id -> node).toMap

  def chain(id: SceneId): List[SceneId] = SceneStructure.chain(parentOf, id)

  def isContainer(id: SceneId): Boolean = byId.get(id).exists(_.isContainer)

private[scene] object SceneStructure:

  // Motivation at the back, Implementation at the front; a node with no layer of its own
  // (a grouping, a junction) defaults here.
  val BUSINESS: Int = 2

  // id and its ancestors, nearest first
  def chain(parentOf: Map[SceneId, SceneId], id: SceneId): List[SceneId] =
    Iterator.iterate(Option(id))(_.flatMap(parentOf.get)).takeWhile(_.isDefined).flatten.toList

  def of(graph: SceneGraph): SceneStructure =
    val nodes    = graph.allNodes
    val parentOf = nodes.flatMap(parent => parent.children.map(_.id -> parent.id)).toMap
    // A connector to a node's own ancestor cannot be drawn on the plinth it stands on.
    val edges    = graph.edges.filter: edge =>
      edge.source != edge.target &&
      !chain(parentOf, edge.source).contains(edge.target) &&
      !chain(parentOf, edge.target).contains(edge.source)
    val byId     = nodes.map(node => node.id -> node).toMap
    val around   = edges.flatMap(edge =>
      List(edge.source -> edge.target, edge.target -> edge.source)
    ).groupMap(_._1)(_._2)
    // An element takes its layer's partition; a grouping its children's, a junction its
    // neighbours'.
    def partitionOf(node: SceneNode): Int =
      layerOf(node.kind)
        .orElse(node.children.map(partitionOf).minOption)
        .orElse(around.getOrElse(node.id, Nil).flatMap(byId.get).flatMap(other =>
          layerOf(other.kind)
        ).minOption)
        .getOrElse(BUSINESS)
    SceneStructure(
      graph.nodes,
      nodes,
      parentOf,
      edges,
      nodes.map(node => node.id -> partitionOf(node)).toMap,
    )
  end of

  private def layerOf(kind: NodeKind): Option[Int] = kind match
    case NodeKind.Element(shape)                                        =>
      Some(shape.group match
        case ShapeGroup.Motivation     => 0
        case ShapeGroup.Strategy       => 1
        case ShapeGroup.Business       => BUSINESS
        case ShapeGroup.Application    => 3
        case ShapeGroup.Technology     => 4
        case ShapeGroup.Physical       => 5
        case ShapeGroup.Implementation => 6)
    case NodeKind.Grouping | NodeKind.AndJunction | NodeKind.OrJunction => None

end SceneStructure
