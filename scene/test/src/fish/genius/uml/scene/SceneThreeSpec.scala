package fish.genius.uml.scene

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.LocalDate

import scala.util.Try

import zio.json.*
import zio.test.*

import zio.{Scope, ZIO}

// The three.js shapes carry the passport's pane too, laid out like the layout and the
// Blender builder lay it out. The heights are compared by running the shapes in node,
// with no three.js loaded (the layout needs none); without node that test is ignored.
object SceneThreeSpec extends ZIOSpecDefault:

  private val js = SceneBuilder.threeJs.getOrElse("")

  // archimate3d.js's PANE table: "width": 2.1, ...
  private val paneTable =
    val start = js.indexOf("var PANE = {")
    """"([A-Za-z]+)": ([\d.]+)""".r
      .findAllMatchIn(js.slice(start, js.indexOf("}", start)))
      .map(m => m.group(1) -> m.group(2).toDouble)
      .toMap

  private val node = sys.env.getOrElse("NODE", "node")

  private val hasNode: Boolean =
    Try(new ProcessBuilder(node, "--version").redirectErrorStream(true).start().waitFor() == 0).getOrElse(false)

  private val asOf = LocalDate.of(2026, 10, 13)

  private val passports: List[Passport] = List(
    Passport("Node", "A"),
    Passport(
      "Application component",
      "Customer Relationship Management Platform for Sales and Service",
      Some("CRM · SaaS on a platform somebody else runs, with a subtitle long enough to wrap"),
      List(
        PassportSection.Text(List.fill(40)("opportunity").mkString(" ")),
        PassportSection.Facts(
          Some("Ownership"),
          List(Fact("Owner", "Sales Operations, Customer Care and the Service Desk"), Fact("Assigned", "Account Manager")),
        ),
        PassportSection.Facts(None, List(Fact("Pace layer", "Systems of differentiation"))),
        PassportSection.Timeline(
          "Lifecycle",
          List(
            Milestone("Planned", LocalDate.of(2019, 9, 1), Tone.Neutral),
            Milestone("Active", LocalDate.of(2021, 3, 15), Tone.Layer),
          ),
          asOf,
          Some("Active since Mar 2021 · phasing out from Jan 2028, and a summary long enough to wrap twice over"),
        ),
        PassportSection.Timeline("Lifecycle", Nil, asOf, None),
        PassportSection.Rated(
          "Criticality",
          Chip("Critical", Tone.Critical, Some(List.fill(20)("deliveries").mkString(" "))),
        ),
        PassportSection.Rated("TIME verdict", Chip("Invest", Tone.Good, None)),
        PassportSection.Rating("Security", List(Mark("C", "H", Tone.Warning)), Some("Personal data, GDPR article 9")),
      ),
      Some("as of 13 Oct 2026"),
    ),
    Passport("Données", "Système de gestion des commandes", Some("Référentiel · données à caractère personnel")),
  )

  private val sameHeight =
    test("a pane in three.js is as tall as the layout made it"):
      ZIO.scoped:
        for
          dir     <- ZIO.acquireRelease(ZIO.attemptBlocking(Files.createTempDirectory("scene-three")))(dir =>
            ZIO.attemptBlocking {
              Files.list(dir).forEach(Files.delete(_))
              Files.delete(dir)
            }.orDie
          )
          heights <- ZIO.attemptBlocking:
            val shapes = Files.writeString(dir.resolve("archimate3d.js"), js, StandardCharsets.UTF_8)
            val input  = Files.writeString(dir.resolve("passports.json"), passports.toJson, StandardCharsets.UTF_8)
            val script =
              s"""globalThis.window = { THREE: {} }; globalThis.document = { fonts: null };
                 |require(${shapes.toString.toJson});
                 |const ps = JSON.parse(require('fs').readFileSync(${input.toString.toJson}, 'utf8'));
                 |console.log(JSON.stringify(ps.map(p => window.AM3D.paneHeight(p))));""".stripMargin
            val run    = new ProcessBuilder(node, "-e", script).redirectErrorStream(true).start()
            val out    = new String(run.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
            run.waitFor()
            out.trim.fromJson[List[Double]]
        yield assertTrue(heights == Right(passports.map(PaneLayout.height)))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("scene / the three.js shapes carry the pane")(
      test("the three.js shapes lay a pane out with the layout's measures"):
        assertTrue(paneTable.size == PaneLayout.metrics.size, PaneLayout.metrics.toMap == paneTable)
      ,
      test("the three.js shapes know every section kind and tone the layout writes"):
        assertTrue(
          List("text", "facts", "rated", "rating", "timeline").forall(kind => js.contains(s"s.kind === '$kind'")),
          Tone.values.toList.filterNot(_ == Tone.Layer).map(_.toJson.stripPrefix("\"").stripSuffix("\"")).forall(t =>
            js.contains(s"$t: '#")
          ),
        )
      ,
      sameHeight @@ (if hasNode then TestAspect.identity else TestAspect.ignore),
    )

end SceneThreeSpec
