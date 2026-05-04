package fish.genius.uml.render

import zio.*

import fish.genius.uml.ast.PUmlNode

import net.sourceforge.plantuml.FileFormat

/**
 * Convenience facade over [[PUmlEngine]] that mirrors the original
 * `fish.genius.uml.Renderer` shape: top-level methods named after each
 * supported PlantUML output format, plus a generic `render`.
 *
 * All methods scope the output file to the surrounding `ZIO.scoped` boundary;
 * callers who want to keep the rendered file beyond that boundary should
 * `os.copy.over` it out first.
 */
object Renderer:

  def latexFull(
    doc: PUmlNode,
    filename: String = "diagram",
  ): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    PUmlEngine.renderLatexFull(doc, filename)

  def latex(
    doc: PUmlNode,
    filename: String = "diagram",
  ): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    PUmlEngine.renderLatex(doc, filename)

  def svg(
    doc: PUmlNode,
    filename: String = "diagram",
  ): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    PUmlEngine.renderSvg(doc, filename)

  def png(
    doc: PUmlNode,
    filename: String = "diagram",
  ): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    PUmlEngine.renderPng(doc, filename)

  def eps(
    doc: PUmlNode,
    filename: String = "diagram",
  ): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    PUmlEngine.renderEps(doc, filename)

  def render(
    doc: PUmlNode,
    filename: String = "diagram",
    fileFormat: FileFormat = FileFormat.SVG,
  ): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
    PUmlEngine.render(doc, filename, fileFormat)

  extension (doc: PUmlNode)

    def renderedAsSvg(filename: String = "diagram"): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
      svg(doc, filename)

    def renderedAsPng(filename: String = "diagram"): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
      png(doc, filename)

    def renderedAsEps(filename: String = "diagram"): ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
      eps(doc, filename)

    def renderedAsLatex(filename: String = "diagram")
      : ZIO[PUmlEngine & Scope, PUmlError, os.Path] =
      latex(doc, filename)

end Renderer
