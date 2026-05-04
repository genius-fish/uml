# Release 1.2.1

**Release Date:** 2026-05-04

A tooling-only patch on top of `1.2.0`. The published artifacts
(`genius-uml-core`, `genius-uml-render`, `genius-uml-testkit`) are
byte-identical to `1.2.0` apart from the version stamp — there are no
changes to `core/`, `render/`, `testkit/`, or `examples/`.

## Highlights

- **`make publish`** — new Makefile target that pushes every
  `PublishModule` to the Genius Fish Maven repo (releases + snapshots),
  failing fast if `REPO_USERNAME` / `REPO_TOKEN` aren't set.
- **`.release-state.json` is now gitignored** — `/gf-standards:release-prepare`
  no longer leaves stray state in `git status`.
- **`.claude/settings.json`** — registers the Genius Fish Claude Code
  marketplace at the project level so `/plugin marketplace add genius-fish`
  works from this repo without a global setting.

## Changelog

### Added

- `make publish` target backed by `mill mill.javalib.MavenPublishModule/`
  with `--releaseUri https://repo.genius.fish/releases` and
  `--snapshotUri https://repo.genius.fish/snapshots`. Pre-flight checks
  that both `REPO_USERNAME` and `REPO_TOKEN` are set in the environment.
- `.claude/settings.json` declaring the `genius-fish/claude-marketplace`
  source as a project-scoped extra known marketplace.

### Changed

- `.gitignore` ignores `.release-state.json`, the local-only state file
  used by `/gf-standards:release-prepare` and `release-finalize`.

## Module Changes

No source code changed. Only repo tooling (`.claude/settings.json`,
`.gitignore`, `Makefile`) was touched.

## Compatibility

Built against (unchanged from `1.2.0`):

- Mill 1.1.5
- Scala 3.8.3
- ZIO 2.1.25
- ZIO Process 0.8.0
- os-lib 0.11.8
- sourcecode 0.4.4
- PlantUML 1.2026.2

Maven coordinates: `fish.genius:genius-uml-{core,render,testkit}:1.2.1`.
