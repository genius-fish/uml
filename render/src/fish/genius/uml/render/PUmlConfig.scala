package fish.genius.uml.render

import net.sourceforge.plantuml.FileFormat

/**
 * Configuration for the PlantUML render runtime.
 *
 * @param defaultFormat the file format used when callers don't specify one
 * @param workspaceRoot when set, render workspaces are created under this
 *                      directory; when None, the OS default temp location is
 *                      used.
 */
final case class PUmlConfig(
  defaultFormat: FileFormat = FileFormat.SVG,
  workspaceRoot: Option[os.Path] = None)
  derives CanEqual

object PUmlConfig:
  /** Default config: SVG output, OS temp dir. */
  val default: PUmlConfig = PUmlConfig()
