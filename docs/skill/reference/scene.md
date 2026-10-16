# scene — ArchiMate in 3D

Package `fish.genius.uml.scene` (the `genius-uml-scene` module). DSL.md, "Scenes:
ArchiMate in 3D", has every detail.

```scala
SceneLayout.layout(graph): Either[SceneError, Scene]   // ELK, then the 3D placement
scene.toJsonPretty                                     // zio-json
SceneBuilder.blenderScript: Either[BuilderError, String] // archimate3d_blender.py
SceneBuilder.threeJs: Either[BuilderError, String]       // the same shapes for a browser
```

```bash
blender -b -P archimate3d_blender.py -- --view scene.json --render scene.png
blender -b -P archimate3d_blender.py -- --view scene.json --highlight "Order System" --render hl.png
blender -b -P archimate3d_blender.py -- --view scene.json --flyover "Place an order" --animation flow.mp4
```

- A node's `children` stand on its plinth. Layers are planes (`ShapeGroup`).
- A `SceneFlow` is a named list of steps the flyover flies along.
- A `Passport` beside a node is drawn on a glass pane; `PaneLayout` keeps floor for it.
- The JSON is a contract with the builder: a field renamed in `Scene.scala` is renamed
  in `archimate3d_blender.py`; `SceneContractSpec` pins both.
