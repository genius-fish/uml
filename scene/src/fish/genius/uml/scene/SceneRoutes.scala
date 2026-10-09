package fish.genius.uml.scene

// Finishing a connector's route: ELK's route made to meet the ports exactly; where ELK
// left it unfinished between containers, a route of our own around the plinths; and as
// a last resort, when the plinths leave no way through, a plain orthogonal route that is
// flagged as not clear.
final private[scene] class SceneRoutes(elements: List[SceneElement]):
  import SceneRoutes.*

  private val plinths =
    elements.filterNot(_.container).map(element =>
      OrthogonalRouter.Box(element.x, element.y, element.w, element.d)
    )

  def finish(
    source: SceneElement,
    from: PortName,
    bends: List[ScenePoint],
    target: SceneElement,
    to: PortName,
  ): Route =
    val (start, end) = (portOf(source, from), portOf(target, to))
    val snapped      = snap(start, from, bends, end, to)
    if isClear(start :: snapped ::: List(end)) then Route(snapped, clear = true)
    else
      OrthogonalRouter
        .route((start.x, start.y), heading(from), (end.x, end.y), heading(to), plinths)
        .map(points =>
          Route(
            points.map((x, y) => ScenePoint(SceneLayout.round(x), SceneLayout.round(y))),
            clear = true,
          )
        )
        .getOrElse(Route(snap(start, from, Nil, end, to), clear = false))

  end finish

  // straight segments only, and none through a plinth
  private def isClear(points: List[ScenePoint]): Boolean =
    points.zip(points.drop(1)).forall: (here, next) =>
      (math.abs(here.x - next.x) < STRAIGHT || math.abs(here.y - next.y) < STRAIGHT) &&
      !plinths.exists(_.crosses(here.x, here.y, next.x, next.y))

end SceneRoutes

private[scene] object SceneRoutes:

  final case class Route(bends: List[ScenePoint], clear: Boolean)

  private val STRAIGHT    = 2e-3
  private val ON_THE_LINE = 1e-3

  // the middle of a plinth side, where the builder puts the port
  def portOf(element: SceneElement, side: PortName): ScenePoint = side match
    case PortName.North => ScenePoint(element.x, SceneLayout.round(element.y + element.d / 2))
    case PortName.South => ScenePoint(element.x, SceneLayout.round(element.y - element.d / 2))
    case PortName.West  => ScenePoint(SceneLayout.round(element.x - element.w / 2), element.y)
    case PortName.East  => ScenePoint(SceneLayout.round(element.x + element.w / 2), element.y)

  private def heading(side: PortName): OrthogonalRouter.Heading = side match
    case PortName.North => (0, 1)
    case PortName.South => (0, -1)
    case PortName.West  => (-1, 0)
    case PortName.East  => (1, 0)

  private def isVertical(side: PortName): Boolean = side == PortName.North || side == PortName.South

  // ELK puts a container's port where it likes on its side, a few hundredths off the
  // middle; moving the first and last bend along with the end keeps every segment
  // straight. With no bends at all it makes the plainest orthogonal route.
  private def snap(
    start: ScenePoint,
    from: PortName,
    bends: List[ScenePoint],
    end: ScenePoint,
    to: PortName,
  ): List[ScenePoint] =
    def align(
      point: ScenePoint,
      port: ScenePoint,
      side: PortName,
    ): ScenePoint =
      if isVertical(side) then point.copy(x = port.x) else point.copy(y = port.y)
    bends match
      case Nil           => plain(start, from, end, to)
      case single :: Nil => List(align(align(single, start, from), end, to))
      case first :: rest =>
        rest.lastOption.fold(List(align(first, start, from))): last =>
          align(first, start, from) :: rest.dropRight(1) ::: List(align(last, end, to))

  end snap

  private def plain(
    start: ScenePoint,
    from: PortName,
    end: ScenePoint,
    to: PortName,
  ): List[ScenePoint] =
    if isVertical(from) && isVertical(to) && math.abs(start.x - end.x) > ON_THE_LINE then
      val middle = SceneLayout.round((start.y + end.y) / 2)
      List(ScenePoint(start.x, middle), ScenePoint(end.x, middle))
    else if !isVertical(from) && !isVertical(to) && math.abs(start.y - end.y) > ON_THE_LINE then
      val middle = SceneLayout.round((start.x + end.x) / 2)
      List(ScenePoint(middle, start.y), ScenePoint(middle, end.y))
    else if isVertical(from) != isVertical(to) then
      List(if isVertical(from) then ScenePoint(start.x, end.y) else ScenePoint(end.x, start.y))
    else Nil

end SceneRoutes
