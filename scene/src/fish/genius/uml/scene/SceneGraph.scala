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
// one of the geometric glyphs a diagram hands out to tell several things apart; or set
// at the middle of a line (SceneEdge.sign), such as the style of an API. The label says
// what it stands for, in the legend. The diagram draws them as one family (Signs).
//
// After the twelve geometric glyphs: a public and a closed API, a health that is degraded
// or failing; what a gap does to an element (added, modified, removed; one retained has
// no sign), an element being phased out, an open risk and an open decision; and the
// styles of an API: REST, GraphQL, gRPC, events and batch.
enum Glyph derives CanEqual:

  case Circle, Square, TriangleUp, Diamond, Star, Hexagon, TriangleDown, Cross, Pentagon,
    Ring, Ellipse, Chevron, Globe, Padlock, Warning, Failure, GapAdd, GapModify, GapRemove,
    PhaseOut, Risk, Decision, Rest, GraphQl, Grpc, Event, Batch

// A badge is its logo when it has one (the glyph then stands in only where a logo
// cannot be drawn), else its glyph. text is a short word set right after it on the box
// (a phase-out's date, a risk's code or count); the legend names the badge by its label
// alone, since the text differs from box to box. A text longer than MAX_TEXT characters is
// cut and ends in an ellipsis, so one badge cannot widen its box out of its row.
final case class Badge(
  glyph: Glyph,
  label: String,
  logo: Option[Logo] = None,
  text: Option[String] = None):

  // the text as a diagram sets it: trimmed, none when blank, cut to MAX_TEXT characters
  def shownText: Option[String] =
    text.map(_.trim).filter(_.nonEmpty).map: t =>
      if t.codePointCount(0, t.length) <= Badge.MAX_TEXT then t
      else t.substring(0, t.offsetByCodePoints(0, Badge.MAX_TEXT - 1)).trim + "\u2026"

object Badge:
  val MAX_TEXT = 12

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

// How a line is drawn: whole, in dashes or in dots. The diagram module has its own Dash,
// the same three, which it maps this to (Diagram.dashOf): scene cannot depend on diagram,
// and diagram's stays its own so a drawing's prims need nothing from the graph.
enum Stroke derives CanEqual:
  case Solid, Dashed, Dotted

object Stroke:

  given JsonCodec[Stroke] = JsonCodec.string.transformOrFail(
    raw => Stroke.values.find(_.toString.equalsIgnoreCase(raw)).toRight(s"no stroke named $raw"),
    _.toString.toLowerCase,
  )

// A node's edge drawn its own way, to set the node apart without taking its layer's fill
// (a proposed element, say, or one weighted by how critical it is). weight multiplies the
// normal line and colour (hex) replaces its ink. A diagram takes a weight that is not a
// positive number as 1 and one above MAX_WEIGHT as MAX_WEIGHT, and leaves a colour that
// is not six hex digits out, as it does a fill.
final case class Outline(
  dash: Stroke = Stroke.Dashed,
  weight: Double = 1.0,
  colour: Option[String] = None):

  def effectiveWeight: Double =
    if weight.isNaN || weight <= 0 then 1.0 else math.min(weight, Outline.MAX_WEIGHT)

  def validColour: Option[String] =
    colour.map(_.stripPrefix("#")).filter(_.matches("[0-9A-Fa-f]{6}")).map(_.toUpperCase)

object Outline:
  val MAX_WEIGHT = 4.0
  given JsonCodec[Outline] = DeriveJsonCodec.gen

// A short code in a small pill at a box's top right, in a fill of its own: a verdict
// (I, T, M, E), a classification. A diagram sets it in capitals at a small size (small
// caps) with every character in a cell of one width (tabular figures), in a dark or a
// light ink, whichever keeps a contrast of at least 4.5:1 on the fill. A code of more
// than three characters, or a blank one, is left out, and so is a fill that is not six
// hex digits.
final case class CornerTag(code: String, fill: String):

  // counted in characters as drawn, after the capitals (ß becomes SS)
  def validCode: Option[String] =
    Option(code.trim.toUpperCase(java.util.Locale.ROOT)).filter(c =>
      c.nonEmpty && c.codePointCount(0, c.length) <= CornerTag.MAX_CODE
    )

  def validFill: Option[String] =
    Option(fill.stripPrefix("#")).filter(_.matches("[0-9A-Fa-f]{6}")).map(_.toUpperCase)

  // the tag as it is drawn: its code and its fill, when both are good
  def valid: Option[(String, String)] = validCode.zip(validFill)

object CornerTag:
  val MAX_CODE = 3
  given JsonCodec[CornerTag] = DeriveJsonCodec.gen

// What a diagram's marks stand for, drawn under it: its badges (entries), and sections
// of their own, each with a heading, such as the element types, the relationships and
// the logos a diagram holds.
final case class Legend(
  title: String,
  entries: List[Badge],
  sections: List[LegendSection] = Nil)

final case class LegendSection(title: String, items: List[LegendItem])

// One line of a legend: a badge (its glyph or logo), an element type (a small box in
// its layer's colour with its icon), a relationship (a short line with its ends), a
// logo, an outline (a small box with that edge: in a shape's layer with its icon
// when one is given, else on paper), a tag (its pill), a faded box (as a faded node is
// drawn, in a shape's layer or on paper), a weighted line (a relationship's line that
// many times as heavy), or a swatch (a small box in a fill, with a shape's icon when one
// is given, a step of a heat scale say); each with what it stands for.
enum LegendItem derives CanEqual:
  case Mark(badge: Badge)
  case Shape(shape: ShapeType, text: String)
  case Line(relationship: RelationshipType, text: String)
  case LogoMark(logo: fish.genius.uml.scene.Logo, text: String)

  case Outlined(
    outline: Outline,
    text: String,
    shape: Option[ShapeType] = None)

  case Tagged(tag: CornerTag, text: String)
  case Faded(shape: Option[ShapeType], text: String)

  case Weighted(
    relationship: RelationshipType,
    weight: Double,
    text: String)

  case Swatch(
    fill: String,
    text: String,
    shape: Option[ShapeType] = None)

  def label: String = this match
    case Mark(badge)       => badge.label
    case Shape(_, t)       => t
    case Line(_, t)        => t
    case LogoMark(_, t)    => t
    case Outlined(_, t, _) => t
    case Tagged(_, t)      => t
    case Faded(_, t)       => t
    case Weighted(_, _, t) => t
    case Swatch(_, t, _)   => t

end LegendItem

// caption and note are for a flat diagram, which writes them in the box under the
// label: the element's type (and technology), and a line or two about it. So are fill,
// a colour (hex) that sets the box apart from its layer, badges, the signs at its
// foot, and logo, the product's logo in a leaf's top left corner; the 3D scene leaves
// them all out. outline draws the box's edge (a container's frame) its own way, and
// keeps the fill; the scene's JSON carries it, which the 3D builders leave out for now.
// faded draws the box lighter, its edge, icon, signs and text paler but still legible (an
// element a gap analysis removes); a flat diagram only.
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
  logo: Option[Logo] = None,
  outline: Option[Outline] = None,
  tag: Option[CornerTag] = None,
  faded: Boolean = false):
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
  label: Option[String] = None,
  // a colour of its own (hex) for the line and its heads, to set it apart (proposed, say)
  colour: Option[String] = None,
  // a sign at the middle of the line (an API's style, say), before its label when it has one
  sign: Option[Glyph] = None,
  // the line that many times its normal width (an integration's volume, say), its heads
  // grown with it; a diagram takes one that is not a positive number as 1 and one above
  // 2.5 as 2.5; a flat diagram only
  weight: Double = 1.0)

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
