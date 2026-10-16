package fish.genius.uml.diagram

import java.nio.file.Files

import scala.sys.process.{Process, ProcessLogger}

import zio.test.*

import zio.Scope

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeGroup, ShapeType}
import fish.genius.uml.scene.*

// The visual encodings a town plan's gap and heat views draw with (townplanner, TOWN-67):
// a badge's text (APP-2272), a faded box (APP-2273), a weighted line (APP-2274) and a legend
// swatch (APP-2275).
object EncodingSpec extends ZIOSpecDefault:

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
    weight: Double = 1.0,
  ) =
    SceneEdge(SceneId(id), SceneId(from), SceneId(to), kind, weight = weight)

  private def drawn(graph: SceneGraph): Diagram.Drawing = Diagram.draw(graph).toOption.get

  private def box(d: Diagram.Drawing, id: String) = d.scene.elements.find(_.key == SceneId(id)).get

  private val style   = Diagram.Style()
  private val measure = Measure(style.typeface)

  // ── APP-2272: a badge's text ──

  val retiring = SceneGraph(
    "Retiring",
    List(
      leaf("crm", "CRM", ShapeType.ApplicationComponent).copy(badges =
        List(
          Badge(Glyph.PhaseOut, "Phase-out", text = Some("06/2027")),
          Badge(Glyph.Risk, "Risk", text = Some("R-12")),
          Badge(Glyph.Decision, "Decision", text = Some("D-104")),
          Badge(Glyph.Warning, "Degraded", text = Some("since 2025")),
        )
      )
    ),
    Nil,
    legend = Some(Legend("Legend", List(Badge(Glyph.PhaseOut, "Phase-out", text = Some("06/2027"))))),
  )

  // ── APP-2273: the looks a town plan draws in, and one of a reader's own ──

  val innovenso = Map(
    "am-motivation" -> "D6D8E8",
    "am-strategy" -> "E8D3B5",
    "am-business" -> "F9E2B0",
    "am-application" -> "C8E3DF",
    "am-technology" -> "DDD9B8",
    "am-physical" -> "CFD8C0",
    "am-implementation" -> "F0D2C4",
    "am-line" -> "403725",
    "am-text" -> "251A06",
    "am-muted" -> "847656",
    "am-paper" -> "FBF6EC",
  )

  // saturated layers, one of them dark, on grey paper
  private val custom = Map(
    "am-business" -> "FF6961",
    "am-application" -> "3D85C6",
    "am-technology" -> "2F4F4F",
    "am-strategy" -> "FFB347",
    "am-motivation" -> "B39EB5",
    "am-implementation" -> "77DD77",
    "am-physical" -> "FDFD96",
    "am-text" -> "000000",
    "am-muted" -> "555555",
    "am-paper" -> "F2F2F2",
  )

  private val looks = List("archimate" -> Map.empty[String, String], "innovenso" -> innovenso, "custom" -> custom)

  private val layers = ShapeGroup.values.toList.map(Notation.layer).distinct :+ "am-fill-f4c542"

  // what a faded box's text stands on: its fill, the top of its fall-off, its field as a container
  private def grounds(c: Look.Colours, layer: String): List[String] =
    val fill = c.of(Look.faded(Look.Faded.Fill, layer))
    List(fill, c.top(Look.faded(Look.Faded.Fill, layer)), c.field(Look.faded(Look.Faded.Fill, layer)))

  val gap = SceneGraph(
    "Gap",
    List(
      leaf("old", "Legacy billing", ShapeType.ApplicationComponent).copy(
        faded = true,
        caption = Some("Application Component"),
        badges = List(Badge(Glyph.GapRemove, "Removed", text = Some("2027"))),
      ),
      leaf("new", "Billing", ShapeType.ApplicationComponent),
      SceneNode(
        SceneId("dc"),
        NodeKind.Element(ShapeType.TechnologyNode),
        "Old data centre",
        children = List(leaf("vm", "Mainframe", ShapeType.TechnologyNode)),
        faded = true,
      ),
    ),
    List(edge("e1", "old", "new", RelationshipType.Flow)),
    legend = Some(Legend(
      "Legend",
      Nil,
      List(LegendSection(
        "Gap",
        List(
          LegendItem.Faded(Some(ShapeType.ApplicationComponent), "Removed"),
          LegendItem.Faded(None, "Removed, any type"),
        ),
      )),
    )),
  )

  // ── APP-2274: an integration's volume ──

  val volumes = SceneGraph(
    "Volumes",
    List(
      leaf("a", "Shop", ShapeType.ApplicationComponent),
      leaf("b", "Orders", ShapeType.ApplicationComponent),
      leaf("c", "Billing", ShapeType.ApplicationComponent),
      leaf("d", "Ledger", ShapeType.ApplicationComponent),
      leaf("e", "Archive", ShapeType.ApplicationComponent),
    ),
    List(
      edge("low", "a", "b", RelationshipType.Flow),
      edge("mid", "b", "c", RelationshipType.Flow, 1.6),
      edge("high", "c", "d", RelationshipType.Serving, 2.4),
      edge("over", "d", "e", RelationshipType.Triggering, 9.0),
      edge("nan", "a", "e", RelationshipType.Triggering, Double.NaN),
    ),
    legend = Some(Legend(
      "Legend",
      Nil,
      List(LegendSection(
        "Volume",
        List(
          LegendItem.Weighted(RelationshipType.Flow, 1.0, "Low"),
          LegendItem.Weighted(RelationshipType.Flow, 1.6, "Medium"),
          LegendItem.Weighted(RelationshipType.Flow, 2.4, "High"),
        ),
      )),
    )),
  )

  // ── APP-2275: a heat scale ──

  private val heat = List(
    "Critical" -> "#B2182B",
    "High" -> "EF8A62",
    "Medium" -> "FDDBC7",
    "Low" -> "D1E5F0",
    "Unknown" -> "not a colour",
  )

  // a badge whose text is a sentence, and a faded box whose outline has a colour of its own
  val long = SceneGraph(
    "Long",
    List(
      leaf("crm", "CRM", ShapeType.ApplicationComponent).copy(badges =
        List(Badge(Glyph.Risk, "Risk", text = Some("The vendor stops support for this release in 2027")))
      ),
      leaf("old", "Legacy", ShapeType.ApplicationComponent).copy(
        faded = true,
        outline = Some(Outline(Stroke.Dashed, colour = Some("#D9534F"))),
      ),
    ),
    Nil,
  )

  val heatMap = SceneGraph(
    "Heat",
    List(leaf("cap", "Billing", ShapeType.StrategyCapability).copy(fill = Some("#B2182B"))),
    Nil,
    legend = Some(Legend(
      "Legend",
      Nil,
      List(LegendSection(
        "Heat",
        heat.map((t, f) => LegendItem.Swatch(f, t, Some(ShapeType.StrategyCapability))),
      )),
    )),
  )

  private lazy val latexTools = List("pdflatex", "pdftoppm").forall: tool =>
    scala.util.Try(Process(Seq(tool, "-v")).!(ProcessLogger(_ => ()))).isSuccess

  // pdflatex's verdict on a drawing: whether it compiled, and its log when it did not
  private def compiles(d: Diagram.Drawing, palette: Map[String, String]): (Boolean, String) =
    val dir = Files.createTempDirectory("encoding")
    try
      Files.writeString(dir.resolve("e.tex"), Tikz.standalone(d, palette))
      val out = StringBuilder()
      val rc  = Process(Seq("pdflatex", "-interaction=nonstopmode", "-halt-on-error", "e.tex"), dir.toFile)
        .!(ProcessLogger(l => out.append(l).append('\n'), _ => ()))
      (rc == 0, out.toString.linesIterator.filter(_.startsWith("!")).mkString("\n"))
    finally
      scala.util.Using(Files.list(dir))(_.forEach { p => Files.deleteIfExists(p); () })
      Files.deleteIfExists(dir)

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("EncodingSpec")(
      suite("a badge's text (APP-2272)")(
        test("stands after its sign at the caption's size in the muted ink, inside the box, which makes room"):
          val d = drawn(retiring)
          val bare = drawn(retiring.copy(nodes = retiring.nodes.map(n => n.copy(badges = n.badges.map(_.copy(text = None))))))
          val crm   = box(d, "crm")
          val words = d.prims.collect:
            case t @ Prim.Text(_, "06/2027" | "R-12" | "D-104" | "since 2025", _, _, _, _, _) => t
          val signs = d.prims.collect:
            case Prim.Path(segs, _, _, Role.Mark("am-application"), _) => segs.flatMap(Seg.points)
          val right = words.map(t => t.baseline._1 + measure.width(t.text, t.size, t.weight)).max
          val date  = words.find(_.text == "06/2027").get
          // the phase-out sign is the first mark: the date starts after all of its points
          val first = signs.filter(_.forall(_._1 < date.baseline._1))
          assertTrue(
            words.size == 4,
            words.forall(t => t.size == style.captionSize && t.tone == Emphasis.Muted && !t.centred),
            first.nonEmpty,
            right <= crm.x + crm.w / 2 - style.padding + 1e-6,
            crm.w > box(bare, "crm").w,
            d.overflow == 0,
            Tikz.picture(d).contains("06/2027"),
            Svg.document(d).contains("R-12"),
            Png.image(d).nonEmpty,
          )
        ,
        test("a text longer than twelve characters is cut and ends in an ellipsis, so its box keeps its width"):
          val d     = drawn(long)
          val shown =
            Badge(Glyph.Risk, "Risk", text = Some("The vendor stops support for this release in 2027")).shownText
          val texts = d.prims.collect { case t: Prim.Text => t.text }
          assertTrue(
            shown.contains("The vendor\u2026"),
            texts.contains("The vendor\u2026"),
            box(d, "crm").w < 3.5,
            Badge(Glyph.Risk, "Risk", text = Some("06/2027")).shownText.contains("06/2027"),
            Badge(Glyph.Risk, "Risk", text = Some("abcdefghijkl")).shownText.contains("abcdefghijkl"),
            Badge(Glyph.Risk, "Risk", text = Some("\uD83D\uDE80" * 13)).shownText.contains("\uD83D\uDE80" * 11 +
              "\u2026"),
          )
        ,
        test("the legend names the badge by its label only, and a blank text draws as none"):
          val d     = drawn(retiring)
          val blank =
            drawn(retiring.copy(nodes = retiring.nodes.map(n => n.copy(badges = n.badges.map(_.copy(text = Some("  ")))))))
          val none = drawn(retiring.copy(nodes = retiring.nodes.map(n => n.copy(badges = n.badges.map(_.copy(text = None))))))
          val texts = d.prims.collect { case t: Prim.Text => t.text }
          assertTrue(
            texts.count(_ == "06/2027") == 1,
            texts.contains("Phase-out"),
            blank.prims == none.prims,
          ),
      ),
      suite("a faded box (APP-2273)")(
        test("keeps its text at 4.5:1 and its caption at 3:1 on every ground, in each look, and is lighter"):
          val checks =
            for
              (look, palette) <- looks
              c = Look.colours(palette ++ Map("am-fill-f4c542" -> "F4C542"))
              layer <- layers
            yield
              val made  = c.fade(layer).map(d => d.name -> d.hex).toMap
              val text  = made(Look.faded(Look.Faded.Text, layer))
              val muted = made(Look.faded(Look.Faded.Muted, layer))
              val g     = grounds(c.fading(List(layer)), layer)
              val fill  = made(Look.faded(Look.Faded.Fill, layer))
              val edge  = made(Look.faded(Look.Faded.Edge, layer))
              (
                s"$look $layer",
                g.forall(Look.contrast(text, _) >= 4.5),
                g.forall(Look.contrast(muted, _) >= 3.0),
                // paler than the box it fades: its fill nearer the paper, its edge and its text too
                Look.contrast(fill, c.paper) <= Look.contrast(c.of(layer), c.paper) + 1e-9,
                Look.contrast(edge, c.paper) < Look.contrast(c.edge(layer), c.paper),
                Look.contrast(text, c.paper) <= Look.contrast(c.text, c.paper) + 1e-9,
              )
          val failed = checks.filterNot(c => c._2 && c._3 && c._4 && c._5 && c._6).map(_._1)
          assertTrue(checks.size == 3 * layers.size, failed.isEmpty)
        ,
        test("falls back to black or white when no mix of the text colour holds, written as a colour of its own"):
          val poor = Look.colours(Map("am-text" -> "EEEEEE", "am-muted" -> "F0F0F0", "am-paper" -> "FFFFFF"))
          val made = poor.fade("am-business")
          val text = made.find(_.name == Look.faded(Look.Faded.Text, "am-business")).get
          val g    = grounds(poor.fading(List("am-business")), "am-business")
          assertTrue(text.mix.isEmpty, text.hex == "000000", g.forall(Look.contrast(text.hex, _) >= 4.5))
        ,
        test("draws its fill, edge, marks and text in its faded colours, a container's too, in every writer"):
          val d                   = drawn(gap)
          val fill                = Look.faded(Look.Faded.Fill, "am-application")
          val edge                = Look.faded(Look.Faded.Edge, "am-application")
          val mark                = Look.faded(Look.Faded.Mark, "am-application")
          val text                = Look.faded(Look.Faded.Text, "am-application")
          val muted               = Look.faded(Look.Faded.Muted, "am-application")
          val tech                = Look.faded(Look.Faded.Fill, "am-technology")
          val old                 = box(d, "old")
          def inOld(ps: List[Pt]) =
            ps.forall(p => math.abs(p._1 - old.x) <= old.w / 2 && math.abs(p._2 - old.y) <= old.d / 2)
          val boxes               = d.prims.collect:
            case Prim.Box(x, y, w, h, _, _, _, _, Role.Element(`fill`), pen) if inOld(List((x + w / 2, y + h / 2))) =>
              pen
          val faint               = d.prims.collect:
            case Prim.Poly(ps, _, _, _, Role.Faint(`mark`), _) if inOld(ps)                      => ps
            case Prim.Path(segs, _, _, Role.Faint(`mark`), _) if inOld(segs.flatMap(Seg.points)) =>
              segs.flatMap(Seg.points)
          val tones               = d.prims.collect:
            case Prim.Text(at, t, _, _, tone, _, _) if inOld(List(at)) => t -> tone
          val header              = d.prims.collect:
            case Prim.Text(_, "Old data centre", _, _, tone, _, _) => tone
          val tikz                = Tikz.picture(d)
          val svg                 = Svg.document(d)
          val hex                 = Look.colours(Map.empty).fade("am-application").map(x => x.name -> x.hex).toMap
          assertTrue(
            boxes == List(Pen(ink = Some(edge))),
            faint.nonEmpty,
            tones.toMap.get("Legacy billing").contains(Emphasis.Inked(text)),
            tones.toMap.get("Application").contains(Emphasis.Inked(muted)),
            tones.toMap.get("2027").contains(Emphasis.Inked(muted)),
            header == List(Emphasis.Inked(Look.faded(Look.Faded.Text, "am-technology"))),
            d.prims.exists:
              case Prim.Box(_, _, _, _, _, _, _, _, Role.Container(`tech`), _) => true
              case _                                                           =>
                false
            ,
            d.faded.toSet == Set("am-application", "am-technology", "am-paper"),
            tikz.contains(s"\\colorlet{$fill}{am-application!38!am-paper}"),
            tikz.contains(s"\\colorlet{$edge}{am-application!50!am-text!45!am-paper}"),
            tikz.indexOf("\\colorlet") > tikz.lastIndexOf("\\providecolor"),
            svg.contains(s"#${hex(text)}"),
            svg.contains(s"fall-$fill"),
            Png.image(d).nonEmpty,
            // the unfaded box beside it is drawn as ever
            d.prims.exists:
              case Prim.Box(_, _, _, _, _, _, _, _, Role.Element("am-application"), Pen.Normal) => true
              case _                                                                            => false,
          )
        ,
        test("a faded box whose outline has a colour of its own keeps that colour for its edge"):
          val d    = drawn(long)
          val fill = Look.faded(Look.Faded.Fill, "am-application")
          val pens = d.prims.collect { case Prim.Box(_, _, _, _, _, _, _, _, Role.Element(`fill`), pen) => pen }
          assertTrue(pens == List(Pen(ink = Some("am-fill-d9534f"))))
        ,
        test("the legend draws a faded sample, in a shape's layer with its icon or on paper"):
          val d       = drawn(gap)
          val app     = Look.faded(Look.Faded.Fill, "am-application")
          val paper   = Look.faded(Look.Faded.Fill, "am-paper")
          val samples = d.prims.collect:
            case Prim.Box(_, _, w, _, _, _, _, _, Role.Element(l), pen) if w < 0.5 && (l == app || l == paper) =>
              l -> pen.ink
          assertTrue(
            samples.toSet == Set(
              app -> Some(Look.faded(Look.Faded.Edge, "am-application")),
              paper -> Some(Look.faded(Look.Faded.Edge, "am-paper")),
            ),
            d.prims.collect { case t: Prim.Text => t.text }.contains("Removed, any type"),
          )
        ,
        test("a palette the writer is given fades from its own layer colours"):
          val d    = drawn(gap)
          val made = Look.colours(innovenso).fade("am-application").map(x => x.name -> x.hex).toMap
          val svg  = Svg.document(d, innovenso)
          assertTrue(
            svg.contains(s"#${made(Look.faded(Look.Faded.Text, "am-application"))}"),
            made(Look.faded(Look.Faded.Fill, "am-application")) == Look.mix("C8E3DF", Look.FADE_FILL, "FBF6EC"),
          ),
      ),
      suite("a weighted line (APP-2274)")(
        test("carries its weight, kept between 1 and 2.5, and weight 1 draws as before"):
          val d       = drawn(volumes)
          val weights = d.prims.collect { case c: Prim.Connector if c.points.size > 1 && c.start.isEmpty => c.weight }
          val plain   = drawn(volumes.copy(edges = volumes.edges.map(_.copy(weight = 1.0)), legend = None))
          val before  = drawn(volumes.copy(
            edges = volumes.edges.map(e => SceneEdge(e.id, e.source, e.target, e.relationship)),
            legend = None,
          ))
          assertTrue(
            weights.toSet.subsetOf(Set(1.0, 1.6, 2.4, 2.5)),
            weights.contains(2.5),
            weights.contains(1.6),
            !weights.exists(_.isNaN),
            plain.prims == before.prims,
            Look.linkWeight(-3) == 1.0,
            Look.linkWeight(0) == 1.0,
          )
        ,
        test("is drawn that many times as wide in TikZ, SVG and PNG, its tips and dashes grown with it"):
          val d    = drawn(volumes.copy(legend = None))
          val tikz = Tikz.picture(d)
          val svg  = Svg.document(d)
          val png  = Png.image(d)
          assertTrue(
            tikz.contains("line width=1.44pt"),                 // 0.6 × 2.4
            tikz.contains("line width=1.5pt"),                  // 0.6 × 2.5, clamped
            tikz.contains("Straight Barb[length=2.6pt,width=5pt,round,scale=1.7]"),
            tikz.contains("dash pattern=on 4.48pt off 2.88pt"), // a dashed flow at 1.6
            svg.contains("""stroke-width="1.92""""),            // 1.44 pt in CSS pixels
            png.nonEmpty,
          )
        ,
        test("its heads grow half as fast as the line, and their outlines take its weight"):
          val heavy = Heads.of(Prim.Connector(
            List((0.0, 0.0), (3.0, 0.0)),
            Dash.Solid,
            None,
            Some(Head.OpenTriangle),
            weight = 2.4,
          ))
          val plain = Heads.of(Prim.Connector(List((0.0, 0.0), (3.0, 0.0)), Dash.Solid, None, Some(Head.OpenTriangle)))
          def span(ps: List[Prim]) = ps.collect { case p: Prim.Poly => p.points.map(_._2) }.flatten match
            case ys => ys.max - ys.min
          assertTrue(
            math.abs(span(heavy) / span(plain) - 1.7) < 1e-9,
            heavy.collect { case p: Prim.Poly => p.pen.weight } == List(2.4),
            plain.collect { case p: Prim.Poly => p.pen.weight } == List(1.0),
          )
        ,
        test("where it passes over another line, the piece drawn again keeps its weight"):
          val level      = List((0.0, 1.0), (4.0, 1.0))
          val upright    = List((2.0, 0.0), (2.0, 2.0))
          val drawnAgain = Diagram.crossings(
            List(
              level -> Prim.Connector(level, Dash.Solid, None, None, weight = 2.4),
              upright -> Prim.Connector(upright, Dash.Solid, None, None),
            ),
            _ => Role.Halo,
          ).collect { case c: Prim.Connector => c.weight }
          assertTrue(drawnAgain == List(2.4))
        ,
        test("the legend shows each weight as a line of its relationship, longer than a plain one"):
          val d           = drawn(volumes)
          val texts       = d.prims.collect { case t: Prim.Text => t.text }
          val lines       = d.prims.collect:
            case c: Prim.Connector
              if c.points.size == 2 && c.points.head._2 == c.points(1)._2 && c.end.contains(Head.FilledTriangle) &&
              c.dash == Dash.Dashed => (c.weight, c.points(1)._1 - c.points.head._1)
          val legendLines = lines.filter(_._2 < 1.0)
          assertTrue(
            legendLines.map(_._1).toSet == Set(1.0, 1.6, 2.4),
            legendLines.forall(_._2 > 0.7 * 0.8),
            List("Low", "Medium", "High").forall(texts.contains),
          ),
      ),
      suite("a legend swatch (APP-2275)")(
        test(
          "is a small box in its fill with the shape's icon, in the order given; a fill that is no colour keeps only its text"
        ):
          val d        = drawn(heatMap)
          val names    = heat.flatMap((_, f) => Diagram.fillName(f))
          val swatches = d.prims.collect:
            case Prim.Box(x, y, w, _, _, Ink.Layer(l), _, _, Role.Element(r), _)
              if w < 0.5 && l == r && names.contains(l) => (l, x, y)
          val icons    = d.prims.collect:
            case Prim.Poly(_, _, _, _, Role.Mark(l), _) if names.contains(l) => l
          val texts    = d.prims.collect { case t: Prim.Text => t.text }
          // read in the legend's order: down its rows, then along
          val order    = swatches.sortBy(
            (
              _,
              x,
              y,
            ) => (-math.rint(y * 100), x)
          ).map(_._1)
          assertTrue(
            order == names,
            names.forall(icons.contains),
            heat.map(_._1).forall(texts.contains),
            d.colours.get("am-fill-ef8a62").contains("EF8A62"),
            Tikz.picture(d).contains("\\definecolor{am-fill-d1e5f0}{HTML}{D1E5F0}"),
            Svg.document(d).contains("fall-am-fill-fddbc7"),
          )
      ),
      // compiled, so it proves pdflatex takes the faded mixes and the scaled tips; reported as
      // ignored where pdflatex or pdftoppm is missing, never passed in silence
      {
        val compiled = test("compiled, faded boxes and weighted lines go through pdflatex in each look"):
          val runs   =
            for
              (look, palette) <- looks
              graph           <- List(gap, volumes, retiring, heatMap, long)
            yield (look, graph.name, compiles(drawn(graph), palette))
          val failed = runs.filterNot(_._3._1).map(r => s"${r._1} ${r._2}: ${r._3._2}")
          assertTrue(failed.isEmpty)
        if latexTools then compiled else compiled @@ TestAspect.ignore
      },
    )

end EncodingSpec
