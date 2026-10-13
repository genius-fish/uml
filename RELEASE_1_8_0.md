# Release 1.8.0

**Release Date:** 2026-10-13

This release makes the `scene` module's Blender stills easier to read and sharper. A passport pane placed on the floor (lectern or flat) now gets frosted glass tinted with its layer. Before, it used clear glass, and its text faded into the brightly lit floor beneath it. The three.js preview shapes get the same frosted glass, so the browser preview and the Blender render agree.

Stills now render at 3840×2400 with 256 samples by default (previously 1920×1200 with 96), and Cycles uses a GPU whenever this Blender can use one. Floating names and the threshold for floating them now scale with the render's width, so a larger render shows its names at the same proportion instead of shrinking them. The DSL, AST, emitter and renderer are unchanged.

## Highlights

- **Frosted floor panes** - Lectern and flat panes always use frosted glass tinted with their layer, whatever `PaneStyle.tint` says, in both Blender and three.js.
- **Sharper stills by default** - Stills default to 3840×2400 and 256 denoised Cycles samples. Videos stay at 1920×1080.
- **GPU rendering** - Cycles picks OptiX, CUDA, HIP, oneAPI or Metal when one is available and falls back to the CPU otherwise. The builder prints which device it used.
- **Labels that scale with the render** - Floating names and the legibility threshold are defined at 1920 px wide and scale with the render's width.

## New Features

### Frosted glass for panes on the floor

A lectern or flat pane lies over the bright floor under the key light. With clear glass, the passport's text faded into the floor. These panes now use frosted glass: transmission 0.3, roughness 0.55, and a layer tint lightened 25% toward white. Upright and floating panes keep the glass their style asks for. The builder prints the glass it chose:

```
archimate3d: pane glass frosted
```

In three.js (`AM3D.pane`), lectern and flat panes get a layer-tinted material with roughness 0.55 and opacity 0.72.

### Render device selection

`stage` now chooses the Cycles device itself. It tries OptiX, CUDA, HIP, oneAPI and Metal in that order and enables only the devices of the first kind it finds. If none is found, it resets the compute device type and renders on the CPU. A GPU kind that this Blender build lacks, or whose driver fails to load, is skipped. The builder reports the device with the size and samples:

```
archimate3d: rendering on the GPU at 3840x2400, 256 samples
```

### Higher-resolution defaults for stills

```sh
blender -b -P archimate3d_blender.py -- scene.json --render out.png                       # 3840x2400, 256 samples
blender -b -P archimate3d_blender.py -- scene.json --render out.png --size 1920x1200 --samples 96   # the 1.7.0 defaults
```

`--size` and `--samples` still override the defaults. `--animation` still defaults to 1920×1080.

### Labels that scale with the render width

`LABEL_PX` (16 px) and `LEGIBLE_PX` (9 px) are now defined for a render 1920 px wide and scaled by `resolution_x / 1920`. A floating name therefore takes up the same share of a 3840-wide render as of a 1920-wide one. `--labels auto` also makes the same float-or-placard decision at both sizes. The screen boxes that the overlap pass uses are scaled the same way.

## Changelog

### Added
- `render_device()` in the Blender builder: Cycles renders on a GPU (OptiX, CUDA, HIP, oneAPI, Metal) when one is available, otherwise on the CPU.
- The builder prints the render device, size and samples, and the pane glass it used.
- `SceneBlenderSpec`: tests for the glass per pane orientation (frosted on lectern and flat panes), the default and overridden render size and samples, and that a large view floats the same names at 1920×1200 and 3840×2400 while its placard measures twice as many pixels.

### Changed
- Lectern and flat panes always use frosted, layer-tinted glass in the Blender builder and the three.js shapes. Upright and floating panes still follow `PaneStyle.tint`.
- The default still resolution is now 3840×2400 (was 1920×1200), and the default samples are 256 (were 96).
- Floating label height and the `--labels auto` legibility threshold now scale with the render's width, based on 1920 px.
- DSL.md now describes the render defaults, the device choice, the width-relative label sizes and the frosted floor panes.

### Fixed
- Passport text on lectern and flat panes no longer fades into the floor in Blender renders.
- Floating names no longer shrink, relative to the frame, in renders wider than 1920 px.
- The glass material cache name now records whether the glass is layer-tinted or frosted, so a cached material of one kind is no longer reused for the other.

## Module Changes

- **`scene`**: the Blender builder (`archimate3d_blender.py`), the three.js shapes (`archimate3d.js`) and `SceneBlenderSpec`. `SceneBlenderSpec` runs only with `BLENDER_AVAILABLE=1`.
- **`core`**, **`render`**, **`testkit`**, **`examples`**: unchanged.

## Compatibility

- Scala 3.8.3, built with Mill
- ZIO 2.1.25, zio-process 0.8.0, zio-json 0.7.3
- os-lib 0.11.8, sourcecode 0.4.4
- PlantUML 1.2026.2
- Eclipse ELK 0.9.1
- Blender 5.x with EEVEE headless for the scene builder. The GPU render path needs a Blender build and driver that support OptiX, CUDA, HIP, oneAPI or Metal.
- The Scala API and the JSON contract between `Scene.scala` and the builder are unchanged, so no source changes are needed. Stills are larger and slower on the CPU by default. To keep the 1.7.0 output, pass `--size 1920x1200 --samples 96`.
