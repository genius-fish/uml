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
  // decides how much floor the layout keeps beside each plinth. footprint sizes a leaf
  // (width, depth) instead of the fixed plinth, as a flat diagram sizes a box to its
  // name, and gives a container the least width it may have; headerAtBack puts a container's header
  // strip at its back, where a flat
  // diagram reads it first, instead of at its front. spreadPorts gives each connector
  // on a side its own point along it, ordered by where its other end lies, instead of
  // all meeting in the middle (a flat diagram, where they would run on top of one another).
  final case class Options(
    nodeSpacing: Double = 0.9,
    layerSpacing: Double = 1.6,
    containerPadding: Double = 0.4,
    containerHeader: Double = 1.0,
    maxPerRow: Int = 0,
    panes: PaneStyle = PaneStyle(),
    footprint: SceneNode => Option[(Double, Double)] = _ => None,
    headerAtBack: Boolean = false,
    spreadPorts: Boolean = false)

  def layout(graph: SceneGraph, options: Options = Options()): Either[SceneError, Scene] =
    graph.problems match
      case first :: rest => Left(SceneError.InvalidGraph(::(first, rest)))
      case Nil if graph.nodes.isEmpty => Right(Scene(graph.name, 0, 0, Nil, Nil, Nil, options.panes))
      case Nil =>
        val structure = SceneStructure.of(graph)
        attempt(structure, options, modelOrder = true)
          .orElse(attempt(structure, options, modelOrder = false))
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
      folds = ElkPass.foldsOf(structure, plain, options)
      first <- if folds.isEmpty then Right(plain)
      else ElkPass.run(structure, Map.empty, folds, options, modelOrder)
      laid  <- settle(
        structure,
        ElkPass.sidesOf(structure, first),
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
        val again = ElkPass.sidesOf(structure, built)
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
    val shifts        = if options.spreadPorts then spread(structure, sides, byKey) else Map.empty
    val relationships = structure.edges.flatMap: edge =>
      for
        laid   <- built.edges.get(edge.id)
        ends   <- sides.get(edge.id)
        source <- byKey.get(edge.source)
        target <- byKey.get(edge.target)
      yield
        val (from, to) =
          (shifts.getOrElse((edge.id, true), 0.0), shifts.getOrElse((edge.id, false), 0.0))
        val route = routes.finish(source, ends.from, built.bendsOf(laid), target, ends.to, from, to)
        SceneRelationship(
          key = edge.id,
          source = edge.source,
          target = edge.target,
          kind = SceneNames.of(edge.relationship),
          sourcePort = ends.from,
          targetPort = ends.to,
          bends = route.bends,
          clear = route.clear,
          start = Option.when(options.spreadPorts)(SceneRoutes.portOf(source, ends.from, from)),
          end = Option.when(options.spreadPorts)(SceneRoutes.portOf(target, ends.to, to)),
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

  private val SPREAD_STEP  = 0.35
  private val SPREAD_SHARE = 0.6

  // How far along its side each connector end moves from the middle, keyed by the
  // connector and whether it is the source end: the ends that meet on one side are
  // spaced evenly over its middle part, in the order their other ends lie along it.
  private def spread(
    structure: SceneStructure,
    sides: Map[SceneId, Sides],
    byKey: Map[SceneId, SceneElement],
  ): Map[(SceneId, Boolean), Double] =
    val ends = structure.edges.flatMap: edge =>
      sides.get(edge.id).toList.flatMap: laid =>
        List(
          (edge.id, true, edge.source, laid.from, edge.target),
          (edge.id, false, edge.target, laid.to, edge.source),
        )
    ends
      .groupBy(
        (
          _,
          _,
          at,
          side,
          _,
        ) => (at, side)
      )
      .toList
      .flatMap:
        case ((at, side), here) =>
          byKey.get(at).toList.flatMap: element =>
            val vertical = side == PortName.North || side == PortName.South
            val length   = if vertical then element.w else element.d
            val step     =
              if here.size < 2 then 0.0
              else math.min(SPREAD_STEP, length * SPREAD_SHARE / (here.size - 1))
            val ordered  = here.sortBy(
              (
                id,
                _,
                _,
                _,
                other,
              ) =>
                byKey.get(other).fold((0.0, id.value))(o =>
                  ((if vertical then o.x else o.y), id.value)
                )
            )
            ordered.zipWithIndex.map:
              case ((id, isSource, _, _, _), i) => (id, isSource) ->
                round((i - (here.size - 1) / 2.0) * step)
      .toMap

  end spread

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
    )

  end element

end SceneLayout

// The two plinth sides a connector meets on.
final private[scene] case class Sides(from: PortName, to: PortName)
