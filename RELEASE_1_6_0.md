# Release 1.6.0

**Release Date:** 2026-10-13

genius-uml 1.6.0 lets a 3D ArchiMate scene show what an element is as well as where it stands. A leaf element in a `SceneGraph` can now carry a `Passport`, and the Blender builder draws it on a frosted glass pane beside the element's plinth. The passport is generic: the caller builds it from its own model (an architecture repository, a CMDB) in the reader's language. Its content is a set of sections: text, facts, a rated chip, a rating in letters and a lifecycle timeline.

The layout now keeps floor space for every pane, so a pane never stands on another plinth, pane or connector. When a connector leaves on the pane's preferred side, the pane moves to the opposite side, then behind, then in front. The Scala layout and the Blender builder size panes from the same table of measures, and `SceneContractSpec` checks that the two stay equal.

## Highlights

- **Passports on glass panes**: a leaf `SceneNode` takes an optional `passport`, which renders as a frosted glass pane in a metal rim beside its plinth in Blender.
- **Room for the panes in the layout**: each leaf's ELK node grows by its pane's floor space on the chosen side. The plinth keeps its size and its ports stay centred on its sides.
- **Five orientations**: `Auto` (the default), `Upright`, `Lectern`, `Flat` and `Floating`. `Auto` stands panes upright and lays them flat once the camera looks down more than 56°.
- **`--panes` builder option**: `--panes auto|upright|lectern|flat|floating|off` overrides the scene's pane style for one render.
- **Backward-compatible JSON**: scenes written by 1.5.x read unchanged, with no panes and the default pane style.

## New Features

### Passports

A `Passport` has an eyebrow, a title, an optional subtitle, an ordered list of sections and an optional footer:

| Section | What it shows |
|---|---|
| `PassportSection.Text(body)` | Running text, wrapped to the pane's width |
| `PassportSection.Facts(label, rows)` | Label and value rows (`Fact`) under an optional heading |
| `PassportSection.Rated(label, chip)` | One rating as a `Chip`, with an optional note (criticality, a verdict) |
| `PassportSection.Rating(label, marks, note)` | A rating in letters, such as C·I·A (`Mark(letter, level, tone)`) |
| `PassportSection.Timeline(label, milestones, asOf, summary)` | Dated `Milestone`s on a bar, each colouring the stretch up to the next, with a mark at `asOf` |

A `Tone` (`Neutral`, `Layer`, `Good`, `Info`, `Caution`, `Warning`, `Critical`) says what a chip, a mark or a timeline stretch means, and the builder picks the colour. `Layer` uses the element's own layer colour.

```scala
import java.time.LocalDate
import fish.genius.uml.scene.*

val crm = SceneNode(SceneId("crm"), NodeKind.Element(ApplicationComponent), "Customer Hub",
  passport = Some(Passport(
    eyebrow = "Application component",
    title = "Customer Hub",
    subtitle = Some("CRM · SaaS"),
    sections = List(
      PassportSection.Text("System of record for accounts, contacts and opportunities."),
      PassportSection.Facts(Some("Ownership"), List(Fact("Accountable", "Sales Operations"))),
      PassportSection.Timeline("Lifecycle",
        List(Milestone("Active", LocalDate.of(2021, 3, 15), Tone.Layer),
             Milestone("Deprecated", LocalDate.of(2028, 1, 1), Tone.Caution)),
        asOf = LocalDate.of(2026, 10, 13), summary = Some("Active since March 2021")),
      PassportSection.Rated("Criticality", Chip("High", Tone.Warning, Some("Sales cannot quote without it"))),
      PassportSection.Rating("Security", List(Mark("C", "H", Tone.Warning), Mark("I", "M", Tone.Caution))),
    ),
    footer = Some("as of 13 Oct 2026"),
  )))
```

Only a leaf element shows a pane. Containers, groupings and junctions do not.

### Pane style

`PaneStyle(orientation, side, faceCamera, anchored, tint)` applies to the whole scene. You pass it through `SceneLayout.Options`, and the layout writes it into the `Scene` as `panes`:

```scala
SceneLayout.layout(graph, SceneLayout.Options(
  panes = PaneStyle(orientation = PaneOrientation.Lectern, side = PaneSide.Left)
))
```

- `orientation`:
  - `Auto`: upright while the camera is low, flat on the floor above 56° elevation.
  - `Upright`: on a stem beside the plinth.
  - `Lectern`: leaning back at the placard's 35°.
  - `Flat`: lying on the floor.
  - `Floating`: above the sculpture on a tether, taking no floor.
- `side`: the side a pane prefers (`Right` by default, also `Left`, `Behind`, `Front`). A pane gives way to connectors that leave on that side.
- `faceCamera`: keeps an upright or floating pane turned to the camera while it moves.
- `anchored = false`: leaves out stems and tethers.
- `tint`: `Clear` or `Layer`.

### Pane sizing: `PaneLayout`

`PaneLayout.height(passport)` returns a pane's height in scene units. The width is fixed at 2.1 units. The text is not measured: `PaneLayout.lines` wraps words greedily by character count at fixed font sizes and line heights, taken from the `PaneLayout.metrics` table. The Blender builder lays panes out with an identical `PANE` table. For each render it prints:

- how far each pane's height is from the layout's height;
- how many lines came out wider than their room;
- how many panes stand over a plinth that is not their own.

### Scene JSON

- Each element with a pane carries `pane` with its `side`, `w`, `h` and `passport`.
- The scene carries its style as `panes`.
- Passport sections are written with a lower-case `kind` discriminator (`text`, `facts`, `rated`, `rating`, `timeline`).
- Enum values are written in lower case, like the ports.

### Example

`SceneExample` now also writes `passports.json`: the same view, with passports on "Order Intake", "Order" and "Application Server". To render it:

```sh
blender -b -P out/scene/examples/archimate3d_blender.py -- \
  --view out/scene/examples/passports.json --render out/scene/examples/passports.png
```

## Changelog

### Added
- `Passport`, `PassportSection` (`Text`, `Facts`, `Rated`, `Rating`, `Timeline`), `Fact`, `Chip`, `Mark`, `Milestone` and `Tone`, each with a zio-json codec.
- `PaneStyle`, `PaneOrientation`, `PaneSide`, `PaneTint` and `ScenePane`.
- `SceneNode.passport`, `SceneElement.pane`, `Scene.panes` and `SceneLayout.Options.panes`, all with defaults.
- `PaneLayout`: pane sizing (`height`, `heightOf`, `lines`), the shared `metrics` table, and floor reservation and side choice for the layout.
- In the Blender builder: glass panes parented to their plinth, all five orientations, the camera-facing turn for upright and floating panes, the `--panes` option, and printed pane measures.
- `SceneLayoutSpec` cases:
  - panes stand on no other plinth, pane or connector, on the large graph too;
  - side choice around connectors;
  - `Floating` takes no floor;
  - a JSON round-trip of every section kind;
  - scenes without panes still read.
- `SceneBlenderSpec` cases: every orientation is built at the layout's height, and every line fits its room.
- `SceneContractSpec` holds the builder's `PANE` table equal to `PaneLayout.metrics`.
- DSL.md section "Passports: a glass pane beside the plinth".

### Changed
- `ElkPass` grows a leaf's ELK node by its pane's floor space on the pane's side, while the plinth keeps its size and its ports stay centred on its sides.
- `SceneLayout` now places elements from the plinth's rectangle (`plinthOf`), not the enlarged ELK node.
- The scene JSON documentation in DSL.md now covers `pane` and `panes`.

## Module Changes

- **`scene`**: all functional changes are here. New `Passport.scala` and `PaneLayout.scala`; `ElkPass`, `Scene`, `SceneGraph` and `SceneLayout` changed; the Blender builder (`archimate3d_blender.py`) is extended; the tests are extended.
- **`examples`**: `SceneExample` writes a second scene with passports.
- **`core`**, **`render`**, **`testkit`**: unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-process 0.8.0, os-lib 0.11.8, sourcecode 0.4.4
- PlantUML 1.2026.2
- ELK 0.9.1 and zio-json 0.7.3 (the `scene` module)
- Built with Mill on `fish.genius::mill-release` 0.5.0
- Blender 5.x (EEVEE headless) to render scenes and run `SceneBlenderSpec`

This release is source- and JSON-compatible for callers that build `Scene`, `SceneElement`, `SceneNode` and `SceneLayout.Options` with named or positional arguments, because every new field has a default. Code that pattern-matches these case classes positionally must add the new trailing field. A scene JSON file without `pane` or `panes` reads as it did in 1.5.x.
