package fish.genius.uml.examples

import zio.*

import fish.genius.uml.dsl.*
import fish.genius.uml.dsl.SkinParamProperty.*
import fish.genius.uml.dsl.archimate.*
import fish.genius.uml.dsl.archimate.RelationshipType.*
import fish.genius.uml.dsl.archimate.ShapeType.*

/**
 * A deliberately large Archimate diagram: a fictional online-retail landscape
 * spanning the motivation, business, application and technology layers, with
 * nested boundaries, three custom stereotypes and ~25 cross-layer
 * relationships.
 *
 * Renders with the ELK layout engine by default (this is the kind of diagram
 * where ELK pays off); set `UML_LAYOUT=graphviz` to compare with the classic
 * engine. Note that ELK ignores `Edge` direction hints, so none are used here.
 */
object EnterpriseExample extends ZIOAppDefault:

  given ArchimateConfiguration = ArchimateConfiguration(
    elkLayout = !sys.env.get("UML_LAYOUT").contains("graphviz")
  )

  private val doc = block:
    uml:
      archimateDiagram:
        val phaseout  = defineStereoType(
          "phaseout",
          Some("being phased out"),
          Some("#FF8888"),
          BorderColor("#FF0000"),
          BorderBold(),
        )
        val strategic = defineStereoType(
          "strategic",
          Some("strategic investment"),
          Some("#88CC88"),
          BorderColor("#008800"),
          BorderBold(),
        )
        val saas      = defineStereoType(
          "saas",
          Some("SaaS / vendor-hosted"),
          Some("#8888FF"),
          BorderColor("#0000AA"),
        )

        legend("Landscape legend", stereoTypes = List(phaseout, strategic, saas))

        // Aliases assigned inside boundary bodies, referenced across layers below.
        var revenueGoal   = Alias("tbd")
        var checkout      = Alias("tbd")
        var pay           = Alias("tbd")
        var fulfil        = Alias("tbd")
        var shopping      = Alias("tbd")
        var webShop       = Alias("tbd")
        var mobileApp     = Alias("tbd")
        var search        = Alias("tbd")
        var reco          = Alias("tbd")
        var orderService  = Alias("tbd")
        var inventory     = Alias("tbd")
        var legacyPayment = Alias("tbd")
        var paymentHub    = Alias("tbd")
        var crm           = Alias("tbd")
        var cdp           = Alias("tbd")
        var profile       = Alias("tbd")
        var kubernetes    = Alias("tbd")
        var postgres      = Alias("tbd")
        var kafka         = Alias("tbd")

        boundary("Motivation", shapeType = Some(MotivationDriver)):
          val cmo       = shape(MotivationStakeholder, label("CMO"))
          val churn     = shape(MotivationDriver, label("Customer churn"))
          val slowPay   = shape(
            MotivationAssessment,
            label("Checkout abandonment", description = Some("payment takes >30s")),
          )
          revenueGoal = shape(MotivationGoal, label("Grow online revenue"))
          val subSecond = shape(MotivationRequirement, label("Sub-second search"))

          relationship(Association)(cmo)(churn)
          relationship(Influence, label = Some("worsens"))(slowPay)(churn)
          relationship(Influence)(churn)(revenueGoal)
          relationship(Realization)(subSecond)(revenueGoal)

        boundary("Customer journey", shapeType = Some(StrategyValueStream)):
          val customer = shape(BusinessActor, label("Customer"))
          val browse   = shape(BusinessProcess, label("Browse catalogue"))
          checkout = shape(BusinessProcess, label("Check out"))
          pay = shape(BusinessProcess, label("Pay"))
          fulfil = shape(BusinessProcess, label("Fulfil order"))
          val placed   = shape(BusinessEvent, label("Order placed"))
          shopping = shape(BusinessService, label("Online shopping"))

          relationship(Assignment)(customer)(browse)
          relationship(Triggering)(browse)(checkout)
          relationship(Triggering)(checkout)(pay)
          relationship(Triggering)(pay)(placed)
          relationship(Triggering)(placed)(fulfil)
          relationship(Serving, label = Some("serves"))(shopping)(customer)

        boundary("Application landscape", shapeType = Some(ApplicationComponent)):
          boundary("Storefront", shapeType = Some(ApplicationCollaboration)):
            webShop = shape(ApplicationComponent, label("Web shop", Some("IT system")))
            mobileApp = shape(ApplicationComponent, label("Mobile app", Some("IT system")))
            search = shape(ApplicationService, label("Product search"))
            reco = shape(
              ApplicationComponent,
              label("Recommendation engine", Some("IT system")),
              stereoType = Some(strategic),
            )
            relationship(Serving)(search)(webShop)
            relationship(Serving)(search)(mobileApp)
            relationship(Serving, label = Some("suggests"))(reco)(webShop)

          boundary("Commerce backbone", shapeType = Some(ApplicationCollaboration)):
            orderService = shape(ApplicationComponent, label("Order service", Some("IT system")))
            inventory = shape(ApplicationComponent, label("Inventory service", Some("IT system")))
            legacyPayment = shape(
              ApplicationComponent,
              label("Legacy payment", Some("IT system"), description = Some("retire by Q4")),
              stereoType = Some(phaseout),
            )
            paymentHub = shape(
              ApplicationComponent,
              label("Payment hub", Some("IT system")),
              stereoType = Some(strategic),
            )
            relationship(Flow, label = Some("stock levels"))(inventory)(orderService)
            relationship(Triggering, label = Some("replaces"))(paymentHub)(legacyPayment)

          boundary("Customer 360", shapeType = Some(ApplicationCollaboration)):
            crm = shape(
              ApplicationComponent,
              label("CRM", Some("IT system")),
              stereoType = Some(saas),
            )
            cdp = shape(ApplicationComponent, label("Customer data platform", Some("IT system")))
            profile = shape(ApplicationDataObject, label("Customer profile"))
            relationship(WriteAccess)(crm)(profile)
            relationship(ReadWriteAccess)(cdp)(profile)

        boundary("Platform", shapeType = Some(TechnologyNode)):
          kubernetes = shape(TechnologyNode, label("Kubernetes", Some("Runtime")))
          postgres = shape(TechnologyService, label("PostgreSQL", Some("Database")))
          kafka = shape(TechnologySystemSoftware, label("Kafka", Some("Event bus")))
          val cdn = shape(TechnologyService, label("CDN", Some("Edge")), stereoType = Some(saas))

          relationship(Serving)(cdn)(kubernetes)

        // Cross-layer wiring: applications serve business processes ...
        relationship(Serving)(webShop)(checkout)
        relationship(Serving)(mobileApp)(checkout)
        relationship(Serving)(legacyPayment)(pay)
        relationship(Serving)(paymentHub)(pay)
        relationship(Serving)(orderService)(fulfil)
        relationship(Serving)(inventory)(fulfil)
        relationship(Serving, label = Some("case handling"))(crm)(fulfil)

        // ... business services realize goals, applications realize services ...
        relationship(Realization)(shopping)(revenueGoal)
        relationship(Realization)(webShop)(shopping)
        relationship(Realization)(mobileApp)(shopping)

        // ... and the platform carries the applications.
        relationship(Assignment)(kubernetes)(orderService)
        relationship(Assignment)(kubernetes)(reco)
        relationship(Serving)(postgres)(orderService)
        relationship(Serving)(postgres)(inventory)
        relationship(Flow, label = Some("events"))(kafka)(cdp)
        relationship(Flow, label = Some("order events"))(orderService)(kafka)

  def run: ZIO[Any, Throwable, Any] = Renderer.run("enterprise-example", doc)

end EnterpriseExample
