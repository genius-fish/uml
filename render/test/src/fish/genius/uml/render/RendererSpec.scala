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
    test("test engine writes a placeholder for SVG output"):
      ZIO.scoped:
        for
          path <- Renderer.svg(archimateDoc, "archimate-test")
          body <- ZIO.attempt(os.read(path)).orDie
        yield assertTrue(
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
          Renderer.render(sequenceDoc, "diagram", f).map: p =>
            assertTrue(p.toString.endsWith(f.getFileSuffix))
      .map(_.reduce(_ && _))
    ,
    test("renderBytes returns a non-empty byte stream"):
      PUmlEngine
        .renderBytes(sequenceDoc, FileFormat.SVG)
        .map(b => assertTrue(b.nonEmpty)),
  ).provide(PUmlEngine.test)

end RendererSpec
