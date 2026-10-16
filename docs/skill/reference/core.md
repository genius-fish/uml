# core — the ArchiMate vocabulary

Package `fish.genius.uml.dsl.archimate` (the `genius-uml-core` module). No
dependencies. The scene and the diagram match on these enums exhaustively.

## `ShapeType`

Every ArchiMate 3 element, as `ShapeType(group: ShapeGroup, name: String)`.

| Layer | Cases |
| --- | --- |
| Strategy | `StrategyResource`, `StrategyCapability`, `StrategyValueStream`, `StrategyCourseOfAction` |
| Business | `BusinessActor`, `BusinessRole`, `BusinessCollaboration`, `BusinessInterface`, `BusinessProcess`, `BusinessFunction`, `BusinessInteraction`, `BusinessEvent`, `BusinessService`, `BusinessObject`, `BusinessContract`, `BusinessRepresentation`, `BusinessProduct`, `BusinessLocation` |
| Application | `ApplicationComponent`, `ApplicationService`, `ApplicationCollaboration`, `ApplicationInterface`, `ApplicationFunction`, `ApplicationInteraction`, `ApplicationProcess`, `ApplicationEvent`, `ApplicationDataObject` |
| Technology | `TechnologyNode`, `TechnologyDevice`, `TechnologySystemSoftware`, `TechnologyCollaboration`, `TechnologyInterface`, `TechnologyPath`, `TechnologyCommunicationNetwork`, `TechnologyFunction`, `TechnologyProcess`, `TechnologyInteraction`, `TechnologyEvent`, `TechnologyService`, `TechnologyArtifact` |
| Physical | `PhysicalFacility`, `PhysicalEquipment`, `PhysicalDistributionNetwork`, `PhysicalMaterial` |
| Motivation | `MotivationStakeholder`, `MotivationDriver`, `MotivationAssessment`, `MotivationGoal`, `MotivationOutcome`, `MotivationPrinciple`, `MotivationRequirement`, `MotivationConstraint`, `MotivationMeaning`, `MotivationValue` |
| Implementation | `ImplementationWorkPackage`, `ImplementationDeliverable`, `ImplementationPlateau`, `ImplementationEvent`, `ImplementationGap` |

`ShapeType.application`, `strategy`, `business`, `technology`, `physical`,
`motivation`, `implementation` list each layer; `ShapeType.*` lists all of them.

## `ShapeGroup`

`Application`, `Strategy`, `Business`, `Technology`, `Physical`, `Motivation`,
`Implementation`. The scene stacks planes by layer; the diagram colours by layer
(`am-business`, `am-application`, …).

## `RelationshipType`

`Composition`, `Aggregation`, `Assignment`, `Serving`, `Flow`, `Specialization`,
`Association`, `DirectedAssociation`, `Realization`, `Triggering`, `Access`,
`ReadAccess`, `WriteAccess`, `ReadWriteAccess`, `Influence`; `RelationshipType.*`
lists them. The diagram's `Notation` decides line, dash, head and tail.

## `ShapeLabel` and `ArchimateConfiguration`

```scala
final case class ShapeLabel(
  name: String,
  componentType: Option[String] = None,
  componentTypeDetails: Option[String] = None,
  description: Option[String] = None,
)(using val config: ArchimateConfiguration)

final case class ArchimateConfiguration(
  technicalFontSize: Int = 12,
  descriptionFontSize: Int = 10,
  elkLayout: Boolean = false)
```

Plain values for what a box says, for code that builds labels before it builds a
`SceneNode` (its `label`, `caption`, `note`). A default configuration is always in
scope. `elkLayout` has no effect and stays for source compatibility.
