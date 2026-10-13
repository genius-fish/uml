# Release 1.7.0

**Release Date:** 2026-10-13

Release 1.7.0 adds the passport's glass pane to the browser preview. In 1.6.0 the Blender builder learned to place an element's passport on a frosted glass pane beside its plinth. The three.js shapes (`SceneBuilder.threeJs`, `archimate3d.js`) could not show one, so a browser preview did not match what Blender rendered. They now build the pane with the same measures, line breaking, orientations and placement as `PaneLayout` and the Blender builder, and a new spec checks that the browser pane is exactly as tall as the layout made it.

A rebuilt canvas disposes its pane textures and takes them off the font-repaint list, and a `data-passport` attribute that is not valid JSON shows the element on its own and logs an error, rather than the sample passport. There are no breaking changes: the JavaScript API only gains members, and `AM3D.version` moves from `0.2.0` to `0.3.0`.

## Highlights

- **Glass panes in the three.js preview**: `AM3D.pane(passport, opts)` builds a passport on frosted glass with a metal rim and a stem or tether. It places the pane beside a plinth exactly where the Blender builder would.
- **One layout table for all three**: the three.js `PANE` table matches `PaneLayout.metrics` and the builder's `PANE`, and `AM3D.paneHeight(passport)` matches `PaneLayout.height`. Both are checked in tests.
- **Declarative pane canvases**: `<canvas data-am="pane-<element>">` shows an element with its pane. The passport comes from `data-passport` (JSON) or is a built-in sample.

## New Features

### Passport panes in the three.js shapes

`archimate3d.js` now lays out a passport the same way the scene layout does: eyebrow, title, subtitle, sections and footer, with the same greedy character-based line breaking as `PaneLayout.lines`. It handles every section kind:

- `text`
- `facts`
- `rated` (a chip with an optional note)
- `rating` (letter and level marks)
- `timeline` (milestones, a today marker and year ticks)

The face is painted on a canvas the same way the placards are, and it is repainted once the page's font has loaded.

```js
// A pane on its own, beside a plinth at the origin
const p = AM3D.pane(passport, {
  orientation: 'upright',  // upright | lectern | flat | floating | auto
  side: 'right',           // right | left | behind | front
  layer: 'application',
  tint: 'clear',           // clear | layer
  azimuth: 30,             // camera azimuth in degrees; upright and floating panes turn to face it
  anchored: true,          // stem (upright, lectern) or tether (floating)
  elevation: 24            // used by 'auto': above PANE.autoFlat the pane lies flat
});
scene.add(p);

AM3D.paneHeight(passport); // equals PaneLayout.height(passport)
AM3D.PANE;                 // the shared layout table
```

To show an element with its pane, declare the canvas in HTML:

```html
<canvas data-am="pane-application-component"></canvas>

<canvas data-am="pane-application-component"
        data-passport='{"eyebrow":"Application component","title":"Customer Hub","sections":[]}'></canvas>
```

The first canvas shows a built-in sample passport. The second shows the passport in `data-passport`, in the JSON format the scene writes.

## Changelog

### Added
- `AM3D.pane(passport, opts)`, which builds a passport on a glass pane in the upright, lectern, flat or floating orientation, or picks one with `auto`, and places it as the Blender builder does.
- `AM3D.paneHeight(passport)` and `AM3D.PANE`, which expose the pane layout and its measures.
- The `pane-<element>` canvas id, with an optional `data-passport` JSON attribute.
- `SceneThreeSpec`, which checks three things:
  - The three.js `PANE` table equals `PaneLayout.metrics`.
  - Every tone the layout writes has a colour.
  - `paneHeight` equals `PaneLayout.height` for wrapping titles, facts, notes and summaries, an empty timeline and accented text. This runs the shapes in node and is ignored when node is not available; set `NODE` to choose the binary.
- A DSL.md section, "Scenes: ArchiMate in 3D", describing the pane in the three.js shapes.

### Changed
- `AM3D.version` moves from `0.2.0` to `0.3.0`.
- Pane faces repaint along with the placard labels when the page's font loads.

### Fixed
- A canvas rebuilt for new names now disposes its pane textures and takes them off the repaint list. Before, they were repainted for the life of the page.
- A `data-passport` that is not valid JSON now shows the element alone and logs `archimate3d: data-passport is not JSON`. Before, it showed the sample passport as though it were the data.

## Module Changes

- **`scene`**: `resources/fish/genius/uml/scene/archimate3d.js` (pane layout, painting, glass geometry and placement, the `pane-` canvas, rebuild cleanup) and the new test `SceneThreeSpec`.
- **Docs**: `DSL.md`.

The `core`, `render`, `testkit` and `examples` modules are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-process 0.8.0, zio-json 0.7.3
- os-lib 0.11.8, sourcecode 0.4.4
- PlantUML 1.2026.2
- ELK 0.9.1
- Built with Mill and mill-release 0.5.0

The release is source- and binary-compatible with 1.6.0. Running the optional height test needs Node.js on the `PATH`, or set `NODE`.
