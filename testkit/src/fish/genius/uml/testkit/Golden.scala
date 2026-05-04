package fish.genius.uml.testkit

import zio.test.{assertCompletes, assertTrue, TestResult}

import zio.*

/**
 * Golden-file matcher for ZIO Test.
 *
 * A "golden" test compares the rendered output of some code against a
 * checked-in fixture. When the fixture and the actual output disagree, the
 * assertion fails and ZIO Test renders a diff. Set the `ZIO_TEST_UPDATE_GOLDEN`
 * environment variable to regenerate the fixture from the current output
 * instead of failing — useful when the rendered shape has changed
 * intentionally.
 *
 * Convention: golden files live at
 * `<module>/test/resources/golden/<id>.<extension>` (default `puml`). Use
 * [[Golden.moduleResources]] to derive the path from the workspace root.
 */
object Golden:

  /**
   * Workspace root, derived from `MILL_WORKSPACE_ROOT` if set, otherwise
   * the JVM current working directory.
   */
  def workspaceRoot: os.Path =
    sys.env.get("MILL_WORKSPACE_ROOT").map(os.Path.apply).getOrElse(os.pwd)

  /** Resolve `<workspace>/<module>/test/resources` for golden file storage. */
  def moduleResources(module: String): os.Path =
    workspaceRoot / module / "test" / "resources"

  /** True iff the test runner has been asked to regenerate goldens. */
  def shouldUpdate: Boolean = sys.env.contains("ZIO_TEST_UPDATE_GOLDEN")

  /**
   * Compare `actual` to the contents of
   * `<resourcesDir>/golden/<id>.<extension>`.
   *
   *   - When the file is missing, the test fails with a hint to set
   *     `ZIO_TEST_UPDATE_GOLDEN=1` (or pass `update = true`).
   *   - When `update` is true, the file is created/overwritten with `actual`
   *     and the assertion passes vacuously.
   */
  def assertMatches(
    id: String,
    actual: String,
    resourcesDir: os.Path,
    extension: String = "puml",
    update: Boolean = shouldUpdate,
  ): ZIO[Any, java.io.IOException, TestResult] =
    val path = resourcesDir / "golden" / s"$id.$extension"
    if update then
      ZIO
        .attempt:
          os.makeDir.all(path / os.up)
          os.write.over(path, actual)
        .refineToOrDie[java.io.IOException]
        .as(assertCompletes)
    else
      ZIO
        .attempt:
          if !os.exists(path) then
            throw new java.io.FileNotFoundException(
              s"Golden file not found: $path (run with ZIO_TEST_UPDATE_GOLDEN=1 to create)"
            )
          os.read(path)
        .refineToOrDie[java.io.IOException]
        .map: expected =>
          assertTrue(actual == expected)
    end if

  end assertMatches

end Golden
