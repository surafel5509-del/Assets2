package com.sengine.engine.ecs

import com.sengine.engine.core.GameObject

/**
 * Ultimate Edition ECS - Data Oriented Technology Stack (DOTS) like Unity
 * High-performance Entity Component System with archetypes and job scheduling
 */

// Entity is just an ID with version
@JvmInline
value class Entity(val id: Long) {
    val index: Int get() = (id and 0xFFFFFF).toInt()
    val version: Int get() = (id shr 24).toInt()
    companion object {
        fun create(index: Int, version: Int): Entity = Entity((version.toLong() shl 24) or (index.toLong() and 0xFFFFFF))
    }
}

interface IComponentData

// Archetype - collection of entities with same component types
class Archetype(val types: Set<Class<out IComponentData>>) {
    val entities = mutableListOf<Entity>()
    val chunks = mutableListOf<Chunk>()

    fun matches(query: Query): Boolean {
        return query.all.all { it in types } &&
               query.none.none { it in types } &&
               (query.any.isEmpty() || query.any.any { it in types })
    }
}

class Chunk(val archetype: Archetype, val capacity: Int = 128) {
    val entities = mutableListOf<Entity>()
    val components = mutableMapOf<Class<out IComponentData>, MutableList<IComponentData>>()
    var count = 0

    fun <T : IComponentData> getComponents(type: Class<T>): MutableList<T> {
        @Suppress("UNCHECKED_CAST")
        return components.getOrPut(type) { mutableListOf() } as MutableList<T>
    }

    fun isFull() = count >= capacity
}

data class Query(
    val all: Set<Class<out IComponentData>> = emptySet(),
    val any: Set<Class<out IComponentData>> = emptySet(),
    val none: Set<Class<out IComponentData>> = emptySet()
)

// System base like Unity's SystemBase
abstract class SystemBase {
    lateinit var world: World
    abstract fun onCreate()
    abstract fun onUpdate(dt: Float)
    open fun onDestroy() {}
}

class World(val name: String = "Default World") {
    private val archetypes = mutableListOf<Archetype>()
    private val systems = mutableListOf<SystemBase>()
    private var nextIndex = 0
    private val freeIndices = mutableListOf<Int>()
    private val versions = mutableMapOf<Int, Int>()

    fun createEntity(vararg components: IComponentData): Entity {
        val index = if (freeIndices.isNotEmpty()) freeIndices.removeAt(freeIndices.size - 1) else nextIndex++
        val version = versions.getOrDefault(index, 0) + 1
        versions[index] = version
        val entity = Entity.create(index, version)

        val types = components.map { it::class.java as Class<out IComponentData> }.toSet()
        val archetype = getOrCreateArchetype(types)
        var chunk = archetype.chunks.lastOrNull { !it.isFull() }
        if (chunk == null) {
            chunk = Chunk(archetype)
            archetype.chunks.add(chunk)
        }
        chunk.entities.add(entity)
        chunk.count++
        for (c in components) {
            chunk.getComponents(c::class.java as Class<IComponentData>).add(c)
        }
        archetype.entities.add(entity)
        return entity
    }

    fun destroyEntity(entity: Entity) {
        for (arch in archetypes) {
            val idx = arch.entities.indexOf(entity)
            if (idx >= 0) {
                arch.entities.removeAt(idx)
                for (chunk in arch.chunks) {
                    val cIdx = chunk.entities.indexOf(entity)
                    if (cIdx >= 0) {
                        chunk.entities.removeAt(cIdx)
                        for (list in chunk.components.values) {
                            if (cIdx < list.size) list.removeAt(cIdx)
                        }
                        chunk.count--
                        break
                    }
                }
                freeIndices.add(entity.index)
                versions[entity.index] = entity.version + 1
                return
            }
        }
    }

    private fun getOrCreateArchetype(types: Set<Class<out IComponentData>>): Archetype {
        return archetypes.firstOrNull { it.types == types } ?: Archetype(types).also { archetypes.add(it) }
    }

    fun query(q: Query): List<Chunk> {
        val result = mutableListOf<Chunk>()
        for (arch in archetypes) if (arch.matches(q)) result.addAll(arch.chunks)
        return result
    }

    fun addSystem(system: SystemBase) {
        system.world = this
        system.onCreate()
        systems.add(system)
    }

    fun update(dt: Float) {
        for (sys in systems) sys.onUpdate(dt)
    }

    fun destroy() {
        for (sys in systems) sys.onDestroy()
        archetypes.clear()
        systems.clear()
    }

    fun entityCount(): Int = archetypes.sumOf { it.entities.size }
}

// Example components for DOTS
data class Translation(var x: Float, var y: Float, var z: Float) : IComponentData
data class Rotation(var x: Float, var y: Float, var z: Float, var w: Float = 1f) : IComponentData
data class Scale(var x: Float, var y: Float, var z: Float) : IComponentData
data class Velocity(var x: Float, var y: Float, var z: Float) : IComponentData
data class PhysicsMass(var mass: Float, var invMass: Float) : IComponentData
data class RenderMesh(var meshId: Int, var materialId: Int) : IComponentData

// Transform System example
class TransformSystem : SystemBase() {
    override fun onCreate() {}
    override fun onUpdate(dt: Float) {
        val chunks = world.query(Query(all = setOf(Translation::class.java, Velocity::class.java)))
        for (chunk in chunks) {
            val translations = chunk.getComponents(Translation::class.java)
            val velocities = chunk.getComponents(Velocity::class.java)
            for (i in 0 until chunk.count) {
                translations[i].x += velocities[i].x * dt
                translations[i].y += velocities[i].y * dt
                translations[i].z += velocities[i].z * dt
            }
        }
    }
}

/**
 * EntityManager bridging GameObject and ECS for hybrid approach (like Unity's GameObject + DOTS)
 */
class EntityManager {
    val world = World("Hybrid World")
    private val goToEntity = mutableMapOf<Long, Entity>()
    private val entityToGo = mutableMapOf<Entity, GameObject>()

    fun createFromGameObject(go: GameObject): Entity {
        val entity = world.createEntity(
            Translation(go.x, go.y, go.z),
            Rotation(go.rotX, go.rotY, go.rotation),
            Scale(go.scaleX, go.scaleY, go.scaleZ)
        )
        goToEntity[go.id] = entity
        entityToGo[entity] = go
        return entity
    }

    fun syncToGameObject() {
        val chunks = world.query(Query(all = setOf(Translation::class.java)))
        for (chunk in chunks) {
            val translations = chunk.getComponents(Translation::class.java)
            for (i in 0 until chunk.count) {
                val entity = chunk.entities[i]
                val go = entityToGo[entity] ?: continue
                go.x = translations[i].x
                go.y = translations[i].y
                go.z = translations[i].z
            }
        }
    }

    fun update(dt: Float) {
        world.update(dt)
        syncToGameObject()
    }
}
