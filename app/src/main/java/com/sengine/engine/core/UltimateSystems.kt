package com.sengine.engine.core

import org.json.JSONObject
import org.json.JSONArray

/**
 * Ultimate Edition - Additional Core Systems
 * Asset Database, Build Pipeline, Version Control, Analytics, Crash Reporting
 */

class AssetDatabase {
    data class AssetInfo(var guid: String, var path: String, var type: String, var labels: List<String> = emptyList(), var bundle: String = "", var size: Long = 0)

    private val assets = mutableMapOf<String, AssetInfo>() // guid -> info
    private val pathToGuid = mutableMapOf<String, String>()

    fun register(path: String, type: String): AssetInfo {
        val guid = pathToGuid[path] ?: java.util.UUID.randomUUID().toString()
        val info = AssetInfo(guid, path, type)
        assets[guid] = info
        pathToGuid[path] = guid
        return info
    }

    fun getByGuid(guid: String): AssetInfo? = assets[guid]
    fun getByPath(path: String): AssetInfo? = pathToGuid[path]?.let { assets[it] }
    fun allAssets(): List<AssetInfo> = assets.values.toList()
    fun findByType(type: String): List<AssetInfo> = assets.values.filter { it.type == type }
    fun findByLabel(label: String): List<AssetInfo> = assets.values.filter { label in it.labels }
    fun remove(guid: String) { assets[guid]?.let { pathToGuid.remove(it.path) }; assets.remove(guid) }
}

class BuildPipeline {
    enum class BuildTarget { Android, Windows, Linux, MacOS, WebGL, iOS }
    enum class BuildResult { Succeeded, Failed, Cancelled, Unknown }

    data class BuildReport(var target: BuildTarget, var result: BuildResult, var duration: Float, var size: Long, var errors: List<String> = emptyList(), var warnings: List<String> = emptyList())

    var target: BuildTarget = BuildTarget.Android
    var developmentBuild: Boolean = false
    var allowDebugging: Boolean = false
    var connectWithProfiler: Boolean = false
    var enableDeepProfiling: Boolean = false
    var buildScriptsOnly: Boolean = false

    fun build(project: com.sengine.project.Project): BuildReport {
        val start = System.nanoTime()
        // Simulate build
        val size = project.getProjectSize()
        val duration = (System.nanoTime() - start) / 1e9f
        return BuildReport(target, BuildResult.Succeeded, duration, size)
    }
}

class VersionControlSystem {
    data class Commit(var id: String, var message: String, var timestamp: Long, var author: String, var changes: List<String>)

    private val commits = mutableListOf<Commit>()
    private val staged = mutableListOf<String>()

    fun stage(path: String) { if (path !in staged) staged.add(path) }
    fun unstage(path: String) { staged.remove(path) }
    fun commit(message: String, author: String = "Developer"): Commit {
        val commit = Commit(java.util.UUID.randomUUID().toString().substring(0, 8), message, System.currentTimeMillis(), author, staged.toList())
        commits.add(commit)
        staged.clear()
        return commit
    }

    fun log(): List<Commit> = commits.reversed()
    fun status(): List<String> = staged.toList()
    fun diff(commitId: String): List<String> = commits.firstOrNull { it.id == commitId }?.changes ?: emptyList()
}

class AnalyticsSystem {
    var enabled: Boolean = false
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()

    fun trackEvent(name: String, params: Map<String, Any> = emptyMap()) {
        if (!enabled) return
        events.add(name to params)
    }

    fun getEvents(): List<Pair<String, Map<String, Any>>> = events.toList()
    fun clear() = events.clear()
}

class CrashReportingSystem {
    var enabled: Boolean = true
    var captureScreenshots: Boolean = true
    var captureLogs: Boolean = true

    data class CrashReport(var timestamp: Long, var exception: String, var stackTrace: String, var logs: List<String> = emptyList())

    private val reports = mutableListOf<CrashReport>()

    fun reportException(e: Throwable, logs: List<String> = emptyList()) {
        if (!enabled) return
        reports.add(CrashReport(System.currentTimeMillis(), e.message ?: "Unknown", e.stackTraceToString(), logs))
    }

    fun getReports(): List<CrashReport> = reports.toList()
    fun clear() = reports.clear()
}

class LocalizationSystem {
    private val tables = mutableMapOf<String, MutableMap<String, String>>() // locale -> key -> value
    var currentLocale: String = "en"

    fun addString(locale: String, key: String, value: String) {
        tables.getOrPut(locale) { mutableMapOf() }[key] = value
    }

    fun getString(key: String, locale: String = currentLocale): String {
        return tables[locale]?.get(key) ?: tables["en"]?.get(key) ?: key
    }

    fun getLocales(): List<String> = tables.keys.toList()
    fun hasKey(key: String): Boolean = tables.values.any { key in it }
}

class AchievementSystem {
    data class Achievement(var id: String, var name: String, var description: String, var unlocked: Boolean = false, var progress: Float = 0f, var hidden: Boolean = false)

    private val achievements = mutableMapOf<String, Achievement>()

    fun register(achievement: Achievement) { achievements[achievement.id] = achievement }
    fun unlock(id: String) { achievements[id]?.let { it.unlocked = true; it.progress = 1f } }
    fun setProgress(id: String, progress: Float) { achievements[id]?.progress = progress.coerceIn(0f, 1f) }
    fun isUnlocked(id: String): Boolean = achievements[id]?.unlocked ?: false
    fun getAll(): List<Achievement> = achievements.values.toList()
    fun getUnlocked(): List<Achievement> = achievements.values.filter { it.unlocked }
}

class LeaderboardSystem {
    data class Entry(var playerName: String, var score: Long, var rank: Int = 0, var timestamp: Long = System.currentTimeMillis())

    private val leaderboards = mutableMapOf<String, MutableList<Entry>>()

    fun submitScore(leaderboard: String, playerName: String, score: Long) {
        val list = leaderboards.getOrPut(leaderboard) { mutableListOf() }
        list.add(Entry(playerName, score))
        list.sortByDescending { it.score }
        for ((i, entry) in list.withIndex()) entry.rank = i + 1
    }

    fun getTop(leaderboard: String, count: Int = 10): List<Entry> = leaderboards[leaderboard]?.take(count) ?: emptyList()
    fun getRank(leaderboard: String, playerName: String): Int? = leaderboards[leaderboard]?.firstOrNull { it.playerName == playerName }?.rank
}

class InAppPurchaseSystem {
    data class Product(var id: String, var name: String, var price: Float, var type: Int = 0, var consumable: Boolean = false)

    private val products = mutableMapOf<String, Product>()
    private val purchases = mutableListOf<String>()

    fun register(product: Product) { products[product.id] = product }
    fun purchase(id: String): Boolean {
        if (id !in products) return false
        purchases.add(id)
        return true
    }

    fun isPurchased(id: String): Boolean = id in purchases
    fun restorePurchases(): List<String> = purchases.toList()
}
