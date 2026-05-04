package fish.genius.uml.dsl.archimate

import fish.genius.uml.dsl.archimate.Edge.{EdgePrefix, EdgeSuffix}

/**
 * The fifteen Archimate 3 relationship types, each with the PlantUML
 * connector tokens that surround the [[Edge]] direction.
 */
enum RelationshipType(val prefix: EdgePrefix, val suffix: EdgeSuffix):
  case Composition extends RelationshipType("*-", "-")
  case Aggregation extends RelationshipType("o-", "-")
  case Assignment extends RelationshipType("@@-", "->>")
  case Serving extends RelationshipType("-", "->")
  case Flow extends RelationshipType(".", ".>>")
  case Specialization extends RelationshipType("-", "-|>")
  case Association extends RelationshipType("=", "=")
  case DirectedAssociation extends RelationshipType("=", "=>")
  case Realization extends RelationshipType("~", "~|>")
  case Triggering extends RelationshipType("-", "->>")
  case Access extends RelationshipType("~", "~")
  case ReadAccess extends RelationshipType("<-", "~")
  case WriteAccess extends RelationshipType("~", "->")
  case ReadWriteAccess extends RelationshipType("<~", "~>")
  case Influence extends RelationshipType(".", ".>")
end RelationshipType

object RelationshipType:

  /** All relationship types in canonical order. */
  val * : List[RelationshipType] = List(
    RelationshipType.Composition,
    RelationshipType.Aggregation,
    RelationshipType.Assignment,
    RelationshipType.Serving,
    RelationshipType.Flow,
    RelationshipType.Specialization,
    RelationshipType.Association,
    RelationshipType.DirectedAssociation,
    RelationshipType.Realization,
    RelationshipType.Triggering,
    RelationshipType.Access,
    RelationshipType.ReadAccess,
    RelationshipType.WriteAccess,
    RelationshipType.ReadWriteAccess,
    RelationshipType.Influence,
  )

end RelationshipType
