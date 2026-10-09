package fish.genius.uml.scene

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import scala.jdk.CollectionConverters.*

import zio.json.*
import zio.test.*

import zio.{Scope, Task, ZIO}

import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType
import fish.genius.uml.dsl.archimate.ShapeType.*

// The Blender builder run for real, headless. Opt in with BLENDER_AVAILABLE=1, with
// blender on the PATH or named by BLENDER; without it the suite is ignored, so
// make validate needs no Blender. The builder measures what it built and prints it;
// these tests hold it to those measurements.
object SceneBlenderSpec extends ZIOSpecDefault:

  final private case class Lit(
    elements: Int,
    connectors: Int,
    spots: Int,
    ambient: Double)

  final private case class Flight(
    steps: Int,
    along: Int,
    frames: Int,
    margin: Double,
    clearance: Double,
    gap: Double)

  final private case class Floating(
    names: Int,
    stuck: Int,
    covering: Int)

  // what the builder printed, its "archimate3d: …" lines
  final private case class Run(lines: List[String]):

    private def first(pattern: String): Option[List[String]] =
      lines.flatMap(pattern.r.findFirstMatchIn(_)).headOption.map(_.subgroups)

    def placardPx: Option[Double] =
      first("""a placard's name renders about ([\d.]+) px""").map(_.head.toDouble)

    def checkGap: Option[Double] = first("""connector ends are within ([\d.]+) of""").map(_.head.toDouble)

    def lit: Option[Lit] =
      first("""(\d+) elements and (\d+) connectors in the light, (\d+) spots, ambient light at ([\d.]+)""")
        .collect { case List(e, c, s, a) => Lit(e.toInt, c.toInt, s.toInt, a.toDouble) }

    def flight: Option[Flight] =
      for
        counts <- first("""flyover of .*: (\d+) steps, (\d+) along a connector, (\d+) frames""")
        shown  <- first("""stay ([\d.-]+) inside the frame and ([\d.-]+) below the caption; routes within ([\d.]+)""")
      yield Flight(
        counts(0).toInt,
        counts(1).toInt,
        counts(2).toInt,
        shown(0).toDouble,
        shown(1).toDouble,
        shown(2).toDouble,
      )

    def floating: Option[Floating] =
      first("""(\d+) floating labels, (\d+) not freed, (\d+) covering pairs""")
        .collect { case List(n, s, c) => Floating(n.toInt, s.toInt, c.toInt) }

    def notFound: List[String] =
      lines.flatMap("""nothing called '(.*)' in the view""".r.findFirstMatchIn(_).map(_.group(1)))

    def says(text: String): Boolean = lines.exists(_.contains(text))

  end Run

  private val blender = sys.env.getOrElse("BLENDER", "blender")

  // a working directory for one test, removed afterwards
  private def inDirectory[A](use: Path => Task[A]): Task[A] =
    ZIO.acquireReleaseWith(ZIO.attemptBlocking(Files.createTempDirectory("scene-blender")))(dir =>
      ZIO
        .attemptBlocking(Files.walk(dir).iterator.asScala.toList.reverse.foreach(Files.deleteIfExists))
        .orDie
    )(use)

  private def build(
    dir: Path,
    scene: Scene,
    options: String*
  ): Task[Run] =
    ZIO.attemptBlocking:
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

  private def built(graph: SceneGraph, options: String*): Task[Run] =
    inDirectory(dir => laidOut(graph).flatMap(build(dir, _, options*)))

  private def laidOut(graph: SceneGraph): Task[Scene] =
    ZIO.fromEither(SceneLayout.layout(graph)).mapError(error => RuntimeException(error.toString))

  // every element but a junction carries a name
  private def named(scene: Scene): Int = scene.elements.count(e => !e.shape.endsWith("-junction"))

  // names differ from keys, so a match by name is told apart from one by key
  private def node(
    id: String,
    name: String,
    shape: ShapeType,
  ): SceneNode =
    SceneNode(SceneId(id), NodeKind.Element(shape), name)

  private val small = SceneGraph(
    "Small",
    List(
      node("customer", "Customer", BusinessActor),
      node("ordering", "Ordering", BusinessProcess),
      node("shop", "Web Shop", ApplicationComponent),
    ),
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
          // the connector runs the other way, from the shop to the process
          SceneStep(SceneId("ordering"), SceneId("shop"), "records it"),
        ),
      )
    ),
  )

  private def startsWith(
    file: Path,
    signature: Array[Byte],
    at: Int = 0,
  ): Boolean =
    Files.isRegularFile(file) && Files.size(file) > 1000 && {
      val head = Files.newInputStream(file)
      try head.readNBytes(at + signature.length).drop(at).sameElements(signature)
      finally head.close()
    }

  private val MP4 = "ftyp".getBytes(StandardCharsets.US_ASCII) // after the box size
  private val PNG = Array(0x89, 'P', 'N', 'G').map(_.toByte)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("scene / the Blender builder, headless")(
      test("a small view keeps its placards, and its connectors follow a moved element"):
        for run <- built(small, "--check")
        yield assertTrue(run.placardPx.exists(_ >= 9), run.floating.isEmpty, run.checkGap.exists(_ < 0.05))
      ,
      test("a large view floats every name, none covering another"):
        for
          scene <- laidOut(SceneChecks.large)
          run   <- inDirectory(build(_, scene, "--size", "2400x1500"))
        yield assertTrue(run.placardPx.exists(_ < 9), run.floating.contains(Floating(named(scene), 0, 0)))
      ,
      test("--labels float and --labels placard force either"):
        for
          floated <- built(small, "--labels", "float")
          placard <- built(SceneChecks.large, "--labels", "placard")
        yield assertTrue(floated.floating.map(_.names).contains(3), placard.floating.isEmpty)
      ,
      test("--highlight lights elements by name in any case, connectors either way round, each once"):
        for run <- built(small, "--highlight", "web shop,ordering>customer,Customer,customer,nowhere")
        yield assertTrue(
          // the shop and the customer; the assignment, named from its target; a spot on each
          // element and on the connector's other end; the ambient light dimmed once
          run.lit.contains(Lit(elements = 2, connectors = 1, spots = 3, ambient = 0.2)),
          run.notFound == List("nowhere"),
        )
      ,
      test("--flyover walks a flow's steps along their connectors, and --animation writes a video"):
        inDirectory: dir =>
          val video   = dir.resolve("buying.mp4")
          val options = List("--flyover", "buying", "--animation", video.toString, "--size", "320x180", "--fps", "6")
          for
            run   <- laidOut(small).flatMap(build(dir, _, options*))
            valid <- ZIO.attemptBlocking(startsWith(video, MP4, at = 4))
          yield assertTrue(
            // at 6 fps: 1 + opening 7 + 2 steps × (glide 7 + run 9 + hold 4) + closing 8 + 7
            run.flight.exists(f => f.steps == 2 && f.along == 2 && f.frames == 63),
            run.flight.exists(f => f.margin > 0.1 && f.clearance > 0 && f.gap < 0.4),
            valid,
          )
      ,
      test("a flyover follows the connectors as drawn, straight ones too, and a highlight dims only once"):
        for run <- built(small, "--flyover", "--straight", "--highlight", "Web Shop")
        yield assertTrue(
          run.flight.exists(f => f.along == 2 && f.gap < 0.4),
          run.lit.exists(_.ambient == 0.2),
        )
      ,
      test("--frame renders one frame of the flight as a still"):
        inDirectory: dir =>
          val still   = dir.resolve("frame.png")
          val options = List("--flyover", "--frame", "20", "--render", still.toString, "--size", "320x180")
          for
            _     <- laidOut(small).flatMap(build(dir, _, options*))
            valid <- ZIO.attemptBlocking(startsWith(still, PNG))
          yield assertTrue(valid)
      ,
      test("--animation without a flow to fly says so and writes nothing"):
        inDirectory: dir =>
          val video = dir.resolve("nothing.mp4")
          for
            run    <- laidOut(small).flatMap(build(dir, _, "--flyover", "nowhere", "--animation", video.toString))
            exists <- ZIO.attemptBlocking(Files.exists(video))
          yield assertTrue(run.says("no flow 'nowhere' in the view"), run.says("no video written"), !exists),
    ) @@ TestAspect.ifEnvSet("BLENDER_AVAILABLE") @@ TestAspect.sequential

end SceneBlenderSpec
