package com.sengine.engine.overlay

/**
 * Screen-space overlay model shared by two editors:
 *
 *  - the **Custom Game Control Editor** (kind [Kind.CONTROLS], saved as
 *    `Controls/<name>.ctrl.json`) places joysticks, d-pads and buttons that feed
 *    the game's input actions;
 *  - the **UI Builder** (kind [Kind.UI], saved as `UI/<name>.ui.json`) places
 *    panels, labels, images, progress bars and buttons that scripts drive.
 *
 * Positions are *anchored and normalised*. Each element has a horizontal and a
 * vertical anchor (left/centre/right, top/middle/bottom) and an offset measured
 * from that anchor as a fraction of the screen. A "bottom-right" button therefore
 * stays in the bottom-right corner on a 16:9 phone, a 4:3 tablet and a
 * 21:9 phone alike, which is what makes one layout work across devices.
 *
 * This file has no Android types, so the geometry is unit-tested on the host.
 */
object Overlay {
    const val FORMAT = "sengine-overlay-1"
}

enum class Kind(val label: String) { CONTROLS("Game Controls"), UI("UI Screen") }

enum class HAnchor(val label: String) { LEFT("Left"), CENTER("Centre"), RIGHT("Right") }
enum class VAnchor(val label: String) { TOP("Top"), MIDDLE("Middle"), BOTTOM("Bottom") }

enum class ElementType(val label: String, val interactive: Boolean) {
    // controls
    JOYSTICK("Joystick", true),
    DPAD("D-Pad", true),
    BUTTON("Button", true),
    // ui
    PANEL("Panel", false),
    LABEL("Label", false),
    IMAGE("Image", false),
    PROGRESS("Progress bar", false),
    TOGGLE("Toggle", true),
}

enum class Shape(val label: String) { RECT("Rectangle"), ROUNDED("Rounded"), CIRCLE("Circle") }

/** One placed element. All lengths are fractions of the screen's width or height (see [rect]). */
class OverlayElement(
    var id: String,
    var type: ElementType,
    var name: String,
    /** Offset of the anchor point, as a fraction of the screen. */
    var x: Float = 0f,
    var y: Float = 0f,
    /** Size as a fraction of the screen width (w) and height (h). */
    var w: Float = 0.12f,
    var h: Float = 0.12f,
    var hAnchor: HAnchor = HAnchor.LEFT,
    var vAnchor: VAnchor = VAnchor.TOP,
    /** Visible text (buttons, labels) or a file name (images). */
    var text: String = "",
    var color: Int = 0xFFFFFFFF.toInt(),
    var background: Int = 0x88000000.toInt(),
    var opacity: Float = 1f,
    var shape: Shape = Shape.ROUNDED,
    /** Text size as a fraction of the screen's shorter side. */
    var fontSize: Float = 0.035f,
    /**
     * Controls: the input action this element presses ("a", "b", "fire", "jump"...).
     * UI: the event emitted when the element is tapped ("ui:play" arrives as "play").
     */
    var action: String = "",
    /** Progress bars: current fraction 0..1. Toggles: 1 when on. */
    var value: Float = 1f,
    /** Hidden elements are not drawn or hit-tested at runtime. A script can flip this with ui.show/hide. */
    var visible: Boolean = true,
    /** Controls: draw the element in the editor and at runtime. */
    var locked: Boolean = false,
) {
    fun copy(newId: String) = OverlayElement(
        newId, type, name, x, y, w, h, hAnchor, vAnchor, text, color, background,
        opacity, shape, fontSize, action, value, visible, locked,
    )
}

class OverlayDocument(var name: String, var kind: Kind) {
    val elements = ArrayList<OverlayElement>()

    /** The aspect ratio the layout was authored at (informational; geometry is normalised). */
    var designAspect = 16f / 9f

    fun find(id: String) = elements.firstOrNull { it.id == id }

    /** A new id of the form "prefix_N" that no element uses. */
    fun nextId(prefix: String): String {
        var i = 1
        while (find("${prefix}_$i") != null) i++
        return "${prefix}_$i"
    }

    /**
     * Creates an element with sensible defaults for its type and appends it.
     * Controls sit in a screen corner, a little in from the edge; screen
     * elements (panels, labels...) start centred.
     */
    fun add(type: ElementType): OverlayElement {
        val base = when (type) {
            ElementType.JOYSTICK -> "joystick"
            ElementType.DPAD -> "dpad"
            ElementType.BUTTON -> "button"
            ElementType.PANEL -> "panel"
            ElementType.LABEL -> "label"
            ElementType.IMAGE -> "image"
            ElementType.PROGRESS -> "progress"
            ElementType.TOGGLE -> "toggle"
        }
        val e = OverlayElement(nextId(base), type, type.label)
        when (type) {
            ElementType.JOYSTICK, ElementType.DPAD -> {
                e.w = 0.2f; e.h = 0.36f; e.hAnchor = HAnchor.LEFT; e.vAnchor = VAnchor.BOTTOM
                e.x = 0.04f; e.y = 0.04f; e.shape = Shape.CIRCLE; e.background = 0x44000000; e.action = "move"
            }
            ElementType.BUTTON -> {
                e.w = 0.11f; e.h = 0.2f; e.hAnchor = HAnchor.RIGHT; e.vAnchor = VAnchor.BOTTOM
                e.x = 0.04f; e.y = 0.04f; e.shape = Shape.CIRCLE; e.text = "A"; e.action = "a"
            }
            ElementType.TOGGLE -> {
                e.w = 0.14f; e.h = 0.07f; e.hAnchor = HAnchor.LEFT; e.vAnchor = VAnchor.TOP
                e.x = 0.04f; e.y = 0.04f; e.text = "Option"; e.action = "toggle"
            }
            ElementType.PANEL -> { e.w = 0.4f; e.h = 0.3f; e.background = 0xCC1A1C2C.toInt(); e.shape = Shape.ROUNDED }
            ElementType.LABEL -> { e.w = 0.3f; e.h = 0.06f; e.text = "Label"; e.background = 0; e.fontSize = 0.05f }
            ElementType.IMAGE -> { e.w = 0.2f; e.h = 0.2f; e.background = 0 }
            ElementType.PROGRESS -> { e.w = 0.3f; e.h = 0.04f; e.background = 0x88000000.toInt(); e.color = 0xFF38B764.toInt(); e.value = 1f }
        }
        if (!type.interactive) { e.hAnchor = HAnchor.CENTER; e.vAnchor = VAnchor.MIDDLE }
        elements.add(e)
        return e
    }

    fun remove(id: String) = elements.removeAll { it.id == id }

    fun duplicate(id: String): OverlayElement? {
        val src = find(id) ?: return null
        val copy = src.copy(nextId(src.id.substringBeforeLast('_')))
        copy.x += 0.02f; copy.y += 0.02f
        elements.add(copy)
        return copy
    }

    /** Moves an element to the top of the draw order (last drawn, first hit). */
    fun bringToFront(id: String) {
        val e = find(id) ?: return
        elements.remove(e); elements.add(e)
    }

    /**
     * Pixel rectangle for [e] on a screen of [screenW] x [screenH] pixels,
     * returned as [left, top, right, bottom]. A non-positive screen yields a zero rectangle.
     */
    fun rect(e: OverlayElement, screenW: Float, screenH: Float): FloatArray {
        if (screenW <= 0f || screenH <= 0f) return floatArrayOf(0f, 0f, 0f, 0f)
        val w = e.w * screenW
        val h = e.h * screenH
        val left = when (e.hAnchor) {
            HAnchor.LEFT -> e.x * screenW
            HAnchor.CENTER -> screenW / 2f + e.x * screenW - w / 2f
            HAnchor.RIGHT -> screenW - e.x * screenW - w
        }
        val top = when (e.vAnchor) {
            VAnchor.TOP -> e.y * screenH
            VAnchor.MIDDLE -> screenH / 2f + e.y * screenH - h / 2f
            VAnchor.BOTTOM -> screenH - e.y * screenH - h
        }
        return floatArrayOf(left, top, left + w, top + h)
    }

    /**
     * The topmost visible element under the point, or null. Only elements with
     * [interactive] set are considered when [interactiveOnly] is true, which is
     * how the runtime routes touches past decorative panels.
     */
    fun hitTest(px: Float, py: Float, screenW: Float, screenH: Float, interactiveOnly: Boolean = true): OverlayElement? {
        for (i in elements.indices.reversed()) {
            val e = elements[i]
            if (!e.visible) continue
            if (interactiveOnly && !e.type.interactive) continue
            val r = rect(e, screenW, screenH)
            if (px >= r[0] && px <= r[2] && py >= r[1] && py <= r[3]) return e
        }
        return null
    }

    /**
     * Keeps every element at least partly on screen, so a layout authored on a
     * tablet can never leave a control off the edge of a phone.
     */
    fun clampToScreen() {
        for (e in elements) {
            e.w = e.w.coerceIn(0.01f, 1f)
            e.h = e.h.coerceIn(0.01f, 1f)
            e.x = e.x.coerceIn(-1f, 1f)
            e.y = e.y.coerceIn(-1f, 1f)
        }
    }

    /** Interactive elements in draw order. */
    fun interactive(): List<OverlayElement> = elements.filter { it.type.interactive }

    /** Actions bound on interactive elements, in draw order. Duplicates are the editor's warning to show. */
    fun boundActions(): List<String> = elements.filter { it.type.interactive && it.action.isNotBlank() }.map { it.action }
}

/** Snaps a normalised coordinate to a grid of [step] (no snapping when step is zero or negative). */
fun snap(v: Float, step: Float): Float = if (step <= 0f) v else Math.round(v / step) * step
