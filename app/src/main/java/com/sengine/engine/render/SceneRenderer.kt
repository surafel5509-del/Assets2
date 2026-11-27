package com.sengine.engine.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.sengine.engine.Engine
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.Terrain
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.Tilemap
import com.sengine.engine.core.UIButton
import com.sengine.engine.core.UICanvas
import com.sengine.engine.core.UIImage
import com.sengine.engine.core.UIRect
import com.sengine.engine.core.UIText
import com.sengine.engine.core.UIProgressBar
import com.sengine.engine.math.Affine
import com.sengine.engine.math.Mat4
import com.sengine.project.AtlasCache
import com.sengine.project.MaterialCache
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Draws the scene (2D or 3D), the screen-space UI layer, post effects and — when [editor]
 * is non-null and the engine is in edit mode — editor grids and gizmos.
 */
class SceneRenderer(private val engine: Engine, private val editor: EditorState?) : GLSurfaceView.Renderer {

    private val r = Renderer2D()
    private val r3 = Renderer3D()
    private val shaders = ShaderLibrary(engine.project) { engine.log(2, it) }
    private val post = PostProcessor()
    private val textures = TextureCache(engine.project)
    private val gameView3D = View3D()
    private val shakeView = View2D()
    private val uiView = View2D()
    private var lastNs = 0L
    private var width = 1
    private var height = 1
    private val tmp = Affine()
    private val tmp2 = Affine()
    private val m4 = FloatArray(16)
    private val m4b = FloatArray(16)
    private val rnd = java.util.Random()
    // frustum culling (6 planes, play mode only) + terrain draw scratch
    private val frustum = FloatArray(24)
    private val terrainMr = MeshRenderer()
    // Big Edition: asset-backed materials / atlases, tilemaps and screen-space canvas UI
    private val materialCache = MaterialCache(engine.project)
    private val atlasCache = AtlasCache(engine.project)
    private val scratchSprite = SpriteRenderer()
    private val scratchMesh = MeshRenderer()
    private val scratchUv = FloatArray(4)
    private val scratchUvUI = FloatArray(4)
    private val tileLocal = Affine()
    private val tileWorld = Affine()
    private val uiAffine = Affine()
    private val defaultCanvas = UICanvas()

    private data class UIEntry(
        val go: GameObject, val canvas: UICanvas, val layout: UIRect.Layout,
        val order: Int, val index: Int,
    )

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        shaders.init()
        r.init(shaders)
        r3.init(shaders)
        post.init()
        textures.clear()
        lastNs = System.nanoTime()
    }

    /**
     * Frees shaders, textures and FBOs on the GL thread. GLSurfaceView has no
     * onSurfaceDestroyed callback, so EditorActivity / PlayerActivity call this
     * in a queueEvent before the view is destroyed.
     */
    fun release() {
        shaders.release()
        r.release()
        r3.release()
        post.release()
        textures.release()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width; this.height = height
        GLES20.glViewport(0, 0, width, height)
        engine.gameView.widthPx = width; engine.gameView.heightPx = height
        editor?.view?.let { it.widthPx = width; it.heightPx = height }
        editor?.view3D?.let { it.widthPx = width; it.heightPx = height }
        gameView3D.widthPx = width; gameView3D.heightPx = height
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNs) / 1e9f).coerceIn(0f, 0.1f)
        lastNs = now
        synchronized(engine.lock) {
            engine.tick(dt)
            val t0 = System.nanoTime()
            r.drawCalls = 0; r3.drawCalls = 0
            val time = engine.time.toFloat()
            r.time = time; r3.time = time
            r.resW = width.toFloat(); r.resH = height.toFloat(); r3.resW = r.resW; r3.resH = r.resH
            val editing = editor != null && engine.mode == Engine.Mode.EDIT
            val cam3 = engine.mainCamera3D()
            val use3D = if (editing) editor?.mode3D == true else cam3 != null

            // post effect selection (play mode only)
            var postProg: PostProgram? = null
            var intensity = 1f
            if (!editing) {
                var fx = 0; var shader = ""
                if (use3D) cam3?.get<Camera3D>()?.let { fx = it.postFx; shader = it.postShader; intensity = it.postIntensity }
                else engine.mainCamera()?.get<Camera2D>()?.let { fx = it.postFx; shader = it.postShader; intensity = it.postIntensity }
                if (fx > 0) postProg = shaders.post(if (fx == POST_CUSTOM) 0 else fx, if (fx == POST_CUSTOM) shader else "")
            }
            if (postProg != null) post.begin(width, height)

            if (use3D) render3D(editing, cam3) else render2D(editing)
            drawScreenUI(editing, use3D)

            if (post.active && postProg != null) r.drawCalls += post.end(postProg, time, intensity)
            engine.drawCalls = r.drawCalls + r3.drawCalls
            engine.renderMs = (System.nanoTime() - t0) / 1e6f
        }
    }

    // ------------------------------------------------------------------ 2D

    private fun render2D(editing: Boolean) {
        val view: View2D
        if (editing) view = editor!!.view
        else {
            shakeView.copyFrom(engine.gameView)
            val shake = engine.mainCamera()?.get<Camera2D>()?.shake ?: 0f
            if (shake > 0f) {
                shakeView.cx += (rnd.nextFloat() * 2f - 1f) * shake * 0.25f
                shakeView.cy += (rnd.nextFloat() * 2f - 1f) * shake * 0.25f
            }
            view = shakeView
        }
        val bg = if (editing) 0xFF262B33.toInt() else engine.backgroundColor()
        GLES20.glClearColor(GL.r(bg), GL.g(bg), GL.b(bg), 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        r.begin(view)
        if (editing && editor!!.showGrid) drawGrid(view)
        val ppu = view.pixelsPerUnit
        for (go in sortedObjects()) {
            val ui = isScreenSpace(go)
            if (ui && !editing) continue
            go.get<Tilemap>()?.let { drawTilemap(go, it, ppu) }
            drawObject2D(go, ppu, null)
        }
        if (editing) drawEditorOverlay(view, editor!!)
    }

    private fun sortedObjects(): List<GameObject> =
        engine.scene.objects.withIndex()
            .filter { it.value.isActiveInHierarchy() }
            .sortedWith(compareBy({ it.value.order }, { it.index }))
            .map { it.value }

    private fun isScreenSpace(go: GameObject): Boolean =
        go.get<SpriteRenderer>()?.screenSpace == true || go.get<TextRenderer>()?.screenSpace == true

    /** Draws sprite / text / particles of [go]. When [m3] is given the object is placed in 3D with that matrix. */
    private fun drawObject2D(go: GameObject, ppu: Float, m3: FloatArray?) {
        val w = go.world
        go.get<SpriteRenderer>()?.let { original ->
            val sr = materialCache.get(original.material)?.let { mat ->
                scratchSprite.copyFrom(original).also { mat.applyTo(it) }
            } ?: original
            var texName = sr.animTexture ?: sr.texture
            var uv: FloatArray? = sr.uv
            val atlas = if (sr.atlas.isNotBlank() && sr.animTexture == null) atlasCache.atlas(sr.atlas) else null
            if (atlas != null) {
                if (atlas.texture.isNotBlank()) texName = atlas.texture
                if (atlas.uv(sr.frame, scratchUv)) uv = scratchUv
            }
            val tex = if (texName.isNotBlank()) textures.image(texName) else null
            val prog = if (sr.shader.isNotBlank()) shaders.sprite(sr.shader) else null
            val shape = if (tex != null) 0 else sr.shape
            if (m3 != null) r.quadModel(m3, sr.color, shape, tex, 200f, sr.flipX, sr.flipY, uv, prog, sr.shaderParam)
            else r.quad(w, sr.color, shape, tex, min(w.scaleX, w.scaleY) * ppu, sr.flipX, sr.flipY, uv, prog, sr.shaderParam)
        }
        go.get<TextRenderer>()?.let { tr ->
            if (tr.text.isNotEmpty()) {
                val tex = textures.text(tr.text, tr.bold, tr.align, tr.font)
                val lines = tr.text.count { it == '\n' } + 1
                val hh = tr.size * lines
                val ww = hh * tex.w / tex.h
                val ox = when (tr.align) { 0 -> ww / 2; 2 -> -ww / 2; else -> 0f }
                if (m3 != null) {
                    Mat4.trs(m4b, ox, 0f, 0f, 0f, 0f, 0f, ww, hh, 1f)
                    Mat4.mul(m4, m3, m4b)
                    r.quadModel(m4, tr.color, 0, tex, 100f)
                } else {
                    tmp2.a = ww; tmp2.b = 0f; tmp2.c = 0f; tmp2.d = hh; tmp2.tx = ox; tmp2.ty = 0f
                    tmp.setMul(w, tmp2)
                    r.quad(tmp, tr.color, 0, tex, 100f)
                }
            }
        }
        go.getAny<ParticleEmitter>()?.let { pe -> drawParticles(pe, ppu, if (m3 != null) m3[14] else null) }
    }

    private fun drawParticles(pe: ParticleEmitter, ppu: Float, z: Float?) {
        if (pe.particles.isEmpty()) return
        val tex = if (pe.texture.isNotBlank()) textures.image(pe.texture) else null
        if (pe.additive) GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
        for (p in pe.particles) {
            val t = (p.age / p.life).coerceIn(0f, 1f)
            val size = pe.startSize + (pe.endSize - pe.startSize) * t
            if (size <= 0f) continue
            val c = lerpColor(pe.startColor, pe.endColor, t)
            if (z != null) {
                Mat4.trs(m4, p.x, p.y, z, 0f, 0f, 0f, size, size, size)
                r.quadModel(m4, c, if (tex != null) 0 else 1, tex, 100f)
            } else r.rect(p.x, p.y, size, size, c, if (tex != null) 0 else 1, ppu, tex)
        }
        if (pe.additive) GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    }

    /** Draws a Tilemap as transformed quads with tileset UVs or palette colours. */
    private fun drawTilemap(go: GameObject, tm: Tilemap, ppu: Float) {
        val w = go.world
        val tex = if (tm.tileset.isNotBlank()) textures.image(tm.tileset) else null
        val columns = tm.columns.coerceAtLeast(1)
        val rows = tm.rows.coerceAtLeast(1)
        for (tile in tm.tiles(go)) {
            val sheetCol = tile.id % columns
            val sheetRow = (tile.id / columns).coerceAtMost(rows - 1)
            scratchUv[0] = sheetCol.toFloat() / columns
            scratchUv[1] = (sheetRow + 1f) / rows
            scratchUv[2] = (sheetCol + 1f) / columns
            scratchUv[3] = sheetRow.toFloat() / rows
            tileLocal.setTRS(
                (tile.col + 0.5f) * tm.tileSize, (tile.row + 0.5f) * tm.tileSize,
                0f, tm.tileSize, tm.tileSize,
            )
            tileWorld.setMul(w, tileLocal)
            val color = if (tex != null) tm.color else tile.color
            val aa = minOf(tileWorld.scaleX, tileWorld.scaleY).coerceAtLeast(0.01f) * ppu
            r.quad(tileWorld, color, 0, tex, aa, false, false, if (tex != null) scratchUv else null)
        }
    }

    private fun lerpColor(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) + ((((b shr s) and 0xFF) - ((a shr s) and 0xFF)) * t)).toInt() and 0xFF
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    // ------------------------------------------------------------------ Screen-space UI

    private fun drawScreenUI(editing: Boolean, use3D: Boolean) {
        // Legacy screen-space sprites are drawn in world space while editing 2D;
        // the new canvas UI always uses this screen-space pass.
        val legacy = sortedObjects().filter { isScreenSpace(it) && !(editing && !use3D) }
        val entries = uiEntries()
        if (legacy.isEmpty() && entries.isEmpty()) return
        uiView.cx = 0f; uiView.cy = 0f; uiView.size = 5f; uiView.widthPx = width; uiView.heightPx = height
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        r.begin(uiView)
        val ppu = uiView.pixelsPerUnit
        for (go in legacy) drawObject2D(go, ppu, null)
        val worldW = uiView.halfW * 2f
        val worldH = uiView.size * 2f
        val left = uiView.cx - uiView.halfW
        val bottom = uiView.cy - uiView.size
        for (entry in entries) drawUIEntry(entry, ppu, worldW, worldH, left, bottom)
    }

    private fun canvasFor(go: GameObject): UICanvas? {
        var cur: GameObject? = go
        while (cur != null) {
            val c = cur.getAny<UICanvas>()
            if (c != null) return if (c.enabled) c else null
            cur = cur.parent
        }
        return defaultCanvas
    }

    private fun hasUIDrawable(go: GameObject): Boolean =
        go.get<UIImage>() != null || go.get<UIText>() != null ||
            go.get<UIButton>() != null || go.get<UIProgressBar>() != null

    private fun uiEntries(): List<UIEntry> {
        val out = ArrayList<UIEntry>()
        for ((index, go) in engine.scene.objects.withIndex()) {
            if (!go.isActiveInHierarchy()) continue
            val rect = go.get<UIRect>() ?: continue
            if (!hasUIDrawable(go)) continue
            val canvas = canvasFor(go) ?: continue
            out.add(UIEntry(go, canvas, rect.layout(canvas.frame(width, height)), go.order, index))
        }
        return out.sortedWith(compareBy({ it.canvas.sortOrder }, { it.order }, { it.index }))
    }

    private fun drawUIEntry(entry: UIEntry, ppu: Float, worldW: Float, worldH: Float, left: Float, bottom: Float) {
        val l = entry.layout
        val rectW = l.width * worldW
        val rectH = l.height * worldH
        if (rectW <= 0f || rectH <= 0f) return
        val cx = left + (l.left + l.width * 0.5f) * worldW
        val cy = bottom + (l.bottom + l.height * 0.5f) * worldH
        val op = entry.canvas.opacity.coerceIn(0f, 1f)
        val go = entry.go

        go.get<UIImage>()?.let { img ->
            var texName = img.texture
            var uv: FloatArray? = null
            val atlas = if (img.atlas.isNotBlank()) atlasCache.atlas(img.atlas) else null
            if (atlas != null) {
                if (atlas.texture.isNotBlank()) texName = atlas.texture
                if (atlas.uv(img.frame, scratchUvUI)) uv = scratchUvUI
            }
            val tex = if (texName.isNotBlank()) textures.image(texName) else null
            val prog = if (img.shader.isNotBlank()) shaders.sprite(img.shader) else null
            uiAffine.setTRS(cx, cy, 0f, rectW, rectH)
            r.quad(
                uiAffine, withOpacity(img.color, op), if (tex != null) 0 else img.shape,
                tex, minOf(rectW, rectH) * ppu, img.flipX, img.flipY, uv, prog, img.shaderParam,
            )
        }

        val button = go.get<UIButton>()
        if (button != null) {
            val tex = if (button.texture.isNotBlank()) textures.image(button.texture) else null
            val base = if (button.clicked || button.pressed) button.pressedColor else button.color
            uiAffine.setTRS(cx, cy, 0f, rectW, rectH)
            r.quad(uiAffine, withOpacity(base, op), 0, tex, minOf(rectW, rectH) * ppu)
            if (button.label.isNotEmpty()) {
                drawUIText(
                    button.label, true, 1, button.textColor, "", cx, cy, rectW, rectH,
                    (l.height * 0.55f).coerceIn(0.005f, 0.2f), op, worldH,
                )
            }
        }

        go.get<UIProgressBar>()?.let { bar ->
            if (bar.showBackground) r.rect(cx, cy, rectW, rectH, withOpacity(bar.backgroundColor, op), 0, ppu)
            val n = bar.normalized
            if (n > 0f) {
                var fw = rectW; var fh = rectH; var fx = cx; var fy = cy
                when (bar.direction) {
                    0 -> { fw = rectW * n; fx = cx - rectW * 0.5f + fw * 0.5f }
                    1 -> { fw = rectW * n; fx = cx + rectW * 0.5f - fw * 0.5f }
                    2 -> { fh = rectH * n; fy = cy - rectH * 0.5f + fh * 0.5f }
                    else -> { fh = rectH * n; fy = cy + rectH * 0.5f - fh * 0.5f }
                }
                r.rect(fx, fy, fw, fh, withOpacity(bar.fillColor, op), 0, ppu)
            }
        }

        val label = go.get<UIText>()
        if (label != null && button == null && label.text.isNotEmpty()) {
            drawUIText(label.text, label.bold, label.align, label.color, label.font, cx, cy, rectW, rectH, label.size, op, worldH)
        }
    }

    private fun drawUIText(
        text: String, bold: Boolean, align: Int, color: Int, fontName: String,
        cx: Float, cy: Float, rectW: Float, rectH: Float, sizeNorm: Float, opacity: Float, worldH: Float,
    ) {
        val tex = textures.text(text, bold, align, fontName)
        val lines = text.count { it == '\n' } + 1
        var hh = (sizeNorm.coerceIn(0.005f, 0.5f) * worldH * lines).coerceAtLeast(0.001f)
        var ww = (hh * tex.w / tex.h.coerceAtLeast(1)).coerceAtLeast(0.001f)
        val fit = minOf(1f, rectW / ww, rectH / hh)
        ww *= fit; hh *= fit
        val ox = when (align) { 0 -> -rectW * 0.5f + ww * 0.5f; 2 -> rectW * 0.5f - ww * 0.5f; else -> 0f }
        uiAffine.setTRS(cx + ox, cy, 0f, ww, hh)
        r.quad(uiAffine, withOpacity(color, opacity), 0, tex, 100f)
    }

    private fun withOpacity(color: Int, opacity: Float): Int {
        val a = (color ushr 24) and 0xFF
        val na = (a * opacity.coerceIn(0f, 1f)).toInt().coerceIn(0, 255)
        return (na shl 24) or (color and 0x00FFFFFF)
    }

    // ------------------------------------------------------------------ 3D

    private fun render3D(editing: Boolean, camGo: GameObject?) {
        val scene = engine.scene
        val cam = camGo?.get<Camera3D>()
        val v: View3D
        if (editing) {
            editor!!.updateView3D()
            v = editor!!.view3D
        } else {
            v = gameView3D
            if (cam != null) {
                v.fov = cam.fov; v.near = cam.near; v.far = cam.far
                v.projection = cam.projection; v.orthoSize = cam.orthoSize
                val target = if (cam.lookAtTarget && cam.follow.isNotBlank()) scene.find(cam.follow)?.world3 else null
                val jx = if (cam.shake > 0f) (rnd.nextFloat() * 2f - 1f) * cam.shake * 0.2f else 0f
                val jy = if (cam.shake > 0f) (rnd.nextFloat() * 2f - 1f) * cam.shake * 0.2f else 0f
                v.setFromWorld(camGo.world3, target?.let { floatArrayOf(it[12], it[13], it[14]) }, jx, jy)
            } else {
                v.projection = 0
                v.orthoSize = 8f
            }
        }
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        r3.setupLights(scene, v)
        r3.drawSky(v, cam?.skyTop ?: 0xFF3B7BD4.toInt(), cam?.skyHorizon ?: 0xFFBFD8F0.toInt())

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glDepthMask(true)

        val objs = sortedObjects()
        if (!editing) updateFrustum(v)
        val transparent = ArrayList<Pair<Float, GameObject>>()
        // opaque meshes + terrain: frustum-culled and grouped by (shader, texture) to cut state changes
        val opaque = ArrayList<Pair<String, GameObject>>()
        for (go in objs) {
            val mr = go.get<MeshRenderer>()
            val ter = if (mr == null) go.get<Terrain>() else null
            if (mr == null && ter == null) continue
            val color = mr?.color ?: ter!!.color
            if (GL.a(color) < 0.999f) {
                transparent.add(v.distanceTo(go.world3[12], go.world3[13], go.world3[14]) to go)
                continue
            }
            if (!editing && !inFrustum(go)) continue
            val key = (mr?.shader ?: "") + '\u0001' + (mr?.texture ?: ter?.texture ?: "")
            opaque.add(key to go)
        }
        for ((_, go) in opaque.sortedBy { it.first }) {
            val mr = go.get<MeshRenderer>()
            if (mr != null) drawMesh(go, mr) else drawTerrain(go, go.get<Terrain>()!!)
        }
        // transparent meshes, sprites, text & particles: back to front, no depth writes
        GLES20.glDepthMask(false)
        for (go in objs) {
            if (go.get<MeshRenderer>() != null || go.get<Terrain>() != null || isScreenSpace(go)) continue
            if (go.get<SpriteRenderer>() == null && go.get<TextRenderer>() == null && go.getAny<ParticleEmitter>() == null) continue
            transparent.add(v.distanceTo(go.world3[12], go.world3[13], go.world3[14]) to go)
        }
        r.begin(v.viewProj)
        for ((_, go) in transparent.sortedByDescending { it.first }) {
            val mr = go.get<MeshRenderer>()
            if (mr != null) drawMesh(go, mr) else drawObject2D(go, 100f, go.world3)
        }
        GLES20.glDepthMask(true)

        if (editing) drawEditorOverlay3D(v, editor!!)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawMesh(go: GameObject, mr: MeshRenderer) {
        val drawMr = materialCache.get(mr.material)?.let { mat ->
            scratchMesh.copyFrom(mr).also { mat.applyTo(it) }
        } ?: mr
        val mesh = if (drawMr.mesh == MeshRenderer.MESHES.size - 1) {
            (if (drawMr.model.isNotBlank()) Meshes.model(engine.project.assetFile(drawMr.model)) else null) ?: Meshes.primitive(0)
        } else Meshes.primitive(drawMr.mesh)
        val texName = if (drawMr.texture.isNotBlank()) drawMr.texture
        else if (drawMr.model.isNotBlank()) Meshes.modelTexture(engine.project.assetFile(drawMr.model))
            ?.takeIf { engine.project.assetFile(it).isFile } ?: ""
        else ""
        val tex = if (texName.isNotBlank()) textures.image(texName) else null
        val prog = if (drawMr.shader.isNotBlank()) shaders.mesh(drawMr.shader) else null
        r3.drawMesh(mesh, go.world3, drawMr, tex, prog)
    }

    /** Draws a Terrain component with a shared scratch MeshRenderer (no per-object material cost). */
    private fun drawTerrain(go: GameObject, t: Terrain) {
        val mesh = Meshes.terrain(t)
        terrainMr.color = t.color
        terrainMr.texture = t.texture
        terrainMr.tiling = t.tiling
        terrainMr.specular = t.specular
        terrainMr.shininess = 32f
        terrainMr.emission = t.emission
        terrainMr.unlit = false
        terrainMr.shader = ""
        terrainMr.shaderParam = 1f
        val tex = if (t.texture.isNotBlank()) textures.image(t.texture) else null
        r3.drawMesh(mesh, go.world3, terrainMr, tex, null)
    }

    private fun meshBounds(go: GameObject): Pair<FloatArray, FloatArray> {
        go.get<Terrain>()?.let { t ->
            val h = t.amplitude + 1f
            return floatArrayOf(-t.size / 2f, -h, -t.size / 2f) to floatArrayOf(t.size / 2f, h, t.size / 2f)
        }
        val mr = go.get<MeshRenderer>()
        if (mr != null) {
            val mesh = if (mr.mesh == MeshRenderer.MESHES.size - 1) {
                (if (mr.model.isNotBlank()) Meshes.model(engine.project.assetFile(mr.model)) else null) ?: Meshes.primitive(0)
            } else Meshes.primitive(mr.mesh)
            return mesh.min to mesh.max
        }
        return floatArrayOf(-0.5f, -0.5f, -0.05f) to floatArrayOf(0.5f, 0.5f, 0.05f)
    }

    // ------------------------------------------------------------ frustum culling
    /** Extracts the 6 frustum planes from the view-projection matrix (Gribb-Hartmann, GL clip space). */
    private fun updateFrustum(v: View3D) {
        val m = v.viewProj
        fun row(r: Int) = floatArrayOf(m[r], m[4 + r], m[8 + r], m[12 + r])
        val r0 = row(0); val r1 = row(1); val r2 = row(2); val r3 = row(3)
        fun set(i: Int, a: FloatArray, s: Float) {
            var x = r3[0] + s * a[0]
            var y = r3[1] + s * a[1]
            var z = r3[2] + s * a[2]
            var w = r3[3] + s * a[3]
            val l = kotlin.math.sqrt(x * x + y * y + z * z).coerceAtLeast(1e-8f)
            x /= l; y /= l; z /= l; w /= l
            frustum[i * 4] = x; frustum[i * 4 + 1] = y; frustum[i * 4 + 2] = z; frustum[i * 4 + 3] = w
        }
        set(0, r0, 1f)   // left
        set(1, r0, -1f)  // right
        set(2, r1, 1f)   // bottom
        set(3, r1, -1f)  // top
        set(4, r2, 1f)   // near
        set(5, r2, -1f)  // far
    }

    /** True when the object's world-space bounds intersect the current frustum. */
    private fun inFrustum(go: GameObject): Boolean {
        val w = go.world3
        val (mn, mx) = meshBounds(go)
        var minx = Float.MAX_VALUE; var miny = Float.MAX_VALUE; var minz = Float.MAX_VALUE
        var maxx = -Float.MAX_VALUE; var maxy = -Float.MAX_VALUE; var maxz = -Float.MAX_VALUE
        for (i in 0 until 8) {
            val lx = if (i and 1 == 0) mn[0] else mx[0]
            val ly = if (i and 2 == 0) mn[1] else mx[1]
            val lz = if (i and 4 == 0) mn[2] else mx[2]
            val p = Mat4.point(w, lx, ly, lz)
            if (p[0] < minx) minx = p[0]; if (p[0] > maxx) maxx = p[0]
            if (p[1] < miny) miny = p[1]; if (p[1] > maxy) maxy = p[1]
            if (p[2] < minz) minz = p[2]; if (p[2] > maxz) maxz = p[2]
        }
        for (p in 0 until 6) {
            val a = frustum[p * 4]; val b = frustum[p * 4 + 1]; val c = frustum[p * 4 + 2]; val d = frustum[p * 4 + 3]
            val px = if (a >= 0f) maxx else minx
            val py = if (b >= 0f) maxy else miny
            val pz = if (c >= 0f) maxz else minz
            if (a * px + b * py + c * pz + d < 0f) return false
        }
        return true
    }

    private fun drawEditorOverlay3D(v: View3D, ed: EditorState) {
        r.begin(v.viewProj)
        if (ed.showGrid) {
            val step = if (ed.distance > 60f) 5f else 1f
            val n = 40
            val cx = floor(ed.orbitX / step) * step
            val cz = floor(ed.orbitZ / step) * step
            for (i in -n..n) {
                val x = cx + i * step
                val z = cz + i * step
                val cX = if (abs(x) < 1e-3f) 0xAA4D8CFF.toInt() else if (Math.round(x / step) % 5 == 0) 0x44FFFFFF else 0x22FFFFFF
                val cZ = if (abs(z) < 1e-3f) 0xAAFF5C5C.toInt() else if (Math.round(z / step) % 5 == 0) 0x44FFFFFF else 0x22FFFFFF
                r.line3(x, 0f, cz - n * step, x, 0f, cz + n * step, cX)
                r.line3(cx - n * step, 0f, z, cx + n * step, 0f, z, cZ)
            }
            r.flushLines(1f)
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        for (go in engine.scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val w = go.world3
            val x = w[12]; val y = w[13]; val z = w[14]
            val s = ed.gizmoLength3D(x, y, z) * 0.25f
            go.get<Camera3D>()?.let { cam ->
                val d = s * 3f
                val hh = if (cam.projection == 1) cam.orthoSize
                else (Math.tan(Math.toRadians(cam.fov / 2.0)) * d).toFloat()
                val hw = hh * v.aspect
                val corners = arrayOf(floatArrayOf(-hw, -hh, -d), floatArrayOf(hw, -hh, -d), floatArrayOf(hw, hh, -d), floatArrayOf(-hw, hh, -d)).map { Mat4.point(w, it[0], it[1], it[2]) }
                for (i in 0 until 4) {
                    val a = corners[i]; val b = corners[(i + 1) % 4]
                    r.line3(x, y, z, a[0], a[1], a[2], 0xCCFFFFFF.toInt())
                    r.line3(a[0], a[1], a[2], b[0], b[1], b[2], 0xCCFFFFFF.toInt())
                }
            }
            go.get<Light>()?.let { l ->
                val c = l.color or 0xFF000000.toInt()
                if (l.kind == 1) {
                    r.circle3(x, y, z, s, 0, c, 16); r.circle3(x, y, z, s, 1, c, 16); r.circle3(x, y, z, s, 2, c, 16)
                    if (go.id == ed.selectedId) r.circle3(x, y, z, l.range, 1, 0x66FFFFFF, 48)
                } else if (l.kind == 2) {
                    r.circle3(x, y, z, s, 2, c, 16)
                    val reach = l.range.coerceAtMost(50f)
                    val rad = (Math.tan(Math.toRadians(l.coneAngle.toDouble())) * reach).toFloat()
                    val ring = Array(8) { i ->
                        val a = i * Math.PI * 2.0 / 8.0
                        Mat4.point(w, (Math.cos(a) * rad).toFloat(), (Math.sin(a) * rad).toFloat(), -reach)
                    }
                    for (i in ring.indices) {
                        val q = ring[i]; val n = ring[(i + 1) % ring.size]
                        r.line3(x, y, z, q[0], q[1], q[2], c)
                        r.line3(q[0], q[1], q[2], n[0], n[1], n[2], c)
                    }
                } else {
                    r.circle3(x, y, z, s, 2, c, 16)
                    val d = Mat4.dir(w, 0f, 0f, -1f)
                    r.line3(x, y, z, x + d[0] * s * 4, y + d[1] * s * 4, z + d[2] * s * 4, c)
                }
            }
            if (ed.showColliders || go.id == ed.selectedId) go.get<Collider3D>()?.let { c ->
                val color = if (c.isTrigger) 0xCC4FC3F7.toInt() else 0xCC66FF66.toInt()
                if (c.shape == 1) {
                    val p = Mat4.point(w, c.centerX, c.centerY, c.centerZ)
                    val rad = c.radius * max(Mat4.scaleOf(w, 0), max(Mat4.scaleOf(w, 1), Mat4.scaleOf(w, 2)))
                    r.circle3(p[0], p[1], p[2], rad, 0, color); r.circle3(p[0], p[1], p[2], rad, 1, color); r.circle3(p[0], p[1], p[2], rad, 2, color)
                } else {
                    r.wireBox(w, floatArrayOf(c.centerX - c.sizeX / 2, c.centerY - c.sizeY / 2, c.centerZ - c.sizeZ / 2),
                        floatArrayOf(c.centerX + c.sizeX / 2, c.centerY + c.sizeY / 2, c.centerZ + c.sizeZ / 2), color)
                }
            }
            if (go.get<MeshRenderer>() == null && go.get<Camera3D>() == null && go.get<Light>() == null &&
                go.get<SpriteRenderer>() == null && go.get<TextRenderer>() == null) {
                r.line3(x - s, y, z, x + s, y, z, 0x99FFFFFF.toInt())
                r.line3(x, y - s, z, x, y + s, z, 0x99FFFFFF.toInt())
                r.line3(x, y, z - s, x, y, z + s, 0x99FFFFFF.toInt())
            }
        }
        r.flushLines(2f)
        val sel = engine.scene.findById(ed.selectedId)
        if (sel != null && sel.isActiveInHierarchy()) {
            val (mn, mx) = meshBounds(sel)
            r.wireBox(sel.world3, mn, mx, 0xFFFF9F1C.toInt())
            r.flushLines(2f)
            drawGizmo3D(sel, ed)
        }
    }

    private fun drawGizmo3D(go: GameObject, ed: EditorState) {
        val w = go.world3
        val x = w[12]; val y = w[13]; val z = w[14]
        val len = ed.gizmoLength3D(x, y, z)
        val hi = 0xFFFFFF66.toInt()
        val red = if (ed.activeAxis == 1) hi else 0xFFFF4D4D.toInt()
        val green = if (ed.activeAxis == 2) hi else 0xFF5CE65C.toInt()
        val blue = if (ed.activeAxis == 3) hi else 0xFF4D8CFF.toInt()
        when (ed.tool) {
            Tool.MOVE, Tool.SCALE -> {
                r.line3(x, y, z, x + len, y, z, red)
                r.line3(x, y, z, x, y + len, z, green)
                r.line3(x, y, z, x, y, z + len, blue)
                r.flushLines(5f)
                val hs = len * 0.08f
                val mn = floatArrayOf(-hs, -hs, -hs); val mx = floatArrayOf(hs, hs, hs)
                val id = Mat4.identity()
                fun tip(px: Float, py: Float, pz: Float, c: Int) { id[12] = px; id[13] = py; id[14] = pz; r.wireBox(id, mn, mx, c) }
                tip(x + len, y, z, red); tip(x, y + len, z, green); tip(x, y, z + len, blue)
                tip(x, y, z, if (ed.activeAxis == 4) hi else 0xFFFFD24D.toInt())
                r.flushLines(3f)
            }
            Tool.ROTATE -> {
                r.circle3(x, y, z, len, 0, red, 64)
                r.circle3(x, y, z, len, 1, green, 64)
                r.circle3(x, y, z, len, 2, blue, 64)
                r.flushLines(3f)
            }
            Tool.HAND -> {}
        }
    }

    // ------------------------------------------------------------------ 2D editor overlay

    private fun drawGrid(view: View2D) {
        val ppu = view.pixelsPerUnit
        var step = 1f
        while (step * ppu < 24f) step *= 5f
        while (step * ppu > 200f) step /= 5f
        val left = view.cx - view.halfW
        val right = view.cx + view.halfW
        val bottom = view.cy - view.size
        val top = view.cy + view.size
        val minor = 0x22FFFFFF
        val major = 0x44FFFFFF
        var x = floor(left / step) * step
        var i = 0
        while (x <= right && i++ < 400) {
            val idx = Math.round(x / step)
            r.line(x, bottom, x, top, if (abs(x) < step * 0.01f) 0xAA7FA7FF.toInt() else if (idx % 5 == 0) major else minor)
            x += step
        }
        var y = floor(bottom / step) * step
        i = 0
        while (y <= top && i++ < 400) {
            val idx = Math.round(y / step)
            r.line(left, y, right, y, if (abs(y) < step * 0.01f) 0xAAFF7F7F.toInt() else if (idx % 5 == 0) major else minor)
            y += step
        }
        r.flushLines(1f)
    }

    private fun drawEditorOverlay(view: View2D, ed: EditorState) {
        val scene = engine.scene
        val ppu = view.pixelsPerUnit
        val aspect = view.aspect
        for (go in scene.objects) {
            if (!go.isActiveInHierarchy()) continue
            val w = go.world
            go.get<Camera2D>()?.let { cam ->
                val cw = cam.size * aspect
                tmp.a = 1f; tmp.b = 0f; tmp.c = 0f; tmp.d = 1f; tmp.tx = w.tx; tmp.ty = w.ty
                r.obb(tmp, cw, cam.size, 0xCCFFFFFF.toInt())
                val s = 12f / ppu
                r.line(w.tx - s, w.ty, w.tx + s, w.ty, 0xCCFFFFFF.toInt())
                r.line(w.tx, w.ty - s, w.tx, w.ty + s, 0xCCFFFFFF.toInt())
            }
            if (ed.showColliders || go.id == ed.selectedId) go.get<Collider2D>()?.let { c -> drawCollider(go, c) }
            if (go.get<SpriteRenderer>() == null && go.get<TextRenderer>() == null && go.get<Camera2D>() == null) {
                val s = 8f / ppu
                r.line(w.tx - s, w.ty, w.tx, w.ty + s, 0x99FFFFFF.toInt())
                r.line(w.tx, w.ty + s, w.tx + s, w.ty, 0x99FFFFFF.toInt())
                r.line(w.tx + s, w.ty, w.tx, w.ty - s, 0x99FFFFFF.toInt())
                r.line(w.tx, w.ty - s, w.tx - s, w.ty, 0x99FFFFFF.toInt())
            }
        }
        val sel = scene.findById(ed.selectedId)
        if (sel != null && sel.isActiveInHierarchy()) {
            r.obb(sel.world, 0.5f, 0.5f, 0xFFFF9F1C.toInt())
            r.flushLines(2f)
            drawGizmo(sel, ed, ppu)
        } else r.flushLines(2f)
    }

    private fun drawCollider(go: GameObject, c: Collider2D) {
        val w = go.world
        val cx = w.mapX(c.offsetX, c.offsetY)
        val cy = w.mapY(c.offsetX, c.offsetY)
        val color = if (c.isTrigger) 0xCC4FC3F7.toInt() else 0xCC66FF66.toInt()
        if (c.shape == 2) {
            val pts = c.worldPoints(w)
            for (i in pts.indices) {
                val a = pts[i]; val b = pts[(i + 1) % pts.size]
                r.line(a[0], a[1], b[0], b[1], color)
            }
        } else if (c.shape == 1) r.circleLines(cx, cy, c.radius * max(w.scaleX, w.scaleY), color)
        else {
            tmp.a = 1f; tmp.b = 0f; tmp.c = 0f; tmp.d = 1f; tmp.tx = cx; tmp.ty = cy
            r.obb(tmp, c.width * w.scaleX / 2, c.height * w.scaleY / 2, color)
        }
    }

    private fun drawGizmo(go: GameObject, ed: EditorState, ppu: Float) {
        val w = go.world
        val x = w.tx
        val y = w.ty
        val len = ed.gizmoLength()
        val hs = 14f / ppu * (ed.view.heightPx / 1080f).coerceAtLeast(0.6f)
        val red = if (ed.activeAxis == 1) 0xFFFFFF66.toInt() else 0xFFFF4D4D.toInt()
        val green = if (ed.activeAxis == 2) 0xFFFFFF66.toInt() else 0xFF5CE65C.toInt()
        val free = if (ed.activeAxis == 3) 0xFFFFFF66.toInt() else 0xCCFFD24D.toInt()
        when (ed.tool) {
            Tool.MOVE -> {
                r.line(x, y, x + len, y, red); r.line(x, y, x, y + len, green)
                r.flushLines(5f)
                val rot = Affine().setTRS(x + len + hs * 0.6f, y, -90f, hs * 1.8f, hs * 1.8f)
                r.quad(rot, red, 2, null, hs * 1.8f * ppu)
                val rot2 = Affine().setTRS(x, y + len + hs * 0.6f, 0f, hs * 1.8f, hs * 1.8f)
                r.quad(rot2, green, 2, null, hs * 1.8f * ppu)
                r.rect(x + len * 0.18f, y + len * 0.18f, hs * 1.6f, hs * 1.6f, free, 0, ppu)
            }
            Tool.ROTATE -> {
                r.circleLines(x, y, len, if (ed.activeAxis != 0) 0xFFFFFF66.toInt() else 0xFF4DA6FF.toInt(), 64)
                val a = Math.toRadians(go.rotation.toDouble())
                r.line(x, y, x + (Math.cos(a) * len).toFloat(), y + (Math.sin(a) * len).toFloat(), 0xFF4DA6FF.toInt())
                r.flushLines(4f)
                r.rect(x, y, hs, hs, 0xFF4DA6FF.toInt(), 1, ppu)
            }
            Tool.SCALE -> {
                r.line(x, y, x + len, y, red); r.line(x, y, x, y + len, green)
                r.flushLines(5f)
                r.rect(x + len, y, hs * 1.6f, hs * 1.6f, red, 0, ppu)
                r.rect(x, y + len, hs * 1.6f, hs * 1.6f, green, 0, ppu)
                r.rect(x, y, hs * 1.8f, hs * 1.8f, free, 0, ppu)
            }
            Tool.HAND -> {}
        }
    }

    companion object {
        val POST_CUSTOM = com.sengine.engine.core.ComponentRegistry.POST_FX.size - 1
    }
}
