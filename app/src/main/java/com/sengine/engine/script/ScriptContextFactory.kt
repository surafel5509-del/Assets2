package com.sengine.engine.script

import org.mozilla.javascript.ClassShutter
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContextFactory

/**
 * Aborts a runaway script. Rhino's interpreter only terminates *unconditionally*
 * when the instruction observer throws an [Error] (a RuntimeException is treated
 * as a catchable JS exception), so this must not be a plain Exception.
 */
class ScriptBudgetException(message: String) : Error(message)

/**
 * Isolated Rhino context factory for game scripts.
 *
 * Two hard safety properties (both were missing before Phase 1):
 *
 * 1. **No Java access from JavaScript.** Rhino's LiveConnect exposes every class
 *    on the host classpath through `Packages`, `java`, `com`, ... by default, so
 *    a script could reach the file system, network or reflection. The script
 *    system uses `initSafeStandardObjects()` (which omits those roots) and this
 *    factory installs an allow-list [ClassShutter]: only the engine's own API
 *    bridge classes may be reflected. Everything else (`java.io.File`,
 *    `java.lang.Class`, `android.*`, ...) is prohibited.
 * 2. **No infinite loops.** Interpreted Rhino calls [observeInstructionCount]
 *    every `instructionObserverThreshold` bytecode instructions. A budget that
 *    is refilled once per engine frame aborts runaway scripts instead of
 *    freezing the GL thread forever. Once exceeded, the guard keeps throwing
 *    until the engine resets it at the end of the frame.
 *
 * The budget is intentionally generous (5 million instructions / 250 ms per
 * frame); ordinary games never approach it. It is a safety net, not a throttle.
 */
class ScriptContextFactory : ContextFactory() {
    companion object {
        const val INSTRUCTION_THRESHOLD = 20_000
        const val FRAME_INSTRUCTION_BUDGET = 5_000_000L
        const val FRAME_TIME_BUDGET_MS = 250L

        /**
         * Java classes deliberately exposed to JS. Rhino consults the shutter
         * when wrapping *any* Java object, so the API bridges themselves must
         * be allowed, while all host/platform classes stay denied.
         */
        val ALLOWED_CLASSES = setOf(
            "com.sengine.engine.script.SObject",
            "com.sengine.engine.script.SScene",
            "com.sengine.engine.script.SInput",
            "com.sengine.engine.script.STime",
            "com.sengine.engine.script.SAudio",
            "com.sengine.engine.script.SConsole",
            "com.sengine.engine.script.SModules",
            "com.sengine.engine.script.SAssets",
            // Returned by SObject.unwrap() for advanced engine interop.
            "com.sengine.engine.core.GameObject",
        )
    }

    @Volatile private var remainingInstructions = Long.MAX_VALUE
    @Volatile private var deadlineNanos = Long.MAX_VALUE
    @Volatile private var active = false
    @Volatile private var exceeded = false

    /** Starts (or restarts) a safety budget for the JS work about to run. */
    fun beginBudget(
        instructions: Long = FRAME_INSTRUCTION_BUDGET,
        millis: Long = FRAME_TIME_BUDGET_MS,
    ) {
        remainingInstructions = instructions
        deadlineNanos = System.nanoTime() + millis * 1_000_000L
        exceeded = false
        active = true
    }

    fun endBudget() {
        active = false
        exceeded = false
        remainingInstructions = Long.MAX_VALUE
        deadlineNanos = Long.MAX_VALUE
    }

    override fun onContextCreated(cx: Context) {
        super.onContextCreated(cx)
        cx.optimizationLevel = -1                 // interpreted mode: Android-safe + observable
        cx.languageVersion = Context.VERSION_ES6
        cx.wrapFactory.isJavaPrimitiveWrap = false
        cx.setClassShutter(ClassShutter { name -> name in ALLOWED_CLASSES })
        cx.instructionObserverThreshold = INSTRUCTION_THRESHOLD
    }

    override fun observeInstructionCount(cx: Context, instructionCount: Int) {
        if (!active) return
        if (exceeded) {
            // Keep aborting until the frame boundary resets the budget. A script
            // that somehow swallows the first Error must not be allowed to run on.
            throw ScriptBudgetException("Script execution budget exceeded")
        }
        remainingInstructions -= instructionCount.toLong()
        if (remainingInstructions <= 0) {
            exceeded = true
            throw ScriptBudgetException("Script exceeded $FRAME_INSTRUCTION_BUDGET instructions in one frame")
        }
        // nanoTime is cheap; checking it once per 20k instructions is negligible.
        if (System.nanoTime() > deadlineNanos) {
            exceeded = true
            throw ScriptBudgetException("Script exceeded ${FRAME_TIME_BUDGET_MS}ms in one frame")
        }
    }
}
