package fish.genius.uml.diagram

import zio.test.*

import fish.genius.uml.diagram.Activity.*

object ActivitySpec extends ZIOSpecDefault:

  private val order = DiagramGallery.order
  private val drawn = Activity.draw(order)

  private def arrows(d: Diagram.Drawing) =
    d.prims.collect { case c @ Prim.Connector(_, Dash.Solid, None, Some(Head.FilledTriangle)) => c }

  private def texts(d: Diagram.Drawing) = d.prims.collect { case t: Prim.Text => t }

  // the shapes of the nodes: task boxes, gateway diamonds and event circles, as boxes
  private def shapes(d: Diagram.Drawing): List[(Double, Double, Double, Double)] =
    d.prims.collect:
      case Prim.Box(x, y, w, h, _, _, _, _, Role.Element(_))     => (x, y, x + w, y + h)
      case Prim.Poly(ps, true, _, _, Role.Element(_))            =>
        (ps.map(_._1).min, ps.map(_._2).min, ps.map(_._1).max, ps.map(_._2).max)
      case Prim.Circle((x, y), r, _, Role.Element(_)) if r > 0.1 => (x - r, y - r, x + r, y + r)

  private def segments(points: List[Pt]) = points.zip(points.drop(1))

  private val claim = Activity.draw(DiagramGallery.claim)

  // the middles of the stretches of flows that lie inside a node's shape
  private def through(d: Diagram.Drawing): List[Pt] =
    for
      c      <- arrows(d)
      (a, b) <- segments(c.points)
      if math.hypot(b._1 - a._1, b._2 - a._2) > 0.3
      (mx, my) = ((a._1 + b._1) / 2, (a._2 + b._2) / 2)
      (x0, y0, x1, y1) <- shapes(d)
      if x0 + 0.02 < mx && mx < x1 - 0.02 && y0 + 0.02 < my && my < y1 - 0.02
    yield (mx, my)

  private def overlaps(d: Diagram.Drawing) =
    val boxes = shapes(d)
    for
      (a, i) <- boxes.zipWithIndex
      (b, j) <- boxes.zipWithIndex
      if i < j && a._1 < b._3 && b._1 < a._3 && a._2 < b._4 && b._2 < a._4
    yield (a, b)

  // the centre of the shape whose name is `label`: the one its text stands beside
  private def centre(d: Diagram.Drawing, label: String): Pt =
    val t                =
      texts(d).find(_.text == label.split(" ").head).orElse(texts(d).find(_.text.startsWith(label.split(" ").head))).get
    val (x0, y0, x1, y1) =
      shapes(d).minBy(b => math.hypot((b._1 + b._3) / 2 - t.baseline._1, (b._2 + b._4) / 2 - t.baseline._2))
    ((x0 + x1) / 2, (y0 + y1) / 2)

  override def spec =
    suite("Activity")(
      test("every flow is drawn with an arrowhead, every node in its shape"):
        assertTrue(
          arrows(drawn).size == order.flows.size,
          shapes(drawn).size == order.nodes.size,
          drawn.overflow == 0,
        )
      ,
      test("no flow runs through a node it does not join, in either process"):
        assertTrue(through(drawn).isEmpty, through(claim).isEmpty)
      ,
      test("in the claim, no two nodes overlap, every flow is drawn, and it is the same twice"):
        assertTrue(
          overlaps(claim).isEmpty,
          arrows(claim).size == DiagramGallery.claim.flows.size,
          Activity.draw(DiagramGallery.claim).prims == claim.prims,
          claim.overflow == 0,
        )
      ,
      test("branches into a gateway join from above when its side is taken by a branch out"):
        val (gx, gy) = centre(claim, "Amount agreed?")
        val ends     = arrows(claim).map(_.points.last)
        val corners  = ends.filter(p => math.abs(p._2 - gy) < 1e-6 && math.abs(p._1 - gx) > 0.1)
        val tops     = ends.filter(p => math.abs(p._1 - gx) < 1e-6 && math.abs(p._2 - gy - 0.27) < 1e-3)
        // report, fraud and the loop back all come in at the top: no corner is shared
        assertTrue(corners.isEmpty, tops.size == 3)
      ,
      test("a loop leaves an event with its name beside it from the bottom, not through the name"):
        val (wx, wy) = centre(claim, "Two weeks pass")
        val loop     = arrows(claim).filter(c => c.points.last._2 > c.points.head._2 + 1e-6)
        assertTrue(
          loop.size == 1,
          math.abs(loop.head.points.head._1 - wx) < 1e-6 && loop.head.points.head._2 < wy,
        )
      ,
      test("a note stands beside its node in a bracket, tied to it by a dotted line"):
        val words  = texts(claim).map(_.text)
        val dotted = claim.prims.collect { case c @ Prim.Connector(_, Dash.Dotted, None, None) => c }
        assertTrue(words.contains("Within ten working days"), dotted.size == 1)
      ,
      test("tasks keep their widest when there is room, and narrow when there is not"):
        def wide(d: Diagram.Drawing) =
          val (a, _, b, _) = Extent.of(d, 0); b - a
        val roomy                    = Activity.draw(DiagramGallery.order, Activity.Options(pageWidth = 30.0))
        assertTrue(wide(drawn) < wide(roomy)) // on 13 cm the tasks narrow, with room they keep their widest
      ,
      test("two identical flows are drawn once"):
        val twice = Activity.draw(order.copy(flows = order.flows :+ order.flows.head))
        assertTrue(arrows(twice).size == order.flows.size)
      ,
      test("the order's flows run clear of its nodes"):
        val boxes  = shapes(drawn)
        val inside =
          for
            c                <- arrows(drawn)
            (a, b)           <- segments(c.points)
            (x0, y0, x1, y1) <- boxes
            // a stretch's middle part, so the ends at the shapes it joins do not count
            (mx, my) = ((a._1 + b._1) / 2, (a._2 + b._2) / 2)
            len      = math.hypot(b._1 - a._1, b._2 - a._2)
            if len > 0.3
            if x0 + 0.02 < mx && mx < x1 - 0.02 && y0 + 0.02 < my && my < y1 - 0.02
          yield (mx, my)
        assertTrue(inside.isEmpty)
      ,
      test("no two nodes overlap, and each stands in its lane's column"):
        val boxes = shapes(drawn)
        val pairs =
          for
            (a, i) <- boxes.zipWithIndex
            (b, j) <- boxes.zipWithIndex
            if i < j
            if a._1 < b._3 && b._1 < a._3 && a._2 < b._4 && b._2 < a._4
          yield (a, b)
        val lanes = drawn.prims.collect { case Prim.Box(x, _, w, _, _, Ink.Empty, _, _, Role.Group) =>
          (x, x + w)
        }
        assertTrue(
          pairs.isEmpty,
          lanes.size == order.lanes.size,
          boxes.forall(b => lanes.exists((l0, l1) => l0 <= b._1 + 1e-6 && b._3 <= l1 + 1e-6)),
        )
      ,
      test("the flow runs down the page, and a loop goes back up round the right"):
        val down = arrows(drawn).filter(c => c.points.head._2 > c.points.last._2 + 1e-6)
        val up   = arrows(drawn).filter(c => c.points.last._2 > c.points.head._2 + 1e-6)
        assertTrue(
          up.size == 1,
          down.size == order.flows.size - 1,
          // into the right side of its target, from the right
          up.head.points.reverse.take(2) match
            case List(end, before) => before._1 > end._1 && math.abs(before._2 - end._2) < 1e-6
            case _                 => false,
        )
      ,
      test("a branch leaves a gateway from the corner facing it, with its condition there"):
        val ok      = shapes(drawn).find(b =>
          b._3 - b._1 < 0.6 && b._4 - b._2 < 0.6 &&
          texts(drawn).exists(t => t.text == "Complete?" && t.baseline._2 > b._4)
        )
        val corners = ok.toList.flatMap:
          (
            x0,
            y0,
            x1,
            y1,
          ) =>
            val cy = (y0 + y1) / 2
            arrows(drawn).map(_.points.head).filter(p =>
              math.abs(p._2 - cy) < 1e-6 && (math.abs(p._1 - x0) < 1e-6 || math.abs(p._1 - x1) < 1e-6)
            )
        val strokes = drawn.prims.collect { case c @ Prim.Connector(_, _, None, None) => c }
        assertTrue(
          corners.size == 2,
          texts(drawn).exists(_.text == "yes"),
          texts(drawn).exists(_.text == "no"),
          // the default branch's stroke, besides the gaps where lines cross
          strokes.exists(_.points.size == 2),
        )
      ,
      test("a task says its name and its systems, an event and a gateway their names"):
        val words = texts(drawn).map(_.text)
        assertTrue(
          words.contains("[CRM]"),
          words.contains("Order placed"),
          words.contains("Complete?"),
          words.contains("Due in 30 days"),
          words.contains("Customer"),
          words.contains("Warehouse"),
        )
      ,
      test("without lanes there is one column and no header; a node in no lane gets its own"):
        val bare                      = Activity.draw(order.copy(lanes = Nil))
        val loose                     = Activity.draw(order.copy(
          nodes = order.nodes :+ Node(
            "x",
            "Archive",
            Kind.Work(Task.Plain),
          ),
          flows = order.flows :+ Flow("done", "x"),
        ))
        def lanes(d: Diagram.Drawing) = d.prims.count:
          case Prim.Box(_, _, _, _, _, Ink.Empty, _, _, Role.Group) => true
          case _                                                    => false
        assertTrue(lanes(bare) == 0, lanes(loose) == order.lanes.size + 1)
      ,
      test("a flow to an unknown node is left out, and an empty process still draws"):
        val odd = Activity.draw(order.copy(flows = order.flows :+ Flow("check", "nowhere")))
        val nil = Activity.draw(Graph("Nothing", Nil, Nil, Nil))
        assertTrue(arrows(odd).size == order.flows.size, nil.prims.isEmpty)
      ,
      test("a task loops to itself round its right side"):
        val self = Activity.draw(Graph(
          "Retry",
          Nil,
          List(Node("t", "Try", Kind.Work(Task.Plain))),
          List(Flow("t", "t")),
        ))
        val loop = arrows(self)
        assertTrue(
          loop.size == 1,
          loop.head.points.size == 4,
          loop.head.points.head._1 == loop.head.points.last._1,
        )
      ,
      test("the same process gives the same drawing, and TikZ, SVG and PNG write it"):
        val tex = Tikz.standalone(drawn)
        val svg = Svg.document(drawn)
        assertTrue(
          Activity.draw(order).prims == drawn.prims,
          tex.contains("Sales desk"),
          svg.contains("Sales desk"),
          Png.image(drawn, 100).length > 1000,
        )
      ,
      test("a lane's performer shows by its icon, in its own layer's colour"):
        val marks = drawn.prims.collect { case Prim.Poly(_, _, _, _, Role.Mark(l)) => l }.toSet
        assertTrue(marks.contains("am-business")),
    )

end ActivitySpec
