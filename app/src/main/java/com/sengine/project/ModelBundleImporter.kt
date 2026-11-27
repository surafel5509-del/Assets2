package com.sengine.project

import com.sengine.engine.anim.Animation3DClip
import com.sengine.engine.anim.Animation3DImporters
import com.sengine.engine.anim.AnimationClip
import com.sengine.engine.anim.AnimationTrack
import com.sengine.engine.core.Animator
import com.sengine.engine.core.Component
import com.sengine.engine.core.MaterialAsset
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ModelAnimator
import com.sengine.engine.core.Scene
import com.sengine.engine.render.Meshes
import com.sengine.engine.render.ModelFormatRegistry
import com.sengine.engine.render.PngCodec
import com.sengine.engine.render.TextureProfile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream

/** Validation for `.modelpack` conversion/manifest metadata. */
object ModelBundleManifest {
    fun validate(text: String): String? = try {
        val o = JSONObject(text)
        val type = o.optString("type", "sengine.modelpack")
        if (type !in setOf("sengine.modelpack", "modelpack", "sengine.bundle")) "unsupported model pack type"
        else if (o.optString("renderModel", "").isBlank()) "missing renderModel"
        else if (o.optString("primaryTexture", "").isBlank() && o.optJSONArray("textures").let { it == null || it.length() == 0 }) "missing textures"
        else null
    } catch (e: Exception) { e.message ?: "invalid model pack JSON" }
}

data class ModelBundleResult(
    val name: String,
    val archiveName: String,
    val installed: List<String>,
    val sourceModel: String,
    val renderModel: String,
    val textures: List<String>,
    val primaryTexture: String,
    val material: String,
    val animation3D: String,
    val spriteAnimation: String,
    val metadata: String,
    val sceneName: String,
    val objectName: String,
    val categories: List<String>,
    val warnings: List<String>,
)

/**
 * One-click installer for a complete model archive. All archive members become
 * project assets; a render-capable companion mesh, textures, material, 2D clip,
 * 3D clip, texture profiles and a wired scene object are generated when needed.
 *
 * Proprietary DCC binaries are kept as source assets. At runtime the declared
 * (or discovered) OBJ / glTF / GLB / STL / PLY / OFF / DAE / FBX-ASCII / 3DS
 * companion is decoded; this is explicit conversion metadata, not a claim that
 * closed binary Blender/C4D/Maya/3ds Max formats are decoded in the app.
 */
object ModelBundleImporter {
    private val MANIFEST_NAMES = setOf("manifest.json", "bundle.json", "model.json", "package.json", "manifest.modelpack", "bundle.modelpack")
    private val IGNORED = setOf("project.json", "androidmanifest.xml", "thumbs.db", ".ds_store", "desktop.ini")
    private val RENDER_PRIORITY = listOf("glb", "gltf", "obj", "dae", "stl", "ply", "off", "fbx", "3ds")

    fun install(file: File, project: Project): ModelBundleResult =
        file.inputStream().use { install(it, file.name, project) }

    fun install(input: InputStream, archiveName: String, project: Project): ModelBundleResult {
        val root = File(project.dir, ".bundle_import_${System.nanoTime()}")
        root.mkdirs()
        try {
            val files = ArchiveImporters.extract(input, root, archiveName)
            if (files.isEmpty()) throw IllegalArgumentException("archive is empty")
            val manifestFile = files.firstOrNull { f ->
                val n = f.name.lowercase()
                n in MANIFEST_NAMES || n.endsWith(".modelpack")
            }
            val manifest = if (manifestFile != null) try { JSONObject(manifestFile.readText()) } catch (_: Exception) { JSONObject() } else JSONObject()
            val bundleName = string(manifest, "name", "bundleName")
                ?.let { Project.safeName(it) }?.takeIf { it.isNotBlank() }
                ?: Project.safeName(archiveName.substringBeforeLast('.')).ifBlank { "ModelBundle" }
            val bundleStem = bundleName.substringBeforeLast('.').ifBlank { "ModelBundle" }
            val installedByRel = LinkedHashMap<String, String>()
            val installedByFile = LinkedHashMap<File, String>()
            val installed = ArrayList<String>()

            for (f in files) {
                val rel = relative(root, f)
                if (isIgnored(rel, f.name)) continue
                val assetName = project.uniqueAssetName(Project.safeName(f.name))
                if (assetName.isBlank()) continue
                project.writeBinaryAsset(assetName, f.readBytes())
                installedByRel[rel] = assetName
                installedByFile[f] = assetName
                installed.add(assetName)
            }
            if (installed.isEmpty()) throw IllegalArgumentException("archive has no installable files")

            fun assetFor(raw: String?): String? {
                val clean = normalize(raw ?: return null)
                if (clean.isBlank() || clean.contains("..")) return null
                val candidates = ArrayList<String>()
                candidates.add(clean)
                val dir = manifestFile?.parentFile?.let { relative(root, it) }?.takeIf { it.isNotBlank() }
                if (dir != null) candidates.add("$dir/$clean")
                candidates.add(clean.substringAfterLast('/'))
                for (c in candidates) {
                    installedByRel[c]?.let { return it }
                    val byName = Project.safeName(c.substringAfterLast('/'))
                    installedByRel.entries.firstOrNull { it.key.equals(c, ignoreCase = true) || it.value.equals(byName, ignoreCase = true) }?.value?.let { return it }
                }
                return null
            }

            fun generatedText(base: String, text: String): String {
                val n = project.uniqueAssetName(base)
                project.writeAsset(n, text)
                installed.add(n)
                return n
            }

            fun generatedBinary(base: String, bytes: ByteArray): String {
                val n = project.uniqueAssetName(base)
                project.writeBinaryAsset(n, bytes)
                installed.add(n)
                return n
            }

            val modelAssets = installed.filter { ModelFormatRegistry.isModel(it) }.distinct()
                .sortedBy { RENDER_PRIORITY.indexOf(ModelFormatRegistry.ext(it)).let { i -> if (i < 0) 99 else i } }
            val textureAssets = installed.filter { ModelFormatRegistry.isTexture(it) }.distinct()
            val anim3dAssets = installed.filter { ModelFormatRegistry.ext(it) in com.sengine.engine.core.AssetKind.ANIMATION3D.extensions }.distinct()
            val spriteAnimAssets = installed.filter { it.endsWith(".anim", ignoreCase = true) }.distinct()

            val sourceModel = strings(manifest, "model", "sourceModel", "authoringModel", "dccModel").mapNotNull(::assetFor).firstOrNull()
                ?: modelAssets.firstOrNull()
                ?: throw IllegalArgumentException("bundle has no 3D model")
            var renderModel = strings(manifest, "renderModel", "runtimeModel", "mesh", "geometry").mapNotNull(::assetFor).firstOrNull()
                ?: modelAssets.firstOrNull { Meshes.canRender(project.assetFile(it)) }
                ?: throw IllegalArgumentException("bundle needs a renderModel companion such as OBJ or glTF")
            if (!Meshes.canRender(project.assetFile(renderModel))) {
                throw IllegalArgumentException("renderModel '$renderModel' cannot be decoded at runtime")
            }

            val textures = ArrayList<String>()
            for (t in strings(manifest, "textures")) assetFor(t)?.let { textures.add(it) }
            for (t in listOf(string(manifest, "texture"), string(manifest, "albedo"), string(manifest, "diffuse"))) {
                t?.let { assetFor(it) }?.let { textures.add(it) }
            }
            for (t in textureAssets) if (t !in textures) textures.add(t)
            if (textures.isEmpty()) {
                val pixels = intArrayOf(0xFF80CBC4.toInt(), 0xFFFFF8E1.toInt(), 0xFFFFF8E1.toInt(), 0xFF80CBC4.toInt())
                textures.add(generatedBinary("${bundleStem}_Albedo.png", PngCodec.encode(2, 2, pixels)))
            }
            val primaryTexture = string(manifest, "primaryTexture", "mainTexture")?.let(::assetFor)?.takeIf { it in textures } ?: textures.first()

            val columns = manifest.optInt("columns", manifest.optInt("atlasColumns", 1)).coerceIn(1, 256)
            val rows = manifest.optInt("rows", manifest.optInt("atlasRows", 1)).coerceIn(1, 256)
            val fps = manifest.optDouble("fps", 12.0).toFloat().coerceIn(0.1f, 240f)
            val loop = manifest.optBoolean("loop", true)
            val spriteSheet = manifest.optBoolean("spriteSheet", columns > 1 || rows > 1)
            val profileJson = manifest.optJSONObject("textureProfile") ?: JSONObject()
            for (t in textures) {
                val existing = assetFor("${t.substringBeforeLast('.')}.texture")
                if (existing != null) continue
                val p = TextureProfile.fromJson(profileJson)
                if (t == primaryTexture && spriteSheet) {
                    p.spriteSheet = true
                    p.atlasColumns = columns
                    p.atlasRows = rows
                    p.minFilter = TextureProfile.FILTER_NEAREST
                    p.magFilter = TextureProfile.FILTER_NEAREST
                    p.mipmaps = false
                }
                generatedText("$t.texture", p.toJson().toString(2))
            }

            val materialName = strings(manifest, "material", "materialAsset").mapNotNull(::assetFor).firstOrNull()
                ?: project.uniqueAssetName("$bundleStem.mat")
            val material = if (project.assetFile(materialName).isFile) {
                try { MaterialAsset.fromJson(JSONObject(project.readAsset(materialName) ?: "{}")) } catch (_: Exception) { MaterialAsset() }
            } else MaterialAsset()
            if (material.texture.isBlank()) material.texture = primaryTexture
            string(manifest, "color")?.let { try { material.color = Component.parseColor(it) } catch (_: Exception) {} }
            project.writeAsset(materialName, material.toJson().toString(2))
            if (materialName !in installed) installed.add(materialName)

            val frameList = frames(manifest, columns * rows)
            val clipName = strings(manifest, "spriteAnimation", "spriteClip", "animation2d", "clip2d", "animation").mapNotNull(::assetFor).firstOrNull()
                ?: spriteAnimAssets.firstOrNull()
                ?: project.uniqueAssetName("$bundleStem.anim")
            val spriteClip = if (project.assetFile(clipName).isFile) {
                try {
                    AnimationClip.fromJson(JSONObject(project.readAsset(clipName) ?: "{}")).also { c ->
                        if (c.texture.isBlank()) c.texture = primaryTexture
                        if (c.columns <= 1 && columns > 1) c.columns = columns
                        if (c.rows <= 1 && rows > 1) c.rows = rows
                        if (c.frames.isEmpty()) c.frames = frameList.toMutableList()
                    }
                } catch (_: Exception) { AnimationClip(primaryTexture, columns, rows, frameList.toMutableList(), fps, loop) }
            } else AnimationClip(primaryTexture, columns, rows, frameList.toMutableList(), fps, loop)
            project.writeAsset(clipName, spriteClip.toJson().toString(2))
            if (clipName !in installed) installed.add(clipName)

            val requestedAnim = strings(manifest, "animation3d", "animation3D", "rigAnimation", "rigClip", "motion", "animation").mapNotNull(::assetFor).firstOrNull()
                ?: anim3dAssets.firstOrNull()
            val animation3d = requestedAnim?.let { normalizeAnimationClip(it, bundleStem, ::generatedText, project) }
                ?: anim3dAssets.firstNotNullOfOrNull { normalizeAnimationClip(it, bundleStem, ::generatedText, project) }
                ?: manifest.optJSONObject("animation3dData")?.let { o ->
                    val clip = Animation3DClip.fromJson(o)
                    generatedText("$bundleStem.anim3d", clip.toJson().toString(2))
                }
                ?: generatedText("$bundleStem.anim3d", defaultClip(bundleName).toJson().toString(2))

            val sceneName = Project.safeName(string(manifest, "scene", "targetScene") ?: project.startScene).ifBlank { project.startScene }
            val objectName = Project.safeName(string(manifest, "objectName", "object") ?: bundleName).ifBlank { bundleName }
            val createObject = manifest.optBoolean("createObject", true)
            if (createObject) {
                wireScene(project, sceneName, objectName, renderModel, primaryTexture, materialName, clipName, animation3d)
            }

            val metadata = JSONObject()
                .put("type", "sengine.modelpack")
                .put("version", 1)
                .put("name", bundleName)
                .put("archive", archiveName)
                .put("sourceModel", sourceModel)
                .put("renderModel", renderModel)
                .put("primaryTexture", primaryTexture)
                .put("textures", JSONArray(textures))
                .put("material", materialName)
                .put("animation3d", animation3d)
                .put("spriteAnimation", clipName)
                .put("scene", sceneName)
                .put("object", if (createObject) objectName else "")
                .put("categories", JSONArray(listOf(ModelFormatRegistry.categoryFor(sourceModel)) + ModelFormatRegistry.CATEGORIES))
                .put("sourceFormats", JSONArray(modelAssets.map(ModelFormatRegistry::ext).distinct()))
                .put("status", "fully wired")
                .put("conversion", "DCC source files are retained; renderModel is the runtime mesh companion")
            val metadataName = generatedText("$bundleStem.modelpack", metadata.toString(2))
            val warnings = ArrayList<String>()
            if (!ModelFormatRegistry.isRuntimeDecodable(sourceModel)) {
                warnings += "$sourceModel is retained as an authoring source; $renderModel supplies runtime geometry"
            }
            if (sourceModel != renderModel) warnings += "source model and render companion are linked in $metadataName"
            return ModelBundleResult(
                bundleName, archiveName, installed.distinct(), sourceModel, renderModel,
                textures.distinct(), primaryTexture, materialName, animation3d, clipName,
                metadataName, sceneName, if (createObject) objectName else "",
                listOf(ModelFormatRegistry.categoryFor(sourceModel)) + ModelFormatRegistry.CATEGORIES,
                warnings,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ helpers
    private fun normalizeAnimationClip(asset: String, stem: String, generated: (String, String) -> String, project: Project): String? {
        val ext = ModelFormatRegistry.ext(asset)
        if (ext == "anim3d") return asset
        val text = project.readAsset(asset) ?: return null
        val clip = try { Animation3DImporters.parse(asset, text) ?: Animation3DClip.fromJson(JSONObject(text)) } catch (_: Exception) { return null }
        if (clip.validate() != null) return null
        if (clip.name.isBlank()) clip.name = stem
        return generated("${asset.substringBeforeLast('.')}.anim3d", clip.toJson().toString(2))
    }

    private fun defaultClip(name: String): Animation3DClip {
        val clip = Animation3DClip(name, 30f, true, "root")
        clip.tracks.add(AnimationTrack(
            "root",
            listOf(0f, 1f, 2f, 3f),
            listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0.15f, 0f), floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, -0.15f, 0f)),
            listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 12f, 0f), floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, -12f, 0f)),
            listOf(floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f)),
        ))
        return clip
    }

    private fun wireScene(
        project: Project, sceneName: String, objectName: String,
        renderModel: String, texture: String, material: String,
        spriteClip: String, animation3d: String,
    ) {
        val scene = if (project.sceneExists(sceneName)) project.loadScene(sceneName) else Scene(sceneName)
        val go = scene.create(objectName)
        go.add(MeshRenderer().also {
            it.mesh = MeshRenderer.MESHES.size - 1
            it.model = renderModel
            it.texture = texture
            it.material = material
        })
        go.add(Animator().also { it.clip = spriteClip; it.playOnStart = true })
        val anim = go.add(ModelAnimator().also {
            it.clip = animation3d
            it.playOnStart = true
            it.loop = true
        })
        val text = project.readAsset(animation3d).orEmpty()
        val clip = try { Animation3DImporters.parse(animation3d, text) ?: Animation3DClip.fromJson(JSONObject(text)) } catch (_: Exception) { null }
        anim.skeleton = clip?.skeleton ?: ""
        for (track in clip?.tracks.orEmpty()) {
            val target = track.target
            if (target.isBlank() || target.equals("root", ignoreCase = true) || target == go.name) continue
            val child = scene.create(target, go)
            child.name = target
        }
        project.saveScene(scene)
        if (project.startScene != sceneName) {
            project.startScene = sceneName
            project.saveMeta()
        }
    }

    private fun string(o: JSONObject, vararg keys: String): String? {
        for (k in keys) {
            val v = o.optString(k, "").trim()
            if (v.isNotEmpty()) return v
        }
        return null
    }

    private fun strings(o: JSONObject, vararg keys: String): List<String> {
        val out = ArrayList<String>()
        for (k in keys) {
            val value = o.opt(k) ?: continue
            when (value) {
                is String -> if (value.isNotBlank()) out.add(value.trim())
                is JSONArray -> for (i in 0 until value.length()) value.optString(i, "").takeIf { it.isNotBlank() }?.let { out.add(it.trim()) }
            }
        }
        return out
    }

    private fun frames(o: JSONObject, fallback: Int): List<Int> {
        val arr = o.optJSONArray("frames")
        if (arr != null && arr.length() > 0) return (0 until arr.length()).mapNotNull { i -> arr.optInt(i, -1).takeIf { it >= 0 } }
        val text = o.optString("frameRange", "")
        if (text.isNotBlank()) return AnimationClip.parseFrames(text)
        return (0 until fallback.coerceAtLeast(1)).toList()
    }

    private fun normalize(path: String): String = path.trim().replace('\\', '/').removePrefix("./").trimStart('/')

    private fun relative(root: File, f: File): String =
        try { f.relativeTo(root).path.replace('\\', '/').removePrefix("./") } catch (_: Exception) { f.name }

    private fun isIgnored(rel: String, fileName: String): Boolean {
        val parts = rel.split('/')
        if (parts.any { it.startsWith(".") || it.equals("__MACOSX", ignoreCase = true) }) return true
        return fileName.lowercase() in IGNORED
    }
}
