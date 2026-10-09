package fish.genius.uml.scene

import java.util.EnumSet

import scala.jdk.CollectionConverters.*
import scala.util.Try

import fish.genius.uml.dsl.archimate.ShapeGroup

import org.eclipse.elk.alg.layered.options.{LayeredMetaDataProvider, LayeredOptions}
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.math.{ElkPadding, KVector}
import org.eclipse.elk.core.options.*
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.*
import org.eclipse.elk.graph.util.ElkGraphUtil

// Lays a SceneGraph out as a Scene with ELK Layered, top to bottom, which the scene
// reads as back to front: each ArchiMate layer is an ELK partition, so Motivation lies
// at the back, then Strategy, Business, Application, Technology and Physical, with
// Implementation at the front. It is the layered diagram laid flat on the table.
//
// A first pass places the plinths without ports. A row it made too wide is folded, and
// from where each connector's ends landed the sides they meet on follow: one behind the
// other meet south to north, side by side east to west. The last pass puts a port in
// the middle of each side used and routes every connector orthogonally between them.
// Routes ELK leaves unfinished between containers go to the OrthogonalRouter.
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
        val s = structure(graph)
        for
          plain <- run(build(s, Map.empty, Nil, options))
          folds = foldsOf(s, plain, options)
          first <- if folds.isEmpty then Right(plain) else run(build(s, Map.empty, folds, options))
          sides = sidesOf(s, first)
          last <- run(build(s, sides, folds, options))
        yield scene(graph, s, sides, last)

  // ELK thinks in pixels; one scene unit is a hundred of them.
  private val SCALE         = 100.0
  private val BUSINESS      = 2
  private val ROUNDING      = 1000.0
  private val ON_THE_LINE   = 1e-3
  private val LOWEST_COLUMN = 4

  private lazy val engine: RecursiveGraphLayoutEngine =
    LayoutMetaDataService.getInstance().registerLayoutMetaDataProviders(LayeredMetaDataProvider())
    RecursiveGraphLayoutEngine()

  // ── what the graph holds ──────────────────────────────────────────────────

  final private case class Structure(
    roots: List[SceneNode],
    nodes: List[SceneNode],
    parentOf: Map[SceneId, SceneId],
    edges: List[SceneEdge],
    partition: Map[SceneId, Int]):
    val byId: Map[SceneId, SceneNode] = nodes.map(n => n.id -> n).toMap

    // id and its ancestors, nearest first
    def chain(id: SceneId): List[SceneId] =
      Iterator.iterate(Option(id))(_.flatMap(parentOf.get)).takeWhile(_.isDefined).flatten.toList

    def isContainer(id: SceneId): Boolean = byId.get(id).exists(_.isContainer)

  private def layerOf(kind: NodeKind): Option[Int] = kind match
    case NodeKind.Element(shape)                                        =>
      Some(shape.group match
        case ShapeGroup.Motivation     => 0
        case ShapeGroup.Strategy       => 1
        case ShapeGroup.Business       => 2
        case ShapeGroup.Application    => 3
        case ShapeGroup.Technology     => 4
        case ShapeGroup.Physical       => 5
        case ShapeGroup.Implementation => 6)
    case NodeKind.Grouping | NodeKind.AndJunction | NodeKind.OrJunction => None

  private def structure(graph: SceneGraph): Structure =
    val nodes                             = graph.allNodes
    val parentOf                          = nodes.flatMap(p => p.children.map(_.id -> p.id)).toMap
    def chain(id: SceneId): List[SceneId] =
      Iterator.iterate(Option(id))(_.flatMap(parentOf.get)).takeWhile(_.isDefined).flatten.toList
    // A connector to a node's own ancestor cannot be drawn on the plinth it stands on.
    val edges                             = graph.edges.filter: e =>
      e.source != e.target && !chain(e.source).contains(e.target) &&
      !chain(e.target).contains(e.source)
    val byId                              = nodes.map(n => n.id -> n).toMap
    val around                            =
      edges.flatMap(e => List(e.source -> e.target, e.target -> e.source)).groupMap(_._1)(_._2)
    // An element takes its layer's partition; a grouping its children's, a junction its
    // neighbours'.
    def partitionOf(node: SceneNode): Int =
      layerOf(node.kind)
        .orElse(node.children.map(partitionOf).minOption)
        .orElse(around.getOrElse(node.id, Nil).flatMap(byId.get).flatMap(n =>
          layerOf(n.kind)
        ).minOption)
        .getOrElse(BUSINESS)
    Structure(graph.nodes, nodes, parentOf, edges, nodes.map(n => n.id -> partitionOf(n)).toMap)
  end structure

  // ── the ELK graph ─────────────────────────────────────────────────────────

  final private case class Built(
    root: ElkNode,
    nodes: Map[SceneId, ElkNode],
    edges: Map[SceneId, ElkEdge]):
    def rectOf(id: SceneId): Option[Rect] = nodes.get(id).map(rect)

  private def configure(n: ElkNode, o: Options): Unit =
    n.setProperty(CoreOptions.DIRECTION, Direction.DOWN)
    n.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL)
    n.setProperty(CoreOptions.PARTITIONING_ACTIVATE, java.lang.Boolean.TRUE)
    n.setProperty(CoreOptions.SPACING_NODE_NODE, Double.box(o.nodeSpacing * SCALE))
    n.setProperty(LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS, Double.box(o.layerSpacing * SCALE))
    n.setProperty(LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS, Double.box(0.4 * SCALE))
    n.setProperty(LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS, Double.box(0.25 * SCALE))
    n.setProperty(CoreOptions.SPACING_EDGE_NODE, Double.box(0.35 * SCALE))
    n.setProperty(CoreOptions.SPACING_EDGE_EDGE, Double.box(0.25 * SCALE))

  private def footprint(kind: NodeKind): (Double, Double) = kind match
    case NodeKind.AndJunction | NodeKind.OrJunction => (JUNCTION_SIZE, JUNCTION_SIZE)
    case NodeKind.Element(_) | NodeKind.Grouping    => (PLINTH_WIDTH, PLINTH_DEPTH)

  private def build(
    s: Structure,
    sides: Map[SceneId, (PortSide, PortSide)],
    folds: List[(SceneId, SceneId)],
    o: Options,
  ): Built =
    val root  = ElkGraphUtil.createGraph()
    root.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered")
    root.setProperty(CoreOptions.HIERARCHY_HANDLING, HierarchyHandling.INCLUDE_CHILDREN)
    configure(root, o)
    val nodes = s.roots.foldLeft(Map.empty[SceneId, ElkNode])((acc, n) => acc ++ add(s, n, root, o))
    // the invisible edges that fold a row too wide into several (see foldsOf)
    for
      (a, b) <- folds
      na     <- nodes.get(a)
      nb     <- nodes.get(b)
    do ElkGraphUtil.createSimpleEdge(na, nb)
    val wanted = sides.toList.flatMap: (id, ends) =>
      s.edges.find(_.id == id).toList.flatMap(e => List(e.source -> ends._1, e.target -> ends._2))
    val ports = wanted.distinct
      .flatMap((id, side) => nodes.get(id).map(n => (id, side) -> port(n, side, s.isContainer(id))))
      .toMap
    val edges = s.edges.flatMap: e =>
      val ends: Option[(ElkConnectableShape, ElkConnectableShape)] = sides.get(e.id) match
        case Some((from, to)) => ports.get((e.source, from)).zip(ports.get((e.target, to)))
        case None             => nodes.get(e.source).zip(nodes.get(e.target))
      ends.map: (from, to) =>
        val edge = ElkGraphUtil.createSimpleEdge(from, to)
        edge.setIdentifier(e.id.value)
        e.id -> edge
    Built(root, nodes, edges.toMap)
  end build

  private def add(
    s: Structure,
    node: SceneNode,
    parent: ElkNode,
    o: Options,
  ): Map[SceneId, ElkNode] =
    val n = ElkGraphUtil.createNode(parent)
    n.setIdentifier(node.id.value)
    n.setProperty(
      CoreOptions.PARTITIONING_PARTITION,
      Integer.valueOf(s.partition.getOrElse(node.id, BUSINESS)),
    )
    if node.isContainer then
      container(n, node.kind, o)
      node.children.foldLeft(Map(node.id -> n))((acc, child) => acc ++ add(s, child, n, o))
    else
      val (w, d) = footprint(node.kind)
      n.setDimensions(w * SCALE, d * SCALE)
      n.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS)
      Map(node.id -> n)

  end add

  // A grouping carries its name on a tab at the back; any other container in a strip at
  // its front, with its sculpture. ELK's top is the back of the plinth, its bottom the front.
  private def container(
    n: ElkNode,
    kind: NodeKind,
    o: Options,
  ): Unit =
    val pad      = o.containerPadding * SCALE
    val grouping = kind == NodeKind.Grouping
    val back     = if grouping then pad + 0.2 * SCALE else pad
    val front    = if grouping then pad else o.containerHeader * SCALE
    configure(n, o)
    n.setProperty(CoreOptions.PADDING, ElkPadding(back, pad, front, pad))
    n.setProperty(CoreOptions.NODE_SIZE_CONSTRAINTS, EnumSet.of(SizeConstraint.MINIMUM_SIZE))
    n.setProperty(
      CoreOptions.NODE_SIZE_MINIMUM,
      KVector((PLINTH_WIDTH + 0.8) * SCALE, PLINTH_DEPTH * SCALE + back + front),
    )
    n.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_SIDE)
    n.setProperty(CoreOptions.PORT_ALIGNMENT_DEFAULT, PortAlignment.CENTER)

  end container

  // A leaf's port sits exactly in the middle of its side; a container's is centred by ELK.
  private def port(
    n: ElkNode,
    side: PortSide,
    container: Boolean,
  ): ElkPort =
    val p = ElkGraphUtil.createPort(n)
    p.setDimensions(0, 0)
    p.setProperty(CoreOptions.PORT_SIDE, side)
    if !container then
      side match
        case PortSide.NORTH => p.setLocation(n.getWidth / 2, 0)
        case PortSide.SOUTH => p.setLocation(n.getWidth / 2, n.getHeight)
        case PortSide.WEST  => p.setLocation(0, n.getHeight / 2)
        case _              => p.setLocation(n.getWidth, n.getHeight / 2)
    p

  end port

  private def run(b: Built): Either[SceneError, Built] =
    Try(engine.layout(b.root, BasicProgressMonitor())).toEither
      .map(_ => b)
      .left
      .map(cause =>
        SceneError.LayoutFailed(s"ELK: ${cause.getClass.getSimpleName}: ${cause.getMessage}")
      )

  // ── reading the layout back ───────────────────────────────────────────────

  final private case class Rect(
    x: Double,
    y: Double,
    w: Double,
    h: Double):
    def bottom: Double = y + h
    def right: Double  = x + w

  // a node's top-left corner relative to the root
  private def absolute(n: ElkNode): (Double, Double) =
    Iterator
      .iterate(Option(n))(_.flatMap(p => Option(p.getParent)))
      .takeWhile(_.exists(p => Option(p.getParent).isDefined))
      .flatten
      .foldLeft((0.0, 0.0)) { case ((x, y), p) => (x + p.getX, y + p.getY) }

  private def rect(n: ElkNode): Rect =
    val (x, y) = absolute(n)
    Rect(x, y, n.getWidth, n.getHeight)

  // A row wider than `columns` plinths, folded. ELK puts every element of a layer in one
  // row: thirteen unconnected goals, or ten work packages with five deliverables each,
  // become a line fifty plinths long. In such a row the leaves, the elements with at most
  // one connection, are taken in their order along the row and each gets an invisible
  // edge to the one `columns` further, which puts them in successive rows.
  private def foldsOf(
    s: Structure,
    b: Built,
    o: Options,
  ): List[(SceneId, SceneId)] =
    val columns                    =
      if o.maxPerRow > 0 then o.maxPerRow
      else math.max(LOWEST_COLUMN, math.ceil(math.sqrt(s.nodes.size.toDouble)).toInt)
    val degree                     =
      s.edges.flatMap(e => List(e.source, e.target)).groupMapReduce(identity)(_ => 1)(_ + _)
    def leaf(id: SceneId): Boolean = !s.isContainer(id) && degree.getOrElse(id, 0) <= 1
    s.nodes
      .flatMap(n => b.rectOf(n.id).map(r => (n.id, r)))
      .groupBy((id, r) => (s.parentOf.get(id), math.rint(r.y)))
      .values
      .filter(_.size > columns)
      .toList
      .flatMap: row =>
        val leaves = row.filter((id, _) => leaf(id)).sortBy((_, r) => r.x).map(_._1)
        if leaves.size > columns then leaves.zip(leaves.drop(columns)) else Nil

  end foldsOf

  // ELK's y runs down the page, which is towards the viewer.
  private def sidesOf(s: Structure, b: Built): Map[SceneId, (PortSide, PortSide)] =
    s.edges
      .flatMap: e =>
        b.rectOf(e.source).zip(b.rectOf(e.target)).map: (a, c) =>
          val sides =
            if a.bottom <= c.y then (PortSide.SOUTH, PortSide.NORTH)
            else if c.bottom <= a.y then (PortSide.NORTH, PortSide.SOUTH)
            else if a.right <= c.x then (PortSide.EAST, PortSide.WEST)
            else if c.right <= a.x then (PortSide.WEST, PortSide.EAST)
            else if a.y + a.h / 2 <= c.y + c.h / 2 then (PortSide.SOUTH, PortSide.NORTH)
            else (PortSide.NORTH, PortSide.SOUTH)
          e.id -> sides
      .toMap

  private def round(v: Double): Double = math.rint(v * ROUNDING) / ROUNDING

  private def portName(side: PortSide): PortName = side match
    case PortSide.NORTH => PortName.North
    case PortSide.SOUTH => PortName.South
    case PortSide.WEST  => PortName.West
    case _              => PortName.East

  private def scene(
    graph: SceneGraph,
    s: Structure,
    sides: Map[SceneId, (PortSide, PortSide)],
    b: Built,
  ): Scene =
    val (w, h)                               = (b.root.getWidth, b.root.getHeight)
    // ELK's top-left origin, y down → the scene's centred origin, y away from the viewer
    def at(x: Double, y: Double): ScenePoint =
      ScenePoint(round((x - w / 2) / SCALE), round((h / 2 - y) / SCALE))

    val elements = s.nodes.flatMap: n =>
      b.rectOf(n.id).map: r =>
        val c = at(r.x + r.w / 2, r.y + r.h / 2)
        SceneElement(
          key = n.id,
          shape = SceneNames.of(n.kind),
          name = n.label,
          x = c.x,
          y = c.y,
          w = round(r.w / SCALE),
          d = round(r.h / SCALE),
          z = round((s.chain(n.id).size - 1) * PLINTH_HEIGHT),
          parent = s.parentOf.get(n.id),
          container = n.isContainer,
        )

    val byKey   = elements.map(e => e.key -> e).toMap
    val plinths = elements.filterNot(_.container).map(e => OrthogonalRouter.Box(e.x, e.y, e.w, e.d))
    def good(points: List[ScenePoint]): Boolean =
      points.zip(points.drop(1)).forall: (p, q) =>
        (math.abs(p.x - q.x) < 2 * ON_THE_LINE || math.abs(p.y - q.y) < 2 * ON_THE_LINE) &&
        !plinths.exists(_.crosses(p.x, p.y, q.x, q.y))

    val relationships = s.edges.flatMap: e =>
      for
        edge       <- b.edges.get(e.id)
        (from, to) <- sides.get(e.id)
        source     <- byKey.get(e.source)
        target     <- byKey.get(e.target)
      yield
        val (ox, oy) = Option(edge.getContainingNode).map(absolute).getOrElse((0.0, 0.0))
        val bends    = edge.getSections.asScala.headOption.toList
          .flatMap(_.getBendPoints.asScala)
          .map(p => at(ox + p.getX, oy + p.getY))
        SceneRelationship(
          key = e.id,
          source = e.source,
          target = e.target,
          kind = SceneNames.of(e.relationship),
          sourcePort = portName(from),
          targetPort = portName(to),
          bends = route(source, from, bends, target, to, good, plinths),
        )

    Scene(graph.name, round(w / SCALE), round(h / SCALE), elements, relationships)
  end scene

  // ── routes ────────────────────────────────────────────────────────────────

  // ELK's route, made to meet the ports exactly; or, where ELK left a connector between
  // two containers unfinished, a route of our own around the plinths.
  private def route(
    source: SceneElement,
    from: PortSide,
    bends: List[ScenePoint],
    target: SceneElement,
    to: PortSide,
    good: List[ScenePoint] => Boolean,
    plinths: List[OrthogonalRouter.Box],
  ): List[ScenePoint] =
    val (a, z)  = (portOf(source, from), portOf(target, to))
    val snapped = snap(a, from, bends, z, to)
    if good(a :: snapped ::: List(z)) then snapped
    else
      OrthogonalRouter
        .route((a.x, a.y), heading(from), (z.x, z.y), heading(to), plinths)
        .map(_.map((x, y) => ScenePoint(round(x), round(y))))
        .getOrElse(snapped)

  end route

  private def heading(side: PortSide): OrthogonalRouter.Heading = side match
    case PortSide.NORTH => (0, 1)
    case PortSide.SOUTH => (0, -1)
    case PortSide.WEST  => (-1, 0)
    case _              => (1, 0)

  // the middle of a plinth side, where the builder puts the port
  private def portOf(e: SceneElement, side: PortSide): ScenePoint = side match
    case PortSide.NORTH => ScenePoint(e.x, round(e.y + e.d / 2))
    case PortSide.SOUTH => ScenePoint(e.x, round(e.y - e.d / 2))
    case PortSide.WEST  => ScenePoint(round(e.x - e.w / 2), e.y)
    case _              => ScenePoint(round(e.x + e.w / 2), e.y)

  private def vertical(side: PortSide): Boolean = side == PortSide.NORTH || side == PortSide.SOUTH

  // ELK puts a container's port where it likes on its side, a few hundredths off the
  // middle; moving the first and last bend along with the end keeps every segment straight.
  private def snap(
    a: ScenePoint,
    from: PortSide,
    bends: List[ScenePoint],
    z: ScenePoint,
    to: PortSide,
  ): List[ScenePoint] =
    def align(
      p: ScenePoint,
      port: ScenePoint,
      side: PortSide,
    ): ScenePoint =
      if vertical(side) then p.copy(x = port.x) else p.copy(y = port.y)
    bends match
      case Nil           =>
        if vertical(from) && vertical(to) && math.abs(a.x - z.x) > ON_THE_LINE then
          val mid = round((a.y + z.y) / 2)
          List(ScenePoint(a.x, mid), ScenePoint(z.x, mid))
        else if !vertical(from) && !vertical(to) && math.abs(a.y - z.y) > ON_THE_LINE then
          val mid = round((a.x + z.x) / 2)
          List(ScenePoint(mid, a.y), ScenePoint(mid, z.y))
        else if vertical(from) != vertical(to) then
          List(if vertical(from) then ScenePoint(a.x, z.y) else ScenePoint(z.x, a.y))
        else Nil
      case single :: Nil => List(align(align(single, a, from), z, to))
      case first :: rest =>
        rest.lastOption.fold(List(align(first, a, from))): last =>
          align(first, a, from) :: rest.dropRight(1) ::: List(align(last, z, to))
    end match
  end snap

end SceneLayout
