package fish.genius.uml

import fish.genius.io.{Command, Shell}

import java.io.File

trait CanPreview {
  def preview(file: File) =
    Shell.run(
      Command("preview", List("evince")) + file.getName,
      file.getParentFile
    )

}
