package fish.genius.uml.scene

import scala.annotation.tailrec
import scala.collection.immutable.TreeSet

// A small orthogonal router for the routes ELK cannot finish. With hierarchy handling
// on, ELK routes a connector that crosses from one container into another in pieces
// and leaves the stretch between them out. SceneLayout hands those connectors here: a
// shortest path on the grid of lines along the obstacles' sides, with a penalty per
// bend, leaving and entering each plinth straight out of its port.
private[scene] object OrthogonalRouter:

  final case class Box(
    x: Double,
    y: Double,
    w: Double,
    d: Double):
    def x0: Double = x - w / 2
    def x1: Double = x + w / 2
    def y0: Double = y - d / 2
    def y1: Double = y + d / 2

    def inflate(margin: Double): Box = Box(x, y, w + 2 * margin, d + 2 * margin)

    def contains(px: Double, py: Double): Boolean =
      px > x0 + EPSILON && px < x1 - EPSILON && py > y0 + EPSILON && py < y1 - EPSILON

    // Coordinates come rounded to a thousandth, so "straight" allows that much; a
    // diagonal counts as crossing, since no orthogonal route has one.
    def crosses(
      ax: Double,
      ay: Double,
      bx: Double,
      by: Double,
    ): Boolean =
      if math.abs(ay - by) < STRAIGHT then
        ay > y0 + STRAIGHT && ay < y1 - STRAIGHT && overlaps(ax, bx, x0, x1)
      else if math.abs(ax - bx) < STRAIGHT then
        ax > x0 + STRAIGHT && ax < x1 - STRAIGHT && overlaps(ay, by, y0, y1)
      else true

    private def overlaps(
      a: Double,
      b: Double,
      lo: Double,
      hi: Double,
    ): Boolean =
      math.max(math.min(a, b), lo) < math.min(math.max(a, b), hi) - STRAIGHT

  end Box

  // a unit step out of a port, (dx, dy)
  type Heading = (Int, Int)

  private val EPSILON      = 1e-9
  private val STRAIGHT     = 2e-3
  private val MARGIN       = 0.3
  private val STUB         = 0.35
  private val BEND_PENALTY = 1.5
  private val STEPS        = List((1, 0), (-1, 0), (0, 1), (0, -1))

  final private case class State(
    i: Int,
    j: Int,
    heading: Heading)

  final private case class Entry(
    cost: Double,
    order: Int,
    state: State)

  private given Ordering[Entry] =
    Ordering.by[Entry, (Double, Int)](e => (e.cost, e.order))(
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
    val walls = obstacles.map(_.inflate(MARGIN))
    val from  = (start._1 + out._1 * STUB, start._2 + out._2 * STUB)
    val to    = (end._1 + in._1 * STUB, end._2 + in._2 * STUB)
    val xs = (List(from._1, to._1) ++ walls.flatMap(b => List(b.x0, b.x1))).distinct.sorted.toVector
    val ys = (List(from._2, to._2) ++ walls.flatMap(b => List(b.y0, b.y1))).distinct.sorted.toVector
    val grid  = Grid(xs, ys, walls)
    val first = State(xs.indexOf(from._1), ys.indexOf(from._2), out)
    val goal  = (xs.indexOf(to._1), ys.indexOf(to._2), (-in._1, -in._2))
    search(grid, goal, TreeSet(Entry(0, 0, first)), Map(first -> 0.0), Map.empty, 1)
      .map((last, previous) => corners(trace(last, previous).map(s => (xs(s.i), ys(s.j)))))

  end route

  final private case class Grid(
    xs: Vector[Double],
    ys: Vector[Double],
    walls: List[Box]):
    def inside(i: Int, j: Int): Boolean = i >= 0 && i < xs.size && j >= 0 && j < ys.size
    def free(i: Int, j: Int): Boolean   = !walls.exists(_.contains(xs(i), ys(j)))

    def clear(
      a: State,
      i: Int,
      j: Int,
    ): Boolean =
      !walls.exists(_.crosses(xs(a.i), ys(a.j), xs(i), ys(j)))

    def length(
      a: State,
      i: Int,
      j: Int,
    ): Double =
      math.abs(xs(i) - xs(a.i)) + math.abs(ys(j) - ys(a.j))

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
      case None                                                                       => None
      case Some(entry) if entry.cost > best.getOrElse(entry.state, Double.MaxValue)   =>
        search(grid, goal, frontier - entry, best, previous, order)
      case Some(entry) if (entry.state.i, entry.state.j, entry.state.heading) == goal =>
        Some((entry.state, previous))
      case Some(entry)                                                                =>
        val here   = entry.state
        val better = STEPS
          .filter(step => step != (-here.heading._1, -here.heading._2))
          .flatMap: step =>
            val (i, j) = (here.i + step._1, here.j + step._2)
            val arrive = (i, j) == (goal._1, goal._2)
            Option.when(grid.inside(i, j) && (grid.free(i, j) || arrive) && grid.clear(here, i, j)):
              val bend = if step != here.heading then BEND_PENALTY else 0.0
              (State(i, j, step), entry.cost + grid.length(here, i, j) + bend)
          .filter((next, cost) => cost < best.getOrElse(next, Double.MaxValue))
        val queued = better.zipWithIndex.map { case ((next, cost), k) => Entry(cost, order + k, next) }
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
      case (p, k) if k == 0 || k == points.size - 1 || turns(points(k - 1), p, points(k + 1)) => p

  private def turns(
    a: (Double, Double),
    b: (Double, Double),
    c: (Double, Double),
  ): Boolean =
    val vertical   = math.abs(a._1 - b._1) < EPSILON && math.abs(b._1 - c._1) < EPSILON
    val horizontal = math.abs(a._2 - b._2) < EPSILON && math.abs(b._2 - c._2) < EPSILON
    !(vertical || horizontal)

end OrthogonalRouter
