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
// It draws BPMN's Analytic level: boundary events on a task's bottom edge, expanded
// sub-processes laid out inside their frame as one block in the flow, task markers,
// data beside their task, messages between pools and compensation associations.
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
    case Plain, User, Service, Manual, Script, Rule, Call, Sub, Send, Receive

  // in this order, the ones BPMN's Descriptive level has first, so their ordinals hold
  enum Trigger derives CanEqual:

    case Plain, Message, Timer, Error, Signal, Terminate, Escalation, Cancel, Compensation,
      Conditional, Link, Multiple, ParallelMultiple

  enum Gate derives CanEqual:
    case Exclusive, Parallel, Inclusive, EventBased, Complex, ParallelEventBased

  // the markers at the foot of a task: repeated in a loop, as many instances side by side
  // or one after the other, undoing another, or its steps in no set order
  enum Marker derives CanEqual:
    case Loop, Parallel, Sequential, Compensation, AdHoc

  // what an expanded sub-process is: a plain one, a transaction (a double border), or one
  // an event starts (a dotted border)
  enum Frame derives CanEqual:
    case Sub, Transaction, Event

  // A start that does not interrupt (an event sub-process's) and a boundary event that
  // does not are drawn dashed; an intermediate event that throws, and every end, shows
  // its trigger filled. A boundary event sits on the edge of its node's `host`; a
  // container holds the nodes whose `parent` it is, laid out inside it.
  enum Kind derives CanEqual:
    case Work(task: Task)
    case Start(trigger: Trigger, interrupting: Boolean = true)
    case Intermediate(trigger: Trigger, throwing: Boolean = false)
    case End(trigger: Trigger)
    case Gateway(gate: Gate)
    case Boundary(trigger: Trigger, interrupting: Boolean = true)
    case Container(frame: Frame)

  // a data object (or a data store) a task reads, or writes when `output`
  final case class Datum(
    label: String,
    store: Boolean = false,
    output: Boolean = false)

  // a node: what it says, what it is, its lane, the line under its name (the systems
  // that support it), the notes beside it, the markers at its foot, the node it sits on
  // (a boundary event's), the container it sits in, and the data it reads and writes
  final case class Node(
    id: String,
    label: String,
    kind: Kind,
    lane: Option[String] = None,
    detail: Option[String] = None,
    notes: List[String] = Nil,
    markers: List[Marker] = Nil,
    host: Option[String] = None,
    parent: Option[String] = None,
    data: List[Datum] = Nil)

  // a flow from one node to another, with its condition; the default one out of a
  // gateway has a short stroke across its start
  final case class Flow(
    from: String,
    to: String,
    label: Option[String] = None,
    default: Boolean = false)

  // a message from one node to another in another pool, or to or from a pool drawn as
  // a lane without nodes (a black box): a dashed line from an open circle to an open head
  final case class Message(
    from: String,
    to: String,
    label: Option[String] = None)

  // a dotted line with a head, from a compensation boundary event to what compensates it
  final case class Association(from: String, to: String)

  final case class Graph(
    name: String,
    lanes: List[Lane],
    nodes: List[Node],
    flows: List[Flow],
    messages: List[Message] = Nil,
    associations: List[Association] = Nil)

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

  private val TASK_WIDTHS  = List(2.4, 2.0)
  private val TASK_MIN_W   = 1.9
  private val TASK_MIN_H   = 0.8
  private val EVENT_R      = 0.2
  private val GATE         = 0.27        // half a gateway's diagonal
  private val BESIDE       = 0.1         // from an event or gateway to its name
  private val TRACK_GAP    = 0.4         // between two nodes side by side in a lane
  private val LANE_PAD     = 0.2         // inside a lane, left and right
  private val ROW_GAP      = 0.62        // between two rows
  private val FIRST        = 0.35        // from the lane headers to the first row
  private val LAST         = 0.3         // under the last row
  private val CHANNEL      = 0.3         // a loop back's way round, clear of what it passes
  private val CHANNEL_GAP  = 0.16        // between two loops back side by side
  private val LOOP_IN      = ROW_GAP / 4 // a loop coming in from above, under the flows that do
  private val LABEL_W      = 2.4         // the widest a flow's condition is set
  private val NOTE_W       = 2.6         // the widest a note is set
  private val NOTE_GAP     = 0.28        // from a node to its note
  private val ICON         = 0.85        // a task's icon, against a box's in Diagram
  private val ROUNDED      = 0.16
  private val LANE_HEAD_W  = 3.2         // the widest a lane's name is set
  private val BUSINESS     = "am-business"
  private val BOUNDARY_W   = 1.4         // the widest a boundary event's name is set
  private val DATA_LABEL_W = 1.6         // the widest a data object's name is set
  private val DATA_GAP     = 0.4         // from a task to its data
  private val DOC_W        = 0.28        // a data object's page, and a store
  private val DOC_H        = 0.36
  private val DATA_SEP     = 0.1         // between two data beside a task
  private val FOOT         = 0.17        // a marker at a task's foot
  private val FRAME_PAD    = 0.2         // inside an expanded container, left and right
  private val MESSAGE_OUT  = 0.2         // a message out of a node's side before it turns
  private val BLACK_BOX_W  = 1.2         // a lane without nodes: a pool drawn as a black box

  // the icon a task's kind is shown by, from the ArchiMate notation where one says it
  private def iconOf(task: Task): Option[ShapeType] = task match
    case Task.User    => Some(ShapeType.BusinessActor)
    case Task.Service => Some(ShapeType.ApplicationComponent)
    case Task.Script  => Some(ShapeType.TechnologyArtifact)
    case Task.Rule    => Some(ShapeType.MotivationPrinciple)
    case Task.Call    => Some(ShapeType.BusinessProcess)
    case _            => None

  // a trigger drawn in a unit square, lines in the edge colour: open when the event
  // catches it, filled when it throws
  private def glyph(trigger: Trigger, filled: Boolean = false): List[Prim] =
    def line(points: Pt*)  = Prim.Poly(points.toList, closed = false)
    val ink                = if filled then Ink.Line else Ink.Paper
    def shape(points: Pt*) = Prim.Poly(points.toList, closed = true, ink)
    trigger match
      case Trigger.Plain        => Nil
      case Trigger.Message      =>
        List(
          shape((0.12, 0.22), (0.88, 0.22), (0.88, 0.78), (0.12, 0.78)),
          if filled then Prim.Poly(List((0.12, 0.78), (0.5, 0.46), (0.88, 0.78)), true, Ink.Paper)
          else line((0.12, 0.78), (0.5, 0.46), (0.88, 0.78)),
        )
      case Trigger.Timer        =>
        List(
          Prim.Circle((0.5, 0.5), 0.4, Ink.Paper),
          line((0.5, 0.78), (0.5, 0.5), (0.72, 0.38)),
        )
      case Trigger.Error        =>
        List(shape((0.15, 0.12), (0.4, 0.66), (0.6, 0.42), (0.85, 0.88), (0.6, 0.34), (0.4, 0.58)))
      case Trigger.Escalation   => List(shape((0.5, 0.88), (0.78, 0.14), (0.5, 0.42), (0.22, 0.14)))
      case Trigger.Cancel       =>
        List(shape(
          (0.22, 0.3),
          (0.3, 0.22),
          (0.5, 0.42),
          (0.7, 0.22),
          (0.78, 0.3),
          (0.58, 0.5),
          (0.78, 0.7),
          (0.7, 0.78),
          (0.5, 0.58),
          (0.3, 0.78),
          (0.22, 0.7),
          (0.42, 0.5),
        ))
      case Trigger.Compensation =>
        List(
          shape((0.1, 0.5), (0.48, 0.76), (0.48, 0.24)),
          shape((0.48, 0.5), (0.86, 0.76), (0.86, 0.24)),
        )
      case Trigger.Conditional  =>
        Prim.Poly(List((0.24, 0.12), (0.76, 0.12), (0.76, 0.88), (0.24, 0.88)), true, Ink.Paper) ::
        List(0.72, 0.56, 0.4, 0.24).map(y => line((0.32, y), (0.68, y)))
      case Trigger.Link         =>
        List(shape(
          (0.12, 0.38),
          (0.55, 0.38),
          (0.55, 0.18),
          (0.88, 0.5),
          (0.55, 0.82),
          (0.55, 0.62),
          (0.12, 0.62),
        ))
      case Trigger.Signal       => List(shape((0.5, 0.86), (0.88, 0.18), (0.12, 0.18)))
      case Trigger.Terminate    => List(Prim.Circle((0.5, 0.5), 0.34, Ink.Line))
      case Trigger.Multiple     =>
        List(shape((0.5, 0.88), (0.86, 0.62), (0.72, 0.18), (0.28, 0.18), (0.14, 0.62)))
      case Trigger.ParallelMultiple =>
        List(shape(
          (0.38, 0.12),
          (0.62, 0.12),
          (0.62, 0.38),
          (0.88, 0.38),
          (0.88, 0.62),
          (0.62, 0.62),
          (0.62, 0.88),
          (0.38, 0.88),
          (0.38, 0.62),
          (0.12, 0.62),
          (0.12, 0.38),
          (0.38, 0.38),
        ))
    end match

  end glyph

  // a gateway's kind drawn in a unit square
  private def mark(gate: Gate): List[Prim] =
    def line(points: Pt*) = Prim.Poly(points.toList, closed = false)
    gate match
      case Gate.Exclusive  =>
        List(line((0.28, 0.28), (0.72, 0.72)), line((0.28, 0.72), (0.72, 0.28)))
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
      case Gate.Complex    =>
        List(
          line((0.5, 0.18), (0.5, 0.82)),
          line((0.18, 0.5), (0.82, 0.5)),
          line((0.27, 0.27), (0.73, 0.73)),
          line((0.27, 0.73), (0.73, 0.27)),
        )
      case Gate.ParallelEventBased =>
        Prim.Circle((0.5, 0.5), 0.34) :: glyph(Trigger.ParallelMultiple).map:
          case Prim.Poly(ps, c, f, d, r) =>
            Prim.Poly(ps.map((x, y) => (0.5 + (x - 0.5) * 0.7, 0.5 + (y - 0.5) * 0.7)), c, f, d, r)
          case other                     => other
    end match

  end mark

  // a marker at a task's foot, in a unit square
  private def foot(marker: Option[Marker]): List[Prim] =
    def line(points: Pt*) = Prim.Poly(points.toList, closed = false)
    marker match
      case None                      =>
        List(
          Prim.Box(0.08, 0.08, 0.84, 0.84, 0.0, Ink.Paper),
          line((0.25, 0.5), (0.75, 0.5)),
          line((0.5, 0.25), (0.5, 0.75)),
        )
      case Some(Marker.Loop)         =>
        val arc = (0 to 12).toList.map: i =>
          val a = math.toRadians(-60 + i * 25.0)
          (0.5 + 0.36 * math.cos(a), 0.5 + 0.36 * math.sin(a))
        List(Prim.Poly(arc, closed = false), line((0.5, 0.0), arc.head, (0.86, 0.06)))
      case Some(Marker.Parallel)     => List(0.25, 0.5, 0.75).map(x => line((x, 0.1), (x, 0.9)))
      case Some(Marker.Sequential)   => List(0.25, 0.5, 0.75).map(y => line((0.1, y), (0.9, y)))
      case Some(Marker.Compensation) => glyph(Trigger.Compensation)
      case Some(Marker.AdHoc)        =>
        List(line((0.08, 0.42), (0.28, 0.6), (0.5, 0.48), (0.72, 0.38), (0.92, 0.56)))
    end match

  end foot

  // a circle as a polygon, so it can be dashed
  private def ring(c: Pt, r: Double): List[Pt] =
    (0 until 36).toList.map: i =>
      val a = math.Pi * 2 * i / 36
      (c._1 + r * math.cos(a), c._2 + r * math.sin(a))

  // the same prims moved by (dx, dy)
  private def shift(
    prims: List[Prim],
    dx: Double,
    dy: Double,
  ): List[Prim] =
    def p(pt: Pt): Pt = (pt._1 + dx, pt._2 + dy)
    prims.map:
      case Prim.Poly(ps, closed, fill, dash, role) => Prim.Poly(ps.map(p), closed, fill, dash, role)
      case Prim.Circle(c, r, fill, role)           => Prim.Circle(p(c), r, fill, role)
      case Prim.Ellipse(c, rx, ry, fill, role)     => Prim.Ellipse(p(c), rx, ry, fill, role)
      case Prim.Box(x, y, w, h, r, fill, d, k, role) =>
        Prim.Box(x + dx, y + dy, w, h, r, fill, d, k, role)
      case t: Prim.Text                              => t.copy(baseline = p(t.baseline))
      case Prim.Connector(ps, dash, start, end)      => Prim.Connector(ps.map(p), dash, start, end)

  // A node as it is set: its lines, and how far it reaches round the centre of its
  // shape (a task's box, an event's circle, a gateway's diamond, a container's frame):
  // `left` and `right` with its name, data and notes, `up` and `down` with the boundary
  // events on its edge; `half` and `tall` are the shape's own half width and half
  // height, where the flows meet it. A task carries the markers at its foot and the
  // data beside it; an expanded container the layout of what it holds.
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
    overflow: Int,
    feet: List[Option[Marker]] = Nil,
    data: List[(Datum, List[Line])] = Nil,
    inner: Option[Layout] = None)

  final private class Layout(
    graph: Graph,
    options: Options,
    measure: Measure,
    taskWidth: Double,
    ground: Role = Role.Halo):

    private val style = options.style
    private val all   = graph.nodes.distinctBy(_.id)
    all.map(_.id).toSet

    private val byId = all.map(n => n.id -> n).toMap

    // the container a node is drawn in: its parent, when that is a container here and the
    // chain of parents from it does not come back round; otherwise none
    private def parentOf(n: Node): Option[String] =
      def container(id: String) = byId.get(id).exists(_.kind.isInstanceOf[Kind.Container])
      def cycles: Boolean       =
        Iterator.iterate(n.parent)(_.flatMap(byId.get).flatMap(_.parent)).take(all.size + 1)
          .drop(1).exists(_.contains(n.id))
      n.parent.filter(p => p != n.id && container(p) && !cycles)

    // the nodes drawn at this level: those in no container drawn here
    private val nodes = all.filter(parentOf(_).isEmpty)
    private val index = nodes.map(_.id).zipWithIndex.toMap

    private def isBoundary(n: Node) = n.kind.isInstanceOf[Kind.Boundary]

    // each boundary event and the node at this level it sits on
    private val hosts: Map[String, String] =
      nodes.flatMap: n =>
        n.host.filter(h =>
          isBoundary(n) && h != n.id && nodes.exists(m => m.id == h && !isBoundary(m))
        ).map(n.id -> _)
      .toMap

    // the nodes that take a place of their own: all but the boundary events
    private val cells = nodes.filterNot(n => hosts.contains(n.id))

    private def onHost(id: String): List[Node] = nodes.filter(n => hosts.get(n.id).contains(id))

    private val flows =
      graph.flows.distinct.filter(f => index.contains(f.from) && index.contains(f.to))

    // the compensation associations, routed as flows are
    private val links: List[Flow] =
      graph.associations.distinct.filter(a => index.contains(a.from) && index.contains(a.to))
        .map(a => Flow(a.from, a.to))

    // what a container holds, at every depth, the ones directly in it lifted to its top
    private def within(c: String, seen: Set[String] = Set.empty): List[Node] =
      val kids = all.filter(n => parentOf(n).contains(c) && !seen.contains(n.id))
      kids ++ kids.flatMap(k => within(k.id, seen ++ kids.map(_.id) + c))

    // the lanes in their order, with one for the nodes in none (or in a lane not
    // named) after them; without lanes there is one column and no header
    private val laned = graph.lanes.nonEmpty

    private val lanes: List[Option[Lane]] =
      val named = graph.lanes.distinctBy(_.id)
      val loose = cells.exists(n => n.lane.forall(id => !named.exists(_.id == id)))
      named.map(Some(_)) ++ (if loose || named.isEmpty then List(None) else Nil)

    private def laneIndex(n: Node): Int =
      val i = lanes.indexWhere(_.exists(l => n.lane.contains(l.id)))
      if i >= 0 then i else lanes.size - 1

    // the flows that close a loop: those back to a node still open in a walk from the
    // starts (or, failing those, the first node), following flows in their order
    private val back: Set[Flow] =
      val out                    = (flows ++ links).groupBy(_.from)
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
      val starts                 =
        nodes.filter(n => n.kind.isInstanceOf[Kind.Start] || !(flows ++ links).exists(_.to == n.id))
      (starts ++ nodes).foreach(n => if !state.contains(n.id) then walk(n.id))
      closes.toSet

    end back

    private val forward = flows.filterNot(back.contains)

    // each node's row: one below the lowest row of the nodes that flow into it (or that
    // it compensates); a boundary event on its host's row, and an event sub-process,
    // which no flow reaches, under everything else
    private val rank: Map[String, Int] =
      val into = (forward ++ links.filterNot(back.contains)).groupBy(_.to)
      val memo = mutable.Map.empty[String, Int]
      def of(id: String, seen: Set[String]): Int = hosts.get(id) match
        case Some(h) => of(h, seen)
        case None    =>
          memo.getOrElseUpdate(
            id,
            into.getOrElse(id, Nil).filterNot(f => seen.contains(f.from))
              .map(f => of(f.from, seen + id) + 1).maxOption.getOrElse(0),
          )
      val apart                                  = cells.filter(n =>
        n.kind == Kind.Container(Frame.Event) && !flows.exists(f => f.from == n.id || f.to == n.id)
      ).map(_.id).toSet
      val base                                   = nodes.map(n => n.id -> of(n.id, Set.empty)).toMap
      val deepest = base.collect { case (id, r) if !apart.contains(id) => r }.maxOption.getOrElse(-1)
      base ++ apart.map(_ -> (deepest + 1))

    end rank

    private val rows = if cells.isEmpty then 0 else cells.map(n => rank(n.id)).max + 1

    private def lineWidth(l: Line) = measure.width(l.text, l.size, l.weight)

    private def leading(ls: List[Line]) = ls.map(l => style.lead(l.size)).sum

    // where a boundary event sits along its host's bottom edge, from its centre: to the
    // right first, then the left, clear of the flow that leaves the middle
    private def along(half: Double, k: Int): Double =
      val step  = 2 * EVENT_R + 0.12
      val right = (0 until 8).map(i => half - EVENT_R - 0.12 - i * step).filter(_ >= 0.4).toList
      val left  = right.map(-_)
      val spots = right.zipAll(left, Double.NaN, Double.NaN).flatMap((a, b) => List(a, b))
        .filterNot(_.isNaN)
      spots.lift(k).getOrElse(0.0)

    // the half width a host needs for `n` events on its edge, two to a step of `along`
    private def room(n: Int): Double =
      if n <= 0 then 0.0 else 0.4 + ((n - 1) / 2) * (2 * EVENT_R + 0.12) + EVENT_R + 0.12

    private def boundaryLines(n: Node): List[Line] =
      measure.wrap(n.label, BOUNDARY_W, style.captionSize).map(Line(_, style.captionSize))

    private val sets: Map[String, Sized] =
      val own = nodes.map(n => n.id -> set(n)).toMap
      // a host reaches as far down, and right, as the names of the events on its edge
      own.map: (id, set) =>
        val on = onHost(id)
        if on.isEmpty then id -> set
        else
          // wide enough for every event on its edge, side by side
          val grow  = set.node.kind match
            case Kind.Work(_) | Kind.Container(_) =>
              math.max(0.0, Measure.round(2 * room(on.size)) / 2 - set.half)
            case _                                => 0.0
          val s     = own(id).copy(
            half = own(id).half + grow,
            left = own(id).left + grow,
            right = own(id).right + grow,
          )
          val reach = on.zipWithIndex.map: (b, k) =>
            val ls = boundaryLines(b)
            (
              along(s.half, k) + EVENT_R + 0.04 + ls.map(lineWidth).maxOption.getOrElse(0.0),
              s.tall + EVENT_R * 0.4 + leading(ls),
            )
          id -> s.copy(
            right = (s.right :: reach.map(_._1)).max,
            down = (s.down :: s.tall + EVENT_R + 0.04 :: reach.map(_._2)).max,
          )
        end if

    end sets

    // a task's box, sized to its name and systems, with its markers, data and notes
    private def work(
      n: Node,
      task: Task,
      notes: List[Line],
      feet: List[Option[Marker]],
    ): Sized =
      val noteW  = notes.map(lineWidth).maxOption.fold(0.0)(_ + NOTE_GAP + 0.1)
      val noteH  = leading(notes)
      val icon   = iconOf(task).isDefined || task == Task.Send || task == Task.Receive
      // the name keeps clear of the icon on its side only, and is centred in the room
      // left beside it
      val clear  = if icon then 2 * style.padding + style.iconSize * ICON + 0.05
      else 2 * style.padding
      val room   = taskWidth - clear
      val lines  =
        measure.wrap(n.label, room, style.nameSize, Weight.Medium)
          .map(Line(_, style.nameSize, Weight.Medium)) ++
        n.detail.toList.flatMap(d =>
          measure.wrap(d, room, style.captionSize).map(Line(
            _,
            style.captionSize,
            tone = Emphasis.Muted,
          ))
        )
      val data   = n.data.map(d =>
        d -> measure.wrap(d.label, DATA_LABEL_W, style.captionSize).map(Line(_, style.captionSize))
      )
      val dataW  =
        if data.isEmpty then 0.0
        else
          DATA_GAP + DOC_W + 0.06 + data.flatMap(_._2).map(lineWidth).maxOption.getOrElse(0.0) + 0.1
      val dataH  = data.map((_, ls) => math.max(DOC_H, leading(ls))).sum +
        DATA_SEP * math.max(0, data.size - 1)
      val w      = (TASK_MIN_W :: lines.map(lineWidth(_) + clear)).max
      val h      = math.max(
        TASK_MIN_H,
        leading(lines) + 2 * style.padding +
        (if feet.nonEmpty then FOOT + 0.04 else 0.0),
      )
      val half   = Measure.round(w) / 2
      val tall   = Measure.round(h) / 2
      val over   = lines.count(l => lineWidth(l) > Measure.round(w) - clear + 1e-6)
      val upDown = (tall :: noteH / 2 :: dataH / 2 :: Nil).max
      Sized(
        n,
        lines,
        notes,
        half,
        tall,
        half,
        half + dataW + noteW,
        upDown,
        upDown,
        over,
        feet,
        data,
      )

    end work

    private def set(n: Node): Sized =
      val notes = n.notes.flatMap(t =>
        measure.wrap(t, NOTE_W, style.noteSize, Weight.Italic)
          .map(Line(_, style.noteSize, Weight.Italic, Emphasis.Muted))
      )
      val noteW = notes.map(lineWidth).maxOption.fold(0.0)(_ + NOTE_GAP + 0.1)
      val noteH = leading(notes)
      val feet  = n.markers.distinct.map(Some(_))
      n.kind match
        case Kind.Work(task)     =>
          work(n, task, notes, (if task == Task.Sub then List(None) else Nil) ++ feet)
        case Kind.Container(_)   =>
          val held = within(n.id)
          if held.isEmpty then work(n, Task.Sub, notes, List(None) ++ feet)
          else
            val inner = Layout(
              Graph(
                n.label,
                Nil,
                held.map(m => if parentOf(m).contains(n.id) then m.copy(parent = None) else m),
                graph.flows,
                Nil,
                graph.associations,
              ),
              options,
              measure,
              taskWidth,
              Role.Container(BUSINESS),
            )
            val head  = measure.wrap(
              n.label,
              math.max(inner.width, LANE_HEAD_W),
              style.nameSize,
              Weight.Medium,
            )
              .map(Line(_, style.nameSize, Weight.Medium))
            val w     = math.max(
              inner.width + 2 * FRAME_PAD,
              head.map(lineWidth).maxOption.getOrElse(0.0) +
              2 * style.padding,
            )
            val h     = 0.6 * style.padding + leading(head) + 0.05 + inner.height +
              (if feet.nonEmpty then FOOT + 0.04 else 0.0)
            val half  = Measure.round(w) / 2
            val tall  = Measure.round(h) / 2
            Sized(
              n,
              head,
              notes,
              half,
              tall,
              half,
              half + noteW,
              math.max(tall, noteH / 2),
              math.max(tall, noteH / 2),
              inner.overflow,
              feet,
              inner = Some(inner),
            )
          end if
        case Kind.Boundary(_, _) =>
          Sized(n, boundaryLines(n), Nil, EVENT_R, EVENT_R, EVENT_R, EVENT_R, EVENT_R, EVENT_R, 0)
        case kind                =>
          val half  = kind match
            case Kind.Gateway(_) => GATE
            case _               => EVENT_R
          val lines = measure.wrap(n.label, taskWidth, style.captionSize)
            .map(Line(_, style.captionSize))
          val textW = lines.map(lineWidth).maxOption.getOrElse(0.0)
          val textH = leading(lines)
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

    // how many nodes of a row share a lane at most, and how wide a track is there; a
    // lane without nodes (a pool drawn as a black box) is narrow
    private val tracks: Vector[Int] =
      lanes.indices.toVector.map: li =>
        cells.filter(laneIndex(_) == li).groupBy(n => rank(n.id)).values.map(_.size).maxOption
          .getOrElse(1)

    private val trackW: Vector[Double] =
      lanes.indices.toVector.map: li =>
        cells.filter(laneIndex(_) == li).map(n => sets(n.id).left + sets(n.id).right)
          .maxOption.map(_ + TRACK_GAP).getOrElse(BLACK_BOX_W)

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
        lanes.map(l => leading(headerLines(l))).maxOption.getOrElse(0.0) + 1.4 * style.padding

    // each row's top, the line its shapes are centred on, and its bottom
    private val (rowCentre, rowTop, rowBottom): (Vector[Double], Vector[Double], Vector[Double]) =
      var y       = -header - FIRST
      val centres = mutable.ArrayBuffer.empty[Double]
      val tops    = mutable.ArrayBuffer.empty[Double]
      val bottoms = mutable.ArrayBuffer.empty[Double]
      for r <- 0 until rows do
        val here = cells.filter(n => rank(n.id) == r).map(n => sets(n.id))
        val up   = here.map(_.up).maxOption.getOrElse(TASK_MIN_H / 2)
        val down = here.map(_.down).maxOption.getOrElse(TASK_MIN_H / 2)
        tops += y
        centres += y - up
        bottoms += y - up - down
        y = y - up - down - ROW_GAP
      (centres.toVector, tops.toVector, bottoms.toVector)

    // where each node stands: row by row, a lane's nodes of one row ordered under the
    // nodes they come from (in their order when they come from none drawn yet), and
    // centred on the lane's tracks; a boundary event on its host's bottom edge
    val at: Map[String, Pt] =
      val placed = mutable.Map.empty[String, Pt]
      val into   = forward.groupBy(_.to)
      for r <- 0 until rows do
        val row = cells.filter(n => rank(n.id) == r)
        row.groupBy(laneIndex).toList.sortBy(_._1).foreach: (li, here) =>
          // where a node it comes from stands; a boundary event where it sits on its host
          def from(id: String) = hosts.get(id) match
            case Some(h) =>
              val k = onHost(h).indexWhere(_.id == id)
              placed.get(h).map(p => p._1 + along(sets(h).half, k))
            case None    => placed.get(id).map(_._1)
          def under(n: Node)   =
            into.getOrElse(n.id, Nil).flatMap(f => from(f.from)) match
              case Nil => Double.MaxValue
              case xs  => xs.sum / xs.size
          val ordered          = here.sortBy(n => (under(n), index(n.id)))
          val offset           = (tracks(li) - ordered.size) / 2.0
          ordered.zipWithIndex.foreach: (n, k) =>
            // a node's shape on the track's middle, or under the one node of its lane it
            // comes from, so a straight run stays straight; moved left only as far as what
            // stands beside it (an event's name, a note, data) needs to keep within the
            // track; a gateway on the middle, its question above
            val s      = sets(n.id)
            val middle = laneX(li) + LANE_PAD + (offset + k + 0.5) * trackW(li)
            // the room it may take: its track, or the lane when it is alone on its row there
            val alone  = ordered.size == 1
            val low    =
              if alone then laneX(li) + LANE_PAD
              else laneX(li) + LANE_PAD + (offset + k) * trackW(li) + TRACK_GAP / 2
            val edge   =
              if alone then laneX(li) + laneW(li) - LANE_PAD
              else laneX(li) + LANE_PAD + (offset + k + 1) * trackW(li) - TRACK_GAP / 2
            val above  = into.getOrElse(n.id, Nil).map(_.from).distinct match
              case List(id)
                if cells.exists(c => c.id == hosts.getOrElse(id, id) && laneIndex(c) == li) =>
                from(id)
              case _ => None
            val x      = n.kind match
              case Kind.Gateway(_) => middle
              case _               =>
                val right = laneX(li) + LANE_PAD + (offset + k + 1) * trackW(li) - TRACK_GAP / 2
                val fit = math.max((s.left - s.right) / 2, math.min(0.0, right - middle - s.right))
                above.filter(a => a - s.left >= low - 1e-9 && a + s.right <= edge + 1e-9)
                  .getOrElse(middle + fit)
            placed(n.id) = (x, rowCentre(r))
      end for
      cells.foreach: h =>
        onHost(h.id).zipWithIndex.foreach: (b, k) =>
          val (x, y) = placed(h.id)
          placed(b.id) = (x + along(sets(h.id).half, k), y - sets(h.id).tall)
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
      val (top, low) = (math.max(y0, y1), math.min(y0, y1))
      nodes.forall: n =>
        but.contains(n.id) || {
          val (nx0, ny0, nx1, ny1) = extent(n.id)
          !(nx0 - 0.04 < x && x < nx1 + 0.04 && ny0 < top && low < ny1)
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

    // whether something stands beside a node on its right (an event's name, a note,
    // data), which a flow must not run through; a boundary event is left from below
    private def beside(id: String): Boolean =
      val s = sets(id)
      s.notes.nonEmpty || s.data.nonEmpty || hosts.contains(id) ||
      (!s.node.kind.isInstanceOf[Kind.Work] && !s.node.kind.isInstanceOf[Kind.Container] &&
      !s.node.kind.isInstanceOf[Kind.Gateway] && s.lines.nonEmpty)

    // the nodes a line between two nodes may touch: those two and what they sit on
    private def joined(a: String, b: String): Set[String] =
      Set(a, b) ++ hosts.get(a) ++ hosts.get(b)

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
      val ends     = joined(f.from, f.to)
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
      val ends     = joined(f.from, f.to)
      val reach    = math.max(
        xu + hosts.get(f.from).fold(su.right)(h => at(h)._1 - xu + sets(h).right),
        xv + sets(f.to).right,
      )
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

    private val (routes, linked): (List[(Flow, List[Pt])], List[List[Pt]]) =
      var detours = 0
      def next()  =
        detours += 1; detours - 1
      val drawn   = flows.map: f =>
        if back.contains(f) then f -> loop(f, next()) else f -> route(f, () => next())
      (drawn, links.map(l => route(l, () => next())))

    // A message: across from the side of its source that faces its target and up or down
    // into it, when that is clear; otherwise down into the gap under its source, across
    // and into the target. A pool drawn as a black box is met at its near edge.
    private def message(message: Message): Option[List[Pt]] =
      def lane(id: String)  = lanes.indexWhere(_.exists(_.id == id))
      // a node inside a container is reached at the container drawn here
      def drawn(id: String) =
        Iterator.iterate(Option(id))(_.flatMap(byId.get).flatMap(parentOf)).take(all.size + 1)
          .flatten.find(index.contains).getOrElse(id)
      val m                 = message.copy(from = drawn(message.from), to = drawn(message.to))
      val ends              = joined(m.from, m.to)
      (index.contains(m.from), index.contains(m.to)) match
        case (true, true) if m.from != m.to     =>
          val (xu, yu) = at(m.from)
          val (xv, yv) = at(m.to)
          val side     = if xv >= xu then 'e' else 'w'
          val start    = port(m.from, side)
          val into     = port(m.to, if yu > yv then 'n' else 's')
          val across   =
            if math.abs(yu - yv) < 1e-6 then
              val end = port(m.to, if side == 'e' then 'w' else 'e')
              Option.when(!(side == 'e' && beside(m.from)) && clearAcross(yu, start._1, end._1, ends))(
                List(start, end)
              )
            else
              Option.when(!(side == 'e' && beside(m.from)) && clearAcross(yu, start._1, xv, ends) &&
                clearDown(xv, yu, into._2, ends))(List(start, (xv, yu), into))
          across.orElse:
            val y    = rowBottom(rank(hosts.getOrElse(m.from, m.from))) - ROW_GAP / 4
            val to   = port(m.to, if y > yv then 'n' else 's')
            val out  = port(m.from, if side == 'e' && beside(m.from) then 'w' else side)
            val wide = if out._1 > xu then out._1 + MESSAGE_OUT else out._1 - MESSAGE_OUT
            Some(List(out, (wide, yu), (wide, y), (xv, y), to))
        case (true, false) if lane(m.to) >= 0   =>
          val li       = lane(m.to)
          val (xu, yu) = at(m.from)
          val right    = laneX(li) > xu
          val edge     = if right then laneX(li) else laneX(li) + laneW(li)
          val start    = port(m.from, if right then 'e' else 'w')
          if !(right && beside(m.from)) && clearAcross(yu, start._1, edge, ends) then
            Some(List(start, (edge, yu)))
          else
            // out of the near side, down beside the node into the gap under its row (above
            // the flows that cross there) and across
            val y    = rowBottom(rank(hosts.getOrElse(m.from, m.from))) - ROW_GAP / 4
            val wide = if right then start._1 + MESSAGE_OUT else start._1 - MESSAGE_OUT
            Some(List(start, (wide, yu), (wide, y), (edge, y)))
        case (false, true) if lane(m.from) >= 0 =>
          val li       = lane(m.from)
          val (xv, yv) = at(m.to)
          val right    = laneX(li) > xv
          val edge     = if right then laneX(li) else laneX(li) + laneW(li)
          val end      = port(m.to, if right then 'e' else 'w')
          if !(right && beside(m.to)) && clearAcross(yv, edge, end._1, ends) then
            Some(List((edge, yv), end))
          else
            val y = rowTop(rank(m.to)) + ROW_GAP / 2
            Some(List((edge, y), (xv, y), port(m.to, 'n')))
        case _                                  => None
      end match

    end message

    private val messages: List[(Message, List[Pt])] =
      graph.messages.distinct.flatMap(m => message(m).map(m -> _))

    // the field a point lies on: its lane, or what the diagram stands on
    private def field(p: Pt): Role =
      if laned && lanes.indices.exists(li => laneX(li) <= p._1 && p._1 <= laneX(li) + laneW(li))
      then Role.Group
      else ground

    // a flow's condition, at its start: above the stretch that leaves a corner, beside
    // the one that leaves the bottom
    private def condition(label: Option[String], points: List[Pt]): List[Prim] =
      label.map(_.trim).filter(_.nonEmpty).toList.flatMap: text =>
        val lines = measure.wrap(text, LABEL_W, style.labelSize, Weight.Italic)
          .map(Line(_, style.labelSize, Weight.Italic, Emphasis.Muted))
        val w     = lines.map(lineWidth).maxOption.getOrElse(0.0)
        val h     = leading(lines)
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

    // a message's name, over the middle of its longest stretch across
    private def caption(label: Option[String], points: List[Pt]): List[Prim] =
      val across = points.zip(points.drop(1)).filter((a, b) => math.abs(a._2 - b._2) < 1e-6)
      label.map(_.trim).filter(_.nonEmpty).toList.flatMap: text =>
        across.maxByOption((a, b) => math.abs(b._1 - a._1)).toList.flatMap: (a, b) =>
          val lines = measure.wrap(text, LABEL_W, style.labelSize, Weight.Italic)
            .map(Line(_, style.labelSize, Weight.Italic, Emphasis.Muted))
          val mid   = ((a._1 + b._1) / 2, a._2)
          Diagram.stack(
            mid._1,
            mid._2 + leading(lines) + 0.04,
            lines,
            style,
            centred = true,
            backdrop = Some(field(mid)),
          )

    end caption

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

    // the markers at the foot of a box, side by side round its middle
    private def feet(
      s: Sized,
      x: Double,
      y: Double,
    ): List[Prim] =
      val n     = s.feet.size
      val width = n * FOOT + (n - 1) * 0.05
      s.feet.zipWithIndex.flatMap: (m, i) =>
        Diagram.place(
          foot(m),
          x - width / 2 + i * (FOOT + 0.05),
          y - s.tall + 0.05,
          FOOT,
          Role.Mark(BUSINESS),
        )

    end feet

    // the data a task reads and writes, in a column beside it, each tied to it by a
    // dotted line with a head at the end the data goes to
    private def data(
      s: Sized,
      x: Double,
      y: Double,
    ): List[Prim] =
      val hs    = s.data.map((_, ls) => math.max(DOC_H, leading(ls)))
      val total = hs.sum + DATA_SEP * math.max(0, hs.size - 1)
      val x0    = x + s.half + DATA_GAP
      s.data.zip(hs).zip(hs.scanLeft(0.0)(_ + _ + DATA_SEP)).flatMap:
        case (((d, ls), h), above) =>
          val cy     = y + total / 2 - above - h / 2
          val (b, t) = (cy - DOC_H / 2, cy + DOC_H / 2)
          val fold   = 0.09
          val icon   =
            if d.store then
              List(
                Prim.Poly(
                  List((x0, t - 0.05), (x0, b + 0.05), (x0 + DOC_W, b + 0.05), (x0 + DOC_W, t - 0.05)),
                  closed = true,
                  Ink.Paper,
                ),
                Prim.Ellipse((x0 + DOC_W / 2, b + 0.05), DOC_W / 2, 0.05, Ink.Paper),
                Prim.Ellipse((x0 + DOC_W / 2, t - 0.05), DOC_W / 2, 0.05, Ink.Paper),
              )
            else
              List(
                Prim.Poly(
                  List(
                    (x0, b),
                    (x0 + DOC_W, b),
                    (x0 + DOC_W, t - fold),
                    (x0 + DOC_W - fold, t),
                    (x0, t),
                  ),
                  closed = true,
                  Ink.Paper,
                ),
                Prim.Poly(
                  List((x0 + DOC_W - fold, t), (x0 + DOC_W - fold, t - fold), (x0 + DOC_W, t - fold)),
                  closed = false,
                ),
              )
          val tie    = Prim.Connector(
            List((x + s.half, y), (x0, cy)),
            Dash.Dotted,
            Option.when(!d.output)(Head.Barb),
            Option.when(d.output)(Head.Barb),
          )
          tie :: icon ++ Diagram.stack(
            x0 + DOC_W + 0.06,
            cy + leading(ls) / 2,
            ls,
            style,
            centred = false,
          )

    end data

    // an event's rings, solid or dashed
    private def rings(
      x: Double,
      y: Double,
      double: Boolean,
      thick: Boolean,
      dashed: Boolean,
    ): List[Prim] =
      val r    = EVENT_R
      val fill = Ink.Layer(BUSINESS)
      val body = Role.Element(BUSINESS)
      val edge = Role.Mark(BUSINESS)
      if thick then
        List(Prim.Circle((x, y), r, Ink.Line, edge), Prim.Circle((x, y), r - 0.05, fill, body))
      else
        val outer =
          if dashed then Prim.Poly(ring((x, y), r), closed = true, fill, Dash.Dashed, body)
          else Prim.Circle((x, y), r, fill, body)
        val inner =
          if !double then Nil
          else if dashed then
            List(Prim.Poly(ring((x, y), r - 0.045), closed = true, Ink.Empty, Dash.Dashed, edge))
          else List(Prim.Circle((x, y), r - 0.045, Ink.Empty, edge))
        outer :: inner

    end rings

    private def shape(n: Node): List[Prim] =
      val (x, y)                                   = at(n.id)
      val s                                        = sets(n.id)
      val fill                                     = Ink.Layer(BUSINESS)
      val body                                     = Role.Element(BUSINESS)
      val edge                                     = Role.Mark(BUSINESS)
      def text(
        lines: List[Line],
        left: Double,
        top: Double,
      )                                            =
        Diagram.stack(left, top, lines, style, centred = false)
      val notes                                    =
        if s.notes.isEmpty then Nil
        else
          val bx   = x + s.right - s.notes.map(lineWidth).max - 0.1
          val nh   = leading(s.notes)
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
      def event(trigger: Trigger, filled: Boolean) =
        Diagram.place(
          glyph(trigger, filled),
          x - EVENT_R * 0.55,
          y - EVENT_R * 0.55,
          EVENT_R * 1.1,
          edge,
        )
      val drawn                                    = (n.kind, s.inner) match
        case (Kind.Container(frame), Some(inner))          =>
          val (x0, y0, w, h) = (x - s.half, y - s.tall, 2 * s.half, 2 * s.tall)
          val box            = Prim.Box(
            x0,
            y0,
            w,
            h,
            ROUNDED,
            fill,
            if frame == Frame.Event then Dash.Dotted else Dash.Solid,
            role = Role.Container(BUSINESS),
          )
          val double         =
            if frame == Frame.Transaction then
              List(Prim.Box(
                x0 + 0.05,
                y0 + 0.05,
                w - 0.1,
                h - 0.1,
                ROUNDED - 0.03,
                Ink.Empty,
                role = edge,
              ))
            else Nil
          val top            = y + s.tall - 0.6 * style.padding
          val held = shift(inner.prims, x0 + FRAME_PAD - inner.left, top - leading(s.lines) - 0.05)
          (box :: double) ++ text(s.lines, x0 + style.padding, top) ++ held ++ feet(s, x, y)
        case (Kind.Work(_), _) | (Kind.Container(_), None) =>
          val task     = n.kind match
            case Kind.Work(t) => t
            case _            => Task.Sub
          val box      =
            Prim.Box(x - s.half, y - s.tall, 2 * s.half, 2 * s.tall, ROUNDED, fill, role = body)
          val frame    = n.kind match
            case Kind.Container(Frame.Transaction) =>
              List(Prim.Box(
                x - s.half + 0.05,
                y - s.tall + 0.05,
                2 * s.half - 0.1,
                2 * s.tall - 0.1,
                ROUNDED - 0.03,
                Ink.Empty,
                role = edge,
              ))
            case _                                 => Nil
          val outline  = n.kind match
            case Kind.Container(Frame.Event) => box.copy(dash = Dash.Dotted)
            case _                           => box
          val size     = style.iconSize * ICON
          val (ix, iy) =
            (x + s.half - style.padding * 0.75 - size, y + s.tall - style.padding * 0.75 - size)
          val icon     = task match
            case Task.Send    =>
              Diagram.place(glyph(Trigger.Message, filled = true), ix, iy, size, edge)
            case Task.Receive => Diagram.place(glyph(Trigger.Message), ix, iy, size, edge)
            case _            =>
              iconOf(task).toList.flatMap(i => Diagram.place(Notation.icon(i), ix, iy, size, edge))
          val textH    = leading(s.lines)
          val lift     = if s.feet.nonEmpty then (FOOT + 0.04) / 2 else 0.0
          val aside    = if icon.isEmpty then 0.0 else (style.iconSize * ICON + 0.05) / 2
          (outline :: frame) ++ icon ++ feet(s, x, y) ++ data(s, x, y) ++
          Diagram.stack(x - aside, y + lift + textH / 2, s.lines, style, centred = true)
        case (Kind.Gateway(g), _)                          =>
          val diamond = Prim.Poly(
            List((x, y + GATE), (x + GATE, y), (x, y - GATE), (x - GATE, y)),
            closed = true,
            fill,
            role = body,
          )
          val inner   = Diagram.place(mark(g), x - GATE * 0.55, y - GATE * 0.55, GATE * 1.1, edge)
          val textH   = leading(s.lines)
          diamond :: inner ++ text(s.lines, x + 0.12, y + GATE + textH + 0.04)
        case (Kind.Boundary(t, interrupting), _)           =>
          rings(x, y, double = true, thick = false, dashed = !interrupting) ++ event(
            t,
            filled = false,
          ) ++
          text(s.lines, x + EVENT_R + 0.04, y - EVENT_R * 0.4)
        case (kind, _)                                     =>
          val textH = leading(s.lines)
          val drawn = kind match
            case Kind.Start(t, interrupting)    =>
              rings(x, y, double = false, thick = false, dashed = !interrupting) ++ event(
                t,
                filled = false,
              )
            case Kind.Intermediate(t, throwing) =>
              rings(x, y, double = true, thick = false, dashed = false) ++ event(t, throwing)
            case Kind.End(t)                    =>
              rings(x, y, double = false, thick = true, dashed = false) ++ event(t, filled = true)
            case _                              => Nil
          drawn ++ text(s.lines, x + EVENT_R + BESIDE, y + textH / 2)
      drawn ++ notes

    end shape

    // the lanes behind it all, each a field with its name in a header
    private val bottom: Double =
      val lowest = (rowBottom.lastOption.toList ++ (routes.map(_._2) ++ linked ++ messages.map(_._2))
        .flatMap(_.map(_._2))).minOption
      lowest.getOrElse(-header) - LAST

    private val stage: List[Prim] =
      if !laned then Nil
      else
        lanes.zipWithIndex.flatMap: (lane, li) =>
          val x0    = laneX(li)
          val w     = laneW(li)
          val lines = headerLines(lane)
          val textH = leading(lines)
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
      val ties            = linked.map(ps => Prim.Connector(ps, Dash.Dotted, None, Some(Head.Barb)))
      val sent            = messages.map((_, ps) =>
        Prim.Connector(ps, Dash.Dashed, Some(Head.OpenDot), Some(Head.OpenTriangle))
      )
      val shapes          = cells.flatMap(n => shape(n) ++ onHost(n.id).flatMap(shape))
      val conditions      = routes.flatMap((f, ps) => condition(f.label, ps))
      val captions        = messages.flatMap((m, ps) => caption(m.label, ps))
      def isText(p: Prim) = p.isInstanceOf[Prim.Text]
      stage.filterNot(isText) ++ stage.filter(isText) ++ lines.map(
        _._2
      ) ++ strokes ++ gaps ++ ties ++
      shapes.filterNot(isText) ++ shapes.filter(isText) ++ sent ++ conditions ++ captions

    end prims

    private val (minX, _, maxX, _) =
      Extent.of(Diagram.Drawing("", prims, Scene("", 0, 0, Nil, Nil), style, "", 0), 0)

    val left: Double   = if prims.isEmpty then 0.0 else minX
    val width: Double  = if prims.isEmpty then 0.0 else maxX - minX
    val height: Double = -bottom

    val overflow: Int = sets.values.map(_.overflow).sum

  end Layout

end Activity
