# sequence — the sequence-diagram DSL

Package `fish.genius.uml.dsl.sequence`. Open with `sequenceDiagram { }` inside
`uml { }`; everything here requires `InSequence`.

## Entry point & options

| Helper | Emits |
| --- | --- |
| `sequenceDiagram(body: (PUmlCtx, InSequence) ?=> Unit)(using PUmlCtx, InUml)` | opens the DSL |
| `autonumber()` | `autonumber` — number each subsequent message |
| `teozRenderingEngine()` | switch on the Teoz engine (cleaner group layout) |

## Participants

Each returns the participant's `Alias`; all take `(title: String, color:
Option[String] = None)`:

`participant` · `actor` · `boundary` · `control` · `entity` · `database` ·
`collections` · `queue`. Generic: `participantOf(participantType: String, title,
color=None)`.

Group participants under a coloured header with **`box`**:

```scala
def box[A](title: String, color: Option[String] = None)
          (body: (PUmlCtx, InSequence, InBox) ?=> A)
          (using PUmlCtx, InSequence): A
```
Returns the body's result — so you can declare and capture participants inside:

```scala
val (service, db) = box("Back-end", Some("#DDFFDD")):
  (control("Service"), database("Postgres"))
```

## Messages

```scala
def step(title: String)(from: Alias)(to: Alias)(using PUmlCtx, InSequence): Unit
```
**Curried** — `step("Open store")(user)(browser)` emits `from -> to: title`.

## Control groups

Open with one of these, then `endGroup()` to close. `alt`/`else` model branches.

`altGroup(title=None)` · `elseGroup(title=None)` · `loopGroup(title=None)` ·
`optGroup(title=None)` · `parGroup(title=None)` · `breakGroup(title=None)` ·
`criticalGroup(title=None)` · `group(title=None)` · `endGroup()`.

## Worked example

```scala
block:
  uml:
    sequenceDiagram:
      autonumber()
      teozRenderingEngine()
      val user    = actor("User")
      val browser = box("Front-end", Some("#DAEEFF")):
        participant("Browser")
      val api     = participant("API")
      val (service, db) = box("Back-end", Some("#DDFFDD")):
        (control("Service"), database("Postgres"))
      step("Open store")(user)(browser)
      step("Click checkout")(browser)(api)
      loopGroup(Some("for each item"))
      step("Reserve inventory")(api)(service)
      step("Update stock")(service)(db)
      endGroup()
      altGroup(Some("payment ok"))
      step("Confirm order")(api)(browser)
      elseGroup(Some("payment failed"))
      step("Show error")(api)(browser)
      endGroup()
```
