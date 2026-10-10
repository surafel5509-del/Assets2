package com.sengine.ui

import android.content.Context
import com.sengine.engine.Engine
import com.sengine.engine.overlay.OverlayDocument

/**
 * The touch layer for play mode: draws the project's control layout and any
 * open UI screens, and feeds their input into the engine.
 *
 * - Control actions set `Input.rawActions[name]`, so scripts read them with
 *   `input.action(name)` / `input.actionPressed(name)`. The actions "a" and
 *   "b" also drive the engine's `input.a` / `input.b`.
 * - The d-pad / joystick feed `Input.joyX` / `joyY`.
 * - UI-screen taps are pulsed as `ui.<id>` for exactly one frame.
 *
 * Touches that land on nothing return false, so they still reach the game.
 */
fun playOverlayView(context: Context, engine: Engine): OverlayView =
    OverlayView(context) { overlayDocs(engine) }.apply {
        onAction = { name, down ->
            val input = engine.input
            input.rawActions[name] = down
            if (name == "a") input.rawA = down
            if (name == "b") input.rawB = down
        }
        onAxis = { x, y ->
            engine.input.joyX = x
            engine.input.joyY = y
        }
        onUiTap = { id -> engine.input.pulse("ui.$id") }
    }

/** The control layout first, then every open UI screen. */
fun overlayDocs(engine: Engine): List<OverlayDocument> {
    val list = ArrayList<OverlayDocument>()
    engine.overlays.controls?.let { list.add(it) }
    list.addAll(engine.overlays.screens.values)
    return list
}
