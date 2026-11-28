package com.sengine

import com.sengine.engine.anim.AnimationClip
import com.sengine.engine.anim.AnimationSystem
import com.sengine.engine.core.Animator
import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.AssetValidator
import com.sengine.engine.core.SpriteAtlas
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.render.BitmapData
import com.sengine.engine.render.PngCodec
import com.sengine.engine.render.TextureProfile
import com.sengine.engine.sprite.SpritePacker
import com.sengine.engine.sprite.SpriteSheetDoc
import com.sengine.engine.sprite.SpriteSlicer
import com.sengine.project.Project
import com.sengine.project.SpriteSheetAssets
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** v3.2.0 sprite sheet workflow: slicing, packing, documents, export and named-frame playback. */
class SpriteSheetTest {

    private fun tempProject(name: String): Project {
        val dir = File(System.getProperty("java.io.tmpdir"), "sengine-sheet-$name-${System.nanoTime()}")
        return Project(dir).apply { assetsDir.mkdirs(); scenesDir.mkdirs() }
    }

    /** Transparent image with opaque rectangles at the given pixel bounds. */
    private fun image(w: Int, h: Int, rects: List<IntArray>): BitmapData {
        val px = IntArray(w * h)
        for (r in rects) {
            for (y in r[1] until r[1] + r[3]) {
                for (x in r[0] until r[0] + r[2]) {
                    px[y * w + x] = 0xFF3366CC.toInt()
                }
            }
        }
        return BitmapData(w, h, px)
    }

    // ---------------------------------------------------------------- slicing
    @Test
    fun autoSliceFindsSeparatedOpaqueRegionsInReadingOrder() {
        val img = image(48, 24, listOf(intArrayOf(4, 4, 8, 8), intArrayOf(24, 4, 8, 8)))
        val frames = SpriteSlicer.autoSlice(img.argb, img.width, img.height, alphaThreshold = 16, minSize = 4)
        assertEquals(2, frames.size)
        assertEquals("frame_00", frames[0].name)
        assertEquals(4, frames[0].x); assertEquals(4, frames[0].y)
        assertEquals(8, frames[0].w); assertEquals(8, frames[0].h)
        assertEquals(24, frames[1].x); assertEquals(4, frames[1].y)
        // top-to-bottom: a lower row sorts after an upper one
        val lower = image(48, 48, listOf(intArrayOf(4, 4, 8, 8), intArrayOf(4, 30, 8, 8)))
        val ordered = SpriteSlicer.autoSlice(lower.argb, lower.width, lower.height, 16, 4)
        assertEquals(2, ordered.size)
        assertTrue(ordered[0].y < ordered[1].y)
    }

    @Test
    fun autoSliceRespectsAlphaThresholdAndMinSize() {
        val w = 32; val h = 16
        val px = IntArray(w * h)
        // faint smudge below the default threshold
        px[2 * w + 2] = 0x08FFFFFF.toInt()
        // tiny speck dropped by minSize
        px[6 * w + 6] = 0xFFFFFFFF.toInt()
        px[6 * w + 7] = 0xFFFFFFFF.toInt()
        px[7 * w + 6] = 0xFFFFFFFF.toInt()
        px[7 * w + 7] = 0xFFFFFFFF.toInt()
        // real region
        for (y in 2 until 10) for (x in 16 until 24) px[y * w + x] = 0xFF00FF00.toInt()

        val frames = SpriteSlicer.autoSlice(px, w, h, alphaThreshold = 16, minSize = 8)
        assertEquals(1, frames.size)
        assertEquals(16, frames[0].x)
        assertEquals(8, frames[0].w)

        // with minSize = 2 the 2x2 speck survives but the faint smudge still does not
        val relaxed = SpriteSlicer.autoSlice(px, w, h, alphaThreshold = 16, minSize = 2)
        assertEquals(2, relaxed.size)

        // lowering the threshold picks up the 1px smudge as well
        val all = SpriteSlicer.autoSlice(px, w, h, alphaThreshold = 4, minSize = 1)
        assertEquals(3, all.size)
    }

    @Test
    fun autoSliceTreatsDiagonallyTouchingPixelsAsSeparateRegions() {
        val w = 8; val h = 8
        val px = IntArray(w * h)
        px[0] = 0xFFFFFFFF.toInt()                 // (0,0)
        px[1] = 0xFFFFFFFF.toInt()                 // (1,0)
        px[w] = 0xFFFFFFFF.toInt()                 // (0,1)
        px[w + 2] = 0xFFFFFFFF.toInt()             // (2,1) — diagonal to (1,0)
        px[w + 3] = 0xFFFFFFFF.toInt()             // (3,1)
        val frames = SpriteSlicer.autoSlice(px, w, h, 16, 1)
        // 4-connected: {0,1,w} form one L, {w+2,w+3} form another
        assertEquals(2, frames.size)
        assertEquals(0, frames[0].x)
        assertEquals(2, frames[1].x)
    }

    @Test
    fun gridAndCellSlicingProduceReadingOrderFrames() {
        val doc = SpriteSheetDoc(texture = "hero.png", columns = 4, rows = 2)
        assertEquals(8, doc.sliceGrid(128, 64))
        assertEquals(32, doc.frames[0].w)
        assertEquals(32, doc.frames[0].h)
        assertEquals(0, doc.frames[0].x)
        assertEquals(0, doc.frames[4].x)   // row-major: index 4 starts the second row
        assertEquals(32, doc.frames[4].y)
        assertEquals(32, doc.frames[5].x)  // second column
        assertEquals("frame_00", doc.frames[0].name)
        assertEquals("frame_07", doc.frames[7].name)

        val cells = SpriteSheetDoc(texture = "hero.png", mode = SpriteSheetDoc.MODE_CELL, cellW = 20, cellH = 20)
        assertEquals(4, cells.sliceCell(45, 45)) // leftover pixels are ignored
        assertEquals(20, cells.frames[3].w)
        assertEquals(20, cells.frames[3].x)
        assertEquals(20, cells.frames[3].y)
    }

    // ---------------------------------------------------------------- packing
    @Test
    fun packerKeepsFramesInsideNonOverlappingPowerOfTwoSheet() {
        val sizes = listOf(10 to 20, 30 to 5, 7 to 7, 50 to 50)
        val r = SpritePacker.pack(sizes, padding = 2, powerOfTwo = true)
        assertTrue("width must be a power of two", r.width > 0 && r.width and (r.width - 1) == 0)
        assertTrue("height must be a power of two", r.height > 0 && r.height and (r.height - 1) == 0)
        assertEquals(sizes.size, r.placements.size)
        assertEquals((0 until sizes.size).toSet(), r.placements.map { it.index }.toSet())
        for (p in r.placements) {
            val (w, h) = sizes[p.index]
            assertEquals(w, p.w); assertEquals(h, p.h)
            assertTrue(p.x >= 0 && p.y >= 0)
            assertTrue(p.x + p.w <= r.width)
            assertTrue(p.y + p.h <= r.height)
        }
        for (a in r.placements) {
            for (b in r.placements) {
                if (a.index >= b.index) continue
                val overlap = a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h
                assertFalse("placements ${a.index} and ${b.index} overlap", overlap)
            }
        }
    }

    @Test
    fun packerRejectsOversizedFrames() {
        try {
            SpritePacker.pack(listOf(5000 to 4), padding = 1, powerOfTwo = true, maxSize = 4096)
            fail("expected IllegalArgumentException for oversized frame")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    // ---------------------------------------------------------------- documents
    @Test
    fun sheetDocRoundTripAndValidation() {
        val doc = SpriteSheetDoc(texture = "hero.png", mode = SpriteSheetDoc.MODE_CELL, cellW = 16, cellH = 16, fps = 14f, loop = false)
        doc.frames.add(com.sengine.engine.sprite.SpriteSheetFrame("run_0", 0, 0, 16, 16))
        doc.frames.add(com.sengine.engine.sprite.SpriteSheetFrame("run_1", 16, 0, 16, 16))
        assertNull(doc.validate())

        val parsed = SpriteSheetDoc.parse(doc.toJson().toString())
        assertNotNull(parsed)
        assertEquals("hero.png", parsed!!.texture)
        assertEquals(SpriteSheetDoc.MODE_CELL, parsed.mode)
        assertEquals(16, parsed.cellW)
        assertEquals(14f, parsed.fps, 0.0001f)
        assertFalse(parsed.loop)
        assertEquals(2, parsed.frames.size)
        assertEquals("run_1", parsed.frames[1].name)
        assertNull(parsed.validate())

        // rejected: duplicate names, zero sizes, frames without a texture, wrong header
        val dup = SpriteSheetDoc(texture = "a.png")
        dup.frames.add(com.sengine.engine.sprite.SpriteSheetFrame("f", 0, 0, 4, 4))
        dup.frames.add(com.sengine.engine.sprite.SpriteSheetFrame("f", 4, 0, 4, 4))
        assertNotNull(dup.validate())

        val zero = SpriteSheetDoc(texture = "a.png")
        zero.frames.add(com.sengine.engine.sprite.SpriteSheetFrame("f", 0, 0, 0, 4))
        assertNotNull(zero.validate())

        val noTex = SpriteSheetDoc()
        noTex.frames.add(com.sengine.engine.sprite.SpriteSheetFrame("f", 0, 0, 4, 4))
        assertNotNull(noTex.validate())

        assertNull(SpriteSheetDoc.parse("{\"format\":\"Something Else\"}"))
        assertNull(SpriteSheetDoc.parse("{not json"))
        assertNotNull(SpriteSheetDoc.parse("{}")) // empty document with defaults
    }

    @Test
    fun assetKindAndValidatorWireSheetAssets() {
        assertEquals(AssetKind.SHEET, AssetKind.of("run.sheet"))
        assertEquals(AssetKind.SHEET, AssetKind.of("run.spritesheet"))
        assertEquals("Sprite Sheet Document", AssetValidator.label("run.sheet"))
        assertTrue(AssetValidator.supports("run.sheet"))

        val good = SpriteSheetDoc(texture = "hero.png").toJson().toString()
        assertNull(AssetValidator.validate("run.sheet", good))
        assertNotNull(AssetValidator.validate("run.sheet", "{not json"))
        assertNotNull(AssetValidator.validate("run.sheet", "{\"format\":\"Nope\"}"))
        // empty sheets are valid (sliced later in the editor)
        assertNull(AssetValidator.validate("run.sheet", SpriteSheetDoc().toJson().toString()))
        // binary-looking extensions still bypass validation
        assertNull(AssetValidator.validate("run.bin", "anything"))
    }

    @Test
    fun bigEditionSheetTemplateIsValid() {
        assertNull(AssetValidator.validate("New.sheet", com.sengine.project.BigEditionAssets.SHEET))
    }

    // ---------------------------------------------------------------- atlas rects
    @Test
    fun spriteAtlasCarriesPixelRectsAlongsideUvs() {
        val atlas = SpriteAtlas(
            texture = "packed.png",
            frames = linkedMapOf(
                "a" to floatArrayOf(0f, 1f, 0.5f, 0f),
                "b" to floatArrayOf(0.5f, 1f, 1f, 0f),
            ),
            rects = linkedMapOf(
                "a" to intArrayOf(0, 0, 16, 32),
                "b" to intArrayOf(16, 0, 16, 32),
            ),
        )
        val parsed = SpriteAtlas.fromJson(JSONObject(atlas.toJson().toString()))
        assertEquals("packed.png", parsed.texture)
        val uv = FloatArray(4)
        assertTrue(parsed.uv("b", uv))
        assertEquals(0.5f, uv[0], 0.0001f)
        val rect = IntArray(4)
        assertTrue(parsed.rect("a", rect))
        assertEquals(16, rect[2])
        assertEquals(32, rect[3])
        assertFalse(parsed.rect("missing", rect))
        assertNull(AssetValidator.validate("x.atlas", parsed.toJson().toString()))

        // a malformed rect is rejected
        val bad = parsed.toJson()
        bad.put("rects", JSONObject().put("a", org.json.JSONArray().put(0).put(0).put(0).put(4)))
        assertNotNull(AssetValidator.validate("x.atlas", bad.toString()))
    }

    // ---------------------------------------------------------------- export
    @Test
    fun spriteSheetExportProducesCompleteAssetSetAndIsStableAcrossReExports() {
        val p = tempProject("export")
        val src = image(64, 32, listOf(intArrayOf(0, 0, 16, 16), intArrayOf(32, 0, 16, 16)))
        // mark the two frames with distinct colours so we can verify the copy
        for (y in 0 until 16) for (x in 0 until 16) src.argb[y * 64 + x] = 0xFFFF0000.toInt()
        for (y in 0 until 16) for (x in 32 until 48) src.argb[y * 64 + x] = 0xFF0000FF.toInt()

        val doc = SpriteSheetDoc(texture = "hero.png", mode = SpriteSheetDoc.MODE_MANUAL, fps = 12f, loop = true)
        doc.frames.add(com.sengine.engine.sprite.SpriteSheetFrame("run_a", 0, 0, 16, 16))
        doc.frames.add(com.sengine.engine.sprite.SpriteSheetFrame("run_b", 32, 0, 16, 16))

        val r = SpriteSheetAssets.export(p, doc, "Hero.sheet", src, padding = 2, powerOfTwo = true)
        assertEquals("Hero.sheet", r.sheet)
        assertEquals("Hero.png", r.png)
        assertEquals("Hero.atlas", r.atlas)
        assertEquals("Hero.anim", r.animation)
        assertEquals("Hero.png.texture", r.textureProfile)
        assertEquals(2, r.frameCount)
        assertTrue(r.width >= 32 && r.height >= 32)

        // packed PNG decodes and has power-of-two dimensions
        val pngBytes = p.assetFile(r.png).readBytes()
        val decoded = PngCodec.decode(pngBytes)!!
        assertEquals(r.width, decoded.width)
        assertEquals(r.height, decoded.height)
        assertTrue(decoded.width and (decoded.width - 1) == 0)
        assertTrue(decoded.height and (decoded.height - 1) == 0)

        // atlas: named UVs + pixel rects
        val atlas = SpriteAtlas.fromJson(JSONObject(p.readAsset(r.atlas)!!))
        assertEquals(setOf("run_a", "run_b"), atlas.frames.keys)
        val rectA = IntArray(4); val rectB = IntArray(4)
        assertTrue(atlas.rect("run_a", rectA))
        assertTrue(atlas.rect("run_b", rectB))
        assertEquals(16, rectA[2]); assertEquals(16, rectA[3])
        val uv = FloatArray(4)
        assertTrue(atlas.uv("run_a", uv))
        assertTrue(uv[0] < uv[2]); assertTrue(uv[1] > uv[3])

        // the red frame pixels landed at the packed position
        val red = decoded.argb[rectA[1] * decoded.width + rectA[0]]
        assertEquals(0xFFFF0000.toInt(), red)
        val blue = decoded.argb[rectB[1] * decoded.width + rectB[0]]
        assertEquals(0xFF0000FF.toInt(), blue)

        // anim: named-frame atlas clip
        val clip = AnimationClip.fromJson(JSONObject(p.readAsset(r.animation)!!))
        assertTrue(clip.isAtlasClip)
        assertEquals("Hero.atlas", clip.atlas)
        assertEquals(listOf("run_a", "run_b"), clip.frameNames)
        assertEquals(12f, clip.fps, 0.0001f)
        assertTrue(clip.loop)
        assertEquals(2f / 12f, clip.duration, 0.0001f)
        assertNull(AssetValidator.validate(r.animation, p.readAsset(r.animation)!!))

        // texture profile: nearest sampling for crisp pixels
        val profile = TextureProfile.fromJson(JSONObject(p.readAsset(r.textureProfile)!!))
        assertEquals(TextureProfile.FILTER_NEAREST, profile.minFilter)
        assertEquals(TextureProfile.FILTER_NEAREST, profile.magFilter)
        assertFalse(profile.mipmaps)
        assertTrue(profile.spriteSheet)

        // document round-trips through the validator
        assertNull(AssetValidator.validate(r.sheet, p.readAsset(r.sheet)!!))
        assertEquals(2, SpriteSheetDoc.parse(p.readAsset(r.sheet)!!)!!.frames.size)

        // re-export keeps the same asset names (no copy proliferation)
        SpriteSheetAssets.export(p, doc, r.sheet, src, padding = 2, powerOfTwo = true)
        assertEquals(1, p.listAssets(AssetKind.SHEET).count { it == "Hero.sheet" })
        assertEquals(1, p.listAssets(AssetKind.TEXTURE).count { it == "Hero.png" })
        assertEquals(1, p.listAssets(AssetKind.ATLAS).count { it == "Hero.atlas" })
        assertEquals(1, p.listAssets(AssetKind.ANIMATION).count { it == "Hero.anim" })
    }

    @Test
    fun exportRejectsFramesOutsideTheSourceTexture() {
        val p = tempProject("bounds")
        val src = image(32, 32, listOf(intArrayOf(0, 0, 8, 8)))
        val doc = SpriteSheetDoc(texture = "small.png")
        doc.frames.add(com.sengine.engine.sprite.SpriteSheetFrame("bad", 24, 24, 16, 16))
        try {
            SpriteSheetAssets.export(p, doc, "Bad.sheet", src)
            fail("expected out-of-bounds failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("outside"))
        }
    }

    // ---------------------------------------------------------------- playback
    @Test
    fun animationClipRoundTripsNamedAtlasFrames() {
        val clip = AnimationClip(
            texture = "packed.png", atlas = "packed.atlas",
            frames = mutableListOf(0), fps = 8f, loop = false,
            frameNames = mutableListOf("idle_0", "idle_1", "idle_2"),
        )
        assertTrue(clip.isAtlasClip)
        assertEquals(3f / 8f, clip.duration, 0.0001f)
        val parsed = AnimationClip.fromJson(JSONObject(clip.toJson().toString()))
        assertTrue(parsed.isAtlasClip)
        assertEquals("packed.atlas", parsed.atlas)
        assertEquals(listOf("idle_0", "idle_1", "idle_2"), parsed.frameNames)
        assertEquals(8f, parsed.fps, 0.0001f)
        assertFalse(parsed.loop)

        // legacy grid clips still round-trip and are not atlas clips
        val grid = AnimationClip(texture = "sheet.png", columns = 4, rows = 2, frames = mutableListOf(0, 1, 5), fps = 10f)
        val g = AnimationClip.fromJson(JSONObject(grid.toJson().toString()))
        assertFalse(g.isAtlasClip)
        assertEquals(listOf(0, 1, 5), g.frames)
        assertEquals(4, g.columns)
    }

    @Test
    fun animationSystemDrivesSpriteRendererFromAtlasClip() {
        val p = tempProject("playback")
        p.writeAsset(
            "packed.atlas",
            JSONObject()
                .put("texture", "packed.png")
                .put("columns", 1)
                .put("rows", 1)
                .put(
                    "frames",
                    JSONObject()
                        .put("run_a", org.json.JSONArray().put(0.0).put(1.0).put(0.5).put(0.0))
                        .put("run_b", org.json.JSONArray().put(0.5).put(1.0).put(1.0).put(0.0)),
                )
                .toString(),
        )
        val atlasClip = AnimationClip(
            texture = "packed.png", atlas = "packed.atlas",
            fps = 10f, loop = true,
            frameNames = mutableListOf("run_a", "run_b"),
        )
        p.writeAsset("run.anim", atlasClip.toJson().toString())

        val system = AnimationSystem(p)
        val anim = Animator().apply { clip = "run.anim"; playing = true; speed = 1f; time = 0f; frame = 0 }
        val sr = SpriteRenderer()

        // first tenth of a second -> frame 0
        anim.current = "run.anim" // mirrors Animator.resetRuntime() in the engine
        system.update(anim, sr, 0.05f, playing = true)
        assertEquals("packed.png", sr.animTexture)
        assertEquals(0f, sr.uv[0], 0.0001f)
        assertEquals(0.5f, sr.uv[2], 0.0001f)

        // advance past the second frame
        system.update(anim, sr, 0.06f, playing = true)
        assertEquals(1, anim.frame)
        assertEquals(0.5f, sr.uv[0], 0.0001f)
        assertEquals(1f, sr.uv[2], 0.0001f)

        // loop wraps back to the first frame
        system.update(anim, sr, 0.10f, playing = true)
        assertEquals(0, anim.frame)

        // a missing atlas resets the UVs instead of showing garbage
        p.writeAsset("broken.anim", atlasClip.toJson().toString().replace("packed.atlas", "missing.atlas"))
        val anim2 = Animator().apply { clip = "broken.anim"; current = "broken.anim"; playing = true; time = 0f }
        val sr2 = SpriteRenderer()
        system.update(anim2, sr2, 0.01f, playing = true)
        assertNull(sr2.animTexture)
        assertEquals(0f, sr2.uv[0], 0.0001f)
        assertEquals(1f, sr2.uv[2], 0.0001f)

        // grid clips keep working through the same code path
        p.writeAsset(
            "grid.anim",
            AnimationClip(texture = "sheet.png", columns = 4, rows = 2, frames = mutableListOf(4, 5), fps = 10f).toJson().toString(),
        )
        val anim3 = Animator().apply { clip = "grid.anim"; current = "grid.anim"; playing = true; time = 0f }
        val sr3 = SpriteRenderer()
        system.update(anim3, sr3, 0.11f, playing = true)
        assertEquals("sheet.png", sr3.animTexture)
        // cell 5 of a 4x2 grid -> col 1, row 1
        assertEquals(0.25f, sr3.uv[0], 0.0001f)
        assertEquals(0.5f, sr3.uv[2], 0.0001f)
        assertEquals(1f, sr3.uv[1], 0.0001f)
        assertEquals(0.5f, sr3.uv[3], 0.0001f)
    }

    @Test
    fun sheetDocHelpersRenameMoveAndUniquify() {
        val doc = SpriteSheetDoc(texture = "a.png")
        doc.addRect(0, 0, 8, 8)
        doc.addRect(8, 0, 8, 8)
        doc.addRect(16, 0, 8, 8)
        assertEquals("frame_02", doc.frames[2].name)

        assertTrue(doc.renameFrame(0, "walk_0"))
        assertFalse(doc.renameFrame(1, "walk_0")) // duplicate rejected
        assertFalse(doc.renameFrame(1, "   "))     // blank rejected
        assertEquals("walk_0", doc.frames[0].name)

        assertTrue(doc.moveFrame(2, 0))
        assertEquals("frame_02", doc.frames[0].name)
        assertEquals("walk_0", doc.frames[1].name) // shifted right by the move
        assertEquals("frame_01", doc.frames[2].name)
        assertFalse(doc.moveFrame(0, 99))

        assertTrue(doc.removeFrame(1))
        assertEquals(2, doc.frames.size)
        assertEquals("frame_00", doc.nextName())
    }
}
