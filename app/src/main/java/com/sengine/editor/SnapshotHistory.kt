package com.sengine.editor

/** Bounded scene history, independent of Android. Snapshots are captured BEFORE each edit. */
class SnapshotHistory(private val maxEntries: Int = 100, private val maxBytes: Long = 16L * 1024 * 1024) {
    data class Entry(val json: String, val selection: Long)
    private val undo = ArrayDeque<Entry>()
    private val redo = ArrayDeque<Entry>()
    init { require(maxEntries > 0 && maxBytes > 0) }
    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()
    val undoCount get() = undo.size
    val redoCount get() = redo.size
    // Conservative UTF-16 storage estimate; both stacks share the same budget.
    val estimatedBytes get() = (undo.sumOf { it.json.length.toLong() } + redo.sumOf { it.json.length.toLong() }) * 2

    fun record(entry: Entry) {
        redo.clear() // A new branch invalidates redo even when the snapshot matches the last one.
        if (undo.lastOrNull()?.json != entry.json) undo.addLast(entry)
        trim(undo)
    }
    fun undo(current: Entry): Entry? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        trim(redo)
        return previous
    }
    fun redo(current: Entry): Entry? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        trim(undo)
        return next
    }
    private fun trim(preferred: ArrayDeque<Entry>) {
        while (undo.size + redo.size > maxEntries || estimatedBytes > maxBytes) {
            val other = if (preferred === undo) redo else undo
            if (other.isNotEmpty()) other.removeFirst() else if (preferred.isNotEmpty()) preferred.removeFirst() else break
        }
    }
    fun clear() { undo.clear(); redo.clear() }
}
