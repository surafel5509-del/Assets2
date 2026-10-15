package com.sengine.engine.core

/** Read-only checks shared by the editor and pre-play validation. Run under the engine lock. */
object SceneDiagnostics {
    enum class Severity { WARNING, ERROR }
    data class Issue(val severity: Severity, val message: String, val objectId: Long? = null)

    fun inspect(scene: Scene, assetExists: (String) -> Boolean = { true }): List<Issue> {
        val issues = mutableListOf<Issue>()
        val ids = HashSet<Long>()
        val members = scene.objects.toHashSet()
        fun report(go: GameObject, message: String, severity: Severity = Severity.ERROR) {
            issues.add(Issue(severity, "${go.name}: $message", go.id))
        }
        for (go in scene.objects) {
            if (go.id <= 0 || !ids.add(go.id)) report(go, "Invalid or duplicate object ID ${go.id}")
            if (go.parent != null && go.parent !in members) report(go, "Parent belongs to another scene")
            val values = listOf(go.x, go.y, go.z, go.rotX, go.rotY, go.rotation, go.scaleX, go.scaleY, go.scaleZ)
            if (values.any { !it.isFinite() }) report(go, "Transform contains a non-finite value")
            if (go.scaleX == 0f || go.scaleY == 0f || go.scaleZ == 0f) report(go, "Zero scale makes transforms non-invertible", Severity.WARNING)
            for (component in go.components) {
                if (component is MissingComponent) report(go, "Unavailable component '${component.type}' retained in scene", Severity.WARNING)
                for (prop in component.props()) {
                    if (prop is Prop.F && !prop.get().isFinite()) report(go, "${component.type}.${prop.name} is not finite")
                    if (prop is Prop.Asset && prop.get().isNotBlank() && !assetExists(prop.get())) {
                        report(go, "Missing ${prop.kind.name.lowercase()} '${prop.get()}' (${component.type})", Severity.WARNING)
                    }
                }
            }
        }
        if (scene.objects.none { it.isActiveInHierarchy() && (it.get<Camera2D>() != null || it.get<Camera3D>() != null) }) {
            issues.add(Issue(Severity.WARNING, "No active camera. Add a 2D or 3D camera before playing."))
        }
        return issues
    }
}

/** Keeps plugin/newer component data intact when this editor cannot instantiate it. */
class MissingComponent(private val data: org.json.JSONObject) : Component() {
    override val type: String = data.optString("type", "Unknown")
    init { enabled = data.optBoolean("enabled", true) }
    override fun props(): List<Prop> = emptyList()
    override fun toJson(): org.json.JSONObject = org.json.JSONObject(data.toString()).put("enabled", enabled)
}
