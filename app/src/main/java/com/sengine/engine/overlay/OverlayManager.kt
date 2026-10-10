package com.sengine.engine.overlay

import com.sengine.project.AssetFolders
import com.sengine.project.Project
import org.json.JSONObject

/**
 * Runtime state of the on-screen layers: the active control layout and the UI
 * screens a script has opened. Both the in-app player and the editor's play
 * mode render from this object, and scripts drive it through the `ui` API.
 *
 * Mutations come from the engine thread. The UI thread only reads, and checks
 * [version] to know when to redraw, so a half-applied change is at worst one
 * frame stale.
 */
class OverlayManager(private val project: Project) {

    /** Open screens in the order they were opened (the last is drawn on top). */
    val screens = LinkedHashMap<String, OverlayDocument>()

    /** The control layout in use, or null for the built-in joystick and A/B buttons. */
    var controls: OverlayDocument? = null
        private set

    /** Bumped on every visible change so the view knows to redraw. */
    @Volatile var version = 0
        private set

    fun touch() { version++ }

    /** Loads the project's selected control layout. Called when play starts. */
    fun loadControls() {
        val name = project.controlsLayout
        controls = (if (name.isBlank()) null else load(name, Kind.CONTROLS)) ?: defaultControls()
        touch()
    }

    /**
     * Opens a UI screen. [name] is an asset reference, a file stem ("HUD") or a
     * full name ("HUD.ui.json"). Returns false when no such screen exists.
     */
    fun open(name: String): Boolean {
        val doc = load(name, Kind.UI) ?: return false
        screens.remove(doc.name)
        screens[doc.name] = doc
        touch()
        return true
    }

    fun close(name: String) {
        val key = screens.keys.firstOrNull { it.equals(name, true) } ?: return
        screens.remove(key)
        touch()
    }

    fun isOpen(name: String) = screens.keys.any { it.equals(name, true) }

    /** The element with this id on any open screen, with the screen it belongs to. */
    fun find(id: String): OverlayElement? {
        for (doc in screens.values) doc.find(id)?.let { return it }
        return null
    }

    fun setText(id: String, text: String) { find(id)?.let { it.text = text; touch() } }
    fun getText(id: String): String = find(id)?.text ?: ""
    fun setValue(id: String, value: Float) { find(id)?.let { it.value = value.coerceIn(0f, 1f); touch() } }
    fun getValue(id: String): Float = find(id)?.value ?: 0f
    fun setVisible(id: String, v: Boolean) { find(id)?.let { it.visible = v; touch() } }
    fun isVisible(id: String): Boolean = find(id)?.visible ?: false

    fun setColor(id: String, argb: Int) { find(id)?.let { it.color = argb; touch() } }
    fun setBackground(id: String, argb: Int) { find(id)?.let { it.background = argb; touch() } }

    /** Clears every open screen. Called when the scene ends so screens do not leak into the next scene. */
    fun reset() {
        screens.clear()
        controls = null
        touch()
    }

    private fun load(name: String, kind: Kind): OverlayDocument? {
        val ref = resolve(name, kind) ?: return null
        val text = project.readAsset(ref) ?: return null
        return try {
            OverlayCodec.fromJson(JSONObject(text), AssetFolders.nameOf(ref).substringBefore('.'))
        } catch (_: Exception) {
            null
        }
    }

    /** Finds the asset for a screen or layout name, trying the full name, then the suffixed name, then a stem match. */
    fun resolve(name: String, kind: Kind): String? {
        val suffix = if (kind == Kind.UI) ".ui.json" else ".ctrl.json"
        for (candidate in listOf(name, name + suffix)) {
            val file = project.resolveAsset(candidate) ?: continue
            return file.relativeTo(project.assetsDir).path.replace('\\', '/')
        }
        val assetKind = if (kind == Kind.UI) com.sengine.engine.core.AssetKind.UI else com.sengine.engine.core.AssetKind.CONTROLS
        return project.listAssets(assetKind).firstOrNull { AssetFolders.nameOf(it).substringBefore('.').equals(name, true) }
    }
}

/**
 * The control layout used when a project has not chosen one: a d-pad on the
 * left and A / B buttons on the right, so every game is playable on touch.
 */
fun defaultControls(): OverlayDocument {
    val doc = OverlayDocument("Default", Kind.CONTROLS)
    doc.add(ElementType.DPAD)
    doc.add(ElementType.BUTTON).apply { id = "a"; text = "A"; action = "a" }
    doc.add(ElementType.BUTTON).apply { id = "b"; text = "B"; action = "b"; x = 0.17f }
    return doc
}
