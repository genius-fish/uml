package fish.genius.uml.dsl.sequence

import fish.genius.uml.ast.PUmlNode
import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.markers.*

/**
 * The PlantUML sequence-diagram DSL: actors, participants, boxes, message
 * steps, and the alt/loop/opt/par/break/critical/group control structures.
 *
 * `sequenceDiagram { ... }` injects an [[InSequence]] capability into the
 * body so that participant- and step-level helpers refuse to compile outside
 * the block. Named `sequenceDiagram` to avoid a name clash with the
 * surrounding `fish.genius.uml.dsl.sequence` package.
 */
def sequenceDiagram(
  body: (PUmlCtx, InSequence) ?=> Unit
)(
  using PUmlCtx,
  InUml,
): Unit =
  given InSequence = markers.InSequence
  body

/** Emit `autonumber` so PlantUML numbers each subsequent message step. */
def autonumber(
)(
  using PUmlCtx,
  InSequence,
): Unit = statement("autonumber")

/** Switch on the Teoz rendering engine, which lays out groups more cleanly. */
def teozRenderingEngine(
)(
  using PUmlCtx,
  InSequence,
): Unit = statement("!pragma teoz true")

private def escape(input: String): String =
  input.replace("\"", "").replace("\n", "\\n")

/**
 * Emit a participant declaration of the given PlantUML type and return its
 * alias. The optional colour is appended at the end of the line, exactly as
 * PlantUML expects (`actor "X" as a #ff0000`).
 */
def participantOf(
  participantType: String,
  title: String,
  color: Option[String] = None,
)(
  using PUmlCtx,
  InSequence,
): Alias =
  expression(alias =>
    s"$participantType \"${escape(title)}\" as $alias ${color.getOrElse("")}".stripTrailing
  )

def actor(
  title: String,
  color: Option[String] = None,
)(
  using PUmlCtx,
  InSequence,
): Alias =
  participantOf("actor", title, color)

def boundary(
  title: String,
  color: Option[String] = None,
)(
  using PUmlCtx,
  InSequence,
): Alias =
  participantOf("boundary", title, color)

def control(
  title: String,
  color: Option[String] = None,
)(
  using PUmlCtx,
  InSequence,
): Alias =
  participantOf("control", title, color)

def entity(
  title: String,
  color: Option[String] = None,
)(
  using PUmlCtx,
  InSequence,
): Alias =
  participantOf("entity", title, color)

def database(
  title: String,
  color: Option[String] = None,
)(
  using PUmlCtx,
  InSequence,
): Alias =
  participantOf("database", title, color)

def collections(
  title: String,
  color: Option[String] = None,
)(
  using PUmlCtx,
  InSequence,
): Alias =
  participantOf("collections", title, color)

def queue(
  title: String,
  color: Option[String] = None,
)(
  using PUmlCtx,
  InSequence,
): Alias =
  participantOf("queue", title, color)

def participant(
  title: String,
  color: Option[String] = None,
)(
  using PUmlCtx,
  InSequence,
): Alias =
  participantOf("participant", title, color)

/**
 * Group multiple participants under a coloured `box` header. Body code can
 * declare further participants which become members of the box.
 */
def box(
  title: String,
  color: Option[String] = None,
)(
  body: (PUmlCtx, InSequence, InBox) ?=> Unit
)(
  using PUmlCtx,
  InSequence,
): Unit =
  emit(
    PUmlNode.Block(
      s"box \"${escape(title)}\" ${color.getOrElse("")}".stripTrailing,
      childBlock:
        given InBox = markers.InBox
        given InSequence = summon[InSequence]
        body,
    )
  )

/**
 * Open one of the PlantUML sequence-diagram control structures
 * (`alt`/`opt`/`loop`/`par`/`break`/`critical`/`group`) and run `body` until
 * a matching `endGroup`/`elseGroup` closes it.
 */
private def groupOf(
  kind: String,
  title: Option[String],
)(
  using PUmlCtx,
  InSequence,
): Unit =
  statement(s"$kind ${title.getOrElse("")}".stripTrailing)

def altGroup(
  title: Option[String] = None
)(
  using PUmlCtx,
  InSequence,
): Unit =
  groupOf("alt", title)

def elseGroup(
  title: Option[String] = None
)(
  using PUmlCtx,
  InSequence,
): Unit =
  groupOf("else", title)

def loopGroup(
  title: Option[String] = None
)(
  using PUmlCtx,
  InSequence,
): Unit =
  groupOf("loop", title)

def optGroup(
  title: Option[String] = None
)(
  using PUmlCtx,
  InSequence,
): Unit =
  groupOf("opt", title)

def parGroup(
  title: Option[String] = None
)(
  using PUmlCtx,
  InSequence,
): Unit =
  groupOf("par", title)

def breakGroup(
  title: Option[String] = None
)(
  using PUmlCtx,
  InSequence,
): Unit =
  groupOf("break", title)

def criticalGroup(
  title: Option[String] = None
)(
  using PUmlCtx,
  InSequence,
): Unit =
  groupOf("critical", title)

def group(
  title: Option[String] = None
)(
  using PUmlCtx,
  InSequence,
): Unit =
  groupOf("group", title)

def endGroup(
)(
  using PUmlCtx,
  InSequence,
): Unit = statement("end")

/** Emit a single message step `from -> to: title`. */
def step(
  title: String
)(
  from: Alias
)(
  to: Alias
)(
  using PUmlCtx,
  InSequence,
): Unit =
  statement(s"${from.value}->${to.value}: ${escape(title)}")
