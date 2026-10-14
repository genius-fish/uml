package fish.genius.uml.diagram

import scala.collection.mutable

import fish.genius.uml.diagram.Diagram.Line
import fish.genius.uml.dsl.archimate.ShapeType
import fish.genius.uml.scene.Scene

// A process drawn as an activity diagram with swimlanes, in the look of the ArchiMate
// diagrams (Diagram), so a dossier sets its processes in one style with its views. The
// flow runs from top to bottom, each lane a column with its name in a header. A task is
// a rounded box in the business colour, with an icon for its kind and the systems that
// support it under its name; an event is a circle (thin at the start, double in
// between, thick at the end) with its trigger inside and its name beside it; a gateway
// is a diamond with its kind inside (X, +, O) and its question beside it. A flow is a
// fine line with an arrowhead, its condition at its start; a branch leaves a gateway
// from the corner that faces it, and a loop back goes round the right of what it skips.
//
// The layout is the classic layered one: the flows that close a loop are set aside,
// every node is placed on the row its longest path from a start gives it, and in its
// lane on a track of its own when its row holds more than one node there, ordered under
// the nodes it comes from. Units are centimetres, x to the right and y up, as in
// Diagram, and the three writers draw the result.
object Activity:

  // a lane: its id, its name, and the shape of whoever performs it (an actor, a role)
  final case class Lane(
    id: String,
    name: String,
    shape: Option[ShapeType] = None)

  enum Task derives CanEqual:
    case Plain, User, Service, Manual, Script, Rule, Call, Sub

  enum Trigger derives CanEqual:
    case Plain, Message, Timer, Error, Signal, Terminate

  enum Gate derives CanEqual:
    case Exclusive, Parallel, Inclusive, EventBased

  enum Kind derives CanEqual:
    case Work(task: Task)
    case Start(trigger: Trigger)
    case Intermediate(trigger: Trigger)
    case End(trigger: Trigger)
    case Gateway(gate: Gate)

  // a node: what it says, what it is, its lane, the line under its name (the systems
  // that support it) and the notes beside it
  final case class Node(
    id: String,
    label: String,
    kind: Kind,
    lane: Option[String] = None,
    detail: Option[String] = None,
    notes: List[String] = Nil)

  // a flow from one node to another, with its condition; the default one out of a
  // gateway has a short stroke across its start
  final case class Flow(
    from: String,
    to: String,
    label: Option[String] = None,
    default: Boolean = false)

  final case class Graph(
    name: String,
    lanes: List[Lane],
    nodes: List[Node],
    flows: List[Flow])

  final case class Options(
    style: Diagram.Style = Diagram.Style(),
    pageWidth: Double = 13.0,
    taskWidth: Double = 2.8)

  def draw(graph: Graph, options: Options = Options()): Diagram.Drawing =
    val measure   = Measure(options.style.typeface)
    // the widest tasks that keep within the page, or the narrowest drawing
    val tries     = (options.taskWidth :: TASK_WIDTHS.filter(_ < options.taskWidth))
      .map(w => Layout(graph, options, measure, w))
    val narrowest = tries.map(_.width).min
    val laid      = tries
      .find(_.width <= options.pageWidth)
      .orElse(tries.find(_.width <= narrowest + 0.01))
      .getOrElse(tries.last)
    Diagram.Drawing(
      graph.name,
      laid.prims,
      Scene(graph.name, laid.width, laid.height, Nil, Nil),
      options.style,
      measure.family,
      laid.overflow,
    )

  end draw

  private val TASK_WIDTHS = List(2.4, 2.0)
  private val TASK_MIN_W  = 1.9
  private val TASK_MIN_H  = 0.8
  private val EVENT_R     = 0.2
  private val GATE        = 0.27        // half a gateway's diagonal
  private val BESIDE      = 0.1         // from an event or gateway to its name
  private val TRACK_GAP   = 0.4         // between two nodes side by side in a lane
  private val LANE_PAD    = 0.2         // inside a lane, left and right
  private val ROW_GAP     = 0.62        // between two rows
  private val FIRST       = 0.35        // from the lane headers to the first row
  private val LAST        = 0.3         // under the last row
  private val CHANNEL     = 0.3         // a loop back's way round, clear of what it passes
  private val CHANNEL_GAP = 0.16        // between two loops back side by side
  private val LOOP_IN     = ROW_GAP / 4 // a loop coming in from above, under the flows that do
  private val LABEL_W     = 2.4         // the widest a flow's condition is set
  private val NOTE_W      = 2.6         // the widest a note is set
  private val NOTE_GAP    = 0.28        // from a node to its note
  private val ICON        = 0.85        // a task's icon, against a box's in Diagram
  private val ROUNDED     = 0.16
  private val LANE_HEAD_W = 3.2         // the widest a lane's name is set
  private val BUSINESS    = "am-business"

  // the icon a task's kind is shown by, from the ArchiMate notation where one says it
  private def iconOf(task: Task): Option[ShapeType] = task match
    case Task.User    => Some(ShapeType.BusinessActor)
    case Task.Service => Some(ShapeType.ApplicationComponent)
    case Task.Script  => Some(ShapeType.TechnologyArtifact)
    case Task.Rule    => Some(ShapeType.MotivationPrinciple)
    case Task.Call    => Some(ShapeType.BusinessProcess)
    case _            => None

  // a trigger drawn in a unit square, lines in the edge colour
  private def glyph(trigger: Trigger): List[Prim] =
    def line(points: Pt*) = Prim.Poly(points.toList, closed = false)
    trigger match
      case Trigger.Plain     => Nil
      case Trigger.Message   =>
        List(
          Prim.Poly(
            List((0.12, 0.22), (0.88, 0.22), (0.88, 0.78), (0.12, 0.78)),
            closed = true,
            Ink.Paper,
          ),
          line((0.12, 0.78), (0.5, 0.46), (0.88, 0.78)),
        )
      case Trigger.Timer     =>
        List(Prim.Circle((0.5, 0.5), 0.4, Ink.Paper), line((0.5, 0.78), (0.5, 0.5), (0.72, 0.38)))
      case Trigger.Error     =>
        List(line((0.18, 0.15), (0.38, 0.72), (0.6, 0.38), (0.82, 0.85)))
      case Trigger.Signal    =>
        List(Prim.Poly(List((0.5, 0.86), (0.88, 0.18), (0.12, 0.18)), closed = true, Ink.Paper))
      case Trigger.Terminate => List(Prim.Circle((0.5, 0.5), 0.34, Ink.Line))
    end match

  end glyph

  // a gateway's kind drawn in a unit square
  private def mark(gate: Gate): List[Prim] =
    def line(points: Pt*) = Prim.Poly(points.toList, closed = false)
    gate match
      case Gate.Exclusive => List(line((0.28, 0.28), (0.72, 0.72)), line((0.28, 0.72), (0.72, 0.28)))
      case Gate.Parallel   => List(line((0.5, 0.2), (0.5, 0.8)), line((0.2, 0.5), (0.8, 0.5)))
      case Gate.Inclusive  => List(Prim.Circle((0.5, 0.5), 0.26))
      case Gate.EventBased =>
        List(
          Prim.Circle((0.5, 0.5), 0.3),
          Prim.Poly(
            List((0.5, 0.68), (0.67, 0.55), (0.6, 0.35), (0.4, 0.35), (0.33, 0.55)),
            closed = true,
          ),
        )

  // A node as it is set: its lines, and how far it reaches round the centre of its
  // shape (a task's box, an event's circle, a gateway's diamond): `left` and `right`
  // with its name and notes, `up` and `down`; `half` and `tall` are the shape's own half
  // width and half height, where the flows meet it.
  final private case class Sized(
    node: Node,
    lines: List[Line],
    notes: List[Line],
    half: Double,
    tall: Double,
    left: Double,
    right: Double,
    up: Double,
    down: Double,
    overflow: Int)

  final private class Layout(
    graph: Graph,
    options: Options,
    measure: Measure,
    taskWidth: Double):

    private val style = options.style
    private val nodes = graph.nodes.distinctBy(_.id)
    private val index = nodes.map(_.id).zipWithIndex.toMap

    private val flows =
      graph.flows.distinct.filter(f => index.contains(f.from) && index.contains(f.to))

    // the lanes in their order, with one for the nodes in none (or in a lane not
    // named) after them; without lanes there is one column and no header
    private val laned = graph.lanes.nonEmpty

    private val lanes: List[Option[Lane]] =
      val named = graph.lanes.distinctBy(_.id)
      val loose = nodes.exists(n => n.lane.forall(id => !named.exists(_.id == id)))
      named.map(Some(_)) ++ (if loose || named.isEmpty then List(None) else Nil)

    private def laneIndex(n: Node): Int =
      val i = lanes.indexWhere(_.exists(l => n.lane.contains(l.id)))
      if i >= 0 then i else lanes.size - 1

    // the flows that close a loop: those back to a node still open in a walk from the
    // starts (or, failing those, the first node), following flows in their order
    private val back: Set[Flow] =
      val out                    = flows.groupBy(_.from)
      val state                  = mutable.Map.empty[String, Int] // 1 open, 2 done
      val closes                 = mutable.LinkedHashSet.empty[Flow]
      def walk(id: String): Unit =
        state(id) = 1
        out.getOrElse(id, Nil).foreach: f =>
          state.get(f.to) match
            case Some(1) => closes += f
            case Some(_) => ()
            case None    => walk(f.to)
        state(id) = 2
      val starts = nodes.filter(n => n.kind.isInstanceOf[Kind.Start] || !flows.exists(_.to == n.id))
      (starts ++ nodes).foreach(n => if !state.contains(n.id) then walk(n.id))
      closes.toSet

    end back

    private val forward = flows.filterNot(back.contains)

    // each node's row: one below the lowest row of the nodes that flow into it
    private val rank: Map[String, Int] =
      val into                = forward.groupBy(_.to)
      val memo                = mutable.Map.empty[String, Int]
      def of(id: String): Int = memo.getOrElseUpdate(
        id,
        into.getOrElse(id, Nil).map(f => of(f.from) + 1).maxOption.getOrElse(0),
      )
      nodes.map(n => n.id -> of(n.id)).toMap

    private val rows = if nodes.isEmpty then 0 else rank.values.max + 1

    private def lineWidth(l: Line) = measure.width(l.text, l.size, l.weight)

    private val sets: Map[String, Sized] = nodes.map(n => n.id -> set(n)).toMap

    private def set(n: Node): Sized =
      val notes = n.notes.flatMap(t =>
        measure.wrap(t, NOTE_W, style.noteSize, Weight.Italic)
          .map(Line(_, style.noteSize, Weight.Italic, Emphasis.Muted))
      )
      val noteW = notes.map(lineWidth).maxOption.fold(0.0)(_ + NOTE_GAP + 0.1)
      val noteH = notes.map(l => style.lead(l.size)).sum
      n.kind match
        case Kind.Work(task) =>
          val icon  = iconOf(task).isDefined
          // the name keeps clear of the icon on its side only, and is centred in the room
          // left beside it
          val clear = if icon then 2 * style.padding + style.iconSize * ICON + 0.05
          else 2 * style.padding
          val room  = taskWidth - clear
          val lines =
            measure.wrap(n.label, room, style.nameSize, Weight.Medium)
              .map(Line(_, style.nameSize, Weight.Medium)) ++
            n.detail.toList.flatMap(d =>
              measure.wrap(d, room, style.captionSize).map(Line(
                _,
                style.captionSize,
                tone = Emphasis.Muted,
              ))
            )
          val w     = (TASK_MIN_W :: lines.map(lineWidth(_) + clear)).max
          val h     = math.max(
            TASK_MIN_H,
            lines.map(l => style.lead(l.size)).sum + 2 * style.padding +
            (if task == Task.Sub then 0.16 else 0.0),
          )
          val half  = Measure.round(w) / 2
          val tall  = Measure.round(h) / 2
          val over  = lines.count(l => lineWidth(l) > Measure.round(w) - clear + 1e-6)
          Sized(
            n,
            lines,
            notes,
            half,
            tall,
            half,
            half + noteW,
            math.max(tall, noteH / 2),
            math.max(
              tall,
              noteH / 2,
            ),
            over,
          )
        case kind            =>
          val half  = kind match
            case Kind.Gateway(_) => GATE
            case _               => EVENT_R
          val lines = measure.wrap(n.label, taskWidth, style.captionSize)
            .map(Line(_, style.captionSize))
          val textW = lines.map(lineWidth).maxOption.getOrElse(0.0)
          val textH = lines.map(l => style.lead(l.size)).sum
          // an event's name stands beside it; a gateway's question above its right
          // side, clear of the branches that leave from its corners
          kind match
            case Kind.Gateway(_) =>
              Sized(
                n,
                lines,
                notes,
                half,
                half,
                half,
                math.max(half + noteW, 0.12 + textW),
                half + textH + 0.04,
                half,
                0,
              )
            case _               =>
              val besideW = if lines.isEmpty then 0.0 else BESIDE + textW
              Sized(
                n,
                lines,
                notes,
                half,
                half,
                half,
                half + besideW + noteW,
                math.max(half, math.max(textH, noteH) / 2),
                math.max(half, math.max(textH, noteH) / 2),
                0,
              )
          end match
      end match

    end set

    // how many nodes of a row share a lane at most, and how wide a track is there
    private val tracks: Vector[Int] =
      lanes.indices.toVector.map: li =>
        nodes.filter(laneIndex(_) == li).groupBy(n => rank(n.id)).values.map(_.size).maxOption
          .getOrElse(1)

    private val trackW: Vector[Double] =
      lanes.indices.toVector.map: li =>
        nodes.filter(laneIndex(_) == li).map(n => sets(n.id).left + sets(n.id).right)
          .maxOption.getOrElse(TASK_MIN_W) + TRACK_GAP

    private def headerLines(lane: Option[Lane]): List[Line] =
      lane.toList.flatMap(l =>
        measure.wrap(l.name, Activity.LANE_HEAD_W, style.nameSize, Weight.SemiBold)
          .map(Line(_, style.nameSize, Weight.SemiBold))
      )

    private val laneW: Vector[Double] =
      lanes.zipWithIndex.toVector.map: (lane, li) =>
        val head = headerLines(lane).map(lineWidth).maxOption.fold(0.0)(_ + 2 * style.padding +
        (if lane.exists(_.shape.isDefined) then 2 * (style.iconSize + 0.1) else 0.0))
        math.max(head, 2 * LANE_PAD + tracks(li) * trackW(li))

    private val laneX: Vector[Double] = laneW.scanLeft(0.0)(_ + _).dropRight(1)

    private val header: Double =
      if !laned then 0.0
      else
        lanes.map(l => headerLines(l).map(t => style.lead(t.size)).sum).maxOption.getOrElse(0.0) +
        1.4 * style.padding

    // each row's top, the line its shapes are centred on, and its bottom
    private val (rowCentre, rowTop, rowBottom): (Vector[Double], Vector[Double], Vector[Double]) =
      var y       = -header - FIRST
      val centres = mutable.ArrayBuffer.empty[Double]
      val tops    = mutable.ArrayBuffer.empty[Double]
      val bottoms = mutable.ArrayBuffer.empty[Double]
      for r <- 0 until rows do
        val here = nodes.filter(n => rank(n.id) == r).map(n => sets(n.id))
        val up   = here.map(_.up).maxOption.getOrElse(TASK_MIN_H / 2)
        val down = here.map(_.down).maxOption.getOrElse(TASK_MIN_H / 2)
        tops += y
        centres += y - up
        bottoms += y - up - down
        y = y - up - down - ROW_GAP
      (centres.toVector, tops.toVector, bottoms.toVector)

    // where each node stands: row by row, a lane's nodes of one row ordered under the
    // nodes they come from (in their order when they come from none drawn yet), and
    // centred on the lane's tracks
    val at: Map[String, Pt] =
      val placed = mutable.Map.empty[String, Pt]
      val into   = forward.groupBy(_.to)
      for r <- 0 until rows do
        val row = nodes.filter(n => rank(n.id) == r)
        row.groupBy(laneIndex).toList.sortBy(_._1).foreach: (li, here) =>
          def under(n: Node) =
            into.getOrElse(n.id, Nil).flatMap(f => placed.get(f.from)).map(_._1) match
              case Nil => Double.MaxValue
              case xs  => xs.sum / xs.size
          val ordered        = here.sortBy(n => (under(n), index(n.id)))
          val offset         = (tracks(li) - ordered.size) / 2.0
          ordered.zipWithIndex.foreach: (n, k) =>
            // a node and what stands beside it (an event's name, a note) centred on the
            // track together; a gateway on the track's middle, its question above, so
            // a straight run through it stays straight
            val s     = sets(n.id)
            val shift = n.kind match
              case Kind.Gateway(_) => 0.0
              case _               => (s.left - s.right) / 2
            val x     = laneX(li) + LANE_PAD + (offset + k + 0.5) * trackW(li) + shift
            placed(n.id) = (x, rowCentre(r))
      end for
      placed.toMap

    end at

    private def port(id: String, side: Char): Pt =
      val (x, y) = at(id)
      val s      = sets(id)
      side match
        case 'n' => (x, y + s.tall)
        case 's' => (x, y - s.tall)
        case 'w' => (x - s.half, y)
        case _   => (x + s.half, y)

    // the room each node takes, its name and notes with it
    private def extent(id: String): (Double, Double, Double, Double) =
      val (x, y) = at(id)
      val s      = sets(id)
      (x - s.left, y - s.down, x + s.right, y + s.up)

    // whether a vertical line at x from y0 down to y1 passes clear of every node but
    // the ones it joins
    private def clearDown(
      x: Double,
      y0: Double,
      y1: Double,
      but: Set[String],
    ): Boolean =
      nodes.forall: n =>
        but.contains(n.id) || {
          val (nx0, ny0, nx1, ny1) = extent(n.id)
          !(nx0 - 0.04 < x && x < nx1 + 0.04 && ny0 < y0 && y1 < ny1)
        }

    private def clearAcross(
      y: Double,
      x0: Double,
      x1: Double,
      but: Set[String],
    ): Boolean =
      nodes.forall: n =>
        but.contains(n.id) || {
          val (nx0, ny0, nx1, ny1) = extent(n.id)
          !(ny0 - 0.04 < y && y < ny1 + 0.04 && nx0 < math.max(x0, x1) && math.min(x0, x1) < nx1)
        }

    // a way down the right of everything between two rows that reaches past `from`
    private def channel(
      lowRank: Int,
      highRank: Int,
      from: Double,
      k: Int,
    ): Double =
      val between = nodes.filter(n => rank(n.id) >= lowRank && rank(n.id) <= highRank)
      var x       = from + CHANNEL + k * CHANNEL_GAP
      var moved   = true
      while moved do
        val past = between.map(n => extent(n.id)).filter(
          (
            x0,
            _,
            x1,
            _,
          ) => x0 < x && x < x1 + CHANNEL
        )
          .map(_._3 + CHANNEL + k * CHANNEL_GAP)
        val next = (x :: past).max
        moved = next > x + 1e-9
        x = next
      x

    end channel

    // the corner each branch out of a gateway leaves from: the one facing its target,
    // or the bottom when it is straight below
    private val exits: Map[Flow, Char] =
      forward.groupBy(_.from).toList.flatMap: (from, out) =>
        if !sets(from).node.kind.isInstanceOf[Kind.Gateway] || out.size < 2 then Nil
        else
          out.map: f =>
            val dx = at(f.to)._1 - at(from)._1
            f -> (if math.abs(dx) <= sets(from).half then 's' else if dx > 0 then 'e' else 'w')
      .toMap

    // the sides of a node its branches leave from
    private def taken(id: String): Set[Char] =
      exits.collect { case (f, side) if f.from == id => side }
        .toSet

    // whether something stands beside a node on its right (an event's name, a note),
    // which a flow must not run through
    private def beside(id: String): Boolean =
      val s = sets(id)
      s.notes.nonEmpty ||
      (!s.node.kind.isInstanceOf[Kind.Work] &&
      !s.node.kind.isInstanceOf[Kind.Gateway] && s.lines.nonEmpty)

    // A forward flow: straight down when it can; a branch out of a gateway leaves from
    // the corner facing its target; a flow into a gateway that others join comes in at
    // the corner facing it, when no branch leaves from there; otherwise down, across in
    // the gap above the target (or below the source, when that is clear), and down
    // again; failing all of those, round the right of what is in the way.
    private def route(f: Flow, k: () => Int): List[Pt] =
      val (xu, yu) = at(f.from)
      val (xv, yv) = at(f.to)
      val su       = sets(f.from)
      val sv       = sets(f.to)
      val ends     = Set(f.from, f.to)
      val bottom   = port(f.from, 's')
      val top      = port(f.to, 'n')
      val ins      = forward.count(_.to == f.to)
      if math.abs(xu - xv) < 1e-6 && clearDown(xu, bottom._2, top._2, ends) then List(bottom, top)
      else
        val fromCorner  = exits.get(f).filter(_ != 's').flatMap: side =>
          val corner = port(f.from, side)
          Option.when(clearAcross(yu, corner._1, xv, ends) && clearDown(xv, yu, top._2, ends))(
            List(corner, (xv, yu), top)
          )
        val intoSide    = if xu > xv then 'e' else 'w'
        val intoCorner  =
          Option.when(sv.node.kind.isInstanceOf[Kind.Gateway] && ins > 1 &&
            math.abs(xu - xv) > sv.half && !taken(f.to).contains(intoSide) &&
            !(intoSide == 'e' && back.exists(_.to == f.to)) &&
            clearDown(xu, bottom._2, yv, ends) &&
            clearAcross(yv, xu, port(f.to, intoSide)._1, ends))(
            List(bottom, (xu, yv), port(f.to, intoSide))
          )
        val aboveTarget =
          val y = rowTop(rank(f.to)) + ROW_GAP / 2
          Option.when(clearDown(xu, bottom._2, y, ends) && clearAcross(y, xu, xv, ends) &&
            clearDown(xv, y, top._2, ends))(List(bottom, (xu, y), (xv, y), top))
        val belowSource =
          val y = rowBottom(rank(f.from)) - ROW_GAP / 2
          Option.when(clearAcross(y, xu, xv, ends) && clearDown(xv, y, top._2, ends))(
            List(bottom, (xu, y), (xv, y), top)
          )
        fromCorner.orElse(intoCorner).orElse(aboveTarget).orElse(belowSource).getOrElse:
          val below = rowBottom(rank(f.from)) - ROW_GAP / 2
          val above = rowTop(rank(f.to)) + ROW_GAP / 2
          val cx    = channel(rank(f.from), rank(f.to), math.max(xu + su.right, xv + sv.right), k())
          List(bottom, (xu, below), (cx, below), (cx, above), (xv, above), top)
      end if

    end route

    // A flow back up goes round the right of everything it passes. It leaves its source
    // from the right, or from the bottom when a branch leaves from the right or
    // something stands beside it; it comes into its target from the right, or from
    // above when a branch leaves from there or something stands beside it.
    private def loop(f: Flow, k: Int): List[Pt] =
      val (xu, yu) = at(f.from)
      val (xv, yv) = at(f.to)
      val su       = sets(f.from)
      val ends     = Set(f.from, f.to)
      val reach    = math.max(xu + su.right, xv + sets(f.to).right)
      if f.from == f.to then
        val from = port(f.from, 'e')
        val cx   = channel(rank(f.from), rank(f.from), xu + su.right, k)
        val lo   = (from._1, from._2 - su.tall * 0.5)
        val hi   = (from._1, from._2 + su.tall * 0.5)
        List(lo, (cx, lo._2), (cx, hi._2), hi)
      else
        val sideOut = !taken(f.from).contains('e') && !beside(f.from)
        val sideIn  = !taken(f.to).contains('e') && !beside(f.to)
        val low     = if sideOut then rank(f.from) else rank(f.from) + 1
        val high    = if sideIn then rank(f.to) else rank(f.to) - 1
        val cx      = channel(math.max(0, high), low, reach, k)
        val start   =
          if sideOut && clearAcross(yu, xu + su.right, cx, ends) then
            List(port(f.from, 'e'), (cx, yu))
          else
            val below = rowBottom(rank(f.from)) - ROW_GAP / 2
            List(port(f.from, 's'), (xu, below), (cx, below))
        val end     =
          if sideIn && clearAcross(yv, xv + sets(f.to).right, cx, ends) then
            List((cx, yv), port(f.to, 'e'))
          else
            val above = rowTop(rank(f.to)) + LOOP_IN
            List((cx, above), (xv, above), port(f.to, 'n'))
        start ++ end
      end if

    end loop

    private val routes: List[(Flow, List[Pt])] =
      var detours = 0
      def next()  =
        detours += 1; detours - 1
      flows.map: f =>
        if back.contains(f) then f -> loop(f, next()) else f -> route(f, () => next())

    // the field a point lies on: its lane, or the page
    private def field(p: Pt): Role =
      if laned && lanes.indices.exists(li => laneX(li) <= p._1 && p._1 <= laneX(li) + laneW(li))
      then Role.Group
      else Role.Halo

    // a flow's condition, at its start: above the stretch that leaves a corner, beside
    // the one that leaves the bottom
    private def condition(f: Flow, points: List[Pt]): List[Prim] =
      f.label.map(_.trim).filter(_.nonEmpty).toList.flatMap: text =>
        val lines = measure.wrap(text, LABEL_W, style.labelSize, Weight.Italic)
          .map(Line(_, style.labelSize, Weight.Italic, Emphasis.Muted))
        val w     = lines.map(lineWidth).maxOption.getOrElse(0.0)
        val h     = lines.map(l => style.lead(l.size)).sum
        points match
          case a :: b :: _ if math.abs(a._2 - b._2) < 1e-6 =>
            val x = if b._1 > a._1 then a._1 + 0.08 else a._1 - 0.08 - w
            Diagram.stack(
              x,
              a._2 + h + 0.04,
              lines,
              style,
              centred = false,
              backdrop = Some(field((x, a._2))),
            )
          case a :: _                                      =>
            Diagram.stack(
              a._1 + 0.08,
              a._2 - 0.05,
              lines,
              style,
              centred = false,
              backdrop = Some(field(a)),
            )
          case Nil                                         => Nil
        end match

    // the stroke across the start of a default flow
    private def stroke(points: List[Pt]): List[Prim] = points match
      case a :: b :: _ =>
        val len      = math.max(math.hypot(b._1 - a._1, b._2 - a._2), 1e-9)
        val (dx, dy) = ((b._1 - a._1) / len, (b._2 - a._2) / len)
        val (cx, cy) = (a._1 + dx * 0.13, a._2 + dy * 0.13)
        val s        = 0.07
        List(Prim.Connector(
          List(
            (cx - dy * s - dx * s, cy + dx * s - dy * s),
            (cx + dy * s + dx * s, cy - dx * s + dy * s),
          ),
          Dash.Solid,
          None,
          None,
        ))
      case _           => Nil

    private def shape(n: Node): List[Prim] =
      val (x, y) = at(n.id)
      val s      = sets(n.id)
      val fill   = Ink.Layer(BUSINESS)
      val body   = Role.Element(BUSINESS)
      val edge   = Role.Mark(BUSINESS)
      def text(
        lines: List[Line],
        left: Double,
        top: Double,
      )          =
        Diagram.stack(left, top, lines, style, centred = false)
      val notes  =
        if s.notes.isEmpty then Nil
        else
          val bx   = x + s.right - s.notes.map(lineWidth).max - 0.1
          val nh   = s.notes.map(l => style.lead(l.size)).sum
          // from where the node and its name end to the bracket round the note
          val from = x + s.right - s.notes.map(lineWidth).max - NOTE_GAP - 0.1
          List(
            Prim.Connector(List((from + 0.03, y), (bx, y)), Dash.Dotted, None, None),
            Prim.Poly(
              List(
                (bx + 0.07, y + nh / 2 + 0.04),
                (bx, y + nh / 2 + 0.04),
                (bx, y - nh / 2 - 0.04),
                (bx + 0.07, y - nh / 2 - 0.04),
              ),
              closed = false,
              role = Role.Group,
            ),
          ) ++ text(s.notes, bx + 0.1, y + nh / 2)
      val drawn  = n.kind match
        case Kind.Work(task) =>
          val box   = Prim.Box(
            x - s.half,
            y - s.tall,
            2 * s.half,
            2 * s.tall,
            ROUNDED,
            fill,
            role = body,
          )
          val size  = style.iconSize * ICON
          val icon  = iconOf(task).toList.flatMap(i =>
            Diagram.place(
              Notation.icon(i),
              x + s.half - style.padding * 0.75 - size,
              y + s.tall - style.padding * 0.75 - size,
              size,
              edge,
            )
          )
          val sub   =
            if task == Task.Sub then
              val q = 0.07
              List(
                Prim.Box(x - q, y - s.tall + 0.04, 2 * q, 2 * q, 0.0, Ink.Paper, role = edge),
                Prim.Poly(
                  List(
                    (x - q * 0.6, y - s.tall + 0.04 + q),
                    (
                      x + q * 0.6,
                      y - s.tall + 0.04 + q,
                    ),
                  ),
                  closed = false,
                  role = edge,
                ),
                Prim.Poly(
                  List((x, y - s.tall + 0.04 + q * 0.4), (x, y - s.tall + 0.04 + q * 1.6)),
                  closed = false,
                  role = edge,
                ),
              )
            else Nil
          val textH = s.lines.map(l => style.lead(l.size)).sum
          val lift  = if task == Task.Sub then 0.08 else 0.0
          val aside = if icon.isEmpty then 0.0 else (style.iconSize * ICON + 0.05) / 2
          (box :: icon) ++ sub ++
          Diagram.stack(x - aside, y + lift + textH / 2, s.lines, style, centred = true)
        case Kind.Gateway(g) =>
          val diamond = Prim.Poly(
            List((x, y + GATE), (x + GATE, y), (x, y - GATE), (x - GATE, y)),
            closed = true,
            fill,
            role = body,
          )
          val inner   = Diagram.place(mark(g), x - GATE * 0.55, y - GATE * 0.55, GATE * 1.1, edge)
          val textH   = s.lines.map(l => style.lead(l.size)).sum
          diamond :: inner ++ text(s.lines, x + 0.12, y + GATE + textH + 0.04)
        case kind            =>
          val r       = EVENT_R
          val trigger = kind match
            case Kind.Start(t)        => t
            case Kind.Intermediate(t) => t
            case Kind.End(t)          => t
            case _                    => Trigger.Plain
          val rings   = kind match
            case Kind.Start(_)        => List(Prim.Circle((x, y), r, fill, body))
            case Kind.Intermediate(_) =>
              List(
                Prim.Circle((x, y), r, fill, body),
                Prim.Circle((x, y), r - 0.045, Ink.Empty, edge),
              )
            case _                    =>
              List(Prim.Circle((x, y), r, Ink.Line, edge), Prim.Circle((x, y), r - 0.05, fill, body))
          val inside  = Diagram.place(glyph(trigger), x - r * 0.55, y - r * 0.55, r * 1.1, edge)
          val textH   = s.lines.map(l => style.lead(l.size)).sum
          rings ++ inside ++ text(s.lines, x + r + BESIDE, y + textH / 2)
      drawn ++ notes

    end shape

    // the lanes behind it all, each a field with its name in a header
    private val bottom: Double =
      val lowest = (rowBottom.lastOption.toList ++ routes.flatMap(_._2.map(_._2))).minOption
      lowest.getOrElse(-header) - LAST

    private val stage: List[Prim] =
      if !laned then Nil
      else
        lanes.zipWithIndex.flatMap: (lane, li) =>
          val x0    = laneX(li)
          val w     = laneW(li)
          val lines = headerLines(lane)
          val textH = lines.map(l => style.lead(l.size)).sum
          val icon  = lane.flatMap(_.shape).toList.flatMap(s =>
            Diagram.place(
              Notation.icon(s),
              x0 + w - style.padding - style.iconSize * 0.9,
              -header / 2 - style.iconSize * 0.45,
              style.iconSize * 0.9,
              Role.Mark(Notation.layer(
                s.group
              )),
            )
          )
          List(
            Prim.Box(x0, bottom, w, -bottom, 0.0, Ink.Empty, role = Role.Group),
            Prim.Poly(List((x0, -header), (x0 + w, -header)), closed = false, role = Role.Group),
          ) ++ icon ++ Diagram.stack(x0 + w / 2, -(header - textH) / 2, lines, style, centred = true)

    val prims: List[Prim] =
      val lines           = routes.map: (_, points) =>
        (points, Prim.Connector(points, Dash.Solid, None, Some(Head.FilledTriangle)))
      val strokes         = routes.filter(_._1.default).flatMap((_, ps) => stroke(ps))
      val gaps            = Diagram.crossings(lines, field)
      val shapes          = nodes.flatMap(shape)
      val conditions      = routes.flatMap((f, ps) => condition(f, ps))
      def isText(p: Prim) = p.isInstanceOf[Prim.Text]
      stage.filterNot(isText) ++ stage.filter(isText) ++ lines.map(_._2) ++ strokes ++ gaps ++
      shapes.filterNot(isText) ++ shapes.filter(isText) ++ conditions

    private val (minX, _, maxX, _) =
      Extent.of(Diagram.Drawing("", prims, Scene("", 0, 0, Nil, Nil), style, "", 0), 0)

    val width: Double  = if prims.isEmpty then 0.0 else maxX - minX
    val height: Double = -bottom

    val overflow: Int = sets.values.map(_.overflow).sum

  end Layout

end Activity
