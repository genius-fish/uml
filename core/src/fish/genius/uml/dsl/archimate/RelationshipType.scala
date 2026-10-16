package fish.genius.uml.dsl.archimate

/**
 * The fifteen Archimate 3 relationship types. How each one is drawn (line, dash,
 * head and tail) is the diagram's `Notation`; the 3D builder knows them by
 * `SceneNames`.
 */
enum RelationshipType:
  case Composition
  case Aggregation
  case Assignment
  case Serving
  case Flow
  case Specialization
  case Association
  case DirectedAssociation
  case Realization
  case Triggering
  case Access
  case ReadAccess
  case WriteAccess
  case ReadWriteAccess
  case Influence
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
