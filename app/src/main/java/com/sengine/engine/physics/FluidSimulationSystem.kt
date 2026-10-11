package com.sengine.engine.physics

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Fluid Simulation System
 * SPH Fluid, Smoke, Fire, Cloth advanced, Soft Body, Hair
 */
enum class FluidType { Water, Smoke, Fire, Viscous, Gas }
enum class FluidSolver { SPH, PBF, FLIP, Eulerian }

data class FluidParticle(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var vx: Float = 0f, var vy: Float = 0f, var vz: Float = 0f,
    var density: Float = 1000f,
    var pressure: Float = 0f,
    var temperature: Float = 20f,
    var lifetime: Float = 10f,
    var age: Float = 0f,
    var color: Int = 0xFF4FC3F7.toInt()
) {
    fun isAlive(): Boolean = age < lifetime
    fun update(dt: Float, gravity: Float) {
        x+=vx*dt; y+=vy*dt; z+=vz*dt
        vy+=gravity*dt
        vx*=0.999f; vy*=0.999f; vz*=0.999f
        age+=dt
    }
}

data class FluidEmitter(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var dirX: Float = 0f, var dirY: Float = 1f, var dirZ: Float = 0f,
    var rate: Float = 50f,
    var speed: Float = 2f,
    var radius: Float = 0.5f,
    var type: FluidType = FluidType.Water,
    var enabled: Boolean = true,
    var accumulator: Float = 0f
)

class SPHFluidSystem {
    var particles = mutableListOf<FluidParticle>()
    var emitters = mutableListOf<FluidEmitter>()
    var solver: FluidSolver = FluidSolver.SPH
    var gravity: Float = -9.81f
    var restDensity: Float = 1000f
    var gasConstant: Float = 2000f
    var viscosity: Float = 200f
    var surfaceTension: Float = 0.07f
    var particleRadius: Float = 0.1f
    var smoothingLength: Float = 0.2f
    var maxParticles: Int = 4096
    var boundsMin: FloatArray = floatArrayOf(-20f, 0f, -20f)
    var boundsMax: FloatArray = floatArrayOf(20f, 20f, 20f)

    fun addEmitter(x: Float, y: Float, z: Float, type: FluidType = FluidType.Water, rate: Float = 50f): FluidEmitter {
        val e = FluidEmitter(x=x, y=y, z=z, type=type, rate=rate); emitters.add(e); return e
    }

    fun emit(dt: Float) {
        for (emitter in emitters.filter { it.enabled }) {
            emitter.accumulator+=emitter.rate*dt
            while (emitter.accumulator >= 1f && particles.size < maxParticles) {
                emitter.accumulator-=1f
                val rx = (Math.random().toFloat()-0.5f)*emitter.radius
                val rz = (Math.random().toFloat()-0.5f)*emitter.radius
                val p = FluidParticle(
                    x=emitter.x+rx, y=emitter.y, z=emitter.z+rz,
                    vx=emitter.dirX*emitter.speed + (Math.random().toFloat()-0.5f)*0.5f,
                    vy=emitter.dirY*emitter.speed + (Math.random().toFloat()-0.5f)*0.5f,
                    vz=emitter.dirZ*emitter.speed + (Math.random().toFloat()-0.5f)*0.5f,
                    temperature = if (emitter.type == FluidType.Fire) 500f else 20f,
                    color = when (emitter.type) {
                        FluidType.Water -> 0xFF4FC3F7.toInt()
                        FluidType.Smoke -> 0xFF888888.toInt()
                        FluidType.Fire -> 0xFFFF6D00.toInt()
                        FluidType.Viscous -> 0xFF8D6E63.toInt()
                        FluidType.Gas -> 0xFFB0BEC5.toInt()
                    }
                )
                particles.add(p)
            }
        }
    }

    fun computeDensityPressure() {
        // Simplified SPH density
        for (i in particles.indices) {
            var density = 0f
            for (j in particles.indices) {
                val dx = particles[i].x-particles[j].x; val dy = particles[i].y-particles[j].y; val dz = particles[i].z-particles[j].z
                val r2 = dx*dx+dy*dy+dz*dz
                if (r2 < smoothingLength*smoothingLength) {
                    val r = sqrt(r2); val w = (smoothingLength - r).coerceAtLeast(0f)
                    density+=w*w*w
                }
            }
            particles[i].density = density.coerceAtLeast(restDensity*0.1f)
            particles[i].pressure = gasConstant * (particles[i].density - restDensity)
        }
    }

    fun computeForces(dt: Float) {
        for (i in particles.indices) {
            var fx=0f; var fy=0f; var fz=0f
            for (j in particles.indices) if (i!=j) {
                val dx = particles[i].x-particles[j].x; val dy = particles[i].y-particles[j].y; val dz = particles[i].z-particles[j].z
                val r2 = dx*dx+dy*dy+dz*dz
                if (r2 < smoothingLength*smoothingLength && r2 > 0.0001f) {
                    val r = sqrt(r2)
                    // Pressure force
                    val press = -(particles[i].pressure+particles[j].pressure)/(2f*particles[j].density) * (smoothingLength-r)
                    fx+=dx/r*press; fy+=dy/r*press; fz+=dz/r*press
                    // Viscosity
                    fx+=(particles[j].vx-particles[i].vx)*viscosity*0.0001f
                    fy+=(particles[j].vy-particles[i].vy)*viscosity*0.0001f
                    fz+=(particles[j].vz-particles[i].vz)*viscosity*0.0001f
                }
            }
            particles[i].vx+=fx/particles[i].density*dt
            particles[i].vy+=fy/particles[i].density*dt
            particles[i].vz+=fz/particles[i].density*dt
        }
    }

    fun handleCollisions() {
        for (p in particles) {
            if (p.x < boundsMin[0]) { p.x=boundsMin[0]; p.vx=-p.vx*0.5f }
            if (p.x > boundsMax[0]) { p.x=boundsMax[0]; p.vx=-p.vx*0.5f }
            if (p.y < boundsMin[1]) { p.y=boundsMin[1]; p.vy=-p.vy*0.3f; p.vx*=0.9f; p.vz*=0.9f }
            if (p.y > boundsMax[1]) { p.y=boundsMax[1]; p.vy=-p.vy*0.5f }
            if (p.z < boundsMin[2]) { p.z=boundsMin[2]; p.vz=-p.vz*0.5f }
            if (p.z > boundsMax[2]) { p.z=boundsMax[2]; p.vz=-p.vz*0.5f }
        }
    }

    fun update(dt: Float) {
        emit(dt)
        if (solver == FluidSolver.SPH) {
            computeDensityPressure()
            computeForces(dt)
        }
        for (p in particles) p.update(dt, gravity)
        handleCollisions()
        particles.removeAll { !it.isAlive() }
    }

    fun getStats(): String = "Fluid ${solver} Particles:${particles.size}/$maxParticles Emitters:${emitters.size}"
}

// Soft Body System
data class SoftBodyNode(
    var x: Float, var y: Float, var z: Float,
    var ox: Float, var oy: Float, var oz: Float,
    var vx: Float=0f, var vy: Float=0f, var vz: Float=0f,
    var mass: Float=1f, var fixed: Boolean=false
)

data class SoftBodySpring(
    var a: Int, var b: Int,
    var restLength: Float,
    var stiffness: Float=100f,
    var damping: Float=5f
)

class SoftBodySystem {
    var nodes = mutableListOf<SoftBodyNode>()
    var springs = mutableListOf<SoftBodySpring>()
    var gravity: Float = -9.81f
    var damping: Float = 0.99f

    fun createCube(x: Float, y: Float, z: Float, size: Float, segments: Int = 2): Int {
        val startIdx = nodes.size
        val step = size/segments
        for (ix in 0..segments) for (iy in 0..segments) for (iz in 0..segments) {
            val nx = x + ix*step - size*0.5f; val ny = y + iy*step; val nz = z + iz*step - size*0.5f
            nodes.add(SoftBodyNode(nx, ny, nz, nx, ny, nz, fixed = iy==0 && (ix==0 || ix==segments)))
        }
        // Springs
        for (i in startIdx until nodes.size) for (j in i+1 until nodes.size) {
            val dx = nodes[i].x-nodes[j].x; val dy = nodes[i].y-nodes[j].y; val dz = nodes[i].z-nodes[j].z
            val dist = sqrt(dx*dx+dy*dy+dz*dz)
            if (dist < step*1.8f) springs.add(SoftBodySpring(i, j, dist, 200f, 5f))
        }
        return startIdx
    }

    fun update(dt: Float) {
        // Spring forces
        for (spring in springs) {
            val a = nodes[spring.a]; val b = nodes[spring.b]
            val dx = a.x-b.x; val dy = a.y-b.y; val dz = a.z-b.z
            val dist = sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(0.001f)
            val force = (dist-spring.restLength)*spring.stiffness
            val fx = dx/dist*force; val fy = dy/dist*force; val fz = dz/dist*force
            if (!a.fixed) { a.vx-=fx/a.mass*dt; a.vy-=fy/a.mass*dt; a.vz-=fz/a.mass*dt }
            if (!b.fixed) { b.vx+=fx/b.mass*dt; b.vy+=fy/b.mass*dt; b.vz+=fz/b.mass*dt }
        }
        // Integrate
        for (node in nodes) if (!node.fixed) {
            node.vy+=gravity*dt
            node.vx*=damping; node.vy*=damping; node.vz*=damping
            node.x+=node.vx*dt; node.y+=node.vy*dt; node.z+=node.vz*dt
        }
    }
}
