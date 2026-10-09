package fish.genius.uml.scene

import scala.annotation.tailrec
import scala.collection.immutable.TreeSet

// A small orthogonal router for the routes ELK cannot finish. With hierarchy handling
// on, ELK routes a connector that crosses from one container into another in pieces
// and leaves the stretch between them out. SceneRoutes hands those connectors here: a
// shortest path on the grid of lines along the obstacles' sides, with a penalty per
// bend, leaving and entering each plinth straight out of its port.
private[scene] object OrthogonalRouter:

  final case class Box(
    x: Double,
    y: Double,
    w: Double,
    d: Double):
    def left: Double   = x - w / 2
    def right: Double  = x + w / 2
    def bottom: Double = y - d / 2
    def top: Double    = y + d / 2

    def inflate(margin: Double): Box = Box(x, y, w + 2 * margin, d + 2 * margin)

    def contains(px: Double, py: Double): Boolean =
      px > left + EPSILON && px < right - EPSILON && py > bottom + EPSILON && py < top - EPSILON

    def meets(other: Box): Boolean =
      left < other.right && other.left < right && bottom < other.top && other.bottom < top

    // Coordinates come rounded to a thousandth, so "straight" allows that much; a
    // diagonal counts as crossing, since no orthogonal route has one.
    def crosses(
      ax: Double,
      ay: Double,
      bx: Double,
      by: Double,
    ): Boolean =
      if math.abs(ay - by) < STRAIGHT then
        ay > bottom + STRAIGHT && ay < top - STRAIGHT && overlaps(ax, bx, left, right)
      else if math.abs(ax - bx) < STRAIGHT then
        ax > left + STRAIGHT && ax < right - STRAIGHT && overlaps(ay, by, bottom, top)
      else true

    private def overlaps(
      a: Double,
      b: Double,
      low: Double,
      high: Double,
    ): Boolean =
      math.max(math.min(a, b), low) < math.min(math.max(a, b), high) - STRAIGHT

  end Box

  // a unit step out of a port, (dx, dy)
  type Heading = (Int, Int)

  private val EPSILON      = 1e-9
  private val STRAIGHT     = 2e-3
  private val STUB         = 0.35
  private val BEND_PENALTY = 1.5
  private val STEPS        = List((1, 0), (-1, 0), (0, 1), (0, -1))
  // How far a route keeps from a plinth: wide first, closer when it is crowded.
  private val MARGINS      = List(0.3, 0.15, 0.05)
  // Only the plinths this close to the two ends are considered first: the grid, and
  // so the search, grows with the square of the obstacles.
  private val WINDOW       = 4.0

  final private case class State(
    column: Int,
    row: Int,
    heading: Heading)

  final private case class Entry(
    cost: Double,
    order: Int,
    state: State)

  private given Ordering[Entry] =
    Ordering.by[Entry, (Double, Int)](entry => (entry.cost, entry.order))(
      using Ordering.Tuple2(
        using Ordering.Double.TotalOrdering,
        Ordering.Int,
      )
    )

  // The bends between the two ports, or None when the obstacles leave no way through.
  def route(
    start: (Double, Double),
    out: Heading,
    end: (Double, Double),
    in: Heading,
    obstacles: List[Box],
  ): Option[List[(Double, Double)]] =
    val span   = Box(
      (start._1 + end._1) / 2,
      (start._2 + end._2) / 2,
      math.abs(start._1 - end._1),
      math.abs(start._2 - end._2),
    )
    val nearby = obstacles.filter(_.meets(span.inflate(WINDOW)))
    val tries  = MARGINS.flatMap(margin => List(nearby -> margin, obstacles -> margin)).distinct
    tries.iterator
      .flatMap((considered, margin) => attempt(start, out, end, in, considered, margin))
      .find(bends => clearOf(obstacles, start :: bends ::: List(end)))

  end route

  private def clearOf(obstacles: List[Box], points: List[(Double, Double)]): Boolean =
    points.zip(points.drop(1)).forall((a, b) => !obstacles.exists(_.crosses(a._1, a._2, b._1, b._2)))

  private def attempt(
    start: (Double, Double),
    out: Heading,
    end: (Double, Double),
    in: Heading,
    obstacles: List[Box],
    margin: Double,
  ): Option[List[(Double, Double)]] =
    val walls   = obstacles.map(_.inflate(margin))
    val leaving = (start._1 + out._1 * STUB, start._2 + out._2 * STUB)
    val arrival = (end._1 + in._1 * STUB, end._2 + in._2 * STUB)
    val columns =
      (List(leaving._1, arrival._1) ++
      walls.flatMap(b => List(b.left, b.right))).distinct.sorted.toVector
    val rows    =
      (List(leaving._2, arrival._2) ++
      walls.flatMap(b => List(b.bottom, b.top))).distinct.sorted.toVector
    val grid    = Grid(columns, rows, walls)
    val first   = State(columns.indexOf(leaving._1), rows.indexOf(leaving._2), out)
    val goal    = (columns.indexOf(arrival._1), rows.indexOf(arrival._2), (-in._1, -in._2))
    search(grid, goal, TreeSet(Entry(0, 0, first)), Map(first -> 0.0), Map.empty, 1)
      .map((last, previous) => corners(trace(last, previous).map(s => (columns(s.column), rows(s.row)))))

  end attempt

  final private case class Grid(
    columns: Vector[Double],
    rows: Vector[Double],
    walls: List[Box]):

    def inside(column: Int, row: Int): Boolean =
      column >= 0 && column < columns.size && row >= 0 && row < rows.size

    def free(column: Int, row: Int): Boolean = !walls.exists(_.contains(columns(column), rows(row)))

    def clear(
      from: State,
      column: Int,
      row: Int,
    ): Boolean =
      !walls.exists(_.crosses(columns(from.column), rows(from.row), columns(column), rows(row)))

    def length(
      from: State,
      column: Int,
      row: Int,
    ): Double =
      math.abs(columns(column) - columns(from.column)) + math.abs(rows(row) - rows(from.row))

  end Grid

  // Dijkstra over (grid point, heading), so that a bend can be charged for.
  @tailrec
  private def search(
    grid: Grid,
    goal: (Int, Int, Heading),
    frontier: TreeSet[Entry],
    best: Map[State, Double],
    previous: Map[State, State],
    order: Int,
  ): Option[(State, Map[State, State])] =
    frontier.headOption match
      case None                                                                              => None
      case Some(entry) if entry.cost > best.getOrElse(entry.state, Double.MaxValue)          =>
        search(grid, goal, frontier - entry, best, previous, order)
      case Some(entry) if (entry.state.column, entry.state.row, entry.state.heading) == goal =>
        Some((entry.state, previous))
      case Some(entry)                                                                       =>
        val here   = entry.state
        val better = STEPS
          .filter(step => step != (-here.heading._1, -here.heading._2))
          .flatMap: step =>
            val (column, row) = (here.column + step._1, here.row + step._2)
            val arriving      = (column, row) == (goal._1, goal._2)
            Option.when(
              grid.inside(column, row) && (grid.free(column, row) || arriving) &&
              grid.clear(here, column, row)
            ):
              val bend = if step != here.heading then BEND_PENALTY else 0.0
              (State(column, row, step), entry.cost + grid.length(here, column, row) + bend)
          .filter((next, cost) => cost < best.getOrElse(next, Double.MaxValue))
        val queued = better.zipWithIndex.map { case ((next, cost), index) =>
          Entry(cost, order + index, next)
        }
        search(
          grid,
          goal,
          frontier - entry ++ queued,
          best ++ better,
          previous ++ better.map((next, _) => next -> here),
          order + better.size,
        )
  end search

  private def trace(last: State, previous: Map[State, State]): List[State] =
    Iterator.iterate(Option(last))(
      _.flatMap(previous.get)
    ).takeWhile(_.isDefined).flatten.toList.reverse

  // keep only the points where the route turns, and both ends
  private def corners(points: List[(Double, Double)]): List[(Double, Double)] =
    points.zipWithIndex.collect:
      case (point, index)
        if index == 0 || index == points.size - 1 ||
        turns(points(index - 1), point, points(index + 1)) =>
        point

  private def turns(
    a: (Double, Double),
    b: (Double, Double),
    c: (Double, Double),
  ): Boolean =
    val vertical   = math.abs(a._1 - b._1) < EPSILON && math.abs(b._1 - c._1) < EPSILON
    val horizontal = math.abs(a._2 - b._2) < EPSILON && math.abs(b._2 - c._2) < EPSILON
    !(vertical || horizontal)

end OrthogonalRouter
