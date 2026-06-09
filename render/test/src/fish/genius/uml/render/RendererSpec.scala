package fish.genius.uml.render

import zio.test.*

import zio.*

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.archimate.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.dsl.sequence.*

import net.sourceforge.plantuml.FileFormat

object RendererSpec extends ZIOSpecDefault:

  given ArchimateConfiguration = ArchimateConfiguration()

  /** A small Archimate diagram, used by both the live and test engines. */
  private val archimateDoc = block:
    uml:
      archimateDiagram:
        val customer = shape(BusinessActor, label("Customer"))
        val store    = shape(ApplicationComponent, label("Web store"))
        relationship(RelationshipType.Serving)(store)(customer)

  /** A small sequence diagram, used by both the live and test engines. */
  private val sequenceDoc = block:
    uml:
      sequenceDiagram:
        val a = actor("Alice")
        val b = participant("Bob")
        step("Hello")(a)(b)

  def spec = suite("Renderer")(
    test("test engine writes a placeholder under out/svg/test"):
      ZIO.scoped:
        for
          path <- DiagramOutput.save(archimateDoc, DiagramOutput.Test, "archimate-test", FileFormat.SVG)
          body <- ZIO.attempt(os.read(path)).orDie
        yield assertTrue(
          path == DiagramOutput.path(DiagramOutput.Test, "archimate-test", FileFormat.SVG),
          path.toString.endsWith(".svg"),
          body.startsWith("%FAKE-SVG"),
          body.contains("@startuml"),
          body.contains("Web store"),
        )
    ,
    test("test engine round-trips through every supported FileFormat"):
      val formats = List(FileFormat.SVG, FileFormat.PNG, FileFormat.EPS, FileFormat.LATEX)
      ZIO.foreach(formats): f =>
        ZIO.scoped:
          DiagramOutput.save(sequenceDoc, DiagramOutput.Test, "diagram", f).map: p =>
            assertTrue(p == DiagramOutput.path(DiagramOutput.Test, "diagram", f))
      .map(_.reduce(_ && _))
    ,
    test("renderBytes returns a non-empty byte stream"):
      PUmlEngine
        .renderBytes(sequenceDoc, FileFormat.SVG)
        .map(b => assertTrue(b.nonEmpty))
    ,
    test("path lays out out/<format>/<category>/<name><suffix>"):
      val svg = DiagramOutput.path(DiagramOutput.Examples, "web-store", FileFormat.SVG)
      val eps = DiagramOutput.path(DiagramOutput.Test, "diagram", FileFormat.EPS)
      assertTrue(
        DiagramOutput.formatDir(FileFormat.SVG) == "svg",
        DiagramOutput.formatDir(FileFormat.PNG) == "png",
        DiagramOutput.formatDir(FileFormat.LATEX) == "tex",
        svg.segments.toList.endsWith(List("out", "svg", "examples", "web-store.svg")),
        eps.segments.toList.endsWith(List("out", "eps", "test", "diagram.eps")),
        DiagramOutput.dir(DiagramOutput.Test, FileFormat.EPS) == eps / os.up,
      )
    ,
    test("save surfaces filesystem failures as PUmlError.Internal"):
      ZIO.scoped:
        for
          tmp <- ZIO.acquireRelease(ZIO.attempt(os.temp.dir()))(d =>
            ZIO.attempt(os.remove.all(d)).orDie
          ).orDie
          src     = tmp / "src.svg"
          // A regular file where `copyOut` will need a directory, so makeDir.all fails.
          blocker = tmp / "blocker"
          _   <- ZIO.attempt(os.write(src, "data")).orDie
          _   <- ZIO.attempt(os.write(blocker, "x")).orDie
          res <- DiagramOutput.copyOut(src, blocker / "sub" / "out.svg").either
        yield assertTrue(res match
          case Left(PUmlError.Internal(_)) => true
          case _                           => false),
  ).provide(PUmlEngine.test)

end RendererSpec
