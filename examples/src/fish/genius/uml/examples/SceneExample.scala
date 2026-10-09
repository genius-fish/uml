package fish.genius.uml.examples

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import zio.json.*

import zio.*

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.scene.*

// A small layered view laid out as a 3D scene, written as JSON next to the Blender
// builder from the same jar:
//
//   ./mill examples.runMain fish.genius.uml.examples.SceneExample
//   blender -b -P out/scene/examples/archimate3d_blender.py -- \
//     --view out/scene/examples/scene.json --blend out/scene/examples/scene.blend \
//     --render out/scene/examples/scene.png
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
    ),
    List(
      edge("Customer", "Handle Order", Assignment),
      edge("Order Intake", "Handle Order", Serving),
      edge("Order API", "Order Intake", Realization),
      edge("Order Worker", "Order", Access),
      edge("Application Server", "Order System", Serving),
    ),
  )

  private def write(file: Path, text: String): Task[Path] =
    ZIO.attemptBlocking(Files.writeString(file, text, StandardCharsets.UTF_8))

  def run: Task[Unit] =
    for
      root <- System.env("MILL_WORKSPACE_ROOT").map(_.fold(Paths.get("."))(Paths.get(_)))
      out = root.resolve("out").resolve("scene").resolve("examples")
      scene <- ZIO.fromEither(SceneLayout.layout(graph)).mapError(e => RuntimeException(e.toString))
      script <- ZIO.fromEither(SceneBuilder.blenderScript).mapError(e => RuntimeException(e.toString))
      _    <- ZIO.attemptBlocking(Files.createDirectories(out))
      json <- write(out.resolve("scene.json"), scene.toJsonPretty)
      _    <- write(out.resolve(SceneBuilder.BLENDER_SCRIPT), script)
      _    <- Console.printLine(
        s"${scene.elements.size} elements, ${scene.relationships.size} connectors: $json"
      )
    yield ()

end SceneExample
