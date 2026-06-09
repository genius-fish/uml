# archimate — the ArchiMate DSL

Package `fish.genius.uml.dsl.archimate`. Open with `archimateDiagram { }` inside
`uml { }`; everything here requires `InArchimate` and a `given
ArchimateConfiguration`.

```scala
import fish.genius.uml.dsl.archimate.*
import fish.genius.uml.dsl.archimate.ShapeType.*
given ArchimateConfiguration = ArchimateConfiguration()
```

## Entry point & config

| Signature | Summary |
| --- | --- |
| `archimateDiagram(body: (PUmlCtx, InArchimate, ArchimateConfiguration) ?=> Unit)(using PUmlCtx, InUml, ArchimateConfiguration)` | opens the DSL and emits the ArchiMate skinparam preamble |
| `archimatePreamble(using …)` | emit just the skinparam header (rounded corners, alignment) |
| `ArchimateConfiguration(technicalFontSize=12, descriptionFontSize=10)` | caption sizing; `given default` is provided |

## Shapes

```scala
def shape(
  shapeType: ShapeType,
  label: ShapeLabel,
  color: ShapeColor = ShapeColor(),
  stereoType: Option[ShapeStereoType] = None,
)(using PUmlCtx, InArchimate, ArchimateConfiguration): Alias
```
Emits one `rectangle … <<archimate/…>> as <alias>` line and **returns the alias**.

Build the caption with `label(...)`:

```scala
def label(
  name: String,
  componentType: Option[String] = None,
  componentTypeDetails: Option[String] = None,
  description: Option[String] = None,
  tags: List[SvgIcon] = Nil,
)(using ArchimateConfiguration): ShapeLabel
```

`color(hex: String): ShapeColor` builds a fill colour (with or without leading
`#`). `ShapeColor(hex: Option[String] = None)` directly.

## Relationships

```scala
def relationship(
  ofType: RelationshipType,
  edge: Edge = Edge.NoDirection,
  label: Option[String] = None,
)(using PUmlCtx, InArchimate): Alias => Alias => Unit
```
**Curried** — apply source then target: `relationship(Serving, Right, Some("uses"))(store)(customer)`.

- `RelationshipType` (15): `Composition Aggregation Assignment Serving Flow
  Specialization Association DirectedAssociation Realization Triggering Access
  ReadAccess WriteAccess ReadWriteAccess Influence`. `RelationshipType.*` lists all.
- `Edge` (direction): `NoDirection Up Down Left Right`.

## Boundaries (nesting)

```scala
def boundary(
  label: String,
  boundaryType: Option[String] = None,
  shapeType: Option[ShapeType] = None,
  color: ShapeColor = ShapeColor(),
  stereoType: Option[ShapeStereoType] = None,
)(body: (PUmlCtx, InArchimate, InBoundary, ArchimateConfiguration) ?=> Unit)
 (using PUmlCtx, InArchimate, ArchimateConfiguration): Alias
```
A grouping rectangle whose body holds shapes/relationships (and nested boundaries
via `InBoundary`); returns its alias.

## Stereotypes & legend

```scala
def defineStereoType(
  name: String,
  description: Option[String],
  legendColor: Option[String],
  skinParameters: SkinParamProperty*,
)(using PUmlCtx, InArchimate): ShapeStereoType
```
Defines a reusable stereotype (with a skinparam block) and returns a
`ShapeStereoType` handle — pass it as `shape(..., stereoType = Some(s))` and list
it in the legend.

```scala
def legend(
  title: String = "Legend",
  backgroundColorHexOrName: String = "#GhostWhite",
  stereoTypes: List[ShapeStereoType] = Nil,
  tags: List[SvgIcon] = Nil,
)(using PUmlCtx, InArchimate): Unit
```

`archimateSkinParam(stereotype, properties*)` is the in-body convenience wrapper
over `skinParam`.

## ShapeType — the element vocabulary

`ShapeType` is the full ArchiMate 3 element set, each carrying its layer
(`ShapeGroup`) and sprite-pack name. Layer accessors return the elements of a
layer; `ShapeType.*` is all of them.

| Layer (`ShapeGroup`) | Accessor | Examples |
| --- | --- | --- |
| Strategy | `ShapeType.strategy` | `StrategyResource StrategyCapability StrategyValueStream StrategyCourseOfAction` |
| Business | `ShapeType.business` | `BusinessActor BusinessRole BusinessProcess BusinessService BusinessObject …` |
| Application | `ShapeType.application` | `ApplicationComponent ApplicationService ApplicationInterface ApplicationFunction ApplicationDataObject …` |
| Technology | `ShapeType.technology` | `TechnologyNode TechnologyDevice TechnologySystemSoftware TechnologyService TechnologyArtifact …` |
| Physical | `ShapeType.physical` | `PhysicalFacility PhysicalEquipment PhysicalDistributionNetwork PhysicalMaterial` |
| Motivation | `ShapeType.motivation` | `MotivationStakeholder MotivationDriver MotivationGoal MotivationRequirement MotivationConstraint …` |
| Implementation | `ShapeType.implementation` | `ImplementationWorkPackage ImplementationDeliverable ImplementationPlateau ImplementationEvent ImplementationGap` |

Each `ShapeType` exposes `.group: ShapeGroup`, `.name: String`, `.icon: String`,
and `.decorate(label)` (prefixes a separator for mass-object labels). `ShapeGroup`
(the 7 layers) carries `.defaultColor` and `.prefix`.

## Worked example

```scala
block:
  uml:
    archimateDiagram:
      val phaseout = defineStereoType(
        "phaseout", Some("phasing out"), Some("#FF8888"),
        BorderColor("#FF0000"), BorderBold(), Shadowing(true),
      )
      legend("Domain legend", stereoTypes = List(phaseout))
      boundary("Customer-facing", shapeType = Some(StrategyCapability)):
        val customer = shape(BusinessActor, label("Customer"))
        val store    = shape(ApplicationComponent, label("Web store", Some("IT system")))
        val payment  = shape(
          ApplicationComponent,
          label("Legacy payment", Some("IT system"), description = Some("being retired")),
          stereoType = Some(phaseout),
        )
        relationship(Serving, Right, Some("uses"))(store)(customer)
        relationship(Composition)(store)(payment)
```
