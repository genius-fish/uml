package fish.genius.uml.render

import java.io.OutputStream

import zio.*

import fish.genius.uml.ast.PUmlNode

import net.sourceforge.plantuml.{FileFormat, FileFormatOption, SourceStringReader}

/**
 * Render a [[PUmlNode]] AST into a diagram file on disk.
 *
 * The returned path lives inside a [[Scope]]-managed temp workspace; when the
 * scope closes, the workspace (and the file along with it) is deleted. Copy
 * the file out of the scope before the boundary closes if you need to keep it.
 *
 * Two implementations are provided:
 *   - [[PUmlEngine.live]]: a real engine that uses the PlantUML library's
 *     [[net.sourceforge.plantuml.SourceStringReader]] to render the diagram
 *   - [[PUmlEngine.test]]: a test engine that writes the `.puml` source to
 *     disk without invoking PlantUML; useful for unit tests of downstream
 *     code where dragging in the rendering pipeline is overkill.
 */
trait PUmlEngine:

  /** Render `doc` to a file with the given format and stem. */
  def render(
    doc: PUmlNode,
    filename: String,
    format: FileFormat,
  ): ZIO[Scope, PUmlError, os.Path]

  /** Render `doc` to a fresh in-memory byte array. */
  def renderBytes(
    doc: PUmlNode,
    format: FileFormat,
  ): ZIO[Any, PUmlError, Array[Byte]]

object PUmlEngine:

  // ---------------------------------------------------------------------
  // Accessors
  // ---------------------------------------------------------------------

  def render(
    doc: PUmlNode,
    filename: String = "diagram",
    format: FileFormat = FileFormat.SVG,
  ): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    ZIO.serviceWithZIO[PUmlEngine](_.render(doc, filename, format))

  def renderSvg(doc: PUmlNode, filename: String = "diagram")
    : ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    render(doc, filename, FileFormat.SVG)

  def renderPng(doc: PUmlNode, filename: String = "diagram")
    : ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    render(doc, filename, FileFormat.PNG)

  def renderEps(doc: PUmlNode, filename: String = "diagram")
    : ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    render(doc, filename, FileFormat.EPS)

  def renderLatex(doc: PUmlNode, filename: String = "diagram")
    : ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    render(doc, filename, FileFormat.LATEX_NO_PREAMBLE)

  def renderLatexFull(doc: PUmlNode, filename: String = "diagram")
    : ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    render(doc, filename, FileFormat.LATEX)

  def renderBytes(
    doc: PUmlNode,
    format: FileFormat = FileFormat.SVG,
  ): ZIO[PUmlEngine, PUmlError, Array[Byte]] =
    ZIO.serviceWithZIO[PUmlEngine](_.renderBytes(doc, format))

  // ---------------------------------------------------------------------
  // Layers
  // ---------------------------------------------------------------------

  /** Layer with default config — SVG, OS temp dir. */
  val live: ZLayer[Any, Nothing, PUmlEngine] =
    ZLayer.succeed(PUmlConfig.default) >>> ZLayer.derive[Live]

  /** Layer with an explicit config. */
  def liveWith(config: PUmlConfig): ZLayer[Any, Nothing, PUmlEngine] =
    ZLayer.succeed(config) >>> ZLayer.derive[Live]

  /**
   * Test layer: writes the `.puml` source to a scoped temp file but does not
   * invoke PlantUML. Lets unit tests assert on rendered PlantUML source
   * without needing the rendering pipeline to be on the classpath.
   */
  val test: ZLayer[Any, Nothing, PUmlEngine] =
    ZLayer.succeed(new TestEngine)

  // ---------------------------------------------------------------------
  // Live implementation
  // ---------------------------------------------------------------------

  final class Live(config: PUmlConfig) extends PUmlEngine:

    def render(
      doc: PUmlNode,
      filename: String,
      format: FileFormat,
    ): ZIO[Scope, PUmlError, os.Path] =
      for
        ws <- workspace
        out = ws.output(filename, format.getFileSuffix)
        _ <- writeRendered(doc, format, java.nio.file.Files.newOutputStream(out.toNIO))
      yield out

    def renderBytes(
      doc: PUmlNode,
      format: FileFormat,
    ): ZIO[Any, PUmlError, Array[Byte]] =
      ZIO
        .attemptBlocking:
          val baos = new java.io.ByteArrayOutputStream()
          renderTo(doc, format, baos)
          baos.toByteArray
        .mapError(PUmlError.RenderFailed.apply)

    private def writeRendered(
      doc: PUmlNode,
      format: FileFormat,
      stream: => OutputStream,
    ): ZIO[Any, PUmlError, Unit] =
      ZIO
        .attemptBlocking:
          val s = stream
          try renderTo(doc, format, s)
          finally s.close()
        .mapError(PUmlError.RenderFailed.apply)
        .unit

    private def renderTo(
      doc: PUmlNode,
      format: FileFormat,
      out: OutputStream,
    ): Unit =
      val source = fish.genius.uml.render.Emit.emit(doc)
      val reader = new SourceStringReader(source)
      val opt    = new FileFormatOption(format)
      reader.outputImage(out, opt)

    private def workspace: ZIO[Scope, PUmlError, Workspace] =
      val acquire =
        ZIO.attempt:
          val base = config.workspaceRoot match
            case Some(p) => os.temp.dir(dir = p, prefix = "genius-uml-")
            case None    => os.temp.dir(prefix = "genius-uml-")
          Workspace(base)
        .mapError(PUmlError.Internal.apply)
      ZIO.acquireRelease(acquire)(ws => ZIO.attempt(os.remove.all(ws.root)).orDie)

  end Live

  // ---------------------------------------------------------------------
  // Test implementation
  // ---------------------------------------------------------------------

  /**
   * Test implementation: writes `<filename>.puml` into a scoped temp dir and
   * returns its path. The PlantUML library is never invoked.
   */
  final class TestEngine extends PUmlEngine:

    def render(
      doc: PUmlNode,
      filename: String,
      format: FileFormat,
    ): ZIO[Scope, PUmlError, os.Path] =
      val acquire =
        ZIO
          .attempt:
            val tmp = os.temp.dir(prefix = "genius-uml-test-")
            val src = fish.genius.uml.render.Emit.emit(doc)
            val out = tmp / s"$filename${format.getFileSuffix}"
            os.write.over(tmp / s"$filename.puml", src)
            os.write.over(out, s"%FAKE-${format.name} for testing\n$src")
            (tmp, out)
          .mapError(PUmlError.Internal.apply)
      ZIO
        .acquireRelease(acquire): (tmp, _) =>
          ZIO.attempt(os.remove.all(tmp)).orDie
        .map(_._2)

    end render

    def renderBytes(
      doc: PUmlNode,
      format: FileFormat,
    ): ZIO[Any, PUmlError, Array[Byte]] =
      ZIO
        .attempt:
          s"%FAKE-${format.name} for testing\n${fish.genius.uml.render.Emit.emit(doc)}".getBytes
        .mapError(PUmlError.Internal.apply)

  end TestEngine

end PUmlEngine
