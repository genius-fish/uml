package fish.genius.uml.model

trait Specification extends CanBuildSpecification {
  implicit val builder: SpecificationBuilder =
    new SpecificationBuilder()
  def uml(
      body: => Any
  ): Unit = {
    builder add Statement("@startuml")
    body
    builder add Statement("@enduml")
  }

  override def toString: String = builder.output
}

class SpecificationBuilder {
  private var content: List[Statement] = Nil

  def add(statement: Statement): Unit = content = statement :: content

  def output: String = content.reverse.mkString("\n")
}

trait CanBuildSpecification {
  def statement(value: String)(implicit builder: SpecificationBuilder): Unit =
    builder.add(Statement(value))

  def expression(
      e: Alias => String
  )(implicit builder: SpecificationBuilder): Alias = {
    val alias = Alias()
    builder.add(Statement(e.apply(alias)))
    alias
  }

  def expressionWithBody(
      e: Alias => String
  )(body: => Any)(implicit builder: SpecificationBuilder): Alias = {
    val alias = Alias()
    builder.add(Statement(e.apply(alias) + " {"))
    body
    builder.add(Statement("}"))
    alias
  }
}
