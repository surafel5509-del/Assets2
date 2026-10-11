package com.sengine.engine.render

/**
 * Ultimate Edition Shader Graph - Unity Shader Graph + Unreal Material Editor equivalent
 * Node-based shader authoring that compiles to GLSL
 */

enum class ShaderGraphNodeType {
    Master,
    TextureSample,
    Color,
    Float,
    Vector2,
    Vector3,
    Vector4,
    Add,
    Multiply,
    Subtract,
    Divide,
    Lerp,
    Time,
    UV,
    Normal,
    ViewDirection,
    WorldPosition,
    Fresnel,
    Panner,
    Rotator,
    Saturate,
    OneMinus,
    Power,
    Clamp,
    Remap,
    Noise,
    Voronoi,
    Gradient,
    Step,
    Smoothstep,
    CustomFunction
}

data class ShaderGraphNode(
    var id: String = java.util.UUID.randomUUID().toString(),
    var type: ShaderGraphNodeType = ShaderGraphNodeType.Float,
    var position: FloatArray = floatArrayOf(0f, 0f),
    var properties: MutableMap<String, Any> = mutableMapOf(),
    var inputs: MutableMap<String, String> = mutableMapOf(), // port -> connected node output
    var outputs: MutableMap<String, Any> = mutableMapOf()
)

data class ShaderGraphConnection(var fromNode: String, var fromPort: String, var toNode: String, var toPort: String)

class ShaderGraph(var name: String = "New Shader Graph") {
    var nodes = mutableListOf<ShaderGraphNode>()
    var connections = mutableListOf<ShaderGraphConnection>()
    var target: Int = 0 // 0 URP, 1 HDRP, 2 Built-in, 3 Custom
    var surfaceType: Int = 0 // 0 opaque, 1 transparent
    var blendMode: Int = 0
    var cullMode: Int = 2
    var properties = mutableListOf<ShaderProperty>()

    data class ShaderProperty(var name: String, var displayName: String, var type: Int, var defaultValue: Any, var exposed: Boolean = true)

    fun addNode(type: ShaderGraphNodeType, x: Float = 0f, y: Float = 0f): ShaderGraphNode {
        val node = ShaderGraphNode(type = type, position = floatArrayOf(x, y))
        nodes.add(node)
        return node
    }

    fun connect(from: ShaderGraphNode, fromPort: String, to: ShaderGraphNode, toPort: String) {
        connections.add(ShaderGraphConnection(from.id, fromPort, to.id, toPort))
        to.inputs[toPort] = "${from.id}:$fromPort"
    }

    fun findNode(id: String): ShaderGraphNode? = nodes.firstOrNull { it.id == id }

    fun compileToGLSL(): String {
        val sb = StringBuilder()
        sb.appendLine("// Generated from Shader Graph: $name")
        sb.appendLine("// Target: $target | Surface: $surfaceType")
        sb.appendLine("precision mediump float;")
        sb.appendLine("varying vec2 vUV;")
        sb.appendLine("varying vec3 vWorld;")
        sb.appendLine("varying vec3 vNormal;")
        sb.appendLine("uniform vec4 uColor;")
        sb.appendLine("uniform sampler2D uTex;")
        sb.appendLine("uniform float uTime;")
        sb.appendLine("uniform float uParam;")
        sb.appendLine("uniform vec2 uResolution;")

        // Properties as uniforms
        for (prop in properties) {
            when (prop.type) {
                0 -> sb.appendLine("uniform float ${prop.name}; // ${prop.displayName}")
                1 -> sb.appendLine("uniform vec2 ${prop.name};")
                2 -> sb.appendLine("uniform vec3 ${prop.name};")
                3 -> sb.appendLine("uniform vec4 ${prop.name};")
                4 -> sb.appendLine("uniform sampler2D ${prop.name};")
            }
        }

        // Topological sort for nodes
        val sorted = topologicalSort()

        sb.appendLine("vec4 effect(vec4 color, vec2 uv) {")

        // Generate code for each node
        for (node in sorted) {
            if (node.type == ShaderGraphNodeType.Master) continue
            val code = generateNodeCode(node)
            if (code.isNotBlank()) sb.appendLine("  $code")
        }

        // Master node - final output
        val master = nodes.firstOrNull { it.type == ShaderGraphNodeType.Master }
        if (master != null) {
            val baseColorInput = master.inputs["BaseColor"] ?: master.inputs["Color"]
            if (baseColorInput != null) {
                val varName = baseColorInput.replace(":", "_")
                sb.appendLine("  vec4 finalColor = vec4($varName, 1.0);")
            } else {
                sb.appendLine("  vec4 finalColor = color;")
            }
            sb.appendLine("  return finalColor;")
        } else {
            sb.appendLine("  return color;")
        }

        sb.appendLine("}")
        return sb.toString()
    }

    private fun topologicalSort(): List<ShaderGraphNode> {
        val visited = mutableSetOf<String>()
        val result = mutableListOf<ShaderGraphNode>()

        fun visit(node: ShaderGraphNode) {
            if (node.id in visited) return
            visited.add(node.id)
            for ((_, connected) in node.inputs) {
                val depId = connected.substringBefore(":")
                findNode(depId)?.let { visit(it) }
            }
            result.add(node)
        }

        for (node in nodes) visit(node)
        return result
    }

    private fun generateNodeCode(node: ShaderGraphNode): String {
        val outVar = "${node.id.replace("-", "_")}_out"
        return when (node.type) {
            ShaderGraphNodeType.Float -> {
                val v = node.properties["value"] as? Float ?: 0f
                "float $outVar = $v;"
            }
            ShaderGraphNodeType.Color -> {
                val c = node.properties["color"] as? Int ?: 0xFFFFFFFF.toInt()
                val r = ((c shr 16) and 0xFF) / 255f
                val g = ((c shr 8) and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                val a = ((c shr 24) and 0xFF) / 255f
                "vec4 $outVar = vec4($r, $g, $b, $a);"
            }
            ShaderGraphNodeType.Time -> "float ${outVar} = uTime;"
            ShaderGraphNodeType.UV -> "vec2 ${outVar} = vUV;"
            ShaderGraphNodeType.Add -> {
                val a = getInputVar(node, "A", "0.0")
                val b = getInputVar(node, "B", "0.0")
                "float $outVar = $a + $b;"
            }
            ShaderGraphNodeType.Multiply -> {
                val a = getInputVar(node, "A", "1.0")
                val b = getInputVar(node, "B", "1.0")
                "vec4 $outVar = $a * $b;"
            }
            ShaderGraphNodeType.Lerp -> {
                val a = getInputVar(node, "A", "0.0")
                val b = getInputVar(node, "B", "1.0")
                val t = getInputVar(node, "T", "0.5")
                "vec4 $outVar = mix($a, $b, $t);"
            }
            ShaderGraphNodeType.Panner -> {
                val uv = getInputVar(node, "UV", "vUV")
                val speed = getInputVar(node, "Speed", "vec2(1.0)")
                "vec2 $outVar = $uv + $speed * uTime;"
            }
            ShaderGraphNodeType.Fresnel -> {
                val normal = getInputVar(node, "Normal", "vNormal")
                val viewDir = getInputVar(node, "ViewDir", "vec3(0.0, 0.0, 1.0)")
                val power = node.properties["power"] as? Float ?: 1f
                "float $outVar = pow(1.0 - max(dot(normalize($normal), normalize($viewDir)), 0.0), $power);"
            }
            else -> "// ${node.type} node ${node.id}"
        }
    }

    private fun getInputVar(node: ShaderGraphNode, port: String, default: String): String {
        val connected = node.inputs[port] ?: return default
        return connected.replace(":", "_") + "_out"
    }

    fun toJson(): org.json.JSONObject {
        val o = org.json.JSONObject()
        o.put("name", name)
        o.put("target", target)
        o.put("surfaceType", surfaceType)
        val nodesArr = org.json.JSONArray()
        for (n in nodes) {
            val no = org.json.JSONObject()
            no.put("id", n.id)
            no.put("type", n.type.name)
            no.put("x", n.position[0].toDouble())
            no.put("y", n.position[1].toDouble())
            val props = org.json.JSONObject()
            for ((k, v) in n.properties) props.put(k, v.toString())
            no.put("properties", props)
            nodesArr.put(no)
        }
        o.put("nodes", nodesArr)
        val connArr = org.json.JSONArray()
        for (c in connections) {
            val co = org.json.JSONObject()
            co.put("from", c.fromNode); co.put("fromPort", c.fromPort)
            co.put("to", c.toNode); co.put("toPort", c.toPort)
            connArr.put(co)
        }
        o.put("connections", connArr)
        return o
    }
}
