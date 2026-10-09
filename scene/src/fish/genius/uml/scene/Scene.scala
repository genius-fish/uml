package fish.genius.uml.scene

import zio.json.*

// A graph laid out as a 3D scene: the input of the Blender builder that ships in this
// module (SceneBuilder.blenderScript). Coordinates are scene units on the ground plane,
// x to the right and y away from the viewer, with the origin at the centre of the
// layout. A leaf stands on a plinth of SceneLayout.PLINTH_WIDTH by PLINTH_DEPTH; a
// container's plinth is as large as its children need, and they stand on it.

final case class Scene(
  name: String,
  width: Double,
  depth: Double,
  elements: List[SceneElement],
  relationships: List[SceneRelationship])

// x and y are the plinth's centre, w and d its footprint, z the height it stands at:
// the top of its container's plinth, or 0. `shape` is a name in the builder's catalogue.
final case class SceneElement(
  key: SceneId,
  @jsonField("type") shape: String,
  name: String,
  x: Double,
  y: Double,
  w: Double,
  d: Double,
  z: Double,
  parent: Option[SceneId],
  container: Boolean)

// A connector leaves its source through the middle of one plinth side and arrives at
// the middle of one side of its target; between them it follows `bends`.
final case class SceneRelationship(
  key: SceneId,
  source: SceneId,
  target: SceneId,
  @jsonField("type") kind: String,
  sourcePort: PortName,
  targetPort: PortName,
  bends: List[ScenePoint])

final case class ScenePoint(x: Double, y: Double)

// The four sides of a plinth: north is away from the viewer, south towards it.
enum PortName derives CanEqual:
  case North, South, East, West

object PortName:

  given JsonCodec[PortName] = JsonCodec.string.transformOrFail(
    raw => PortName.values.find(_.toString.equalsIgnoreCase(raw)).toRight(s"no port named $raw"),
    _.toString.toLowerCase,
  )

object ScenePoint:
  given JsonCodec[ScenePoint] = DeriveJsonCodec.gen

object SceneElement:
  given JsonCodec[SceneElement] = DeriveJsonCodec.gen

object SceneRelationship:
  given JsonCodec[SceneRelationship] = DeriveJsonCodec.gen

object Scene:
  given JsonCodec[Scene] = DeriveJsonCodec.gen
