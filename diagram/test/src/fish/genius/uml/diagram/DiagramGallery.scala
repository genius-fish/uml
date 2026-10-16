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
      kinds.zipWithIndex.flatMap((_, i) =>
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

  // every sign of the family (APP-2260): the geometric glyphs and the marks on boxes,
  // the API styles at the middle of lines, one with a label, one in a colour, and the
  // legend that names them all
  val signs: SceneGraph =
    def badge(g: Glyph) = Badge(g, g.toString)
    val api             = List(Glyph.Rest, Glyph.GraphQl, Glyph.Grpc, Glyph.Event, Glyph.Batch)
    val marks           = Glyph.values.toList.filterNot(api.contains)
    SceneGraph(
      "Every sign",
      List(
        leaf("geo", "Building block", ShapeType.ApplicationService).copy(badges = marks.take(6).map(badge)),
        leaf("geo2", "Another block", ShapeType.ApplicationService).copy(badges = marks.slice(6, 12).map(badge)),
        leaf("api", "Order API", ShapeType.ApplicationComponent).copy(badges = marks.slice(12, 16).map(badge)),
        leaf("gap", "Legacy checkout", ShapeType.ApplicationComponent).copy(badges = marks.drop(16).map(badge)),
        leaf("shop", "Web Shop", ShapeType.ApplicationComponent).copy(tag = Some(CornerTag("I", "#2E7D32"))),
        leaf("stock", "Stock Management Service", ShapeType.ApplicationComponent, Some("[IT System: Java]"))
          .copy(tag = Some(CornerTag("M", "#FFC107"))),
      ),
      List(
        edge("e1", "shop", "api", RelationshipType.Flow, Some("orders")).copy(sign = Some(Glyph.Rest)),
        edge("e2", "api", "stock", RelationshipType.Flow).copy(sign = Some(Glyph.GraphQl)),
        edge("e3", "stock", "gap", RelationshipType.Flow).copy(sign = Some(Glyph.Event), colour = Some("#1F6FEB")),
        edge("e4", "gap", "geo", RelationshipType.Flow).copy(sign = Some(Glyph.Batch)),
        edge("e5", "geo", "geo2", RelationshipType.Serving).copy(sign = Some(Glyph.Grpc)),
      ),
      legend = Some(
        Legend(
          "Signs",
          Nil,
          List(
            LegendSection("Marks", marks.map(g => LegendItem.Mark(badge(g)))),
            LegendSection("API styles", api.map(g => LegendItem.Mark(badge(g)))),
            LegendSection(
              "TIME",
              List(
                LegendItem.Tagged(CornerTag("I", "#2E7D32"), "Invest"),
                LegendItem.Tagged(CornerTag("M", "#FFC107"), "Migrate"),
                LegendItem.Tagged(CornerTag("C2", "#1A237E"), "Confidential"),
              ),
            ),
          ),
        )
      ),
    )

  end signs

  // townplanner's sample return (TOWN-68, APP-2262): two lanes, a boundary timer with its
  // own branch, a decision, tasks with systems and data; its tasks' names wrapped over
  // three and four lines, and it came out 16 cm wide
  val returns: Activity.Graph =
    import Activity.*
    Graph(
      "Handle a return",
      List(
        Lane("customer", "Customer", Some(ShapeType.BusinessActor)),
        Lane("service", "Customer service", Some(ShapeType.BusinessRole)),
      ),
      List(
        Node("requested", "Return requested", Kind.Start(Trigger.Message), Some("customer")),
        Node("send", "Send the parcel back", Kind.Work(Task.User), Some("customer"), Some("[Web storefront]")),
        Node(
          "inspect",
          "Inspect the parcel",
          Kind.Work(Task.User),
          Some("service"),
          data = List(Datum("Order")),
        ),
        Node(
          "days",
          "Ten days pass",
          Kind.Boundary(Trigger.Timer, interrupting = false),
          Some("service"),
          host = Some("inspect"),
        ),
        Node("reminded", "Customer reminded", Kind.Intermediate(Trigger.Message, throwing = true), Some("service")),
        Node("sent", "Reminder sent", Kind.End(Trigger.Plain), Some("service")),
        Node("fit", "Can it be resold?", Kind.Gateway(Gate.Exclusive), Some("service")),
        Node(
          "restock",
          "Restock the item",
          Kind.Work(Task.Service),
          Some("service"),
          Some("[Catalogue service]"),
          data = List(Datum("Product", output = true)),
        ),
        Node("dispose", "Dispose of the item", Kind.Work(Task.Manual), Some("service")),
        Node(
          "refund",
          "Refund to the original tender",
          Kind.Work(Task.Service),
          Some("service"),
          Some("[Checkout service]"),
          data = List(Datum("Order", output = true)),
        ),
        Node("refunded", "Refunded", Kind.End(Trigger.Plain), Some("service")),
      ),
      List(
        Flow("requested", "send"),
        Flow("send", "inspect"),
        Flow("days", "reminded"),
        Flow("reminded", "sent"),
        Flow("inspect", "fit"),
        Flow("fit", "restock", Some("yes")),
        Flow("fit", "dispose", Some("no"), default = true),
        Flow("restock", "refund"),
        Flow("dispose", "refund"),
        Flow("refund", "refunded"),
      ),
    )

  end returns

  // a shipment at BPMN's Analytic level: a pool drawn as a black box that messages go
  // to and come from, a non-interrupting timer and an error on the edge of a task and
  // of an expanded sub-process, a multi-instance task inside it, a transaction, a
  // compensation with its association, a throwing signal, data beside a task, and an
  // event sub-process under the rest
  val shipment: Activity.Graph =
    import Activity.*
    Graph(
      "Ship an order",
      List(
        Lane("customer", "Customer", Some(ShapeType.BusinessActor)),
        Lane("sales", "Sales", Some(ShapeType.BusinessRole)),
        Lane("warehouse", "Warehouse", Some(ShapeType.BusinessRole)),
      ),
      List(
        Node("received", "Order received", Kind.Start(Trigger.Message), Some("sales")),
        Node(
          "confirm",
          "Confirm the order",
          Kind.Work(Task.User),
          Some("sales"),
          Some("[CRM]"),
          data = List(Datum("Order")),
        ),
        Node(
          "late",
          "48 hours",
          Kind.Boundary(Trigger.Timer, interrupting = false),
          Some("sales"),
          host = Some("confirm"),
        ),
        Node("remind", "Remind sales", Kind.Work(Task.Send), Some("sales")),
        Node("reminded", "", Kind.End(Trigger.Plain), Some("sales")),
        Node("pack", "Pick and pack", Kind.Container(Frame.Sub), Some("warehouse")),
        Node("p0", "", Kind.Start(Trigger.Plain), parent = Some("pack")),
        Node("pick", "Pick an item", Kind.Work(Task.Manual), markers = List(Marker.Parallel), parent = Some("pack")),
        Node("box", "Pack the parcel", Kind.Work(Task.User), parent = Some("pack")),
        Node("p1", "", Kind.End(Trigger.Plain), parent = Some("pack")),
        Node("stock", "Out of stock", Kind.Boundary(Trigger.Error), Some("warehouse"), host = Some("pack")),
        Node("sorry", "Tell the customer", Kind.Work(Task.Send), Some("sales")),
        Node("cancelled", "Order cancelled", Kind.End(Trigger.Terminate), Some("sales")),
        Node(
          "ship",
          "Ship the parcel",
          Kind.Work(Task.Service),
          Some("warehouse"),
          Some("[WMS]"),
          data = List(Datum("Stock", store = true, output = true)),
        ),
        Node("undo", "Lost in transit", Kind.Boundary(Trigger.Compensation), Some("warehouse"), host = Some("ship")),
        Node(
          "recall",
          "Recall the parcel",
          Kind.Work(Task.Manual),
          Some("warehouse"),
          markers = List(Marker.Compensation),
        ),
        Node("shipped", "Parcel shipped", Kind.Intermediate(Trigger.Signal, throwing = true), Some("warehouse")),
        Node("invoice", "Send the invoice", Kind.Work(Task.Send), Some("sales")),
        Node("done", "Order complete", Kind.End(Trigger.Message), Some("sales")),
        Node("ask", "Status asked", Kind.Container(Frame.Event), Some("sales")),
        Node("q0", "Customer asks", Kind.Start(Trigger.Message, interrupting = false), parent = Some("ask")),
        Node("answer", "Answer the customer", Kind.Work(Task.User), parent = Some("ask")),
        Node("q1", "", Kind.End(Trigger.Plain), parent = Some("ask")),
      ),
      List(
        Flow("received", "confirm"),
        Flow("late", "remind"),
        Flow("remind", "reminded"),
        Flow("confirm", "pack"),
        Flow("p0", "pick"),
        Flow("pick", "box"),
        Flow("box", "p1"),
        Flow("stock", "sorry"),
        Flow("sorry", "cancelled"),
        Flow("pack", "ship"),
        Flow("ship", "shipped"),
        Flow("shipped", "invoice"),
        Flow("invoice", "done"),
        Flow("q0", "answer"),
        Flow("answer", "q1"),
      ),
      List(
        Message("customer", "received", Some("order")),
        Message("sorry", "customer", Some("apology")),
        Message("invoice", "customer", Some("invoice")),
      ),
      List(Association("undo", "recall")),
    )

  end shipment

  // every trigger caught, thrown, at the start and at the end, every gateway and every
  // task with its markers: one lane each, the symbols one under the other
  val symbols: Activity.Graph =
    import Activity.*
    def chain(lane: String, nodes: List[Node]) =
      (nodes.map(_.copy(lane = Some(lane))), nodes.zip(nodes.drop(1)).map((a, b) => Flow(a.id, b.id)))
    val ts                                     = Trigger.values.toList
    val parts                                  = List(
      chain(
        "start",
        ts.filterNot(t => List(Trigger.Terminate, Trigger.Link, Trigger.Cancel).contains(t)).map(t =>
          Node(s"s$t", t.toString, Kind.Start(t))
        ),
      ),
      chain(
        "catch",
        ts.filterNot(_ == Trigger.Terminate).map(t =>
          Node(s"c$t", t.toString, Kind.Boundary(t, interrupting = t != Trigger.Timer))
        ),
      ),
      chain("throw", ts.map(t => Node(s"t$t", t.toString, Kind.Intermediate(t, throwing = true)))),
      chain("end", ts.map(t => Node(s"e$t", t.toString, Kind.End(t)))),
      chain("gate", Gate.values.toList.map(g => Node(s"g$g", g.toString, Kind.Gateway(g)))),
      chain(
        "task",
        Task.values.toList.map(t => Node(s"k$t", t.toString, Kind.Work(t))) ++
        Marker.values.toList.map(m => Node(s"m$m", m.toString, Kind.Work(Task.Plain), markers = List(m))),
      ),
    )
    Graph(
      "Symbols",
      List("start", "catch", "throw", "end", "gate", "task").map(l => Lane(l, l)),
      parts.flatMap(_._1).map(n =>
        n.kind match
          case Kind.Boundary(t, _) => n.copy(kind = Kind.Intermediate(t))
          case _                   => n
      ),
      parts.flatMap(_._2),
    )

  end symbols

  val graphs: List[(String, SceneGraph)] =
    List(
      "landscape" -> DiagramSpec.landscape,
      "layers" -> layers,
      "relationships" -> relationships,
      "badged" -> DiagramSpec.badged,
      "logos" -> LogoSpec.logos,
      "legend" -> LegendSpec.legended,
      "signs" -> signs,
      "retiring" -> EncodingSpec.retiring,
      "gap" -> EncodingSpec.gap,
      "volumes" -> EncodingSpec.volumes,
      "heat" -> EncodingSpec.heatMap,
    )

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
        ("shipment", Right(Activity.draw(shipment))),
        ("symbols", Right(Activity.draw(symbols, Activity.Options(pageWidth = 30)))),
        ("claim", Right(Activity.draw(claim))),
        (
          "returns-innovenso",
          Right(Activity.draw(returns, Activity.Options(Diagram.Style(typeface = Typeface.montserrat)))),
        ),
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
            Tikz.files(d).foreach((file, bytes) => Files.write(dir.resolve(file), bytes))
            Files.writeString(dir.resolve(s"$name.svg"), Svg.document(d, palette), StandardCharsets.UTF_8)
            Files.write(dir.resolve(s"$name.png"), Png.image(d, 200, palette))
          case _                => ()
      assertTrue(drawn.forall(_._2.exists(_.overflow == 0)))

end DiagramGallery
