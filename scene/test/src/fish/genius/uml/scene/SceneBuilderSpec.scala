package fish.genius.uml.scene

import zio.test.*

import zio.Scope

object SceneBuilderSpec extends ZIOSpecDefault:

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("scene / SceneBuilder and OrthogonalRouter")(
      test("the Blender builder ships in the jar and reads a scene's ports and bends"):
        val script = SceneBuilder.blenderScript.getOrElse("")
        assertTrue(
          script.contains("def build_view"),
          script.contains("sourcePort"),
          script.contains("bends"),
        )
      ,
      test("the three.js shapes ship in the jar"):
        assertTrue(SceneBuilder.threeJs.exists(_.contains("window.AM3D")))
      ,
      test("the fallback router goes around a plinth in the way, straight out of each port"):
        import OrthogonalRouter.Box
        val (a, wall, b) = (Box(0, 0, 2, 1.3), Box(3, 0, 2, 1.3), Box(6, 0, 2, 1.3))
        val bends        = OrthogonalRouter.route((1, 0), (1, 0), (5, 0), (-1, 0), List(a, wall, b)).getOrElse(Nil)
        val points       = (1.0, 0.0) :: bends ::: List((5.0, 0.0))
        val segments     = points.zip(points.drop(1))
        assertTrue(
          bends.nonEmpty,
          segments.forall { case ((x0, y0), (x1, y1)) => x0 == x1 || y0 == y1 },
          !segments.exists { case ((x0, y0), (x1, y1)) => List(a, wall, b).exists(_.crosses(x0, y0, x1, y1)) },
          bends.headOption.exists((x, y) => y == 0.0 && x > 1.0),
          bends.lastOption.exists((x, y) => y == 0.0 && x < 5.0),
        )
      ,
      test("the fallback router gives up on a port walled in on every side"):
        import OrthogonalRouter.Box
        // a closed ring of walls around the target's port: nothing gets in
        val ring = List(Box(4, 0, 0.4, 4.6), Box(7, 0, 0.4, 4.6), Box(5.5, 2.2, 3.4, 0.4), Box(5.5, -2.2, 3.4, 0.4))
        assertTrue(OrthogonalRouter.route((1, 0), (1, 0), (5, 0), (-1, 0), Box(0, 0, 2, 1.3) :: ring).isEmpty),
    )

end SceneBuilderSpec
