package fish.genius.uml.diagram

// A connector's ends as geometry, for the writers that have no arrowheads of their own
// (Svg, Png): drawn over the line's end, the open ones filled with paper so the line
// does not show through. Sizes in points, as Tikz gives them to arrows.meta.
object Heads:

  private def cm(points: Double): Double = points * Diagram.PT

  // the heads of a connector, at its start and its end
  def of(connector: Prim.Connector): List[Prim] =
    val ps    = connector.points
    val start =
      connector.start.toList.flatMap(h => ps.drop(1).headOption.map(from => at(h, ps.head, from)))
    val end   =
      connector.end.toList.flatMap(h => ps.dropRight(1).lastOption.map(from => at(h, ps.last, from)))
    (start ++ end).flatten

  // a head with its tip at `tip`, pointing away from `from`
  def at(
    head: Head,
    tip: Pt,
    from: Pt,
  ): List[Prim] =
    val (dx, dy)                          = (tip._1 - from._1, tip._2 - from._2)
    val length                            = math.max(math.hypot(dx, dy), 1e-9)
    val (ux, uy)                          = (dx / length, dy / length)
    def p(back: Double, side: Double): Pt =
      (tip._1 - ux * cm(back) - uy * cm(side), tip._2 - uy * cm(back) + ux * cm(side))
    head match
      case Head.FilledDiamond  =>
        List(Prim.Poly(List(tip, p(3.3, 2.1), p(6.6, 0), p(3.3, -2.1)), closed = true, Ink.Line))
      case Head.OpenDiamond    =>
        List(Prim.Poly(List(tip, p(3.3, 2.1), p(6.6, 0), p(3.3, -2.1)), closed = true, Ink.Paper))
      case Head.Dot            => List(Prim.Circle(p(1.5, 0), cm(1.5), Ink.Line))
      case Head.OpenDot        => List(Prim.Circle(p(1.8, 0), cm(1.8), Ink.Paper))
      // Stealth: a filled point with its back cut in
      case Head.FilledTriangle =>
        List(Prim.Poly(List(tip, p(5.2, 1.95), p(3.9, 0), p(5.2, -1.95)), closed = true, Ink.Line))
      case Head.OpenTriangle   =>
        List(Prim.Poly(List(tip, p(6, 2.7), p(6, -2.7)), closed = true, Ink.Paper))
      case Head.Barb => List(Prim.Poly(List(p(2.6, 2.5), tip, p(2.6, -2.5)), closed = false))

  end at

end Heads
