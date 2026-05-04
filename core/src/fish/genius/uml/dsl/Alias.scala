package fish.genius.uml.dsl

/**
 * A PlantUML-safe identifier (lowercase alphanumeric, no digits as the first
 * character of a generated alias).
 *
 * Construct via:
 *   - [[Alias.fresh]] — auto-allocated unique alias from the current
 *     [[Counters]] in the [[PUmlCtx]]
 *   - [[Alias.apply]] — sanitise an arbitrary string
 *   - [[Alias.unsafe]] — wrap a known-already-safe string verbatim
 */
opaque type Alias = String

object Alias:

  /** The set of characters stripped before sanitisation. */
  private val nonAlnum = "[^A-Za-z0-9]".r

  /** Replace digits with English words so the result is purely alphabetic. */
  private def replaceDigits(s: String): String =
    s.replace("0", "zero")
      .replace("1", "one")
      .replace("2", "two")
      .replace("3", "three")
      .replace("4", "four")
      .replace("5", "five")
      .replace("6", "six")
      .replace("7", "seven")
      .replace("8", "eight")
      .replace("9", "nine")

  /** Sanitise an arbitrary string into a lower-case alphabetic alias. */
  def apply(raw: String): Alias =
    val cleaned = nonAlnum.replaceAllIn(raw, "").toLowerCase
    val safe    = replaceDigits(cleaned)
    if safe.isEmpty then "a" else safe

  /** Wrap a string the caller guarantees is already alias-safe. */
  def unsafe(value: String): Alias = value

  /** Allocate a unique alias from the [[PUmlCtx]]'s counter. */
  def fresh(
    using ctx: PUmlCtx
  ): Alias = s"a${ctx.counters.nextAliasIndex()}"

  extension (a: Alias) def value: String = a

end Alias
