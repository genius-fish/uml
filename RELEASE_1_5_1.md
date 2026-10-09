# Release 1.5.1

**Release Date:** 2026-10-09

This patch release fixes two problems in the `scene` module's Blender flyover (`--flyover`, `--animation`). The first is a flow step from an element to itself. Before, its pulse ran straight up from the plinth and back down, a vertical stroke that did not read as a step to itself. Now the pulse runs once round an upright ring that stands on the element's plinth and turns to face the camera from whatever azimuth the shot uses.

The second is camera distance. The camera no longer comes too close on a step to itself or on two elements that stand near each other. Every step's shot now holds at least five units of floor, centred between the two plinths. Before this, a leaf's plinth could fill up to 47% of the frame's width; it now takes about 24%. The builder prints both new measures, and `SceneBlenderSpec` asserts them.

## Highlights

- **Steps to themselves are visible.** A step whose source and target are the same element runs once round a ring of radius 0.75 over its plinth, facing the camera.
- **No shot comes too close.** A step's shot spans at least `STEP_SPAN = 5.0` units of floor, so a single leaf or a near pair is framed with room around it.
- **New flight measurements.** The builder now prints how close the camera comes (the largest share of the frame's width a leaf's plinth takes) and how wide a step to itself opens its ring.

## Changelog

### Added
- A ring route for a step from an element to itself (`loop_route`). It is turned to the step's camera azimuth, so it faces the viewer from any side.
- `shot_corners`: a step's framing now holds both plinths and a square of floor `STEP_SPAN` wide, centred between them.
- Two new builder output lines:
  - `at its closest the camera shows a leaf's plinth at <share> of the frame's width`
  - `a step to itself opens its ring <share> of the frame's width`, printed only when the flow has such a step
- `SceneBlenderSpec` cases:
  - A step to itself opens its ring wider than 5% of the frame, both from the default azimuth and from `--azimuth 90`, and stays inside the frame.
  - No shot comes too close, either on a step to itself or on a hand-written pair of leaves 2.4 units apart. The leaf share must stay below 0.26.
  - The existing flyover case now also checks the leaf share.

### Changed
- The frame margin now also counts each step's route, so the margin line reads `every step's plinths and route stay … inside the frame`.
- Each flight pose now records the azimuth it was framed from, and that azimuth orients the step's ring.
- The arc route for steps with no connector was moved into its own `arc_route` helper. Its shape has not changed.
- `DSL.md` ("Scenes: ArchiMate in 3D") describes the ring, the minimum floor span per shot and the new measurements.

### Fixed
- A step from an element to itself drew a vertical stroke up from its plinth and back down, which did not read as a step to itself.
- The camera came too close on a step to itself (a leaf's plinth took up to 0.47 of the frame's width) and on a near pair (up to 0.29). Both now take about 0.24.

## Module Changes

- **`scene`**: `archimate3d_blender.py` (Blender builder resource) and `SceneBlenderSpec`. The JSON contract between `Scene.scala` and the builder is unchanged.
- **Documentation**: `DSL.md`.
- `core`, `render`, `testkit` and `examples` are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, zio-process 0.8.0
- Eclipse ELK 0.9.1
- PlantUML 1.2026.2
- os-lib 0.11.8, sourcecode 0.4.4
- `SceneBlenderSpec` needs a Blender that runs EEVEE headless (5.x), enabled with `BLENDER_AVAILABLE=1`.
- No API changes. This is a drop-in upgrade from 1.5.0.
