# Release 1.9.1

**Release Date:** 2026-10-14

This is a patch release of `genius-uml`'s `scene` module. It fixes how the Blender builder measures passport reads during a flyover. Until now the builder measured only how much of the frame's height a read pane filled, and it reported the smallest value across all moments. A wide, low pane, such as a passport carrying only its name, is framed by its width, so it reported a share near 0.35 even though every pane read well. The builder now measures along the binding side, taking the larger of the width and height shares, and that comes to about 0.8 for every correctly framed pane.

The Scala API, the AST, the emitted PlantUML and the scene JSON contract are unchanged. One line of the builder's console output has changed, so a script that parses it needs updating (see Changed).

## Highlights

- **Correct read share for wide passports**: a flyover's "read fills" measure now uses whichever side of the frame the pane binds on, so short passports no longer show up as false low readings.
- **Tighter flyover test**: `SceneBlenderSpec` now covers a passport with nothing but its name. It requires the read share to fall between 0.7 and 0.9, so a pane cropped by the frame no longer passes.
- **Render timing documented**: DSL.md gives a measured render cost for flyover videos.

## Changelog

### Added
- `SceneBlenderSpec` has a new flyover case with a short passport (only an element name and a footer). This produces a wide, low pane that is framed by its width.
- DSL.md gives render timings: about 0.6 s per frame at 1920×1080 on an RTX 3090, so a one-minute flight (1800 frames) takes about twenty minutes, and much longer on a CPU.
- DSL.md lists Blender 5.2.2 as tested, alongside 5.0.1.

### Changed
- In `archimate3d_blender.py`, `measure_flight_panes` now projects the pane's corners and takes the larger of the horizontal and vertical extents as the pane's share of the frame.
- The builder's flyover output line changed from `a pane read fills <x> of its height` to `a pane read fills <x> of the frame where it binds`. Anything that parses this line must use the new wording; `SceneBlenderSpec`'s regex has been updated.
- The flyover test's readability check now requires a share above 0.7 and below 0.9. Previously it only required more than 0.7.
- DSL.md, under "Scenes: ArchiMate in 3D":
  - It explains that a read pane fills the frame with a margin of a tenth on each side, along the binding side.
  - It says the reported share should be about 0.8, and that a much lower value means a read did not frame its pane.
  - It mentions the upside-down count that the builder prints.

### Fixed
- A flyover with a short passport no longer reports a misleadingly low read share (about 0.35) when every pane was framed correctly.

## Module Changes

- **`scene`**: the Blender builder resource `archimate3d_blender.py` and the test `SceneBlenderSpec.scala`.
- **Documentation**: `DSL.md`.
- `core`, `render`, `testkit` and `examples` are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, zio-process 0.8.0
- os-lib 0.11.8, sourcecode 0.4.4
- PlantUML 1.2026.2
- Eclipse ELK 0.9.1
- Blender 5.x for the scene builder (tested headless on 5.0.1 and 5.2.2). Animation needs a Blender that can run EEVEE without a display.
- This release is a drop-in replacement for 1.9.0. The only incompatibility is the changed builder output line described under Changed.
