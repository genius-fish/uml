package fish.genius.uml.render

/** Render-time failures. */
enum PUmlError extends Throwable derives CanEqual:

  /** PlantUML library threw while parsing or rendering the source. */
  case RenderFailed(cause: Throwable)

  /**
   * PlantUML parsed the source but found a syntax error. Without this check
   * PlantUML would silently render its error page as a "successful" image.
   */
  case InvalidSource(message: String)

  /** External preview/viewer command failed. */
  case ViewerFailed(cause: Throwable)

  /** Anything else (filesystem, IO, JVM-side). */
  case Internal(cause: Throwable)

  override def getMessage: String = this match
    case RenderFailed(cause)  => s"PlantUML render failed: ${cause.getMessage}"
    case InvalidSource(error) => s"PlantUML rejected the source: $error"
    case ViewerFailed(cause)  => s"Preview failed: ${cause.getMessage}"
    case Internal(cause)      => s"Internal render error: ${cause.getMessage}"

end PUmlError
