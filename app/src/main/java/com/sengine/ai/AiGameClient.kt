package com.sengine.ai

import android.content.Context
import com.sengine.engine.core.SceneSerializer
import com.sengine.project.Project
import com.sengine.project.ProjectManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Calls the S Engine Game Agent and imports its normalized SceneSerializer project payload. */
object AiGameClient {
    const val API_BASE = "https://game-agent-production.up.railway.app"

    fun generate(context: Context, prompt: String): Project {
        val connection = (URL(API_BASE + "/api/games/generate").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 600000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            context.getSharedPreferences("sengine_ai", Context.MODE_PRIVATE)
                .getString("api_token", null)?.takeIf { it.isNotBlank() }?.let { token ->
                    setRequestProperty("Authorization", "Bearer " + token)
                }
        }
        val request = JSONObject().put("prompt", prompt).toString()
        val responseText: String
        try {
            connection.outputStream.use { it.write(request.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = try { JSONObject(responseText).optString("error", "Agent request failed (" + status + ")") }
                    catch (_: Exception) { "Agent request failed (" + status + ")" }
                throw IllegalStateException(message)
            }
        } finally {
            connection.disconnect()
        }

        val response = JSONObject(responseText)
        val payload = response.getJSONObject("project")
        val projectInfo = payload.getJSONObject("project")
        val requestedName = ProjectManager.sanitize(projectInfo.optString("name", "AI Game")).ifBlank { "AI Game" }
        var name = requestedName
        var suffix = 2
        while (ProjectManager.exists(context, name)) {
            name = requestedName + " " + suffix
            suffix++
        }

        val project = ProjectManager.open(context, name)
        project.startScene = projectInfo.optString("startScene", "Main")
        project.orientation = projectInfo.optInt("orientation", 0).coerceIn(0, 1)
        project.saveMeta()

        val assets = payload.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val assetName = asset.optString("name")
                require(assetName.isNotBlank() && !assetName.contains("..") &&
                    !assetName.contains("/") && !assetName.contains("\\")) {
                    "Unsafe generated asset path: " + assetName
                }
                project.writeAsset(assetName, asset.getString("content"))
            }
        }

        val scenes = payload.getJSONArray("scenes")
        require(scenes.length() > 0) { "The agent returned no scenes" }
        for (i in 0 until scenes.length()) {
            val sceneJson = scenes.getJSONObject(i)
            val scene = SceneSerializer.fromJson(sceneJson)
            project.saveScene(scene)
        }
        if (!project.sceneExists(project.startScene)) {
            project.startScene = project.listScenes().firstOrNull() ?: "Main"
        }
        project.saveMeta()
        return project
    }
}
