package fish.genius.uml.testkit

import zio.test.Assertion

import fish.genius.uml.ast.PUmlNode

/**
 * Structural assertions and extension methods for asserting on [[PUmlNode]]
 * values directly, instead of comparing rendered PlantUML strings.
 *
 * Two flavours are provided:
 *
 *   - **ZIO Test Assertion combinators** (e.g. `containsStatement("@startuml")`)
 *     for use with the classic `assert(value)(assertion)` pattern.
 *   - **Extension methods** on `PUmlNode` (e.g. `n.countStatement(...)`,
 *     `n.hasBlock("skinparam Activity")`) for ergonomic use with
 *     `assertTrue`.
 *
 * Import `PUmlAssertions.*` to bring both into scope.
 */
object PUmlAssertions:

  // ---------------------------------------------------------------------
  // Walk helpers (private; reach via extension methods or assertions)
  // ---------------------------------------------------------------------

  private def walk(node: PUmlNode)(visit: PUmlNode => Unit): Unit =
    visit(node)
    node match
      case PUmlNode.Sequence(cs)   => cs.foreach(walk(_)(visit))
      case PUmlNode.Block(_, body) => walk(body)(visit)
      case PUmlNode.Diagram(_, b)  => walk(b)(visit)
      case _                       => ()

  private def doCountStatementContaining(needle: String, node: PUmlNode): Int =
    var n = 0
    walk(node):
      case PUmlNode.Statement(s) if s.contains(needle) => n += 1
      case _                                           => ()
    n

  private def doHasBlock(headerNeedle: String, node: PUmlNode): Boolean =
    var found = false
    walk(node):
      case PUmlNode.Block(h, _) if h.contains(headerNeedle) => found = true
      case _                                                => ()
    found

  private def doStatements(node: PUmlNode): List[String] =
    val buf = scala.collection.mutable.ListBuffer.empty[String]
    walk(node):
      case PUmlNode.Statement(s) => buf += s
      case _                     => ()
    buf.toList

  private def doDiagramKinds(node: PUmlNode): List[String] =
    val buf = scala.collection.mutable.ListBuffer.empty[String]
    walk(node):
      case PUmlNode.Diagram(k, _) => buf += k
      case _                      => ()
    buf.toList

  // ---------------------------------------------------------------------
  // ZIO Test Assertion combinators
  // ---------------------------------------------------------------------

  /**
   * `assert(node)(containsStatement("@startuml"))` — at least one statement
   * in the AST contains the given substring.
   */
  def containsStatement(needle: String): Assertion[PUmlNode] =
    Assertion.assertion(s"containsStatement(\"$needle\")")(doCountStatementContaining(needle, _) > 0)

  /** `assert(node)(containsBlock("skinparam Activity"))`. */
  def containsBlock(headerNeedle: String): Assertion[PUmlNode] =
    Assertion.assertion(s"containsBlock(\"$headerNeedle\")")(doHasBlock(headerNeedle, _))

  /** `assert(node)(statementCountIs("Alice", 2))`. */
  def statementCountIs(needle: String, expected: Int): Assertion[PUmlNode] =
    Assertion.assertion(s"statementCountIs(\"$needle\", $expected)")(
      doCountStatementContaining(needle, _) == expected
    )

  /** `assert(node)(hasDiagramKind("uml"))`. */
  def hasDiagramKind(kind: String): Assertion[PUmlNode] =
    Assertion.assertion(s"hasDiagramKind(\"$kind\")")(doDiagramKinds(_).contains(kind))

  // ---------------------------------------------------------------------
  // Extension methods (use with assertTrue)
  // ---------------------------------------------------------------------

  extension (n: PUmlNode)

    /** Number of statements whose text contains the given substring. */
    def countStatement(needle: String): Int = doCountStatementContaining(needle, n)

    /** True iff some block header contains the given substring. */
    def hasBlock(headerNeedle: String): Boolean = doHasBlock(headerNeedle, n)

    /** All statements in document order. */
    def statements: List[String] = doStatements(n)

    /** All statements whose text contains the given substring. */
    def statementsContaining(needle: String): List[String] =
      doStatements(n).filter(_.contains(needle))

    /** All diagram kinds in document order (e.g. `List("uml")` for a single diagram). */
    def diagramKinds: List[String] = doDiagramKinds(n)

  end extension

end PUmlAssertions
