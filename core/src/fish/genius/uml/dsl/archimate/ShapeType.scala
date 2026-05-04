package fish.genius.uml.dsl.archimate

/**
 * Every Archimate 3 element. Each case knows its layer ([[ShapeGroup]]) and
 * its sprite-pack name; together they form the `<$archimate/<prefix>-<name>>`
 * sprite reference that PlantUML resolves into the correct icon.
 *
 * The mass-objects (`Object`, `Contract`, `DataObject`, `Representation`,
 * `Artifact`, `Material`) get a `----` separator drawn into their label by
 * [[ShapeType.decorate]] so they render with Archimate's traditional
 * top-edge styling.
 */
enum ShapeType(val group: ShapeGroup, val name: String):

  // Application
  case ApplicationComponent extends ShapeType(ShapeGroup.Application, "component")
  case ApplicationService extends ShapeType(ShapeGroup.Application, "service")
  case ApplicationCollaboration extends ShapeType(ShapeGroup.Application, "collaboration")
  case ApplicationInterface extends ShapeType(ShapeGroup.Application, "interface")
  case ApplicationFunction extends ShapeType(ShapeGroup.Application, "function")
  case ApplicationInteraction extends ShapeType(ShapeGroup.Application, "interaction")
  case ApplicationProcess extends ShapeType(ShapeGroup.Application, "process")
  case ApplicationEvent extends ShapeType(ShapeGroup.Application, "event")
  case ApplicationDataObject extends ShapeType(ShapeGroup.Application, "data-object")

  // Strategy
  case StrategyResource extends ShapeType(ShapeGroup.Strategy, "resource")
  case StrategyCapability extends ShapeType(ShapeGroup.Strategy, "capability")
  case StrategyValueStream extends ShapeType(ShapeGroup.Strategy, "valuestream")
  case StrategyCourseOfAction extends ShapeType(ShapeGroup.Strategy, "course-of-action")

  // Business
  case BusinessActor extends ShapeType(ShapeGroup.Business, "actor")
  case BusinessRole extends ShapeType(ShapeGroup.Business, "role")
  case BusinessCollaboration extends ShapeType(ShapeGroup.Business, "collaboration")
  case BusinessInterface extends ShapeType(ShapeGroup.Business, "interface")
  case BusinessProcess extends ShapeType(ShapeGroup.Business, "process")
  case BusinessFunction extends ShapeType(ShapeGroup.Business, "function")
  case BusinessInteraction extends ShapeType(ShapeGroup.Business, "interaction")
  case BusinessEvent extends ShapeType(ShapeGroup.Business, "event")
  case BusinessService extends ShapeType(ShapeGroup.Business, "service")
  case BusinessObject extends ShapeType(ShapeGroup.Business, "object")
  case BusinessContract extends ShapeType(ShapeGroup.Business, "contract")
  case BusinessRepresentation extends ShapeType(ShapeGroup.Business, "representation")
  case BusinessProduct extends ShapeType(ShapeGroup.Business, "product")
  case BusinessLocation extends ShapeType(ShapeGroup.Business, "location")

  // Technology
  case TechnologyNode extends ShapeType(ShapeGroup.Technology, "node")
  case TechnologyDevice extends ShapeType(ShapeGroup.Technology, "device")
  case TechnologySystemSoftware extends ShapeType(ShapeGroup.Technology, "system-software")
  case TechnologyCollaboration extends ShapeType(ShapeGroup.Technology, "collaboration")
  case TechnologyInterface extends ShapeType(ShapeGroup.Technology, "interface")
  case TechnologyPath extends ShapeType(ShapeGroup.Technology, "path")

  case TechnologyCommunicationNetwork
    extends ShapeType(ShapeGroup.Technology, "communication-network")

  case TechnologyFunction extends ShapeType(ShapeGroup.Technology, "function")
  case TechnologyProcess extends ShapeType(ShapeGroup.Technology, "process")
  case TechnologyInteraction extends ShapeType(ShapeGroup.Technology, "interaction")
  case TechnologyEvent extends ShapeType(ShapeGroup.Technology, "event")
  case TechnologyService extends ShapeType(ShapeGroup.Technology, "service")
  case TechnologyArtifact extends ShapeType(ShapeGroup.Technology, "artifact")

  // Physical
  case PhysicalFacility extends ShapeType(ShapeGroup.Physical, "facility")
  case PhysicalEquipment extends ShapeType(ShapeGroup.Physical, "equipment")
  case PhysicalDistributionNetwork extends ShapeType(ShapeGroup.Physical, "distribution-network")
  case PhysicalMaterial extends ShapeType(ShapeGroup.Physical, "material")

  // Motivation
  case MotivationStakeholder extends ShapeType(ShapeGroup.Motivation, "stakeholder")
  case MotivationDriver extends ShapeType(ShapeGroup.Motivation, "driver")
  case MotivationAssessment extends ShapeType(ShapeGroup.Motivation, "assessment")
  case MotivationGoal extends ShapeType(ShapeGroup.Motivation, "goal")
  case MotivationOutcome extends ShapeType(ShapeGroup.Motivation, "outcome")
  case MotivationPrinciple extends ShapeType(ShapeGroup.Motivation, "principle")
  case MotivationRequirement extends ShapeType(ShapeGroup.Motivation, "requirement")
  case MotivationConstraint extends ShapeType(ShapeGroup.Motivation, "constraint")
  case MotivationMeaning extends ShapeType(ShapeGroup.Motivation, "meaning")
  case MotivationValue extends ShapeType(ShapeGroup.Motivation, "value")

  // Implementation
  case ImplementationWorkPackage extends ShapeType(ShapeGroup.Implementation, "workpackage")
  case ImplementationDeliverable extends ShapeType(ShapeGroup.Implementation, "deliverable")
  case ImplementationPlateau extends ShapeType(ShapeGroup.Implementation, "plateau")
  case ImplementationEvent extends ShapeType(ShapeGroup.Implementation, "event")
  case ImplementationGap extends ShapeType(ShapeGroup.Implementation, "gap")

  /** The PlantUML sprite reference used inside the rectangle stereotype. */
  def icon: String = s"$$archimate/${group.prefix}-$name"

  /**
   * Mass-objects in Archimate render with a horizontal separator at the top
   * of the label box; this prepends the `----` marker that PlantUML expects
   * to draw it.
   */
  def decorate(
    label: ShapeLabel
  )(
    using ArchimateConfiguration
  ): ShapeLabel = this match
    case ApplicationDataObject | BusinessObject | BusinessContract |
      BusinessRepresentation | TechnologyArtifact | PhysicalMaterial =>
      label.copy(name = "----\\n " + label.name)
    case _ => label

end ShapeType

object ShapeType:

  val application: List[ShapeType] = List(
    ApplicationComponent,
    ApplicationService,
    ApplicationCollaboration,
    ApplicationInterface,
    ApplicationFunction,
    ApplicationInteraction,
    ApplicationProcess,
    ApplicationDataObject,
    ApplicationEvent,
  )

  val strategy: List[ShapeType] = List(
    StrategyCapability,
    StrategyCourseOfAction,
    StrategyResource,
    StrategyValueStream,
  )

  val business: List[ShapeType] = List(
    BusinessObject,
    BusinessService,
    BusinessProduct,
    BusinessActor,
    BusinessEvent,
    BusinessContract,
    BusinessFunction,
    BusinessInteraction,
    BusinessInterface,
    BusinessLocation,
    BusinessProcess,
    BusinessRepresentation,
    BusinessRole,
    BusinessCollaboration,
  )

  val technology: List[ShapeType] = List(
    TechnologyArtifact,
    TechnologyPath,
    TechnologyProcess,
    TechnologyInterface,
    TechnologyNode,
    TechnologyCollaboration,
    TechnologyCommunicationNetwork,
    TechnologyDevice,
    TechnologyEvent,
    TechnologyFunction,
    TechnologyInteraction,
    TechnologyService,
    TechnologySystemSoftware,
  )

  val physical: List[ShapeType] = List(
    PhysicalFacility,
    PhysicalEquipment,
    PhysicalMaterial,
    PhysicalDistributionNetwork,
  )

  val motivation: List[ShapeType] = List(
    MotivationMeaning,
    MotivationPrinciple,
    MotivationValue,
    MotivationGoal,
    MotivationAssessment,
    MotivationConstraint,
    MotivationDriver,
    MotivationOutcome,
    MotivationRequirement,
    MotivationStakeholder,
  )

  val implementation: List[ShapeType] = List(
    ImplementationDeliverable,
    ImplementationWorkPackage,
    ImplementationGap,
    ImplementationEvent,
    ImplementationPlateau,
  )

  val * : List[ShapeType] =
    application ::: strategy ::: business ::: technology ::: physical ::: motivation :::
    implementation

end ShapeType
