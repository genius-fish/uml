package fish.genius.uml.scene

import java.util.EnumSet

import scala.jdk.CollectionConverters.*
import scala.util.Try

import org.eclipse.elk.alg.layered.options.*
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
    edges: Map[SceneId, ElkEdge],
    reserves: Map[SceneId, PaneLayout.Reserve] = Map.empty,
    reversed: Set[SceneId] = Set.empty):
    def width: Double                     = root.getWidth / SCALE
    def depth: Double                     = root.getHeight / SCALE
    def rectOf(id: SceneId): Option[Rect] = nodes.get(id).map(rect)

    // the plinth itself: the node, less the floor kept for its pane
    def plinthOf(id: SceneId): Option[Rect] =
      rectOf(id).map: node =>
        reserves.get(id).fold(node): r =>
          Rect(
            node.x + r.left * SCALE,
            node.y + r.back * SCALE,
            SceneLayout.PLINTH_WIDTH * SCALE,
            SceneLayout.PLINTH_DEPTH * SCALE,
          )

    // ELK's top-left origin, y down → the scene's centred origin, y away from the viewer
    def toScene(x: Double, y: Double): ScenePoint =
      ScenePoint(
        SceneLayout.round((x - root.getWidth / 2) / SCALE),
        SceneLayout.round((root.getHeight / 2 - y) / SCALE),
      )

    // where a connector leaves and arrives, as ELK placed its own ports
    def endsOf(edge: ElkEdge): Option[(ScenePoint, ScenePoint)] =
      val (offsetX, offsetY) = Option(edge.getContainingNode).map(absolute).getOrElse((0.0, 0.0))
      edge.getSections.asScala.headOption.map: section =>
        (
          toScene(offsetX + section.getStartX, offsetY + section.getStartY),
          toScene(offsetX + section.getEndX, offsetY + section.getEndY),
        )

    // the room ELK kept for a connector's label, given the label's text
    def labelOf(edge: ElkEdge): Option[String => SceneLabel] =
      val (offsetX, offsetY) = Option(edge.getContainingNode).map(absolute).getOrElse((0.0, 0.0))
      edge.getLabels.asScala.headOption.map: label =>
        val centre = toScene(
          offsetX + label.getX + label.getWidth / 2,
          offsetY + label.getY + label.getHeight / 2,
        )
        text =>
          SceneLabel(
            text,
            centre.x,
            centre.y,
            SceneLayout.round(label.getWidth / SCALE),
            SceneLayout.round(label.getHeight / SCALE),
          )

    end labelOf

    def bendsOf(edge: ElkEdge): List[ScenePoint] =
      val (offsetX, offsetY) = Option(edge.getContainingNode).map(absolute).getOrElse((0.0, 0.0))
      edge.getSections.asScala.headOption.toList
        .flatMap(_.getBendPoints.asScala)
        .map(point => toScene(offsetX + point.getX, offsetY + point.getY))

  end Built

  // the layered algorithm, registered once
  private lazy val registered: Unit =
    LayoutMetaDataService.getInstance().registerLayoutMetaDataProviders(LayeredMetaDataProvider())

  def run(
    structure: SceneStructure,
    sides: Map[SceneId, Sides],
    folds: List[(SceneId, SceneId)],
    options: SceneLayout.Options,
    modelOrder: Boolean,
  ): Either[SceneError, Built] =
    val built = build(structure, sides, folds, options, modelOrder)
    // one layout at a time: ELK's compaction (network simplex placement, edge-length
    // post-compaction) shares state between layouts that run at the same time, which then
    // come out differently, so the same graph would not always give the same scene
    registered
    Try(ElkPass.synchronized(RecursiveGraphLayoutEngine().layout(built.root, BasicProgressMonitor())))
      .toEither
      .map(_ => built)
      .left
      .map(SceneError.LayoutFailed(_))

  end run

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
    val placed                       =
      structure.nodes.flatMap(node => built.rectOf(node.id).map(rect => (node.id, rect)))
    val byCount                      = placed
      .groupBy((id, rect) => (structure.parentOf.get(id), math.rint(rect.y)))
      .values
      .filter(_.size > columns)
      .toList
      .flatMap: row =>
        val leaves = row.filter((id, _) => isLeaf(id)).sortBy((_, rect) => rect.x).map(_._1)
        if leaves.size > columns then leaves.zip(leaves.drop(columns)) else Nil
    // A row wider than the options' rowWidth (a page) folds too: the nodes of one parent
    // that stand side by side (their heights overlap), as many of its leaves on the row
    // as leave it within the width, the rest each under the one that many before it.
    val byWidth                      =
      if options.rowWidth <= 0 then Nil
      else
        val budget = options.rowWidth * SCALE
        val gap    = options.nodeSpacing * SCALE
        placed
          .groupBy((id, _) => structure.parentOf.get(id))
          .values
          .toList
          .flatMap: siblings =>
            siblings
              .sortBy(_._2.y)
              .foldLeft(List.empty[List[(SceneId, Rect)]]):
                case (row :: rows, node) if row.exists(_._2.bottom > node._2.y) =>
                  (node :: row) :: rows
                case (rows, node) => List(node) :: rows
          .flatMap: row =>
            val wide            = row.map(_._2.w).sum + gap * (row.size - 1)
            val (leaves, fixed) = row.partition((id, _) => isLeaf(id))
            if wide <= budget || leaves.isEmpty then Nil
            else
              val room   = budget - fixed.map(_._2.w + gap).sum
              val each   = leaves.map(_._2.w).sum / leaves.size + gap
              val fit    = math.max(1, (room / each).toInt)
              val sorted = leaves.sortBy(_._2.x).map(_._1)
              if sorted.size > fit then sorted.zip(sorted.drop(fit)) else Nil
    (byCount ++ byWidth).distinct

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
    if options.compact then
      elkNode.setProperty(
        LayeredOptions.NODE_PLACEMENT_STRATEGY,
        NodePlacementStrategy.NETWORK_SIMPLEX,
      )
      elkNode.setProperty(
        LayeredOptions.COMPACTION_POST_COMPACTION_STRATEGY,
        GraphCompactionStrategy.EDGE_LENGTH,
      )

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
    val kept   = PaneLayout.reserves(structure, sides, options.panes)
    val nodes  = structure.roots.foldLeft(Map.empty[SceneId, ElkNode])((acc, node) =>
      acc ++ add(structure, node, root, options, modelOrder, kept)
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
    // one port per side, in its middle; spread, one per connector end, placed by ELK
    val ports  =
      if options.spreadPorts then Map.empty
      else
        wanted.distinct
          .flatMap((id, side) =>
            nodes.get(id).map(elkNode =>
              (id, side) -> port(elkNode, side, structure.isContainer(id), kept.get(id))
            )
          )
          .toMap
    def own(id: SceneId, side: PortName): Option[ElkPort] =
      nodes.get(id).map(elkNode => port(elkNode, side, isContainer = true, None))
    val edges                                             = structure.edges.flatMap: edge =>
      val ends: Option[(ElkConnectableShape, ElkConnectableShape)] = sides.get(edge.id) match
        // an edge that runs up, against the layers, is handed to ELK the other way
        // round, so ELK routes it straight instead of looping round its inverted ports
        case Some(laid) if options.spreadPorts && upward(laid) =>
          own(edge.target, laid.to).zip(own(edge.source, laid.from))
        case Some(laid) if options.spreadPorts                 =>
          own(edge.source, laid.from).zip(own(edge.target, laid.to))
        case Some(laid) => ports.get((edge.source, laid.from)).zip(ports.get((edge.target, laid.to)))
        case None => nodes.get(edge.source).zip(nodes.get(edge.target))
      ends.map: (from, to) =>
        val elkEdge = ElkGraphUtil.createSimpleEdge(from, to)
        elkEdge.setIdentifier(edge.id.value)
        for
          text   <- edge.label
          (w, d) <- options.labelSize(text)
        do
          val label = ElkGraphUtil.createLabel(text, elkEdge)
          label.setDimensions(w * SCALE, d * SCALE)
          label.setProperty(CoreOptions.EDGE_LABELS_PLACEMENT, EdgeLabelPlacement.CENTER)
          label.setProperty(CoreOptions.EDGE_LABELS_INLINE, java.lang.Boolean.TRUE)
        edge.id -> elkEdge
    val reversed                                          = Option
      .when(options.spreadPorts)(
        structure.edges.filter(e => sides.get(e.id).exists(upward)).map(_.id).toSet
      )
      .getOrElse(Set.empty)
    Built(root, nodes, edges.toMap, kept, reversed)
  end build

  private def add(
    structure: SceneStructure,
    node: SceneNode,
    parent: ElkNode,
    options: SceneLayout.Options,
    modelOrder: Boolean,
    kept: Map[SceneId, PaneLayout.Reserve],
  ): Map[SceneId, ElkNode] =
    val elkNode   = ElkGraphUtil.createNode(parent)
    val partition = structure.partition.getOrElse(node.id, SceneStructure.BUSINESS)
    elkNode.setIdentifier(node.id.value)
    elkNode.setProperty(CoreOptions.PARTITIONING_PARTITION, Integer.valueOf(partition))
    if node.isContainer then
      container(elkNode, node, options, modelOrder)
      node.children.foldLeft(Map(node.id -> elkNode))((acc, child) =>
        acc ++ add(structure, child, elkNode, options, modelOrder, kept)
      )
    else
      // a pane's floor makes the node larger than its plinth
      val size = kept.get(node.id) match
        case Some(r) => (width = r.w, depth = r.d)
        case None    =>
          options.footprint(node).fold(footprint(node.kind))((w, d) => (width = w, depth = d))
      elkNode.setDimensions(size.width * SCALE, size.depth * SCALE)
      // spread, ELK places a leaf's ports along their sides; else they sit in the middle
      if options.spreadPorts && !kept.contains(node.id) then
        elkNode.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_SIDE)
      else elkNode.setProperty(CoreOptions.PORT_CONSTRAINTS, PortConstraints.FIXED_POS)
      Map(node.id -> elkNode)
    end if

  end add

  // A grouping carries its name on a tab at the back; any other container in a strip at
  // its front, with its sculpture, or at its back when the options say so (a flat
  // diagram). ELK's top is the back of the plinth, its bottom the front.
  private def container(
    elkNode: ElkNode,
    node: SceneNode,
    options: SceneLayout.Options,
    modelOrder: Boolean,
  ): Unit =
    val padding    = options.containerPadding * SCALE
    val isGrouping = node.kind == NodeKind.Grouping
    val header     = options.containerHeader * SCALE
    // a flat diagram heads a grouping like any container, with its name at the top
    val back       =
      if options.headerAtBack then header
      else if isGrouping then padding + TAB_DEPTH * SCALE
      else padding
    val front      = if isGrouping || options.headerAtBack then padding else header
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
    // spread, a container's own ports (the ones ELK makes where an edge crosses its
    // edge) go on whichever side faces where the edge goes; else on fixed sides
    elkNode.setProperty(
      CoreOptions.PORT_CONSTRAINTS,
      if options.spreadPorts then PortConstraints.FREE else PortConstraints.FIXED_SIDE,
    )
    elkNode.setProperty(CoreOptions.PORT_ALIGNMENT_DEFAULT, PortAlignment.CENTER)

  end container

  // A leaf's port sits exactly in the middle of its side; a container's is centred by ELK.
  // With a pane's floor beside the plinth, the port stays on the node's edge, in line with
  // the middle of the plinth's side, and the route runs straight on to the plinth.
  private def port(
    elkNode: ElkNode,
    side: PortName,
    isContainer: Boolean,
    kept: Option[PaneLayout.Reserve],
  ): ElkPort =
    val elkPort = ElkGraphUtil.createPort(elkNode)
    elkPort.setDimensions(0, 0)
    elkPort.setProperty(CoreOptions.PORT_SIDE, portSide(side))
    if !isContainer then
      val across =
        kept.fold(elkNode.getWidth / 2)(r => (r.left + SceneLayout.PLINTH_WIDTH / 2) * SCALE)
      val along  =
        kept.fold(elkNode.getHeight / 2)(r => (r.back + SceneLayout.PLINTH_DEPTH / 2) * SCALE)
      side match
        case PortName.North => elkPort.setLocation(across, 0)
        case PortName.South => elkPort.setLocation(across, elkNode.getHeight)
        case PortName.West  => elkPort.setLocation(0, along)
        case PortName.East  => elkPort.setLocation(elkNode.getWidth, along)
    elkPort

  end port

  // an edge leaving its source's north side for its target's south: up the page
  private def upward(laid: Sides): Boolean = laid.from == PortName.North &&
    laid.to == PortName.South

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
