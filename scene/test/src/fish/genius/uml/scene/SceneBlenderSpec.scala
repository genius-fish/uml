package fish.genius.uml.scene

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import zio.json.*
import zio.test.*

import zio.{Scope, Task, ZIO}

import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*

// The Blender builder run for real, headless. Opt in with BLENDER_AVAILABLE=1, with
// blender on the PATH or named by BLENDER; without it the suite is ignored, so
// make validate needs no Blender.
object SceneBlenderSpec extends ZIOSpecDefault:

  // what the builder printed, its "archimate3d: …" lines
  final private case class Run(lines: List[String]):

    private def number(pattern: String): Option[Double] =
      lines.flatMap(pattern.r.findFirstMatchIn(_)).headOption.map(_.group(1).toDouble)

    def placardPx: Option[Double] = number("""a placard's name renders about ([\d.]+) px""")
    def checkGap: Option[Double]  = number("""connector ends are within ([\d.]+) of""")

    // (elements, connectors) in the light
    def lit: Option[(Int, Int)] =
      lines
        .flatMap("""(\d+) elements and (\d+) connectors in the light""".r.findFirstMatchIn(_))
        .headOption
        .map(m => (m.group(1).toInt, m.group(2).toInt))

    // (steps, frames)
    def flyover: Option[(Int, Int)] =
      lines
        .flatMap("""flyover of .*: (\d+) steps, (\d+) frames""".r.findFirstMatchIn(_))
        .headOption
        .map(m => (m.group(1).toInt, m.group(2).toInt))

    def notFound: List[String] =
      lines.flatMap("""nothing called '(.*)' in the view""".r.findFirstMatchIn(_).map(_.group(1)))

    // (names, names not freed, pairs still covering)
    def floating: Option[(Int, Int, Int)] =
      lines
        .flatMap("""(\d+) floating labels, (\d+) not freed, (\d+) covering pairs""".r.findFirstMatchIn(_))
        .headOption
        .map(m => (m.group(1).toInt, m.group(2).toInt, m.group(3).toInt))

  end Run

  private val blender = sys.env.getOrElse("BLENDER", "blender")

  private def build(scene: Scene, options: String*): Task[Run] =
    ZIO.attemptBlocking:
      val dir     = Files.createTempDirectory("scene-blender")
      val view    = Files.writeString(dir.resolve("view.json"), scene.toJson, StandardCharsets.UTF_8)
      val script  = Files.writeString(
        dir.resolve(SceneBuilder.BLENDER_SCRIPT),
        SceneBuilder.blenderScript.getOrElse(""),
        StandardCharsets.UTF_8,
      )
      val command = List(blender, "-b", "--factory-startup", "-P", script.toString, "--", "--view", view.toString) ++
        options
      val process = new ProcessBuilder(command*).redirectErrorStream(true).start()
      val output  = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
      process.waitFor()
      Run(output.linesIterator.filter(_.startsWith("archimate3d:")).toList)

  private def laidOut(graph: SceneGraph): Task[Scene] =
    ZIO.fromEither(SceneLayout.layout(graph)).mapError(error => RuntimeException(error.toString))

  // every element but a junction carries a name
  private def named(scene: Scene): Int = scene.elements.count(e => !e.shape.endsWith("-junction"))

  private def node(id: String, shape: fish.genius.uml.dsl.archimate.ShapeType): SceneNode =
    SceneNode(SceneId(id), NodeKind.Element(shape), id)

  private val small = SceneGraph(
    "Small",
    List(node("customer", BusinessActor), node("ordering", BusinessProcess), node("shop", ApplicationComponent)),
    List(
      SceneEdge(SceneId("assigned"), SceneId("customer"), SceneId("ordering"), Assignment),
      SceneEdge(SceneId("serves"), SceneId("shop"), SceneId("ordering"), Serving),
    ),
    List(
      SceneFlow(
        SceneId("buying"),
        "Buying",
        List(
          SceneStep(SceneId("customer"), SceneId("ordering"), "places an order"),
          SceneStep(SceneId("ordering"), SceneId("shop"), "records it"),
        ),
      )
    ),
  )

  // the first bytes of an MP4 file: a size, then "ftyp"
  private def isMp4(file: Path): Boolean =
    Files.isRegularFile(file) && Files.size(file) > 1000 && {
      val head = Files.newInputStream(file)
      try new String(head.readNBytes(8), StandardCharsets.US_ASCII).endsWith("ftyp")
      finally head.close()
    }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("scene / the Blender builder, headless")(
      test("a small view keeps its placards, and its connectors follow a moved element"):
        for run <- laidOut(small).flatMap(build(_, "--check"))
        yield assertTrue(run.placardPx.exists(_ >= 9), run.floating.isEmpty, run.checkGap.exists(_ < 0.05))
      ,
      test("a large view floats every name, none covering another"):
        for
          scene <- laidOut(SceneChecks.large)
          run   <- build(scene, "--size", "2400x1500")
        yield assertTrue(run.placardPx.exists(_ < 9), run.floating.contains((named(scene), 0, 0)))
      ,
      test("--labels float and --labels placard force either"):
        for
          floated <- laidOut(small).flatMap(build(_, "--labels", "float"))
          placard <- laidOut(SceneChecks.large).flatMap(build(_, "--labels", "placard"))
        yield assertTrue(floated.floating.map(_._1).contains(3), placard.floating.isEmpty)
      ,
      test("--highlight lights an element and a connector, and names what it cannot find"):
        for run <- laidOut(small).flatMap(build(_, "--highlight", "shop,customer>ordering,nowhere"))
        yield assertTrue(run.lit.contains((1, 1)), run.notFound == List("nowhere"))
      ,
      test("--flyover walks a flow's steps, and --animation writes them as a video"):
        for
          video <- ZIO.attemptBlocking(Files.createTempDirectory("scene-video").resolve("buying.mp4"))
          run   <- laidOut(small).flatMap(
            build(_, "--flyover", "Buying", "--animation", video.toString, "--size", "320x180", "--fps", "6")
          )
          made  <- ZIO.attemptBlocking(isMp4(video))
        yield assertTrue(run.flyover.exists((steps, frames) => steps == 2 && frames > 20), made),
    ) @@ TestAspect.ifEnvSet("BLENDER_AVAILABLE") @@ TestAspect.sequential

end SceneBlenderSpec
