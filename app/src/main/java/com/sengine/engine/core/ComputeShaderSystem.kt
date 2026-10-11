package com.sengine.engine.core

import kotlin.math.*

/**
 * S Engine Ultimate 6.1 [Quantum] - Compute Shader & GPU Systems
 * Compute Shaders, GPU Driven Rendering, Async Compute
 */
enum class ComputeQueue { Graphics, Compute, AsyncCompute }
enum class ComputeBufferType { Structured, Raw, Append, Counter, Constant }

data class ComputeBuffer(
    var name: String = "Buffer",
    var count: Int = 1024,
    var stride: Int = 16,
    var type: ComputeBufferType = ComputeBufferType.Structured,
    var data: FloatArray = FloatArray(count*stride/4),
    var isDirty: Boolean = true
) {
    fun setData(index: Int, values: FloatArray) {
        val offset = index*stride/4
        for (i in values.indices) if (offset+i < data.size) data[offset+i]=values[i]
        isDirty=true
    }
    fun getData(index: Int): FloatArray {
        val offset = index*stride/4
        return FloatArray(stride/4) { i -> data.getOrElse(offset+i){0f} }
    }
    fun clear() { data.fill(0f); isDirty=true }
}

data class ComputeShader(
    var name: String = "ComputeShader",
    var source: String = "",
    var threadGroupSizeX: Int = 8,
    var threadGroupSizeY: Int = 8,
    var threadGroupSizeZ: Int = 1,
    var isCompiled: Boolean = false,
    var compileTimeMs: Float = 0f
) {
    fun compile(): Boolean {
        val t0 = System.nanoTime()
        // Simulate compilation
        isCompiled = source.isNotEmpty()
        compileTimeMs = (System.nanoTime()-t0)/1_000_000f
        return isCompiled
    }
}

data class ComputeDispatch(
    var shaderName: String = "",
    var groupsX: Int = 1, var groupsY: Int = 1, var groupsZ: Int = 1,
    var buffers: MutableList<String> = mutableListOf(),
    var uniforms: MutableMap<String, Float> = mutableMapOf()
)

class ComputeShaderSystem {
    var shaders = mutableMapOf<String, ComputeShader>()
    var buffers = mutableMapOf<String, ComputeBuffer>()
    var dispatchQueue = mutableListOf<ComputeDispatch>()
    var enabled: Boolean = true
    var asyncCompute: Boolean = true
    var totalDispatches: Long = 0L
    var gpuTimeMs: Float = 0f

    fun createBuffer(name: String, count: Int, stride: Int, type: ComputeBufferType = ComputeBufferType.Structured): ComputeBuffer {
        val buf = ComputeBuffer(name=name, count=count, stride=stride, type=type); buffers[name]=buf; return buf
    }

    fun createShader(name: String, source: String, groupX: Int = 8, groupY: Int = 8, groupZ: Int = 1): ComputeShader {
        val shader = ComputeShader(name=name, source=source, threadGroupSizeX=groupX, threadGroupSizeY=groupY, threadGroupSizeZ=groupZ)
        shader.compile(); shaders[name]=shader; return shader
    }

    fun createDefaultShaders() {
        createShader("ClearBuffer", """
            // Clear buffer to zero
            RWStructuredBuffer<float> buffer;
            [numthreads(8,8,1)]
            void main(uint3 id : SV_DispatchThreadID) { buffer[id.x] = 0; }
        """.trimIndent())

        createShader("ParticleUpdate", """
            // GPU Particle Update
            struct Particle { float3 pos; float3 vel; float life; };
            RWStructuredBuffer<Particle> particles;
            cbuffer Params { float dt; float gravity; };
            [numthreads(64,1,1)]
            void main(uint3 id : SV_DispatchThreadID) {
                particles[id.x].vel.y += gravity * dt;
                particles[id.x].pos += particles[id.x].vel * dt;
                particles[id.x].life -= dt;
            }
        """.trimIndent(), 64,1,1)

        createShader("FrustumCulling", """
            // GPU Frustum Culling
            struct Instance { float4x4 matrix; float3 boundsMin; float3 boundsMax; };
            RWStructuredBuffer<Instance> instances;
            RWStructuredBuffer<uint> visible;
            cbuffer Frustum { float4 planes[6]; };
            [numthreads(64,1,1)]
            void main(uint3 id : SV_DispatchThreadID) {
                // Frustum check
                bool visible = true;
                // ... culling logic
                if (visible) visible[id.x] = 1;
            }
        """.trimIndent(), 64,1,1)

        createShader("Skinning", """
            // GPU Skinning
            struct Vertex { float3 pos; float3 normal; float4 weights; uint4 bones; };
            RWStructuredBuffer<Vertex> vertices;
            StructuredBuffer<float4x4> boneMatrices;
            [numthreads(64,1,1)]
            void main(uint3 id : SV_DispatchThreadID) {
                Vertex v = vertices[id.x];
                float4x4 skinMat = boneMatrices[v.bones.x]*v.weights.x + boneMatrices[v.bones.y]*v.weights.y;
                vertices[id.x].pos = mul(skinMat, float4(v.pos,1)).xyz;
            }
        """.trimIndent(), 64,1,1)

        createShader("FluidSPH", """
            // SPH Fluid Simulation
            struct FluidParticle { float3 pos; float3 vel; float density; };
            RWStructuredBuffer<FluidParticle> particles;
            cbuffer Params { float smoothingLength; float restDensity; float gasConstant; };
            [numthreads(64,1,1)]
            void main(uint3 id : SV_DispatchThreadID) {
                // Density and pressure calculation
            }
        """.trimIndent(), 64,1,1)
    }

    fun dispatch(shaderName: String, groupsX: Int, groupsY: Int = 1, groupsZ: Int = 1, uniforms: Map<String, Float> = emptyMap()) {
        if (!enabled) return
        val dispatch = ComputeDispatch(shaderName=shaderName, groupsX=groupsX, groupsY=groupsY, groupsZ=groupsZ, uniforms=uniforms.toMutableMap())
        dispatchQueue.add(dispatch)
    }

    fun executeQueue() {
        if (!enabled || dispatchQueue.isEmpty()) return
        val t0 = System.nanoTime()
        for (dispatch in dispatchQueue) {
            val shader = shaders[dispatch.shaderName] ?: continue
            if (!shader.isCompiled) continue
            // Simulate GPU execution
            val totalThreads = dispatch.groupsX*shader.threadGroupSizeX * dispatch.groupsY*shader.threadGroupSizeY * dispatch.groupsZ*shader.threadGroupSizeZ
            // Execute - for CPU fallback we just mark buffers dirty
            for (bufName in dispatch.buffers) buffers[bufName]?.isDirty=true
            totalDispatches++
        }
        dispatchQueue.clear()
        gpuTimeMs = (System.nanoTime()-t0)/1_000_000f
    }

    fun getStats(): String = "Compute Shaders:${shaders.size} Buffers:${buffers.size} Dispatches:$totalDispatches GPU:${gpuTimeMs}ms"
}
