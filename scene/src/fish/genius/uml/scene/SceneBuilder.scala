package fish.genius.uml.scene

import scala.io.Source
import scala.util.Using

// The two builders that ship in this jar, so a scene and the code that builds it come
// from the same release:
//   - the Blender builder (bpy): every element a plinth (form = aspect, colour = layer)
//     with its icon as a sculpture and its name on a placard; connectors hooked to the
//     ports, so they follow an element that is moved. Run it with
//     `blender -b -P archimate3d_blender.py -- --view scene.json --render scene.png`.
//   - the same shapes in three.js, for a preview in a browser.
object SceneBuilder:

  val BLENDER_SCRIPT = "archimate3d_blender.py"
  val THREE_JS       = "archimate3d.js"

  def blenderScript: Either[SceneError, String] = resource(BLENDER_SCRIPT)

  def threeJs: Either[SceneError, String] = resource(THREE_JS)

  private def resource(name: String): Either[SceneError, String] =
    Option(getClass.getResourceAsStream(s"/fish/genius/uml/scene/$name"))
      .toRight(SceneError.ResourceMissing(name))
      .flatMap: stream =>
        Using(Source.fromInputStream(stream, "UTF-8"))(_.mkString).toEither.left
          .map(_ => SceneError.ResourceMissing(name))

end SceneBuilder
