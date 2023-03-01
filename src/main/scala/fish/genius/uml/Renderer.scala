package fish.genius.uml

import fish.genius.logging.Loggable
import fish.genius.uml.model.Specification
import net.sourceforge.plantuml.{
  FileFormat,
  FileFormatOption,
  SourceStringReader
}

import java.io.{File, FileOutputStream}
import java.nio.file.Files
import scala.util.{Failure, Success, Try}

object Renderer extends Loggable {

  def latexFull(
      specification: Specification,
      filename: String = "diagram"
  ): Option[File] =
    render(specification, filename, FileFormat.LATEX)

  def latex(
      specification: Specification,
      filename: String = "diagram"
  ): Option[File] =
    render(specification, filename, FileFormat.LATEX_NO_PREAMBLE)

  def svg(
      specification: Specification,
      filename: String = "diagram"
  ): Option[File] =
    render(specification, filename, FileFormat.SVG)
  def eps(
      specification: Specification,
      filename: String = "diagram"
  ): Option[File] =
    render(specification, filename, FileFormat.EPS)

  def render(
      specification: Specification,
      filename: String = "diagram",
      fileFormat: FileFormat = FileFormat.SVG
  ): Option[File] =
    Try {
      val workingDirectory = Files.createTempDirectory("uml").toFile
      val sourceStringReader = new SourceStringReader(specification.toString)
      val outputFile =
        new File(workingDirectory, s"$filename${fileFormat.getFileSuffix}")
      sourceStringReader.outputImage(
        new FileOutputStream(outputFile),
        new FileFormatOption(fileFormat)
      )
      outputFile
    } match {
      case Success(value) => Some(value)
      case Failure(cause) =>
        exception(cause)
        None
    }

}
