# Release 1.5.0

**Release Date:** 2026-10-09

genius-uml 1.5.0 lets an architect point at parts of a 3D ArchiMate scene while presenting it. The Blender builder has a new `--highlight` option that lights the named elements and connectors and dims everything else. A scene graph can now also carry **flows**: ordered walks through the graph, such as the interactions of a sequence diagram. The builder's new `--flyover` option turns a flow into a camera flight, which can be rendered to an H.264 video or to a single still frame.

The flight follows each connector along the route it was actually drawn on, including with `--straight`. The builder also measures the flight it built and prints the results: how many steps ran along a connector, how far inside the frame the plinths stay, how much room is left below the caption, and the largest gap between a route and its connector. The headless Blender specs check those printed values.

## Highlights

- **Highlight** - `--highlight` puts a spot on each named element and makes each named connector glow amber, while the rest of the scene dims.
- **Flows in the scene graph** - `SceneFlow` and `SceneStep` describe an ordered walk through the graph. The layout carries flows into the `Scene` as `FlowRoute` and `FlowLeg`.
- **Flyover** - `--flyover` writes a camera flight through a flow into the `.blend` timeline. `--animation` renders it to MP4 and `--frame N` renders one frame as a still.
- **Measured flights** - the builder prints what it measured in the flight it built, and `SceneBlenderSpec` asserts those values.

## New Features

### Flows in a scene graph

`SceneGraph` takes an optional list of flows. Each `SceneStep` goes from one node to another and has a label saying what passes between them.

```scala
val graph = SceneGraph(
  "Order handling",
  nodes,
  edges,
  List(
    SceneFlow(
      SceneId("place-an-order"),
      "Place an order",
      List(
        SceneStep(SceneId("Customer"), SceneId("Handle Order"), "Places an order"),
        SceneStep(SceneId("Handle Order"), SceneId("Order Intake"), "Submits it"),
      ),
    )
  ),
)
```

`SceneLayout.layout` validates the steps. A step that names an unknown node is refused with `SceneError.InvalidGraph`, which contains a `GraphProblem.UnknownStepEnd(flow, endpoint)`. Flow ids also count in the blank-id and duplicate-id checks.

For each step, the layout records the connector that joins its two ends in `FlowLeg.relationship`. It first looks for a connector from source to target, then for one in the other direction. If no connector joins the two ends, the field is `None`.

In JSON, a scene now has a `flows` array. For each flow it holds the key, the name and the `steps`, and each step has `source`, `target`, `label` and an optional `relationship`. Scene JSON without `flows` still reads, as a scene with no flows.

### Highlight

```bash
blender -b -P archimate3d_blender.py -- --view scene.json \
  --highlight "Order System,Customer>Handle Order" --render lit.png
```

`--highlight` takes a comma-separated list. Each item can be:

- an element key;
- an element name, in any case;
- `source>target`, using names or keys, which matches the connectors between those two elements in either direction.

Each element or connector is lit only once, however often it is named. The builder prints how many elements and connectors it lit, how many spots it hung and how far it turned the ambient light down. It also prints each item that matched nothing.

### Flyover and animation

```bash
blender -b -P archimate3d_blender.py -- --view scene.json \
  --flyover "Place an order" --blend flight.blend --animation flight.mp4
```

`--flyover` selects a flow by key or by name; without a value it uses the first flow. The flight runs like this:

1. It opens on the overview in full light.
2. For each step, the camera glides to the step's two elements and views them from the side. Both elements are lit while the rest dims.
3. A pulse runs from source to target and leaves an amber trail. The trail follows the connector's drawn route, or arcs above the scene when no connector joins the two elements.
4. A caption bar at the top of the frame shows the step's number and label. Labels longer than 60 characters are cut short.
5. A finished step's trail thins and stops glowing, so the flow so far stays visible.
6. At the end the camera returns to the overview and the full light comes back.

Each step takes about 3.3 seconds.

- **`--animation out.mp4`** renders the timeline to H.264 with EEVEE and Blender's own encoder, so no ffmpeg is needed. The defaults are 1920×1080 and 30 fps; `--size` and `--fps` change them.
- **`--frame N --render still.png`** renders one frame of the flight with Cycles, for example as a thumbnail.

If the builder cannot find the named flow, it says so and `--animation` writes nothing.

## Changelog

### Added
- `SceneFlow` and `SceneStep` in `SceneGraph`, through a new `flows` parameter that defaults to `Nil`.
- `FlowRoute` and `FlowLeg` in `Scene`, through a new `flows` field that defaults to `Nil`, with zio-json codecs.
- `GraphProblem.UnknownStepEnd(flow, endpoint)`.
- Builder options `--highlight`, `--flyover`, `--animation`, `--fps` and `--frame`. `--size` defaults to 1920×1080 for an animation.
- The builder prints measurements of the flight it built: steps along a connector, plinth margins inside the frame and below the caption, and the largest gap between a route and its connector.
- The flow "Place an order" in `SceneExample`, with example commands for highlight and flyover.
- New sections in DSL.md: "Pointing: highlight and flyover", and the flows' JSON shape.

### Changed
- `SceneGraph.problems` now includes flow ids in the blank-id and duplicate-id checks, and reports steps that point at unknown nodes.
- In the README, the description of the `scene` module now mentions highlights and flyovers.
- `SceneBlenderSpec` removes its working directories afterwards.

### Fixed
- `--size` is now validated before anything is built. An odd value used to fail inside Blender after the scene was built.

## Module Changes

- **`scene`**: new flow model (`SceneFlow`, `SceneStep`, `FlowRoute`, `FlowLeg`), flow validation and connector lookup in `SceneLayout`, and the highlight, flyover, animation and measurement code in `archimate3d_blender.py`. The specs now cover flows through the layout, refusals, JSON with and without flows, and the contract over the new fields. `SceneBlenderSpec` runs highlight and flyover headless, checks the MP4 it writes and asserts the printed measurements.
- **`examples`**: `SceneExample` gains a flow and the commands to highlight it and fly through it.
- **`core`**, **`render`**, **`testkit`**: unchanged.

## Compatibility

- Scala 3.8.3
- ZIO 2.1.25, zio-json 0.7.3, zio-process 0.8.0
- ELK 0.9.1, PlantUML 1.2026.2, os-lib 0.11.8, sourcecode 0.4.4
- The highlight, flyover and animation features were tested headless on Blender 5.0.1. The animation needs a Blender that can run EEVEE without a display, as 5.x can.
- **Source compatible:** the new `flows` parameters on `SceneGraph` and `Scene` default to `Nil`, so existing code compiles unchanged.
- **Not binary compatible:** the constructors, `apply` and `copy` of `SceneGraph` and `Scene` have changed, so code that depends on them must be recompiled.
- **New enum case:** an exhaustive `match` on `GraphProblem` needs a branch for the new `UnknownStepEnd` case.
- **Builder contract:** a 1.4.x builder ignores the new `flows` field and still builds the scene; highlight and flyover need the 1.5.0 builder that ships in this jar.
