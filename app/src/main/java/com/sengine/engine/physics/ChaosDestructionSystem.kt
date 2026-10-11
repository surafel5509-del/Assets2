package com.sengine.engine.physics

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Chaos Destruction System
 * Fracture, Debris, Physics Fields, Clustering, Breaking
 */
enum class FractureMode { Voronoi, Uniform, Clustered, Radial, Slice }
enum class DestructionState { Intact, Damaged, Fractured, Destroyed }

data class FracturePiece(
    var id: Int = 0,
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var sizeX: Float = 1f, var sizeY: Float = 1f, var sizeZ: Float = 1f,
    var mass: Float = 1f,
    var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f,
    var health: Float = 100f,
    var isSleeping: Boolean = true,
    var clusterId: Int = -1
) {
    fun applyForce(fx: Float, fy: Float, fz: Float) { vx+=fx/mass; vy+=fy/mass; vz+=fz/mass; isSleeping=false }
    fun update(dt: Float) {
        if (isSleeping) return
        x+=vx*dt; y+=vy*dt; z+=vz*dt
        vy+=-9.81f*dt // gravity
        vx*=0.99f; vy*=0.99f; vz*=0.99f // damping
        if (y < 0f) { y=0f; vy=-vy*0.3f; vx*=0.8f; vz*=0.8f; if (abs(vy) < 0.1f) isSleeping=true }
    }
}

data class FractureCluster(
    var id: Int = 0,
    var pieces: MutableList<Int> = mutableListOf(),
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var health: Float = 1000f,
    var isBroken: Boolean = false
)

data class PhysicsField(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var radius: Float = 5f,
    var strength: Float = 10f,
    var type: String = "Radial", // Radial, Uniform, Vortex, Strain
    var falloff: Float = 1f,
    var active: Boolean = true
) {
    fun applyTo(piece: FracturePiece) {
        if (!active) return
        val dx = piece.x-x; val dy = piece.y-y; val dz = piece.z-z
        val dist2 = dx*dx+dy*dy+dz*dz; val dist = sqrt(dist2)
        if (dist > radius) return
        val atten = 1f - (dist/radius).pow(falloff)
        when (type) {
            "Radial" -> { val f = strength*atten; if (dist > 0.01f) piece.applyForce(dx/dist*f, dy/dist*f, dz/dist*f) }
            "Uniform" -> piece.applyForce(0f, strength*atten, 0f)
            "Vortex" -> { piece.applyForce(-dz*strength*atten*0.1f, 0f, dx*strength*atten*0.1f) }
        }
    }
}

data class DestructibleObject(
    var id: Long = 0L,
    var name: String = "Destructible",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var sizeX: Float = 2f, var sizeY: Float = 2f, var sizeZ: Float = 2f,
    var health: Float = 100f,
    var maxHealth: Float = 100f,
    var state: DestructionState = DestructionState.Intact,
    var fractureMode: FractureMode = FractureMode.Voronoi,
    var pieceCount: Int = 20,
    var pieces: MutableList<FracturePiece> = mutableListOf(),
    var clusters: MutableList<FractureCluster> = mutableListOf(),
    var damageThreshold: Float = 20f,
    var supportDepth: Int = 1
) {
    fun takeDamage(amount: Float, hitX: Float, hitY: Float, hitZ: Float) {
        health-=amount
        if (health <= 0f && state != DestructionState.Destroyed) fracture(hitX, hitY, hitZ)
        else if (health < maxHealth*0.5f) state = DestructionState.Damaged
    }
    fun fracture(hitX: Float, hitY: Float, hitZ: Float) {
        if (state == DestructionState.Destroyed) return
        state = DestructionState.Fractured
        pieces.clear(); clusters.clear()
        // Generate fracture pieces
        for (i in 0 until pieceCount) {
            val px = x + (Math.random().toFloat()-0.5f)*sizeX
            val py = y + (Math.random().toFloat()-0.5f)*sizeY
            val pz = z + (Math.random().toFloat()-0.5f)*sizeZ
            val sx = sizeX/pieceCount*2f + Math.random().toFloat()*0.5f
            val sy = sizeY/pieceCount*2f + Math.random().toFloat()*0.5f
            val sz = sizeZ/pieceCount*2f + Math.random().toFloat()*0.5f
            val piece = FracturePiece(id=i, x=px, y=py, z=pz, sizeX=sx, sizeY=sy, sizeZ=sz, mass=sx*sy*sz)
            // Explosion from hit point
            val dx = px-hitX; val dy = py-hitY; val dz = pz-hitZ
            val dist = sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(0.1f)
            val force = 20f/dist
            piece.applyForce(dx/dist*force, dy/dist*force+2f, dz/dist*force)
            pieces.add(piece)
        }
        // Create clusters
        val clusterCount = max(1, pieceCount/5)
        for (c in 0 until clusterCount) {
            val cluster = FractureCluster(id=c)
            for (p in pieces.indices) if (p % clusterCount == c) cluster.pieces.add(p)
            if (cluster.pieces.isNotEmpty()) {
                var cx=0f; var cy=0f; var cz=0f
                for (pi in cluster.pieces) { cx+=pieces[pi].x; cy+=pieces[pi].y; cz+=pieces[pi].z }
                cx/=cluster.pieces.size; cy/=cluster.pieces.size; cz/=cluster.pieces.size
                cluster.x=cx; cluster.y=cy; cluster.z=cz
            }
            clusters.add(cluster)
        }
        state = DestructionState.Destroyed
    }
    fun update(dt: Float) { for (p in pieces) p.update(dt) }
}

class ChaosDestructionSystem {
    var destructibles = mutableMapOf<Long, DestructibleObject>()
    var fields = mutableListOf<PhysicsField>()
    var gravity: Float = -9.81f
    var maxPieces: Int = 500
    var sleepThreshold: Float = 0.1f
    var debrisLifetime: Float = 10f

    fun createDestructible(id: Long, name: String, x: Float, y: Float, z: Float, sizeX: Float, sizeY: Float, sizeZ: Float, pieceCount: Int = 20): DestructibleObject {
        val obj = DestructibleObject(id=id, name=name, x=x, y=y, z=z, sizeX=sizeX, sizeY=sizeY, sizeZ=sizeZ, pieceCount=pieceCount)
        destructibles[id]=obj; return obj
    }

    fun applyDamage(id: Long, amount: Float, hitX: Float, hitY: Float, hitZ: Float) {
        destructibles[id]?.takeDamage(amount, hitX, hitY, hitZ)
    }

    fun addField(x: Float, y: Float, z: Float, radius: Float, strength: Float, type: String = "Radial"): PhysicsField {
        val f = PhysicsField(x,y,z,radius,strength,type); fields.add(f); return f
    }

    fun applyFields() {
        for (field in fields.filter { it.active }) for (obj in destructibles.values) for (piece in obj.pieces) field.applyTo(piece)
    }

    fun update(dt: Float) {
        applyFields()
        for (obj in destructibles.values) obj.update(dt)
        // Remove inactive fields
        fields.removeAll { !it.active }
    }

    fun getActivePieceCount(): Int = destructibles.values.sumOf { it.pieces.count { p -> !p.isSleeping } }
    fun getTotalPieceCount(): Int = destructibles.values.sumOf { it.pieces.size }
    fun getStats(): String = "Destructibles:${destructibles.size} Pieces:${getTotalPieceCount()} Active:${getActivePieceCount()} Fields:${fields.size}"
}
