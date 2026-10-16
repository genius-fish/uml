# Release 1.20.1

**Release Date:** 2026-10-16

This patch release fixes a rendering bug in the `diagram` module's TikZ writer that dates back to 1.19.0. When a connector had a colour and one or more bends, the writer added `fill=<ink>` to its `\draw` command. On a bent path, TikZ then filled the whole polygon the path enclosed. As a result, every PDF showed a solid coloured plane over part of the figure wherever a proposed or not-modelled relationship had a bend. SVG and PNG output were not affected.

The fix writes only the draw colour. A filled `arrows.meta` tip already takes that colour, and an open tip sets its own paper fill, so arrowheads look the same as before. A new spec compiles a bent coloured connector with `pdflatex` and counts its pixels to show that this is fixed in the real PDF output.

## Highlights

- **No more filled planes behind coloured connectors in TikZ/PDF:** a coloured connector with bends now draws only its line and its arrowhead.
- **Compiled regression test:** the TikZ output is checked after a real `pdflatex` and `pdftoppm` render. On a machine without those tools, the check shows as ignored instead of passing silently.

## Changelog

### Fixed
- `Tikz`: a coloured `Prim.Connector` now writes `draw=<ink>` without `fill=<ink>`. Before this fix, a coloured connector with a bend (an L-shaped or orthogonal route) filled the area its path enclosed in every LaTeX/PDF render. Filled and open arrowheads still draw in the connector's colour. The straight crossing hop is unaffected.

### Added
- `DiagramSpec`: a source-level check that a coloured, bent connector's `\draw` line has a draw colour and no `fill=`. The test first checks that it found the `\draw` line.
- `DiagramSpec`: a compiled check that builds an L-shaped blue connector with pdflatex, rasterises it at 300 dpi and counts its blue pixels. It covers a filled head, an open head and no head:
  - the connector stays a line plus a head and never the enclosed plane (on 1.20.0 the plane was 82,017 pixels);
  - both head styles add their own blue.

  The check is marked ignored when `pdflatex` or `pdftoppm` is missing, and it removes its temporary directories.

### Changed
- The existing pdflatex arrows test imports `scala.sys.process.{Process, ProcessLogger}` instead of using fully qualified names (test code only).
- The project's work is now tracked in its own Jira project, **UML**, on the geniusfish site. `.gf-standards.json` points review and architecture-plan tooling at it, and `CLAUDE.md` documents it. This does not affect the published artifacts.

## Module Changes

- **`genius-uml-diagram`:** the TikZ connector fix in `Tikz.scala`, plus new tests in `DiagramSpec`.
- **`genius-uml-core`, `genius-uml-render`, `genius-uml-scene`, `genius-uml-testkit`:** no code changes. They are republished at 1.20.1 alongside the fix.

## Compatibility

- **Scala:** 3.8.3
- **ZIO:** 2.1.25 (zio-process 0.8.0)
- **PlantUML:** 1.2026.2
- **Eclipse ELK:** 0.12.0 (xbase.lib 2.36.0)
- **zio-json:** 0.7.3
- **os-lib:** 0.11.8, **sourcecode:** 0.4.4

The API is unchanged and 1.20.1 is a drop-in replacement for 1.20.0. The TikZ output for coloured connectors loses its `fill=` option, so golden fixtures that captured the 1.20.0 TikZ source of a coloured connector need to be regenerated. The compiled test needs `pdflatex` and `pdftoppm` (poppler) on the test machine and is ignored where they are missing.
