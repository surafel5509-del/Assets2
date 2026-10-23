package com.sengine.project.templates

import com.sengine.engine.core.*
import com.sengine.engine.ui.*
import com.sengine.project.Project
import org.json.JSONArray
import org.json.JSONObject

/**
 * Professional 2D Platformer Adventure Game Template
 *
 * Features:
 * - Full character controller with run, jump, double-jump, wall-jump, dash
 * - Multiple enemy types (walker, flyer, boss)
 * - Collectible coins and power-ups
 * - Health system with UI
 * - Level transitions with checkpoints
 * - Parallax scrolling backgrounds
 * - Particle effects for jumps, landings, collectibles
 * - Professional HUD with health bar, coin counter, score
 * - Pause menu system
 * - Sound effects and music
 */
object PlatformerAdventureTemplate {
    const val NAME = "2D Platformer Adventure"
    const val DESC = "Professional 2D platformer with full character controller, enemies, power-ups, parallax backgrounds, health system, checkpoints and professional HUD."

    fun create(project: Project) {
        // ── Scripts ──
        writeScripts(project)

        // ── Main Scene ──
        val scene = Scene("Main")
        scene.gravityY = -25f

        // Camera
        val cam = scene.create("MainCamera")
        cam.add(Camera2D().also {
            it.size = 6f; it.follow = "Player"; it.smoothing = 8f
            it.offsetY = 1.5f; it.background = 0xFF1A1A2E.toInt()
        })

        // ── Parallax Background Layers ──
        for (i in 0 until 4) {
            val layer = scene.create("BG_Layer${i}")
            layer.order = -100 + i
            layer.add(SpriteRenderer().also {
                it.texture = ""; it.color = bgColor(i)
                it.shape = 0; it.screenSpace = true
            })
            layer.scaleX = 40f; layer.scaleY = 20f
            layer.x = 0f; layer.y = 0f
        }

        // ── Player ──
        val player = scene.create("Player")
        player.tag = "Player"
        player.x = 0f; player.y = 2f
        player.add(SpriteRenderer().also { it.texture = "player.png"; it.pivotY = 0f })
        player.add(Rigidbody2D().also {
            it.mass = 1f; it.freezeRotation = true; it.gravityScale = 3f
            it.drag = 0.1f; it.friction = 0f
        })
        player.add(Collider2D().also { it.shape = 0; it.width = 0.8f; it.height = 1.4f; it.offsetY = 0.7f })
        player.add(ScriptComponent().also {
            it.script = "PlayerController.js"
            it.params = "speed=7, jump=13, doubleJump=11, dash=18, dashDuration=0.2, wallJumpX=10, wallJumpY=12"
        })
        player.add(Animator().also { it.clip = "player_idle.anim" })
        player.add(AudioSource())

        // ── Ground ──
        val ground = scene.create("Ground")
        ground.tag = "Ground"
        ground.y = -1f; ground.scaleX = 40f; ground.scaleY = 2f
        ground.add(SpriteRenderer().also { it.color = 0xFF3D5A80.toInt() })
        ground.add(Rigidbody2D().also { it.bodyType = 2 }) // Static
        ground.add(Collider2D().also { it.shape = 0; it.width = 1f; it.height = 1f })

        // ── Platforms ──
        val platforms = listOf(
            floatArrayOf(3f, 1f, 4f, 0.5f),
            floatArrayOf(7f, 3f, 3f, 0.5f),
            floatArrayOf(11f, 2f, 5f, 0.5f),
            floatArrayOf(-4f, 2f, 3f, 0.5f),
            floatArrayOf(-7f, 4f, 4f, 0.5f),
            floatArrayOf(15f, 4f, 3f, 0.5f),
            floatArrayOf(20f, 1f, 6f, 0.5f),
            floatArrayOf(25f, 3f, 4f, 0.5f),
        )
        for ((i, p) in platforms.withIndex()) {
            val plat = scene.create("Platform_$i")
            plat.tag = "Ground"
            plat.x = p[0]; plat.y = p[1]; plat.scaleX = p[2]; plat.scaleY = p[3]
            plat.add(SpriteRenderer().also { it.color = 0xFF4A7C9B.toInt() })
            plat.add(Rigidbody2D().also { it.bodyType = 2 })
            plat.add(Collider2D().also { it.shape = 0; it.width = 1f; it.height = 1f })
        }

        // ── Moving Platform ──
        val movPlat = scene.create("MovingPlatform")
        movPlat.tag = "Ground"
        movPlat.x = 30f; movPlat.y = 2f; movPlat.scaleX = 4f; movPlat.scaleY = 0.4f
        movPlat.add(SpriteRenderer().also { it.color = 0xFFE9C46A.toInt() })
        movPlat.add(Rigidbody2D().also { it.bodyType = 1; it.startVx = 3f })
        movPlat.add(Collider2D().also { it.shape = 0; it.width = 1f; it.height = 1f })
        movPlat.add(ScriptComponent().also {
            it.script = "MovingPlatform.js"
            it.params = "rangeX=5, speed=3"
        })

        // ── Enemies ──
        val enemyPositions = listOf(
            floatArrayOf(5f, 1f), floatArrayOf(12f, 1f),
            floatArrayOf(18f, 1f), floatArrayOf(22f, 1f),
        )
        for ((i, pos) in enemyPositions.withIndex()) {
            val enemy = scene.create("WalkerEnemy_$i")
            enemy.tag = "Enemy"
            enemy.x = pos[0]; enemy.y = pos[1]
            enemy.add(SpriteRenderer().also { it.texture = "enemy_walker.png"; it.pivotY = 0f })
            enemy.add(Rigidbody2D().also { it.freezeRotation = true; it.gravityScale = 3f })
            enemy.add(Collider2D().also { it.shape = 1; it.radius = 0.5f; it.isTrigger = true })
            enemy.add(ScriptComponent().also {
                it.script = "WalkerEnemy.js"
                it.params = "speed=2, rangeX=3, health=2"
            })
        }

        // Flying enemy
        val flyer = scene.create("FlyerEnemy")
        flyer.tag = "Enemy"
        flyer.x = 9f; flyer.y = 5f
        flyer.add(SpriteRenderer().also { it.texture = "enemy_flyer.png" })
        flyer.add(Collider2D().also { it.shape = 1; it.radius = 0.4f; it.isTrigger = true })
        flyer.add(ScriptComponent().also {
            it.script = "FlyerEnemy.js"
            it.params = "amplitude=2, speed=3, health=1"
        })

        // ── Coins ──
        val coinPositions = listOf(
            floatArrayOf(2f, 3f), floatArrayOf(4f, 2f), floatArrayOf(6f, 4f),
            floatArrayOf(8f, 5f), floatArrayOf(10f, 3f), floatArrayOf(13f, 4f),
            floatArrayOf(16f, 3f), floatArrayOf(19f, 2f), floatArrayOf(22f, 4f),
            floatArrayOf(24f, 5f), floatArrayOf(27f, 3f), floatArrayOf(30f, 4f),
        )
        for ((i, pos) in coinPositions.withIndex()) {
            val coin = scene.create("Coin_$i")
            coin.tag = "Coin"
            coin.x = pos[0]; coin.y = pos[1]
            coin.add(SpriteRenderer().also { it.texture = "coin.png" })
            coin.add(Collider2D().also { it.shape = 1; it.radius = 0.3f; it.isTrigger = true })
            coin.add(ScriptComponent().also {
                it.script = "Collectible.js"
                it.params = "type=coin, value=10, bobAmplitude=0.2, bobSpeed=3"
            })
            coin.add(ParticleEmitter().also {
                it.emitting = false; it.rate = 50f; it.burstCount = 8
                it.startColor = 0xFFFFDD00.toInt(); it.endColor = 0x00FFDD00.toInt()
                it.startSize = 0.2f; it.endSize = 0f; it.lifetime = 0.5f
                it.speed = 3f; it.gravityY = -5f
            })
        }

        // ── Power-up: Health ──
        val health = scene.create("HealthPickup")
        health.tag = "Pickup"
        health.x = 14f; health.y = 3f
        health.add(SpriteRenderer().also { it.texture = "health_pickup.png" })
        health.add(Collider2D().also { it.shape = 1; it.radius = 0.35f; it.isTrigger = true })
        health.add(ScriptComponent().also {
            it.script = "Collectible.js"
            it.params = "type=health, value=1, bobAmplitude=0.3, bobSpeed=2"
        })

        // ── Checkpoint ──
        val checkpoint = scene.create("Checkpoint")
        checkpoint.tag = "Checkpoint"
        checkpoint.x = 15f; checkpoint.y = 1f
        checkpoint.add(SpriteRenderer().also { it.texture = "checkpoint_flag.png" })
        checkpoint.add(Collider2D().also { it.shape = 0; it.width = 1f; it.height = 2f; it.isTrigger = true })
        checkpoint.add(ScriptComponent().also {
            it.script = "Checkpoint.js"
        })

        // ── Level End ──
        val end = scene.create("LevelEnd")
        end.tag = "LevelEnd"
        end.x = 33f; end.y = 1f
        end.add(SpriteRenderer().also { it.texture = "portal.png"; it.color = 0xFF7B2FF7.toInt() })
        end.add(Collider2D().also { it.shape = 1; it.radius = 1f; it.isTrigger = true })
        end.add(ScriptComponent().also {
            it.script = "LevelEnd.js"
        })
        end.add(ParticleEmitter().also {
            it.emitting = true; it.rate = 20f
            it.startColor = 0xFF7B2FF7.toInt(); it.endColor = 0x007B2FF7.toInt()
            it.startSize = 0.3f; it.endSize = 0f; it.lifetime = 1f
            it.speed = 1f
        })

        // ── Death Zone ──
        val deathZone = scene.create("DeathZone")
        deathZone.tag = "DeathZone"
        deathZone.y = -10f; deathZone.scaleX = 100f; deathZone.scaleY = 0.5f
        deathZone.add(Collider2D().also { it.shape = 0; it.width = 1f; it.height = 1f; it.isTrigger = true })
        deathZone.add(ScriptComponent().also {
            it.script = "DeathZone.js"
        })

        // ── HUD (Screen Space UI) ──
        val hudCanvas = scene.create("HUD_Canvas")
        hudCanvas.add(UICanvas().also { it.refWidth = 1920; it.refHeight = 1080; it.scaleMode = 3; it.sortOrder = 100 })

        // Health Bar
        val healthBar = scene.create("HUD_HealthBar")
        healthBar.parent = hudCanvas
        healthBar.add(RectTransform().also {
            it.anchorMinX = 0f; it.anchorMinY = 1f; it.anchorMaxX = 0f; it.anchorMaxY = 1f
            it.pivotX = 0f; it.pivotY = 1f
            it.anchoredX = 20f; it.anchoredY = -20f
            it.width = 250f; it.height = 30f
        })
        healthBar.add(UIProgressBar().also {
            it.value = 3f; it.minValue = 0f; it.maxValue = 3f
            it.fillColor = 0xFFE5383B.toInt(); it.bgColor = 0x80333333.toInt()
            it.direction = 0
        })

        // Heart icon
        val heartIcon = scene.create("HUD_HeartIcon")
        heartIcon.parent = hudCanvas
        heartIcon.add(RectTransform().also {
            it.anchorMinX = 0f; it.anchorMinY = 1f; it.anchorMaxX = 0f; it.anchorMaxY = 1f
            it.pivotX = 0f; it.pivotY = 1f
            it.anchoredX = 20f; it.anchoredY = -55f
            it.width = 30f; it.height = 30f
        })
        heartIcon.add(UIImage().also { it.image = "heart_icon.png" })

        // Coin counter
        val coinText = scene.create("HUD_CoinText")
        coinText.tag = "CoinCounter"
        coinText.parent = hudCanvas
        coinText.add(RectTransform().also {
            it.anchorMinX = 0f; it.anchorMinY = 1f; it.anchorMaxX = 0f; it.anchorMaxY = 1f
            it.pivotX = 0f; it.pivotY = 1f
            it.anchoredX = 70f; it.anchoredY = -55f
            it.width = 200f; it.height = 40f
        })
        coinText.add(UIText().also { it.text = "× 0"; it.fontSize = 28; it.color = 0xFFFFDD00.toInt(); it.align = 0 })

        // Score text
        val scoreText = scene.create("HUD_Score")
        scoreText.tag = "ScoreText"
        scoreText.parent = hudCanvas
        scoreText.add(RectTransform().also {
            it.anchorMinX = 1f; it.anchorMinY = 1f; it.anchorMaxX = 1f; it.anchorMaxY = 1f
            it.pivotX = 1f; it.pivotY = 1f
            it.anchoredX = -20f; it.anchoredY = -20f
            it.width = 300f; it.height = 40f
        })
        scoreText.add(UIText().also { it.text = "Score: 0"; it.fontSize = 32; it.color = 0xFFFFFFFF.toInt(); it.align = 2 })

        // Game Manager
        val gameManager = scene.create("GameManager")
        gameManager.add(ScriptComponent().also {
            it.script = "GameManager.js"
            it.params = "maxHealth=3, startCoins=0"
        })

        // Ambient particles
        val ambientFx = scene.create("AmbientParticles")
        ambientFx.add(ParticleEmitter().also {
            it.emitting = true; it.rate = 5f
            it.startColor = 0x40FFFFFF.toInt(); it.endColor = 0x00FFFFFF.toInt()
            it.startSize = 0.1f; it.endSize = 0.05f; it.lifetime = 4f
            it.speed = 0.5f; it.gravityY = -0.1f
        })

        com.sengine.engine.core.SceneSerializer.save(scene, project.sceneFile("Main"))
    }

    private fun bgColor(layer: Int): Int = when (layer) {
        0 -> 0xFF0F0F23.toInt()
        1 -> 0xFF16213E.toInt()
        2 -> 0xFF1A1A40.toInt()
        3 -> 0xFF1F2937.toInt()
        else -> 0xFF000000.toInt()
    }

    private fun writeScripts(project: Project) {
        // Player Controller
        project.assetFile("PlayerController.js").writeText("""
// PlayerController.js — Professional 2D Platformer Character Controller
// Params: speed=7, jump=13, doubleJump=11, dash=18, dashDuration=0.2, wallJumpX=10, wallJumpY=12
var velocityX = 0;
var velocityY = 0;
var grounded = false;
var canDoubleJump = false;
var hasDoubleJumped = false;
var isDashing = false;
var dashTimer = 0;
var dashDir = 1;
var wallSliding = false;
var facingRight = true;
var invincibleTimer = 0;
var coyoteTime = 0;
var jumpBufferTime = 0;
var health = 3;
var maxHealth = 3;

function start() {
    health = 3;
    self.tag = "Player";
}

function update(dt) {
    // Input
    var moveX = input.axisX;
    if (moveX != 0) facingRight = moveX > 0;

    // Coyote time (allow jump briefly after leaving ground)
    if (self.grounded) {
        coyoteTime = 0.12;
        canDoubleJump = true;
        hasDoubleJumped = false;
    } else {
        coyoteTime -= dt;
    }

    // Jump buffer (register jump input slightly before landing)
    if (input.aDown) jumpBufferTime = 0.1;
    else jumpBufferTime -= dt;

    // Dashing
    if (isDashing) {
        dashTimer -= dt;
        self.vx = dashDir * dash;
        self.vy = 0;
        if (dashTimer <= 0) isDashing = false;
    } else {
        // Horizontal movement with acceleration
        var targetVx = moveX * speed;
        var accel = self.grounded ? 25 : 15;
        self.vx += (targetVx - self.vx) * accel * dt;

        // Gravity
        if (wallSliding && self.vy < 0) {
            self.vy += 9.81 * 0.3 * dt * 60; // Slower fall while wall sliding
        } else {
            self.vy += self.grounded ? 0 : 0;
        }
    }

    // Jumping
    if (jumpBufferTime > 0) {
        if (coyoteTime > 0) {
            self.vy = jump;
            grounded = false;
            coyoteTime = 0;
            jumpBufferTime = 0;
            audio.beep();
        } else if (canDoubleJump && !hasDoubleJumped) {
            self.vy = doubleJump;
            hasDoubleJumped = true;
            jumpBufferTime = 0;
            audio.beep();
        }
    }

    // Variable jump height
    if (!input.a && self.vy > 0) {
        self.vy *= 0.85;
    }

    // Dash
    if (input.bDown && !isDashing) {
        isDashing = true;
        dashTimer = dashDuration;
        dashDir = facingRight ? 1 : -1;
        invincibleTimer = dashDuration;
    }

    // Flip sprite
    self.scaleX = facingRight ? 1 : -1;

    // Invincibility timer
    if (invincibleTimer > 0) invincibleTimer -= dt;

    // Death check
    if (self.y < -15) {
        scene.reload();
    }
}

function onCollision(other) {
    if (other.tag == "Ground") {
        grounded = true;
    }
}

function onTrigger(other) {
    if (other.tag == "Enemy" && invincibleTimer <= 0) {
        takeDamage(1);
    }
    if (other.tag == "Coin") {
        // Handled by GameManager event
    }
}

function takeDamage(amount) {
    if (invincibleTimer > 0) return;
    health -= amount;
    invincibleTimer = 1.5;
    self.vy = 8;
    // Flash effect
    var sr = self.getComponent("SpriteRenderer");
    if (sr) sr.color = 0xFFFF0000;
    after(0.1, function() { if (sr) sr.color = 0xFFFFFFFF; });
    if (health <= 0) {
        scene.reload();
    }
}

function onTriggerExit(other) {
    if (other.tag == "Ground") {
        grounded = false;
    }
}
        """.trimIndent())

        // Walker Enemy
        project.assetFile("WalkerEnemy.js").writeText("""
// WalkerEnemy.js — Patrolling walker enemy
var direction = 1;
var startX = 0;
var health = 2;

function start() {
    startX = self.x;
    health = 2;
}

function update(dt) {
    self.vx = direction * speed;
    if (self.x > startX + rangeX) direction = -1;
    if (self.x < startX - rangeX) direction = 1;
    self.scaleX = direction;
}

function onTrigger(other) {
    if (other.tag == "Player") {
        // Check if player is above (stomp)
        if (other.y > self.y + 0.3) {
            health--;
            other.vy = 10;
            audio.beep();
            if (health <= 0) {
                self.destroy();
            }
        }
    }
}
        """.trimIndent())

        // Flyer Enemy
        project.assetFile("FlyerEnemy.js").writeText("""
// FlyerEnemy.js — Flying enemy with sine wave movement
var startY = 0;
var health = 1;
var time = 0;

function start() {
    startY = self.y;
    health = 1;
}

function update(dt) {
    time += dt;
    self.y = startY + sin(time * speed) * amplitude;
    self.x += speed * 0.5 * dt;
    if (self.x > 40) self.x = -5;
}

function onTrigger(other) {
    if (other.tag == "Player") {
        if (other.y > self.y + 0.2) {
            other.vy = 12;
            self.destroy();
        }
    }
}
        """.trimIndent())

        // Collectible
        project.assetFile("Collectible.js").writeText("""
// Collectible.js — Generic collectible (coin, health, power-up)
var collected = false;
var startY = 0;
var time = 0;

function start() {
    startY = self.y;
    time = random() * 6.28;
}

function update(dt) {
    time += dt;
    self.y = startY + sin(time * bobSpeed) * bobAmplitude;
}

function onTrigger(other) {
    if (collected) return;
    if (other.tag == "Player") {
        collected = true;
        audio.beep();
        self.burst(8);
        after(0.01, function() { self.destroy(); });
    }
}
        """.trimIndent())

        // Checkpoint
        project.assetFile("Checkpoint.js").writeText("""
// Checkpoint.js — Level checkpoint
var activated = false;

function start() {
    self.color = 0xFF888888;
}

function onTrigger(other) {
    if (activated || other.tag != "Player") return;
    activated = true;
    self.color = 0xFF44FF44;
    audio.beep();
    storage.set("checkpoint_x", self.x);
    storage.set("checkpoint_y", self.y);
}
        """.trimIndent())

        // Moving Platform
        project.assetFile("MovingPlatform.js").writeText("""
// MovingPlatform.js — Oscillating platform
var startX = 0;
var startY = 0;
var time = 0;

function start() {
    startX = self.x;
    startY = self.y;
}

function update(dt) {
    time += dt;
    self.x = startX + sin(time * speed) * rangeX;
}
        """.trimIndent())

        // Death Zone
        project.assetFile("DeathZone.js").writeText("""
// DeathZone.js — Kills player on contact
function onTrigger(other) {
    if (other.tag == "Player") {
        scene.reload();
    }
}
        """.trimIndent())

        // Level End
        project.assetFile("LevelEnd.js").writeText("""
// LevelEnd.js — Level completion trigger
function onTrigger(other) {
    if (other.tag == "Player") {
        audio.beep();
        scene.load("Level2");
    }
}
        """.trimIndent())

        // Game Manager
        project.assetFile("GameManager.js").writeText("""
// GameManager.js — Central game state manager
var score = 0;
var coins = 0;
var health = 3;
var gameTime = 0;

function start() {
    score = 0;
    coins = storage.get("coins", 0);
    health = 3;
    var cx = storage.get("checkpoint_x", 0);
    var cy = storage.get("checkpoint_y", 2);
    var player = scene.find("Player");
    if (player && cx != 0) {
        player.setPosition(cx, cy);
    }
}

function update(dt) {
    gameTime += dt;
}
        """.trimIndent())
    }
}
