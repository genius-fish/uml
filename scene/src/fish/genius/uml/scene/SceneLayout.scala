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
  // square root of the graph's size.
  final case class Options(
    nodeSpacing: Double = 0.9,
    layerSpacing: Double = 1.6,
    containerPadding: Double = 0.4,
    containerHeader: Double = 1.0,
    maxPerRow: Int = 0)

  def layout(graph: SceneGraph, options: Options = Options()): Either[SceneError, Scene] =
    graph.problems match
      case first :: rest              => Left(SceneError.InvalidGraph(::(first, rest)))
      case Nil if graph.nodes.isEmpty => Right(Scene(graph.name, 0, 0, Nil, Nil))
      case Nil                        =>
        val structure = SceneStructure.of(graph)
        attempt(structure, options, modelOrder = true)
          .orElse(attempt(structure, options, modelOrder = false))
          .map((sides, built) => scene(graph, structure, sides, built))

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
  ): Scene =
    val elements      =
      structure.nodes.flatMap(node => built.rectOf(node.id).map(element(structure, built, node, _)))
    val byKey         = elements.map(element => element.key -> element).toMap
    val routes        = SceneRoutes(elements)
    val relationships = structure.edges.flatMap: edge =>
      for
        laid   <- built.edges.get(edge.id)
        ends   <- sides.get(edge.id)
        source <- byKey.get(edge.source)
        target <- byKey.get(edge.target)
      yield
        val route = routes.finish(source, ends.from, built.bendsOf(laid), target, ends.to)
        SceneRelationship(
          key = edge.id,
          source = edge.source,
          target = edge.target,
          kind = SceneNames.of(edge.relationship),
          sourcePort = ends.from,
          targetPort = ends.to,
          bends = route.bends,
          clear = route.clear,
        )
    Scene(graph.name, round(built.width), round(built.depth), elements, relationships)

  end scene

  private def element(
    structure: SceneStructure,
    built: ElkPass.Built,
    node: SceneNode,
    rect: ElkPass.Rect,
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
    )

  end element

end SceneLayout

// The two plinth sides a connector meets on.
final private[scene] case class Sides(from: PortName, to: PortName)
