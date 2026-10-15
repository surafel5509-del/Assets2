package com.sengine.ui

import com.sengine.editor.SnapshotHistory
import com.sengine.engine.Engine
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import org.json.JSONObject

/** Adapter around the bounded, independently testable history store. */
class History(private val engine: Engine) {
    private val store = SnapshotHistory()
    var dirty = false
    val canUndo get() = store.canUndo
    val canRedo get() = store.canRedo
    val undoCount get() = store.undoCount
    val redoCount get() = store.redoCount
    private fun snapshot(selected: Long) = SnapshotHistory.Entry(SceneSerializer.toJson(engine.scene).toString(), selected)

    fun record(selected: Long) = synchronized(engine.lock) {
        if (engine.mode != Engine.Mode.EDIT) return@synchronized
        store.record(snapshot(selected))
        dirty = true
    }
    fun undo(selected: Long): Pair<Scene, Long>? = synchronized(engine.lock) {
        if (engine.mode != Engine.Mode.EDIT) return@synchronized null
        val e = store.undo(snapshot(selected)) ?: return@synchronized null
        dirty = true
        SceneSerializer.fromJson(JSONObject(e.json)) to e.selection
    }
    fun redo(selected: Long): Pair<Scene, Long>? = synchronized(engine.lock) {
        if (engine.mode != Engine.Mode.EDIT) return@synchronized null
        val e = store.redo(snapshot(selected)) ?: return@synchronized null
        dirty = true
        SceneSerializer.fromJson(JSONObject(e.json)) to e.selection
    }
    fun clear() { store.clear(); dirty = false }
}
