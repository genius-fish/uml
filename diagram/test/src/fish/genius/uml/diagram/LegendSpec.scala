package fish.genius.uml.diagram

import zio.test.*

import zio.Scope

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.scene.*

object LegendSpec extends ZIOSpecDefault:

  private def leaf(
    id: String,
    label: String,
    shape: ShapeType,
  ) =
    SceneNode(SceneId(id), NodeKind.Element(shape), label)

  private def edge(
    id: String,
    from: String,
    to: String,
    kind: RelationshipType,
  ) =
    SceneEdge(SceneId(id), SceneId(from), SceneId(to), kind)

  val legended: SceneGraph = SceneGraph(
    "Legend",
    List(
      leaf("c", "Customer", ShapeType.BusinessActor),
      leaf("p", "Place an order", ShapeType.BusinessProcess),
      leaf("s", "Shop", ShapeType.ApplicationComponent).copy(
        logo = Some(LogoSpec.framed),
        badges = List(Badge(Glyph.Globe, "Public API")),
      ),
      leaf("k", "Kubernetes", ShapeType.TechnologyNode),
    ),
    List(
      edge("e1", "c", "p", RelationshipType.Assignment),
      edge("e2", "s", "p", RelationshipType.Serving),
      edge("e3", "k", "s", RelationshipType.Realization),
    ),
    legend = Some(
      Legend(
        "Legend",
        Nil,
        List(
          LegendSection(
            "Elements",
            List(
              LegendItem.Shape(ShapeType.BusinessActor, "Business Actor"),
              LegendItem.Shape(ShapeType.BusinessProcess, "Business Process"),
              LegendItem.Shape(ShapeType.ApplicationComponent, "Application Component"),
              LegendItem.Shape(ShapeType.TechnologyNode, "Node"),
            ),
          ),
          LegendSection(
            "Relationships",
            List(
              LegendItem.Line(RelationshipType.Assignment, "Assignment"),
              LegendItem.Line(RelationshipType.Serving, "Serving"),
              LegendItem.Line(RelationshipType.Realization, "Realization"),
            ),
          ),
          LegendSection("Signs", List(LegendItem.Mark(Badge(Glyph.Globe, "Public API")))),
          LegendSection("Logos", List(LegendItem.LogoMark(LogoSpec.framed, "Framework"))),
          LegendSection("Empty", Nil),
        ),
      )
    ),
  )

  private lazy val drawing = Diagram.draw(legended).toOption.get

  // what the legend draws: everything below the lowest box
  private lazy val lowest = drawing.scene.elements.map(e => e.y - e.d / 2).min

  private def below(p: Prim): Boolean = p match
    case Prim.Text(at, _, _, _, _, _, _)     => at._2 < lowest
    case Prim.Box(_, y, _, h, _, _, _, _, _) => y + h < lowest
    case Prim.Connector(ps, _, _, _)         => ps.forall(_._2 < lowest)
    case Prim.Curve(segs, _, _)              => segs.flatMap(Seg.points).forall(_._2 < lowest)
    case _                                   => false

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("diagram / the legend")(
      test("each section stands under its heading, under the drawing; an empty one is left out"):
        val words = drawing.prims.filter(below).collect { case Prim.Text(_, t, _, _, _, _, _) => t }
        assertTrue(
          List("Legend", "Elements", "Relationships", "Signs", "Logos").forall(words.contains),
          !words.contains("Empty"),
          List("Business Actor", "Node", "Assignment", "Serving", "Realization", "Public API", "Framework")
            .forall(words.contains),
          // headings in order, top to bottom
          List("Elements", "Relationships", "Signs", "Logos").map(h =>
            drawing.prims.collectFirst { case Prim.Text(at, `h`, _, _, _, _, _) => at._2 }.get
          ).sliding(2).forall { case List(a, b) => a > b; case _ => true },
          drawing.overflow == 0,
        )
      ,
      test("an element type is a small box in its layer's colour; a relationship a line with its ends"):
        val legendBoxes = drawing.prims.filter(below).collect:
          case Prim.Box(_, _, w, _, _, _, _, _, Role.Element(layer)) if w < 1 => layer
        val lines       = drawing.prims.filter(below).collect:
          case Prim.Connector(_, dash, start, end) => (dash, start, end)
        assertTrue(
          legendBoxes.toSet == Set("am-business", "am-application", "am-technology"),
          legendBoxes.size == 4,
          lines.toSet == Set(
            Notation.ends(RelationshipType.Assignment),
            Notation.ends(RelationshipType.Serving),
            Notation.ends(RelationshipType.Realization),
          ),
        )
      ,
      test("a logo stands in the legend in its own colour"):
        assertTrue(drawing.prims.filter(below).exists { case Prim.Curve(_, "1F6FEB", _) => true; case _ => false })
      ,
      test("a legend with nothing in it draws nothing; badges alone still make one"):
        val empty  = Diagram.draw(legended.copy(legend = Some(Legend("Legend", Nil, List(LegendSection("x", Nil))))))
          .toOption.get
        val none   = Diagram.draw(legended.copy(legend = None)).toOption.get
        val badges = Diagram.draw(legended.copy(legend = Some(Legend("Realized by", List(Badge(Glyph.Star, "S"))))))
          .toOption.get
        def says(d: Diagram.Drawing, t: String) =
          d.prims.exists { case Prim.Text(_, `t`, _, _, _, _, _) => true; case _ => false }
        assertTrue(
          empty.prims == none.prims,
          says(badges, "Realized by"),
          says(badges, "S"),
        )
      ,
      test("SVG, PNG and TikZ write the legend, and the same graph gives the same drawing"):
        assertTrue(
          Svg.document(drawing).contains("Relationships"),
          Tikz.picture(drawing).contains("Relationships"),
          Png.image(drawing).nonEmpty,
          Diagram.draw(legended).toOption.get.prims == drawing.prims,
        ),
    )

end LegendSpec
