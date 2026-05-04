package fish.genius.uml.dsl

import scala.collection.mutable

import fish.genius.uml.ast.PUmlNode

/**
 * A scope-local mutable buffer that DSL helpers append to.
 *
 * Each [[block]] invocation allocates a fresh `PUmlCtx`; there is no shared
 * global state. Concurrent renders are race-free by construction.
 *
 * User code does not construct or call this directly — DSL helpers receive it
 * via a `using` parameter and emit into it through [[emit]].
 */
final class PUmlCtx private[uml] (val counters: Counters):

  private val buf: mutable.ArrayBuffer[PUmlNode] = mutable.ArrayBuffer.empty

  /** Append a single AST node. */
  private[uml] def emit(node: PUmlNode): Unit =
    buf += node

  /**
   * Collapse the buffer into a single AST node:
   *   - empty buffer  → [[PUmlNode.empty]]
   *   - single child  → that child
   *   - multiple      → [[PUmlNode.Sequence]]
   */
  private[uml] def collect: PUmlNode =
    buf.size match
      case 0 => PUmlNode.empty
      case 1 => buf.head
      case _ => PUmlNode.Sequence(buf.toVector)

end PUmlCtx

object PUmlCtx:

  /** Allocate a brand-new top-level context with its own [[Counters]]. */
  private[uml] def fresh: PUmlCtx = new PUmlCtx(Counters.fresh)

  /**
   * Allocate a child context that inherits its parent's [[Counters]] so
   * auto-generated aliases stay globally unique across nested scopes
   * (boundary bodies, box bodies, captured-marker bodies …).
   */
  private[uml] def childOf(parent: PUmlCtx): PUmlCtx = new PUmlCtx(parent.counters)

  /**
   * Run a body that requires a single marker capability in a child of the
   * surrounding context and return the produced AST. Used by helpers that
   * introduce a structural marker (e.g. `archimate`, `sequence`).
   */
  private[uml] def captureWith[M](
    body: (PUmlCtx, M) ?=> Unit
  )(
    using
    outer: PUmlCtx,
    m: M,
  ): PUmlNode =
    val inner = childOf(outer)
    body(
      using inner,
      m,
    )
    inner.collect

  /** Like [[captureWith]] but threads two marker capabilities into the body. */
  private[uml] def captureWith2[M1, M2](
    body: (PUmlCtx, M1, M2) ?=> Unit
  )(
    using
    outer: PUmlCtx,
    m1: M1,
    m2: M2,
  ): PUmlNode =
    val inner = childOf(outer)
    body(
      using inner,
      m1,
      m2,
    )
    inner.collect

  end captureWith2

end PUmlCtx

/**
 * A piece of PlantUML-DSL code that produces an `A` while emitting nodes into
 * a [[PUmlCtx]] via context-function semantics.
 */
type PUml[A] = PUmlCtx ?=> A
