package fish.genius.uml.model.archimate

import fish.genius.uml.model.archimate.Edge.{EdgePrefix, EdgeSuffix}

object RelationshipType {
  val * : List[RelationshipType] =
    List(
      Composition,
      Aggregation,
      Serving,
      Flow,
      Specialization,
      Association,
      DirectedAssociation,
      Realization,
      Triggering,
      Access,
      ReadAccess,
      WriteAccess,
      ReadWriteAccess,
      Influence
    )
}
sealed trait RelationshipType {
  def prefix: EdgePrefix

  def suffix: EdgeSuffix
}

case object Composition extends RelationshipType {
  override val prefix = "*-"
  override val suffix = "-"
}

case object Aggregation extends RelationshipType {
  override val prefix = "o-"
  override val suffix = "-"
}

case object Assignment extends RelationshipType {
  override val prefix = "@@-"
  override val suffix = "->>"
}

case object Serving extends RelationshipType {
  override val prefix = "-"
  override val suffix = "->"
}

case object Flow extends RelationshipType {
  override val prefix = "."
  override val suffix = ".>>"
}

case object Specialization extends RelationshipType {
  override val prefix = "-"
  override val suffix = "-|>"
}

case object Association extends RelationshipType {
  override val prefix = "="
  override val suffix = "="
}

case object DirectedAssociation extends RelationshipType {
  override val prefix = "="
  override val suffix = "=>"
}

case object Realization extends RelationshipType {
  override val prefix = "~"
  override val suffix = "~|>"
}

case object Triggering extends RelationshipType {
  override val prefix = "-"
  override val suffix = "->>"
}

case object Access extends RelationshipType {
  override val prefix = "~"
  override val suffix = "~"
}

case object ReadAccess extends RelationshipType {
  override val prefix = "<-"
  override val suffix = "~"
}

case object WriteAccess extends RelationshipType {
  override val prefix = "~"
  override val suffix = "->"
}

case object ReadWriteAccess extends RelationshipType {
  override val prefix = "<~"
  override val suffix = "~>"
}

case object Influence extends RelationshipType {
  override val prefix = "."
  override val suffix = ".>"
}
