package fish.genius.uml.examples

import zio.*

import fish.genius.uml.ast.PUmlNode
import fish.genius.uml.render.{Emit, PUmlEngine}

import net.sourceforge.plantuml.FileFormat

/**
 * Shared "print PlantUML, then maybe render" plumbing for the examples.
 *
 * Every example calls `Renderer.run(name, doc)`:
 *   - the rendered PlantUML source is always printed to stdout;
 *   - if `PLANTUML_AVAILABLE` is set, the document is also rendered through
 *     the live engine and the resulting file is copied into the current
 *     working directory as `<name>.<suffix>`.
 *
 * The output format defaults to SVG and can be overridden with the
 * `PLANTUML_FORMAT` env var (case-insensitive: `svg`, `eps`, `png`,
 * `latex`, `latex_no_preamble`).
 */
object Renderer:

  private def lookupFormat: FileFormat =
    sys.env.get("PLANTUML_FORMAT").map(_.trim.toLowerCase) match
      case Some("eps")               => FileFormat.EPS
      case Some("png")               => FileFormat.PNG
      case Some("latex")             => FileFormat.LATEX
      case Some("latex_no_preamble") => FileFormat.LATEX_NO_PREAMBLE
      case _                         => FileFormat.SVG

  def run(name: String, doc: PUmlNode): ZIO[Any, Throwable, Unit] =
    val printSource = Console.printLine(Emit.emit(doc))

    val maybeRender: ZIO[Any, Throwable, Unit] =
      if !sys.env.contains("PLANTUML_AVAILABLE") then ZIO.unit
      else
        val format = lookupFormat
        ZIO
          .scoped:
            for
              path <- PUmlEngine.render(doc, name, format)
              dest = os.pwd / s"$name${format.getFileSuffix}"
              _ <- ZIO.attempt(os.copy.over(path, dest))
              _ <- Console.printLine(s"${format.name} written to $dest")
            yield ()
          .provide(PUmlEngine.live)

    printSource *> maybeRender

  end run

end Renderer
