package com.sengine.engine.world

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Spline System
 * Roads, Rivers, Paths, Rails, Procedural Meshes along Spline
 */
enum class SplineType { Linear, Bezier, CatmullRom, BSpline }
enum class SplineWrapMode { Clamp, Loop, PingPong }

data class SplinePoint(
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var inTangentX: Float = 0f, var inTangentY: Float = 0f, var inTangentZ: Float = 0f,
    var outTangentX: Float = 0f, var outTangentY: Float = 0f, var outTangentZ: Float = 0f,
    var upX: Float = 0f, var upY: Float = 1f, var upZ: Float = 0f,
    var scale: Float = 1f,
    var roll: Float = 0f
)

class Spline(
    var name: String = "Spline",
    var type: SplineType = SplineType.CatmullRom,
    var wrapMode: SplineWrapMode = SplineWrapMode.Clamp,
    var points: MutableList<SplinePoint> = mutableListOf(),
    var closed: Boolean = false,
    var length: Float = 0f,
    var cachedDistances: MutableList<Float> = mutableListOf()
) {
    fun addPoint(x: Float, y: Float, z: Float): SplinePoint {
        val p = SplinePoint(x,y,z); points.add(p); recalculateLength(); return p
    }
    fun addPoint(p: SplinePoint) { points.add(p); recalculateLength() }
    fun removePoint(index: Int) { if (index in points.indices) { points.removeAt(index); recalculateLength() } }
    fun clear() { points.clear(); cachedDistances.clear(); length=0f }

    fun recalculateLength() {
        cachedDistances.clear(); length=0f
        if (points.size < 2) return
        cachedDistances.add(0f)
        for (i in 1 until points.size) {
            val dx = points[i].x-points[i-1].x; val dy = points[i].y-points[i-1].y; val dz = points[i].z-points[i-1].z
            length+=sqrt(dx*dx+dy*dy+dz*dz); cachedDistances.add(length)
        }
        if (closed && points.size > 2) {
            val dx = points[0].x-points.last().x; val dy = points[0].y-points.last().y; val dz = points[0].z-points.last().z
            length+=sqrt(dx*dx+dy*dy+dz*dz)
        }
    }

    fun evaluate(t: Float): FloatArray {
        if (points.isEmpty()) return floatArrayOf(0f,0f,0f)
        if (points.size == 1) return floatArrayOf(points[0].x, points[0].y, points[0].z)
        var tt = t.coerceIn(0f,1f)
        if (wrapMode == SplineWrapMode.Loop) tt %= 1f
        val scaledT = tt * (points.size-1)
        val idx = scaledT.toInt().coerceIn(0, points.size-2)
        val frac = scaledT - idx
        return when (type) {
            SplineType.Linear -> lerp(points[idx], points[idx+1], frac)
            SplineType.Bezier -> bezier(points[idx], points[idx+1], frac)
            SplineType.CatmullRom -> catmullRom(idx, frac)
            SplineType.BSpline -> bSpline(idx, frac)
        }
    }

    private fun lerp(a: SplinePoint, b: SplinePoint, t: Float): FloatArray =
        floatArrayOf(a.x+(b.x-a.x)*t, a.y+(b.y-a.y)*t, a.z+(b.z-a.z)*t)

    private fun bezier(a: SplinePoint, b: SplinePoint, t: Float): FloatArray {
        val p0x=a.x; val p0y=a.y; val p0z=a.z
        val p1x=a.x+a.outTangentX; val p1y=a.y+a.outTangentY; val p1z=a.z+a.outTangentZ
        val p2x=b.x+b.inTangentX; val p2y=b.y+b.inTangentY; val p2z=b.z+b.inTangentZ
        val p3x=b.x; val p3y=b.y; val p3z=b.z
        val mt=1f-t; val mt2=mt*mt; val mt3=mt2*mt; val t2=t*t; val t3=t2*t
        return floatArrayOf(
            mt3*p0x + 3*mt2*t*p1x + 3*mt*t2*p2x + t3*p3x,
            mt3*p0y + 3*mt2*t*p1y + 3*mt*t2*p2y + t3*p3y,
            mt3*p0z + 3*mt2*t*p1z + 3*mt*t2*p2z + t3*p3z
        )
    }

    private fun catmullRom(idx: Int, t: Float): FloatArray {
        val p0 = points.getOrElse(idx-1){points[idx]}
        val p1 = points[idx]
        val p2 = points[idx+1]
        val p3 = points.getOrElse(idx+2){points[idx+1]}
        val t2=t*t; val t3=t2*t
        val x = 0.5f*(2*p1.x + (-p0.x+p2.x)*t + (2*p0.x-5*p1.x+4*p2.x-p3.x)*t2 + (-p0.x+3*p1.x-3*p2.x+p3.x)*t3)
        val y = 0.5f*(2*p1.y + (-p0.y+p2.y)*t + (2*p0.y-5*p1.y+4*p2.y-p3.y)*t2 + (-p0.y+3*p1.y-3*p2.y+p3.y)*t3)
        val z = 0.5f*(2*p1.z + (-p0.z+p2.z)*t + (2*p0.z-5*p1.z+4*p2.z-p3.z)*t2 + (-p0.z+3*p1.z-3*p2.z+p3.z)*t3)
        return floatArrayOf(x,y,z)
    }

    private fun bSpline(idx: Int, t: Float): FloatArray {
        // Simplified B-Spline - use Catmull for now
        return catmullRom(idx, t)
    }

    fun evaluateTangent(t: Float): FloatArray {
        val delta = 0.001f
        val p1 = evaluate((t-delta).coerceIn(0f,1f))
        val p2 = evaluate((t+delta).coerceIn(0f,1f))
        val dx = p2[0]-p1[0]; val dy = p2[1]-p1[1]; val dz = p2[2]-p1[2]
        val len = sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(0.0001f)
        return floatArrayOf(dx/len, dy/len, dz/len)
    }

    fun evaluateAtDistance(distance: Float): FloatArray {
        if (length <= 0f) return evaluate(0f)
        val d = distance.coerceIn(0f, length)
        // Find segment
        for (i in 1 until cachedDistances.size) {
            if (d <= cachedDistances[i]) {
                val segStart = cachedDistances[i-1]; val segLen = cachedDistances[i]-segStart
                val frac = if (segLen > 0f) (d-segStart)/segLen else 0f
                val t = (i-1+frac)/(points.size-1)
                return evaluate(t)
            }
        }
        return evaluate(1f)
    }

    fun getClosestPoint(worldX: Float, worldY: Float, worldZ: Float, iterations: Int = 10): Pair<FloatArray, Float> {
        var bestT=0f; var bestDist=Float.MAX_VALUE; var bestPos=floatArrayOf(0f,0f,0f)
        for (i in 0..100) {
            val t = i/100f; val pos = evaluate(t)
            val dx = pos[0]-worldX; val dy = pos[1]-worldY; val dz = pos[2]-worldZ
            val dist = dx*dx+dy*dy+dz*dz
            if (dist < bestDist) { bestDist=dist; bestT=t; bestPos=pos }
        }
        // Refine
        var t = bestT
        var step = 0.01f
        repeat(iterations) {
            val pos = evaluate(t)
            val tan = evaluateTangent(t)
            val dx = worldX-pos[0]; val dy = worldY-pos[1]; val dz = worldZ-pos[2]
            val dot = dx*tan[0]+dy*tan[1]+dz*tan[2]
            t = (t+dot*step).coerceIn(0f,1f)
            bestPos = evaluate(t)
        }
        return Pair(bestPos, t)
    }
}

data class SplineMesh(
    var splineName: String = "",
    var width: Float = 2f,
    var forwardAxis: Int = 2, // 0=x,1=y,2=z
    var upAxis: Int = 1,
    var offsetX: Float = 0f, var offsetY: Float = 0f,
    var segments: Int = 50,
    var meshVertices: MutableList<FloatArray> = mutableListOf(),
    var meshTriangles: MutableList<Int> = mutableListOf()
) {
    fun generate(spline: Spline) {
        meshVertices.clear(); meshTriangles.clear()
        for (i in 0..segments) {
            val t = i.toFloat()/segments
            val pos = spline.evaluate(t)
            val tan = spline.evaluateTangent(t)
            // Compute right vector = cross(tan, up)
            val upX=0f; val upY=1f; val upZ=0f
            var rightX = tan[1]*upZ - tan[2]*upY
            var rightY = tan[2]*upX - tan[0]*upZ
            var rightZ = tan[0]*upY - tan[1]*upX
            val rLen = sqrt(rightX*rightX+rightY*rightY+rightZ*rightZ).coerceAtLeast(0.0001f)
            rightX/=rLen; rightY/=rLen; rightZ/=rLen
            val halfW = width*0.5f
            meshVertices.add(floatArrayOf(pos[0]+rightX*halfW+offsetX, pos[1]+offsetY, pos[2]+rightZ*halfW))
            meshVertices.add(floatArrayOf(pos[0]-rightX*halfW+offsetX, pos[1]+offsetY, pos[2]-rightZ*halfW))
        }
        for (i in 0 until segments) {
            val base = i*2
            meshTriangles.add(base); meshTriangles.add(base+1); meshTriangles.add(base+2)
            meshTriangles.add(base+1); meshTriangles.add(base+3); meshTriangles.add(base+2)
        }
    }
}

class SplineSystem {
    var splines = mutableMapOf<String, Spline>()
    var splineMeshes = mutableMapOf<String, SplineMesh>()

    fun createSpline(name: String, type: SplineType = SplineType.CatmullRom): Spline {
        val s = Spline(name, type); splines[name]=s; return s
    }

    fun getSpline(name: String): Spline? = splines[name]

    fun createRoad(name: String, points: List<FloatArray>): Spline {
        val spline = createSpline(name, SplineType.CatmullRom)
        for (p in points) spline.addPoint(p[0], p[1], p[2])
        spline.closed = false
        val mesh = SplineMesh(splineName=name, width=4f, segments=100)
        mesh.generate(spline)
        splineMeshes[name]=mesh
        return spline
    }

    fun createRiver(name: String, points: List<FloatArray>): Spline {
        val spline = createSpline(name, SplineType.Bezier)
        for (p in points) spline.addPoint(p[0], p[1], p[2])
        val mesh = SplineMesh(splineName=name, width=8f, segments=80)
        mesh.generate(spline)
        splineMeshes[name]=mesh
        return spline
    }

    fun evaluate(name: String, t: Float): FloatArray? = splines[name]?.evaluate(t)
    fun getStats(): String = "Splines:${splines.size} Meshes:${splineMeshes.size}"
}
