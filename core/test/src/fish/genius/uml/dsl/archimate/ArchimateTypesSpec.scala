package fish.genius.uml.dsl.archimate

import zio.test.*

// core is the ArchiMate vocabulary the scene and the diagram match on; these pin what
// downstream code (townplanner) builds against.
object ArchimateTypesSpec extends ZIOSpecDefault:

  def spec = suite("Archimate types")(
    test("every element is listed once, under its own layer"):
      val byLayer = List(
        ShapeGroup.Application -> ShapeType.application,
        ShapeGroup.Strategy -> ShapeType.strategy,
        ShapeGroup.Business -> ShapeType.business,
        ShapeGroup.Technology -> ShapeType.technology,
        ShapeGroup.Physical -> ShapeType.physical,
        ShapeGroup.Motivation -> ShapeType.motivation,
        ShapeGroup.Implementation -> ShapeType.implementation,
      )
      assertTrue(
        ShapeType.*.size == ShapeType.values.length,
        ShapeType.*.toSet == ShapeType.values.toSet,
        byLayer.forall((group, shapes) => shapes.forall(_.group == group)),
        byLayer.map(_._1).toSet == ShapeGroup.values.toSet,
      )
    ,
    test("every relationship is listed once"):
      assertTrue(
        RelationshipType.*.size == RelationshipType.values.length,
        RelationshipType.*.toSet == RelationshipType.values.toSet,
      )
    ,
    // a name is how the scene and the diagram tell two elements of one layer apart
    test("an element's name is unique within its layer"):
      val names = ShapeType.values.toList.groupBy(_.group).view.mapValues(_.map(_.name))
      assertTrue(names.values.forall(ns => ns.distinct.size == ns.size))
    ,
    test("a label keeps the configuration in scope where it was built"):
      given ArchimateConfiguration = ArchimateConfiguration(technicalFontSize = 9)
      val label = ShapeLabel("Web Shop", Some("IT System"), Some("Next.js"))
      assertTrue(
        label.config.technicalFontSize == 9,
        label.config.descriptionFontSize == 10,
        label.componentTypeDetails.contains("Next.js"),
      )
    ,
    test("a caller that still sets elkLayout compiles and gets the defaults otherwise"):
      val cfg = ArchimateConfiguration(elkLayout = true)
      assertTrue(cfg.technicalFontSize == 12, cfg.descriptionFontSize == 10),
  )

end ArchimateTypesSpec
