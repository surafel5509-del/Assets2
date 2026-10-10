package com.sengine

import com.sengine.engine.overlay.ElementType
import com.sengine.engine.overlay.HAnchor
import com.sengine.engine.overlay.Kind
import com.sengine.engine.overlay.OverlayDocument
import com.sengine.engine.overlay.VAnchor
import com.sengine.project.AssetFolders
import com.sengine.studio.Argb
import com.sengine.studio.FitMode
import com.sengine.studio.SpriteDocument
import com.sengine.studio.Tool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Host-side tests for the pure models behind the asset tree, the Sprite Studio and the overlay editors. */
class StudioModelsTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    // ------------------------------------------------------------------ asset folders

    @Test
    fun assetsLandInTheirBranch() {
        assertEquals("Scripts", AssetFolders.folderFor("Player.js"))
        assertEquals("Blueprints", AssetFolders.folderFor("Door.bp"))
        assertEquals("Audio/SFX", AssetFolders.folderFor("jump.wav"))
        assertEquals("Audio/Music", AssetFolders.folderFor("level_music.ogg"))
        assertEquals("Sprites", AssetFolders.folderFor("hero.png"))
        assertEquals("Sprites/Studio", AssetFolders.folderFor("hero.png", studioOrigin = true))
        assertEquals("Animations", AssetFolders.folderFor("walk.anim"))
        assertEquals("Shaders", AssetFolders.folderFor("glow.glsl"))
        assertEquals("Models", AssetFolders.folderFor("tree.obj"))
        assertEquals("UI", AssetFolders.folderFor("hud.ui.json"))
        assertEquals("Controls", AssetFolders.folderFor("touch.ctrl.json"))
        assertEquals("Data", AssetFolders.folderFor("levels.json"))
        assertEquals("Data", AssetFolders.folderFor("notes.txt"))
    }

    @Test
    fun safeRelativePathsAreAccepted() {
        assertTrue(AssetFolders.isSafeRelative("Sprites/hero.png"))
        assertTrue(AssetFolders.isSafeRelative("hero.png"))
    }

    @Test
    fun unsafePathsAreRejected() {
        assertFalse(AssetFolders.isSafeRelative("../secret.js"))
        assertFalse(AssetFolders.isSafeRelative("Sprites/../../x.png"))
        assertFalse(AssetFolders.isSafeRelative("/etc/passwd"))
        assertFalse(AssetFolders.isSafeRelative("a\\b.png"))
        assertFalse(AssetFolders.isSafeRelative(""))
        assertFalse(AssetFolders.isSafeRelative("a//b.png"))
    }

    @Test
    fun uniqueKeepsFolderAndCompoundSuffix() {
        val taken = setOf("UI/hud.ui.json", "UI/hud_1.ui.json")
        assertEquals("UI/hud_2.ui.json", AssetFolders.unique("UI/hud.ui.json") { it in taken })
        assertEquals("Sprites/hero.png", AssetFolders.unique("Sprites/hero.png") { false })
        assertEquals("README_1", AssetFolders.unique("README") { it == "README" })
    }

    @Test
    fun ancestorsAreShallowestFirst() {
        assertEquals(listOf("Audio", "Audio/SFX"), AssetFolders.ancestorsOf("Audio/SFX/jump.wav"))
        assertEquals(emptyList<String>(), AssetFolders.ancestorsOf("hero.png"))
    }

    // ------------------------------------------------------------------ colour

    @Test
    fun hexRoundTrips() {
        assertEquals(0xFF112233.toInt(), Argb.parse("#112233"))
        assertEquals(0x80112233.toInt(), Argb.parse("#80112233"))
        assertNull(Argb.parse("#XYZ"))
        assertEquals("#FF112233", Argb.toHex(0xFF112233.toInt()))
    }

    @Test
    fun hsvRoundTripsPrimaries() {
        val h = Argb.toHsv(red)
        assertEquals(0f, h[0], 0.01f)
        assertEquals(1f, h[1], 0.01f)
        assertEquals(red, Argb.fromHsv(h[0], h[1], h[2]))
        assertEquals(blue, Argb.fromHsv(240f, 1f, 1f))
    }

    @Test
    fun overCompositesAndKeepsOpaque() {
        assertEquals(red, Argb.over(blue, red))
        assertEquals(blue, Argb.over(blue, 0))
        val half = Argb.over(0xFF000000.toInt(), 0x80FFFFFF.toInt())
        assertEquals(255, Argb.a(half))
        assertTrue(Argb.r(half) in 120..135)
    }

    // ------------------------------------------------------------------ sprite studio

    @Test
    fun pencilStrokeWritesActiveLayerOnly() {
        val d = SpriteDocument(8, 8)
        d.strokeSegment(1, 1, 4, 1, red)
        val img = d.compositeCurrent()
        assertEquals(red, img[1 * 8 + 1])
        assertEquals(red, img[1 * 8 + 4])
        assertEquals(0, img[2 * 8 + 1])
    }

    @Test
    fun eraserClearsPixels() {
        val d = SpriteDocument(4, 4)
        d.drawRect(0, 0, 3, 3, red, filled = true)
        d.strokeSegment(0, 0, 3, 0, 0, erase = true)
        assertEquals(0, d.compositeCurrent()[1])
        assertEquals(red, d.compositeCurrent()[4])
    }

    @Test
    fun symmetryMirrorsStamps() {
        val d = SpriteDocument(8, 8)
        d.symmetryX = true
        d.strokeSegment(1, 2, 1, 2, red)
        val img = d.compositeCurrent()
        assertEquals(red, img[2 * 8 + 1])
        assertEquals(red, img[2 * 8 + 6]) // mirror of x=1 across width 8
    }

    @Test
    fun floodFillStopsAtBoundaries() {
        val d = SpriteDocument(5, 5)
        d.drawRect(0, 0, 4, 4, blue, filled = false)
        d.floodFill(2, 2, red)
        val img = d.compositeCurrent()
        assertEquals(red, img[2 * 5 + 2])
        assertEquals(blue, img[0]) // the border is untouched
    }

    @Test
    fun filledEllipseFillsCentre() {
        val d = SpriteDocument(9, 9)
        d.drawEllipse(0, 0, 8, 8, red, filled = true)
        val img = d.compositeCurrent()
        assertEquals(red, img[4 * 9 + 4])
        assertEquals(0, img[0])
    }

    @Test
    fun lineIsContinuous() {
        val d = SpriteDocument(6, 6)
        d.drawLine(0, 0, 5, 5, red)
        val img = d.compositeCurrent()
        for (i in 0 until 6) assertEquals(red, img[i * 6 + i])
    }

    @Test
    fun lockedLayerIgnoresDrawing() {
        val d = SpriteDocument(4, 4)
        d.currentLayer.locked = true
        d.drawRect(0, 0, 3, 3, red, filled = true)
        assertEquals(0, d.compositeCurrent()[0])
    }

    @Test
    fun undoAndRedoRestoreStrokes() {
        val d = SpriteDocument(4, 4)
        d.pushUndo("Stroke")
        d.drawRect(0, 0, 3, 3, red, filled = true)
        assertEquals(red, d.compositeCurrent()[0])
        assertTrue(d.undo())
        assertEquals(0, d.compositeCurrent()[0])
        assertTrue(d.redo())
        assertEquals(red, d.compositeCurrent()[0])
    }

    @Test
    fun framesAddDuplicateAndDelete() {
        val d = SpriteDocument(4, 4)
        d.drawRect(0, 0, 3, 3, red, filled = true)
        d.addFrame(copy = true)
        assertEquals(2, d.frameCount)
        assertEquals(red, d.composite(1)[0])
        d.deleteFrame(0)
        assertEquals(1, d.frameCount)
        d.deleteFrame(0) // the last frame can never be deleted
        assertEquals(1, d.frameCount)
    }

    @Test
    fun resizeAnchorsPixels() {
        val d = SpriteDocument(2, 2)
        d.drawRect(0, 0, 1, 1, red, filled = true)
        d.resizeCanvas(4, 4, 1f, 1f)
        val img = d.compositeCurrent()
        assertEquals(red, img[3 * 4 + 3])
        assertEquals(0, img[0])
    }

    @Test
    fun importContainLetterboxes() {
        val d = SpriteDocument(4, 4)
        val src = IntArray(2) { red } // a 2x1 image
        d.importIntoActiveLayer(src, 2, 1, FitMode.CONTAIN)
        val img = d.compositeCurrent()
        assertEquals(red, img[2 * 4 + 0])
        assertEquals(0, img[0])
    }

    @Test
    fun sheetLaysFramesOutLeftToRight() {
        val d = SpriteDocument(2, 2)
        d.drawRect(0, 0, 1, 1, red, filled = true)
        d.addFrame(copy = false)
        d.drawRect(0, 0, 1, 1, blue, filled = true)
        val sheet = d.exportSheet()
        assertEquals(4, sheet.width)
        assertEquals(2, sheet.height)
        assertEquals(red, sheet.pixels[0])
        assertEquals(blue, sheet.pixels[2])
    }

    @Test
    fun fromImageDownscalesLargeImages() {
        val big = IntArray(512 * 256) { red }
        val d = SpriteDocument.fromImage(big, 512, 256, "big")
        assertEquals(SpriteDocument.MAX_SIZE, d.width)
        assertEquals(SpriteDocument.MAX_SIZE / 2, d.height)
        assertEquals(red, d.compositeCurrent()[0])
    }

    @Test
    fun gradeChangesHueButKeepsAlpha() {
        val d = SpriteDocument(1, 1)
        d.drawRect(0, 0, 0, 0, red, filled = true)
        d.grade(hue = 120f, saturation = 1f, brightness = 1f, contrast = 1f, allFrames = false)
        val c = d.compositeCurrent()[0]
        assertEquals(255, Argb.a(c))
        assertTrue(Argb.g(c) > Argb.r(c))
    }

    @Test
    fun outlineSurroundsOpaqueShape() {
        val d = SpriteDocument(5, 5)
        d.drawRect(2, 2, 2, 2, red, filled = true)
        d.outline(blue)
        assertEquals(blue, d.compositeCurrent()[2 * 5 + 1])
        assertEquals(0, d.compositeCurrent()[0])
    }

    @Test
    fun spriteFileRoundTrips() {
        val d = SpriteDocument(4, 3, "hero sprite")
        d.drawRect(0, 0, 1, 1, red, filled = true)
        d.addFrame(copy = true)
        d.drawLine(0, 2, 3, 2, blue)
        d.fps = 12f
        d.currentLayer.opacity = 0.5f
        val back = com.sengine.studio.SpriteFile.decode(com.sengine.studio.SpriteFile.encode(d))
        assertEquals(2, back.frameCount)
        assertEquals(4, back.width)
        assertEquals(3, back.height)
        assertEquals(12f, back.fps, 0.001f)
        assertEquals(d.composite(0).toList(), back.composite(0).toList())
        assertEquals(d.composite(1).toList(), back.composite(1).toList())
    }

    @Test
    fun toolsHaveLabels() {
        assertTrue(Tool.values().all { it.label.isNotBlank() })
    }

    // ------------------------------------------------------------------ overlay

    @Test
    fun anchoredRectsStayInTheirCorner() {
        val doc = OverlayDocument("hud", Kind.CONTROLS)
        val btn = doc.add(ElementType.BUTTON)
        btn.hAnchor = HAnchor.RIGHT; btn.vAnchor = VAnchor.BOTTOM
        btn.x = 0f; btn.y = 0f; btn.w = 0.1f; btn.h = 0.2f
        val wide = doc.rect(btn, 1600f, 800f)
        assertEquals(1440f, wide[0], 0.5f)
        assertEquals(640f, wide[1], 0.5f)
        assertEquals(1600f, wide[2], 0.5f)
        val tall = doc.rect(btn, 800f, 1600f)
        assertEquals(720f, tall[0], 0.5f)
        assertEquals(1280f, tall[1], 0.5f)
    }

    @Test
    fun hitTestReturnsTopmostInteractive() {
        val doc = OverlayDocument("hud", Kind.UI)
        val panel = doc.add(ElementType.PANEL)
        panel.w = 0.5f; panel.h = 0.5f
        val label = doc.add(ElementType.LABEL)
        label.w = 0.5f; label.h = 0.5f
        val btn = doc.add(ElementType.BUTTON)
        btn.hAnchor = HAnchor.CENTER; btn.vAnchor = VAnchor.MIDDLE; btn.x = 0f; btn.y = 0f
        btn.w = 0.2f; btn.h = 0.2f
        assertEquals(btn.id, doc.hitTest(500f, 500f, 1000f, 1000f)?.id)
        assertNull(doc.hitTest(10f, 10f, 1000f, 1000f))
    }

    @Test
    fun duplicatedElementsGetFreshIds() {
        val doc = OverlayDocument("c", Kind.CONTROLS)
        val a = doc.add(ElementType.BUTTON)
        val b = doc.duplicate(a.id)!!
        assertTrue(a.id != b.id)
        assertEquals(2, doc.elements.size)
    }

    // ------------------------------------------------------------------ shader presets

    @Test
    fun shaderPresetsAreWellFormedEffects() {
        val presets = com.sengine.engine.render.ShaderPresets.ALL
        assertTrue(presets.size >= 10)
        assertEquals(presets.size, presets.map { it.name }.toSet().size)
        for (p in presets) {
            assertTrue("${p.name} defines effect()", p.source.contains("vec4 effect(vec4 color, vec2 uv)"))
            assertFalse("${p.name} has no dollar signs", p.source.contains('$'))
            var depth = 0
            for (ch in p.source) { if (ch == '{') depth++; if (ch == '}') depth-- ; assertTrue("${p.name} braces", depth >= 0) }
            assertEquals("${p.name} braces balance", 0, depth)
            assertTrue("${p.name} lives under Shaders/", p.fileName.startsWith("Shaders/") && p.fileName.endsWith(".glsl"))
            assertTrue("${p.name} category is known", com.sengine.engine.render.ShaderPresets.CATEGORIES.contains(p.category))
        }
        assertEquals("hit_flash", com.sengine.engine.render.ShaderPresets.slug("Hit Flash"))
        assertEquals("damage_vignette", com.sengine.engine.render.ShaderPresets.slug("  Damage -- Vignette "))
    }
}
