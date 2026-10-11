package com.sengine.engine.input

import kotlin.math.abs

/**
 * Ultimate Edition Input System - Unity New Input System + Unreal Enhanced Input equivalent
 * Action-based, rebindable, composite bindings, processors, interactions
 */

enum class InputActionType { Button, Value, PassThrough }
enum class InputControlType { Button, Axis, Vector2, Vector3, Quaternion }
enum class InputInteraction { Press, Hold, Tap, MultiTap, SlowTap }
enum class InputProcessorType { Scale, Invert, Normalize, Clamp, Deadzone, Curve }

data class InputProcessor(var type: InputProcessorType, var params: FloatArray = floatArrayOf())

data class InputBinding(
    var path: String = "", // e.g. "<Keyboard>/space", "<Gamepad>/leftStick"
    var groups: String = "", // e.g. "Keyboard&Mouse;Gamepad"
    var action: String = "",
    var isComposite: Boolean = false,
    var isPartOfComposite: Boolean = false,
    var processors: MutableList<InputProcessor> = mutableListOf(),
    var interactions: MutableList<InputInteraction> = mutableListOf()
)

class InputAction(var name: String = "", var type: InputActionType = InputActionType.Button) {
    var expectedControlType: InputControlType = InputControlType.Button
    var bindings = mutableListOf<InputBinding>()
    var processors = mutableListOf<InputProcessor>()
    var interactions = mutableListOf<InputInteraction>()

    // Runtime state
    var isPressed: Boolean = false
        private set
    var wasPressedThisFrame: Boolean = false
        private set
    var wasReleasedThisFrame: Boolean = false
        private set
    var phase: Int = 0 // 0 waiting, 1 started, 2 performed, 3 canceled
    var valueFloat: Float = 0f
    var valueVector2: FloatArray = floatArrayOf(0f, 0f)
    var valueVector3: FloatArray = floatArrayOf(0f, 0f, 0f)

    private var previousPressed = false

    fun addBinding(binding: InputBinding) { bindings.add(binding) }

    fun update(pressed: Boolean, floatValue: Float = if (pressed) 1f else 0f, vec2: FloatArray? = null) {
        previousPressed = isPressed
        isPressed = pressed
        wasPressedThisFrame = isPressed && !previousPressed
        wasReleasedThisFrame = !isPressed && previousPressed
        valueFloat = floatValue
        if (vec2 != null) valueVector2 = vec2

        phase = when {
            wasPressedThisFrame -> 1
            isPressed -> 2
            wasReleasedThisFrame -> 3
            else -> 0
        }

        // apply processors
        for (proc in processors) {
            when (proc.type) {
                InputProcessorType.Scale -> valueFloat *= proc.params.getOrElse(0) { 1f }
                InputProcessorType.Invert -> valueFloat = -valueFloat
                InputProcessorType.Deadzone -> {
                    val dead = proc.params.getOrElse(0) { 0.1f }
                    if (abs(valueFloat) < dead) valueFloat = 0f
                }
                InputProcessorType.Clamp -> {
                    val min = proc.params.getOrElse(0) { -1f }
                    val max = proc.params.getOrElse(1) { 1f }
                    valueFloat = valueFloat.coerceIn(min, max)
                }
                else -> {}
            }
        }
    }

    fun readValueFloat(): Float = valueFloat
    fun readValueVector2(): FloatArray = valueVector2
    fun readValueVector3(): FloatArray = valueVector3
}

class InputActionMap(var name: String = "") {
    val actions = mutableMapOf<String, InputAction>()
    var enabled: Boolean = false

    fun addAction(action: InputAction) { actions[action.name] = action }
    fun findAction(name: String): InputAction? = actions[name]
    fun enable() { enabled = true }
    fun disable() { enabled = false }
}

class InputActionAsset {
    val maps = mutableMapOf<String, InputActionMap>()
    val controlSchemes = mutableListOf<String>()

    fun addMap(map: InputActionMap) { maps[map.name] = map }
    fun findMap(name: String): InputActionMap? = maps[name]
    fun findAction(mapName: String, actionName: String): InputAction? = maps[mapName]?.findAction(actionName)

    companion object {
        fun createDefault(): InputActionAsset {
            val asset = InputActionAsset()
            val gameplay = InputActionMap("Gameplay")
            gameplay.addAction(InputAction("Move", InputActionType.Value).apply {
                expectedControlType = InputControlType.Vector2
                addBinding(InputBinding("<Gamepad>/leftStick", "Gamepad"))
                addBinding(InputBinding("2DVector", "Keyboard&Mouse", isComposite = true))
            })
            gameplay.addAction(InputAction("Look", InputActionType.Value).apply {
                expectedControlType = InputControlType.Vector2
                addBinding(InputBinding("<Gamepad>/rightStick", "Gamepad"))
                addBinding(InputBinding("<Mouse>/delta", "Keyboard&Mouse"))
            })
            gameplay.addAction(InputAction("Jump", InputActionType.Button).apply {
                addBinding(InputBinding("<Keyboard>/space", "Keyboard&Mouse"))
                addBinding(InputBinding("<Gamepad>/buttonSouth", "Gamepad"))
            })
            gameplay.addAction(InputAction("Sprint", InputActionType.Button).apply {
                addBinding(InputBinding("<Keyboard>/leftShift", "Keyboard&Mouse"))
                addBinding(InputBinding("<Gamepad>/leftStickPress", "Gamepad"))
            })
            gameplay.addAction(InputAction("Crouch", InputActionType.Button).apply {
                addBinding(InputBinding("<Keyboard>/leftCtrl", "Keyboard&Mouse"))
                addBinding(InputBinding("<Gamepad>/buttonEast", "Gamepad"))
            })
            gameplay.addAction(InputAction("Fire", InputActionType.Button).apply {
                addBinding(InputBinding("<Mouse>/leftButton", "Keyboard&Mouse"))
                addBinding(InputBinding("<Gamepad>/rightTrigger", "Gamepad"))
            })
            gameplay.addAction(InputAction("Aim", InputActionType.Button).apply {
                addBinding(InputBinding("<Mouse>/rightButton", "Keyboard&Mouse"))
                addBinding(InputBinding("<Gamepad>/leftTrigger", "Gamepad"))
            })
            asset.addMap(gameplay)

            val ui = InputActionMap("UI")
            ui.addAction(InputAction("Navigate", InputActionType.Value).apply {
                expectedControlType = InputControlType.Vector2
            })
            ui.addAction(InputAction("Submit", InputActionType.Button))
            ui.addAction(InputAction("Cancel", InputActionType.Button))
            ui.addAction(InputAction("Point", InputActionType.Value).apply {
                expectedControlType = InputControlType.Vector2
            })
            ui.addAction(InputAction("Click", InputActionType.Button))
            asset.addMap(ui)

            return asset
        }
    }
}

class InputSystemV2 {
    var asset: InputActionAsset = InputActionAsset.createDefault()
    var currentControlScheme: String = "Keyboard&Mouse"

    // Raw input from engine
    var keyboard = mutableMapOf<String, Boolean>()
    var gamepadButtons = mutableMapOf<String, Boolean>()
    var gamepadAxes = mutableMapOf<String, Float>()
    var mousePosition: FloatArray = floatArrayOf(0f, 0f)
    var mouseDelta: FloatArray = floatArrayOf(0f, 0f)
    var mouseButtons = mutableMapOf<Int, Boolean>()

    fun update() {
        // Update all enabled maps
        for (map in asset.maps.values) if (map.enabled) {
            for (action in map.actions.values) {
                // Resolve bindings
                var pressed = false
                var floatVal = 0f
                var vec2 = floatArrayOf(0f, 0f)

                for (binding in action.bindings) {
                    when {
                        binding.path.contains("space") -> pressed = pressed || (keyboard["space"] == true)
                        binding.path.contains("leftShift") -> pressed = pressed || (keyboard["leftShift"] == true)
                        binding.path.contains("leftCtrl") -> pressed = pressed || (keyboard["leftCtrl"] == true)
                        binding.path.contains("w") -> { if (keyboard["w"] == true) vec2[1] += 1f }
                        binding.path.contains("s") -> { if (keyboard["s"] == true) vec2[1] -= 1f }
                        binding.path.contains("a") -> { if (keyboard["a"] == true) vec2[0] -= 1f }
                        binding.path.contains("d") -> { if (keyboard["d"] == true) vec2[0] += 1f }
                        binding.path.contains("leftButton") -> pressed = pressed || (mouseButtons[0] == true)
                        binding.path.contains("rightButton") -> pressed = pressed || (mouseButtons[1] == true)
                        binding.path.contains("leftStick") && !binding.path.contains("Press") -> {
                            val x = gamepadAxes["leftStickX"] ?: 0f
                            val y = gamepadAxes["leftStickY"] ?: 0f
                            vec2[0] += x; vec2[1] += y
                        }
                        binding.path.contains("rightStick") -> {
                            val x = gamepadAxes["rightStickX"] ?: 0f
                            val y = gamepadAxes["rightStickY"] ?: 0f
                            vec2[0] += x; vec2[1] += y
                        }
                        binding.path.contains("buttonSouth") -> pressed = pressed || (gamepadButtons["buttonSouth"] == true)
                        binding.path.contains("rightTrigger") -> {
                            val v = gamepadAxes["rightTrigger"] ?: 0f
                            if (v > 0.5f) pressed = true
                            floatVal = maxOf(floatVal, v)
                        }
                    }
                }

                if (action.type == InputActionType.Button) {
                    action.update(pressed, if (pressed) 1f else 0f)
                } else {
                    val isMoving = vec2[0] != 0f || vec2[1] != 0f || floatVal != 0f
                    action.update(isMoving, floatVal, vec2)
                }
            }
        }
    }

    fun enableMap(name: String) { asset.findMap(name)?.enable() }
    fun disableMap(name: String) { asset.findMap(name)?.disable() }
    fun getAction(map: String, action: String): InputAction? = asset.findAction(map, action)
}
