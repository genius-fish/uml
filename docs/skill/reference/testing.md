# testing — testkit and the repo's habits

Package `fish.genius.uml.testkit` (the `genius-uml-testkit` module), on ZIO Test.

## Golden files

```scala
Golden.assertMatches(
  id: String,
  actual: String,
  resourcesDir: os.Path,
  extension: String = "txt",
  update: Boolean = Golden.shouldUpdate,
): ZIO[Any, IOException, TestResult]
```

Compares `actual` with `<resourcesDir>/golden/<id>.<extension>`.
`ZIO_TEST_UPDATE_GOLDEN=1` (or `update = true`) writes the fixture instead. A missing
fixture fails with a hint. `Golden.moduleResources("diagram")` is
`<workspace>/diagram/test/resources` (`MILL_WORKSPACE_ROOT`, else `os.pwd`).

## Testing drawings

- Assert on the `Drawing` itself: `drawing.scene.elements` (boxes with `x`, `y`, `w`,
  `d`), `drawing.scene.relationships` (ports, bends, labels), `drawing.overflow`.
  Geometry checks (no two boxes overlap, every edge drawn, labels off boxes) catch
  layout regressions that a golden file only shows as a diff.
- Assert on the writers' text for what each must carry: `Tikz.picture` defines its
  colours and escapes `&`; `Svg.document` escapes entities; `Png.image` starts with
  the PNG signature.
- `DIAGRAM_OUT=dir` with `./mill diagram.test` writes the sample as `.tex`, `.svg`,
  `.png` to look at.
- The 3D builder runs headless in `SceneBlenderSpec` only with `BLENDER_AVAILABLE=1`.
