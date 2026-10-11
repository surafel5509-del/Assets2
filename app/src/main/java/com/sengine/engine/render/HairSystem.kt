package com.sengine.engine.render

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Hair & Fur System
 * Strand-based hair, grooming, physics, shading
 */
enum class HairType { Straight, Wavy, Curly, Coily, Fur }
enum class HairSolver { Verlet, PBD, MassSpring }

data class HairStrand(
    var points: MutableList<FloatArray> = mutableListOf(), // x,y,z per point
    var restPoints: MutableList<FloatArray> = mutableListOf(),
    var velocities: MutableList<FloatArray> = mutableListOf(),
    var thickness: Float = 0.01f,
    var length: Float = 0.3f,
    var curl: Float = 0f,
    var clump: Float = 0f
) {
    fun initialize(rootX: Float, rootY: Float, rootZ: Float, length: Float, segments: Int, curl: Float) {
        points.clear(); restPoints.clear(); velocities.clear()
        this.length = length; this.curl = curl
        for (i in 0..segments) {
            val t = i.toFloat()/segments
            val cx = rootX + sin(t*PI.toFloat()*curl)*0.02f*t
            val cy = rootY - t*length
            val cz = rootZ + cos(t*PI.toFloat()*curl)*0.02f*t
            points.add(floatArrayOf(cx,cy,cz))
            restPoints.add(floatArrayOf(cx,cy,cz))
            velocities.add(floatArrayOf(0f,0f,0f))
        }
    }
    fun update(dt: Float, gravity: Float, windX: Float, windY: Float, windZ: Float, stiffness: Float) {
        if (points.isEmpty()) return
        // Keep root fixed
        for (i in 1 until points.size) {
            val p = points[i]; val rp = restPoints[i]; val v = velocities[i]
            // Spring to rest
            val dx = rp[0]-p[0]; val dy = rp[1]-p[1]; val dz = rp[2]-p[2]
            v[0]+=dx*stiffness*dt; v[1]+=dy*stiffness*dt; v[2]+=dz*stiffness*dt
            // Gravity
            v[1]+=gravity*dt
            // Wind
            v[0]+=windX*dt; v[1]+=windY*dt; v[2]+=windZ*dt
            // Damping
            v[0]*=0.99f; v[1]*=0.99f; v[2]*=0.99f
            // Integrate
            p[0]+=v[0]*dt; p[1]+=v[1]*dt; p[2]+=v[2]*dt
        }
        // Distance constraints
        for (i in 1 until points.size) {
            val prev = points[i-1]; val curr = points[i]
            val dx = curr[0]-prev[0]; val dy = curr[1]-prev[1]; val dz = curr[2]-prev[2]
            val dist = sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(0.0001f)
            val restDist = length/ (points.size-1)
            val diff = (dist-restDist)/dist
            if (i > 1) { // don't move root
                curr[0]-=dx*diff*0.5f; curr[1]-=dy*diff*0.5f; curr[2]-=dz*diff*0.5f
                prev[0]+=dx*diff*0.5f; prev[1]+=dy*diff*0.5f; prev[2]+=dz*diff*0.5f
            } else {
                curr[0]-=dx*diff; curr[1]-=dy*diff; curr[2]-=dz*diff
            }
        }
    }
}

data class HairGroup(
    var name: String = "Hair",
    var type: HairType = HairType.Straight,
    var strands: MutableList<HairStrand> = mutableListOf(),
    var rootPositions: MutableList<FloatArray> = mutableListOf(),
    var color: Int = 0xFF8D6E63.toInt(),
    var specular: Float = 0.5f,
    var roughness: Float = 0.3f,
    var thickness: Float = 0.005f,
    var density: Int = 1000,
    var length: Float = 0.3f,
    var stiffness: Float = 50f,
    var gravity: Float = -1f,
    var clumpSize: Float = 0.1f,
    var clumpStrength: Float = 0.5f
) {
    fun generateRoots(headCenterX: Float, headCenterY: Float, headCenterZ: Float, headRadius: Float, count: Int) {
        rootPositions.clear(); strands.clear()
        for (i in 0 until count) {
            val theta = Math.random().toFloat()*2f*PI.toFloat()
            val phi = Math.random().toFloat()*PI.toFloat()*0.6f // top hemisphere
            val r = headRadius
            val x = headCenterX + r*sin(phi)*cos(theta)
            val y = headCenterY + r*cos(phi)
            val z = headCenterZ + r*sin(phi)*sin(theta)
            rootPositions.add(floatArrayOf(x,y,z))
            val strand = HairStrand(thickness=thickness, length=length+Math.random().toFloat()*0.1f)
            strand.initialize(x,y,z, length, 8, if (type==HairType.Curly) 3f else if (type==HairType.Wavy) 1f else 0f)
            strands.add(strand)
        }
    }
    fun update(dt: Float, windX: Float, windY: Float, windZ: Float) {
        for (strand in strands) strand.update(dt, gravity, windX, windY, windZ, stiffness)
    }
}

class HairSystem {
    var groups = mutableMapOf<String, HairGroup>()
    var enabled: Boolean = true
    var solver: HairSolver = HairSolver.Verlet
    var lodDistance: Float = 10f
    var windX: Float = 0f; var windY: Float = 0f; var windZ: Float = 0f
    var time: Float = 0f

    fun createHairGroup(name: String, type: HairType = HairType.Straight, density: Int = 1000): HairGroup {
        val g = HairGroup(name=name, type=type, density=density); groups[name]=g; return g
    }

    fun generateHeadHair(groupName: String, headX: Float, headY: Float, headZ: Float, radius: Float = 0.3f) {
        val group = groups[groupName] ?: return
        group.generateRoots(headX, headY, headZ, radius, group.density)
    }

    fun update(dt: Float) {
        if (!enabled) return
        time+=dt
        // Animate wind
        windX = sin(time*0.5f)*0.5f; windZ = cos(time*0.3f)*0.3f
        for (group in groups.values) group.update(dt, windX, windY, windZ)
    }

    fun getVisibleStrands(cameraX: Float, cameraY: Float, cameraZ: Float): List<HairStrand> {
        return groups.values.flatMap { group ->
            group.strands.filter { strand ->
                if (strand.points.isEmpty()) false else {
                    val root = strand.points[0]
                    val dx = root[0]-cameraX; val dy = root[1]-cameraY; val dz = root[2]-cameraZ
                    sqrt(dx*dx+dy*dy+dz*dz) < lodDistance
                }
            }
        }
    }

    fun getStats(): String = "Hair Groups:${groups.size} Strands:${groups.values.sumOf { it.strands.size }}"
}
