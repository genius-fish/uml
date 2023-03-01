package fish.genius.uml.model.archimate

import fish.genius.uml.model.{CanBuildSpecification, SpecificationBuilder}

class ActivityDiagram()(implicit
    specificationBuilder: SpecificationBuilder
) extends CanBuildSpecification {
  def title(value: String): Unit = statement(s"title $value \n")

  def action(name: String): Unit = statement(s":$name;")

  def start(): Unit = statement("start")

  def stop(): Unit = statement("stop")
}

object ActivityDiagram {
  def activityDiagram(
      body: ActivityDiagram => Any
  )(implicit specificationBuilder: SpecificationBuilder): ActivityDiagram = {
    val diagram = new ActivityDiagram()
    body.apply(diagram)
    diagram
  }
}
