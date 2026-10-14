package fish.genius.uml.diagram

import zio.test.*

import zio.*

// The tidying of routes after the layout, on routes made by hand: they stay orthogonal,
// their ends stay put, and a second pass changes nothing.
object DiagramRoutesSpec extends ZIOSpecDefault:

  private def orthogonal(ps: List[Pt]): Boolean =
    ps.zip(ps.drop(1)).forall((a, b) => math.abs(a._1 - b._1) < 1e-9 || math.abs(a._2 - b._2) < 1e-9)

  private def close(a: Pt, b: Pt) = math.abs(a._1 - b._1) < 1e-9 && math.abs(a._2 - b._2) < 1e-9

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("diagram / routes tidied")(
      test("a tiny step square to the first stretch goes, the start sliding along its side"):
        // down from a box's south side, a 0.05 step right, down to the target
        val route = List((0.0, 0.0), (0.0, -1.0), (0.05, -1.0), (0.05, -2.0))
        val done  = Diagram.straighten(route)
        assertTrue(
          orthogonal(done),
          close(done.last, route.last),
          done.head._2 == 0.0, // the start stays on its side, only slid along it
          done.zip(done.drop(1)).forall((a, b) => math.hypot(b._1 - a._1, b._2 - a._2) >= 0.1 - 1e-9 || close(a, b)),
          Diagram.straighten(done) == done,
        )
      ,
      test("a step that is not square to the first stretch is taken off the end instead"):
        // down from a south side, right, a tiny step down, then right into a west side:
        // the step is square to the last stretch only, so the end slides up its side
        val route = List((0.0, 0.0), (0.0, -1.0), (1.0, -1.0), (1.0, -1.05), (2.0, -1.05))
        val done  = Diagram.straighten(route)
        assertTrue(
          orthogonal(done),
          close(done.head, route.head),
          done.last._1 == 2.0, // the end stays on its side, only slid along it
          Diagram.straighten(done) == done,
        )
      ,
      test("two routes on top of one another are spread apart, their ends where they were"):
        val a            = List((0.0, 2.0), (0.0, 1.0), (1.0, 1.0), (1.0, -1.0), (0.0, -1.0), (0.0, -2.0))
        val b            = List((2.0, 2.0), (2.0, 1.0), (1.0, 1.0), (1.0, -1.0), (2.0, -1.0), (2.0, -2.0))
        val List(na, nb) = Diagram.nudge(List(a, b)): @unchecked
        assertTrue(
          orthogonal(na),
          orthogonal(nb),
          close(na.head, a.head) && close(na.last, a.last),
          close(nb.head, b.head) && close(nb.last, b.last),
          math.abs(math.abs(na(2)._1 - nb(2)._1) - 0.12) < 1e-9,
          // the one coming from the left stays left
          na(2)._1 < nb(2)._1,
        )
      ,
      test("a spread that would turn a short stretch round is not made"):
        // b's neighbouring stretches are only 0.03 long: moving it 0.06 would reverse one
        val a = List((0.0, 2.0), (0.0, 1.0), (1.0, 1.0), (1.0, -1.0), (0.0, -1.0), (0.0, -2.0))
        val b = List((1.03, 2.0), (1.03, 1.0), (1.0, 1.0), (1.0, -1.0), (1.03, -1.0), (1.03, -2.0))
        assertTrue(Diagram.nudge(List(a, b)) == List(a, b))
      ,
      test("colours mix as xcolor mixes them"):
        assertTrue(
          Look.mix("FFFFFF", 50, "000000") == "808080",
          Look.mix("C8E3DF", 100, "251A06") == "C8E3DF",
          Look.mix("C8E3DF", 0, "251A06") == "251A06",
        ),
    )

end DiagramRoutesSpec
