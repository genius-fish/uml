package fish.genius.uml.model.archimate

object ShapeType {
  val application: List[ShapeType] = List(
    ApplicationComponent,
    ApplicationService,
    ApplicationCollaboration,
    ApplicationInterface,
    ApplicationFunction,
    ApplicationInteraction,
    ApplicationProcess,
    ApplicationDataObject,
    ApplicationEvent
  )
  val strategy: List[ShapeType] = List(
    StrategyCapability,
    StrategyCourseOfAction,
    StrategyResource,
    StrategyValueStream
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
    BusinessCollaboration
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
    TechnologySystemSoftware
  )
  val physical: List[ShapeType] = List(
    PhysicalFacility,
    PhysicalEquipment,
    PhysicalMaterial,
    PhysicalDistributionNetwork
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
    MotivationStakeholder
  )
  val implementation: List[ShapeType] = List(
    ImplementationDeliverable,
    ImplementationWorkPackage,
    ImplementationGap,
    ImplementationEvent,
    ImplementationPlateau
  )

  val * : List[ShapeType] =
    application ::: strategy ::: business ::: technology ::: physical ::: motivation ::: implementation
}
sealed trait ShapeType {
  def group: ShapeGroup
  def name: String

  def decorate(label: ShapeLabel)(implicit
      archimate: ArchimateConfiguration
  ): ShapeLabel = label
}

sealed trait ApplicationShape extends ShapeType {
  override val group: ShapeGroup = Application
}
case object ApplicationComponent extends ApplicationShape {
  override val name: String = "component"
}

case object ApplicationService extends ApplicationShape {
  override val name: String = "service"
}

case object ApplicationCollaboration extends ApplicationShape {
  override val name: String = "collaboration"
}

case object ApplicationInterface extends ApplicationShape {
  override val name: String = "interface"
}

case object ApplicationFunction extends ApplicationShape {
  override val name: String = "function"
}

case object ApplicationInteraction extends ApplicationShape {
  override val name: String = "interaction"
}
case object ApplicationProcess extends ApplicationShape {
  override val name: String = "process"
}
case object ApplicationEvent extends ApplicationShape {
  override val name: String = "event"
}

case object ApplicationDataObject extends ApplicationShape {
  override val name: String = "data-object"

  override def decorate(label: ShapeLabel)(implicit
      archimateConfiguration: ArchimateConfiguration
  ): ShapeLabel = label.copy(name = "----\\n " + label.name)
}

sealed trait StrategyShape extends ShapeType {
  override val group: ShapeGroup = Strategy
}

case object StrategyResource extends StrategyShape {
  override val name: String = "resource"
}

case object StrategyCapability extends StrategyShape {
  override val name: String = "capability"
}
case object StrategyValueStream extends StrategyShape {
  override val name: String = "value-stream"
}
case object StrategyCourseOfAction extends StrategyShape {
  override val name: String = "course-of-action"
}

sealed trait BusinessShape extends ShapeType {
  override val group: ShapeGroup = Business
}

case object BusinessActor extends BusinessShape {
  override val name: String = "actor"
}

case object BusinessRole extends BusinessShape {
  override val name: String = "role"
}

case object BusinessCollaboration extends BusinessShape {
  override val name: String = "collaboration"
}

case object BusinessInterface extends BusinessShape {
  override val name: String = "interface"
}
case object BusinessProcess extends BusinessShape {
  override val name: String = "process"
}
case object BusinessFunction extends BusinessShape {
  override val name: String = "function"
}

case object BusinessInteraction extends BusinessShape {
  override val name: String = "interaction"
}
case object BusinessEvent extends BusinessShape {
  override val name: String = "event"
}
case object BusinessService extends BusinessShape {
  override val name: String = "service"
}

case object BusinessObject extends BusinessShape {
  override val name: String = "object"

  override def decorate(label: ShapeLabel)(implicit
      archimate: ArchimateConfiguration
  ): ShapeLabel = label.copy(name = "----\\n " + label.name)
}

case object BusinessContract extends BusinessShape {
  override val name: String = "contract"

  override def decorate(label: ShapeLabel)(implicit
      archimate: ArchimateConfiguration
  ): ShapeLabel = label.copy(name = "----\\n " + label.name)
}
case object BusinessRepresentation extends BusinessShape {
  override val name: String = "representation"

  override def decorate(label: ShapeLabel)(implicit
      archimate: ArchimateConfiguration
  ): ShapeLabel = label.copy(name = "----\\n " + label.name)
}

case object BusinessProduct extends BusinessShape {
  override val name: String = "product"
}

case object BusinessLocation extends BusinessShape {
  override val name: String = "location"
}

sealed trait TechnologyShape extends ShapeType {
  override val group: ShapeGroup = Technology
}

case object TechnologyNode extends TechnologyShape {
  override val name: String = "node"
}

case object TechnologyDevice extends TechnologyShape {
  override val name: String = "device"
}

case object TechnologySystemSoftware extends TechnologyShape {
  override val name: String = "system-software"
}

case object TechnologyCollaboration extends TechnologyShape {
  override val name: String = "collaboration"
}

case object TechnologyInterface extends TechnologyShape {
  override val name: String = "interface"
}
case object TechnologyPath extends TechnologyShape {
  override val name: String = "path"
}

case object TechnologyCommunicationNetwork extends TechnologyShape {
  override val name: String = "communication-network"
}
case object TechnologyFunction extends TechnologyShape {
  override val name: String = "function"
}
case object TechnologyProcess extends TechnologyShape {
  override val name: String = "process"
}

case object TechnologyInteraction extends TechnologyShape {
  override val name: String = "interaction"
}
case object TechnologyEvent extends TechnologyShape {
  override val name: String = "event"
}
case object TechnologyService extends TechnologyShape {
  override val name: String = "service"
}
case object TechnologyArtifact extends TechnologyShape {
  override val name: String = "artifact"

  override def decorate(label: ShapeLabel)(implicit
      archimate: ArchimateConfiguration
  ): ShapeLabel = label.copy(name = "----\\n " + label.name)
}

sealed trait PhysicalShape extends ShapeType {
  override val group: ShapeGroup = Physical
}

case object PhysicalFacility extends PhysicalShape {
  override val name: String = "facility"
}

case object PhysicalEquipment extends PhysicalShape {
  override val name: String = "equipment"
}
case object PhysicalDistributionNetwork extends PhysicalShape {
  override val name: String = "distribution-network"
}
case object PhysicalMaterial extends PhysicalShape {
  override val name: String = "material"

  override def decorate(label: ShapeLabel)(implicit
      archimate: ArchimateConfiguration
  ): ShapeLabel = label.copy(name = "----\\n " + label.name)
}

sealed trait MotivationShape extends ShapeType {
  override val group: ShapeGroup = Motivation
}

case object MotivationStakeholder extends MotivationShape {
  override val name: String = "stakeholder"
}

case object MotivationDriver extends MotivationShape {
  override val name: String = "driver"
}
case object MotivationAssessment extends MotivationShape {
  override val name: String = "assessment"
}

case object MotivationGoal extends MotivationShape {
  override val name: String = "assessment"
}
case object MotivationOutcome extends MotivationShape {
  override val name: String = "assessment"
}
case object MotivationPrinciple extends MotivationShape {
  override val name: String = "principle"
}

case object MotivationRequirement extends MotivationShape {
  override val name: String = "requirement"
}
case object MotivationConstraint extends MotivationShape {
  override val name: String = "constraint"
}
case object MotivationMeaning extends MotivationShape {
  override val name: String = "meaning"
}

case object MotivationValue extends MotivationShape {
  override val name: String = "value"
}

sealed trait ImplementationShape extends ShapeType {
  override val group: ShapeGroup = Implementation
}

case object ImplementationWorkPackage extends ImplementationShape {
  override val name: String = "workpackage"
}

case object ImplementationDeliverable extends ImplementationShape {
  override val name: String = "deliverable"
}
case object ImplementationPlateau extends ImplementationShape {
  override val name: String = "plateau"
}
case object ImplementationEvent extends ImplementationShape {
  override val name: String = "event"
}
case object ImplementationGap extends ImplementationShape {
  override val name: String = "gap"
}
