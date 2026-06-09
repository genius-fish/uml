package fish.genius.uml.render

import zio.*

import fish.genius.uml.ast.PUmlNode

import net.sourceforge.plantuml.FileFormat

/**
 * Where rendered diagrams are persisted on disk.
 *
 * The engine renders into a scoped temp workspace that is deleted when the
 * scope closes (see [[PUmlEngine]]); this object copies the result out to a
 * stable, well-known location so tests and examples leave inspectable files
 * behind.
 *
 * The layout is `<root>/out/<format>/<category>/<name><suffix>`:
 *   - `<root>` is the Mill workspace root (`MILL_WORKSPACE_ROOT`) when set,
 *     otherwise the current working directory — matching `Golden`.
 *   - `<format>` is the file suffix without the leading dot (`svg`, `png`,
 *     `eps`, `tex`, ...).
 *   - `<category>` separates test output ([[Test]]) from runnable example
 *     output ([[Examples]]).
 */
object DiagramOutput:

  /** Category for diagrams rendered by the test suites. */
  val Test = "test"

  /** Category for diagrams rendered by the runnable examples. */
  val Examples = "examples"

  /** Root under which the `out/` tree lives. */
  def root: os.Path =
    sys.env.get("MILL_WORKSPACE_ROOT").map(os.Path(_)).getOrElse(os.pwd)

  /** `svg`, `png`, `eps`, `tex`, ... derived from the format's file suffix. */
  def formatDir(format: FileFormat): String =
    format.getFileSuffix.stripPrefix(".")

  /** `<root>/out/<format>/<category>`. */
  def dir(category: String, format: FileFormat): os.Path =
    root / "out" / formatDir(format) / category

  /** `<root>/out/<format>/<category>/<name><suffix>`. */
  def path(
    category: String,
    name: String,
    format: FileFormat,
  ): os.Path =
    dir(category, format) / s"$name${format.getFileSuffix}"

  /**
   * Render `doc` and persist the result under `out/<format>/<category>`,
   * returning the saved path. Unlike the raw [[PUmlEngine.render]], the
   * returned file outlives the surrounding `Scope`.
   */
  def save(
    doc: PUmlNode,
    category: String,
    name: String,
    format: FileFormat = FileFormat.SVG,
  ): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    for
      rendered <- PUmlEngine.render(doc, name, format)
      dest     <- copyOut(rendered, path(category, name, format))
    yield dest

  /**
   * Copy `rendered` to `dest`, creating parent directories, and return `dest`.
   * Filesystem failures surface as [[PUmlError.Internal]].
   */
  private[render] def copyOut(rendered: os.Path, dest: os.Path): IO[PUmlError, os.Path] =
    ZIO
      .attemptBlocking:
        os.makeDir.all(dest / os.up)
        os.copy.over(rendered, dest)
        dest
      .mapError(PUmlError.Internal.apply)

end DiagramOutput
