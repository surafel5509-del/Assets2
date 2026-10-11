package com.sengine.engine.anim.`2d`

import kotlin.math.*

/**
 * S Engine Ultimate 6.1.1 [Titan] - Unity 2D Animation System V2
 * Sprite Skinning, Bones, IK, Sprite Library, Sprite Resolver, 2D Animation Package
 */
enum class SpriteSkinningMode { Bone, Freeform }
enum class SpriteLibraryAssetType { Category, Label }

data class Bone2D(
    var name: String = "Bone",
    var parent: String = "",
    var x: Float = 0f, var y: Float = 0f,
    var rotation: Float = 0f,
    var length: Float = 1f,
    var scaleX: Float = 1f, var scaleY: Float = 1f,
    var depth: Int = 0,
    var color: Int = 0xFF00FF00.toInt(),
    var isVisible: Boolean = true
) {
    fun getEndPosition(): FloatArray {
        val rad = rotation*PI.toFloat()/180f
        return floatArrayOf(x+cos(rad)*length, y+sin(rad)*length)
    }
    fun getWorldPosition(parentX: Float, parentY: Float, parentRot: Float): FloatArray {
        val rad = parentRot*PI.toFloat()/180f
        val wx = parentX + x*cos(rad) - y*sin(rad)
        val wy = parentY + x*sin(rad) + y*cos(rad)
        return floatArrayOf(wx, wy)
    }
}

data class SpriteSkin(
    var spriteName: String = "Sprite",
    var bones: MutableList<Bone2D> = mutableListOf(),
    var weights: MutableMap<String, FloatArray> = mutableMapOf(), // vertex index -> weights per bone
    var bindPoses: MutableList<FloatArray> = mutableListOf(), // 4x4 matrix per bone
    var vertices: MutableList<FloatArray> = mutableListOf(), // x,y per vertex
    var indices: MutableList<Int> = mutableListOf(),
    var mode: SpriteSkinningMode = SpriteSkinningMode.Bone
) {
    fun addBone(bone: Bone2D) { bones.add(bone) }
    fun setWeight(vertexIndex: Int, boneWeights: FloatArray) { weights[vertexIndex.toString()]=boneWeights }
    fun deformVertices(): List<FloatArray> {
        // Simple linear blend skinning
        val deformed = mutableListOf<FloatArray>()
        for (i in vertices.indices) {
            val vert = vertices[i]
            val boneWeights = weights[i.toString()] ?: floatArrayOf(1f)
            var dx=0f; var dy=0f
            for (b in bones.indices) {
                if (b >= boneWeights.size) break
                val w = boneWeights[b]
                if (w <= 0f) continue
                val bone = bones[b]
                dx+=bone.x*w; dy+=bone.y*w
            }
            deformed.add(floatArrayOf(vert[0]+dx, vert[1]+dy))
        }
        return deformed
    }
}

data class SpriteLibraryAsset(
    var name: String = "Library",
    var categories: MutableMap<String, MutableMap<String, String>> = mutableMapOf() // category -> label -> sprite
) {
    fun addCategory(category: String) { categories.getOrPut(category){mutableMapOf()} }
    fun addEntry(category: String, label: String, sprite: String) {
        categories.getOrPut(category){mutableMapOf()}[label]=sprite
    }
    fun getSprite(category: String, label: String): String? = categories[category]?.get(label)
    fun getCategories(): List<String> = categories.keys.toList()
    fun getLabels(category: String): List<String> = categories[category]?.keys?.toList() ?: emptyList()
}

data class SpriteResolver(
    var library: String = "",
    var category: String = "",
    var label: String = "",
    var sprite: String = ""
) {
    fun resolve(libraryAsset: SpriteLibraryAsset?): String {
        if (libraryAsset == null) return sprite
        return libraryAsset.getSprite(category, label) ?: sprite
    }
}

data class IK2D(
    var name: String = "IK",
    var targetX: Float = 0f, var targetY: Float = 0f,
    var chain: MutableList<String> = mutableListOf(), // bone names
    var tolerance: Float = 0.01f,
    var maxIterations: Int = 10,
    var weight: Float = 1f
) {
    fun solve(bones: MutableMap<String, Bone2D>) {
        if (chain.size < 2) return
        // CCD IK for 2D
        repeat(maxIterations) {
            for (i in chain.size-2 downTo 0) {
                val boneName = chain[i]
                val bone = bones[boneName] ?: continue
                val endBone = bones[chain.last()] ?: continue
                val endX = endBone.x; val endY = endBone.y
                val dx1 = endX-bone.x; val dy1 = endY-bone.y
                val dx2 = targetX-bone.x; val dy2 = targetY-bone.y
                val angle1 = atan2(dy1, dx1); val angle2 = atan2(dy2, dx2)
                var deltaAngle = angle2-angle1
                deltaAngle = (deltaAngle+PI.toFloat()) % (2*PI.toFloat()) - PI.toFloat()
                bone.rotation+=deltaAngle*180f/PI.toFloat()*weight
                // Update child positions
                updateChain(bones)
            }
            val endBone = bones[chain.last()] ?: return
            val dx = endBone.x-targetX; val dy = endBone.y-targetY
            if (sqrt(dx*dx+dy*dy) < tolerance) return
        }
    }
    private fun updateChain(bones: MutableMap<String, Bone2D>) {
        for (i in 1 until chain.size) {
            val parent = bones[chain[i-1]] ?: continue
            val child = bones[chain[i]] ?: continue
            val end = parent.getEndPosition()
            child.x = end[0]; child.y = end[1]
        }
    }
}

class SpriteAnimationSystem {
    var skins = mutableMapOf<String, SpriteSkin>()
    var bones = mutableMapOf<String, Bone2D>()
    var libraries = mutableMapOf<String, SpriteLibraryAsset>()
    var resolvers = mutableMapOf<String, SpriteResolver>()
    var iks = mutableMapOf<String, IK2D>()
    var nextBoneId: Int = 0

    fun createBone(name: String, parent: String = "", x: Float = 0f, y: Float = 0f, length: Float = 1f): Bone2D {
        val bone = Bone2D(name=name, parent=parent, x=x, y=y, length=length); bones[name]=bone; return bone
    }

    fun createSkin(spriteName: String): SpriteSkin {
        val skin = SpriteSkin(spriteName=spriteName); skins[spriteName]=skin; return skin
    }

    fun createLibrary(name: String): SpriteLibraryAsset {
        val lib = SpriteLibraryAsset(name=name); libraries[name]=lib; return lib
    }

    fun createResolver(library: String, category: String, label: String): SpriteResolver {
        val resolver = SpriteResolver(library=library, category=category, label=label)
        resolvers["${library}_${category}_${label}"]=resolver; return resolver
    }

    fun createIK(name: String, targetX: Float, targetY: Float, chain: List<String>): IK2D {
        val ik = IK2D(name=name, targetX=targetX, targetY=targetY, chain=chain.toMutableList()); iks[name]=ik; return ik
    }

    fun createHumanoid2D(name: String, x: Float, y: Float): List<Bone2D> {
        val created = mutableListOf<Bone2D>()
        created.add(createBone("${name}_Root", "", x, y, 0.5f))
        created.add(createBone("${name}_Spine", "${name}_Root", x, y+0.5f, 0.5f))
        created.add(createBone("${name}_Head", "${name}_Spine", x, y+1f, 0.3f))
        created.add(createBone("${name}_LeftArm", "${name}_Spine", x-0.3f, y+0.8f, 0.4f))
        created.add(createBone("${name}_LeftForeArm", "${name}_LeftArm", x-0.6f, y+0.8f, 0.4f))
        created.add(createBone("${name}_RightArm", "${name}_Spine", x+0.3f, y+0.8f, 0.4f))
        created.add(createBone("${name}_RightForeArm", "${name}_RightArm", x+0.6f, y+0.8f, 0.4f))
        created.add(createBone("${name}_LeftLeg", "${name}_Root", x-0.2f, y-0.2f, 0.5f))
        created.add(createBone("${name}_LeftShin", "${name}_LeftLeg", x-0.2f, y-0.6f, 0.5f))
        created.add(createBone("${name}_RightLeg", "${name}_Root", x+0.2f, y-0.2f, 0.5f))
        created.add(createBone("${name}_RightShin", "${name}_RightLeg", x+0.2f, y-0.6f, 0.5f))
        return created
    }

    fun solveAllIK() { for (ik in iks.values) ik.solve(bones) }

    fun update(dt: Float) { solveAllIK() }

    fun getStats(): String = "2D Animation Bones:${bones.size} Skins:${skins.size} Libraries:${libraries.size} IKs:${iks.size}"
}
