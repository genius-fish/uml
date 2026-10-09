# Release 1.4.0

**Release Date:** 2026-10-09

This release adds `genius-uml-scene`, a new module that turns an ArchiMate graph into a 3D scene. It lays the graph out with ELK Layered, writes the result as JSON through zio-json, and ships a Blender builder and a three.js preview in the same jar. A scene and the code that builds it therefore always come from the same release. Each ArchiMate layer becomes a band from back to front: Motivation at the back, then Strategy, Business, Application, Technology and Physical, with Implementation at the front. Every element stands on its own plinth, and connectors are routed orthogonally between ports on the plinth sides.

The `core`, `render` and `testkit` modules are unchanged, and `core` still has no runtime dependencies. The scene module only reuses `core`'s `ShapeType` and `RelationshipType`.

## Highlights

- **ArchiMate in 3D**: `SceneLayout.layout` turns a `SceneGraph` into a `Scene` with coordinates, footprints, heights, ports and connector bends for every element.
- **Blender and three.js builders included**: `SceneBuilder.blenderScript` and `SceneBuilder.threeJs` return the builder scripts from the jar. You can run the Blender script headless to produce a `.blend` file or a render.
- **Errors are returned, never thrown**: invalid graphs come back as `SceneError.InvalidGraph` with every problem listed at once. ELK failures come back as `SceneError.LayoutFailed` with their cause.
- **Repeatable layout**: a fixed seed and ELK's model order give the same scene for the same graph. The exception is described under the layout section below.

## New Features

### Scene module (`genius-uml-scene`)

You describe a graph of ArchiMate elements nested by containment, plus the relationships between them, and lay it out:

```scala
import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*
import fish.genius.uml.scene.*
import zio.json.*

val graph = SceneGraph(
  "Order handling",
  List(
    SceneNode(SceneId("customer"), NodeKind.Element(BusinessActor), "Customer"),
    SceneNode(SceneId("process"), NodeKind.Element(BusinessProcess), "Handle Order"),
    SceneNode(SceneId("system"), NodeKind.Element(ApplicationComponent), "Order System",
      children = List(SceneNode(SceneId("api"), NodeKind.Element(ApplicationComponent), "Order API"))),
  ),
  List(SceneEdge(SceneId("assigned"), SceneId("customer"), SceneId("process"), Assignment)),
)

val json: Either[SceneError, String] = SceneLayout.layout(graph).map(_.toJsonPretty)
```

Then build the scene in Blender:

```bash
blender -b -P archimate3d_blender.py -- --view scene.json --blend scene.blend --render scene.png
```

#### The graph

- `SceneNode(id, kind, label, children)` takes one of four kinds: `NodeKind.Element(shapeType)`, `Grouping`, `AndJunction` or `OrJunction`. Children stand on their container's plinth.
- `SceneEdge(id, source, target, relationship)` covers all fifteen ArchiMate relationship types. A connector from a node to one of its own ancestors is left out, because it cannot be drawn on the plinth the node stands on.
- `SceneId` is an opaque type. `SceneId.validated` rejects blank ids from untrusted input.
- `SceneGraph.problems` reports every problem at once: blank ids, duplicate ids, and edges that point to unknown nodes.

#### The layout

- The layout uses ELK Layered from top to bottom, which reads as back to front in the scene. Each ArchiMate layer is its own ELK partition.
- If a row is wider than `Options.maxPerRow` (by default about the square root of the graph's size), its leaves are folded into extra rows.
- **Port sides:** each connector leaves and arrives through the middle of one plinth side. The side depends on where the two ends are: south to north when one is behind the other, east to west when they sit side by side. Adding ports moves the plinths a little, so the sides are chosen again from the new positions until they stop changing.
- **Routing:** ELK sometimes leaves a connector between two containers unfinished. A small orthogonal router then takes it around the plinths. If there is no way around, the connector gets a plain orthogonal route and `clear = false`.
- **Options:** `nodeSpacing`, `layerSpacing`, `containerPadding`, `containerHeader` and `maxPerRow` can be set through `SceneLayout.Options`.
- **When the layout can differ:** ELK 0.9.1 fails on model order in some nested graphs, mostly larger ones. The layout then runs again without model order. The result is still valid and follows every rule above, but two runs of such a graph may differ.

#### The scene JSON

The `Scene` JSON has these fields:

- **Each element:** `key`, `type` (the builder's shape name), `name`, the centre `x`/`y`, the footprint `w`/`d`, the base height `z`, `parent` and `container`.
- **Each relationship:** `key`, `type`, `sourcePort` and `targetPort` (`north`, `south`, `east` or `west`; north is away from the viewer), `bends` and `clear`.

A contract spec checks that the JSON and the Blender builder agree on every shape name, connector name and field.

#### The notation in Blender

- **Elements:** every element is a plinth with a sculpture of its icon on top and its name on a placard tilted towards the viewer. The plinth's form shows the aspect (square for structure, rounded for behaviour, chamfered for motivation) and its colour shows the layer.
- **Groupings:** a grouping is a glass tray with a dashed rim and its name on a tab.
- **Junctions:** a junction is built at its own size (1.24 by 1.24).
- **Moving things:** each element is one selectable object, and an element standing on a container is parented to it. Connectors are curves hooked to their ports, so they follow when you move an element.
- **Builder flags:** `--check` moves an element and reports how far the connector ends are from their ports. `--straight` ignores the layout's routes.
- **Tested on:** Blender 5.0.1 and 5.2.2, headless.

### Scene example

`fish.genius.uml.examples.SceneExample` lays out a small "Order handling" view. It writes `scene.json` and the Blender builder to `out/scene/examples/`:

```bash
./mill examples.runMain fish.genius.uml.examples.SceneExample
blender -b -P out/scene/examples/archimate3d_blender.py -- \
  --view out/scene/examples/scene.json --blend out/scene/examples/scene.blend \
  --render out/scene/examples/scene.png
```

## Changelog

### Added
- New `scene` module (`genius-uml-scene`) with `SceneGraph`, `SceneLayout`, `Scene` (JSON) and `SceneBuilder`.
- Bundled resources `archimate3d_blender.py` (Blender bpy builder) and `archimate3d.js` (three.js preview).
- `SceneNames`, which maps every `ShapeType`, `RelationshipType` and `NodeKind` to its builder name. The matches are exhaustive, so a new ArchiMate type does not compile until it has a shape.
- `SceneRelationship.clear` marks a connector whose route may cross a plinth.
- `BuilderError` (`Missing`, `Unreadable`) for loading the builder resources.
- `SceneExample` in `examples`.
- Specs for the scene module:
  - **SceneLayoutSpec:** layout rules on graphs with junctions and on generated graphs of more than 200 nodes, plus layout stability.
  - **SceneContractSpec:** the JSON and the Blender builder agree on names and fields.
  - **SceneBuilderSpec:** the builder resources load.
- DSL.md section "Scenes: ArchiMate in 3D".

### Changed
- `examples` now depends on `core`, `render` and `scene`.
- README and CLAUDE.md describe the new module.
- Generated review reports are no longer tracked: `reviews/` is git-ignored and the old 2026-06-09 HTML reports were removed from the repository.
- Repository tooling: the default Claude Code agent is now `gf-developer`.

## Module Changes

- **scene**: new module (`genius-uml-scene`), depending on `core`, ELK and zio-json.
- **examples**: adds `SceneExample` and depends on `scene`.
- **core**, **render**, **testkit**: unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-process 0.8.0, os-lib 0.11.8, sourcecode 0.4.4
- PlantUML 1.2026.2
- New in `scene`: Eclipse ELK 0.9.1 (`core`, `graph`, `alg.common`, `alg.layered`) and zio-json 0.7.3. The module pins ZIO 2.1.25 explicitly so that zio-json cannot pull in an older ZIO.
- The Blender builder is tested headless on Blender 5.0.1 and 5.2.2.
- No breaking changes to existing modules.
