package fish.genius.uml.diagram

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeGroup, ShapeType}

// The ArchiMate notation as plain geometry: what a box looks like, the icon in its
// corner and the ends of a connector. Drawn once here, written as TikZ and as SVG by
// the two writers, so the PDF and the Markdown export show the same diagram.

type Pt = (Double, Double)

// A colour by name. The layer colours are provided with ArchiMate's defaults and can be
// redefined by a theme before the picture (TikZ) or in the palette (SVG, PNG).
enum Ink derives CanEqual:
  case Empty, Paper, Line
  case Layer(name: String)

enum Dash derives CanEqual:
  case Solid, Dashed, Dotted

// The end of a connector, at its source or its target.
enum Head derives CanEqual:
  case FilledDiamond, OpenDiamond, Dot, FilledTriangle, OpenTriangle, Barb, OpenDot

// What a shape is part of, which says how a writer styles it: a box in its layer's
// colour with an edge a shade darker and a soft shadow, a container lighter behind
// what it holds, a grouping's dashed frame, a mark (an icon, a band) in its box's edge
// colour, a patch of the field under it, or a plain line.
enum Role derives CanEqual:
  case Plain
  case Element(layer: String)
  case Container(layer: String)
  case Group
  case Mark(layer: String)
  case Halo
  // a flat patch of the field it lies on (a container, a grouping, the page): the
  // gap where one line crosses another, the ground under a connector's label
  case Patch(on: Role)
  // drawn in a layer's colour of its own: the heads of a connector in a colour (its ink)
  case Inked(layer: String)

// how a line of text is set: its weight, and whether it is the main text or quieter
enum Weight derives CanEqual:
  case Regular, Medium, SemiBold, Italic

enum Emphasis derives CanEqual:
  case Main, Muted

enum Prim derives CanEqual:

  case Poly(
    points: List[Pt],
    closed: Boolean,
    fill: Ink = Ink.Empty,
    dash: Dash = Dash.Solid,
    role: Role = Role.Plain)

  case Circle(
    centre: Pt,
    r: Double,
    fill: Ink = Ink.Empty,
    role: Role = Role.Plain)

  case Ellipse(
    centre: Pt,
    rx: Double,
    ry: Double,
    fill: Ink = Ink.Empty,
    role: Role = Role.Plain)

  // a rectangle from its lower left corner; without `stroke` only its fill shows
  case Box(
    x: Double,
    y: Double,
    w: Double,
    h: Double,
    radius: Double,
    fill: Ink,
    dash: Dash = Dash.Solid,
    stroke: Boolean = true,
    role: Role = Role.Plain)

  // one line of text on its baseline, centred on x or starting from it, at size points;
  // with a backdrop it stands on its own patch of that role's fill, so a line under it
  // does not run through the letters
  case Text(
    baseline: Pt,
    text: String,
    size: Double,
    weight: Weight = Weight.Regular,
    tone: Emphasis = Emphasis.Main,
    centred: Boolean = true,
    backdrop: Option[Role] = None)

  // with `ink`, the line and its heads in that layer's colour instead of the line's
  case Connector(
    points: List[Pt],
    dash: Dash,
    start: Option[Head],
    end: Option[Head],
    ink: Option[String] = None)

  // a filled outline in a colour of its own (hex), as a logo is drawn: moves, lines and
  // cubic curves, each piece closed; with evenOdd an inner piece cuts a hole
  case Curve(
    segments: List[Seg],
    fill: String,
    evenOdd: Boolean = false)

  // a picture (PNG or JPEG, base64) from its lower left corner, stretched to w × h
  case Image(
    x: Double,
    y: Double,
    w: Double,
    h: Double,
    data: String,
    mime: String)

end Prim

// A piece of a Curve's outline, in centimetres.
enum Seg derives CanEqual:
  case Move(to: Pt)
  case Line(to: Pt)

  case Cubic(
    c1: Pt,
    c2: Pt,
    to: Pt)

  case Close

object Seg:

  def shift(
    seg: Seg,
    dx: Double,
    dy: Double,
  ): Seg =
    def p(pt: Pt): Pt = (pt._1 + dx, pt._2 + dy)
    seg match
      case Move(to)        => Move(p(to))
      case Line(to)        => Line(p(to))
      case Cubic(a, b, to) => Cubic(p(a), p(b), p(to))
      case Close           => Close

  // every point a segment names, its controls included (they bound its curve)
  def points(seg: Seg): List[Pt] = seg match
    case Move(to)        => List(to)
    case Line(to)        => List(to)
    case Cubic(a, b, to) => List(a, b, to)
    case Close           => Nil

end Seg

object Notation:

  // ArchiMate's own layer colours, as ArchiMate 3.2 suggests them
  val palette: List[(String, String)] = List(
    "am-business" -> "FFFFB5",
    "am-application" -> "B5FFFF",
    "am-technology" -> "C9E7B7",
    "am-physical" -> "C9E7B7",
    "am-strategy" -> "F5DEAA",
    "am-motivation" -> "CCCCFF",
    "am-implementation" -> "FFE0E0",
    "am-line" -> "3C3C3C",
    "am-text" -> "1F1F1F",
    "am-muted" -> "6B6B6B",
    "am-paper" -> "FFFFFF",
  )

  // The colours a writer uses: ArchiMate's, with a palette's own laid over them. A
  // palette entry counts only with a name of lower-case letters, digits and dashes and
  // six hex digits (a leading # is fine); anything else is left out, so a stray value
  // cannot break the SVG, the LaTeX or the PNG.
  def colours(palette: Map[String, String]): List[(String, String)] =
    val valid = palette.toList.sorted.collect:
      case (name, hex)
        if name.matches("[a-z][a-z0-9-]*") && hex.stripPrefix("#").matches("[0-9A-Fa-f]{6}") =>
        name -> hex.stripPrefix("#").toUpperCase
    val own   = valid.toMap
    Notation.palette.map((name, hex) => name -> own.getOrElse(name, hex)) ++
    valid.filterNot((name, _) => Notation.palette.exists(_._1 == name))

  // text as it may be written: control characters, which neither XML nor LaTeX
  // takes, become spaces
  def clean(text: String): String = text.map(c => if c < ' ' || c == '\u007f' then ' ' else c)

  def colour(group: ShapeGroup): Ink = Ink.Layer(layer(group))

  def layer(group: ShapeGroup): String = group match
    case ShapeGroup.Business       => "am-business"
    case ShapeGroup.Application    => "am-application"
    case ShapeGroup.Technology     => "am-technology"
    case ShapeGroup.Physical       => "am-physical"
    case ShapeGroup.Strategy       => "am-strategy"
    case ShapeGroup.Motivation     => "am-motivation"
    case ShapeGroup.Implementation => "am-implementation"

  // The outline of a box: square for structure, rounded for behaviour and strategy,
  // cut corners for motivation, and a band across the top for a passive object.
  enum Outline derives CanEqual:
    case Square, Rounded, Cut, Banded

  def outline(shape: ShapeType): Outline = shape match
    case ShapeType.ApplicationDataObject | ShapeType.BusinessObject | ShapeType.BusinessContract |
      ShapeType.BusinessRepresentation | ShapeType.TechnologyArtifact | ShapeType.PhysicalMaterial =>
      Outline.Banded
    case s if s.group == ShapeGroup.Motivation               => Outline.Cut
    case s if behaviour(s) || s.group == ShapeGroup.Strategy => Outline.Rounded
    case _                                                   => Outline.Square

  private def behaviour(shape: ShapeType): Boolean =
    val n = shape.toString
    List("Process", "Function", "Interaction", "Event", "Service").exists(n.endsWith) ||
    shape == ShapeType.ImplementationWorkPackage

  // ArchiMate's connector notation
  def ends(relationship: RelationshipType): (Dash, Option[Head], Option[Head]) =
    relationship match
      case RelationshipType.Composition => (Dash.Solid, Some(Head.FilledDiamond), None)
      case RelationshipType.Aggregation => (Dash.Solid, Some(Head.OpenDiamond), None)
      case RelationshipType.Assignment  => (Dash.Solid, Some(Head.Dot), Some(Head.FilledTriangle))
      case RelationshipType.Serving     => (Dash.Solid, None, Some(Head.Barb))
      case RelationshipType.Flow        => (Dash.Dashed, None, Some(Head.FilledTriangle))
      case RelationshipType.Specialization      => (Dash.Solid, None, Some(Head.OpenTriangle))
      case RelationshipType.Association         => (Dash.Solid, None, None)
      case RelationshipType.DirectedAssociation => (Dash.Solid, None, Some(Head.Barb))
      case RelationshipType.Realization         => (Dash.Dashed, None, Some(Head.OpenTriangle))
      case RelationshipType.Triggering          => (Dash.Solid, None, Some(Head.FilledTriangle))
      case RelationshipType.Access              => (Dash.Dotted, None, None)
      case RelationshipType.ReadAccess          => (Dash.Dotted, Some(Head.Barb), None)
      case RelationshipType.WriteAccess         => (Dash.Dotted, None, Some(Head.Barb))
      case RelationshipType.ReadWriteAccess     => (Dash.Dotted, Some(Head.Barb), Some(Head.Barb))
      case RelationshipType.Influence           => (Dash.Dashed, None, Some(Head.Barb))

  // points on a circle from a0 to a1 degrees, for the round parts of an icon
  private def arc(
    c: Pt,
    rx: Double,
    ry: Double,
    a0: Double,
    a1: Double,
    n: Int = 12,
  ): List[Pt] = (0 to n).toList.map: i =>
    val a = math.toRadians(a0 + (a1 - a0) * i / n)
    (c._1 + rx * math.cos(a), c._2 + ry * math.sin(a))

  private def line(points: Pt*): Prim      = Prim.Poly(points.toList, closed = false)
  private def shape(points: Pt*): Prim     = Prim.Poly(points.toList, closed = true, Ink.Paper)
  private def ring(c: Pt, r: Double): Prim = Prim.Circle(c, r, Ink.Paper)
  private def disc(c: Pt, r: Double): Prim = Prim.Circle(c, r, Ink.Line)

  private val actor = List(
    ring((0.5, 0.84), 0.15),
    line((0.5, 0.69), (0.5, 0.3)),
    line((0.18, 0.56), (0.82, 0.56)),
    line((0.22, 0.0), (0.5, 0.3), (0.78, 0.0)),
  )

  // a cylinder on its side
  private val role = List(
    Prim.Poly(
      List((0.8, 0.8), (0.2, 0.8)) ++ arc((0.2, 0.5), 0.15, 0.3, 90, 270) ++ List((0.8, 0.2)),
      closed = false,
    ),
    Prim.Ellipse((0.8, 0.5), 0.15, 0.3, Ink.Paper),
  )

  private val collaboration = List(ring((0.36, 0.5), 0.3), ring((0.64, 0.5), 0.3))

  private val interface = List(line((0.0, 0.5), (0.52, 0.5)), ring((0.74, 0.5), 0.22))

  private val service = List(
    Prim.Box(0.0, 0.22, 1.0, 0.56, 0.28, Ink.Paper)
  )

  private val function = List(
    shape((0.1, 0.0), (0.1, 0.72), (0.5, 1.0), (0.9, 0.72), (0.9, 0.0), (0.5, 0.28))
  )

  private val process = List(
    shape(
      (0.0, 0.36),
      (0.58, 0.36),
      (0.58, 0.15),
      (1.0, 0.5),
      (0.58, 0.85),
      (0.58, 0.64),
      (0.0, 0.64),
    )
  )

  private val interaction = List(
    Prim.Poly(arc((0.44, 0.5), 0.36, 0.4, 90, 270), closed = true, Ink.Paper),
    Prim.Poly(arc((0.56, 0.5), 0.36, 0.4, -90, 90), closed = true, Ink.Paper),
  )

  private val event = List(
    Prim.Poly(
      List((0.0, 0.8), (0.7, 0.8)) ++ arc((0.7, 0.5), 0.3, 0.3, 90, -90) ++
      List((0.0, 0.2), (0.22, 0.5)),
      closed = true,
      Ink.Paper,
    )
  )

  private val component = List(
    Prim.Box(0.22, 0.05, 0.78, 0.9, 0.0, Ink.Paper),
    Prim.Box(0.06, 0.58, 0.32, 0.16, 0.0, Ink.Paper),
    Prim.Box(0.06, 0.26, 0.32, 0.16, 0.0, Ink.Paper),
  )

  private val node = List(
    shape((0.0, 0.0), (0.78, 0.0), (0.78, 0.78), (0.0, 0.78)),
    shape((0.0, 0.78), (0.22, 1.0), (1.0, 1.0), (0.78, 0.78)),
    shape((0.78, 0.0), (1.0, 0.22), (1.0, 1.0), (0.78, 0.78)),
  )

  private val device = List(
    Prim.Box(0.1, 0.32, 0.8, 0.68, 0.08, Ink.Paper),
    shape((0.0, 0.0), (1.0, 0.0), (0.78, 0.3), (0.22, 0.3)),
  )

  private val systemSoftware = List(ring((0.58, 0.58), 0.38), ring((0.44, 0.44), 0.38))

  private val artifact = List(
    shape((0.15, 0.0), (0.15, 1.0), (0.64, 1.0), (0.85, 0.79), (0.85, 0.0)),
    line((0.64, 1.0), (0.64, 0.79), (0.85, 0.79)),
  )

  private val network = List(
    line((0.15, 0.3), (0.85, 0.3)),
    line((0.15, 0.7), (0.85, 0.7)),
    line((0.15, 0.3), (0.15, 0.7)),
    line((0.85, 0.3), (0.85, 0.7)),
    disc((0.15, 0.3), 0.09),
    disc((0.85, 0.3), 0.09),
    disc((0.15, 0.7), 0.09),
    disc((0.85, 0.7), 0.09),
  )

  private val path = List(
    Prim.Poly(List((0.12, 0.5), (0.88, 0.5)), closed = false, dash = Dash.Dashed),
    line((0.28, 0.75), (0.04, 0.5), (0.28, 0.25)),
    line((0.72, 0.75), (0.96, 0.5), (0.72, 0.25)),
  )

  private val capability = List(
    shape(
      (0.0, 0.0),
      (1.0, 0.0),
      (1.0, 1.0),
      (0.67, 1.0),
      (0.67, 0.67),
      (0.33, 0.67),
      (0.33, 0.33),
      (0.0, 0.33),
    ),
    line((0.33, 0.0), (0.33, 0.33), (1.0, 0.33)),
    line((0.67, 0.0), (0.67, 0.67), (1.0, 0.67)),
  )

  private val resource = List(
    Prim.Box(0.0, 0.2, 0.84, 0.6, 0.12, Ink.Paper),
    Prim.Box(0.84, 0.38, 0.14, 0.24, 0.0, Ink.Paper),
    line((0.25, 0.32), (0.25, 0.68)),
    line((0.42, 0.32), (0.42, 0.68)),
    line((0.59, 0.32), (0.59, 0.68)),
  )

  private val valueStream = List(
    shape((0.0, 0.2), (0.72, 0.2), (1.0, 0.5), (0.72, 0.8), (0.0, 0.8), (0.26, 0.5))
  )

  private def target(withArrow: Boolean) =
    List(ring((0.55, 0.55), 0.42), ring((0.55, 0.55), 0.27), disc((0.55, 0.55), 0.1)) ++
    (if withArrow then List(line((0.0, 0.0), (0.5, 0.5)), line((0.3, 0.5), (0.5, 0.5), (0.5, 0.3)))
     else Nil)

  private val driver =
    ring((0.5, 0.5), 0.34) :: disc((0.5, 0.5), 0.08) ::
    (0 until 8).toList.map: i =>
      val a = math.toRadians(i * 45.0)
      line(
        (0.5 + 0.34 * math.cos(a), 0.5 + 0.34 * math.sin(a)),
        (0.5 + 0.5 * math.cos(a), 0.5 + 0.5 * math.sin(a)),
      )

  private val assessment = List(ring((0.6, 0.6), 0.32), line((0.37, 0.37), (0.0, 0.0)))

  private val principle = List(
    Prim.Box(0.18, 0.0, 0.64, 1.0, 0.0, Ink.Paper),
    line((0.5, 0.36), (0.5, 0.84)),
    disc((0.5, 0.18), 0.06),
  )

  private val requirement = List(shape((0.2, 0.2), (1.0, 0.2), (0.8, 0.8), (0.0, 0.8)))

  private val constraint = requirement :+ line((0.36, 0.2), (0.16, 0.8))

  private val meaning = List(
    Prim.Ellipse((0.3, 0.45), 0.28, 0.24, Ink.Paper),
    Prim.Ellipse((0.7, 0.45), 0.28, 0.24, Ink.Paper),
    Prim.Ellipse((0.5, 0.62), 0.3, 0.26, Ink.Paper),
  )

  private val value = List(Prim.Ellipse((0.5, 0.5), 0.5, 0.3, Ink.Paper))

  private val workPackage = List(
    Prim.Poly(arc((0.5, 0.5), 0.36, 0.36, 110, 400), closed = false),
    line((0.28, 0.98), (0.38, 0.84), (0.22, 0.76)),
  )

  private val deliverable = List(
    Prim.Poly(
      List((0.0, 1.0), (1.0, 1.0), (1.0, 0.18)) ++ arc((0.75, 0.18), 0.25, 0.12, 0, -180, 6) ++
      arc((0.25, 0.18), 0.25, 0.12, 0, 180, 6),
      closed = true,
      Ink.Paper,
    )
  )

  private val plateau = List(
    line((0.3, 0.85), (1.0, 0.85)),
    line((0.15, 0.5), (0.85, 0.5)),
    line((0.0, 0.15), (0.7, 0.15)),
  )

  private val gap =
    List(ring((0.5, 0.5), 0.34), line((0.0, 0.62), (1.0, 0.62)), line((0.0, 0.38), (1.0, 0.38)))

  private val product = List(
    Prim.Box(0.0, 0.1, 1.0, 0.8, 0.0, Ink.Paper),
    Prim.Box(0.0, 0.66, 0.5, 0.24, 0.0, Ink.Paper),
  )

  private val location = List(
    Prim.Poly(arc((0.5, 0.66), 0.3, 0.3, -35, 215) ++ List((0.5, 0.0)), closed = true, Ink.Paper)
  )

  private val facility = List(
    shape(
      (0.0, 0.0),
      (1.0, 0.0),
      (1.0, 0.55),
      (0.75, 0.4),
      (0.75, 0.55),
      (0.5, 0.4),
      (0.5, 0.55),
      (0.25, 0.4),
      (0.25, 1.0),
      (0.0, 1.0),
    )
  )

  private val equipment = List(ring((0.36, 0.36), 0.3), ring((0.72, 0.72), 0.22))

  private val distribution = List(
    line((0.0, 0.38), (1.0, 0.38)),
    line((0.0, 0.62), (1.0, 0.62)),
    line((0.2, 0.84), (0.0, 0.5), (0.2, 0.16)),
    line((0.8, 0.84), (1.0, 0.5), (0.8, 0.16)),
  )

  // The icon in a box's top right corner, in a unit square with y up; Nil for the
  // passive objects, whose band says what they are.
  def icon(shape: ShapeType): List[Prim] = shape match
    case ShapeType.BusinessActor                                  => actor
    case ShapeType.BusinessRole | ShapeType.MotivationStakeholder => role
    case ShapeType.BusinessCollaboration | ShapeType.ApplicationCollaboration |
      ShapeType.TechnologyCollaboration => collaboration
    case ShapeType.BusinessInterface | ShapeType.ApplicationInterface | ShapeType.TechnologyInterface =>
      interface
    case ShapeType.BusinessService | ShapeType.ApplicationService | ShapeType.TechnologyService =>
      service
    case ShapeType.BusinessFunction | ShapeType.ApplicationFunction | ShapeType.TechnologyFunction =>
      function
    case ShapeType.BusinessProcess | ShapeType.ApplicationProcess | ShapeType.TechnologyProcess =>
      process
    case ShapeType.BusinessInteraction | ShapeType.ApplicationInteraction |
      ShapeType.TechnologyInteraction => interaction
    case ShapeType.BusinessEvent | ShapeType.ApplicationEvent | ShapeType.TechnologyEvent |
      ShapeType.ImplementationEvent => event
    case ShapeType.ApplicationComponent => component
    case ShapeType.TechnologyNode                 => node
    case ShapeType.TechnologyDevice               => device
    case ShapeType.TechnologySystemSoftware       => systemSoftware
    case ShapeType.TechnologyArtifact             => artifact
    case ShapeType.TechnologyPath                 => path
    case ShapeType.StrategyCapability             => capability
    case ShapeType.StrategyResource               => resource
    case ShapeType.StrategyValueStream            => valueStream
    case ShapeType.StrategyCourseOfAction         => target(withArrow = true)
    case ShapeType.MotivationDriver               => driver
    case ShapeType.MotivationAssessment           => assessment
    case ShapeType.MotivationGoal                 => target(withArrow = false)
    case ShapeType.MotivationOutcome              => target(withArrow = true)
    case ShapeType.MotivationPrinciple            => principle
    case ShapeType.MotivationRequirement          => requirement
    case ShapeType.MotivationConstraint           => constraint
    case ShapeType.MotivationMeaning              => meaning
    case ShapeType.MotivationValue                => value
    case ShapeType.ImplementationWorkPackage      => workPackage
    case ShapeType.ImplementationDeliverable      => deliverable
    case ShapeType.ImplementationPlateau          => plateau
    case ShapeType.ImplementationGap              => gap
    case ShapeType.BusinessProduct                => product
    case ShapeType.BusinessLocation               => location
    case ShapeType.PhysicalFacility               => facility
    case ShapeType.PhysicalEquipment              => equipment
    case ShapeType.PhysicalDistributionNetwork    => distribution
    case ShapeType.TechnologyCommunicationNetwork => network
    case ShapeType.ApplicationDataObject | ShapeType.BusinessObject | ShapeType.BusinessContract |
      ShapeType.BusinessRepresentation | ShapeType.PhysicalMaterial => Nil

  // A badge's sign, in a unit square with y up, solid in its box's mark colour so it
  // reads at a small size. The twelve geometric glyphs tell things apart; the globe
  // and the padlock mark a public and a closed API, the warning and the failure a
  // health that is degraded or failing.
  def glyph(g: fish.genius.uml.scene.Glyph): List[Prim] =
    import fish.genius.uml.scene.Glyph
    def solid(points: Pt*): Prim = Prim.Poly(points.toList, closed = true, Ink.Line)
    def around(
      n: Int,
      r: Double,
      turn: Double = 90.0,
    ): List[Pt]                  = (0 until n).toList.map: i =>
      val a = math.toRadians(turn + i * 360.0 / n)
      (0.5 + r * math.cos(a), 0.5 + r * math.sin(a))
    g match
      case Glyph.Circle       => List(disc((0.5, 0.5), 0.4))
      case Glyph.Square       => List(solid((0.12, 0.12), (0.88, 0.12), (0.88, 0.88), (0.12, 0.88)))
      case Glyph.TriangleUp   => List(solid((0.06, 0.1), (0.94, 0.1), (0.5, 0.92)))
      case Glyph.Diamond      => List(solid((0.5, 0.02), (0.98, 0.5), (0.5, 0.98), (0.02, 0.5)))
      case Glyph.Star         =>
        val outer = around(5, 0.5)
        val inner = around(5, 0.2, 90.0 + 36.0)
        List(solid(outer.zip(inner).flatMap((a, b) => List(a, b))*))
      case Glyph.Hexagon      => List(solid(around(6, 0.46)*))
      case Glyph.TriangleDown => List(solid((0.06, 0.9), (0.94, 0.9), (0.5, 0.08)))
      case Glyph.Cross        =>
        List(
          solid(
            (0.36, 0.06),
            (0.64, 0.06),
            (0.64, 0.36),
            (0.94, 0.36),
            (0.94, 0.64),
            (0.64, 0.64),
            (0.64, 0.94),
            (0.36, 0.94),
            (0.36, 0.64),
            (0.06, 0.64),
            (0.06, 0.36),
            (0.36, 0.36),
          )
        )
      case Glyph.Pentagon     => List(solid(around(5, 0.48)*))
      case Glyph.Ring         => List(disc((0.5, 0.5), 0.42), ring((0.5, 0.5), 0.22))
      case Glyph.Ellipse      => List(Prim.Ellipse((0.5, 0.5), 0.46, 0.26, Ink.Line))
      case Glyph.Chevron      =>
        List(solid((0.06, 0.78), (0.5, 0.44), (0.94, 0.78), (0.94, 0.5), (0.5, 0.16), (0.06, 0.5)))
      case Glyph.Globe        =>
        List(
          Prim.Circle((0.5, 0.5), 0.44, Ink.Paper),
          Prim.Ellipse((0.5, 0.5), 0.2, 0.44),
          line((0.06, 0.5), (0.94, 0.5)),
        )
      case Glyph.Padlock      =>
        List(
          line(arc((0.5, 0.6), 0.2, 0.26, 0, 180)*),
          Prim.Box(0.14, 0.06, 0.72, 0.5, 0.06, Ink.Line),
        )
      case Glyph.Warning      =>
        List(
          shape((0.04, 0.08), (0.96, 0.08), (0.5, 0.94)),
          line((0.5, 0.66), (0.5, 0.36)),
          disc((0.5, 0.22), 0.05),
        )
      case Glyph.Failure      =>
        List(
          ring((0.5, 0.5), 0.44),
          line((0.28, 0.28), (0.72, 0.72)),
          line((0.28, 0.72), (0.72, 0.28)),
        )
    end match

  end glyph

end Notation
