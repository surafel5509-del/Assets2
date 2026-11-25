package com.sengine.ai

import android.content.Context
import com.sengine.engine.core.AssetKind
import com.sengine.engine.core.Component
import com.sengine.engine.core.ComponentRegistry
import com.sengine.engine.core.Prop
import com.sengine.engine.core.Scene
import com.sengine.engine.core.SceneSerializer
import com.sengine.engine.core.ScriptComponent
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.mozilla.javascript.Context as RhinoContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Calls the S Engine Game Agent and strictly imports its SceneSerializer project payload.
 *
 * The parser derives accepted component fields from the real [ComponentRegistry] and [Prop]
 * descriptors, so a malformed model response cannot silently introduce unsupported APIs.
 */
object AiGameClient {
    const val API_BASE = "https://game-agent-production.up.railway.app"
    const val MAX_RESPONSE_BYTES = 2_000_000
    const val MAX_TOTAL_ASSET_BYTES = 1_000_000

    private const val MAX_PROMPT = 4_000
    private const val MAX_SCENES = 30
    private const val MAX_OBJECTS = 2_000
    private const val MAX_ASSETS = 100
    private val PROJECT_NAME = Regex("^[A-Za-z0-9 _-]{1,40}$")
    private val ASSET_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._ -]{0,79}$")
    private val COLOR = Regex("^#?[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
    private val LIFECYCLE = Regex("\\bfunction\\s+(start|update|onFixedUpdate|onLateUpdate|onEnable|onDisable|onCollision|onTrigger|onTriggerExit|onTap|onDestroy|onStop|onHotReload)\\s*\\(")
    private val TEXT_EXTENSIONS = setOf("js", "bp", "glsl", "anim", "obj", "txt", "json")
    private val PAYLOAD_KEYS = setOf("format", "formatVersion", "project", "scenes", "assets", "notes")
    private val PROJECT_KEYS = setOf("name", "startScene", "orientation")
    private val SCENE_KEYS = setOf(
        "name", "gravityX", "gravityY", "gravityZ", "gravity3D", "ambient", "fogColor",
        "fogStart", "fogEnd", "fog", "nextId", "objects"
    )
    private val OBJECT_KEYS = setOf(
        "id", "name", "tag", "active", "order", "x", "y", "z", "rotation", "rotX", "rotY",
        "scaleX", "scaleY", "scaleZ", "parent", "components"
    )
    private val ASSET_KEYS = setOf("name", "content")

    private val FORBIDDEN_SCRIPT = listOf(
        Regex("\\b(document|window|navigator|location)\\s*\\."),
        Regex("\\b(Unity|Godot|Phaser|Three|Babylon)\\b"),
        Regex("\\b(requestAnimationFrame|setTimeout|setInterval|setImmediate|fetch|XMLHttpRequest|alert|prompt|confirm)\\s*\\("),
        Regex("\\bconsole\\s*\\."),
        Regex("\\b(import|export|class|async|await|let|const)\\b"),
        Regex("=>"), Regex("\\?\\?"), Regex("\\?\\."),
        Regex("\\beval\\s*\\("), Regex("\\bnew\\s+Function\\s*\\("), Regex("\\bprocess\\s*\\.")
    )
    private val API_METHODS: Map<String, Set<String>> = mapOf(
        "events" to setOf("on", "once", "off", "emit", "last", "count", "clear"),
        "state" to setOf("set", "get", "is", "elapsed", "update"),
        "physics" to setOf("circleCast", "boxCast", "sphereCast", "hitDistance", "explode", "attract", "wind", "joint", "joint3", "setIterations", "getIterations", "getJointIterations", "getBroadphasePairs", "getBruteForcePairs", "getFixedStep", "getAlpha", "getSteps", "getSeed", "getJointCount"),
        "soft" to setOf("rope", "cloth", "point", "setPoint", "pin", "wind", "force", "explode", "step", "remove", "count", "names"),
        "camera" to setOf("getX", "getY", "getZ", "getZoom", "getFov", "getPriority", "isBlending", "isCinematic", "setBounds", "setDeadZone", "setLookAhead", "zoomTo", "shake", "snap", "panTo", "setPriority", "activate", "stopPath", "playPath"),
        "game" to setOf("save", "load", "has", "clear", "keys", "emit", "getVersion", "getEdition", "getSchema", "fixedStep", "instructions", "diagnostics"),
        "studio" to setOf("report", "brief", "progress", "years", "months", "next", "milestones", "complete", "reset", "diagnostics"),
        "scene" to setOf("getName", "find", "findAll", "count", "spawn", "load", "reload", "getCamera", "getGravityX", "setGravityX", "getGravityY", "setGravityY", "shake", "broadcast", "pool", "obtain", "release", "raycast", "raycast2D", "raycastAll", "raycastAll3D", "overlapCircle", "overlapSphere", "terrainHeight", "getCamera3D", "gravity3D"),
        "audio" to setOf("play", "beep", "stopAll", "play3d", "play3dLoop", "move3d", "stop3d", "setFalloff", "setBusVolume", "playMusic", "pauseMusic", "resumeMusic", "setMusicVolume"),
        "self" to setOf("setPosition", "move", "rotate", "addForce", "addImpulse", "setVelocity", "destroy", "child", "distanceTo", "distanceTo3", "overlaps", "is", "send", "hasComponent", "setComponentEnabled", "setTexture", "burst", "setEmitting", "play", "stopAnimation", "isAnimationFinished", "setAnimSpeed", "setShaderParam", "setMeshColor", "forward", "setSky", "getParent", "setColor", "setIntensity", "setSize"),
        "transform" to setOf("setPosition", "move", "rotate", "addForce", "addImpulse", "setVelocity", "destroy", "child", "distanceTo", "distanceTo3", "overlaps", "is", "send", "hasComponent", "setComponentEnabled", "setTexture", "burst", "setEmitting", "play", "stopAnimation", "isAnimationFinished", "setAnimSpeed", "setShaderParam", "setMeshColor", "forward", "setSky", "getParent", "setColor", "setIntensity", "setSize"),
        "gameObject" to setOf("setPosition", "move", "rotate", "addForce", "addImpulse", "setVelocity", "destroy", "child", "distanceTo", "distanceTo3", "overlaps", "is", "send", "hasComponent", "setComponentEnabled", "setTexture", "burst", "setEmitting", "play", "stopAnimation", "isAnimationFinished", "setAnimSpeed", "setShaderParam", "setMeshColor", "forward", "setSky", "getParent", "setColor", "setIntensity", "setSize"),
        "input" to setOf("key")
    )
    private val BLUEPRINT_NODES = setOf(
        "AddForce", "AddVar", "Beep", "Burst", "Chance", "Cooldown", "Controller3D", "DestroyOther",
        "DestroySelf", "FlipX", "If", "JoystickMove", "Jump", "LoadScene", "Log", "Move", "Move3D",
        "OnButtonA", "OnButtonB", "OnCollision", "OnMessage", "OnStart", "OnTap", "OnTimer", "OnTouch",
        "OnTrigger", "OnTriggerExit", "OnUpdate", "Once", "Platformer", "PlayAnimation", "PlaySound",
        "Reload", "Repeat", "Rotate", "Rotate3D", "SendMessage", "Sequence", "SetActive", "SetColor",
        "SetPosition", "SetText", "SetVar", "SetVelocity", "Shake", "Spawn", "Wait"
    )
    private val BLUEPRINT_EVENTS = setOf(
        "OnStart", "OnUpdate", "OnTap", "OnTouch", "OnButtonA", "OnButtonB", "OnCollision",
        "OnTrigger", "OnTriggerExit", "OnTimer", "OnMessage"
    )

    /** Allows the UI to cancel a long HTTPS read and abort staging before any partial project is published. */
    class Cancellation {
        private val cancelled = AtomicBoolean(false)
        @Volatile private var connection: HttpURLConnection? = null
        fun isCancelled(): Boolean = cancelled.get()
        fun bind(value: HttpURLConnection) {
            connection = value
            if (cancelled.get()) value.disconnect()
        }
        fun cancel() {
            if (cancelled.compareAndSet(false, true)) connection?.disconnect()
        }
        fun clear() { connection = null }
    }

    data class ImportedAsset(val name: String, val content: String)
    data class ImportedProject(
        val name: String,
        val startScene: String,
        val orientation: Int,
        val assets: List<ImportedAsset>,
        val scenes: List<Scene>
    )

    private fun fail(message: String): Nothing = throw IllegalArgumentException(message)

    private fun finite(value: Any?): Boolean {
        val n = value as? Number ?: return false
        val d = n.toDouble()
        return !d.isNaN() && !d.isInfinite()
    }

    private fun integral(value: Any?): Boolean {
        val n = value as? Number ?: return false
        val d = n.toDouble()
        return finite(d) && d == n.toLong().toDouble()
    }

    private fun positiveLong(value: Any?): Boolean = integral(value) && (value as Number).toLong() > 0

    private fun safeName(name: String): Boolean =
        PROJECT_NAME.matches(name) && name.trim() == name && !name.contains("..")

    private fun safeAsset(name: String): Boolean {
        if (!ASSET_NAME.matches(name) || name.startsWith(".") || name.contains("..") ||
            name.contains("/") || name.contains("\\") || name.contains(":")
        ) return false
        return name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
    }

    private fun requireFiniteField(o: JSONObject, key: String, at: String) {
        if (o.has(key) && !finite(o.get(key))) fail("$at.$key must be a finite number")
    }

    private fun rejectUnknown(o: JSONObject, allowed: Set<String>, at: String) {
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next() as String
            if (key !in allowed) fail("$at has unsupported field '$key'")
        }
    }

    private fun requireColorField(o: JSONObject, key: String, at: String) {
        if (!o.has(key)) return
        val value = o.get(key)
        if (value !is String || !COLOR.matches(value)) fail("$at.$key must be #RRGGBB or #AARRGGBB")
    }

    private fun validateScript(name: String, content: String) {
        if (!LIFECYCLE.containsMatchIn(content)) fail("$name has no S Engine lifecycle function")
        if (content.contains('`')) fail("$name uses template literals, which the embedded Rhino subset does not support")
        for (pattern in FORBIDDEN_SCRIPT) {
            if (pattern.containsMatchIn(content)) fail("$name uses a forbidden or unsupported API: ${pattern.pattern}")
        }
        for ((namespace, methods) in API_METHODS) {
            val calls = Regex("\\b$namespace\\.([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(")
            for (match in calls.findAll(content)) {
                if (match.groupValues[1] !in methods) fail("$name uses unsupported $namespace.${match.groupValues[1]}() API")
            }
        }
        val cx = RhinoContext.enter()
        try {
            cx.languageVersion = RhinoContext.VERSION_ES6
            cx.optimizationLevel = -1
            cx.compileString(content, name, 1, null)
        } catch (e: Exception) {
            fail("$name is not valid Rhino JavaScript: ${e.message ?: "parse error"}")
        } finally {
            RhinoContext.exit()
        }
    }

    private fun validateBlueprint(name: String, content: String) {
        val bp = try { JSONObject(content) } catch (e: JSONException) { fail("$name is not valid blueprint JSON") }
        if (bp.optString("format") != "sengine-blueprint-1") fail("$name has an unsupported blueprint format")
        val nodes = bp.optJSONArray("nodes") ?: fail("$name has no blueprint nodes")
        val links = bp.optJSONArray("links") ?: fail("$name has no blueprint links")
        val ids = HashSet<Int>()
        var hasEvent = false
        for (i in 0 until nodes.length()) {
            val node = nodes.optJSONObject(i) ?: fail("$name.nodes[$i] is invalid")
            val id = node.optInt("id", -1)
            if (id < 1 || !ids.add(id)) fail("$name.nodes[$i] requires a unique positive id")
            val type = node.optString("type")
            if (type !in BLUEPRINT_NODES) fail("$name uses unsupported blueprint node '$type'")
            if (type == "Code") fail("$name uses an arbitrary Code blueprint node")
            if (type in BLUEPRINT_EVENTS) hasEvent = true
            if (node.has("params") && node.optJSONObject("params") == null) fail("$name.nodes[$i].params must be an object")
        }
        for (i in 0 until links.length()) {
            val link = links.optJSONObject(i) ?: fail("$name.links[$i] is invalid")
            if (!ids.contains(link.optInt("from", -1)) || !ids.contains(link.optInt("to", -1)) ||
                link.optString("pin").isBlank()
            ) fail("$name.links[$i] references an unknown node or lacks a pin")
        }
        if (!hasEvent) fail("$name has no blueprint lifecycle event node")
    }

    private fun validateAsset(name: String, content: String) {
        when (name.substringAfterLast('.', "").lowercase()) {
            "js" -> validateScript(name, content)
            "bp" -> validateBlueprint(name, content)
            "json" -> try { JSONObject(content) } catch (e: JSONException) { fail("$name is not valid JSON") }
            "glsl", "anim", "obj", "txt" -> if (content.isBlank()) fail("$name is empty")
        }
    }

    private fun assetMatchesKind(name: String, kind: AssetKind): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return AssetKind.of(name) == kind || (kind == AssetKind.SCRIPT && ext == "js")
    }

    /** Parses and validates a generate response. This method is Android-free and unit-testable. */
    fun parseGenerateResponse(response: JSONObject): ImportedProject {
        val payload = response.optJSONObject("project") ?: fail("The agent response has no project payload")
        rejectUnknown(payload, PAYLOAD_KEYS, "Project payload")
        if (payload.optString("format") != "s-engine-project") fail("Unsupported project payload format")
        if (payload.optInt("formatVersion", -1) != 1) fail("Unsupported project payload version")
        val info = payload.optJSONObject("project") ?: fail("Project metadata is missing")
        rejectUnknown(info, PROJECT_KEYS, "Project metadata")
        val name = info.optString("name")
        if (!safeName(name)) fail("Unsafe generated project name: $name")
        val orientation = info.optInt("orientation", 0)
        if (orientation != 0 && orientation != 1) fail("Project orientation must be 0 or 1")
        val startScene = info.optString("startScene", "Main")
        if (!safeName(startScene)) fail("Unsafe generated start scene name: $startScene")

        val assetArray = payload.optJSONArray("assets") ?: fail("Project assets are missing")
        if (assetArray.length() > MAX_ASSETS) fail("Project has too many assets")
        val assets = ArrayList<ImportedAsset>()
        val assetNames = HashSet<String>()
        var totalAssetBytes = 0
        for (i in 0 until assetArray.length()) {
            val asset = assetArray.optJSONObject(i) ?: fail("assets[$i] is invalid")
            rejectUnknown(asset, ASSET_KEYS, "assets[$i]")
            val assetName = asset.optString("name")
            if (!safeAsset(assetName)) fail("Unsafe generated asset path: $assetName")
            if (!assetNames.add(assetName)) fail("Duplicate generated asset name: $assetName")
            if (!asset.has("content")) fail("$assetName has no content")
            val content = try { asset.getString("content") } catch (e: JSONException) { fail("$assetName content must be a string") }
            val bytes = content.toByteArray(Charsets.UTF_8).size
            totalAssetBytes += bytes
            if (totalAssetBytes > MAX_TOTAL_ASSET_BYTES) fail("Generated assets exceed the 1 MB text limit")
            validateAsset(assetName, content)
            assets.add(ImportedAsset(assetName, content))
        }

        val sceneArray = payload.optJSONArray("scenes") ?: fail("Project scenes are missing")
        if (sceneArray.length() < 1 || sceneArray.length() > MAX_SCENES) fail("Project must contain 1 to $MAX_SCENES scenes")
        val scenes = ArrayList<Scene>()
        val sceneNames = HashSet<String>()
        for (si in 0 until sceneArray.length()) {
            val sceneJson = sceneArray.optJSONObject(si) ?: fail("scenes[$si] is invalid")
            rejectUnknown(sceneJson, SCENE_KEYS, "scenes[$si]")
            val sceneName = sceneJson.optString("name")
            if (!safeName(sceneName)) fail("Unsafe generated scene name: $sceneName")
            if (!sceneNames.add(sceneName)) fail("Duplicate generated scene name: $sceneName")
            for (key in arrayOf("gravityX", "gravityY", "gravityZ", "gravity3D", "fogStart", "fogEnd")) requireFiniteField(sceneJson, key, "scenes[$si]")
            for (key in arrayOf("ambient", "fogColor")) requireColorField(sceneJson, key, "scenes[$si]")
            if (sceneJson.has("nextId") && !positiveLong(sceneJson.get("nextId"))) fail("scenes[$si].nextId must be a positive integer")
            if (sceneJson.has("fog") && sceneJson.get("fog") !is Boolean) fail("scenes[$si].fog must be a boolean")
            val objects = sceneJson.optJSONArray("objects") ?: fail("scenes[$si].objects is missing")
            if (objects.length() < 2 || objects.length() > MAX_OBJECTS) fail("scenes[$si] needs 2 to $MAX_OBJECTS objects")
            val ids = HashSet<Long>()
            val parents = HashMap<Long, Long>()
            var hasCamera = false
            var hasScript = false
            for (oi in 0 until objects.length()) {
                val obj = objects.optJSONObject(oi) ?: fail("scenes[$si].objects[$oi] is invalid")
                val at = "scenes[$si].objects[$oi]"
                rejectUnknown(obj, OBJECT_KEYS, at)
                if (!positiveLong(obj.opt("id"))) fail("$at.id must be a positive integer")
                val id = (obj.get("id") as Number).toLong()
                if (!ids.add(id)) fail("$at.id is duplicated")
                if (obj.has("name") && obj.get("name") !is String) fail("$at.name must be a string")
                if (obj.has("tag") && obj.get("tag") !is String) fail("$at.tag must be a string")
                if (obj.has("active") && obj.get("active") !is Boolean) fail("$at.active must be a boolean")
                if (obj.has("order") && !integral(obj.get("order"))) fail("$at.order must be an integer")
                for (key in arrayOf("x", "y", "z", "rotation", "rotX", "rotY", "scaleX", "scaleY", "scaleZ")) requireFiniteField(obj, key, at)
                if (obj.has("parent")) {
                    if (!positiveLong(obj.get("parent"))) fail("$at.parent must be a positive integer")
                    parents[id] = (obj.get("parent") as Number).toLong()
                }
                val components = obj.optJSONArray("components") ?: fail("$at.components is missing")
                if (components.length() < 1) fail("$at.components must not be empty")
                for (ci in 0 until components.length()) {
                    val component = components.optJSONObject(ci) ?: fail("$at.components[$ci] is invalid")
                    val type = component.optString("type")
                    val prototype = ComponentRegistry.create(type) ?: fail("$at uses unsupported component type: $type")
                    if (component.has("enabled") && component.get("enabled") !is Boolean) fail("$at.components[$ci].enabled must be a boolean")
                    val props = prototype.props().associateBy { it.name }
                    val keys = component.keys()
                    while (keys.hasNext()) {
                        val field = keys.next() as String
                        if (field == "type" || field == "enabled") continue
                        val prop = props[field] ?: fail("$at uses unsupported field '$field' for $type")
                        val value = component.get(field)
                        when (prop) {
                            is Prop.F -> if (!finite(value)) fail("$at.$field must be a finite number")
                            is Prop.I -> if (!integral(value)) fail("$at.$field must be an integer")
                            is Prop.B -> if (value !is Boolean) fail("$at.$field must be a boolean")
                            is Prop.S -> if (value !is String) fail("$at.$field must be a string")
                            is Prop.Color -> if (value !is String || !COLOR.matches(value)) fail("$at.$field must be #RRGGBB or #AARRGGBB")
                            is Prop.Choice -> if (value !is String || value !in prop.options) fail("$at.$field must be one of ${prop.options.joinToString(", ")}")
                            is Prop.Asset -> {
                                if (value !is String) fail("$at.$field must be a string")
                                val ref = value
                                if (ref.isNotEmpty()) {
                                    if (!safeAsset(ref)) fail("$at.$field is an unsafe asset reference: $ref")
                                    if (!assetMatchesKind(ref, prop.kind)) fail("$at.$field references the wrong asset kind: $ref")
                                    if (ref !in assetNames) fail("$at.$field references missing asset: $ref")
                                }
                                if (prop.name == "Script" && ref.isEmpty()) fail("$at.Script must reference a .js or .bp asset")
                            }
                        }
                        if (prop is Prop.Asset && prop.name == "Script" && value is String && value.isNotEmpty()) {
                            val ext = value.substringAfterLast('.', "").lowercase()
                            if (ext !in setOf("js", "bp")) fail("$at.Script must reference a .js or .bp asset")
                        }
                    }
                    if (type == "Camera" || type == "Camera3D") hasCamera = true
                    if (type == "Script") {
                        hasScript = true
                        val ref = component.optString("Script")
                        if (ref.isBlank()) fail("$at.Script component is missing its uppercase Script asset reference")
                        if (ref !in assetNames) fail("$at references missing script asset: $ref")
                    }
                }
            }
            for ((child, parent) in parents) {
                if (parent !in ids) fail("scenes[$si] object $child references missing parent $parent")
                val seen = HashSet<Long>()
                var cursor = parent
                while (cursor in parents) {
                    if (cursor == child || !seen.add(cursor)) fail("scenes[$si] contains a parent cycle involving object $child")
                    cursor = parents[cursor] ?: break
                }
            }
            if (!hasCamera) fail("scenes[$si] has no Camera or Camera3D component")
            if (!hasScript) fail("scenes[$si] has no Script component")
            scenes.add(SceneSerializer.fromJson(sceneJson))
        }
        if (!sceneNames.contains(startScene)) fail("Project start scene '$startScene' does not exist")
        return ImportedProject(name, startScene, orientation, assets, scenes)
    }

    private fun readBounded(stream: InputStream?, maxBytes: Int): String {
        if (stream == null) return ""
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        stream.use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (out.size() + n > maxBytes) fail("The agent response is too large")
                out.write(buffer, 0, n)
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /** Stages and verifies a project in a private temporary directory, then renames it atomically. */
    fun importProject(root: File, response: JSONObject, cancellation: Cancellation? = null): Project {
        val parsed = parseGenerateResponse(response)
        root.mkdirs()
        var finalName = parsed.name
        var suffix = 2
        while (File(root, finalName).exists()) {
            finalName = "${parsed.name} $suffix"
            suffix++
        }
        val stagingDir = File(root, ".ai-import-${UUID.randomUUID()}")
        val staging = Project(stagingDir)
        try {
            if (cancellation?.isCancelled() == true) fail("AI generation cancelled")
            staging.startScene = parsed.startScene
            staging.orientation = parsed.orientation
            staging.saveMeta()
            for (asset in parsed.assets) {
                if (cancellation?.isCancelled() == true) fail("AI generation cancelled")
                staging.writeAsset(asset.name, asset.content)
            }
            for (scene in parsed.scenes) {
                if (cancellation?.isCancelled() == true) fail("AI generation cancelled")
                staging.saveScene(scene)
            }
            if (cancellation?.isCancelled() == true) fail("AI generation cancelled")
            if (!staging.sceneExists(parsed.startScene)) fail("Generated start scene was not saved")
            for (asset in parsed.assets) {
                if (staging.readAsset(asset.name) != asset.content) fail("Generated asset ${asset.name} failed verification")
            }
            for (savedScene in parsed.scenes) {
                val loaded = staging.loadScene(savedScene.name)
                if (loaded.objects.size != savedScene.objects.size) fail("Generated scene ${savedScene.name} failed verification")
                if (loaded.objects.none { go -> go.components.any { it is ScriptComponent && it.script in parsed.assets.map { a -> a.name } } }) {
                    fail("Generated scene ${savedScene.name} has no attached script asset")
                }
                if (loaded.objects.none { go -> go.components.any { it.type == "Camera" || it.type == "Camera3D" } }) {
                    fail("Generated scene ${savedScene.name} has no camera")
                }
            }
            val target = File(root, finalName)
            if (target.exists() || !stagingDir.renameTo(target)) fail("Could not publish generated project")
            return Project(target)
        } catch (e: Exception) {
            stagingDir.deleteRecursively()
            throw e
        }
    }

    fun generate(
        context: Context,
        prompt: String,
        token: String? = null,
        cancellation: Cancellation? = null
    ): Project {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.length < 8 || cleanPrompt.length > MAX_PROMPT) {
            fail("Prompt must contain between 8 and $MAX_PROMPT characters")
        }
        val url = URL(API_BASE + "/api/games/generate")
        if (url.protocol != "https") fail("The Game Agent endpoint must use HTTPS")
        val effectiveToken = token?.takeIf { it.isNotBlank() }
            ?: context.getSharedPreferences("sengine_ai", Context.MODE_PRIVATE)
                .getString("api_token", null)?.takeIf { it.isNotBlank() }
        val requestBytes = JSONObject().put("prompt", cleanPrompt).toString().toByteArray(Charsets.UTF_8)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 600_000
            doOutput = true
            instanceFollowRedirects = false
            setFixedLengthStreamingMode(requestBytes.size)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Connection", "close")
            effectiveToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        val responseText: String
        try {
            cancellation?.bind(connection)
            if (cancellation?.isCancelled() == true) fail("AI generation cancelled")
            connection.outputStream.use { it.write(requestBytes) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            responseText = readBounded(stream, MAX_RESPONSE_BYTES)
            if (status !in 200..299) {
                val message = try {
                    JSONObject(responseText).optString("error", "Agent request failed ($status)").take(300)
                } catch (e: Exception) {
                    "Agent request failed ($status)"
                }
                throw IllegalStateException(message)
            }
        } finally {
            cancellation?.clear()
            connection.disconnect()
        }
        if (cancellation?.isCancelled() == true) fail("AI generation cancelled")
        val response = try { JSONObject(responseText) } catch (e: JSONException) { fail("The agent returned malformed JSON") }
        return importProject(ProjectManager.root(context), response, cancellation)
    }
}
