package fish.genius.uml.dsl

/**
 * Marker capabilities used by DSL helpers to enforce structural constraints at
 * compile time.
 *
 * A producer (e.g. `archimate`) introduces a marker via `given`; consumers
 * (e.g. `shape`) require it via `using`. Because each `PUml[A]` body is a
 * context function, the marker is automatically threaded through the body.
 *
 * Example: calling `shape(...)` outside an `archimate { ... }` block produces
 * a compile error
 * "no given instance of type fish.genius.uml.dsl.markers.InArchimate was found".
 */
object markers:

  /** Inside `@startuml` / `@enduml`. Required by every domain DSL helper. */
  sealed trait InUml

  /** Inside an `archimate { ... }` block. Required by Archimate helpers. */
  sealed trait InArchimate

  /** Inside an Archimate `boundary { ... }`. Allows nested boundaries. */
  sealed trait InBoundary

  /** Inside a `sequence { ... }` block. Required by sequence-diagram helpers. */
  sealed trait InSequence

  /** Inside a sequence `box { ... }`. */
  sealed trait InBox

  /** Inside a sequence `alt`/`opt`/`loop`/... group. */
  sealed trait InGroup

  /** Inside an `activity { ... }` block. Required by activity-diagram helpers. */
  sealed trait InActivity

  // Singleton instances. They are package-private so producer methods can
  // introduce them as `given`s, but user code cannot fabricate one to bypass
  // the type checker.
  private[uml] object InUml extends InUml
  private[uml] object InArchimate extends InArchimate
  private[uml] object InBoundary extends InBoundary
  private[uml] object InSequence extends InSequence
  private[uml] object InBox extends InBox
  private[uml] object InGroup extends InGroup
  private[uml] object InActivity extends InActivity

end markers
