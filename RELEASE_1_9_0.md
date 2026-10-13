# Release 1.9.0

**Release Date:** 2026-10-13

This release changes how the `scene` module's Blender builder (`archimate3d_blender.py`) films a flyover through a scene that has element passports. Before 1.9.0 a flight dimmed every passport pane along with the rest of the scene, even the pane of an element in the spotlight. It framed each step on the plinths alone, so a pane was only in shot by chance and could not be read at 1080p. Now the camera lights the panes of the elements it visits, keeps them in frame, and stops at each one long enough to read it.

The release also fixes a bug where a flat pane could be filmed upside down. Videos now render with EEVEE raytracing on, so a pane's glass refracts instead of showing as an opaque grey slab. The Scala API does not change: everything here is in the builder script and its tests.

## Highlights

- **Passport moments in a flyover:** after each step, the camera glides square on to the pane of each element whose pane it has not read yet. It holds there for 3.2 seconds, then flies on.
- **Panes are lit with their elements:** each pane has a soft area light that switches on and off with its element's spotlight. `--highlight` lights the panes of the elements it names as well.
- **Panes read the right way up:** flat and lectern panes no longer come out upside down.
- **Glass refracts in videos:** `--animation` renders with EEVEE raytracing on, at 64 samples (was 32).

## New Features

### Flyovers that read passports

When a flyover visits an element that has a passport, the step lights that element's pane, and the step's shot frames the panes of its two elements together with their plinths. After the step, each of its elements whose pane has not been read yet gets a passport moment:

- the camera glides square on to the pane, close enough that the pane fills about three quarters of the frame's height;
- it holds there for 3.2 seconds, then flies on.

The camera's position depends on the pane:

- A pane that turns to the camera (`faceCamera`) is read from the side the camera comes from, a little above it.
- A lectern or flat pane is read along its face, from at most 80° above.

Each pane is read once per flight. A flow without passports flies exactly as before, with the same number of frames.

```bash
blender -b -P archimate3d_blender.py -- --view scene.json \
  --flyover "Place an order" --blend flight.blend --animation flight.mp4
```

The builder measures the flight and prints a summary line, for example:

```
archimate3d: 3 passport moments; the panes of each step stay 0.08 inside the frame, and a pane read fills 0.74 of its height, 0 upside down
```

### Pane lights

Each pane gets its own soft area light (30 W). The light sits 1.4 units in front of the pane's face and follows the pane as it turns to the camera. Its beam is narrow, so it does not light the floor. The light itself is invisible to the camera and in reflections. `--highlight` now lights the panes of the elements it names and prints how many pane lights it created:

```bash
blender -b -P archimate3d_blender.py -- --view scene.json --highlight "Web Shop"
```

## Changelog

### Added
- Passport moments in `--flyover`: after each step the camera glides to each unread pane, holds for 3.2 s and flies on.
- A soft area light per pane, switched with its element's spotlight in a flyover and lit by `--highlight`.
- New builder measurements: the number of passport moments, how far inside the frame each step's panes stay, the smallest share of the frame's height a pane fills while read, and how many panes show upside down.
- `--highlight` prints how many pane lights it created.
- `--animation` prints its render settings (samples, and whether raytracing is on).
- `SceneBlenderSpec` cases:
  - a flyover reads every passport once, the right way up, with the default, flat and lectern panes;
  - the timeline grows by exactly the length of the moments;
  - the plain flight still has 63 frames at 6 fps;
  - `--highlight` lights the panes of the elements it names;
  - the video renders with 64 samples and raytracing on.

### Changed
- The shot of each step frames the panes of its two elements as well as their plinths.
- `--animation` renders with EEVEE raytracing on and 64 samples by default (was 32).
- Tuning values in the builder now have names: `READ_S`, `READ_MARGIN`, `READ_ELEVATION`, `READ_MAX_ELEVATION`, `READ_PUSH`, `PANE_GROW`, `PANE_LIGHT`, `PANE_SPREAD`, `PANE_LIGHT_PAD`, `PANE_LIGHT_DIST`, `VIDEO_SAMPLES`.
- DSL.md ("Scenes: ArchiMate in 3D") now describes passport moments, pane lights and the new render settings.

### Fixed
- A video rendered a pane's glass as an opaque grey slab, because EEVEE only refracts with raytracing on. Raytracing is now on.

## Module Changes

- **`scene`**: `resources/fish/genius/uml/scene/archimate3d_blender.py` (passport moments, pane lights, the upside-down fix, render settings) and `test/.../SceneBlenderSpec.scala` (new headless Blender cases).
- **Documentation**: DSL.md.
- No changes to `core`, `render`, `testkit` or `examples`.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-process 0.8.0, zio-json 0.7.3
- PlantUML 1.2026.2
- ELK 0.9.1
- os-lib 0.11.8, sourcecode 0.4.4
- Built with Mill using mill-release 0.5.0 and mill-scalafix 0.6.0
- Flyovers and animation are tested headless on Blender 5.0.1. Animation needs a Blender that runs EEVEE without a display (5.x). On an older Blender without the raytracing setting, the builder prints that raytracing is unavailable.
- The Scala API does not change. `Scene.scala` is untouched, so the layout's JSON format is the same as in 1.8.0.
