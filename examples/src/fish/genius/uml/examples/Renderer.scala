package fish.genius.uml.examples

import zio.*

import fish.genius.uml.ast.PUmlNode
import fish.genius.uml.render.{Emit, PUmlEngine}

/**
 * Shared "print PlantUML, then maybe render SVG" plumbing for the examples.
 *
 * Every example calls `Renderer.run(name, doc)`:
 *   - the rendered PlantUML source is always printed to stdout;
 *   - if `PLANTUML_AVAILABLE` is set in the environment, the document is also
 *     rendered to SVG and the resulting file is copied into the current
 *     working directory as `<name>.svg`.
 */
object Renderer:

  def run(name: String, doc: PUmlNode): ZIO[Any, Throwable, Unit] =
    val printSource = Console.printLine(Emit.emit(doc))

    val maybeRender: ZIO[Any, Throwable, Unit] =
      if !sys.env.contains("PLANTUML_AVAILABLE") then ZIO.unit
      else
        ZIO
          .scoped:
            for
              path <- PUmlEngine.renderSvg(doc, name)
              dest = os.pwd / s"$name.svg"
              _ <- ZIO.attempt(os.copy.over(path, dest))
              _ <- Console.printLine(s"SVG written to $dest")
            yield ()
          .provide(PUmlEngine.live)

    printSource *> maybeRender

  end run

end Renderer
