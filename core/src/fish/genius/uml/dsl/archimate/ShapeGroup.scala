package fish.genius.uml.dsl.archimate

/**
 * The seven Archimate 3 layers, each with its default fill colour macro and
 * the PlantUML sprite-pack prefix used to look up the layer's icons.
 */
enum ShapeGroup(val defaultColor: String, val prefix: String):
  case Application extends ShapeGroup("#APPLICATION", "application")
  case Strategy extends ShapeGroup("#STRATEGY", "strategy")
  case Business extends ShapeGroup("#BUSINESS", "business")
  case Technology extends ShapeGroup("#TECHNOLOGY", "technology")
  case Physical extends ShapeGroup("#PHYSICAL", "physical")
  case Motivation extends ShapeGroup("#MOTIVATION", "motivation")
  case Implementation extends ShapeGroup("#IMPLEMENTATION", "implementation")
