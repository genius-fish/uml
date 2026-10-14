package fish.genius.uml.diagram

import java.util.Locale

// A Drawing as a tikzpicture, in absolute centimetres: LaTeX computes nothing, it only
// draws. The layer colours are provided, not defined, so a theme that defines
// am-application (and the others) before the picture keeps its own.
object Tikz:

  val libraries: String = "\\usetikzlibrary{arrows.meta}"

  def picture(drawing: Diagram.Drawing): String =
    val style   = drawing.style
    val size    = f(style.fontSize)
    val leaded  = f(style.fontSize * style.lineHeight)
    val colours = Notation.palette.map((name, hex) => s"\\providecolor{$name}{HTML}{$hex}")
    val body    = drawing.prims.map(prim)
    (colours ++
    List(
      s"\\begin{tikzpicture}[x=1cm,y=1cm,line width=0.5pt,line join=round,draw=am-line," +
      s"font=\\sffamily\\fontsize{$size}{$leaded}\\selectfont]"
    ) ++ body ++ List("\\end{tikzpicture}")).mkString("\n")

  // a standalone document around the picture, for pdflatex
  def standalone(drawing: Diagram.Drawing): String =
    List(
      "\\documentclass[tikz,border=4pt]{standalone}",
      "\\usepackage[T1]{fontenc}",
      "\\usepackage{tgheros}",
      libraries,
      "\\begin{document}",
      picture(drawing),
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

  private def prim(p: Prim): String = p match
    case Prim.Poly(points, closed, fill, d)    =>
      val path = points.map(pt).mkString(" -- ") + (if closed then " -- cycle" else "")
      s"\\draw${options(ink(fill).map("fill=" + _), dash(d), Some("line width=0.4pt"))} $path;"
    case Prim.Circle(c, r, fill)               =>
      s"\\draw${options(ink(fill).map("fill=" + _), Some("line width=0.4pt"))} ${pt(c)} circle[radius=${f(r)}];"
    case Prim.Ellipse(c, rx, ry, fill)         =>
      s"\\draw${options(ink(fill).map("fill=" + _), Some("line width=0.4pt"))} ${pt(c)} ellipse[x radius=${f(rx)},y radius=${f(ry)}];"
    case Prim.Box(x, y, w, h, r, fill, d)      =>
      val round = Option.when(r > 0)(s"rounded corners=${f(r)}cm")
      s"\\draw${options(ink(fill).map("fill=" + _), dash(d), round)} ${pt((x, y))} rectangle ${pt((x + w, y + h))};"
    case Prim.Label(at, lines, centred, bold)  =>
      val text   = lines.map(escape).mkString("\\\\")
      val font   = if bold then "\\bfseries " else ""
      val anchor = if centred then "align=center" else "anchor=west,align=left"
      s"\\node[$anchor,inner sep=0pt] at ${pt(at)} {$font$text};"
    case Prim.Connector(points, d, start, end) =>
      val arrows = s"arrows={${start.map(head).getOrElse("")}-${end.map(head).getOrElse("")}}"
      s"\\draw${options(dash(d), Some(arrows))} ${points.map(pt).mkString(" -- ")};"

  def escape(text: String): String = text.flatMap:
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
