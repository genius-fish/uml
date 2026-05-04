package fish.genius.uml.dsl

import java.util.concurrent.atomic.AtomicInteger

/**
 * Scope-local id generator for auto-allocated [[Alias]]es.
 *
 * One [[Counters]] instance is bundled with each [[PUmlCtx]], so two
 * concurrent `block { ... }` renders never share counter state. Within a
 * single block, all helpers that ask for `using PUmlCtx` see the same
 * counters and produce a deterministic ascending alias sequence.
 */
final class Counters private[dsl] ():

  private val aliases: AtomicInteger = AtomicInteger(0)

  /** Allocate a fresh alias suffix (e.g. `0`, `1`, `2`). */
  def nextAliasIndex(): Int = aliases.getAndIncrement()

object Counters:

  /** Allocate a fresh, empty counter set. */
  private[dsl] def fresh: Counters = new Counters
