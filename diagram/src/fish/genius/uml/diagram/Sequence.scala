package fish.genius.uml.diagram

import fish.genius.uml.diagram.Diagram.{Line, PT}
import fish.genius.uml.dsl.archimate.ShapeType
import fish.genius.uml.scene.Scene

// A sequence of messages between the parts of a system, drawn in the look of the
// ArchiMate diagrams (Diagram), so a dossier sets both in one style. Each part has a
// head at the top, a box in its layer's colour with its icon in the corner, and a fine
// dashed lifeline under it in that box's edge colour; the parts of one system stand on
// a field with the system's name in its header. A message is a horizontal line from
// one lifeline to another, its number and its label above it: a call ends in a filled
// arrowhead, a reply is dashed and ends in an open one, a signal is solid and ends in an
// open one. A loop is a frame round its messages with a tag that says loop and its
// condition beside it.
//
// The columns are set as far apart as the labels between them need, and no further; a
// label wraps rather than push the diagram wider than the page. Units are centimetres,
// x to the right and y up, as in Diagram, and the three writers draw the result.
object Sequence:

  // a system the parts stand in, drawn as a container of its shape (or as a grouping
  // without one)
  final case class Group(
    name: String,
    shape: Option[ShapeType] = None)

  // a part: its id, its name, the shape it takes (an actor, a component), the line
  // under its name (its type), and the system it belongs to
  final case class Lifeline(
    id: String,
    name: String,
    shape: ShapeType,
    caption: Option[String] = None,
    group: Option[Group] = None)

  enum Kind derives CanEqual:
    case Call, Reply, Signal

  enum Step derives CanEqual:

    // a message, with the line set under its label in a quieter voice (its technology,
    // what it carries)
    case Send(
      from: String,
      to: String,
      label: String,
      kind: Kind = Kind.Call,
      detail: Option[String] = None)

    case Loop(
      label: String,
      steps: List[Step])

  final case class Graph(
    name: String,
    lifelines: List[Lifeline],
    steps: List[Step])

  final case class Options(
    style: Diagram.Style = Diagram.Style(),
    pageWidth: Double = 13.0,
    numbered: Boolean = true,
    messageSize: Double = 6.4,
    detailSize: Double = 5.6,
    labelWidth: Double = 4.4)

  def draw(graph: Graph, options: Options = Options()): Diagram.Drawing =
    val measure                                     = Measure(options.style.typeface)
    // each try is laid out twice: the second time each label wraps as wide as the columns the first
    // one
    // set, which never moves them further apart
    def attempt(head: Double, room: Double): Layout =
      val first = Layout(graph, options, measure, head, room, None)
      Layout(graph, options, measure, head, room, Some(first.xs))
    // the widest heads, then the widest labels, that keep within the page; when none
    // do, the narrowest drawing, with the widest heads and labels that make it so
    val tries                                       =
      for
        head <- HEAD_ROOMS
        room <- options.labelWidth ::
        Iterator
          .iterate(options.labelWidth - 0.4)(_ - 0.4)
          .takeWhile(_ >= MIN_LABEL)
          .toList
      yield attempt(head, room)
    val narrowest                                   = tries.map(_.width).min
    val laid                                        =
      tries
        .find(_.width <= options.pageWidth)
        .orElse(tries.find(_.width <= narrowest + 0.01))
        .getOrElse(tries.last)
    Diagram.Drawing(
      graph.name,
      laid.prims,
      Scene(graph.name, laid.width, laid.height, Nil, Nil),
      options.style,
      measure.family,
      laid.overflow,
    )

  end draw

  private val MIN_LABEL   = 2.4
  private val COLUMN_GAP  = 0.4  // between two heads
  private val FIELD_PAD   = 0.16 // a system's field round its heads
  private val FIELD_GAP   = 0.3  // between two fields, or a field and a head
  private val LABEL_PAD   = 0.16 // a label's air from the lifeline it starts at
  private val HEAD_MIN    = 0.2  // a head is at least this much wider than its room
  // the widest a line of a head's name or caption is set: the first, unless the
  // diagram is then wider than the page
  private val HEAD_ROOMS  = List(1.6, 1.35, 1.1)
  private val ICON        = 0.85 // a head's icon, against a box's in Diagram
  private val HEAD_HEIGHT = 0.9
  private val FIRST       = 0.4  // from the heads to the first message
  private val ROW         = 0.3  // under a message
  private val RISE        = 0.07 // from a label's last line to its arrow
  private val SELF_W      = 0.45 // a message to itself goes this far out, and
  private val SELF_H      = 0.3  // comes back this much lower
  private val INSET       = 0.32 // a loop's frame round the lifelines it spans
  private val NEST        = 0.14 // and that much more round a frame inside it
  private val TAG_GAP     = 0.16 // from a frame's tag to its first message
  private val END         = 0.3  // a lifeline goes on this far under the last message
  private val NUMBER_GAP  = 0.1  // between a message's number and its label
  private val SPOT        = 0.05 // the air a lifeline keeps round a word on it

  // a message or a frame's edge, in the order they are drawn down the page
  private enum Row:

    case Message(
      number: Int,
      send: Step.Send,
      depth: Int)

    case Open(
      label: String,
      span: (Int, Int),
      depth: Int)

    case Close(depth: Int)

  // a message's label set: its lines, how far they are indented past the number, and
  // how wide and tall it all is
  final private case class Said(
    number: Option[Line],
    lines: List[Line],
    indent: Double,
    w: Double,
    h: Double,
    room: Double,
    stretch: Stretch)

  // the stretch of a message a label stands on: from a lifeline or a field's edge
  // (x of column `left`, moved by `leftBy`) to the next one on the right; `air` is the
  // room kept at its left for an arrowhead that ends there
  final private case class Stretch(
    left: Int,
    leftBy: Double,
    right: Int,
    rightBy: Double,
    air: Double)

  final private class Layout(
    graph: Graph,
    options: Options,
    measure: Measure,
    headRoom: Double,
    room: Double,
    seed: Option[Vector[Double]]):

    private val style = options.style

    private val lifelines = graph.lifelines.distinctBy(_.id)
    private val column    = lifelines.map(_.id).zipWithIndex.toMap

    // the messages between known lifelines, numbered, with the loops round them; a
    // loop with nothing in it is left out
    private val rows: List[Row] =
      var n                                              = 0
      def walk(steps: List[Step], depth: Int): List[Row] = steps.flatMap:
        case s: Step.Send if column.contains(s.from) && column.contains(s.to) =>
          n += 1
          List(Row.Message(n, s, depth))
        case _: Step.Send                                                     => Nil
        case Step.Loop(label, inner)                                          =>
          val body = walk(inner, depth + 1)
          val cols = body.flatMap:
            case Row.Message(_, s, _)   => List(column(s.from), column(s.to))
            case Row.Open(_, (a, b), _) => List(a, b)
            case _                      => Nil
          if cols.isEmpty then Nil
          else Row.Open(label, (cols.min, cols.max), depth) :: body ::: List(Row.Close(depth))
      walk(graph.steps, 0)

    end rows

    // how deep the loops go inside each frame, so an outer frame clears an inner one
    private val inner: Map[Int, Int] =
      rows.zipWithIndex.collect { case (o: Row.Open, i) => i -> o }.map: (i, o) =>
        val body = rows.drop(i + 1).takeWhile:
          case Row.Close(d) => d != o.depth
          case _            => true
        i -> body.collect { case Row.Open(_, _, d) => d - o.depth }.maxOption.getOrElse(0)
      .toMap

    private def inset(depthBelow: Int): Double = INSET + depthBelow * NEST

    // the heads: each line of a name and caption clear of the icon on both sides, so it
    // stays centred on the lifeline; the icon is a little smaller and closer to the
    // corner than on a box in Diagram, as a head is narrower
    private val icon   = style.iconSize * ICON
    private val corner = style.padding * 0.75
    private val clear  = 2 * (corner + icon + 0.05)

    private val heads: List[(Lifeline, List[Line], Double)] = lifelines.map: l =>
      val lines =
        measure
          .wrap(l.name, headRoom, style.nameSize, Weight.Medium)
          .map(Line(_, style.nameSize, Weight.Medium)) ++
        l.caption.toList.flatMap(c =>
          measure
            .wrap(c, headRoom, style.captionSize)
            .map(Line(_, style.captionSize, tone = Emphasis.Muted))
        )
      val w     =
        (headRoom + HEAD_MIN :: lines.map(t => measure.width(t.text, t.size, t.weight) + clear)).max
      (l, lines, Measure.round(w))

    private val headH = math.max(
      HEAD_HEIGHT,
      heads.map(_._2.map(t => style.lead(t.size)).sum).maxOption.getOrElse(0.0) +
      2 * style.padding,
    )

    private def said(m: Row.Message): Said =
      val number  = Option.when(options.numbered)(
        Line(m.number.toString, options.messageSize, Weight.SemiBold, Emphasis.Muted)
      )
      val indent  = number.fold(0.0)(t => measure.width(t.text, t.size, t.weight) + NUMBER_GAP)
      // as wide as its stretch already is, and at least `room`
      val (a, b)  = (column(m.send.from), column(m.send.to))
      val stretch = stretchOf(a, b)
      val span    =
        if a == b then
          if a + 1 < apart.size then apart(a + 1) - apart(a) - 3 * LABEL_PAD - SELF_W else 0.0
        else
          apart(stretch.right) + stretch.rightBy - apart(stretch.left) - stretch.leftBy -
          2 * LABEL_PAD - stretch.air
      val wide    = math.max(room, span)
      val lines   =
        measure
          .wrap(m.send.label, wide - indent, options.messageSize)
          .map(Line(_, options.messageSize)) ++
        m.send.detail.toList.flatMap(d =>
          measure
            .wrap(d, wide - indent, options.detailSize, Weight.Italic)
            .map(Line(_, options.detailSize, Weight.Italic, Emphasis.Muted))
        )
      val w       = indent +
        lines.map(t => measure.width(t.text, t.size, t.weight)).maxOption
          .getOrElse(0.0)
      Said(number, lines, indent, w, lines.map(t => style.lead(t.size)).sum, wide, stretch)

    end said

    // A message's label keeps off the edges of the fields it crosses: it stands on the
    // longest stretch between them (the one nearest the sender of equals), which the
    // columns then widen until the label fits. A message to itself has the stretch to
    // the right of its lifeline.
    private def stretchOf(a: Int, b: Int): Stretch =
      val (lo, hi)           = (math.min(a, b), math.max(a, b))
      def half(i: Int)       = heads(i)._3 / 2 + FIELD_PAD
      val edges              = runs
        .flatMap:
          (
            _,
            r0,
            r1,
          ) =>
            List(
              Option.when(lo < r0 && r0 <= hi)((r0, -half(r0))),
              Option.when(lo <= r1 && r1 < hi)((r1, half(r1))),
            ).flatten
        .sortBy((c, by) => apart(c) + by)
      val bounds             = ((lo, 0.0) :: edges) ::: List((hi, 0.0))
      val stretches          = bounds.zip(bounds.drop(1)).map: (l, r) =>
        Stretch(l._1, l._2, r._1, r._2, if a > b && l == (lo, 0.0) then 0.2 else 0.0)
      def length(t: Stretch) = apart(t.right) + t.rightBy - apart(t.left) - t.leftBy - t.air
      val movable            = stretches.filter(t => t.left != t.right)
      val nearSender         = if a <= b then movable else movable.reverse
      if lo == hi then Stretch(lo, 0.0, lo, 0.0, 0.0)
      else nearSender.maxByOption(length).getOrElse(stretches.maxBy(length))

    end stretchOf

    private lazy val labels: Map[Int, Said] =
      rows.collect { case m: Row.Message => m.number -> said(m) }.toMap

    // a loop's tag, and its condition beside it, wrapped as a label is
    private def tag(label: String): (Double, Double) =
      val tagW   = measure.width("loop", options.detailSize + 0.4, Weight.SemiBold) + 0.26
      val guardW = guard(label).map(measure.width(_, options.detailSize, Weight.Italic)).max
      (tagW, guardW)

    private def guard(label: String): List[String] =
      measure.wrap(s"[$label]", room, options.detailSize, Weight.Italic) match
        case Nil   => List("[]")
        case lines => lines

    // the runs of neighbouring lifelines in one system, each a field
    private lazy val runs: List[(Group, Int, Int)] =
      lifelines.zipWithIndex
        .foldLeft(List.empty[(Option[Group], Int, Int)]):
          case ((g, a, _) :: done, (l, i)) if g.isDefined && g == l.group => (g, a, i) :: done
          case (done, (l, i))                                             => (l.group, i, i) :: done
        .reverse
        .collect { case (Some(g), a, b) => (g, a, b) }

    private def runOf(i: Int): Option[(Group, Int, Int)] = runs.find(
      (
        _,
        a,
        b,
      ) => a <= i && i <= b
    )

    // where the lifelines stand with nothing but the heads and fields to keep apart, or
    // where an earlier round set them
    private lazy val apart: Vector[Double] = seed.getOrElse(alone)

    private lazy val alone: Vector[Double] =
      val n    = heads.size
      val base = Array.fill(n)(0.0)
      for i <- 1 until n do
        val (_, _, wa) = heads(i - 1)
        val (_, _, wb) = heads(i)
        val gap        = (runOf(i - 1), runOf(i)) match
          case (a, b) if a.isDefined && a == b => COLUMN_GAP
          case (a, b)                          =>
            a.fold(0.0)(_ => FIELD_PAD) + b.fold(0.0)(_ => FIELD_PAD) + FIELD_GAP
        base(i) = base(i - 1) + wa / 2 + wb / 2 + gap
      base.toVector

    // where each lifeline stands: as close as the heads and fields allow, then as far
    // apart as each label needs, the shortest spans first so a long message only
    // widens what the short ones between its ends did not already
    val xs: Vector[Double] =
      val n     = heads.size
      val base  = apart.toArray
      val needs = rows.zipWithIndex.flatMap:
        case (m: Row.Message, _) =>
          val (a, b) = (column(m.send.from), column(m.send.to))
          val said   = labels(m.number)
          val w      = said.w + 2 * LABEL_PAD
          val t      = said.stretch
          if a == b then
            Option.when(a + 1 < n)((a, a + 1, w + SELF_W + LABEL_PAD))
          else if t.left == t.right then None
          else Some((t.left, t.right, w + t.air + t.leftBy - t.rightBy))
        case (o: Row.Open, i)    =>
          val (tw, gw) = tag(o.label)
          val (a, b)   = o.span
          val pad      = 2 * inset(inner.getOrElse(i, 0))
          Option.when(a < b)((a, b, tw + gw + 0.3 - pad))
        case _                   => None
      needs.sortBy(
        (
          a,
          b,
          _,
        ) => b - a
      ).foreach:
        (
          a,
          b,
          need,
        ) =>
          val short = need - (base(b) - base(a))
          if short > 0 then for k <- b until n do base(k) += short
      base.toVector

    end xs

    // what reaches past the outermost lifelines: a head, a field, a frame, a message
    // to itself, a label
    private val left: Double =
      val headLeft  = heads.zip(xs).map { case ((l, _, w), x) =>
        x - w / 2 - (if runOf(column(l.id)).exists(_._2 == column(l.id)) then FIELD_PAD else 0)
      }
      val frameLeft = rows.zipWithIndex.collect { case (o: Row.Open, i) =>
        xs(o.span._1) - inset(inner.getOrElse(i, 0))
      }
      (headLeft ++ frameLeft).minOption.getOrElse(0.0)

    private val right: Double =
      val last      = xs.size - 1
      val headRight = heads.zip(xs).map { case ((l, _, w), x) =>
        x + w / 2 + (if runOf(column(l.id)).exists(_._3 == column(l.id)) then FIELD_PAD else 0)
      }
      val frames    = rows.zipWithIndex.collect { case (o: Row.Open, i) =>
        val (tw, gw) = tag(o.label)
        math.max(xs(o.span._2) + inset(inner.getOrElse(i, 0)), xs(o.span._1) + tw + gw)
      }
      val selves    = rows.collect {
        case m: Row.Message if m.send.from == m.send.to                                 =>
          val x = xs(column(m.send.from))
          x + math.max(SELF_W, LABEL_PAD + labels(m.number).w) + 0.1
        case m: Row.Message if column(m.send.from) == last || column(m.send.to) == last =>
          xs(last)
      }
      (headRight ++ frames ++ selves).maxOption.getOrElse(0.0)

    end right

    val width: Double = right - left

    // the top band where the fields' names go
    private val band: Double =
      if runs.isEmpty then 0.0 else style.lead(style.nameSize) + 1.4 * style.padding

    private def fieldRole(g: Group): Role =
      g.shape.fold(Role.Group)(s => Role.Container(Notation.layer(s.group)))

    // down the page: each message's label and arrow, each frame's top and bottom
    private val (drawnRows, bottom): (List[Prim], Double) =
      val headsBottom = -band - headH
      var y           = headsBottom - FIRST
      val opened      = scala.collection.mutable.Stack.empty[(Row.Open, Int, Double)]
      val frames      = List.newBuilder[Prim]
      val lines       = List.newBuilder[Prim]
      val words       = List.newBuilder[Prim]
      rows.zipWithIndex.foreach:
        case (o: Row.Open, i)    =>
          opened.push((o, i, y))
          y -= style.lead(options.detailSize + 0.4) + 0.12 + TAG_GAP +
          (guard(o.label).size - 1) * style.lead(options.detailSize)
        case (_: Row.Close, _)   =>
          val (o, i, top) = opened.pop()
          val bottomY     = y + ROW - 0.12
          val pad         = inset(inner.getOrElse(i, 0))
          val x0          = xs(o.span._1) - pad
          val (tw, gw)    = tag(o.label)
          val x1          = math.max(xs(o.span._2) + pad, xs(o.span._1) + tw + gw)
          val th          = style.lead(options.detailSize + 0.4) + 0.12
          val c           = 0.1
          frames +=
            Prim.Poly(List((x0, bottomY), (x1, bottomY), (x1, top), (x0, top)), closed = true)
          frames += Prim.Poly(
            List(
              (x0, top),
              (x0 + tw, top),
              (x0 + tw, top - th + c),
              (x0 + tw - c, top - th),
              (x0, top - th),
            ),
            closed = true,
            Ink.Paper,
          )
          val baseline    = top - th / 2 - 0.35 * (options.detailSize + 0.4) * PT
          words += Prim.Text(
            (x0 + 0.13, baseline),
            "loop",
            options.detailSize + 0.4,
            Weight.SemiBold,
            centred = false,
          )
          guard(o.label).zipWithIndex.foreach: (line, k) =>
            words += Prim.Text(
              (x0 + tw + 0.1, baseline - k * style.lead(options.detailSize)),
              line,
              options.detailSize,
              Weight.Italic,
              Emphasis.Muted,
              centred = false,
            )
          y = bottomY - 0.18
        case (m: Row.Message, _) =>
          val (a, b)       = (column(m.send.from), column(m.send.to))
          val s            = labels(m.number)
          val (xa, xb)     = (xs(a), xs(b))
          val at           =
            if a == b then xa + LABEL_PAD
            else xs(s.stretch.left) + s.stretch.leftBy + s.stretch.air + LABEL_PAD
          val top          = y
          s.number.foreach: num =>
            words ++= Diagram.stack(at, top, List(num), style, centred = false)
          words ++= Diagram.stack(at + s.indent, top, s.lines, style, centred = false)
          val arrowY       = top - s.h - RISE
          val (dash, head) = m.send.kind match
            case Kind.Call   => (Dash.Solid, Head.FilledTriangle)
            case Kind.Reply  => (Dash.Dashed, Head.Barb)
            case Kind.Signal => (Dash.Solid, Head.Barb)
          if a == b then
            val back = arrowY - SELF_H
            lines += Prim.Connector(
              List((xa, arrowY), (xa + SELF_W, arrowY), (xa + SELF_W, back), (xa, back)),
              dash,
              None,
              Some(head),
            )
            y = back - ROW
          else
            lines += Prim.Connector(List((xa, arrowY), (xb, arrowY)), dash, None, Some(head))
            y = arrowY - ROW
      (frames.result() ++ lines.result() ++ words.result(), y + ROW - END)

    end val

    val height: Double = -bottom

    // where the words over the lifelines stand, each with a little air round it
    private val spots: List[(Double, Double, Double, Double)] = drawnRows.collect:
      case Prim.Text((x, y), text, size, weight, _, false, _) =>
        val w = measure.width(text, size, weight)
        (x - SPOT, y - 0.3 * size * PT - SPOT, x + w + SPOT, y + 0.78 * size * PT + SPOT)

    // a lifeline from `from` down to `to`, broken where a word stands on it, so a
    // label reads clean without a patch behind it
    private def gaps(
      x: Double,
      from: Double,
      to: Double,
    ): List[(Double, Double)] =
      val cuts = spots
        .filter(
          (
            x0,
            _,
            x1,
            _,
          ) => x0 <= x && x <= x1
        )
        .map(
          (
            _,
            y0,
            _,
            y1,
          ) => (y1, y0)
        )
        .sortBy(-_._1)
      var y    = from
      val out  = List.newBuilder[(Double, Double)]
      for (top, low) <- cuts if low < y && y > to do
        if top < y then out += ((y, math.max(top, to)))
        y = low
      if y > to then out += ((y, to))
      out.result().filter((a, b) => a - b > 0.02)

    end gaps

    // the fields, the lifelines and the heads, drawn behind the rows
    private val stage: List[Prim] =
      val fields     = runs.flatMap:
        (
          g,
          a,
          b,
        ) =>
          val x0   = xs(a) - heads(a)._3 / 2 - FIELD_PAD
          val x1   = xs(b) + heads(b)._3 / 2 + FIELD_PAD
          val role = fieldRole(g)
          val box  = role match
            case Role.Container(layer) =>
              Prim.Box(x0, bottom, x1 - x0, -bottom, 0.04, Ink.Layer(layer), role = role)
            case _                     =>
              Prim.Box(x0, bottom, x1 - x0, -bottom, 0.04, Ink.Empty, Dash.Dashed, role = role)
          val icon = g.shape.toList.flatMap: s =>
            Diagram.place(
              Notation.icon(s),
              x1 - style.padding - style.iconSize,
              -style.padding - style.iconSize * 0.9,
              style.iconSize * 0.9,
              Role.Mark(Notation.layer(s.group)),
            )
          val name = Diagram.stack(
            x0 + style.padding,
            -0.7 * style.padding,
            List(Line(g.name, style.nameSize, Weight.SemiBold)),
            style,
            centred = false,
            backdrop = Some(role),
          )
          (box :: icon) ++ name
      val top        = -band
      val people     = heads.zip(xs).map { case ((l, lines, w), x) =>
        val layer    = Notation.layer(l.shape.group)
        val radius   = if Notation.outline(l.shape) == Notation.Outline.Rounded then 0.16 else 0.04
        val lifeline = gaps(x, top - headH, bottom).map: (a, b) =>
          Prim.Poly(List((x, a), (x, b)), closed = false, dash = Dash.Dashed, role = Role.Mark(layer))
        val box      = Prim.Box(
          x - w / 2,
          top - headH,
          w,
          headH,
          radius,
          Ink.Layer(layer),
          role = Role.Element(layer),
        )
        val mark     = Diagram.place(
          Notation.icon(l.shape),
          x + w / 2 - corner - icon,
          top - corner - icon,
          icon,
          Role.Mark(layer),
        )
        val textH    = lines.map(t => style.lead(t.size)).sum
        val text     = Diagram.stack(x, top - (headH - textH) / 2, lines, style, centred = true)
        (lifeline, box :: mark, text)
      }
      val lifelinesP = people.flatMap(_._1)
      val boxes      = people.flatMap(_._2)
      val texts      = people.flatMap(_._3)
      fields.filterNot(_.isInstanceOf[Prim.Text]) ++ lifelinesP ++ boxes ++
      fields.filter(_.isInstanceOf[Prim.Text]) ++ texts

    end stage

    // the frames and arrows over the lifelines, the words on top
    val prims: List[Prim] =
      val (words, rest) = drawnRows.partition(_.isInstanceOf[Prim.Text])
      val (back, front) = stage.partition(!_.isInstanceOf[Prim.Text])
      back ++ rest ++ front ++ words

    // a label's lines wider than their room: only a single word longer than it
    val overflow: Int =
      labels.values.toList.map: s =>
        s.lines.count(t => measure.width(t.text, t.size, t.weight) > s.room - s.indent + 1e-6)
      .sum

  end Layout

end Sequence
