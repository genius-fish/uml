package fish.genius.uml.render

import zio.*
import zio.process.Command

/**
 * Open a rendered diagram with the OS's default viewer (or the first
 * candidate from a small known list if `xdg-open` / `open` is unavailable).
 *
 * Used by example apps and by tests that want to eyeball the output. The
 * caller is responsible for making sure the file outlives the viewer (i.e.
 * for copying it out of any [[Scope]]-managed render workspace before
 * invoking the viewer).
 */
object PUmlViewer:

  def open(file: os.Path): ZIO[Any, PUmlError, os.Path] =
    val candidates =
      if scala.util.Properties.isMac then Seq("open")
      else if scala.util.Properties.isWin then Seq("explorer")
      else Seq("xdg-open")

    val tryAll: ZIO[Any, Throwable, os.Path] =
      ZIO.foldLeft(candidates)(Option.empty[os.Path]):
        case (Some(p), _) => ZIO.succeed(Some(p))
        case (None, cmd)  =>
          Command(cmd, file.toString)
            .workingDirectory(file.toIO.getParentFile)
            .run
            .map(_ => Some(file))
            .catchAll(_ => ZIO.succeed(None))
      .flatMap:
        case Some(p) => ZIO.succeed(p)
        case None    => ZIO.fail(new RuntimeException(s"No viewer found for $file"))

    tryAll.mapError(PUmlError.ViewerFailed.apply)

  end open

end PUmlViewer
