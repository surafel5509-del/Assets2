package com.sengine.engine.ui.canvas

/**
 * Ultimate Edition UI System - Unity UI Toolkit + uGUI + Unreal UMG equivalent
 * Canvas, layout groups, graphic raycaster, etc.
 */

enum class CanvasRenderMode { ScreenSpaceOverlay, ScreenSpaceCamera, WorldSpace }
enum class CanvasScalerMode { ConstantPixelSize, ScaleWithScreenSize, ConstantPhysicalSize }
enum class TextAnchor { UpperLeft, UpperCenter, UpperRight, MiddleLeft, MiddleCenter, MiddleRight, LowerLeft, LowerCenter, LowerRight }

data class Rect(var x: Float, var y: Float, var width: Float, var height: Float) {
    fun contains(px: Float, py: Float): Boolean = px >= x && px <= x + width && py >= y && py <= y + height
    fun centerX(): Float = x + width / 2
    fun centerY(): Float = y + height / 2
}

class Canvas {
    var renderMode: CanvasRenderMode = CanvasRenderMode.ScreenSpaceOverlay
    var sortingOrder: Int = 0
    var targetDisplay: Int = 0
    var pixelPerfect: Boolean = false
    var planeDistance: Float = 100f
    var overrideSorting: Boolean = false
    var sortingLayerID: Int = 0
    var additionalShaderChannels: Int = 0

    var scalerMode: CanvasScalerMode = CanvasScalerMode.ConstantPixelSize
    var referenceResolution: FloatArray = floatArrayOf(1920f, 1080f)
    var screenMatchMode: Int = 0
    var matchWidthOrHeight: Float = 0.5f
    var referencePixelsPerUnit: Float = 100f
}

class RectTransform {
    var anchorMin: FloatArray = floatArrayOf(0.5f, 0.5f)
    var anchorMax: FloatArray = floatArrayOf(0.5f, 0.5f)
    var anchoredPosition: FloatArray = floatArrayOf(0f, 0f)
    var sizeDelta: FloatArray = floatArrayOf(100f, 100f)
    var pivot: FloatArray = floatArrayOf(0.5f, 0.5f)
    var rotation: Float = 0f
    var scale: FloatArray = floatArrayOf(1f, 1f)

    fun getRect(parentSize: FloatArray): Rect {
        val parentW = parentSize[0]; val parentH = parentSize[1]
        val anchorMinX = anchorMin[0] * parentW
        val anchorMinY = anchorMin[1] * parentH
        val anchorMaxX = anchorMax[0] * parentW
        val anchorMaxY = anchorMax[1] * parentH
        val anchorCenterX = (anchorMinX + anchorMaxX) * 0.5f
        val anchorCenterY = (anchorMinY + anchorMaxY) * 0.5f
        val x = anchorCenterX + anchoredPosition[0] - sizeDelta[0] * pivot[0]
        val y = anchorCenterY + anchoredPosition[1] - sizeDelta[1] * pivot[1]
        return Rect(x, y, sizeDelta[0], sizeDelta[1])
    }
}

class CanvasGroup {
    var alpha: Float = 1f
    var interactable: Boolean = true
    var blocksRaycasts: Boolean = true
    var ignoreParentGroups: Boolean = false

    fun isVisible(): Boolean = alpha > 0.001f
    fun canInteract(): Boolean = interactable && alpha > 0.001f
}

class LayoutGroup {
    enum class Type { Horizontal, Vertical, Grid }

    var type: Type = Type.Horizontal
    var paddingLeft: Float = 0f
    var paddingRight: Float = 0f
    var paddingTop: Float = 0f
    var paddingBottom: Float = 0f
    var spacing: Float = 0f
    var childAlignment: TextAnchor = TextAnchor.UpperLeft
    var childForceExpandWidth: Boolean = true
    var childForceExpandHeight: Boolean = true
    var childControlWidth: Boolean = true
    var childControlHeight: Boolean = true

    // Grid
    var cellSize: FloatArray = floatArrayOf(100f, 100f)
    var constraint: Int = 0
    var constraintCount: Int = 2

    fun calculateLayout(children: List<RectTransform>, parentRect: Rect): List<Rect> {
        val result = mutableListOf<Rect>()
        when (type) {
            Type.Horizontal -> {
                var x = parentRect.x + paddingLeft
                for (child in children) {
                    val w = child.sizeDelta[0]
                    val h = parentRect.height - paddingTop - paddingBottom
                    result.add(Rect(x, parentRect.y + paddingBottom, w, h))
                    x += w + spacing
                }
            }
            Type.Vertical -> {
                var y = parentRect.y + parentRect.height - paddingTop
                for (child in children) {
                    val w = parentRect.width - paddingLeft - paddingRight
                    val h = child.sizeDelta[1]
                    y -= h
                    result.add(Rect(parentRect.x + paddingLeft, y, w, h))
                    y -= spacing
                }
            }
            Type.Grid -> {
                var idx = 0
                var y = parentRect.y + parentRect.height - paddingTop - cellSize[1]
                while (idx < children.size) {
                    var x = parentRect.x + paddingLeft
                    for (col in 0 until constraintCount) {
                        if (idx >= children.size) break
                        result.add(Rect(x, y, cellSize[0], cellSize[1]))
                        x += cellSize[0] + spacing
                        idx++
                    }
                    y -= cellSize[1] + spacing
                }
            }
        }
        return result
    }
}

class GraphicRaycaster {
    var ignoreReversedGraphics: Boolean = true
    var blockingObjects: Int = 0
    var blockingMask: Int = -1

    data class RaycastResult(var gameObject: String, var distance: Float, var screenPosition: FloatArray, var worldPosition: FloatArray? = null)

    fun raycast(screenPos: FloatArray, uiElements: List<Pair<String, Rect>>): List<RaycastResult> {
        val results = mutableListOf<RaycastResult>()
        for ((name, rect) in uiElements) {
            if (rect.contains(screenPos[0], screenPos[1])) {
                results.add(RaycastResult(name, 0f, screenPos))
            }
        }
        return results.sortedBy { it.distance }
    }
}

class UIAnimation {
    var duration: Float = 0.3f
    var delay: Float = 0f
    var easing: Int = 0 // 0 linear, 1 ease in, 2 ease out, 3 ease in out
    var loop: Boolean = false
    var autoPlay: Boolean = false

    fun evaluate(t: Float): Float {
        val nt = (t / duration).coerceIn(0f, 1f)
        return when (easing) {
            1 -> nt * nt
            2 -> 1f - (1f - nt) * (1f - nt)
            3 -> if (nt < 0.5f) 2f * nt * nt else 1f - (-2f * nt + 2f).let { it * it / 2f }
            else -> nt
        }
    }
}
