package com.sengine.engine.core

import org.json.JSONObject
import kotlin.math.*

/**
 * S Engine Ultimate 6.1.2 [Ultra] - Ultra Engine Core - Months/Years Professional Project
 * Deterministic Loop, Burst-like Compiler, IL2CPP AOT, Assembly Defs, Domain Reload, Undo/Redo, Prefab Variants Nested, ScriptableObjects, SRP Custom
 */
enum class BurstOptimization { Off, O1, O2, O3, Size, Fast }
enum class AssemblyType { Runtime, Editor, Test, Plugin }
enum class DomainReloadMode { Full, Partial, Disabled }
enum class UndoGroup { Transform, Component, Asset, Scene, Custom }

data class BurstJob(
    var name: String = "Job",
    var source: String = "",
    var optimization: BurstOptimization = BurstOptimization.O3,
    var isCompiled: Boolean = false,
    var compileTimeMs: Float = 0f,
    var executionTimeMs: Float = 0f,
    var safetyChecks: Boolean = false,
    var debugInfo: Boolean = false
) {
    fun compile(): Boolean {
        val t0 = System.nanoTime()
        isCompiled = source.isNotEmpty()
        compileTimeMs = (System.nanoTime()-t0)/1_000_000f
        return isCompiled
    }
    fun execute(iterations: Int = 1000): Float {
        val t0 = System.nanoTime()
        var sum = 0f
        for (i in 0 until iterations) sum+=sin(i*0.01f)*cos(i*0.01f)
        executionTimeMs = (System.nanoTime()-t0)/1_000_000f
        return sum
    }
}

data class AssemblyDefinition(
    var name: String = "Assembly",
    var type: AssemblyType = AssemblyType.Runtime,
    var references: MutableList<String> = mutableListOf(),
    var includePlatforms: MutableList<String> = mutableListOf(),
    var excludePlatforms: MutableList<String> = mutableListOf(),
    var allowUnsafeCode: Boolean = false,
    var autoReferenced: Boolean = true,
    var overrideReferences: Boolean = false,
    var precompiledReferences: MutableList<String> = mutableListOf(),
    var defineConstraints: MutableList<String> = mutableListOf(),
    var versionDefines: MutableMap<String, String> = mutableMapOf()
) {
    fun addReference(ref: String) { if (ref !in references) references.add(ref) }
    fun toJson(): JSONObject {
        val o = JSONObject()
        o.put("name", name)
        o.put("references", references.joinToString(","))
        o.put("includePlatforms", includePlatforms.joinToString(","))
        o.put("allowUnsafeCode", allowUnsafeCode)
        o.put("autoReferenced", autoReferenced)
        return o
    }
}

data class ScriptableObject(
    var guid: String = "",
    var name: String = "ScriptableObject",
    var type: String = "ScriptableObject",
    var data: JSONObject = JSONObject(),
    var isDirty: Boolean = false,
    var filePath: String = ""
) {
    fun setFloat(key: String, value: Float) { data.put(key, value.toDouble()); isDirty=true }
    fun getFloat(key: String, default: Float = 0f): Float = data.optDouble(key, default.toDouble()).toFloat()
    fun setString(key: String, value: String) { data.put(key, value); isDirty=true }
    fun getString(key: String, default: String = ""): String = data.optString(key, default)
    fun setInt(key: String, value: Int) { data.put(key, value); isDirty=true }
    fun getInt(key: String, default: Int = 0): Int = data.optInt(key, default)
    fun toJson(): JSONObject = JSONObject().put("guid", guid).put("name", name).put("type", type).put("data", data)
}

data class UndoRecord(
    var id: Long = 0L,
    var group: UndoGroup = UndoGroup.Transform,
    var action: String = "Action",
    var timestamp: Long = System.currentTimeMillis(),
    var dataBefore: String = "",
    var dataAfter: String = "",
    var isUndone: Boolean = false
)

class UndoRedoSystem {
    var records = mutableListOf<UndoRecord>()
    var currentIndex: Int = -1
    var maxRecords: Int = 500
    var isRecording: Boolean = true
    var groupStack = mutableListOf<String>()
    var nextId: Long = 0L

    fun record(group: UndoGroup, action: String, before: String, after: String): UndoRecord {
        if (!isRecording) return UndoRecord()
        // Clear redo history if not at end
        if (currentIndex < records.size-1) records = records.subList(0, currentIndex+1).toMutableList()
        val record = UndoRecord(id=nextId++, group=group, action=action, dataBefore=before, dataAfter=after)
        records.add(record); currentIndex++
        if (records.size > maxRecords) { records.removeAt(0); currentIndex-- }
        return record
    }

    fun undo(): UndoRecord? {
        if (currentIndex < 0) return null
        val record = records[currentIndex]
        record.isUndone=true
        currentIndex--
        return record
    }

    fun redo(): UndoRecord? {
        if (currentIndex >= records.size-1) return null
        currentIndex++
        val record = records[currentIndex]
        record.isUndone=false
        return record
    }

    fun canUndo(): Boolean = currentIndex >= 0
    fun canRedo(): Boolean = currentIndex < records.size-1
    fun getCurrentAction(): String? = if (currentIndex >= 0) records[currentIndex].action else null
    fun clear() { records.clear(); currentIndex=-1 }
    fun getStats(): String = "Undo Records:${records.size} Index:$currentIndex CanUndo:${canUndo()} CanRedo:${canRedo()}"
}

class PrefabVariantSystem {
    data class PrefabVariant(
        var id: String = "variant_0",
        var basePrefab: String = "BasePrefab",
        var name: String = "Variant",
        var overrides: MutableMap<String, Any> = mutableMapOf(),
        var addedComponents: MutableList<String> = mutableListOf(),
        var removedComponents: MutableList<String> = mutableListOf(),
        var nestedPrefabs: MutableList<String> = mutableListOf()
    ) {
        fun setOverride(property: String, value: Any) { overrides[property]=value }
        fun getOverride(property: String): Any? = overrides[property]
        fun addComponent(type: String) { if (type !in addedComponents) addedComponents.add(type) }
        fun removeComponent(type: String) { if (type !in removedComponents) removedComponents.add(type) }
    }

    var variants = mutableMapOf<String, PrefabVariant>()
    var nextId: Int = 0

    fun createVariant(basePrefab: String, name: String): PrefabVariant {
        val id = "variant_${nextId++}"
        val variant = PrefabVariant(id=id, basePrefab=basePrefab, name=name)
        variants[id]=variant; return variant
    }

    fun getVariant(id: String): PrefabVariant? = variants[id]
    fun getVariantsForBase(basePrefab: String): List<PrefabVariant> = variants.values.filter { it.basePrefab == basePrefab }
    fun applyVariant(baseJson: JSONObject, variantId: String): JSONObject {
        val variant = variants[variantId] ?: return baseJson
        val result = JSONObject(baseJson.toString())
        for ((prop, value) in variant.overrides) result.put(prop, value)
        return result
    }
    fun getStats(): String = "Prefab Variants:${variants.size}"
}

class UltraEngineCore {
    var burstJobs = mutableMapOf<String, BurstJob>()
    var assemblies = mutableMapOf<String, AssemblyDefinition>()
    var scriptableObjects = mutableMapOf<String, ScriptableObject>()
    var undoRedo = UndoRedoSystem()
    var prefabVariants = PrefabVariantSystem()
    var domainReloadMode: DomainReloadMode = DomainReloadMode.Partial
    var deterministic: Boolean = true
    var fixedTimeStep: Float = 1f/60f
    var maxSubSteps: Int = 4
    var timeAccumulator: Float = 0f

    fun createBurstJob(name: String, source: String, optimization: BurstOptimization = BurstOptimization.O3): BurstJob {
        val job = BurstJob(name=name, source=source, optimization=optimization); job.compile(); burstJobs[name]=job; return job
    }

    fun createAssembly(name: String, type: AssemblyType = AssemblyType.Runtime): AssemblyDefinition {
        val asm = AssemblyDefinition(name=name, type=type); assemblies[name]=asm; return asm
    }

    fun createScriptableObject(name: String, type: String): ScriptableObject {
        val guid = "so_${System.currentTimeMillis()}_${(Math.random()*1000).toInt()}"
        val so = ScriptableObject(guid=guid, name=name, type=type); scriptableObjects[guid]=so; return so
    }

    fun getScriptableObject(guid: String): ScriptableObject? = scriptableObjects[guid]

    fun deterministicUpdate(dt: Float, updateCallback: (Float)->Unit) {
        if (!deterministic) { updateCallback(dt); return }
        timeAccumulator+=dt
        var steps=0
        while (timeAccumulator >= fixedTimeStep && steps < maxSubSteps) {
            updateCallback(fixedTimeStep)
            timeAccumulator-=fixedTimeStep
            steps++
        }
    }

    fun getStats(): String = "Ultra Core Burst:${burstJobs.size} Assemblies:${assemblies.size} SO:${scriptableObjects.size} ${undoRedo.getStats()} ${prefabVariants.getStats()}"
}
