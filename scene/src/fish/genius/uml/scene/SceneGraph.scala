package fish.genius.uml.scene

import zio.json.*

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}

// What a scene is laid out from: ArchiMate elements, nested by containment, and the
// relationships between them. Which element contains which is the caller's to say
// (a 2D diagram draws the same thing as a boundary); in the scene the children stand
// on their container's plinth.

opaque type SceneId = String

object SceneId:
  def apply(value: String): SceneId = value

  // The way in from untrusted input: an id is never empty or blank.
  def validated(raw: String): Either[GraphProblem, SceneId] =
    Either.cond(raw.trim.nonEmpty, raw, GraphProblem.BlankId)

  extension (id: SceneId) def value: String = id

  given Ordering[SceneId] = Ordering.String
  given JsonCodec[SceneId] = JsonCodec.string

enum NodeKind derives CanEqual:
  case Element(shape: ShapeType)
  case Grouping
  case AndJunction
  case OrJunction

final case class SceneNode(
  id: SceneId,
  kind: NodeKind,
  label: String,
  children: List[SceneNode] = Nil):
  def isContainer: Boolean = children.nonEmpty

  // this node and everything standing on it, parents before children
  def flatten: List[SceneNode] = this :: children.flatMap(_.flatten)

final case class SceneEdge(
  id: SceneId,
  source: SceneId,
  target: SceneId,
  relationship: RelationshipType)

final case class SceneGraph(
  name: String,
  nodes: List[SceneNode],
  edges: List[SceneEdge]):
  def allNodes: List[SceneNode] = nodes.flatMap(_.flatten)

  // Every problem at once, so a caller fixes its graph in one round.
  def problems: List[GraphProblem] =
    val ids        = allNodes.map(_.id) ++ edges.map(_.id)
    val blank      = ids.filter(_.value.trim.isEmpty).distinct.map(_ => GraphProblem.BlankId)
    val duplicates = ids.diff(ids.distinct).distinct.map(GraphProblem.DuplicateId(_))
    val known      = allNodes.map(_.id).toSet
    val dangling   = edges.flatMap: e =>
      List(e.source, e.target).filterNot(known).map(GraphProblem.UnknownEndpoint(e.id, _))
    blank ++ duplicates ++ dangling

end SceneGraph

enum GraphProblem derives CanEqual:
  case BlankId
  case DuplicateId(id: SceneId)
  case UnknownEndpoint(edge: SceneId, endpoint: SceneId)

enum SceneError derives CanEqual:
  case InvalidGraph(problems: ::[GraphProblem])
  case LayoutFailed(reason: String)
  case ResourceMissing(name: String)
