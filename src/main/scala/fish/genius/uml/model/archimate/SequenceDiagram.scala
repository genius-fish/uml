package fish.genius.uml.model.archimate

import fish.genius.uml.model.{
  Alias,
  CanBuildSpecification,
  SpecificationBuilder
}

class SequenceDiagram()(implicit
    specificationBuilder: SpecificationBuilder
) extends CanBuildSpecification {
  def autonumber(): Unit = statement("autonumber")

  type StepTitle = String
  type ParticipantTitle = String
  type ParticipantType = String
  type ParticipantColor = Option[String]
  type Participant = ParticipantTitle => Alias
  type ColoredParticipant = ParticipantColor => Participant

  private val _participant
      : ParticipantType => ParticipantColor => ParticipantTitle => Alias =
    participantType =>
      participantColor =>
        participantTitle =>
          expression(alias =>
            s"$participantType \"${escape(participantTitle)}\" as $alias ${participantColor.getOrElse("")}"
          )

  val coloredActor: ColoredParticipant = _participant("actor")
  val actor: Participant = coloredActor(None)
  val coloredBoundary: ColoredParticipant = _participant("boundary")
  val boundary: Participant = coloredBoundary(None)
  val coloredControl: ColoredParticipant = _participant("control")
  val control: Participant = coloredControl(None)
  val coloredEntity: ColoredParticipant = _participant("entity")
  val entity: Participant = coloredEntity(None)
  val coloredDatabase: ColoredParticipant = _participant("database")
  val database: Participant = coloredDatabase(None)
  val coloredCollections: ColoredParticipant = _participant("collections")
  val collections: Participant = coloredCollections(None)
  val coloredQueue: ColoredParticipant = _participant("queue")
  val queue: Participant = coloredQueue(None)
  val coloredParticipant: ColoredParticipant = _participant("participant")
  val participant: Participant = coloredParticipant(None)

  type GroupType = String
  type GroupTitle = Option[String]
  type Group = GroupTitle => Unit

  val _group: GroupType => GroupTitle => Unit = groupType =>
    groupTitle => statement(s"$groupType ${groupTitle.getOrElse("")}")
  val altGroup: Group = _group("alt")
  val elseGroup: Group = _group("else")
  val loopGroup: Group = _group("loop")
  val optGroup: Group = _group("opt")
  val parGroup: Group = _group("par")
  val breakGroup: Group = _group("break")
  val criticalGroup: Group = _group("critical")
  val group: Group = _group("group")
  def endGroup(): Unit = statement("end")

  def box(title: String, color: Option[String] = None): Unit = statement(
    s"box \"${escape(title)}\" ${color.getOrElse("")}"
  )
  def endBox(): Unit = statement("end box")

  val step: StepTitle => Alias => Alias => Unit = stepTitle =>
    from => to => statement(s"$from->$to: ${escape(stepTitle)}")

  private def escape(input: String): String =
    input.replaceAll("\"", "").replaceAll("\n", "\\n")
}

object SequenceDiagram {
  def sequenceDiagram(
      body: SequenceDiagram => Any
  )(implicit specificationBuilder: SpecificationBuilder): SequenceDiagram = {
    val diagram = new SequenceDiagram()
    body.apply(diagram)
    diagram
  }
}
