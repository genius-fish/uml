package fish.genius.uml.dsl

import fish.genius.uml.ast.PUmlNode

/**
 * Run a [[PUml]] block in a fresh scope and return the produced AST.
 *
 * This is the user-facing entry point that turns context-function code into
 * an inspectable [[PUmlNode]] value.
 *
 * Example:
 * {{{
 *   val node: PUmlNode = block {
 *     uml { statement("Alice -> Bob: hi") }
 *   }
 * }}}
 */
def block(body: PUml[Unit]): PUmlNode =
  given ctx: PUmlCtx = PUmlCtx.fresh
  body
  ctx.collect

/**
 * Like [[block]], but bubbles the body's nodes into the surrounding
 * [[PUmlCtx]]. Used by library helpers that build a sub-AST inside a parent
 * context.
 *
 * `block(body)` is correct for the *outermost* user-facing entry point;
 * `childBlock(body)` is correct everywhere else inside the library.
 */
private[uml] def childBlock(
  body: PUml[Unit]
)(
  using outer: PUmlCtx
): PUmlNode =
  val inner = PUmlCtx.childOf(outer)
  body(
    using inner
  )
  inner.collect

/** Append a single AST node to the current [[PUmlCtx]]. */
def emit(
  node: PUmlNode
)(
  using ctx: PUmlCtx
): Unit =
  ctx.emit(node)
