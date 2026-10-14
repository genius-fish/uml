package fish.genius.uml.diagram

import java.awt.{Font, GraphicsEnvironment}
import java.awt.font.FontRenderContext
import java.io.File

import scala.collection.concurrent.TrieMap
import scala.sys.process.*
import scala.util.Try

// The face a diagram is set in: what a standalone picture loads in LaTeX, how each
// weight is selected there, and the font files a box is measured with, the same files
// LaTeX sets the text from, found with kpsewhich. Without them (no TeX installed),
// the installed families stand in, which is what an SVG or PNG export uses anyway.
final case class Typeface(
  name: String,
  preamble: List[String],
  select: Map[Weight, String],
  files: Map[Weight, String],
  families: List[String])

object Typeface:

  // TeX Gyre Heros, LaTeX's Helvetica: no medium, so a name is regular
  val heros: Typeface = Typeface(
    "TeX Gyre Heros",
    List("\\usepackage{tgheros}", "\\renewcommand{\\familydefault}{\\sfdefault}"),
    Map(
      Weight.Regular -> "\\fontseries{m}\\fontshape{n}\\selectfont",
      Weight.Medium -> "\\fontseries{m}\\fontshape{n}\\selectfont",
      Weight.SemiBold -> "\\fontseries{b}\\fontshape{n}\\selectfont",
      Weight.Italic -> "\\fontseries{m}\\fontshape{it}\\selectfont",
    ),
    Map(
      Weight.Regular -> "texgyreheros-regular.otf",
      Weight.Medium -> "texgyreheros-regular.otf",
      Weight.SemiBold -> "texgyreheros-bold.otf",
      Weight.Italic -> "texgyreheros-italic.otf",
    ),
    List("TeX Gyre Heros", "Nimbus Sans", "Liberation Sans"),
  )

  // Montserrat, the face of the Innovenso documents
  val montserrat: Typeface = Typeface(
    "Montserrat",
    List("\\usepackage[lining]{montserrat}", "\\renewcommand{\\familydefault}{\\sfdefault}"),
    Map(
      Weight.Regular -> "\\fontseries{m}\\fontshape{n}\\selectfont",
      Weight.Medium -> "\\fontseries{medium}\\fontshape{n}\\selectfont",
      Weight.SemiBold -> "\\fontseries{sb}\\fontshape{n}\\selectfont",
      Weight.Italic -> "\\fontseries{m}\\fontshape{it}\\selectfont",
    ),
    Map(
      Weight.Regular -> "Montserrat-Regular.otf",
      Weight.Medium -> "Montserrat-Medium.otf",
      Weight.SemiBold -> "Montserrat-SemiBold.otf",
      Weight.Italic -> "Montserrat-Italic.otf",
    ),
    List("Montserrat"),
  )

  private val found = TrieMap.empty[String, Option[Font]]

  // a font file from the TeX tree, loaded once
  private[diagram] def load(file: String): Option[Font] =
    found.getOrElseUpdate(
      file,
      Try(Seq("kpsewhich", file).!!(ProcessLogger(_ => ())).trim).toOption
        .filter(_.nonEmpty)
        .map(File(_))
        .filter(_.isFile)
        .flatMap(f => Try(Font.createFont(Font.TRUETYPE_FONT, f).deriveFont(100f)).toOption),
    )

end Typeface

// Text measured in a typeface, in centimetres at a size in points, and wrapped greedily
// by words. `family` is the face the text is measured in: the typeface's own files, or
// the first of its families that is installed, or the JVM's own sans.
final class Measure(typeface: Typeface):

  private val installed =
    GraphicsEnvironment.getLocalGraphicsEnvironment.getAvailableFontFamilyNames.toSet

  private val fallback = typeface.families.find(installed).getOrElse(Font.SANS_SERIF)

  private val fonts: Map[Weight, Font] = Weight.values.toList.map: weight =>
    weight -> typeface.files.get(weight).flatMap(Typeface.load).getOrElse:
      val style = weight match
        case Weight.SemiBold => Font.BOLD
        case Weight.Italic   => Font.ITALIC
        case _               => Font.PLAIN
      Font(fallback, style, 100)
  .toMap

  val family: String =
    typeface.files.get(Weight.Regular).flatMap(Typeface.load).fold(fallback)(_.getFamily)

  // the font a writer without LaTeX (Png) draws a weight with
  def font(weight: Weight): Font = fonts(weight)

  private val context = FontRenderContext(null, true, true)

  def width(
    text: String,
    size: Double,
    weight: Weight = Weight.Regular,
  ): Double =
    fonts(weight).getStringBounds(text, context).getWidth / 100.0 * size * Diagram.PT

  def wrap(
    text: String,
    room: Double,
    size: Double,
    weight: Weight = Weight.Regular,
  ): List[String] =
    text.trim
      .split("\\s+")
      .toList
      .filter(_.nonEmpty)
      .foldLeft(List.empty[String]):
        case (Nil, word)                                                         => List(word)
        case (last :: done, word) if width(s"$last $word", size, weight) <= room =>
          s"$last $word" :: done
        case (lines, word)                                                       => word :: lines
      .reverse

end Measure

object Measure:
  def round(value: Double): Double = math.ceil(value * 100) / 100
