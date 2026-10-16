package fish.genius.uml.diagram

import fish.genius.uml.scene.Glyph

// The signs a diagram stamps on a box or sets on a line, drawn as one family so they read
// as one set at 6 pt in print and on a slide (APP-2260): on one grid of 24 units, every
// line 2 units wide with round caps and joins, inside a live area from 2 to 22. A solid
// sign is filled and stroked at the same weight, so its corners round as a line's do.
// Sizes are optical: a circle's diameter 20 units, a square's side 18, a triangle and a
// diamond reach the live area's edges, as a type designer sizes an O against an H.
// Everything is a vector path (Prim.Path), never a font's glyph, so TikZ, SVG and PNG
// draw the same outline. A sign is drawn in the unit square, y up; Diagram.place scales
// it, its weight with it.
object Signs:

  val GRID: Double           = 24.0
  val STROKE: Double         = 2.0 / GRID
  // the live area, as a fraction of the sign: where every sign's ink stays
  val LIVE: (Double, Double) = (2.0 / GRID, 22.0 / GRID)

  private val K = 0.5522847498 // a quarter circle as one cubic

  private def u(p: Pt): Pt = (p._1 / GRID, p._2 / GRID)

  // closed outlines and open strokes, in grid units
  private def polygon(points: Pt*): List[Seg] =
    Seg.Move(u(points.head)) :: points.toList.tail.map(p => Seg.Line(u(p))) ::: List(Seg.Close)

  private def polyline(points: Pt*): List[Seg] =
    Seg.Move(u(points.head)) :: points.toList.tail.map(p => Seg.Line(u(p)))

  private def ellipse(
    c: Pt,
    rx: Double,
    ry: Double,
  ): List[Seg] =
    val (x, y) = c
    List(
      Seg.Move(u((x + rx, y))),
      Seg.Cubic(u((x + rx, y + K * ry)), u((x + K * rx, y + ry)), u((x, y + ry))),
      Seg.Cubic(u((x - K * rx, y + ry)), u((x - rx, y + K * ry)), u((x - rx, y))),
      Seg.Cubic(u((x - rx, y - K * ry)), u((x - K * rx, y - ry)), u((x, y - ry))),
      Seg.Cubic(u((x + K * rx, y - ry)), u((x + rx, y - K * ry)), u((x + rx, y))),
      Seg.Close,
    )

  private def circle(c: Pt, r: Double): List[Seg] = ellipse(c, r, r)

  // an arc of a circle from a0 to a1 degrees (counter-clockwise when a1 > a0), in cubics
  // of at most a quarter turn; `from` false continues the path it follows
  private def arc(
    c: Pt,
    r: Double,
    a0: Double,
    a1: Double,
    from: Boolean = true,
  ): List[Seg] =
    val n                 = math.max(1, math.ceil(math.abs(a1 - a0) / 90.0 - 1e-9).toInt)
    val step              = (a1 - a0) / n
    def at(a: Double): Pt =
      (c._1 + r * math.cos(math.toRadians(a)), c._2 + r * math.sin(math.toRadians(a)))
    val k                 = 4.0 / 3.0 * math.tan(math.toRadians(step) / 4)
    val start             = if from then List(Seg.Move(u(at(a0)))) else Nil
    start ++ (0 until n).toList.map: i =>
      val (s, e)   = (a0 + i * step, a0 + (i + 1) * step)
      val (ps, pe) = (at(s), at(e))
      val (rs, re) = (math.toRadians(s), math.toRadians(e))
      Seg.Cubic(
        u((ps._1 - k * r * math.sin(rs), ps._2 + k * r * math.cos(rs))),
        u((pe._1 + k * r * math.sin(re), pe._2 - k * r * math.cos(re))),
        u(pe),
      )

  end arc

  // a rectangle with round corners of radius r, from its lower left corner
  private def rounded(
    x: Double,
    y: Double,
    w: Double,
    h: Double,
    r: Double,
  ): List[Seg] =
    List(Seg.Move(u((x + r, y))), Seg.Line(u((x + w - r, y)))) ++
    arc((x + w - r, y + r), r, -90, 0, from = false) ++ List(Seg.Line(u((x + w, y + h - r)))) ++
    arc((x + w - r, y + h - r), r, 0, 90, from = false) ++ List(Seg.Line(u((x + r, y + h)))) ++
    arc((x + r, y + h - r), r, 90, 180, from = false) ++ List(Seg.Line(u((x, y + r)))) ++
    arc((x + r, y + r), r, 180, 270, from = false) ++ List(Seg.Close)

  private def around(
    n: Int,
    r: Double,
    c: Pt = (12.0, 12.0),
    turn: Double = 90.0,
  ): List[Pt] = (0 until n).toList.map: i =>
    val a = math.toRadians(turn + i * 360.0 / n)
    (c._1 + r * math.cos(a), c._2 + r * math.sin(a))

  // a line at the family's weight, and a shape filled and stroked at it
  private def stroke(segs: List[Seg], evenOdd: Boolean = false): Prim =
    Prim.Path(segs, Ink.Empty, STROKE, evenOdd = evenOdd)

  private def solid(segs: List[Seg], evenOdd: Boolean = false): Prim =
    Prim.Path(segs, Ink.Line, STROKE, evenOdd = evenOdd)

  // a full stop, as the family sets one: a disc as wide as a line and a half
  private def dot(c: Pt): Prim = Prim.Path(circle(c, 1.5), Ink.Line, 0.0)

  // the signs that sit in a frame: the frame and what it holds
  private val badgeFrame = rounded(4, 4, 16, 16, 2.5)

  def of(g: Glyph): List[Prim] = g match
    // the twelve geometric glyphs, solid, to tell things apart
    case Glyph.Circle       => List(solid(circle((12, 12), 9)))
    case Glyph.Square       => List(solid(polygon((4, 4), (20, 4), (20, 20), (4, 20))))
    case Glyph.TriangleUp   => List(solid(polygon((3, 4.5), (21, 4.5), (12, 20.5))))
    case Glyph.TriangleDown => List(solid(polygon((3, 19.5), (21, 19.5), (12, 3.5))))
    case Glyph.Diamond      => List(solid(polygon((12, 3), (21, 12), (12, 21), (3, 12))))
    case Glyph.Star         =>
      val outer = around(5, 9.4, (12, 11.4))
      val inner = around(5, 4.2, (12, 11.4), 90.0 + 36.0)
      List(solid(polygon(outer.zip(inner).flatMap((a, b) => List(a, b))*)))
    case Glyph.Hexagon      => List(solid(polygon(around(6, 9.0)*)))
    case Glyph.Pentagon     => List(solid(polygon(around(5, 9.4, (12, 11.4))*)))
    case Glyph.Cross        =>
      List(
        solid(
          polygon(
            (9.5, 3.5),
            (14.5, 3.5),
            (14.5, 9.5),
            (20.5, 9.5),
            (20.5, 14.5),
            (14.5, 14.5),
            (14.5, 20.5),
            (9.5, 20.5),
            (9.5, 14.5),
            (3.5, 14.5),
            (3.5, 9.5),
            (9.5, 9.5),
          )
        )
      )
    // a thick ring: a disc with a hole, its rim as wide as five units
    case Glyph.Ring      => List(solid(circle((12, 12), 9) ++ circle((12, 12), 6), evenOdd = true))
    case Glyph.Ellipse   => List(solid(ellipse((12, 12), 9, 5)))
    case Glyph.Chevron   =>
      List(solid(polygon((3, 18), (12, 11), (21, 18), (21, 12.5), (12, 5), (3, 12.5))))
    // a public API: the globe, its meridian and its equator
    case Glyph.Globe     =>
      List(
        stroke(circle((12, 12), 9)),
        stroke(ellipse((12, 12), 4, 9)),
        stroke(polyline((3, 12), (21, 12))),
      )
    // a closed API: a solid body under an open shackle
    case Glyph.Padlock   =>
      List(
        stroke(polyline((8, 11)) ++ List(Seg.Line(u((8, 15)))) ++
          arc((12, 15), 4, 180, 0, from = false) ++
          List(Seg.Line(u((16, 11))))),
        solid(rounded(5, 3, 14, 9, 1.5)),
      )
    // a health that is degraded: a warning triangle with its exclamation mark
    case Glyph.Warning   =>
      List(
        stroke(polygon((3, 4), (21, 4), (12, 20.5))),
        stroke(polyline((12, 14), (12, 10))),
        dot((12, 7)),
      )
    // a health that is failing: a circle struck through by a cross
    case Glyph.Failure   =>
      List(
        stroke(circle((12, 12), 9)),
        stroke(polyline((8.5, 8.5), (15.5, 15.5))),
        stroke(polyline((8.5, 15.5), (15.5, 8.5))),
      )
    // what a gap does to an element, in a frame: adds it, modifies it, removes it
    case Glyph.GapAdd    =>
      List(
        stroke(badgeFrame),
        stroke(polyline((12, 8), (12, 16))),
        stroke(polyline((8, 12), (16, 12))),
      )
    case Glyph.GapModify =>
      List(
        stroke(badgeFrame),
        stroke(
          List(
            Seg.Move(u((7.5, 10.5))),
            Seg.Cubic(u((8.5, 13.5)), u((10.5, 14)), u((12, 12))),
            Seg.Cubic(u((13.5, 10)), u((15.5, 10.5)), u((16.5, 13.5))),
          )
        ),
      )
    case Glyph.GapRemove => List(stroke(badgeFrame), stroke(polyline((8, 12), (16, 12))))
    // phased out: the sun setting on the horizon, and going down
    case Glyph.PhaseOut  =>
      List(
        stroke(polyline((3, 9), (21, 9))),
        stroke(arc((12, 9), 5, 0, 180)),
        stroke(polyline((12, 16.5), (12, 20))),
        stroke(polyline((17.3, 14.3), (19.8, 16.8))),
        stroke(polyline((6.7, 14.3), (4.2, 16.8))),
        stroke(polyline((9.5, 5.5), (12, 3), (14.5, 5.5))),
      )
    // an open risk: a shield with an exclamation mark, a shape apart from the warning's
    case Glyph.Risk      =>
      List(
        stroke(
          List(
            Seg.Move(u((12, 21))),
            Seg.Line(u((19.5, 18.5))),
            Seg.Line(u((19.5, 12))),
            Seg.Cubic(u((19.5, 7.5)), u((16, 4.5)), u((12, 3))),
            Seg.Cubic(u((8, 4.5)), u((4.5, 7.5)), u((4.5, 12))),
            Seg.Line(u((4.5, 18.5))),
            Seg.Close,
          )
        ),
        stroke(polyline((12, 16), (12, 11.5))),
        dot((12, 8.2)),
      )
    // an open decision: the flowchart's decision diamond, asking
    case Glyph.Decision  =>
      List(
        stroke(polygon((12, 3), (21, 12), (12, 21), (3, 12))),
        stroke(arc((12, 14), 2.6, 165, -45) ++ List(Seg.Line(u((12, 10.8))))),
        dot((12, 7.4)),
      )
    // the styles of an API, for the middle of a line: REST's request and response,
    // GraphQL's graph in a hexagon, gRPC's stream, an event's lightning, a batch's stack
    case Glyph.Rest      =>
      List(
        stroke(polyline((4, 16), (19.5, 16))),
        stroke(polyline((16.5, 19), (19.5, 16), (16.5, 13))),
        stroke(polyline((20, 8), (4.5, 8))),
        stroke(polyline((7.5, 11), (4.5, 8), (7.5, 5))),
      )
    case Glyph.GraphQl   =>
      val hex = around(6, 9.0)
      List(
        stroke(polygon(hex*)),
        stroke(polygon(hex(0), hex(2), hex(4))),
      )
    case Glyph.Grpc      =>
      List(
        stroke(polyline((4.5, 19), (11.5, 12), (4.5, 5))),
        stroke(polyline((12.5, 19), (19.5, 12), (12.5, 5))),
      )
    case Glyph.Event     =>
      List(solid(polygon(
        (14, 21),
        (6, 11),
        (11.5, 11),
        (9.5, 3),
        (18, 13.5),
        (12.5, 13.5),
        (15.5, 21),
      )))
    case Glyph.Batch     =>
      List(
        stroke(polygon((3, 3), (15, 3), (15, 13), (3, 13))),
        stroke(polyline((6, 13), (6, 16), (18, 16), (18, 6), (15, 6))),
        stroke(polyline((9, 16), (9, 19), (21, 19), (21, 9), (18, 9))),
      )

end Signs
