package com.sengine.project

import org.json.JSONObject

/** Single source of truth for engine/version information. */
object EngineInfo {
    const val VERSION = "3.0.0"
    const val EDITION = "Professional Studio Edition"
    const val SCHEMA_VERSION = 3
    const val ENGINE_ID = "S Engine"

    /** Short version of the multi-quarter plan; the full plan lives in docs/ROADMAP.md. */
    const val ROADMAP_BRIEF = """
Q1  Deterministic simulation, spatial-hash broad phase, joints, soft bodies, shape casts, area forces
Q2  Camera rig (dead zones, bounds, look-ahead, blends, cinematics), events, coroutines, hot reload, budgets
Q3  Project schema/migrations, build profiles, diagnostics, editor tooling, two full reference games
Q4  Networking-ready replay, save system, performance budgets, CI regression suite
Y2  Visual scripting 2, animation graph, GPU particles, occlusion, asset pipeline, console-quality polish
"""
}

/**
 * Per-project simulation / quality settings. Persisted in project.json and
 * applied by the engine when play begins, so old projects keep their old
 * behaviour until explicitly upgraded.
 */
data class ProjectSettings(
    var fixedHz: Int = 60,
    var maxSubSteps: Int = 5,
    var solverIterations: Int = 1,
    var jointIterations: Int = 2,
    var broadphase: Boolean = true,
    var broadphaseCell: Float = 4f,
    var deterministicSeed: Int = 1337,
    var quality: Int = 2, // 0 Low, 1 Medium, 2 High, 3 Ultra
    var scriptBudget: Int = 250_000,
    var cameraBlend: Float = 0.35f,
    var autoMigrate: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("fixedHz", fixedHz)
        put("maxSubSteps", maxSubSteps)
        put("solverIterations", solverIterations)
        put("jointIterations", jointIterations)
        put("broadphase", broadphase)
        put("broadphaseCell", broadphaseCell.toDouble())
        put("deterministicSeed", deterministicSeed)
        put("quality", quality)
        put("scriptBudget", scriptBudget)
        put("cameraBlend", cameraBlend.toDouble())
        put("autoMigrate", autoMigrate)
    }

    companion object {
        val QUALITY = listOf("Low", "Medium", "High", "Ultra")

        fun fromJson(o: JSONObject?): ProjectSettings {
            val s = ProjectSettings()
            if (o == null) return s
            s.fixedHz = o.optInt("fixedHz", s.fixedHz).coerceIn(30, 240)
            s.maxSubSteps = o.optInt("maxSubSteps", s.maxSubSteps).coerceIn(1, 12)
            s.solverIterations = o.optInt("solverIterations", s.solverIterations).coerceIn(1, 8)
            s.jointIterations = o.optInt("jointIterations", s.jointIterations).coerceIn(1, 12)
            s.broadphase = o.optBoolean("broadphase", s.broadphase)
            s.broadphaseCell = o.optDouble("broadphaseCell", s.broadphaseCell.toDouble()).toFloat().coerceIn(0.25f, 64f)
            s.deterministicSeed = o.optInt("deterministicSeed", s.deterministicSeed)
            s.quality = o.optInt("quality", s.quality).coerceIn(0, 3)
            s.scriptBudget = o.optInt("scriptBudget", s.scriptBudget).coerceIn(10_000, 5_000_000)
            s.cameraBlend = o.optDouble("cameraBlend", s.cameraBlend.toDouble()).toFloat().coerceIn(0f, 5f)
            s.autoMigrate = o.optBoolean("autoMigrate", s.autoMigrate)
            return s
        }
    }
}

/** A single schema upgrade step. Migrations are chained and idempotent. */
data class Migration(val from: Int, val to: Int, val description: String, val apply: (JSONObject) -> Unit)

/**
 * Forward-compatible project schema. `migrate()` upgrades any older
 * project.json (v1 "S Engine 1.0", v2 "2.x") to the current schema without
 * losing scenes, orientation or start scene.
 */
object ProjectSchema {

    val migrations: List<Migration> = listOf(
        Migration(0, 1, "v1 projects get an explicit schema version and start scene") { o ->
            if (!o.has("startScene")) o.put("startScene", "Main")
            o.put("schemaVersion", 1)
        },
        Migration(1, 2, "v2 adds engine version, orientation and creation timestamp") { o ->
            if (!o.has("orientation")) o.put("orientation", 0)
            if (!o.has("created")) o.put("created", System.currentTimeMillis())
            o.put("engineVersion", "2.0.0")
            o.put("schemaVersion", 2)
        },
        Migration(2, 3, "v3 adds simulation/quality settings, edition and migration policy") { o ->
            if (!o.has("settings")) o.put("settings", ProjectSettings().toJson())
            o.put("engineVersion", EngineInfo.VERSION)
            o.put("edition", EngineInfo.EDITION)
            o.put("engine", "${EngineInfo.ENGINE_ID} ${EngineInfo.VERSION}")
            o.put("schemaVersion", EngineInfo.SCHEMA_VERSION)
        }
    )

    /**
     * Mutates [o] in place, applies every pending migration in order and
     * returns the list of applied descriptions (empty when already current).
     */
    fun migrate(o: JSONObject): List<String> {
        var version = o.optInt("schemaVersion", 0)
        val applied = ArrayList<String>()
        for (m in migrations) {
            if (version < m.to && version <= m.from) {
                m.apply(o)
                version = m.to
                applied.add("${m.from}→${m.to}: ${m.description}")
            }
        }
        // Unknown/newer versions: keep the data untouched, just stamp metadata.
        if (version == 0) {
            o.put("settings", ProjectSettings().toJson())
            o.put("schemaVersion", EngineInfo.SCHEMA_VERSION)
            o.put("engineVersion", EngineInfo.VERSION)
            applied.add("0→${EngineInfo.SCHEMA_VERSION}: recovery migration")
        }
        o.put("schemaVersion", maxOf(version, EngineInfo.SCHEMA_VERSION))
        return applied
    }

    fun validate(o: JSONObject): List<String> {
        val issues = ArrayList<String>()
        if (o.optString("startScene", "Main").isBlank()) issues.add("startScene is empty")
        if (o.optInt("schemaVersion", 0) < EngineInfo.SCHEMA_VERSION) issues.add("outdated schemaVersion")
        val s = o.optJSONObject("settings") ?: ProjectSettings().toJson()
        if (s.optInt("fixedHz", 60) !in 30..240) issues.add("fixedHz out of range")
        if (s.optInt("scriptBudget", 250_000) < 10_000) issues.add("scriptBudget too low")
        return issues
    }
}

/** Human-readable health report shown by the editor's Diagnostics action. */
object ProjectDiagnostics {
    fun report(p: Project): String {
        val scenes = p.listScenes()
        val assets = p.listAssets()
        val scripts = assets.filter { it.endsWith(".js") || it.endsWith(".bp") || it.endsWith(".glsl") }
        val warnings = ArrayList<String>()
        if (scenes.isEmpty()) warnings.add("No scenes — add a Main scene")
        if (!scenes.contains(p.startScene)) warnings.add("Start scene '${p.startScene}' does not exist")
        if (scripts.isEmpty()) warnings.add("No scripts — this project has no behaviours")
        val settings = p.settings
        if (settings.solverIterations > 4) warnings.add("Solver iterations > 4 may cost frame time on phones")
        if (settings.quality == 3) warnings.add("Ultra quality is not recommended for low-end devices")
        for (name in scenes) {
            val scene = p.loadScene(name)
            if (scene.objects.none { it.components.any { c -> c.type == "Camera" || c.type == "Camera3D" } })
                warnings.add("Scene '$name' has no camera")
        }
        val plan = StudioPlan.load(p)
        val sb = StringBuilder()
        sb.appendLine("Engine      ${EngineInfo.ENGINE_ID} ${EngineInfo.VERSION} — ${EngineInfo.EDITION}")
        sb.appendLine("Schema      v${EngineInfo.SCHEMA_VERSION} (${if (warnings.isEmpty()) "OK" else "${warnings.size} warning(s)"})")
        sb.appendLine("Project     ${p.name}")
        sb.appendLine("Roadmap     ${plan.completeCount}/${plan.milestones.size} milestones (${plan.progress}%) — next: ${plan.next?.id ?: "complete"}")
        sb.appendLine("Scenes      ${scenes.size} — start: ${p.startScene}")
        sb.appendLine("Assets      ${assets.size} (${scripts.size} scripts/shaders/blueprints)")
        sb.appendLine("Simulation  ${settings.fixedHz} Hz, ${settings.maxSubSteps} substeps, ${settings.solverIterations} solver iterations")
        sb.appendLine("Broadphase  ${if (settings.broadphase) "spatial hash (${settings.broadphaseCell} units)" else "brute force"}")
        sb.appendLine("Physics     CCD, joints, soft bodies, shape casts, area forces")
        sb.appendLine("Scripts     events, coroutines, FSM, hot reload, instruction budgets")
        sb.appendLine("Cameras     priority blends, dead zones, bounds, shake, cinematic paths")
        sb.appendLine("Quality     ${ProjectSettings.QUALITY[settings.quality]}")
        sb.appendLine("Script cap  ${settings.scriptBudget} instructions/call")
        if (warnings.isEmpty()) sb.appendLine("\n✔ No issues found")
        else { sb.appendLine("\nWarnings:"); warnings.forEach { sb.appendLine(" • $it") } }
        return sb.toString()
    }
}
