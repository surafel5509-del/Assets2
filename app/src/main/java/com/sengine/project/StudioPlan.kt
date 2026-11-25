package com.sengine.project

import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * A production-ready, two-year project system for S Engine.
 *
 * The plan is stored as `roadmap.json` beside `project.json`, so it travels with
 * exported/imported `.zip` projects. It contains 24 monthly milestones across
 * eight quarterly phases, with acceptance criteria, due months, shipped engine
 * capabilities and per-project completion flags. Missing or older plans are
 * merged forward without losing user progress.
 */
data class RoadmapMilestone(
    val id: String,
    val month: Int,
    val phase: String,
    val title: String,
    val acceptance: String,
    /** Built-in engine capability delivered by v3; locked in the editor. */
    val shipped: Boolean = false,
    /** Project-specific production sign-off, toggled by the team in the editor. */
    var done: Boolean = false
) {
    val complete: Boolean get() = done || shipped

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("month", month)
        put("phase", phase)
        put("title", title)
        put("acceptance", acceptance)
        put("shipped", shipped)
        put("done", done)
    }

    companion object {
        fun fromJson(o: JSONObject): RoadmapMilestone = RoadmapMilestone(
            id = o.optString("id"),
            month = o.optInt("month", 1),
            phase = o.optString("phase", "Foundation"),
            title = o.optString("title"),
            acceptance = o.optString("acceptance"),
            shipped = o.optBoolean("shipped", false),
            done = o.optBoolean("done", false)
        )
    }
}

class Roadmap(
    val projectName: String,
    /** Epoch day on which month 1 begins; due dates are derived from it. */
    val startEpochDay: Long = System.currentTimeMillis() / 86_400_000L,
    val milestones: MutableList<RoadmapMilestone> = ArrayList()
) {
    val completeCount: Int get() = milestones.count { it.complete }
    val progress: Int get() = if (milestones.isEmpty()) 0 else completeCount * 100 / milestones.size
    val next: RoadmapMilestone? get() = milestones.firstOrNull { !it.complete }

    fun dueDate(m: RoadmapMilestone): String =
        LocalDate.ofEpochDay(startEpochDay).plusMonths((m.month - 1).toLong()).toString()

    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", 1)
        put("engine", EngineInfo.VERSION)
        put("edition", EngineInfo.EDITION)
        put("years", StudioPlan.YEARS)
        put("months", StudioPlan.MONTHS)
        put("project", projectName)
        put("startEpochDay", startEpochDay)
        put("milestones", org.json.JSONArray(milestones.map { it.toJson() }))
    }
}

object StudioPlan {
    const val YEARS = 2
    const val MONTHS = 24

    val PHASES = listOf(
        "Foundation", "Future Physics", "Script Runtime", "Camera & Renderer",
        "Studio Editor", "Content & Games", "Scale & Polish", "Release"
    )

    private val DEFINITIONS = listOf(
        Triple("Project charter, 24-month release map and schema", "Roadmap persists with the project and reports progress.", true),
        Triple("Project schema v3, migrations and per-project settings", "Old v1/v2 projects migrate without scene loss; settings apply at play start.", true),
        Triple("Deterministic fixed-step clock and seeded simulation", "Same seed + inputs produce the same 60 Hz steps and interpolation alpha.", true),
        Triple("Spatial-hash broad phase and solver iteration budget", "Broad phase keeps candidate pairs conservative and reduces brute-force work.", true),
        Triple("Constraint joints: distance, spring, rope and weld", "2D/3D joints converge, obey rest length and break at a configured force.", true),
        Triple("Verlet ropes, cloth, wind and radial impulses", "Soft bodies remain stable across frames and respond to wind/impulses.", true),
        Triple("Swept circle/box/sphere shape casts and CCD", "Fast bodies hit the first collider; casts report the nearest hit and distance.", true),
        Triple("Area force fields: explode, attract and wind", "Forces affect tagged dynamic bodies inside a radius without tunneling.", true),
        Triple("Event bus, ES6 coroutines and deterministic timers", "Ordered pub/sub, once/wildcard listeners and waitFor/waitSec fibers work.", true),
        Triple("FSM states, hot reload, instruction budgets and profiler timings", "State enter/update hooks run; reload keeps persist; runaway loops are stopped.", true),
        Triple("Durable save slots and full-game state API", "game.save/load/has/keys persist progress across scene loads and launches.", true),
        Triple("Camera rigs: priority, dead zones, bounds and look-ahead", "Multiple cameras blend by priority; follow respects dead zones and bounds.", true),
        Triple("Trauma shake, blends, snapping and Catmull–Rom dolly paths", "Shake decays smoothly; cinematic paths interpolate and can be stopped.", true),
        Triple("Camera director with script control and 2D/3D parity", "camera.playPath/activate/setBounds drive both 2D and 3D rigs.", true),
        Triple("Project settings, diagnostics and health reports in the editor", "Settings are editable; diagnostics report schema, scenes, assets and warnings.", true),
        Triple("Object creation for joints and advanced camera rigs", "Editor menu adds v3 joints/cameras and inspector exposes their properties.", true),
        Triple("Full game 1: Citadel of Echoes, six scenes", "Menu → two levels → boss → victory/game over, with saves and AI.", true),
        Triple("Full game 2: Neon Apex GP, three scenes", "Countdown, three laps, rival AI, boosts, best-lap saves and podium.", true),
        Triple("Headless regression suite and CI APK pipeline", "All engine/game tests pass; CI assembles installable debug + release APKs.", true),
        Triple("Localization, accessibility and scalable UI", "All UI strings externalized; touch targets and font scaling verified.", false),
        Triple("Asset bundles, streaming, LOD and memory budgets", "Large scenes stream in under the target memory ceiling on mid-range phones.", false),
        Triple("Replay and networking-ready deterministic sandbox", "Replays reproduce a session; netcode spike validates lockstep transport.", false),
        Triple("Console-quality polish: GPU particles, occlusion and post FX", "Render profile hits the frame budget with the reference games at High quality.", false),
        Triple("Release candidate: store build, QA sign-off and retrospective", "Signed store build passes the full QA matrix; two-year plan reviewed.", false)
    )

    fun phaseFor(month: Int): String = PHASES[(month - 1).coerceIn(0, MONTHS - 1) / 3]

    fun defaults(name: String, startEpochDay: Long = System.currentTimeMillis() / 86_400_000L): Roadmap {
        val list = DEFINITIONS.mapIndexed { i, (title, acceptance, shipped) ->
            RoadmapMilestone(
                id = "M%02d".format(i + 1),
                month = i + 1,
                phase = phaseFor(i + 1),
                title = title,
                acceptance = acceptance,
                shipped = shipped,
                done = false
            )
        }.toMutableList()
        return Roadmap(name, startEpochDay, list)
    }

    private fun file(p: Project): File = File(p.dir, "roadmap.json")

    /** Loads a saved plan, merging new built-in milestones forward. Never creates a file. */
    fun load(p: Project): Roadmap {
        val f = file(p)
        if (!f.exists()) return defaults(p.name, p.created / 86_400_000L)
        return try {
            val o = JSONObject(f.readText())
            val plan = Roadmap(
                projectName = o.optString("project", p.name),
                startEpochDay = o.optLong("startEpochDay", p.created / 86_400_000L),
                milestones = ArrayList()
            )
            val arr = o.optJSONArray("milestones")
            val saved = HashMap<String, RoadmapMilestone>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val m = RoadmapMilestone.fromJson(arr.getJSONObject(i))
                    if (m.id.isNotBlank()) saved[m.id] = m
                }
            }
            for (d in defaults(p.name, plan.startEpochDay).milestones) {
                val old = saved[d.id]
                plan.milestones.add(if (old == null) d else d.copy(
                    phase = if (old.phase.isBlank()) d.phase else old.phase,
                    title = if (old.title.isBlank()) d.title else old.title,
                    acceptance = if (old.acceptance.isBlank()) d.acceptance else old.acceptance,
                    done = old.done || (old.complete && !d.shipped)
                ))
            }
            plan
        } catch (_: Exception) {
            defaults(p.name, p.created / 86_400_000L)
        }
    }

    fun save(p: Project, plan: Roadmap) {
        p.dir.mkdirs()
        file(p).writeText(plan.toJson().toString(2))
    }

    fun markDone(p: Project, id: String, done: Boolean = true): Boolean {
        val plan = load(p)
        val m = plan.milestones.firstOrNull { it.id == id } ?: return false
        if (m.shipped) return true
        m.done = done
        save(p, plan)
        return true
    }

    fun markNext(p: Project): RoadmapMilestone? {
        val plan = load(p)
        val m = plan.next ?: return null
        m.done = true
        save(p, plan)
        return m
    }

    fun reset(p: Project): Roadmap {
        val plan = defaults(p.name, p.created / 86_400_000L)
        save(p, plan)
        return plan
    }

    fun report(plan: Roadmap): String {
        val sb = StringBuilder()
        sb.appendLine("S Engine ${EngineInfo.VERSION} — ${EngineInfo.EDITION}")
        sb.appendLine("Two-year production plan · ${plan.projectName}")
        sb.appendLine("Start: ${LocalDate.ofEpochDay(plan.startEpochDay)}   Progress: ${plan.completeCount}/${plan.milestones.size} (${plan.progress}%)")
        val next = plan.next
        sb.appendLine("Next: ${next?.let { "${it.id} · ${it.title}" } ?: "All milestones complete"}")
        sb.appendLine("Shipped engine milestones are marked [S]; project sign-off is [x].")
        sb.appendLine()
        var phase = ""
        for (m in plan.milestones) {
            if (m.phase != phase) {
                phase = m.phase
                sb.appendLine("── ${phase.uppercase()} ──")
            }
            val mark = when {
                m.shipped -> "S"
                m.done -> "x"
                else -> " "
            }
            sb.appendLine("[${mark}] M${m.month.toString().padStart(2, '0')} ${plan.dueDate(m)}  ${m.title}")
            sb.appendLine("      ✓ ${m.acceptance}")
        }
        sb.appendLine()
        sb.appendLine("Tip: tap a planned milestone in the editor's Roadmap dialog to sign it off.")
        return sb.toString()
    }

    fun brief(): String = EngineInfo.ROADMAP_BRIEF.trimIndent()
}
