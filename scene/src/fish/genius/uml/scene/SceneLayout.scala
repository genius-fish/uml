package fish.genius.uml.scene

// Lays a SceneGraph out as a Scene with ELK Layered, top to bottom, which the scene
// reads as back to front: each ArchiMate layer is an ELK partition, so Motivation lies
// at the back, then Strategy, Business, Application, Technology and Physical, with
// Implementation at the front. It is the layered diagram laid flat on the table.
//
// A first pass places the plinths without ports. A row it made too wide is folded, and
// from where each connector's ends landed the sides they meet on follow: one behind the
// other meet south to north, side by side east to west. The last pass puts a port in
// the middle of each side used and routes every connector orthogonally between them
// (SceneRoutes finishes what ELK leaves unfinished). With ELK's model order the same
// graph gives the same scene; ELK 0.9.1 fails on model order in some nested graphs,
// mostly larger ones, and then the layout runs without it: still valid, but two runs
// may differ there (ELK's hierarchical crossing minimisation is not stable).
object SceneLayout:

  val PLINTH_WIDTH: Double  = 2.0
  val PLINTH_DEPTH: Double  = 1.3
  val PLINTH_HEIGHT: Double = 0.2
  val JUNCTION_SIZE: Double = 1.24

  // Spacing in scene units. maxPerRow caps the plinths side by side; 0 means about the
  // square root of the graph's size. panes says how the passports' panes stand, which
  // decides how much floor the layout keeps beside each plinth.
  //
  // The rest is for a flat diagram, and the scene leaves it off. footprint sizes a leaf
  // (width, depth) instead of the fixed plinth, as a diagram sizes a box to its name
  // (ELK sizes a container from its children). headerAtBack puts a container's
  // header strip at its back, where a diagram reads it first. spreadPorts gives each
  // connector its own port, which ELK places along the side, instead of all meeting in
  // the middle and running on top of one another. labelSize says how large a
  // connector's label is (width, depth), and the layout keeps room for it. compact
  // places the nodes with network simplex and compacts the result, which a page
  // wants and a scene to walk through does not. rowWidth folds a row wider than it
  // (a page's width; 0, no limit). A diagram
  // has no passports, and spreadPorts does not keep a pane's floor: use one or the other.
  final case class Options(
    nodeSpacing: Double = 0.9,
    layerSpacing: Double = 1.6,
    containerPadding: Double = 0.4,
    containerHeader: Double = 1.0,
    maxPerRow: Int = 0,
    panes: PaneStyle = PaneStyle(),
    footprint: SceneNode => Option[(Double, Double)] = _ => None,
    headerAtBack: Boolean = false,
    spreadPorts: Boolean = false,
    labelSize: String => Option[(Double, Double)] = _ => None,
    compact: Boolean = false,
    rowWidth: Double = 0,
    direction: Direction = Direction.Down)

  /**
   * Which way the layers run: down the page (the default, and back to front in a scene), or
   * left to right, which draws a chain (a flow) wide instead of tall. Laid out to the right,
   * no row is folded: the rows are the layers' columns then. It is meant for a flat diagram
   * (`Diagram.Options.direction`); a 3D scene's panes assume the layers run back to front.
   */
  enum Direction derives CanEqual:
    case Down, Right

  def layout(graph: SceneGraph, options: Options = Options()): Either[SceneError, Scene] =
    graph.problems match
      case first :: rest => Left(SceneError.InvalidGraph(::(first, rest)))
      case Nil if graph.nodes.isEmpty => Right(Scene(graph.name, 0, 0, Nil, Nil, Nil, options.panes))
      case Nil =>
        val structure = SceneStructure.of(graph)
        // model order where ELK manages it; and, since ELK's network simplex fails on
        // some nested graphs, a compact layout that cannot be had falls back to the plain one
        val plain     = options.copy(compact = false)
        val tries     =
          List(options -> true, options -> false) ++
          (if options.compact then List(plain -> true, plain -> false) else Nil)
        tries.tail
          .foldLeft(attempt(structure, tries.head._1, tries.head._2)): (laid, next) =>
            laid.orElse(attempt(structure, next._1, next._2))
          .map((sides, built) => scene(graph, structure, sides, built, options))

  private val ROUNDING      = 1000.0
  private val SETTLE_ROUNDS = 3

  // Model order keeps a layout the same from run to run; where ELK fails on it, the same
  // passes run without it.
  private def attempt(
    structure: SceneStructure,
    options: Options,
    modelOrder: Boolean,
  ): Either[SceneError, (Map[SceneId, Sides], ElkPass.Built)] =
    for
      plain <- ElkPass.run(structure, Map.empty, Nil, options, modelOrder)
      folds =
        if options.direction == Direction.Right then Nil
        else ElkPass.foldsOf(structure, plain, options)
      first <- if folds.isEmpty then Right(plain)
      else ElkPass.run(structure, Map.empty, folds, options, modelOrder)
      laid  <- settle(
        structure,
        ElkPass.sidesOf(structure, first, options.direction),
        folds,
        options,
        modelOrder,
        SETTLE_ROUNDS,
      )
    yield laid

  // Ports move the plinths a little, so each connector's sides are chosen again from
  // where its ends ended up, until they stop changing.
  private def settle(
    structure: SceneStructure,
    sides: Map[SceneId, Sides],
    folds: List[(SceneId, SceneId)],
    options: Options,
    modelOrder: Boolean,
    rounds: Int,
  ): Either[SceneError, (Map[SceneId, Sides], ElkPass.Built)] =
    ElkPass
      .run(structure, sides, folds, options, modelOrder)
      .flatMap: built =>
        val again = ElkPass.sidesOf(structure, built, options.direction)
        if again == sides || rounds <= 1 then Right((sides, built))
        else settle(structure, again, folds, options, modelOrder, rounds - 1)

  private[scene] def round(value: Double): Double = math.rint(value * ROUNDING) / ROUNDING

  private def scene(
    graph: SceneGraph,
    structure: SceneStructure,
    sides: Map[SceneId, Sides],
    built: ElkPass.Built,
    options: Options,
  ): Scene =
    val panes         = options.panes
    val paneSides     = PaneLayout.sides(structure, sides, panes)
    val elements      = structure.nodes.flatMap: node =>
      built.plinthOf(node.id).map(element(structure, built, node, _, paneSides.get(node.id)))
    val byKey         = elements.map(element => element.key -> element).toMap
    val routes        = SceneRoutes(elements)
    val relationships = structure.edges.flatMap: edge =>
      for
        laid   <- built.edges.get(edge.id)
        ends   <- sides.get(edge.id)
        source <- byKey.get(edge.source)
        target <- byKey.get(edge.target)
      yield
        // an edge ELK was handed the other way round comes back reversed
        val back   = built.reversed.contains(edge.id)
        val placed = Option.when(options.spreadPorts)(built.endsOf(laid)).flatten
          .map((a, b) => if back then (b, a) else (a, b))
        val bends  = if back then built.bendsOf(laid).reverse else built.bendsOf(laid)
        val route  = routes.finish(
          source,
          ends.from,
          bends,
          target,
          ends.to,
          placed.map(_._1),
          placed.map(_._2),
        )
        SceneRelationship(
          key = edge.id,
          source = edge.source,
          target = edge.target,
          kind = SceneNames.of(edge.relationship),
          sourcePort = ends.from,
          targetPort = ends.to,
          bends = route.bends,
          clear = route.clear,
          start = placed.map(_._1),
          end = placed.map(_._2),
          label = edge.label.flatMap(text => built.labelOf(laid).map(_(text))),
          colour = edge.colour,
        )
    val flows         = graph.flows.map: flow =>
      FlowRoute(
        flow.id,
        flow.name,
        flow.steps.map(step =>
          FlowLeg(step.source, step.target, step.label, carrier(relationships, step))
        ),
      )
    Scene(graph.name, round(built.width), round(built.depth), elements, relationships, flows, panes)

  end scene

  // The connector a step runs along: one from its source to its target, else one the
  // other way round. A connector to an ancestor is not in the scene, so not a carrier.
  private def carrier(relationships: List[SceneRelationship], step: SceneStep): Option[SceneId] =
    relationships
      .find(r => r.source == step.source && r.target == step.target)
      .orElse(relationships.find(r => r.source == step.target && r.target == step.source))
      .map(_.key)

  private def element(
    structure: SceneStructure,
    built: ElkPass.Built,
    node: SceneNode,
    rect: ElkPass.Rect,
    paneSide: Option[PaneSide],
  ): SceneElement =
    val centre = built.toScene(rect.x + rect.w / 2, rect.y + rect.h / 2)
    SceneElement(
      key = node.id,
      shape = SceneNames.of(node.kind),
      name = node.label,
      x = centre.x,
      y = centre.y,
      w = round(rect.w / ElkPass.SCALE),
      d = round(rect.h / ElkPass.SCALE),
      z = round((structure.chain(node.id).size - 1) * PLINTH_HEIGHT),
      parent = structure.parentOf.get(node.id),
      container = node.isContainer,
      pane =
        for
          side     <- paneSide
          passport <- node.passport
        yield ScenePane(side, PaneLayout.WIDTH, PaneLayout.height(passport), passport),
      outline = node.outline,
      tag = node.tag,
    )

  end element

end SceneLayout

// The two plinth sides a connector meets on.
final private[scene] case class Sides(from: PortName, to: PortName)
