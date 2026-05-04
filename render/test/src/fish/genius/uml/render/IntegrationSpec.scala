package fish.genius.uml.render

import zio.test.*

import zio.*

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.archimate.*
import fish.genius.uml.dsl.archimate.ShapeType.*

import net.sourceforge.plantuml.FileFormat

/**
 * Live integration test: invokes the real PlantUML library against a small
 * Archimate diagram and verifies that the produced SVG bytes look like SVG.
 *
 * PlantUML ships with everything bundled in the jar, so no external binary
 * is required — the test always runs.
 */
object IntegrationSpec extends ZIOSpecDefault:

  given ArchimateConfiguration = ArchimateConfiguration()

  private val doc = block:
    uml:
      archimateDiagram:
        val customer = shape(BusinessActor, label("Customer"))
        val store    = shape(ApplicationComponent, label("Web store"))
        relationship(RelationshipType.Serving)(store)(customer)

  def spec = suite("Live PlantUML rendering")(
    test("live engine produces an SVG that starts with `<?xml` or `<svg`"):
      PUmlEngine
        .renderBytes(doc)
        .map: bytes =>
          val head = new String(bytes.take(64), "UTF-8")
          assertTrue(
            bytes.length > 64,
            head.contains("<svg") || head.startsWith("<?xml"),
          )
    ,
    test("live engine writes an SVG file under a scoped workspace"):
      ZIO.scoped:
        for
          path <- Renderer.svg(doc, "integration")
          size <- ZIO.attempt(os.size(path)).orDie
        yield assertTrue(
          path.toString.endsWith(".svg"),
          size > 64,
        )
    ,
    test("live engine produces an EPS that starts with the PostScript header"):
      PUmlEngine
        .renderBytes(doc, FileFormat.EPS)
        .map: bytes =>
          val head = new String(bytes.take(32), "ISO-8859-1")
          assertTrue(
            bytes.length > 64,
            head.startsWith("%!PS-Adobe"),
          )
    ,
    test("live engine writes an EPS file under a scoped workspace"):
      ZIO.scoped:
        for
          path <- Renderer.eps(doc, "integration")
          size <- ZIO.attempt(os.size(path)).orDie
          head <- ZIO.attempt(os.read(path).take(32)).orDie
        yield assertTrue(
          path.toString.endsWith(".eps"),
          size > 64,
          head.startsWith("%!PS-Adobe"),
        ),
  ).provide(PUmlEngine.live)

end IntegrationSpec
