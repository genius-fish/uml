package fish.genius.uml.examples

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.LocalDate

import zio.json.*

import zio.*

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.scene.*

// A small layered view laid out as a 3D scene, written as JSON next to the Blender
// builder from the same jar, with one flow to fly through:
//
//   ./mill examples.runMain fish.genius.uml.examples.SceneExample
//   blender -b -P out/scene/examples/archimate3d_blender.py -- \
//     --view out/scene/examples/scene.json --blend out/scene/examples/scene.blend \
//     --render out/scene/examples/scene.png
//   blender -b -P out/scene/examples/archimate3d_blender.py -- \
//     --view out/scene/examples/scene.json --highlight "Order System" \
//     --render out/scene/examples/order-system.png
//   blender -b -P out/scene/examples/archimate3d_blender.py -- \
//     --view out/scene/examples/scene.json --flyover "Place an order" \
//     --animation out/scene/examples/place-an-order.mp4
//
// The same view with a passport beside some of its elements, on glass panes:
//
//   blender -b -P out/scene/examples/archimate3d_blender.py -- \
//     --view out/scene/examples/passports.json --render out/scene/examples/passports.png
object SceneExample extends ZIOAppDefault:

  private def node(
    label: String,
    shape: ShapeType,
    children: SceneNode*
  ): SceneNode =
    SceneNode(SceneId(label), NodeKind.Element(shape), label, children.toList)

  private def edge(
    source: String,
    target: String,
    relationship: RelationshipType,
  ): SceneEdge =
    SceneEdge(SceneId(s"$source>$target"), SceneId(source), SceneId(target), relationship)

  private def step(
    source: String,
    target: String,
    label: String,
  ): SceneStep =
    SceneStep(SceneId(source), SceneId(target), label)

  private val graph = SceneGraph(
    "Order handling",
    List(
      node("Customer", BusinessActor),
      node("Handle Order", BusinessProcess),
      node(
        "Order System",
        ApplicationComponent,
        node("Order API", ApplicationComponent),
        node("Order Worker", ApplicationComponent),
      ),
      node("Order Intake", ApplicationService),
      node("Order", ApplicationDataObject),
      node("Application Server", TechnologyNode),
      SceneNode(SceneId("either"), NodeKind.OrJunction, "either"),
    ),
    List(
      edge("Customer", "Handle Order", Assignment),
      edge("Order Intake", "Handle Order", Serving),
      edge("Order API", "Order Intake", Realization),
      edge("Order Worker", "Order", Access),
      edge("Application Server", "Order System", Serving),
      edge("Order Intake", "either", Serving),
      edge("either", "Customer", Serving),
    ),
    List(
      SceneFlow(
        SceneId("place-an-order"),
        "Place an order",
        List(
          step("Customer", "Handle Order", "Places an order"),
          step("Handle Order", "Order Intake", "Submits it"),
          step("Order Intake", "Order API", "Calls the API"),
          step("Order API", "Order Worker", "Queues the order"),
          step("Order Worker", "Order", "Stores it"),
        ),
      )
    ),
  )

  private val asOf = LocalDate.of(2026, 10, 13)

  private def passport(
    eyebrow: String,
    title: String,
    subtitle: String,
    description: String,
    owner: String,
    criticality: Chip,
    milestones: List[Milestone],
  ): Passport =
    Passport(
      eyebrow,
      title,
      Some(subtitle),
      List(
        PassportSection.Text(description),
        PassportSection.Facts(Some("Ownership"), List(Fact("Accountable", owner))),
        PassportSection.Timeline("Lifecycle", milestones, asOf, None),
        PassportSection.Rated("Criticality", criticality),
        PassportSection.Rating(
          "Security",
          List(
            Mark("C", "H", Tone.Warning),
            Mark("I", "M", Tone.Caution),
            Mark("A", "H", Tone.Warning),
          ),
        ),
      ),
      Some("as of 13 Oct 2026"),
    )

  private val passports: Map[String, Passport] = Map(
    "Order Intake" -> passport(
      "Application service",
      "Order Intake",
      "REST · public",
      "Takes orders from the web shop and the call centre, checks them and hands them to the worker.",
      "Sales Operations",
      Chip("High", Tone.Warning, Some("No order is taken without it")),
      List(Milestone("Active", LocalDate.of(2021, 3, 15), Tone.Layer)),
    ),
    "Order" -> passport(
      "Data object",
      "Order",
      "Master data · personal data",
      "An order as the customer placed it: lines, prices, delivery address and payment reference.",
      "Data Office",
      Chip("Critical", Tone.Critical, Some("Lost orders cannot be recovered")),
      List(Milestone("Active", LocalDate.of(2019, 1, 1), Tone.Layer)),
    ),
    "Application Server" -> passport(
      "Node",
      "Application Server",
      "On premises · two nodes",
      "Runs the order system. Replaced by the managed platform when its support ends.",
      "IT Operations",
      Chip("Medium", Tone.Caution, Some("A failover node takes over")),
      List(
        Milestone("Active", LocalDate.of(2016, 6, 1), Tone.Layer),
        Milestone("Deprecated", LocalDate.of(2025, 1, 1), Tone.Caution),
        Milestone("Retired", LocalDate.of(2027, 12, 31), Tone.Neutral),
      ),
    ),
  )

  private def withPassports(n: SceneNode): SceneNode =
    n.copy(passport = passports.get(n.label), children = n.children.map(withPassports))

  private def write(file: Path, text: String): Task[Path] =
    ZIO.attemptBlocking(Files.writeString(file, text, StandardCharsets.UTF_8))

  def run: Task[Unit] =
    for
      root <- System.env("MILL_WORKSPACE_ROOT").map(_.fold(Paths.get("."))(Paths.get(_)))
      out = root.resolve("out").resolve("scene").resolve("examples")
      scene  <-
        ZIO.fromEither(SceneLayout.layout(graph)).mapError(error => RuntimeException(error.toString))
      script <-
        ZIO.fromEither(SceneBuilder.blenderScript).mapError(error => RuntimeException(error.toString))
      _      <- ZIO.attemptBlocking(Files.createDirectories(out))
      json   <- write(out.resolve("scene.json"), scene.toJsonPretty)
      glass  <- ZIO
        .fromEither(SceneLayout.layout(graph.copy(nodes = graph.nodes.map(withPassports))))
        .mapError(error => RuntimeException(error.toString))
      _      <- write(out.resolve("passports.json"), glass.toJsonPretty)
      _      <- write(out.resolve(SceneBuilder.BLENDER_SCRIPT), script)
      _      <- Console.printLine(
        s"${scene.elements.size} elements, ${scene.relationships.size} connectors: $json"
      )
    yield ()

end SceneExample
