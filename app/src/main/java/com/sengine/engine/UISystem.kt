package com.sengine.engine

import com.sengine.engine.core.GameObject
import com.sengine.engine.core.UIButton
import com.sengine.engine.core.UICanvas
import com.sengine.engine.core.UIRect

/**
 * Screen-space UI input. Buttons are hit-tested in the same normalised canvas
 * space used by SceneRenderer, then reported to scripts on the frame of the tap.
 */
class UISystem(private val engine: Engine) {
    private val defaultCanvas = UICanvas()

    private data class Hit(
        val go: GameObject, val canvas: UICanvas, val rect: UIRect.Layout,
        val order: Int, val index: Int,
    )

    private fun canvasFor(go: GameObject): UICanvas? {
        var cur: GameObject? = go
        while (cur != null) {
            val c = cur.getAny<UICanvas>()
            if (c != null) return if (c.enabled && c.interactive) c else null
            cur = cur.parent
        }
        return defaultCanvas
    }

    private fun hits(): List<Hit> {
        val out = ArrayList<Hit>()
        for ((index, go) in engine.scene.objects.withIndex()) {
            if (!go.isActiveInHierarchy()) continue
            val button = go.get<UIButton>() ?: continue
            if (!button.enabled) continue
            val rect = go.get<UIRect>() ?: continue
            val canvas = canvasFor(go) ?: continue
            out.add(Hit(go, canvas, rect.layout(canvas.frame(engine.gameView.widthPx, engine.gameView.heightPx)), go.order, index))
        }
        return out.sortedWith(compareBy({ it.canvas.sortOrder }, { it.order }, { it.index }))
    }

    fun reset() {
        for (go in engine.scene.objects) go.getAny<UIButton>()?.resetRuntime()
    }

    /** Called once per PLAY frame after Input.beginFrame() and before scripts.update(). */
    fun update() {
        for (go in engine.scene.objects) go.getAny<UIButton>()?.let {
            it.clicked = false
            it.pressed = false
        }
        val input = engine.input
        if (!input.tapped) return
        val w = engine.gameView.widthPx.coerceAtLeast(1).toFloat()
        val h = engine.gameView.heightPx.coerceAtLeast(1).toFloat()
        val nx = input.rawTouchSX / w
        val ny = 1f - input.rawTouchSY / h
        // Top-most button wins, matching the renderer's sorted draw order.
        for (hit in hits().asReversed()) {
            if (!hit.rect.contains(nx, ny)) continue
            val button = hit.go.get<UIButton>() ?: continue
            button.clicked = true
            button.pressed = true
            button.clickCount++
            button.pressedTime = engine.time.toFloat()
            engine.scripts.onUIButtonClick(hit.go, button.action, button.label)
            break
        }
    }
}
