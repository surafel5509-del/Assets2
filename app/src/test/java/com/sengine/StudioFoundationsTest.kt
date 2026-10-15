package com.sengine

import com.sengine.editor.SnapshotHistory
import com.sengine.engine.core.FixedStepClock
import com.sengine.engine.core.TimeManager
import com.sengine.project.AtomicFiles
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class StudioFoundationsTest {
    @Test fun fixedStepIsIndependentOfRenderRate() {
        for (fps in listOf(30, 60, 120, 144)) {
            val clock = FixedStepClock()
            var ticks = 0
            repeat(fps * 10) { clock.advance(1f / fps) { ticks++ } }
            assertEquals("render FPS $fps", 600, ticks)
        }
    }
    @Test fun longFramesAreBoundedAndFractionIsRetained() {
        val clock = FixedStepClock(0.01f, 4)
        assertEquals(4, clock.advance(0.105f) {})
        assertEquals(0.06, clock.droppedSeconds, 0.00001)
        assertEquals(0.5f, clock.interpolationAlpha, 0.0001f)
        assertEquals(1, clock.advance(0.005f) {})
    }
    @Test fun invalidDeltasDoNotPoisonTheClock() {
        val clock = FixedStepClock()
        listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f, 0f).forEach { assertEquals(0, clock.advance(it) { fail() }) }
        assertEquals(1, clock.advance(1f / 60f) {})
        clock.reset()
        assertEquals(0f, clock.interpolationAlpha, 0f)
        assertEquals(0.0, clock.droppedSeconds, 0.0)
    }
    @Test fun changingFixedRateClearsOldRemainder() {
        val clock = FixedStepClock()
        clock.advance(0.005f) {}
        clock.stepSeconds = 0.01f
        assertEquals(0f, clock.interpolationAlpha, 0f)
        assertEquals(1, clock.advance(0.01f) {})
    }
    @Test(expected = IllegalArgumentException::class) fun invalidClockConfigurationIsRejected() { FixedStepClock(0f) }
    @Test fun timeScaleAffectsSimulationNotWallClock() {
        val time = TimeManager().apply { timeScale = 0.5f }
        time.update(0.02f)
        assertEquals(0.01f, time.deltaTime, 0.00001f)
        assertEquals(0.02, time.unscaledTime, 0.00001)
        assertEquals(0.01f, time.smoothDeltaTime, 0.00001f)
        time.timeScale = 0f
        time.update(1f)
        assertEquals(0f, time.deltaTime, 0f)
        assertEquals(1.02, time.unscaledTime, 0.00001)
        time.reset()
        assertEquals(0f, time.deltaTime, 0f)
        assertEquals(0f, time.smoothDeltaTime, 0f)
    }
    @Test fun nonFiniteTimeInputIsIgnored() {
        val time = TimeManager()
        time.update(Float.NaN)
        time.update(Float.POSITIVE_INFINITY)
        assertEquals(0.0, time.time, 0.0)
        time.fixedUpdate()
        assertEquals(time.fixedDeltaTime.toDouble(), time.fixedTime, 0.000001)
    }
    @Test(expected = IllegalArgumentException::class) fun negativeTimeScaleIsRejected() { TimeManager().timeScale = -1f }
    private fun entry(value: String, id: Long = 1) = SnapshotHistory.Entry(value, id)
    @Test fun undoRedoRestoresSelectionAndBranchingInvalidatesRedo() {
        val history = SnapshotHistory()
        history.record(entry("a", 7))
        history.record(entry("b", 9))
        assertEquals(entry("b", 9), history.undo(entry("c")))
        assertEquals(entry("c"), history.redo(entry("b", 9)))
        history.undo(entry("c"))
        history.record(entry("b", 9))
        assertFalse(history.canRedo)
    }
    @Test fun historyHonorsCombinedMemoryAndCountLimits() {
        val history = SnapshotHistory(3, 20)
        repeat(20) { history.record(entry("000$it")) }
        assertTrue(history.estimatedBytes <= 20)
        assertTrue(history.undoCount <= 3)
        history.undo(entry("large current snapshot"))
        assertTrue(history.estimatedBytes <= 20)
        history.clear()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
    @Test fun newBranchClearsRedoEvenWhenSnapshotIsDuplicate() {
        val h = SnapshotHistory()
        h.record(entry("a")); h.record(entry("b"))
        h.undo(entry("c"))
        h.record(entry("a"))
        assertFalse(h.canRedo)
    }
    @Test fun atomicSaveRotatesBackupsAndLeavesNoTemporaryFiles() {
        val dir = Files.createTempDirectory("atomic-engine").toFile()
        try {
            val file = File(dir, "scene.json")
            AtomicFiles.write(file, "one", 2)
            AtomicFiles.write(file, "two", 2)
            AtomicFiles.write(file, "three", 2)
            assertEquals("three", file.readText())
            assertEquals("two", File(dir, "scene.json.bak1").readText())
            assertEquals("one", File(dir, "scene.json.bak2").readText())
            assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
        } finally { dir.deleteRecursively() }
    }
    @Test fun projectPathsCannotEscapeTheirRoot() {
        val dir = Files.createTempDirectory("paths-engine").toFile()
        try {
            assertEquals(File(dir, "a/b.txt"), AtomicFiles.child(dir, "a/b.txt"))
            for (path in listOf("../outside", "a/../../outside", "", ".", "a\\b")) {
                try { AtomicFiles.child(dir, path); fail("Accepted unsafe path $path") } catch (_: IllegalArgumentException) {}
            }
        } finally { dir.deleteRecursively() }
    }
}
