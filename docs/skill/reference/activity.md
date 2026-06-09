# activity — the activity-diagram DSL

Package `fish.genius.uml.dsl.activity`. Open with `activityDiagram { }` inside
`uml { }`; everything here requires `InActivity`. This wraps PlantUML's **Activity
Beta** syntax.

| Helper | Emits |
| --- | --- |
| `activityDiagram(body: (PUmlCtx, InActivity) ?=> Unit)(using PUmlCtx, InUml)` | opens the DSL |
| `title(value: String)` | `title <text>` |
| `start()` | `start` |
| `action(name: String)` | a single action step `:name;` |
| `stop()` | `stop` |
| `end()` | `end` |
| `note(side: String, body: String)` | a free-form note attached to the previous action |

> The DSL currently exposes the linear core (title, start/stop/end, actions,
> notes). For branching/looping/fork constructs not covered by a helper, drop to
> `statement(...)` / `statements(...)` (from `core`) to emit raw Activity-Beta
> lines inside the `activityDiagram` body.

## Worked example

```scala
block:
  uml:
    skinParam(
      "Activity",
      FontColor("black"),
      BorderColor("steelblue"),
      BorderThickness(2),
    )
    activityDiagram:
      title("Order checkout")
      start()
      action("Add items to basket")
      action("Click checkout")
      action("Confirm payment")
      action("Receive confirmation")
      stop()
```
