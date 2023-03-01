package fish.genius.uml.model.archimate

sealed trait ShapeGroup {
  def defaultColor: String
  def prefix: String
}

case object Application extends ShapeGroup {
  override val defaultColor: String = "#APPLICATION"
  override val prefix: String = "application"
}

case object Strategy extends ShapeGroup {
  override val defaultColor: String = "#STRATEGY"
  override val prefix: String = "strategy"
}

case object Business extends ShapeGroup {
  override val defaultColor: String = "#BUSINESS"
  override val prefix: String = "business"
}

case object Technology extends ShapeGroup {
  override val defaultColor: String = "#TECHNOLOGY"
  override val prefix: String = "technology"
}

case object Physical extends ShapeGroup {
  override val defaultColor: String = "#PHYSICAL"
  override val prefix: String = "physical"
}

case object Motivation extends ShapeGroup {
  override val defaultColor: String = "#MOTIVATION"
  override val prefix: String = "motivation"
}

case object Implementation extends ShapeGroup {
  override val defaultColor: String = "#IMPLEMENTATION"
  override val prefix: String = "implementation"
}
