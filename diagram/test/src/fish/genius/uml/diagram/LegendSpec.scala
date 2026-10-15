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

  // the legend's frame, and what lies in it
  private def frameOf(d: Diagram.Drawing) =
    d.prims.collectFirst { case b @ Prim.Box(_, _, _, _, _, Ink.Paper, _, true, Role.Plain) => b }

  private lazy val frame = frameOf(drawing).get

  private def within(p: Pt) =
    p._1 >= frame.x - 1e-9 && p._1 <= frame.x + frame.w + 1e-9 && p._2 >= frame.y - 1e-9 &&
    p._2 <= frame.y + frame.h + 1e-9

  private def below(p: Prim): Boolean = p match
    case Prim.Text(at, _, _, _, _, _, _)         => within(at)
    case b @ Prim.Box(x, y, w, h, _, _, _, _, _) => b != frame && within((x, y)) && within((x + w, y + h))
    case Prim.Connector(ps, _, _, _, _)          => ps.forall(within)
    case Prim.Curve(segs, _, _)                  => segs.flatMap(Seg.points).forall(within)
    case _                                       => false

  private def chain(n: Int): SceneGraph =
    SceneGraph(
      "Tall",
      (1 to n).toList.map(i => leaf(s"n$i", s"Step $i", ShapeType.BusinessProcess)),
      (1 until n).toList.map(i => edge(s"e$i", s"n$i", s"n${i + 1}", RelationshipType.Triggering)),
      legend = legended.legend,
    )

  private def fan(n: Int): SceneGraph =
    SceneGraph(
      "Wide",
      leaf("hub", "Hub", ShapeType.ApplicationComponent) ::
      (1 to n).toList.map(i => leaf(s"s$i", s"Spoke $i", ShapeType.ApplicationService)),
      (1 to n).toList.map(i => edge(s"f$i", "hub", s"s$i", RelationshipType.Serving)),
      legend = legended.legend,
    )

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("diagram / the legend")(
      test("a legend never makes a tall drawing taller or a wide one wider, and covers no box"):
        val graphs = List(legended, chain(9), fan(9), DiagramSpec.badged)
        val kept   = graphs.forall: g =>
          val bare                 = Diagram.draw(g.copy(legend = None)).toOption.get
          val with_                = Diagram.draw(g).toOption.get
          val (bx0, by0, bx1, by1) = Extent.of(bare, 0)
          val (wx0, wy0, wx1, wy1) = Extent.of(with_, 0)
          val f                    = frameOf(with_).get
          val clear                = with_.scene.elements.forall(e =>
            e.x + e.w / 2 <= f.x || e.x - e.w / 2 >= f.x + f.w || e.y + e.d / 2 <= f.y || e.y - e.d / 2 >= f.y + f.h
          )
          val tall                 = by1 - by0 > bx1 - bx0
          clear &&
          (if tall then math.abs((wy1 - wy0) - (by1 - by0)) < 1e-6
           else math.abs((wx1 - wx0) - (bx1 - bx0)) < 1e-6)
        assertTrue(kept)
      ,
      test("a long legend beside a short tall drawing takes more columns rather than hang below it"):
        val many  = Legend(
          "Legend",
          Nil,
          List(LegendSection(
            "Elements",
            (1 to 14).toList.map(i => LegendItem.Shape(ShapeType.BusinessProcess, s"Kind $i")),
          )),
        )
        val g     = chain(3).copy(legend = Some(many))
        val bare  = Extent.of(Diagram.draw(g.copy(legend = None)).toOption.get, 0)
        val with_ = Extent.of(Diagram.draw(g).toOption.get, 0)
        assertTrue(
          bare._4 - bare._2 > bare._3 - bare._1,
          math.abs((with_._4 - with_._2) - (bare._4 - bare._2)) < 1e-6,
        )
      ,
      test("a tall chain gets its legend beside it, as narrow as its height allows"):
        val g     = chain(9)
        val d     = Diagram.draw(g).toOption.get
        val f     = frameOf(d).get
        val boxes = d.scene.elements
        assertTrue(
          f.x >= boxes.map(e => e.x + e.w / 2).max,
          f.y + f.h <= boxes.map(e => e.y + e.d / 2).max + 1e-6,
          f.y >= boxes.map(e => e.y - e.d / 2).min - 1e-6,
        )
      ,
      test("badges and sections stand in one legend, and items share rows when there is room"):
        val mixed = legended.copy(legend = legended.legend.map(_.copy(entries = List(Badge(Glyph.Star, "Starred")))))
        val d     = Diagram.draw(fan(9).copy(legend = mixed.legend)).toOption.get
        val words = d.prims.collect { case Prim.Text(at, t, _, _, _, _, _) => t -> at._2 }.toMap
        val m     = Diagram.draw(mixed).toOption.get
        val said  = m.prims.collect { case Prim.Text(_, t, _, _, _, _, _) => t }
        assertTrue(
          said.contains("Starred") && said.contains("Elements"),
          // in a wide drawing the element types run side by side
          math.abs(words("Business Actor") - words("Business Process")) < 1e-6,
        )
      ,
      test("each section stands under its heading, in the legend; an empty one is left out"):
        val words = drawing.prims.filter(below).collect { case Prim.Text(_, t, _, _, _, _, _) => t }
        assertTrue(
          List("Legend", "Elements", "Relationships", "Signs", "Logos").forall(words.contains),
          !words.contains("Empty"),
          List("Business Actor", "Node", "Assignment", "Serving", "Realization", "Public API", "Framework")
            .forall(words.contains),
          // headings in order, top to bottom
          List("Elements", "Relationships", "Signs", "Logos").map(h =>
            drawing.prims.filter(below).collectFirst { case Prim.Text(at, `h`, _, _, _, _, _) => at._2 }.get
          ).sliding(2).forall { case List(a, b) => a > b; case _ => true },
          drawing.overflow == 0,
        )
      ,
      test("an element type is a small box in its layer's colour; a relationship a line with its ends"):
        val legendBoxes = drawing.prims.filter(below).collect:
          case Prim.Box(_, _, w, _, _, _, _, _, Role.Element(layer)) if w < 1 => layer
        val lines       = drawing.prims.filter(below).collect:
          case Prim.Connector(_, dash, start, end, _) => (dash, start, end)
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
