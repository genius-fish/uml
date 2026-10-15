package fish.genius.uml.scene

import zio.json.*

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}

// What a scene is laid out from: ArchiMate elements, nested by containment, and the
// relationships between them. Which element contains which is the caller's to say
// (a 2D diagram draws the same thing as a boundary); in the scene the children stand
// on their container's plinth. A leaf element may carry a passport, which the scene
// shows on a glass pane beside its plinth (PaneLayout).

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

// A small sign stamped on a box, such as a padlock for an API that is not public, or
// one of the geometric glyphs a diagram hands out to tell several things apart. The
// label says what it stands for, in the legend.
enum Glyph derives CanEqual:

  case Circle, Square, TriangleUp, Diamond, Star, Hexagon, TriangleDown, Cross, Pentagon,
    Ring, Ellipse, Chevron, Globe, Padlock, Warning, Failure

// A badge is its logo when it has one (the glyph then stands in only where a logo
// cannot be drawn), else its glyph.
final case class Badge(
  glyph: Glyph,
  label: String,
  logo: Option[Logo] = None)

// A logo as a diagram draws it: filled outlines in SVG path syntax, in the coordinates
// of its own view box (y running down, as in SVG), each in its colour (hex); or a
// picture (PNG or JPEG, base64) of so many pixels.
final case class LogoShape(
  path: String,
  fill: String,
  evenOdd: Boolean = false)

enum Logo derives CanEqual:

  case Vector(
    minX: Double,
    minY: Double,
    width: Double,
    height: Double,
    shapes: List[LogoShape])

  case Picture(
    data: String,
    mime: String,
    width: Int,
    height: Int)

  // width over height
  def aspect: Double = this match
    case Vector(_, _, w, h, _) => if h > 0 then w / h else 1.0
    case Picture(_, _, w, h)   => if h > 0 then w.toDouble / h else 1.0

end Logo

// What the badges of a diagram stand for, drawn under it.
final case class Legend(title: String, entries: List[Badge])

// caption and note are for a flat diagram, which writes them in the box under the
// label: the element's type (and technology), and a line or two about it. So are fill,
// a colour (hex) that sets the box apart from its layer, badges, the signs at its
// foot, and logo, the product's logo in a leaf's top left corner; the 3D scene leaves
// them all out.
final case class SceneNode(
  id: SceneId,
  kind: NodeKind,
  label: String,
  children: List[SceneNode] = Nil,
  passport: Option[Passport] = None,
  caption: Option[String] = None,
  note: Option[String] = None,
  fill: Option[String] = None,
  badges: List[Badge] = Nil,
  logo: Option[Logo] = None):
  def isContainer: Boolean = children.nonEmpty

  // this node and everything standing on it, parents before children
  def flatten: List[SceneNode] = this :: children.flatMap(_.flatten)

end SceneNode

// label is written along the connector; the layout keeps room for it when the
// options say how large it is (SceneLayout.Options.labelSize).
final case class SceneEdge(
  id: SceneId,
  source: SceneId,
  target: SceneId,
  relationship: RelationshipType,
  label: Option[String] = None)

// A walk through the graph in order, such as the interactions of a sequence diagram:
// each step goes from one node to another and says what passes between them. The
// Blender builder flies its camera along one (archimate3d_blender.py --flyover).
final case class SceneStep(
  source: SceneId,
  target: SceneId,
  label: String)

final case class SceneFlow(
  id: SceneId,
  name: String,
  steps: List[SceneStep])

final case class SceneGraph(
  name: String,
  nodes: List[SceneNode],
  edges: List[SceneEdge],
  flows: List[SceneFlow] = Nil,
  legend: Option[Legend] = None):
  def allNodes: List[SceneNode] = nodes.flatMap(_.flatten)

  // Every problem at once, so a caller fixes its graph in one round.
  def problems: List[GraphProblem] =
    val ids           = allNodes.map(_.id) ++ edges.map(_.id) ++ flows.map(_.id)
    val blank         = ids.filter(_.value.trim.isEmpty).distinct.map(_ => GraphProblem.BlankId)
    val duplicates    = ids.diff(ids.distinct).distinct.map(GraphProblem.DuplicateId(_))
    val known         = allNodes.map(_.id).toSet
    val dangling      = edges.flatMap: e =>
      List(e.source, e.target).filterNot(known).map(GraphProblem.UnknownEndpoint(e.id, _))
    val danglingSteps = flows.flatMap: flow =>
      flow.steps
        .flatMap(step => List(step.source, step.target))
        .filterNot(known)
        .distinct
        .map(GraphProblem.UnknownStepEnd(flow.id, _))
    blank ++ duplicates ++ dangling ++ danglingSteps

end SceneGraph

enum GraphProblem derives CanEqual:
  case BlankId
  case DuplicateId(id: SceneId)
  case UnknownEndpoint(edge: SceneId, endpoint: SceneId)
  case UnknownStepEnd(flow: SceneId, endpoint: SceneId)

enum SceneError derives CanEqual:
  case InvalidGraph(problems: ::[GraphProblem])
  case LayoutFailed(cause: Throwable)
