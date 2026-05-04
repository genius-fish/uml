package fish.genius.uml.render

/** Render-time failures. */
enum PUmlError extends Throwable derives CanEqual:

  /** PlantUML library threw while parsing or rendering the source. */
  case RenderFailed(cause: Throwable)

  /** External preview/viewer command failed. */
  case ViewerFailed(cause: Throwable)

  /** Anything else (filesystem, IO, JVM-side). */
  case Internal(cause: Throwable)

  override def getMessage: String = this match
    case RenderFailed(cause) => s"PlantUML render failed: ${cause.getMessage}"
    case ViewerFailed(cause) => s"Preview failed: ${cause.getMessage}"
    case Internal(cause)     => s"Internal render error: ${cause.getMessage}"

end PUmlError
