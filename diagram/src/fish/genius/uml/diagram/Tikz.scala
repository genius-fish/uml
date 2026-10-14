package fish.genius.uml.diagram

import java.util.Locale

// A Drawing as a tikzpicture, in absolute centimetres: LaTeX computes nothing, it only
// draws, and draws it the way a printed book would. A box has its layer's colour with
// a faint fall-off from top to bottom, an edge a shade darker than its fill, and a soft
// short shadow; a container is a lighter field behind what it holds; a connector is a
// fine line with rounded bends, broken where it passes under another, and ends in an
// arrowhead shaped for its relationship. Each line of text is a node on its
// baseline, where Diagram put it, in the weight Diagram asked for.
//
// The colours are provided, not defined, so a document that defines am-application
// (and the others) before the picture keeps its own; a palette passed here is defined
// outright. am-paper is the page the picture stands on: gaps and labels outside any
// container are cut in it.
object Tikz:

  val libraries: String = "\\usetikzlibrary{arrows.meta,shadows.blur}"

  def picture(drawing: Diagram.Drawing, palette: Map[String, String] = Map.empty): String =
    val own     = Notation.colours(palette).filterNot(Notation.palette.contains)
    val colours = Notation.colours(palette).map: (name, hex) =>
      if own.exists(_._1 == name) then s"\\definecolor{$name}{HTML}{$hex}"
      else s"\\providecolor{$name}{HTML}{$hex}"
    val face    = drawing.style.typeface
    (colours ++ List(s"\\begin{tikzpicture}[$STYLES]") ++
    drawing.prims.map(prim(_, face)) ++ List("\\end{tikzpicture}")).mkString("\n")

  // a standalone document around the picture, for pdflatex, set in the drawing's face
  def standalone(drawing: Diagram.Drawing, palette: Map[String, String] = Map.empty): String =
    (List("\\documentclass[tikz,border=6pt]{standalone}", "\\usepackage[T1]{fontenc}") ++
    drawing.style.typeface.preamble ++
    List(libraries, "\\begin{document}", picture(drawing, palette), "\\end{document}"))
      .mkString("\n")

  // the picture's own styles, named so each shape says what it is rather than how
  private val STYLES = List(
    "x=1cm,y=1cm,line join=round,line cap=round,font=\\sffamily",
    "am shadow/.style={blur shadow={shadow xshift=0.8pt,shadow yshift=-1pt," +
    "shadow blur radius=0.8pt,shadow blur steps=8,shadow opacity=17}}",
    "am faint shadow/.style={blur shadow={shadow xshift=0.6pt,shadow yshift=-0.8pt," +
    "shadow blur radius=0.6pt,shadow blur steps=6,shadow opacity=8}}",
    "am link/.style={line width=0.6pt,draw=am-line,rounded corners=2.2pt}",
  ).mkString(",")

  private def f(value: Double): String =
    String.format(Locale.ROOT, "%.3f", Double.box(value)).replaceAll("\\.?0+$", "")

  private def pt(p: Pt): String = s"(${f(p._1)},${f(p._2)})"

  private def dash(d: Dash): Option[String] = d match
    case Dash.Solid  => None
    case Dash.Dashed => Some("dash pattern=on 2.8pt off 1.8pt")
    // round caps on a dash of nothing: a line of round dots
    case Dash.Dotted => Some("dash pattern=on 0pt off 1.9pt,line width=0.95pt")

  private def options(parts: List[Option[String]]): String =
    val all = parts.flatten
    if all.isEmpty then "" else all.mkString("[", ",", "]")

  private def head(h: Head): String = h match
    case Head.FilledDiamond  => "Diamond[length=6.6pt,width=4.2pt,round]"
    case Head.OpenDiamond    => "Diamond[open,length=6.6pt,width=4.2pt,round,fill={am-paper}]"
    case Head.Dot            => "Circle[length=3pt]"
    case Head.FilledTriangle => "Stealth[length=5.2pt,width=3.9pt,inset=1.3pt,round]"
    case Head.OpenTriangle   => "Triangle[open,length=6pt,width=5.4pt,round,fill={am-paper}]"
    case Head.Barb           => "Straight Barb[length=2.6pt,width=5pt,round]"
    case Head.OpenDot        => "Circle[open,length=3.6pt,fill={am-paper}]"

  private def plain(ink: Ink): Option[String] = ink match
    case Ink.Empty       => None
    case Ink.Paper       => Some("am-paper")
    case Ink.Line        => Some("am-line")
    case Ink.Layer(name) => Some(name)

  // A shape's look by what it is part of: its edge, how its fill is laid, its weight.
  // A box's layer colour falls off a little from top to bottom and stands on a soft
  // shadow; a container is a lighter field on a fainter one; a mark (an icon, a band)
  // is drawn in its box's edge colour, filled lighter than the box.
  private def look(role: Role, fill: Ink): List[Option[String]] = role match
    case Role.Element(layer)   =>
      List(
        Some(s"draw=$layer!${Look.EDGE}!am-text"),
        Option.when(fill != Ink.Empty)(s"top color=$layer!${Look.TOP}!am-paper,bottom color=$layer"),
        Some("am shadow"),
        Some("line width=0.55pt"),
      )
    case Role.Container(layer) =>
      List(
        Some(s"draw=$layer!${Look.CONTAINER_EDGE}!am-text"),
        Some(s"fill=$layer!${Look.CONTAINER_FILL}!am-paper"),
        Some("am faint shadow"),
        Some("line width=0.5pt"),
      )
    case Role.Group            =>
      List(
        Some("draw=am-muted"),
        Some(s"fill=am-muted!${Look.GROUP_FILL}!am-paper"),
        Some("line width=0.55pt"),
      )
    case Role.Mark(layer)      =>
      val inked = fill match
        case Ink.Paper => Some(s"$layer!${Look.MARK_LIGHT}!am-paper")
        case Ink.Line  => Some(s"$layer!${Look.MARK_EDGE}!am-text")
        case other     => plain(other)
      List(
        Some(s"draw=$layer!${Look.MARK_EDGE}!am-text"),
        inked.map("fill=" + _),
        Some("line width=0.4pt"),
      )
    case Role.Halo             => List(Some("fill=am-paper"))
    case Role.Patch(on)        => List(field(on).map("fill=" + _))
    case Role.Plain            =>
      List(Some("draw=am-line"), plain(fill).map("fill=" + _), Some("line width=0.5pt"))

  // the fill a text's backdrop takes, the field it stands on
  private def field(role: Role): Option[String] = role match
    case Role.Container(layer) => Some(s"$layer!${Look.CONTAINER_FILL}!am-paper")
    case Role.Group            => Some(s"am-muted!${Look.GROUP_FILL}!am-paper")
    case Role.Element(layer)   => Some(layer)
    case _                     => Some("am-paper")

  private val PATCH_X  = "1.2pt"
  private val PATCH_Y  = "0.6pt"
  private val PATCH_CM = 1.2 * 2.54 / 72

  private def shape(
    role: Role,
    fill: Ink,
    d: Dash,
    path: String,
    extra: Option[String] = None,
  ): String = s"\\path${options(look(role, fill) ++ List(dash(d), extra))} $path;"

  private def prim(p: Prim, face: Typeface): String = p match
    case Prim.Poly(points, closed, fill, d, role)                   =>
      shape(role, fill, d, points.map(pt).mkString(" -- ") + (if closed then " -- cycle" else ""))
    case Prim.Circle(c, r, fill, role)                              =>
      shape(role, fill, Dash.Solid, s"${pt(c)} circle[radius=${f(r)}]")
    case Prim.Ellipse(c, rx, ry, fill, role)                        =>
      shape(role, fill, Dash.Solid, s"${pt(c)} ellipse[x radius=${f(rx)},y radius=${f(ry)}]")
    case Prim.Box(x, y, w, h, r, fill, d, stroke, role)             =>
      val round = Option.when(r > 0)(s"rounded corners=${f(r)}cm")
      val edge  = if stroke then role
      else
        role match
          case Role.Patch(_) => role
          case _             => Role.Halo
      shape(edge, fill, d, s"${pt((x, y))} rectangle ${pt((x + w, y + h))}", round)
    case Prim.Text(at, text, size, weight, tone, centred, backdrop) =>
      val anchor     = if centred then "base" else "base west"
      val ink        = if tone == Emphasis.Muted then "am-muted" else "am-text"
      val select     = face.select.getOrElse(weight, "")
      // on a backdrop the node grows by its padding, so a west-anchored line moves
      // back by as much to keep its text where Diagram put it
      val (patch, x) = backdrop.flatMap(field) match
        case Some(fill) => (
            s"fill=$fill,inner xsep=$PATCH_X,inner ysep=$PATCH_Y,",
            if centred then at._1 else at._1 - PATCH_CM,
          )
        case None       => ("inner sep=0pt,", at._1)
      s"\\node[anchor=$anchor,$patch text=$ink,font=\\sffamily$select" +
      s"\\fontsize{${f(size)}}{${f(size * 1.2)}}\\selectfont] at ${pt((x, at._2))} {${escape(text)}};"
    case Prim.Connector(points, d, start, end)                      =>
      val arrows = s"arrows={${start.map(head).getOrElse("")}-${end.map(head).getOrElse("")}}"
      s"\\draw${options(List(Some("am link"), dash(d), Some(arrows)))} " +
      points.map(pt).mkString(" -- ") + ";"

  def escape(text: String): String = Notation.clean(text).flatMap:
    case '\\' => "\\textbackslash{}"
    case '&'  => "\\&"
    case '%'  => "\\%"
    case '$'  => "\\$"
    case '#'  => "\\#"
    case '_'  => "\\_"
    case '{'  => "\\{"
    case '}'  => "\\}"
    case '~'  => "\\textasciitilde{}"
    case '^'  => "\\textasciicircum{}"
    case c    => c.toString

end Tikz
