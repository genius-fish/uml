package fish.genius.uml.dsl

import fish.genius.uml.ast.PUmlNode

/**
 * Direct AST emitters that user-level DSL helpers compose on. These are the
 * thinnest possible wrappers over [[PUmlNode]] constructors; they do not
 * enforce any structural markers.
 */

/** Emit a single literal PlantUML line. */
def statement(value: String): PUml[Unit] =
  emit(PUmlNode.Statement(value))

/** Emit multiple lines: each non-empty line in `text` becomes a [[PUmlNode.Statement]]. */
def statements(text: String): PUml[Unit] =
  text.linesIterator.foreach: line =>
    statement(line)

/** Emit a `' comment` line. */
def comment(text: String): PUml[Unit] =
  emit(PUmlNode.Comment(text))

/**
 * Emit a `header` line and return a freshly allocated [[Alias]] that the
 * caller can reference in subsequent statements. The header is built by the
 * caller from the alias.
 *
 * Mirrors `Specification.expression` from the previous OO-style API.
 */
def expression(build: Alias => String): PUml[Alias] =
  val alias = Alias.fresh
  statement(build(alias))
  alias

/**
 * Emit a `header { body }` block and return a fresh alias bound to the
 * header. Mirrors `Specification.expressionWithBody`.
 */
def expressionWithBody(build: Alias => String)(body: PUml[Unit]): PUml[Alias] =
  val alias = Alias.fresh
  emit(PUmlNode.Block(build(alias), childBlock(body)))
  alias

/** Emit `header { body }` without allocating an alias. */
def headerBlock(header: String)(body: PUml[Unit]): PUml[Unit] =
  emit(PUmlNode.Block(header, childBlock(body)))

/** Conditionally evaluate `body`. Sugar for `if cond then body`. */
def when(cond: Boolean)(body: PUml[Unit]): PUml[Unit] =
  if cond then body else ()
