package com.sengine.project

import com.sengine.engine.anim.Animation3DClip
import com.sengine.engine.anim.AnimationTrack
import com.sengine.engine.render.TextureProfile
import com.sengine.engine.sprite.SpriteDocument
import com.sengine.engine.sprite.SpriteSheetDoc
import org.json.JSONArray
import org.json.JSONObject

/** Default, valid content for the new Phase 2 text asset kinds. */
object BigEditionAssets {
    val PREFAB = """
{
  "format": "S Engine Prefab",
  "version": 1,
  "name": "StarterPrefab",
  "objects": [
    {
      "id": 1,
      "name": "StarterPrefab",
      "tag": "Untagged",
      "active": true,
      "order": 0,
      "x": 0.0,
      "y": 0.0,
      "rotation": 0.0,
      "scaleX": 1.0,
      "scaleY": 1.0,
      "components": [
        { "type": "SpriteRenderer", "enabled": true, "Color": "#FF4FC3F7" }
      ]
    }
  ]
}
""".trimIndent()

    val MATERIAL = """
{
  "color": "#FF80CBC4",
  "texture": "",
  "specular": 0.65,
  "shininess": 64.0,
  "emission": 0.1,
  "tiling": 1.0,
  "shader": "",
  "shaderParam": 1.0,
  "unlit": false
}
""".trimIndent()

    val ATLAS = """
{
  "texture": "HeroRun.png",
  "columns": 4,
  "rows": 1,
  "frames": {
    "idle": [0.0, 1.0, 0.25, 0.0],
    "step": [0.25, 1.0, 0.5, 0.0],
    "jump": [0.5, 1.0, 0.75, 0.0],
    "fall": [0.75, 1.0, 1.0, 0.0]
  }
}
""".trimIndent()

    val TILEMAP = """
{
  "tileset": "Grass.png",
  "columns": 2,
  "rows": 1,
  "tileSize": 1.0,
  "map": "0,1,0,1;1,1,1,0;0,1,0,0",
  "palette": "#FF6D9E4B,#FF8D6E63,#FF4FC3F7,#FFFFC107",
  "color": "#FFFFFFFF"
}
""".trimIndent()

    val SCENE = """
{
  "name": "Snapshot",
  "gravityX": 0.0,
  "gravityY": -9.81,
  "gravity3D": -9.81,
  "objects": []
}
""".trimIndent()

    val TEXT = """S Engine Big Edition notes.
Use this .txt asset for level data, dialogue, or custom JSON/text content.
""".trimIndent()

    /** Phase 3 validated text asset templates. */
    val TEXTURE_META = TextureProfile.DEFAULT.toJson().toString(2)

    val SPRITE = SpriteDocument(16, 16).apply { addFrame() }.toJson().toString(2)

    val SHEET = SpriteSheetDoc().toJson().toString(2)

    val ANIMATION3D = Animation3DClip("NewAnimation", 30f, true, "root").apply {
        tracks.add(AnimationTrack(
            "root",
            listOf(0f, 1f, 2f),
            listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0.2f, 0f), floatArrayOf(0f, 0f, 0f)),
            listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 15f, 0f), floatArrayOf(0f, 0f, 0f)),
            listOf(floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f)),
        ))
    }.toJson().toString(2)

    val MODELPACK = JSONObject()
        .put("type", "sengine.modelpack")
        .put("version", 1)
        .put("name", "NewModelPack")
        .put("model", "DCCSource.blend")
        .put("renderModel", "RuntimeModel.obj")
        .put("primaryTexture", "ModelAlbedo.png")
        .put("textures", JSONArray(listOf("ModelAlbedo.png")))
        .put("material", "Model.mat")
        .put("animation3d", "Model.anim3d")
        .put("spriteAnimation", "Model.anim")
        .put("scene", "Main")
        .put("object", "NewModelPack")
        .put("status", "template")
        .toString(2)

}
