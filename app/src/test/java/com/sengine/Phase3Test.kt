package com.sengine

import com.sengine.engine.anim.Animation3DClip
import com.sengine.engine.anim.Animation3DImporters
import com.sengine.engine.anim.AnimationClip
import com.sengine.engine.anim.AnimationTrack
import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.AssetValidator
import com.sengine.engine.core.Base64Codec
import com.sengine.engine.core.ComponentRegistry
import com.sengine.engine.core.ModelAnimator
import com.sengine.engine.render.Meshes
import com.sengine.engine.render.ModelFormatRegistry
import com.sengine.engine.render.PngCodec
import com.sengine.engine.render.TextureDecoders
import com.sengine.engine.render.TextureProfile
import com.sengine.engine.script.ApiReference
import com.sengine.engine.sprite.SpriteDocument
import com.sengine.project.ArchiveImporters
import com.sengine.project.BigEditionAssets
import com.sengine.project.ModelBundleImporter
import com.sengine.project.ModelBundleResult
import com.sengine.project.Project
import com.sengine.project.SpriteAssets
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** End-to-end Phase 3 acceptance coverage using pure JVM classes only. */
class Phase3Test {

    private fun tempProject(name: String): Project {
        val dir = File(System.getProperty("java.io.tmpdir"), "sengine-phase3-$name-${System.nanoTime()}")
        return Project(dir).apply { assetsDir.mkdirs(); scenesDir.mkdirs() }
    }

    private fun threeDClip(): Animation3DClip = Animation3DClip("FruitShopBob", 30f, true, "Fruit Shop").apply {
        tracks.add(AnimationTrack(
            "Fruit Shop",
            listOf(0f, 15f, 30f),
            listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0.25f, 0f), floatArrayOf(0f, 0f, 0f)),
            listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 12f, 0f), floatArrayOf(0f, 0f, 0f)),
            listOf(floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f), floatArrayOf(1f, 1f, 1f)),
        ))
    }

    private fun bundleFiles(): LinkedHashMap<String, ByteArray> {
        val files = LinkedHashMap<String, ByteArray>()
        val manifest = JSONObject()
            .put("type", "sengine.modelpack")
            .put("name", "Fruit Shop")
            .put("model", "FruitShop.blend")
            .put("renderModel", "FruitShop.obj")
            .put("textures", JSONArray(listOf("FruitShop.png")))
            .put("primaryTexture", "FruitShop.png")
            .put("material", "FruitShop.mat")
            .put("spriteAnimation", "FruitShop.anim")
            .put("animation3d", "FruitShop.anim3d")
            .put("spriteSheet", true)
            .put("columns", 2).put("rows", 1)
            .put("frames", JSONArray(listOf(0, 1)))
            .put("fps", 8.0)
            .put("scene", "Main")
            .put("objectName", "Fruit Shop")
            .put("createObject", true)
        files["manifest.json"] = manifest.toString(2).toByteArray(Charsets.UTF_8)
        files["FruitShop.blend"] = "BLENDER-v280 proprietary source retained".toByteArray()
        files["FruitShop.fbx"] = "; FBX ASCII\nVertices: *1 { 0,0,0, 1,0,0, 0,1,0 }\nPolygonVertexIndex: *1 { 0,1,-3 }\n".toByteArray()
        files["FruitShop.obj"] = "mtllib FruitShop.mtl\no FruitShop\nv -1 0 0\nv 1 0 0\nv 0 1.5 0\nv 0 0 -1\nvt 0 0\nvt 1 0\nvt 0.5 1\nvt 0.5 0.5\nusemtl FruitMaterial\nf 1/1 2/2 3/3\nf 1/1 4/4 2/2\n".toByteArray()
        files["FruitShop.mtl"] = "newmtl FruitMaterial\nKd 1.0 0.6 0.2\nmap_Kd FruitShop.png\n".toByteArray()
        val px = IntArray(32 * 32) { i ->
            if (((i % 32) / 8 + (i / 32) / 8) % 2 == 0) 0xFFFF8A65.toInt() else 0xFFAED581.toInt()
        }
        files["FruitShop.png"] = PngCodec.encode(32, 32, px)
        files["FruitShop.anim"] = AnimationClip("FruitShop.png", 2, 1, mutableListOf(0, 1), 8f, true).toJson().toString(2).toByteArray()
        files["FruitShop.anim3d"] = threeDClip().toJson().toString(2).toByteArray()
        files["FruitShop.texture"] = TextureProfile.DEFAULT.copy(
            minFilter = TextureProfile.FILTER_NEAREST,
            magFilter = TextureProfile.FILTER_NEAREST,
            mipmaps = false,
            spriteSheet = true,
            atlasColumns = 2,
            atlasRows = 1,
        ).toJson().toString(2).toByteArray()
        files["FruitShop.mat"] = BigEditionAssets.MATERIAL
            .replace("\"texture\": \"\"", "\"texture\": \"FruitShop.png\"")
            .toByteArray()
        return files
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            files.forEach { (name, bytes) ->
                z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun esrJson(files: Map<String, ByteArray>): ByteArray {
        val o = JSONObject()
        files.forEach { (name, bytes) -> o.put(name, Base64Codec.encode(bytes)) }
        return JSONObject().put("type", "sengine.esr").put("name", "Fruit Shop")
            .put("files", o).toString(2).toByteArray()
    }

    private fun install(files: Map<String, ByteArray>, name: String): Pair<Project, ModelBundleResult> {
        val p = tempProject(name)
        val r = ModelBundleImporter.install(ByteArrayInputStream(zip(files)), "FruitShop.zip", p)
        return p to r
    }

    @Test
    fun `phase 3 exposes all screenshot model categories and asset kinds`() {
        val wanted = listOf(
            "Blender Models", "FBX Models", "Cinema 4D Models", "3ds Max Models",
            "Maya Models", "Obj Models", "Rigged", "Animated Models", "Lowpoly Models",
        )
        wanted.forEach { assertTrue("$it missing", it in ModelFormatRegistry.CATEGORIES) }
        listOf("blend", "fbx", "c4d", "3ds", "max", "ma", "mb", "obj", "gltf", "glb", "dae", "stl", "ply", "off")
            .forEach { assertEquals(AssetKind.MODEL, AssetKind.of("model.$it")) }
        assertEquals(AssetKind.ANIMATION3D, AssetKind.of("motion.bvh"))
        assertEquals(AssetKind.ANIMATION3D, AssetKind.of("clip.anim3d"))
        assertEquals(AssetKind.ARCHIVE, AssetKind.of("bundle.esr"))
        assertEquals(AssetKind.SPRITE, AssetKind.of("Hero.sprite"))
        assertEquals(AssetKind.TEXTURE_META, AssetKind.of("Hero.texture"))
        assertEquals(AssetKind.MODELPACK, AssetKind.of("Hero.modelpack"))
        listOf("clip.anim3d", "doc.sprite", "profile.texture", "pack.modelpack")
            .forEach { assertTrue("$it should validate", AssetValidator.supports(it)) }
        assertTrue(ComponentRegistry.types.containsKey("ModelAnimator"))
        assertNotNull(ComponentRegistry.types["ModelAnimator"]?.invoke() as? ModelAnimator)
    }

    @Test
    fun `complete zip bundle installs source, render model, texture, clips and scene object`() {
        val (p, r) = install(bundleFiles(), "zip")
        assertEquals("Fruit Shop", r.name)
        assertEquals("FruitShop.obj", r.renderModel)
        assertEquals("FruitShop.png", r.primaryTexture)
        assertEquals("FruitShop.anim", r.spriteAnimation)
        assertEquals("FruitShop.anim3d", r.animation3D)
        assertTrue(File(p.assetsDir, "FruitShop.blend").isFile)
        assertTrue(File(p.assetsDir, "FruitShop.fbx").isFile)
        assertTrue(File(p.assetsDir, r.metadata).isFile)
        assertTrue(File(p.assetsDir, "FruitShop.mat").isFile)
        assertTrue(File(p.assetsDir, "FruitShop.texture").isFile)
        val scene = p.loadScene(r.sceneName)
        val obj = scene.objects.firstOrNull { it.name == "Fruit Shop" }
        assertNotNull("bundle must create a wired scene object", obj)
        val mesh = obj!!.getAny<com.sengine.engine.core.MeshRenderer>()
        assertNotNull(mesh)
        assertEquals("FruitShop.obj", mesh!!.model)
        assertEquals("FruitShop.png", mesh.texture)
        assertEquals("FruitShop.anim", obj.getAny<com.sengine.engine.core.Animator>()?.clip)
        val anim3d = obj.getAny<ModelAnimator>()
        assertNotNull("3D animator must be wired", anim3d)
        assertEquals("FruitShop.anim3d", anim3d!!.clip)
        assertTrue(anim3d.playOnStart)
        assertEquals("Fruit Shop", anim3d.skeleton)
        assertTrue(Meshes.canRender(File(p.assetsDir, "FruitShop.obj")))
        assertEquals("FruitShop.png", Meshes.modelTexture(File(p.assetsDir, "FruitShop.obj")))
    }

    @Test
    fun `ESR JSON and RAR store bundles install through the same pipeline`() {
        val files = bundleFiles()
        val p1 = tempProject("esr")
        val r1 = ModelBundleImporter.install(ByteArrayInputStream(esrJson(files)), "FruitShop.esr", p1)
        assertEquals("FruitShop.obj", r1.renderModel)
        assertTrue(File(p1.assetsDir, "FruitShop.blend").isFile)
        assertTrue(File(p1.assetsDir, r1.metadata).isFile)

        val rar = ArchiveImporters.createRar4Store(files)
        val p2 = tempProject("rar")
        val r2 = ModelBundleImporter.install(ByteArrayInputStream(rar), "FruitShop.rar", p2)
        assertEquals("FruitShop.png", r2.primaryTexture)
        assertTrue(File(p2.assetsDir, "FruitShop.png").isFile)
        assertTrue(File(p2.assetsDir, "FruitShop.obj").isFile)
        val data = TextureDecoders.decodeBitmapData("FruitShop.png", File(p2.assetsDir, "FruitShop.png").readBytes())
        assertNotNull("PNG decoder must read the installed texture", data)
        assertEquals(32, data!!.width)
        assertEquals(32, data.height)
    }

    @Test
    fun `ESR1 plus ZIP wrapper and archive path safety are supported`() {
        val files = bundleFiles()
        val p = tempProject("esr1")
        val wrapper = ByteArrayOutputStream()
        wrapper.write("ESR1".toByteArray())
        wrapper.write(zip(files))
        val r = ModelBundleImporter.install(ByteArrayInputStream(wrapper.toByteArray()), "Bundle.esr", p)
        assertEquals("FruitShop.obj", r.renderModel)
        assertTrue(File(p.assetsDir, "FruitShop.obj").isFile)

        // zip-slip must never write outside the extraction directory.
        val escapeName = "escape-${System.nanoTime()}.txt"
        val zipSlip = ByteArrayOutputStream()
        ZipOutputStream(zipSlip).use { z ->
            z.putNextEntry(ZipEntry("../$escapeName")); z.write("bad".toByteArray()); z.closeEntry()
        }
        val p2 = tempProject("slip")
        val extracted = ArchiveImporters.extract(ByteArrayInputStream(zipSlip.toByteArray()), p2.dir, "Slip.zip")
        assertTrue("unsafe archive entry should be ignored", extracted.isEmpty())
        assertFalse(File(p2.dir.parentFile, escapeName).exists())
    }

    @Test
    fun `sprite document edits, undo, transforms and serialization round-trip`() {
        val d = SpriteDocument(8, 8).apply { addFrame(); addLayer() }
        val before = d.pixels().copyOf()
        d.checkpoint()
        d.drawLine(0, 0, 7, 7, 2, 0xFF112233.toInt(), false)
        assertFalse(before.contentEquals(d.pixels()))
        val drawn = d.pixels().copyOf()
        assertTrue(d.undo())
        assertTrue(before.contentEquals(d.pixels()))
        assertTrue(d.redo())
        assertTrue(drawn.contentEquals(d.pixels()))
        d.floodFill(7, 0, 0xFF445566.toInt(), 0)
        val parsed = SpriteDocument.parse(d.toJson().toString())!!
        assertEquals(8, parsed.width)
        assertEquals(2, parsed.frameCount)
        assertEquals(2, parsed.layers.size)
        assertTrue(parsed.pixels().contentEquals(d.pixels()))
        parsed.checkpoint(); parsed.rotate90(true)
        assertEquals(8, parsed.width); assertEquals(8, parsed.height)
        assertTrue(parsed.undo())
    }

    @Test
    fun `sprite export produces PNG sheet, atlas, clip and texture profile`() {
        val p = tempProject("sprite")
        val d = SpriteDocument(4, 4).apply {
            addLayer(); addFrame(true); addFrame(false)
            drawRect(0, 0, 3, 3, 1, 0xFFE91E63.toInt(), true, false)
        }
        val r = SpriteAssets.export(p, d, "Hero.sprite")
        assertTrue(File(p.assetsDir, r.png).isFile)
        assertTrue(File(p.assetsDir, r.atlas).isFile)
        assertTrue(File(p.assetsDir, r.animation).isFile)
        assertTrue(File(p.assetsDir, r.textureProfile).isFile)
        val data = PngCodec.decode(File(p.assetsDir, r.png).readBytes())
        assertNotNull(data)
        assertEquals(4 * 3, data!!.width)
        assertEquals(4, data.height)
        val atlas = JSONObject(File(p.assetsDir, r.atlas).readText())
        assertEquals(r.png, atlas.optString("texture"))
        assertEquals(3, atlas.optJSONObject("frames")?.length())
        val clip = JSONObject(File(p.assetsDir, r.animation).readText())
        assertEquals(r.png, clip.optString("texture"))
        val profile = TextureProfile.fromJson(JSONObject(File(p.assetsDir, r.textureProfile).readText()))
        assertTrue(profile.spriteSheet)
        assertEquals(3, profile.atlasColumns)
    }

    @Test
    fun `3D clips sample tracks and model animation API is documented`() {
        val clip = Animation3DClip(
            "Bob", 1f, true, "root",
            mutableListOf(AnimationTrack(
                "Fruit Shop",
                listOf(0f, 1f),
                listOf(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 2f, 0f)),
                emptyList(), emptyList(),
            )),
        )
        val pose = clip.sample(0.5f, true)
        assertEquals(1f, pose["Fruit Shop"]?.first?.get(1) ?: 0f, 0.001f)
        val parsed = Animation3DImporters.parse("Bob.anim3d", clip.toJson().toString())!!
        assertEquals("Bob", parsed.name)
        assertEquals(1, parsed.tracks.size)
        val api = ApiReference.API_DOC
        listOf("playModelAnimation", "stopModelAnimation", "setModelAnimationSpeed", "setModelAnimationTime")
            .forEach { assertTrue("$it missing from API docs", api.contains(it)) }
        assertTrue(api.contains("runtime v3.2.0"))
    }

    @Test
    fun `asset validator accepts phase 3 templates and rejects malformed ones`() {
        val samples = mapOf(
            "clip.anim3d" to threeDClip().toJson().toString(),
            "doc.sprite" to SpriteDocument(4, 4).toJson().toString(),
            "profile.texture" to TextureProfile.DEFAULT.toJson().toString(),
            "pack.modelpack" to JSONObject().put("type", "sengine.modelpack")
                .put("renderModel", "mesh.obj").put("primaryTexture", "albedo.png").toString(),
        )
        samples.forEach { (name, text) ->
            assertNull("$name should validate: ${AssetValidator.validate(name, text)}", AssetValidator.validate(name, text))
        }
        assertNotNull(AssetValidator.validate("pack.modelpack", "{}"))
        assertNotNull(AssetValidator.validate("doc.sprite", "{not json"))
    }

    @Test
    fun `phase 3 version and docs are consistent`() {
        assertEquals("S Engine 3.2.0", Project.ENGINE_VERSION)
        val doc = File(System.getProperty("sengine.repo"), "docs/SCRIPTING_API.md").readText().trim()
        assertEquals(doc, ApiReference.API_DOC.trimIndent().trim())
        val manifest = File(System.getProperty("sengine.repo"), "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("SpriteEditorActivity"))
        assertTrue(manifest.contains("SpriteSheetEditorActivity"))
        val gradle = File(System.getProperty("sengine.repo"), "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionName = \"3.2.0\""))
        assertTrue(gradle.contains("versionCode = 6"))
    }
}
