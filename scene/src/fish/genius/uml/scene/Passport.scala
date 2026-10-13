package fish.genius.uml.scene

import java.time.LocalDate

import zio.json.*

// A passport: what an element is, told on a glass pane beside its plinth. It is
// generic on purpose. The scene knows nothing of where the facts come from; a caller
// (an architecture model, a CMDB) composes one per node from its own data, already in
// the reader's language, and the builder draws whatever sections it holds, in order.

final case class Passport(
  eyebrow: String,
  title: String,
  subtitle: Option[String] = None,
  sections: List[PassportSection] = Nil,
  footer: Option[String] = None)

enum PassportSection derives CanEqual:
  // running text, wrapped to the pane's width
  case Text(body: String)
  // label and value rows, under an optional heading
  case Facts(label: Option[String], rows: List[Fact])
  // one rating as a chip, with a note under it: a criticality, a verdict
  case Rated(label: String, chip: Chip)

  // a rating in letters, such as confidentiality, integrity and availability
  case Rating(
    label: String,
    marks: List[Mark],
    note: Option[String] = None)

  // dated milestones on a bar, each colouring the stretch up to the next
  case Timeline(
    label: String,
    milestones: List[Milestone],
    asOf: LocalDate,
    summary: Option[String] = None)

end PassportSection

final case class Fact(label: String, value: String)

final case class Chip(
  text: String,
  tone: Tone = Tone.Neutral,
  note: Option[String] = None)

final case class Mark(
  letter: String,
  level: String,
  tone: Tone)

final case class Milestone(
  label: String,
  date: LocalDate,
  tone: Tone)

// What a chip, a mark or a stretch of the timeline means, not its colour: the builder
// picks the colour. Layer is the element's own layer colour.
enum Tone derives CanEqual:
  case Neutral, Layer, Good, Info, Caution, Warning, Critical

// How the panes stand, for the whole scene. Auto stands them upright while the camera
// is low and lays them flat on the floor once it looks down steeply; the layout keeps
// floor free for whichever it becomes.
final case class PaneStyle(
  orientation: PaneOrientation = PaneOrientation.Auto,
  side: PaneSide = PaneSide.Right,
  faceCamera: Boolean = false,
  anchored: Boolean = true,
  tint: PaneTint = PaneTint.Clear)

enum PaneOrientation derives CanEqual:
  case Auto, Upright, Lectern, Flat, Floating

// The side a pane prefers. A pane gives way to a connector: when one leaves its plinth
// on that side, the pane moves to the opposite side, then behind, then in front.
enum PaneSide derives CanEqual:
  case Right, Left, Behind, Front

enum PaneTint derives CanEqual:
  case Clear, Layer

// The pane of one element in a laid-out scene: the side it stands on, its size in scene
// units, and what it says.
final case class ScenePane(
  side: PaneSide,
  w: Double,
  h: Double,
  passport: Passport)

// lower-case names in JSON, as the ports have
private def lowerCase[A](values: Array[A]): JsonCodec[A] =
  JsonCodec.string.transformOrFail(
    raw => values.find(_.toString.equalsIgnoreCase(raw)).toRight(s"unknown value $raw"),
    value => value.toString.head.toLower.toString + value.toString.tail,
  )

object Tone:
  given JsonCodec[Tone] = lowerCase(Tone.values)

object PaneOrientation:
  given JsonCodec[PaneOrientation] = lowerCase(PaneOrientation.values)

object PaneSide:
  given JsonCodec[PaneSide] = lowerCase(PaneSide.values)

object PaneTint:
  given JsonCodec[PaneTint] = lowerCase(PaneTint.values)

object Fact:
  given JsonCodec[Fact] = DeriveJsonCodec.gen

object Chip:
  given JsonCodec[Chip] = DeriveJsonCodec.gen

object Mark:
  given JsonCodec[Mark] = DeriveJsonCodec.gen

object Milestone:
  given JsonCodec[Milestone] = DeriveJsonCodec.gen

object Passport:
  given JsonCodec[Passport] = DeriveJsonCodec.gen

object PaneStyle:
  given JsonCodec[PaneStyle] = DeriveJsonCodec.gen

object ScenePane:
  given JsonCodec[ScenePane] = DeriveJsonCodec.gen

// The sections by hand, as {"kind": "text", "body": …}: zio-json's own discriminator
// would name them by their Scala case, and the builder dispatches on lower-case kinds.
object PassportSection:
  import zio.json.ast.Json

  final private case class TextS(body: String)
  final private case class FactsS(label: Option[String], rows: List[Fact])
  final private case class RatedS(label: String, chip: Chip)

  final private case class RatingS(
    label: String,
    marks: List[Mark],
    note: Option[String])

  final private case class TimelineS(
    label: String,
    milestones: List[Milestone],
    asOf: LocalDate,
    summary: Option[String])

  private given JsonCodec[TextS] = DeriveJsonCodec.gen
  private given JsonCodec[FactsS] = DeriveJsonCodec.gen
  private given JsonCodec[RatedS] = DeriveJsonCodec.gen
  private given JsonCodec[RatingS] = DeriveJsonCodec.gen
  private given JsonCodec[TimelineS] = DeriveJsonCodec.gen

  given JsonCodec[PassportSection] = JsonCodec[Json].transformOrFail(decode, encode)

  private def tag[A : JsonEncoder](kind: String, value: A): Json =
    value.toJsonAST match
      case Right(Json.Obj(fields)) => Json.Obj(("kind" -> Json.Str(kind)) +: fields)
      case _                       => Json.Obj("kind" -> Json.Str(kind))

  private def encode(section: PassportSection): Json = section match
    case Text(body)                   => tag("text", TextS(body))
    case Facts(label, rows)           => tag("facts", FactsS(label, rows))
    case Rated(label, chip)           => tag("rated", RatedS(label, chip))
    case Rating(label, marks, note)   => tag("rating", RatingS(label, marks, note))
    case Timeline(label, ms, at, sum) => tag("timeline", TimelineS(label, ms, at, sum))

  private def decode(json: Json): Either[String, PassportSection] =
    val kind = json match
      case Json.Obj(fields) => fields.collectFirst { case ("kind", Json.Str(k)) => k }
      case _                => None
    kind match
      case Some("text")     => json.as[TextS].map(s => Text(s.body))
      case Some("facts")    => json.as[FactsS].map(s => Facts(s.label, s.rows))
      case Some("rated")    => json.as[RatedS].map(s => Rated(s.label, s.chip))
      case Some("rating")   => json.as[RatingS].map(s => Rating(s.label, s.marks, s.note))
      case Some("timeline") =>
        json.as[TimelineS].map(s => Timeline(s.label, s.milestones, s.asOf, s.summary))
      case Some(other)      => Left(s"unknown passport section $other")
      case None             => Left("a passport section without a kind")

end PassportSection
