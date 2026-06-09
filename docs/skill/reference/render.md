# render — the rendering pipeline & error conventions

Package `fish.genius.uml.render`. The only module that performs IO: a ZIO 2
service, pure AST in, real image out, temp state Scope-managed, typed errors.
PlantUML (`net.sourceforge.plantuml`) ships with this module — no external
install needed for the core diagram kinds. Depends only on `core`.

## PUmlEngine — the service

```scala
trait PUmlEngine:
  def render(doc: PUmlNode, filename: String, format: FileFormat): ZIO[Scope, PUmlError, os.Path]
  def renderBytes(doc: PUmlNode, format: FileFormat): ZIO[Any, PUmlError, Array[Byte]]
```
`FileFormat` is PlantUML's `net.sourceforge.plantuml.FileFormat`. The returned
`os.Path` lives in a Scope-managed temp dir — **deleted when the scope closes**;
copy it out (or use `DiagramOutput.save`). The companion offers format accessors
(all `filename: String = "diagram"`):

| Accessor | Format |
| --- | --- |
| `renderSvg(doc, filename)` | SVG |
| `renderPng(doc, filename)` | PNG |
| `renderEps(doc, filename)` | EPS |
| `renderLatex(doc, filename)` | LaTeX (no preamble) |
| `renderLatexFull(doc, filename)` | LaTeX (full preamble) |
| `render(doc, filename, format)` | generic |
| `renderBytes(doc, format)` | in-memory `Array[Byte]` |

Layers: `live` (default `PUmlConfig`, SVG, OS temp), `liveWith(config)`, and
`test` (writes the `.puml` source to a scoped temp file without invoking PlantUML).

## PUmlConfig

```scala
final case class PUmlConfig(
  defaultFormat: FileFormat = FileFormat.SVG,
  workspaceRoot: Option[os.Path] = None)
```
`PUmlConfig.default` = SVG, OS temp dir.

## Persisting output — DiagramOutput

`PUmlEngine.render*` paths vanish with the scope. `DiagramOutput` renders **and**
copies the result to a stable location `out/<format>/<category>/<name>.<suffix>`:

```scala
def save(doc: PUmlNode, category: String, name: String, format: FileFormat = FileFormat.SVG)
  : ZIO[PUmlEngine & Scope, PUmlError, os.Path]
```
Categories: `DiagramOutput.Test` / `DiagramOutput.Examples`. The root is
`MILL_WORKSPACE_ROOT` (else cwd); the whole `out/` tree is git-ignored. Helpers:
`dir(category, format)`, `path(category, name, format)`, `formatDir(format)`.

## Convenience facade & viewer

`Renderer` mirrors the accessors (`svg`, `png`, `eps`, `latex`, `latexFull`,
`render`) and adds `PUmlNode` extension methods: `.renderedAsSvg(...)`,
`.renderedAsPng(...)`, `.renderedAsEps(...)`, `.renderedAsLatex(...)`.

`PUmlViewer.open(file): ZIO[Any, PUmlError, os.Path]` opens a rendered file with
the platform viewer (`open`/`explorer`/`xdg-open`).

`Workspace(root)` is the on-disk layout for a single render
(`.output(filename, suffix)`).

## Error-handling conventions

Two kinds of failure, handled differently — same philosophy as `genius-latex`.

**Render-time failures → typed ZIO channel.** The `PUmlError` enum sits in the
`E` position of every `render` effect:

| Case | Means |
| --- | --- |
| `RenderFailed(cause)` | PlantUML threw while parsing/rendering the source |
| `ViewerFailed(cause)` | an external preview/viewer command failed |
| `Internal(cause)` | filesystem / IO / JVM-side problems |

**Author-time misuse → compile errors / fail-fast.** The pure `core` DSL builds
eagerly and returns `PUmlNode`; structural mistakes are caught at compile time by
the marker system (e.g. calling `shape` outside `archimateDiagram`), not at
runtime. There is no error channel in `core` because there is nothing to fail
against — building a diagram is a pure function.
