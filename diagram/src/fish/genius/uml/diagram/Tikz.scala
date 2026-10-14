package fish.genius.uml.diagram

import java.util.Locale

// A Drawing as a tikzpicture, in absolute centimetres: LaTeX computes nothing, it only
// draws. Each line of text is a node on its baseline, where Diagram put it. The layer
// colours are provided, not defined, so a theme that defines am-application (and the
// others) before the picture keeps its own; a palette passed here is defined outright.
object Tikz:

  val libraries: String = "\\usetikzlibrary{arrows.meta}"

  def picture(drawing: Diagram.Drawing, palette: Map[String, String] = Map.empty): String =
    // ArchiMate's colours provided, so a document's own win; a palette's defined
    val own     = Notation.colours(palette).filterNot(Notation.palette.contains)
    val colours = Notation.colours(palette).map: (name, hex) =>
      if own.exists(_._1 == name) then s"\\definecolor{$name}{HTML}{$hex}"
      else s"\\providecolor{$name}{HTML}{$hex}"
    val body    = drawing.prims.map(prim)
    (colours ++
    List(
      "\\begin{tikzpicture}[x=1cm,y=1cm,line width=0.5pt,line join=round,draw=am-line,font=\\sffamily]"
    ) ++
    body ++ List("\\end{tikzpicture}")).mkString("\n")

  // a standalone document around the picture, for pdflatex
  def standalone(drawing: Diagram.Drawing, palette: Map[String, String] = Map.empty): String =
    List(
      "\\documentclass[tikz,border=4pt]{standalone}",
      "\\usepackage[T1]{fontenc}",
      "\\usepackage{tgheros}",
      libraries,
      "\\begin{document}",
      picture(drawing, palette),
      "\\end{document}",
    ).mkString("\n")

  private def f(value: Double): String =
    String.format(Locale.ROOT, "%.3f", Double.box(value)).replaceAll("\\.?0+$", "")

  private def pt(p: Pt): String = s"(${f(p._1)},${f(p._2)})"

  private def ink(fill: Ink): Option[String] = fill match
    case Ink.Empty       => None
    case Ink.Paper       => Some("white")
    case Ink.Line        => Some("am-line")
    case Ink.Layer(name) => Some(name)

  private def dash(d: Dash): Option[String] = d match
    case Dash.Solid  => None
    case Dash.Dashed => Some("dash pattern=on 3pt off 2pt")
    case Dash.Dotted => Some("dash pattern=on 0.8pt off 1.6pt")

  private def options(parts: Option[String]*): String =
    val all = parts.flatten
    if all.isEmpty then "" else all.mkString("[", ",", "]")

  private def head(h: Head): String = h match
    case Head.FilledDiamond  => "Diamond[length=8pt,width=5pt]"
    case Head.OpenDiamond    => "Diamond[open,fill=white,length=8pt,width=5pt]"
    case Head.Dot            => "Circle[length=3.5pt]"
    case Head.FilledTriangle => "Latex[length=5pt,width=4pt]"
    case Head.OpenTriangle   => "Triangle[open,fill=white,length=7pt,width=6pt]"
    case Head.Barb           => "Straight Barb[length=4pt,width=5pt]"

  private val thin = Some("line width=0.4pt")

  private def prim(p: Prim): String = p match
    case Prim.Poly(points, closed, fill, d)       =>
      val path = points.map(pt).mkString(" -- ") + (if closed then " -- cycle" else "")
      s"\\draw${options(ink(fill).map("fill=" + _), dash(d), thin)} $path;"
    case Prim.Circle(c, r, fill)                  =>
      s"\\draw${options(ink(fill).map("fill=" + _), thin)} ${pt(c)} circle[radius=${f(r)}];"
    case Prim.Ellipse(c, rx, ry, fill)            =>
      s"\\draw${options(ink(fill).map("fill=" + _), thin)} ${pt(c)} ellipse[x radius=${f(rx)},y radius=${f(ry)}];"
    case Prim.Box(x, y, w, h, r, fill, d, stroke) =>
      val round   = Option.when(r > 0)(s"rounded corners=${f(r)}cm")
      val command = if stroke then "\\draw" else "\\fill"
      s"$command${options(ink(fill).map("fill=" + _), dash(d), round)} ${pt((x, y))} rectangle ${pt((x + w, y + h))};"
    case Prim.Text(at, text, size, bold, centred) =>
      val anchor = if centred then "base" else "base west"
      val weight = if bold then "\\bfseries" else ""
      s"\\node[anchor=$anchor,inner sep=0pt,font=\\sffamily$weight\\fontsize{${f(size)}}{${f(size *
          1.2)}}\\selectfont] at ${pt(at)} {${escape(text)}};"
    case Prim.Connector(points, d, start, end)    =>
      val arrows = s"arrows={${start.map(head).getOrElse("")}-${end.map(head).getOrElse("")}}"
      s"\\draw${options(dash(d), Some(arrows))} ${points.map(pt).mkString(" -- ")};"

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
