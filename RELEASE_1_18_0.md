# Release 1.18.0

**Release Date:** 2026-10-15

genius-uml 1.18.0 adds a layout direction to flat ArchiMate diagrams. A diagram can now lay its layers out from left to right, so a chain or a flow comes out wide instead of tall. That shape fits a slide better than a page. The default is still top to bottom, so existing diagrams render as they did.

Ports follow the direction. Laid out to the right, a connector leaves its source on the east side and enters its target on the west side. A connector that runs back against the flow, such as a reply, goes from west to east and is routed straight rather than looping around its boxes.

## Highlights

- **Left-to-right diagrams:** the new `Diagram.Options(direction = SceneLayout.Direction.Right)` draws a chain wide, for slides.
- **Ports follow the direction:** connectors attach east to west when the layers run right, and north to south when they run down.
- **Replies are routed cleanly:** an edge that runs against the layers is handed to ELK reversed, in both directions.

## New Features

### Layout direction

`SceneLayout.Options` and `Diagram.Options` have a new `direction` field of type `SceneLayout.Direction`, an enum with two cases:

- `Down` (the default): the layers run down the page, which is back to front in a 3D scene.
- `Right`: the layers run from left to right.

```scala
import fish.genius.uml.diagram.Diagram
import fish.genius.uml.scene.SceneLayout

val wide = Diagram.draw(
  graph,
  Diagram.Options(direction = SceneLayout.Direction.Right),
)
```

With `Right`, no row is folded, because each row is a column of layers. `rowWidth` and `pageWidth` therefore have no effect in that direction. The option is meant for flat diagrams: a 3D scene's panes still assume the layers run back to front.

## Changelog

### Added
- `SceneLayout.Direction` enum (`Down`, `Right`), which derives `CanEqual`.
- `direction` field on `SceneLayout.Options` and `Diagram.Options`, defaulting to `Down`.
- `DiagramSpec` cases with these checks:
  - A five-step chain is taller than wide by default and wider than tall with `Right`.
  - In a fork and a join, every connector leaves east and enters west.
  - In a request and its reply, the reply runs from west to east and no line overflows.
  - In a nested landscape laid out to the right, no two boxes overlap and every connector is kept.
- `DSL.md` documents the `direction` option.

### Changed
- ELK's `CoreOptions.DIRECTION` is now set from the options (`DOWN` or `RIGHT`) instead of always `DOWN`.
- `ElkPass.sidesOf` chooses port sides on the axis of the layout direction first and falls back to the other axis only when the boxes do not stand apart along it.
- The check for an edge that runs against the layers depends on the direction: north to south when the layers run down, west to east when they run right.
- The row-folding pass is skipped when the direction is `Right`.

## Module Changes

- **`scene`:** `SceneLayout` (new `Direction` and `Options.direction`) and `ElkPass` (direction-aware ELK direction, port sides and reversed edges).
- **`diagram`:** `Diagram.Options.direction`, passed through to the scene layout, plus the new `DiagramSpec` cases.
- **`core`, `render`, `testkit`, `examples`:** unchanged.

## Compatibility

- Scala 3.8.3
- ELK 0.12.0, zio-json 0.7.3, xtext xbase.lib 2.36.0 (`scene`, `diagram`)
- ZIO 2.1.25, zio-process 0.8.0, os-lib 0.11.8, PlantUML 1.2026.2 (`render`)
- sourcecode 0.4.4 (`core`)

The change is source-compatible: the new fields have defaults, so existing calls compile and produce the same layout.
