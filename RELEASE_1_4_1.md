# Release 1.4.1

**Release Date:** 2026-10-09

This release makes element names readable in overviews of large ArchiMate scenes. The Blender scene builder (`archimate3d_blender.py`) gains a `--labels` option. In its default `auto` mode, when an element's placard name would render under 9 px tall, the builder also floats each name above its element: about 16 px tall at any render size or aspect ratio, on a light card, upright on screen and parented to the element so it moves with it.

The builder is now covered by tests that run it in Blender. `SceneBlenderSpec` runs it headless when `BLENDER_AVAILABLE=1` is set and is ignored otherwise, so `make validate` still needs no Blender. `SceneContractSpec` now also checks that every builder option documented in `DSL.md` exists in the script. The Scala API is unchanged.

## Highlights

- **Floating labels in large scenes**: names stay readable in overview renders, where placard text shrinks to a few pixels.
- **Labels that avoid each other**: working front to back, a floating name that would cover one already placed moves up, at most eight times. The builder prints how many names stayed covered and how many pairs still overlap.
- **Builder tested in Blender**: an opt-in spec runs the real builder headless and checks what it prints.

## New Features

### Floating labels (`--labels auto|placard|float`)

The builder now prints how tall a placard's name renders at the middle of the view. In `auto` mode, it floats the names when that height is under 9 px. `float` and `placard` force one mode or the other.

```sh
# default: floats names only when placards would be unreadable
blender -b -P archimate3d_blender.py -- --view view.json --render out.png --size 2400x1500

# always float names, or never
blender -b -P archimate3d_blender.py -- --view view.json --labels float
blender -b -P archimate3d_blender.py -- --view view.json --labels placard
```

The output reports the label decision. On the Meridian showcase (82 elements) at 2400x1500:

```
archimate3d: a placard's name renders about 3.2 px tall
archimate3d: 82 floating labels, 0 not freed, 0 covering pairs
```

The same view in portrait at 1200x1920 is too crowded for its render size, and the output says so (`28 not freed, 51 covering pairs`).

How the labels are placed:
- A leaf element's name floats above its sculpture. A container's name floats lower, over its front strip, so it sits below its children's names.
- A floating name copies the scene camera's rotation rather than tracking the camera, so names away from the centre of the frame do not roll and their cards do not shear.
- Junctions get no floating name. Labels and their cards cast no shadow and cannot be selected.

### Running the builder in Blender

```sh
BLENDER_AVAILABLE=1 make test
BLENDER_AVAILABLE=1 BLENDER=/opt/blender/blender make test
```

`SceneBlenderSpec` checks three cases:
- A small view keeps its placards, and its connectors follow a moved element (`--check`).
- A large view at 2400x1500 floats every name, with no name stuck and no pair overlapping.
- `--labels float` and `--labels placard` override the automatic choice.

## Changelog

### Added
- `--labels auto|placard|float` option on the Blender scene builder, with `auto` as the default.
- Floating, camera-aligned name labels on a light card, with a front-to-back pass that moves overlapping names up.
- The builder prints how tall a placard name renders and how many floating labels it placed, did not free, and left overlapping.
- `SceneBlenderSpec`: headless Blender tests of the builder, enabled with `BLENDER_AVAILABLE=1` (`BLENDER` sets the binary).
- `SceneContractSpec`: checks that every builder option `DSL.md` documents is one the script accepts, and that `--labels` defaults to `auto`.

### Changed
- The builder stores each element's height, container flag and form on its root object (`am_z`, `am_container`, `am_form`), which the label placement uses.
- `scene.test` passes the path of `DSL.md` to the tests as `SCENE_DSL_MD` through `forkEnv`.
- `DSL.md` ("Scenes: ArchiMate in 3D"), `CLAUDE.md` and the `Makefile` describe floating labels and how to run the Blender tests.

## Module Changes

- **`scene`**: the Blender builder resource gains floating labels and the `--labels` option. The tests add `SceneBlenderSpec` and extend `SceneContractSpec`. No Scala source changed.
- **`core`, `render`, `testkit`, `examples`**: unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, zio-process 0.8.0
- os-lib 0.11.8, sourcecode 0.4.4
- PlantUML 1.2026.2
- Eclipse ELK 0.9.1
- Blender builder tested headless on Blender 5.0.1 and 5.2.2 (1.4.0); the floating labels and `SceneBlenderSpec` on 5.0.1. Blender is needed only for `SceneBlenderSpec` and for rendering scenes.

Binary and source compatible with 1.4.0.
