package fish.genius.uml.examples

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import zio.*

import fish.genius.uml.diagram.*
import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.scene.*

// A small layered view drawn flat: the scene's ELK layout, written as a standalone TikZ
// picture, an SVG and a PNG, to out/diagram/examples:
//
//   ./mill examples.runMain fish.genius.uml.examples.DiagramExample
//   lualatex -output-directory out/diagram/examples out/diagram/examples/order-handling.tex
object DiagramExample extends ZIOAppDefault:

  private def node(
    id: String,
    label: String,
    shape: ShapeType,
    caption: Option[String] = None,
    children: List[SceneNode] = Nil,
  ): SceneNode =
    SceneNode(SceneId(id), NodeKind.Element(shape), label, children, caption = caption)

  private def edge(
    source: String,
    target: String,
    relationship: RelationshipType,
    label: Option[String] = None,
  ): SceneEdge =
    SceneEdge(SceneId(s"$source>$target"), SceneId(source), SceneId(target), relationship, label)

  private val graph = SceneGraph(
    "Order handling",
    List(
      node("customer", "Customer", BusinessActor),
      node("handle", "Handle Order", BusinessProcess),
      node(
        "orders",
        "Order System",
        ApplicationComponent,
        Some("[IT System: Scala]"),
        List(
          node("api", "Order API", ApplicationComponent),
          node("worker", "Order Worker", ApplicationComponent),
        ),
      ),
      node("intake", "Order Intake", ApplicationService),
      node("order", "Order", ApplicationDataObject),
      node("server", "Application Server", TechnologyNode),
    ),
    List(
      edge("customer", "handle", Assignment),
      edge("intake", "handle", Serving),
      edge("api", "intake", Realization),
      edge("worker", "order", Access, Some("stores")),
      edge("server", "orders", Serving),
    ),
  )

  def run: Task[Unit] =
    for
      root <- System.env("MILL_WORKSPACE_ROOT").map(_.fold(Paths.get("."))(Paths.get(_)))
      out = root.resolve("out").resolve("diagram").resolve("examples")
      drawing <- ZIO.fromEither(Diagram.draw(graph)).mapError(e => RuntimeException(e.toString))
      _       <- ZIO.attemptBlocking:
        Files.createDirectories(out)
        Files.writeString(
          out.resolve("order-handling.tex"),
          Tikz.standalone(drawing),
          StandardCharsets.UTF_8,
        )
        Files.writeString(
          out.resolve("order-handling.svg"),
          Svg.document(drawing),
          StandardCharsets.UTF_8,
        )
        Files.write(out.resolve("order-handling.png"), Png.image(drawing))
      _       <- Console.printLine(
        s"${drawing.scene.elements.size} boxes, ${drawing.scene.relationships.size} lines: $out"
      )
    yield ()

end DiagramExample
