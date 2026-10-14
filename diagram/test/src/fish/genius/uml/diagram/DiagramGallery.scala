package fish.genius.uml.diagram

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import zio.test.*

import fish.genius.uml.dsl.archimate.{RelationshipType, ShapeType}
import fish.genius.uml.scene.*

// The diagrams the look is judged on: an application landscape, every layer's main
// elements, and every relationship side by side. DIAGRAM_GALLERY=dir writes each as a
// standalone .tex (Heros) and as -innovenso.tex (Montserrat), with SVG and PNG, to
// compile and look at; DIAGRAM_PALETTE=name=hex,… lays a palette over both.
object DiagramGallery extends ZIOSpecDefault:

  private def leaf(
    id: String,
    label: String,
    shape: ShapeType,
    caption: Option[String] = None,
    note: Option[String] = None,
  ) =
    SceneNode(SceneId(id), NodeKind.Element(shape), label, caption = caption, note = note)

  private def edge(
    id: String,
    from: String,
    to: String,
    kind: RelationshipType,
    label: Option[String] = None,
  ) =
    SceneEdge(SceneId(id), SceneId(from), SceneId(to), kind, label)

  val layers: SceneGraph = SceneGraph(
    "Every layer",
    List(
      leaf("stakeholder", "Chief Executive", ShapeType.MotivationStakeholder),
      leaf("driver", "Margin pressure", ShapeType.MotivationDriver),
      leaf("goal", "Grow online revenue", ShapeType.MotivationGoal),
      leaf("principle", "API first", ShapeType.MotivationPrinciple),
      leaf("requirement", "Checkout under two seconds", ShapeType.MotivationRequirement),
      leaf("capability", "Order Management", ShapeType.StrategyCapability),
      leaf("stream", "Order to cash", ShapeType.StrategyValueStream),
      leaf("actor", "Customer", ShapeType.BusinessActor),
      leaf("role", "Buyer", ShapeType.BusinessRole),
      leaf("process", "Place an order", ShapeType.BusinessProcess),
      leaf("bservice", "Ordering", ShapeType.BusinessService),
      leaf("object", "Order", ShapeType.BusinessObject),
      leaf("event", "Order received", ShapeType.BusinessEvent),
      leaf("component", "Web Shop", ShapeType.ApplicationComponent, Some("[IT System: Next.js]")),
      leaf("interface", "Checkout API", ShapeType.ApplicationInterface),
      leaf("aservice", "Order Intake", ShapeType.ApplicationService),
      leaf("data", "Order Record", ShapeType.ApplicationDataObject),
      leaf("node", "Kubernetes", ShapeType.TechnologyNode),
      leaf("software", "PostgreSQL", ShapeType.TechnologySystemSoftware),
      leaf("artifact", "shop.jar", ShapeType.TechnologyArtifact),
      leaf("device", "Card terminal", ShapeType.TechnologyDevice),
      leaf("facility", "Warehouse Ghent", ShapeType.PhysicalFacility),
      leaf("work", "Replatform checkout", ShapeType.ImplementationWorkPackage),
      leaf("deliverable", "New checkout", ShapeType.ImplementationDeliverable),
    ),
    List(
      edge("r1", "stakeholder", "driver", RelationshipType.Association),
      edge("r2", "driver", "goal", RelationshipType.Influence),
      edge("r3", "requirement", "goal", RelationshipType.Realization),
      edge("r4", "principle", "requirement", RelationshipType.Influence),
      edge("r5", "capability", "goal", RelationshipType.Realization),
      edge("r6", "stream", "capability", RelationshipType.Serving),
      edge("r7", "actor", "role", RelationshipType.Assignment),
      edge("r8", "role", "process", RelationshipType.Assignment),
      edge("r9", "event", "process", RelationshipType.Triggering),
      edge("r10", "process", "bservice", RelationshipType.Realization),
      edge("r11", "process", "object", RelationshipType.WriteAccess),
      edge("r12", "process", "capability", RelationshipType.Realization),
      edge("r13", "aservice", "process", RelationshipType.Serving),
      edge("r14", "component", "aservice", RelationshipType.Realization),
      edge("r15", "component", "interface", RelationshipType.Composition),
      edge("r16", "component", "data", RelationshipType.ReadWriteAccess),
      edge("r17", "node", "component", RelationshipType.Serving),
      edge("r18", "node", "software", RelationshipType.Aggregation),
      edge("r19", "artifact", "component", RelationshipType.Realization),
      edge("r20", "device", "node", RelationshipType.Flow, Some("card data")),
      edge("r21", "facility", "device", RelationshipType.Association),
      edge("r22", "work", "deliverable", RelationshipType.Realization),
      edge("r23", "deliverable", "component", RelationshipType.Realization),
      edge("r24", "data", "object", RelationshipType.Realization),
    ),
  )

  // every relationship once, from a box on the left to one on the right, named on it
  val relationships: SceneGraph =
    val kinds = RelationshipType.values.toList
    SceneGraph(
      "Every relationship",
      kinds.zipWithIndex.flatMap((k, i) =>
        List(
          leaf(s"s$i", s"Source ${i + 1}", ShapeType.ApplicationComponent),
          leaf(s"t$i", s"Target ${i + 1}", ShapeType.ApplicationService),
        )
      ),
      kinds.zipWithIndex.map((k, i) => edge(s"e$i", s"s$i", s"t$i", k, Some(k.toString))),
    )

  // a checkout told as messages: an actor, a system of three parts on its field, an
  // outside service, a loop and a message to itself
  val checkout: Sequence.Graph =
    import Sequence.*
    val shop = Some(Group("Web Shop", Some(ShapeType.ApplicationComponent)))
    Graph(
      "Checkout",
      List(
        Lifeline("customer", "Customer", ShapeType.BusinessActor, Some("Business Actor")),
        Lifeline("spa", "Storefront", ShapeType.ApplicationComponent, Some("[Container: React]"), shop),
        Lifeline("api", "Order API", ShapeType.ApplicationComponent, Some("[Container: Scala, ZIO]"), shop),
        Lifeline("db", "Order Store", ShapeType.ApplicationComponent, Some("[Container: PostgreSQL]"), shop),
        Lifeline("psp", "Payment Service Provider", ShapeType.ApplicationComponent, Some("[IT System]")),
      ),
      List(
        Step.Send("customer", "spa", "Confirms the basket"),
        Step.Send("spa", "api", "Place the order", Kind.Call, Some("HTTPS, JSON {Order}")),
        Step.Send("api", "api", "Check stock and price"),
        Step.Send("api", "db", "Store the order as pending", Kind.Call, Some("JDBC")),
        Step.Send("api", "psp", "Request payment authorisation", Kind.Call, Some("HTTPS {Amount, Card token}")),
        Step.Loop(
          "until authorised or timed out",
          List(
            Step.Send("psp", "api", "Payment status", Kind.Signal, Some("Webhook"))
          ),
        ),
        Step.Send("api", "db", "Mark the order paid"),
        Step.Send("api", "spa", "Order confirmed", Kind.Reply, Some("{Order number}")),
        Step.Send("spa", "customer", "Shows the confirmation", Kind.Reply),
      ),
    )

  end checkout

  // an order handled across three lanes: a choice with a default, a parallel split
  // and join, a loop back, events with triggers, a note
  val order: Activity.Graph =
    import Activity.*
    Graph(
      "Handle an order",
      List(
        Lane("customer", "Customer", Some(ShapeType.BusinessActor)),
        Lane("sales", "Sales desk", Some(ShapeType.BusinessRole)),
        Lane("warehouse", "Warehouse", Some(ShapeType.BusinessRole)),
      ),
      List(
        Node("start", "Order placed", Kind.Start(Trigger.Message), Some("customer")),
        Node("check", "Check the order", Kind.Work(Task.User), Some("sales"), Some("[CRM]")),
        Node("ok", "Complete?", Kind.Gateway(Gate.Exclusive), Some("sales")),
        Node("ask", "Ask for the missing details", Kind.Work(Task.Manual), Some("sales")),
        Node("reply", "Customer replies", Kind.Intermediate(Trigger.Message), Some("customer")),
        Node("split", "", Kind.Gateway(Gate.Parallel), Some("sales")),
        Node(
          "invoice",
          "Send the invoice",
          Kind.Work(Task.Service),
          Some("sales"),
          Some("[ERP]"),
          List("Due in 30 days"),
        ),
        Node("pick", "Pick and pack", Kind.Work(Task.Plain), Some("warehouse"), Some("[WMS]")),
        Node("ship", "Ship the parcel", Kind.Work(Task.Sub), Some("warehouse")),
        Node("join", "", Kind.Gateway(Gate.Parallel), Some("sales")),
        Node("done", "Order fulfilled", Kind.End(Trigger.Plain), Some("customer")),
      ),
      List(
        Flow("start", "check"),
        Flow("check", "ok"),
        Flow("ok", "split", Some("yes")),
        Flow("ok", "ask", Some("no"), default = true),
        Flow("ask", "reply"),
        Flow("reply", "check"),
        Flow("split", "invoice"),
        Flow("split", "pick"),
        Flow("pick", "ship"),
        Flow("invoice", "join"),
        Flow("ship", "join"),
        Flow("join", "done"),
      ),
    )

  end order

  // a claim across four lanes: two branches of a parallel split joining a gateway
  // whose branch leaves from the side they would come in at, a loop back from a timer
  // event (its name beside it) into that gateway, a note, two tracks in one lane
  val claim: Activity.Graph =
    import Activity.*
    Graph(
      "Handle a claim",
      List(
        Lane("holder", "Policy holder", Some(ShapeType.BusinessActor)),
        Lane("handler", "Claims handler", Some(ShapeType.BusinessRole)),
        Lane("adjuster", "Loss adjuster", Some(ShapeType.BusinessRole)),
        Lane("finance", "Finance", Some(ShapeType.BusinessRole)),
      ),
      List(
        Node("start", "Damage noticed", Kind.Start(Trigger.Plain), Some("holder")),
        Node("file", "File the claim online", Kind.Work(Task.User), Some("holder"), Some("[Claims Portal]")),
        Node("register", "Register the claim", Kind.Work(Task.Service), Some("handler"), Some("[Policy Admin]")),
        Node("covered", "Covered by the policy?", Kind.Gateway(Gate.Exclusive), Some("handler")),
        Node("refuse", "Write the refusal", Kind.Work(Task.User), Some("handler")),
        Node("refused", "Claim refused", Kind.End(Trigger.Plain), Some("handler")),
        Node("split", "", Kind.Gateway(Gate.Parallel), Some("handler")),
        Node("inspect", "Inspect the damage", Kind.Work(Task.Manual), Some("adjuster")),
        Node(
          "report",
          "Write the report",
          Kind.Work(Task.User),
          Some("adjuster"),
          None,
          List("Within ten working days"),
        ),
        Node("fraud", "Check for fraud", Kind.Work(Task.Rule), Some("handler"), Some("[Policy Admin]")),
        Node("agree", "Amount agreed?", Kind.Gateway(Gate.Exclusive), Some("handler")),
        Node("negotiate", "Negotiate with the holder", Kind.Work(Task.User), Some("handler")),
        Node("wait", "Two weeks pass", Kind.Intermediate(Trigger.Timer), Some("handler")),
        Node("pay", "Pay the claim", Kind.Work(Task.Service), Some("finance"), Some("[Payments]")),
        Node("settled", "Claim settled", Kind.End(Trigger.Plain), Some("finance")),
      ),
      List(
        Flow("start", "file"),
        Flow("file", "register"),
        Flow("register", "covered"),
        Flow("covered", "refuse", Some("no")),
        Flow("refuse", "refused"),
        Flow("covered", "split", Some("yes"), default = true),
        Flow("split", "inspect"),
        Flow("inspect", "report"),
        Flow("split", "fraud"),
        Flow("report", "agree"),
        Flow("fraud", "agree"),
        Flow("agree", "negotiate", Some("no")),
        Flow("negotiate", "wait"),
        Flow("wait", "agree"),
        Flow("agree", "pay", Some("yes"), default = true),
        Flow("pay", "settled"),
      ),
    )

  end claim

  val graphs: List[(String, SceneGraph)] =
    List("landscape" -> DiagramSpec.landscape, "layers" -> layers, "relationships" -> relationships)

  private def palette: Map[String, String] =
    sys.env.get("DIAGRAM_PALETTE").toList.flatMap(_.split(",").toList).flatMap: pair =>
      pair.split("=").toList match
        case List(name, hex) => List(name.trim -> hex.trim)
        case _               => Nil
    .toMap

  override def spec =
    test("the gallery draws, and is written where DIAGRAM_GALLERY says"):
      val drawn = graphs.flatMap: (name, graph) =>
        List(
          (name, Diagram.draw(graph)),
          (s"$name-innovenso", Diagram.draw(graph, Diagram.Options(Diagram.Style(typeface = Typeface.montserrat)))),
        )
      ++ List(
        ("checkout", Right(Sequence.draw(checkout))),
        (
          "checkout-innovenso",
          Right(Sequence.draw(checkout, Sequence.Options(Diagram.Style(typeface = Typeface.montserrat)))),
        ),
        ("order", Right(Activity.draw(order))),
        (
          "order-innovenso",
          Right(Activity.draw(order, Activity.Options(Diagram.Style(typeface = Typeface.montserrat)))),
        ),
      )
      sys.env.get("DIAGRAM_GALLERY").map(Path.of(_)).foreach: dir =>
        Files.createDirectories(dir)
        drawn.foreach:
          case (name, Right(d)) =>
            Files.writeString(dir.resolve(s"$name.tex"), Tikz.standalone(d, palette), StandardCharsets.UTF_8)
            Files.writeString(dir.resolve(s"$name.svg"), Svg.document(d, palette), StandardCharsets.UTF_8)
            Files.write(dir.resolve(s"$name.png"), Png.image(d, 200, palette))
          case _                => ()
      assertTrue(drawn.forall(_._2.exists(_.overflow == 0)))

end DiagramGallery
