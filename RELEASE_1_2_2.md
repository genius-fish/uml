# Release 1.2.2

**Release Date:** 2026-05-04

A single-fix patch on top of `1.2.1`. `1.2.0` and `1.2.1` shipped with a
hardcoded `def publishVersion = "0.1.0-SNAPSHOT"` in `build.mill`, which
meant `make publish` always uploaded an artefact stamped `0.1.0-SNAPSHOT`
regardless of what tag the working tree was on. From `1.2.2` onward the
publish version is derived from `git describe`: tagged commits publish
the exact tag (with the leading `v` stripped), other commits append
`-SNAPSHOT`.

## Highlights

- **`publishVersion` reads the git tag.** No more `0.1.0-SNAPSHOT`
  collision in the snapshots repo. Verified via
  `./mill show core.publishVersion` returning `"1.2.1"` on the v1.2.1
  tag during development of this fix.

## Changelog

### Fixed

- `build.mill` now derives `publishVersion` via `Task.Input(computeGitVersion())`,
  ported from the genius-fish/sdk pattern. On a tagged commit it strips
  the leading `v` and publishes that tag; otherwise it appends
  `-SNAPSHOT`. Falls back to `0.0.0-SNAPSHOT` when no tags exist.

## Module Changes

Only `build.mill` changed. No source code in `core/`, `render/`,
`testkit/`, or `examples/` is affected — the bytecode of those modules is
identical to `1.2.1` apart from the version stamp.

## Compatibility

Built against (unchanged from `1.2.1`):

- Mill 1.1.5
- Scala 3.8.3
- ZIO 2.1.25
- ZIO Process 0.8.0
- os-lib 0.11.8
- sourcecode 0.4.4
- PlantUML 1.2026.2

Maven coordinates: `fish.genius:genius-uml-{core,render,testkit}:1.2.2`.

## Cleanup note

Anything you previously published to `repo.genius.fish/snapshots` under
`fish.genius:genius-uml-{core,render,testkit}:0.1.0-SNAPSHOT` is orphaned
and can be removed at the operator's discretion.
