package fish.genius.uml.scene

import zio.json.*
import zio.json.ast.Json
import zio.test.*

import zio.Scope

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}

// The scene's JSON and the Blender builder that reads it are one contract: every name the
// layout writes must be one the builder knows.
object SceneContractSpec extends ZIOSpecDefault:

  private val script = SceneBuilder.blenderScript.getOrElse("")

  // the keys of the builder's catalogue, its define(...) rows: ("business-actor", "actor")
  private val shapes =
    """\("([a-z-]+)", (?:"[A-Za-z]+"|None)\)""".r.findAllMatchIn(script).map(_.group(1)).toSet

  // the keys of its connector table: "serving": (None, "open", None)
  private val connectors = """"([a-z-]+)": \(""".r.findAllMatchIn(script).map(_.group(1)).toSet

  private def keys(json: Json): Set[String] = json match
    case Json.Obj(fields) => fields.map(_._1).toSet ++ fields.flatMap((_, value) => keys(value))
    case Json.Arr(values) => values.flatMap(keys).toSet
    case _                => Set.empty

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("scene / the contract between the layout and the Blender builder")(
      test("the builder knows every shape the layout names"):
        val named = ShapeType.values.toList.map(SceneNames.of) ++
          List(NodeKind.Grouping, NodeKind.AndJunction, NodeKind.OrJunction).map(SceneNames.of)
        assertTrue(shapes.size >= named.size, named.filterNot(shapes).isEmpty)
      ,
      test("the builder knows every connector the layout names"):
        assertTrue(RelationshipType.values.toList.map(SceneNames.of).filterNot(connectors).isEmpty)
      ,
      test("the builder reads every field the layout writes"):
        val scene  = Scene(
          "One",
          8,
          4,
          List(
            SceneElement(SceneId("a"), "node", "A", -2, 0, 2, 1.3, 0, Some(SceneId("p")), container = false),
            SceneElement(SceneId("b"), "node", "B", 2, 0, 2, 1.3, 0, None, container = false),
          ),
          List(
            SceneRelationship(
              SceneId("ab"),
              SceneId("a"),
              SceneId("b"),
              "serving",
              PortName.East,
              PortName.West,
              List(ScenePoint(0, 0)),
              clear = true,
            )
          ),
        )
        val fields = scene.toJsonAST.toOption.map(keys).getOrElse(Set.empty) --
          Set("name", "width", "depth", "elements", "relationships", "clear")
        assertTrue(fields.nonEmpty, fields.filterNot(field => script.contains(s"\"$field\"")).isEmpty),
    )

end SceneContractSpec
