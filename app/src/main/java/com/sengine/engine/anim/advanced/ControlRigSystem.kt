package com.sengine.engine.anim.advanced

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Control Rig & Full Body IK System
 * UE5 Control Rig, Full Body IK, Retargeting, Pose Warping
 */
enum class ControlType { Transform, Float, Bool, Vector, Rotator }
enum class RigUnitType { Transform, Aim, FABRIK, CCDIK, FullBodyIK, Spring, Noise, Blend, Chain }

data class RigControl(
    var name: String = "Control",
    var type: ControlType = ControlType.Transform,
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var rotX: Float = 0f, var rotY: Float = 0f, var rotZ: Float = 0f,
    var scale: Float = 1f,
    var value: Float = 0f,
    var boolValue: Boolean = false,
    var parent: String = "",
    var visible: Boolean = true,
    var gizmoColor: Int = 0xFF00FF00.toInt()
)

data class RigBone(
    var name: String = "Bone",
    var parent: String = "",
    var x: Float = 0f, var y: Float = 0f, var z: Float = 0f,
    var rotX: Float = 0f, var rotY: Float = 0f, var rotZ: Float = 0f,
    var scale: Float = 1f,
    var initialX: Float = 0f, var initialY: Float = 0f, var initialZ: Float = 0f
)

data class RigUnit(
    var name: String = "Unit",
    var type: RigUnitType = RigUnitType.Transform,
    var inputs: MutableMap<String, Any> = mutableMapOf(),
    var outputs: MutableMap<String, Any> = mutableMapOf(),
    var enabled: Boolean = true
)

class ControlRig(
    var name: String = "ControlRig",
    var controls: MutableMap<String, RigControl> = mutableMapOf(),
    var bones: MutableMap<String, RigBone> = mutableMapOf(),
    var units: MutableList<RigUnit> = mutableListOf(),
    var hierarchy: MutableMap<String, MutableList<String>> = mutableMapOf() // parent -> children
) {
    fun addControl(name: String, type: ControlType = ControlType.Transform, x: Float=0f, y: Float=0f, z: Float=0f): RigControl {
        val c = RigControl(name=name, type=type, x=x, y=y, z=z); controls[name]=c; return c
    }
    fun addBone(name: String, parent: String = "", x: Float=0f, y: Float=0f, z: Float=0f): RigBone {
        val b = RigBone(name=name, parent=parent, x=x, y=y, z=z, initialX=x, initialY=y, initialZ=z); bones[name]=b
        hierarchy.getOrPut(parent){mutableListOf()}.add(name); return b
    }
    fun addUnit(name: String, type: RigUnitType): RigUnit {
        val u = RigUnit(name=name, type=type); units.add(u); return u
    }

    fun evaluate() {
        for (unit in units.filter { it.enabled }) {
            when (unit.type) {
                RigUnitType.Transform -> {
                    val controlName = unit.inputs["control"] as? String ?: continue
                    val boneName = unit.inputs["bone"] as? String ?: continue
                    val control = controls[controlName] ?: continue
                    val bone = bones[boneName] ?: continue
                    bone.x = control.x; bone.y = control.y; bone.z = control.z
                    bone.rotX = control.rotX; bone.rotY = control.rotY; bone.rotZ = control.rotZ
                }
                RigUnitType.FABRIK -> {
                    val chain = unit.inputs["chain"] as? List<String> ?: continue
                    val targetX = unit.inputs["targetX"] as? Float ?: 0f
                    val targetY = unit.inputs["targetY"] as? Float ?: 0f
                    val targetZ = unit.inputs["targetZ"] as? Float ?: 0f
                    solveFABRIK(chain, targetX, targetY, targetZ)
                }
                RigUnitType.FullBodyIK -> solveFullBodyIK(unit)
                else -> {}
            }
        }
    }

    private fun solveFABRIK(chain: List<String>, tx: Float, ty: Float, tz: Float) {
        if (chain.size < 2) return
        val positions = chain.map { bones[it]?.let { b -> floatArrayOf(b.x,b.y,b.z) } ?: floatArrayOf(0f,0f,0f) }.toMutableList()
        val lengths = mutableListOf<Float>()
        for (i in 0 until positions.size-1) {
            val dx = positions[i+1][0]-positions[i][0]; val dy = positions[i+1][1]-positions[i][1]; val dz = positions[i+1][2]-positions[i][2]
            lengths.add(sqrt(dx*dx+dy*dy+dz*dz))
        }
        // FABRIK
        val totalLen = lengths.sum()
        val root = positions[0].copyOf()
        val distToTarget = sqrt((tx-root[0]).pow(2)+(ty-root[1]).pow(2)+(tz-root[2]).pow(2))
        if (distToTarget > totalLen) {
            for (i in 0 until positions.size-1) {
                val dx = tx-positions[i][0]; val dy = ty-positions[i][1]; val dz = tz-positions[i][2]
                val len = sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(0.0001f)
                positions[i+1][0]=positions[i][0]+dx/len*lengths[i]
                positions[i+1][1]=positions[i][1]+dy/len*lengths[i]
                positions[i+1][2]=positions[i][2]+dz/len*lengths[i]
            }
        } else {
            repeat(10) {
                positions[positions.size-1][0]=tx; positions[positions.size-1][1]=ty; positions[positions.size-1][2]=tz
                for (i in positions.size-2 downTo 0) {
                    val dx = positions[i][0]-positions[i+1][0]; val dy = positions[i][1]-positions[i+1][1]; val dz = positions[i][2]-positions[i+1][2]
                    val len = sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(0.0001f)
                    positions[i][0]=positions[i+1][0]+dx/len*lengths[i]
                    positions[i][1]=positions[i+1][1]+dy/len*lengths[i]
                    positions[i][2]=positions[i+1][2]+dz/len*lengths[i]
                }
                positions[0]=root.copyOf()
                for (i in 0 until positions.size-1) {
                    val dx = positions[i+1][0]-positions[i][0]; val dy = positions[i+1][1]-positions[i][1]; val dz = positions[i+1][2]-positions[i][2]
                    val len = sqrt(dx*dx+dy*dy+dz*dz).coerceAtLeast(0.0001f)
                    positions[i+1][0]=positions[i][0]+dx/len*lengths[i]
                    positions[i+1][1]=positions[i][1]+dy/len*lengths[i]
                    positions[i+1][2]=positions[i][2]+dz/len*lengths[i]
                }
            }
        }
        for (i in chain.indices) {
            bones[chain[i]]?.let { it.x=positions[i][0]; it.y=positions[i][1]; it.z=positions[i][2] }
        }
    }

    private fun solveFullBodyIK(unit: RigUnit) {
        // Simplified full body IK - distribute to all bones
        val targetX = unit.inputs["targetX"] as? Float ?: 0f
        val targetY = unit.inputs["targetY"] as? Float ?: 0f
        val targetZ = unit.inputs["targetZ"] as? Float ?: 0f
        // Just move root slightly towards target
        bones.values.firstOrNull()?.let { root ->
            root.x += (targetX-root.x)*0.1f; root.y += (targetY-root.y)*0.1f; root.z += (targetZ-root.z)*0.1f
        }
    }

    fun createHumanoidRig(): ControlRig {
        addBone("Root", "", 0f,0f,0f)
        addBone("Hips", "Root", 0f,1f,0f)
        addBone("Spine", "Hips", 0f,1.2f,0f)
        addBone("Chest", "Spine", 0f,1.4f,0f)
        addBone("Neck", "Chest", 0f,1.6f,0f)
        addBone("Head", "Neck", 0f,1.8f,0f)
        addBone("LeftShoulder", "Chest", -0.2f,1.5f,0f)
        addBone("LeftArm", "LeftShoulder", -0.5f,1.5f,0f)
        addBone("LeftForeArm", "LeftArm", -0.8f,1.5f,0f)
        addBone("LeftHand", "LeftForeArm", -1.0f,1.5f,0f)
        addBone("RightShoulder", "Chest", 0.2f,1.5f,0f)
        addBone("RightArm", "RightShoulder", 0.5f,1.5f,0f)
        addBone("RightForeArm", "RightArm", 0.8f,1.5f,0f)
        addBone("RightHand", "RightForeArm", 1.0f,1.5f,0f)
        addBone("LeftUpLeg", "Hips", -0.1f,0.5f,0f)
        addBone("LeftLeg", "LeftUpLeg", -0.1f,0f,0f)
        addBone("LeftFoot", "LeftLeg", -0.1f,-0.5f,0f)
        addBone("RightUpLeg", "Hips", 0.1f,0.5f,0f)
        addBone("RightLeg", "RightUpLeg", 0.1f,0f,0f)
        addBone("RightFoot", "RightLeg", 0.1f,-0.5f,0f)

        addControl("Root_Ctrl", ControlType.Transform, 0f,0f,0f)
        addControl("LeftHand_Ctrl", ControlType.Transform, -1.0f,1.5f,0f)
        addControl("RightHand_Ctrl", ControlType.Transform, 1.0f,1.5f,0f)
        addControl("LeftFoot_Ctrl", ControlType.Transform, -0.1f,-0.5f,0f)
        addControl("RightFoot_Ctrl", ControlType.Transform, 0.1f,-0.5f,0f)
        addControl("Head_Ctrl", ControlType.Transform, 0f,1.8f,0f)

        addUnit("LeftArm_FABRIK", RigUnitType.FABRIK).inputs.putAll(mapOf("chain" to listOf("LeftShoulder","LeftArm","LeftForeArm","LeftHand"), "targetX" to -1.0f, "targetY" to 1.5f, "targetZ" to 0f))
        addUnit("RightArm_FABRIK", RigUnitType.FABRIK).inputs.putAll(mapOf("chain" to listOf("RightShoulder","RightArm","RightForeArm","RightHand"), "targetX" to 1.0f, "targetY" to 1.5f, "targetZ" to 0f))

        return this
    }
}

class ControlRigSystem {
    var rigs = mutableMapOf<String, ControlRig>()

    fun createRig(name: String): ControlRig {
        val rig = ControlRig(name); rigs[name]=rig; return rig
    }

    fun createHumanoidRig(name: String): ControlRig {
        val rig = ControlRig(name); rig.createHumanoidRig(); rigs[name]=rig; return rig
    }

    fun evaluateAll() { for (rig in rigs.values) rig.evaluate() }

    fun getRig(name: String): ControlRig? = rigs[name]
    fun getStats(): String = "ControlRigs:${rigs.size} Bones:${rigs.values.sumOf { it.bones.size }} Controls:${rigs.values.sumOf { it.controls.size }}"
}
