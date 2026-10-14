# Release 1.14.1

**Release Date:** 2026-10-15

This patch release fixes how the `diagram` module draws flat BPMN-style activity diagrams. In some diagrams, a message flow from another pool went to a node on the first row of a lane while the node's near side was already in use. The message then came in from above at the height of the lane headers, and its label sat on the header line.

The first row now gets extra vertical room (0.4 units) whenever a message from a pool targets one of its nodes. The incoming message runs through that gap, below the lane headers. Only diagrams with such a message change. All other layouts stay exactly as they were, and the API is unchanged.

## Highlights

- **Messages into the first row clear the lane headers** - A message flow from a pool to a first-row node no longer runs along the lane header line, and its label stays clear of the headers.

## Changelog

### Fixed
- `Activity.draw`: a message from a pool into a node on the first row whose near side is taken now enters below the lane headers, with its label beneath the header line instead of on it (APP-2227). The first row leaves a `MESSAGE_IN` margin above it only when such a message exists, and the message's horizontal leg runs through the middle of that gap.
- Added an `ActivitySpec` case that draws two lanes, a message start event and a message from the second pool. It checks that the dashed message line and its label stay below the lane header line.

## Module Changes

- **`diagram`** (`genius-uml-diagram`): `Activity.scala` changed how it lays out the first row and routes messages into it. Tests were added in `ActivitySpec.scala`.
- `core`, `render`, `testkit`, `scene` and `examples` are unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-process 0.8.0, zio-json 0.7.3
- os-lib 0.11.8, sourcecode 0.4.4
- PlantUML 1.2026.2
- Eclipse ELK 0.9.1
- Binary- and source-compatible with 1.14.0: no public API changed.
