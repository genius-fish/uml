package fish.genius.uml.scene

import java.util.EnumSet

import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.eclipse.elk.alg.layered.options.{
  LayeredMetaDataProvider, LayeredOptions, OrderingStrategy,
}
import org.eclipse.elk.core.RecursiveGraphLayoutEngine
import org.eclipse.elk.core.data.LayoutMetaDataService
import org.eclipse.elk.core.math.{ElkPadding, KVector}
import org.eclipse.elk.core.options.*
import org.eclipse.elk.core.util.BasicProgressMonitor
import org.eclipse.elk.graph.*
import org.eclipse.elk.graph.util.ElkGraphUtil

// One ELK pass over a structure, and what is read back from it. ELK is a mutable Java
// API; it stays inside this file.
private[scene] object ElkPass:

  // ELK thinks in pixels; one scene unit is a hundred of them.
  val SCALE: Double = 100.0

  private val ALGORITHM                = "org.eclipse.elk.layered"
  private val SEED                     = 1
  private val EDGE_NODE_BETWEEN_LAYERS = 0.4
  private val EDGE_EDGE_BETWEEN_LAYERS = 0.25
  private val EDGE_NODE                = 0.35
  private val EDGE_EDGE                = 0.25
  private val TAB_DEPTH                = 0.2
  private val CONTAINER_MARGIN         = 0.8
  private val LOWEST_COLUMN            = 4

  final case class Rect(
    x: Double,
    y: Double,
    w: Double,
    h: Double):
    def bottom: Double = y + h
    def right: Double  = x + w

  final case class Built(
    root: ElkNode,
    nodes: Map[SceneId, ElkNode],
    edges: Map[SceneId, ElkEdge]):
    def width: Double                     = root.getWidth / SCALE
    def depth: Double                     = root.getHeight / SCALE
    def rectOf(id: SceneId): Option[Rect] = nodes.get(id).map(rect)

    // ELK's top-left origin, y down → the scene's centred origin, y away from the viewer
    def toScene(x: Double, y: Double): ScenePoint =
      ScenePoint(
        SceneLayout.round((x - root.getWidth / 2) / SCALE),
        SceneLayout.round((root.getHeight / 2 - y) / SCALE),
      )

    def bendsOf(edge: ElkEdge): List[ScenePoint] =
      val (offsetX, offsetY) = Option(edge.getContainingNode).map(absolute).getOrElse((0.0, 0.0))
      edge.getSections.asScala.headOption.toList
        .flatMap(_.getBendPoints.asScala)
        .map(point => toScene(offsetX + point.getX, offsetY + point.getY))

  end Built

  private lazy val engine: RecursiveGraphLayoutEngine =
    LayoutMetaDataService.getInstance().registerLayoutMetaDataProviders(LayeredMetaDataProvider())
    RecursiveGraphLayoutEngine()

  def run(
    structure: SceneStructure,
    sides: Map[SceneId, Sides],
    folds: List[(SceneId, SceneId)],
    options: SceneLayout.Options,
    modelOrder: Boolean,
  ): Either[SceneError, Built] =
    val built = build(structure, sides, folds, options, modelOrder)
    Try(engine.layout(built.root, BasicProgressMonitor())).toEither
      .map(_ => built)
      .left
      .map(SceneError.LayoutFailed(_))

  // A row wider than `columns` plinths, folded. ELK puts every element of a layer in one
  // row: thirteen unconnected goals, or ten work packages with five deliverables each,
  // become a line fifty plinths long. In such a row the leaves, the elements with at most
  // one connection, are taken in their order along the row and each gets an invisible
  // edge to the one `columns` further, which puts them in successive rows.
  def foldsOf(
    structure: SceneStructure,
    built: Built,
    options: SceneLayout.Options,
  ): List[(SceneId, SceneId)] =
    val columns                      =
      if options.maxPerRow > 0 then options.maxPerRow
      else math.max(LOWEST_COLUMN, math.ceil(math.sqrt(structure.nodes.size.toDouble)).toInt)
    val degree                       =
      structure.edges.flatMap(edge => List(edge.source, edge.target)).groupMapReduce(identity)(_ =>
        1
      )(_ + _)
    def isLeaf(id: SceneId): Boolean = !structure.isContainer(id) && degree.getOrElse(id, 0) <= 1
    structure.nodes
      .flatMap(node => built.rectOf(node.id).map(rect => (node.id, rect)))
      .groupBy((id, rect) => (structure.parentOf.get(id), math.rint(rect.y)))
      .values
      .filter(_.size > columns)
      .toList
      .flatMap: row =>
        val leaves = row.filter((id, _) => isLeaf(id)).sortBy((_, rect) => rect.x).map(_._1)
        if leaves.size > columns then leaves.zip(leaves.drop(columns)) else Nil

  end foldsOf

  // ELK's y runs down the page, which is towards the viewer.
  def sidesOf(structure: SceneStructure, built: Built): Map[SceneId, Sides] =
    structure.edges
      .flatMap: edge =>
        built.rectOf(edge.source).zip(built.rectOf(edge.target)).map: (source, target) =>
          val sides =
            if source.bottom <= target.y then Sides(PortName.South, PortName.North)
            else if target.bottom <= source.y then Sides(PortName.North, PortName.South)
            else if source.right <= target.x then Sides(PortName.East, PortName.West)
            else if target.right <= source.x then Sides(PortName.West, PortName.East)
            else if source.y + source.h / 2 <= target.y + target.h / 2 then
              Sides(PortName.South, PortName.North)
            else Sides(PortName.North, PortName.South)
          edge.id -> sides
      .toMap

  private def configure(
    elkNode: ElkNode,
    options: SceneLayout.Options,
    modelOrder: Boolean,
  ): Unit =
    elkNode.setProperty(CoreOptions.DIRECTION, Direction.DOWN)
    // A fixed seed and the model's own order make the same graph give the same scene;
    // ELK 0.9.1 can fail on model order with hierarchy, so a caller may switch it off.
    elkNode.setProperty(CoreOptions.RANDOM_SEED, Integer.valueOf(SEED))
    if modelOrder then
      elkNode.setProperty(
        LayeredOptions.CONSIDER_MODEL_ORDER_STRATEGY,
        OrderingStrategy.NODES_AND_EDGES,
      )
    elkNode.setProperty(CoreOptions.EDGE_ROUTING, EdgeRouting.ORTHOGONAL)
    elkNode.setProperty(CoreOptions.PARTITIONING_ACTIVATE, java.lang.Boolean.TRUE)
    elkNode.setProperty(CoreOptions.SPACING_NODE_NODE, Double.box(options.nodeSpacing * SCALE))
    elkNode.setProperty(
      LayeredOptions.SPACING_NODE_NODE_BETWEEN_LAYERS,
      Double.box(options.layerSpacing * SCALE),
    )
    elkNode.setProperty(
      LayeredOptions.SPACING_EDGE_NODE_BETWEEN_LAYERS,
      Double.box(EDGE_NODE_BETWEEN_LAYERS * SCALE),
    )
    elkNode.setProperty(
      LayeredOptions.SPACING_EDGE_EDGE_BETWEEN_LAYERS,
      Double.box(EDGE_EDGE_BETWEEN_LAYERS * SCALE),
    )
    elkNode.setProperty(CoreOptions.SPACING_EDGE_NODE, Double.box(EDGE_NODE * SCALE))
    elkNode.setProperty(CoreOptions.SPACING_EDGE_EDGE, Double.box(EDGE_EDGE * SCALE))

  end configure

  private def footprint(kind: NodeKind): (width: Double, depth: Double) = kind match
    case NodeKind.AndJunction | NodeKind.OrJunction =>
      (width = SceneLayout.JUNCTION_SIZE, depth = SceneLayout.JUNCTION_SIZE)
    case NodeKind.Element(_) | NodeKind.Grouping    =>
      (width = SceneLayout.PLINTH_WIDTH, depth = SceneLayout.PLINTH_DEPTH)

  private def build(
    structure: SceneStructure,
    sides: Map[SceneId, Sides],
    folds: List[(SceneId, SceneId)],
    options: SceneLayout.Options,
    modelOrder: Boolean,
  ): Built =
    val root   = ElkGraphUtil.createGraph()
    root.setProperty(CoreOptions.ALGORITHM, ALGORITHM)
    root.setProperty(CoreOptions.HIERARCHY_HANDLING, HierarchyHandling.INCLUDE_CHILDREN)
    configure(root, options, modelOrder)
    val nodes  = structure.roots.foldLeft(Map.empty[SceneId, ElkNode])((acc, node) =>
      acc ++ add(structure, node, root, options, modelOrder)
    )
    // the invisible edges that fold a row too wide into several (see foldsOf)
    for
      (from, to) <- folds
      fromNode   <- nodes.get(from)
      toNode     <- nodes.get(to)
    do ElkGraphUtil.createSimpleEdge(fromNode, toNode)
    val wanted = sides.toList.sortBy(_._1).flatMap: (id, ends) =>
      structure.edges.find(_.id == id).toList.flatMap(edge =>
        List(edge.source -> ends.from, edge.target -> ends.to)
      )
    val ports  = wanted.distinct
      .flatMap((id, side) =>
        nodes.get(id).map(elkNode => (id, side) -> port(elkNode, side, structure.isContainer(id)))
      )
      .toMap
    val edges  = structure.edges.flatMap: edge =>
      val ends: Option[(ElkConnectableShape, ElkConnectableShape)] = sides.get(edge.id) match
        case Some(laid) => ports.get((edge.source, laid.from)).zip(ports.get((edge.target, laid.to)))
        case None => nodes.get(edge.source).zip(nodes.get(edge.target))
      ends.map: (from, to) =>
        val elkEdge = ElkGraphUtil.createSimpleEdge(from, to)
        elkEdge.setIdentifier(edge.id.value)
        edge.id -> elkEdge
    Built(root, nodes, edges.toMap)
  end build

  private def add(
    structure: SceneStructure,
    node: SceneNode,
    parent: ElkNode,
    options: SceneLayout.Options,
    modelOrder: Boolean,
  ): Map[SceneId, ElkNode] =
    val elkNode   = ElkGraphUtil.createNode(parent)
    val partition = structure.partition.getOrElse(node.id, SceneStructure.BUSINESS)
    elkNode.setIdentifier(node.id.value)
    elkNode.setProperty(CoreOptions.PARTITIONING_PARTITION, Integer.valueOf(partition))
    if node.isContainer then
      container(elkNode, node.kind, options, modelOrder)
      node.children.foldLeft(Map(node.id -> elkNode))((acc, child) =>
        acc ++ add(structure, child, elkNode, options, modelOrder)
      )
    else
      val size = footprint(node.kind)
      elkNode.setDimensions(size.width * SCALE, size.depth * SCALE)
      elkNode.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS)
      Map(node.id -> elkNode)

  end add

  // A grouping carries its name on a tab at the back; any other container in a strip at
  // its front, with its sculpture. ELK's top is the back of the plinth, its bottom the front.
  private def container(
    elkNode: ElkNode,
    kind: NodeKind,
    options: SceneLayout.Options,
    modelOrder: Boolean,
  ): Unit =
    val padding    = options.containerPadding * SCALE
    val isGrouping = kind == NodeKind.Grouping
    val back       = if isGrouping then padding + TAB_DEPTH * SCALE else padding
    val front      = if isGrouping then padding else options.containerHeader * SCALE
    configure(elkNode, options, modelOrder)
    elkNode.setProperty(CoreOptions.PADDING, ElkPadding(back, padding, front, padding))
    elkNode.setProperty(CoreOptions.NODE_SIZE_CONSTRAINTS, EnumSet.of(SizeConstraint.MINIMUM_SIZE))
    elkNode.setProperty(
      CoreOptions.NODE_SIZE_MINIMUM,
      KVector(
        (SceneLayout.PLINTH_WIDTH + CONTAINER_MARGIN) * SCALE,
        SceneLayout.PLINTH_DEPTH * SCALE + back + front,
      ),
    )
    elkNode.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_SIDE)
    elkNode.setProperty(CoreOptions.PORT_ALIGNMENT_DEFAULT, PortAlignment.CENTER)

  end container

  // A leaf's port sits exactly in the middle of its side; a container's is centred by ELK.
  private def port(
    elkNode: ElkNode,
    side: PortName,
    isContainer: Boolean,
  ): ElkPort =
    val elkPort = ElkGraphUtil.createPort(elkNode)
    elkPort.setDimensions(0, 0)
    elkPort.setProperty(CoreOptions.PORT_SIDE, portSide(side))
    if !isContainer then
      side match
        case PortName.North => elkPort.setLocation(elkNode.getWidth / 2, 0)
        case PortName.South => elkPort.setLocation(elkNode.getWidth / 2, elkNode.getHeight)
        case PortName.West  => elkPort.setLocation(0, elkNode.getHeight / 2)
        case PortName.East  => elkPort.setLocation(elkNode.getWidth, elkNode.getHeight / 2)
    elkPort

  end port

  private def portSide(side: PortName): PortSide = side match
    case PortName.North => PortSide.NORTH
    case PortName.South => PortSide.SOUTH
    case PortName.West  => PortSide.WEST
    case PortName.East  => PortSide.EAST

  // a node's top-left corner relative to the root
  private def absolute(elkNode: ElkNode): (Double, Double) =
    Iterator
      .iterate(Option(elkNode))(_.flatMap(parent => Option(parent.getParent)))
      .takeWhile(_.exists(parent => Option(parent.getParent).isDefined))
      .flatten
      .foldLeft((0.0, 0.0)) { case ((x, y), parent) => (x + parent.getX, y + parent.getY) }

  private def rect(elkNode: ElkNode): Rect =
    val (x, y) = absolute(elkNode)
    Rect(x, y, elkNode.getWidth, elkNode.getHeight)

end ElkPass
