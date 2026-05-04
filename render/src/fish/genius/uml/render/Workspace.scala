package fish.genius.uml.render

/**
 * The on-disk layout of a single render. Created by the engine inside a
 * `Scope`; deleted when the scope closes.
 *
 * @param root the temp directory containing the rendered diagram
 */
final case class Workspace(root: os.Path):
  def output(filename: String, suffix: String): os.Path = root / s"$filename$suffix"
