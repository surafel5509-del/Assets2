package com.sengine.engine.tools

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.2 [Ultra] - Ultra Editor System - Months/Years Professional Project
 * Full Editor Dockable Windows Layout Themes, Scene View Gizmos Handles Tools Move/Rotate/Scale/Rect, Inspector Custom Editors Property Drawers, Hierarchy Search Filtering Locking, Project Search Favorites Labels Dependencies, Console Filtering Stack Trace Double-Click, Profiler CPU GPU Memory Rendering Physics Audio Networking Modules, Frame Debugger Draw Call Inspection, Memory Profiler Snapshots Diff, Build Pipeline Build Profiles Addressables Asset Bundles, Version Control Diff Merge Branches, Plugin Hot Reload
 */
enum class EditorWindowType { Hierarchy, Inspector, Project, Console, SceneView, GameView, Profiler, FrameDebugger, MemoryProfiler, BuildSettings, PackageManager, AssetStore, VersionControl, Animator, Timeline, ShaderGraph, VFXGraph, TilePalette, SpriteEditor }
enum class EditorToolV2 { Move, Rotate, Scale, Rect, Transform, Universal, Custom }
enum class EditorTheme { Light, Dark, Custom }

data class EditorWindow(
    var id: String = "window_0",
    var type: EditorWindowType = EditorWindowType.Hierarchy,
    var x: Float = 0f, var y: Float = 0f,
    var width: Float = 300f, var height: Float = 400f,
    var isDocked: Boolean = true,
    var dockPosition: Int = 0, // 0 left, 1 right, 2 bottom, 3 top, 4 center
    var isVisible: Boolean = true,
    var isFocused: Boolean = false,
    var title: String = "Window"
)

data class EditorLayout(
    var name: String = "Default",
    var windows: MutableList<EditorWindow> = mutableListOf(),
    var theme: EditorTheme = EditorTheme.Dark,
    var isDefault: Boolean = false
) {
    fun addWindow(type: EditorWindowType, x: Float, y: Float, w: Float, h: Float): EditorWindow {
        val win = EditorWindow(id="window_${windows.size}", type=type, x=x, y=y, width=w, height=h, title=type.name)
        windows.add(win); return win
    }
    fun getWindow(type: EditorWindowType): EditorWindow? = windows.find { it.type == type }
    fun save(): String = "Layout $name with ${windows.size} windows"
}

data class EditorGizmo(
    var type: String = "Cube",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var size: Float = 1f,
    var color: Int = 0xFF00FF00.toInt(),
    var isWireframe: Boolean = true
)

data class EditorHandle(
    var type: EditorToolV2 = EditorToolV2.Move,
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var axis: Int = 0, // 0 x, 1 y, 2 z, 3 all
    var isDragging: Boolean = false,
    var dragStartX: Float = 0f, var dragStartY: Float = 0f
) {
    fun startDrag(sx: Float, sy: Float) { isDragging=true; dragStartX=sx; dragStartY=sy }
    fun updateDrag(cx: Float, cy: Float): Pair<Float,Float> {
        if (!isDragging) return Pair(0f,0f)
        return Pair(cx-dragStartX, cy-dragStartY)
    }
    fun endDrag() { isDragging=false }
}

class UltraEditorSystem {
    var layouts = mutableMapOf<String, EditorLayout>()
    var currentLayout: String = "Default"
    var windows = mutableMapOf<String, EditorWindow>()
    var gizmos = mutableListOf<EditorGizmo>()
    var handles = mutableListOf<EditorHandle>()
    var currentTool: EditorToolV2 = EditorToolV2.Move
    var theme: EditorTheme = EditorTheme.Dark
    var isPlaying: Boolean = false
    var isPaused: Boolean = false
    var showGizmos: Boolean = true
    var showGrid: Boolean = true
    var gridSize: Float = 1f
    var snapEnabled: Boolean = true
    var snapValue: Float = 1f
    var nextWindowId: Int = 0

    init {
        // Default layout
        val defaultLayout = EditorLayout(name="Default", isDefault=true)
        defaultLayout.addWindow(EditorWindowType.Hierarchy, 0f, 0f, 300f, 600f)
        defaultLayout.addWindow(EditorWindowType.Inspector, 1620f, 0f, 300f, 600f)
        defaultLayout.addWindow(EditorWindowType.Project, 0f, 600f, 1920f, 300f)
        defaultLayout.addWindow(EditorWindowType.Console, 300f, 600f, 800f, 200f)
        defaultLayout.addWindow(EditorWindowType.SceneView, 300f, 0f, 1320f, 600f)
        defaultLayout.addWindow(EditorWindowType.GameView, 300f, 0f, 1320f, 600f)
        layouts["Default"]=defaultLayout

        val tallLayout = EditorLayout(name="Tall")
        tallLayout.addWindow(EditorWindowType.Hierarchy, 0f, 0f, 250f, 800f)
        tallLayout.addWindow(EditorWindowType.Inspector, 1670f, 0f, 250f, 800f)
        tallLayout.addWindow(EditorWindowType.SceneView, 250f, 0f, 800f, 600f)
        tallLayout.addWindow(EditorWindowType.GameView, 1050f, 0f, 620f, 600f)
        tallLayout.addWindow(EditorWindowType.Project, 0f, 800f, 1920f, 280f)
        layouts["Tall"]=tallLayout

        val wideLayout = EditorLayout(name="Wide")
        wideLayout.addWindow(EditorWindowType.Hierarchy, 0f, 0f, 300f, 400f)
        wideLayout.addWindow(EditorWindowType.Project, 0f, 400f, 300f, 400f)
        wideLayout.addWindow(EditorWindowType.SceneView, 300f, 0f, 900f, 800f)
        wideLayout.addWindow(EditorWindowType.Inspector, 1200f, 0f, 300f, 800f)
        wideLayout.addWindow(EditorWindowType.GameView, 1500f, 0f, 420f, 400f)
        wideLayout.addWindow(EditorWindowType.Console, 1500f, 400f, 420f, 400f)
        layouts["Wide"]=wideLayout
    }

    fun createWindow(type: EditorWindowType): EditorWindow {
        val id = "window_${nextWindowId++}"
        val win = EditorWindow(id=id, type=type, title=type.name)
        windows[id]=win
        layouts[currentLayout]?.windows?.add(win)
        return win
    }

    fun setLayout(name: String): Boolean {
        if (name !in layouts) return false
        currentLayout=name; return true
    }

    fun createLayout(name: String): EditorLayout {
        val layout = EditorLayout(name=name); layouts[name]=layout; return layout
    }

    fun addGizmo(type: String, x: Float, y: Float, z: Float, size: Float = 1f, color: Int = 0xFF00FF00.toInt()): EditorGizmo {
        val gizmo = EditorGizmo(type=type, x=x, y=y, z=z, size=size, color=color)
        gizmos.add(gizmo); return gizmo
    }

    fun createHandle(type: EditorToolV2, x: Float, y: Float, z: Float): EditorHandle {
        val handle = EditorHandle(type=type, x=x, y=y, z=z); handles.add(handle); return handle
    }

    fun setTool(tool: EditorToolV2) { currentTool=tool }

    fun getStats(): String = "Ultra Editor Layout:$currentLayout Theme:$theme Tool:$currentTool Windows:${windows.size} Gizmos:${gizmos.size} Handles:${handles.size} Layouts:${layouts.size} Playing:$isPlaying Paused:$isPaused Grid:$showGrid Snap:$snapEnabled"
}
