package fish.genius.uml.diagram

import zio.test.*

import fish.genius.uml.diagram.Sequence.*
import fish.genius.uml.dsl.archimate.ShapeType

object SequenceSpec extends ZIOSpecDefault:

  private val checkout = DiagramGallery.checkout
  private val drawn    = Sequence.draw(checkout)
  private val measure  = Measure(Typeface.heros)

  private def connectors(d: Diagram.Drawing) = d.prims.collect { case c: Prim.Connector => c }

  private def texts(d: Diagram.Drawing) = d.prims.collect { case t: Prim.Text => t }

  // the rectangle a left-set line of text covers
  private def rect(t: Prim.Text) =
    val w = measure.width(t.text, t.size, t.weight)
    (
      t.baseline._1,
      t.baseline._2 - 0.3 * t.size * Diagram.PT,
      t.baseline._1 + w,
      t.baseline._2 + 0.75 * t.size * Diagram.PT,
    )

  private def sends(steps: List[Step]): List[Step.Send] = steps.flatMap:
    case s: Step.Send     => List(s)
    case Step.Loop(_, in) => sends(in)

  override def spec =
    suite("Sequence")(
      test("every message is drawn, numbered in order, with the ends its kind asks for"):
        val all     = sends(checkout.steps)
        val lines   = connectors(drawn)
        val ends    = lines.map(c => (c.dash, c.end))
        val kinds   = all.map(_.kind).map:
          case Kind.Call   => (Dash.Solid, Some(Head.FilledTriangle))
          case Kind.Reply  => (Dash.Dashed, Some(Head.Barb))
          case Kind.Signal => (Dash.Solid, Some(Head.Barb))
        val numbers = texts(drawn).filter(_.weight == Weight.SemiBold).map(_.text)
          .filter(_.forall(_.isDigit))
        assertTrue(
          lines.size == all.size,
          ends == kinds,
          numbers == (1 to all.size).map(_.toString).toList,
          drawn.overflow == 0,
        )
      ,
      test("messages go down the page, each one lower than the one before"):
        val ys = connectors(drawn).map(_.points.head._2)
        assertTrue(ys.zip(ys.drop(1)).forall((a, b) => b < a))
      ,
      test("no word stands on a lifeline, and no label on the edge of a field"):
        val lifelines = drawn.prims.collect {
          case Prim.Poly(List(a, b), false, _, Dash.Dashed, Role.Mark(_), _) => (a._1, b._2, a._2)
        }
        val edges     = drawn.prims.collect { case Prim.Box(x, _, w, _, _, _, _, _, Role.Container(_), _) =>
          List(x, x + w)
        }.flatten
        val labels    = texts(drawn).filterNot(_.centred).filter(_.size < 7.0).map(rect)
        val crossed   =
          for
            (x0, y0, x1, y1) <- labels
            (x, low, high)   <- lifelines
            if x0 < x && x < x1 && low < y1 && y0 < high
          yield (x, y0)
        val onEdge    = labels.filter(
          (
            x0,
            _,
            x1,
            _,
          ) => edges.exists(e => x0 < e && e < x1)
        )
        assertTrue(lifelines.nonEmpty, crossed.isEmpty, onEdge.isEmpty)
      ,
      test("a loop's frame holds its messages, and a loop with nothing in it is left out"):
        val frames = drawn.prims.collect { case Prim.Poly(ps, true, Ink.Empty, Dash.Solid, Role.Plain, _) =>
          ps
        }
        val inside = connectors(drawn).filter(_.end.contains(Head.Barb)).filter(_.dash == Dash.Solid)
        val empty  = Sequence.draw(checkout.copy(steps = checkout.steps :+ Step.Loop("never", Nil)))
        assertTrue(
          frames.size == 1,
          inside.forall: c =>
            frames.head.map(_._2).min < c.points.head._2 && c.points.head._2 <
            frames.head.map(_._2).max,
          empty.prims == drawn.prims,
        )
      ,
      test("a message to an unknown part is left out, a message to itself turns back"):
        val odd  = checkout.copy(steps =
          List(Step.Send("api", "nobody", "lost"), Step.Send("api", "api", "Think it over"))
        )
        val self = connectors(Sequence.draw(odd))
        assertTrue(
          self.size == 1,
          self.head.points.size == 4,
          self.head.points.head._1 == self.head.points.last._1,
        )
      ,
      test("the parts of a system stand on one field, with the system's name in its header"):
        val fields = drawn.prims.collect { case b @ Prim.Box(_, _, _, _, _, _, _, _, Role.Container(_), _) => b }
        val heads  = drawn.prims.collect { case b @ Prim.Box(_, _, _, _, _, _, _, _, Role.Element(_), _) => b }
        val shop   = fields.head
        val inShop = heads.count(h => h.x >= shop.x && h.x + h.w <= shop.x + shop.w)
        assertTrue(
          fields.size == 1,
          heads.size == checkout.lifelines.size,
          inShop == 3,
          texts(drawn).exists(t => t.text == "Web Shop" && t.weight == Weight.SemiBold),
        )
      ,
      test("labels wrap rather than push the diagram wider than the page"):
        val long           = checkout.copy(steps =
          List(Step.Send(
            "customer",
            "psp",
            "A very long message that would never fit on one line between two lifelines at all",
          ))
        )
        val d              = Sequence.draw(long, Sequence.Options(pageWidth = 13.0))
        val (x0, _, x1, _) = Extent.of(d, 0)
        assertTrue(x1 - x0 <= 13.0, texts(d).count(_.text.startsWith("A very")) == 1)
      ,
      test("the same sequence gives the same drawing, and TikZ, SVG and PNG write it"):
        val tex = Tikz.standalone(drawn)
        val svg = Svg.document(drawn)
        val png = Png.image(drawn, 100)
        assertTrue(
          Sequence.draw(checkout).prims == drawn.prims,
          tex.contains("Storefront"),
          svg.contains("Storefront"),
          png.length > 1000,
        )
      ,
      test("a frame inside another sits further in, and a long condition wraps"):
        val cond           = "until every one of the many outstanding payments has been settled or refused"
        val nested         = checkout.copy(steps =
          List(Step.Loop(
            cond,
            List(
              Step.Send("spa", "api", "Poll"),
              Step.Loop("each line", List(Step.Send("api", "db", "Read a line"))),
            ),
          ))
        )
        val d              = Sequence.draw(nested)
        val frames         = d.prims.collect { case Prim.Poly(ps, true, Ink.Empty, Dash.Solid, Role.Plain, _) =>
          (ps.map(_._1).min, ps.map(_._1).max)
        }
        val guards         = texts(d).filter(t => t.weight == Weight.Italic && t.tone == Emphasis.Muted)
          .filterNot(_.size > 6.0)
        val (x0, _, x1, _) = Extent.of(d, 0)
        assertTrue(
          frames.size == 2,
          frames(1)._1 > frames(0)._1 || frames(0)._1 > frames(1)._1,
          guards.count(_.text.startsWith("[until")) == 1,
          guards.size > 2,
          x1 - x0 <= 13.0,
        )
      ,
      test("unnumbered, the messages carry their labels alone"):
        val d = Sequence.draw(checkout, Sequence.Options(numbered = false))
        assertTrue(texts(d).forall(t => !(t.weight == Weight.SemiBold && t.text.forall(_.isDigit))))
      ,
      test("a label width under the narrowest still draws"):
        val d = Sequence.draw(checkout, Sequence.Options(labelWidth = 1.0))
        assertTrue(connectors(d).size == sends(checkout.steps).size)
      ,
      test("a sequence without parts still draws"):
        val d = Sequence.draw(Graph("Nothing", Nil, List(Step.Send("a", "b", "x"))))
        assertTrue(connectors(d).isEmpty)
      ,
      test("an actor head is in the business colour, a component in the application one"):
        val d     = Sequence.draw(Graph(
          "Two",
          List(
            Lifeline("a", "Clerk", ShapeType.BusinessActor),
            Lifeline(
              "b",
              "Ledger",
              ShapeType.ApplicationComponent,
            ),
          ),
          List(Step.Send("a", "b", "Books it")),
        ))
        val heads = d.prims.collect { case Prim.Box(_, _, _, _, _, _, _, _, Role.Element(l), _) => l }
        assertTrue(heads == List("am-business", "am-application")),
    )

end SequenceSpec
