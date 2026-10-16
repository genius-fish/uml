package fish.genius.uml.dsl.archimate

/**
 * The seven Archimate 3 layers. The scene stacks its planes by layer and the flat
 * diagram takes each layer's colour from it.
 */
enum ShapeGroup:
  case Application, Strategy, Business, Technology, Physical, Motivation, Implementation
