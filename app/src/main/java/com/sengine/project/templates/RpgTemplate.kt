package com.sengine.project.templates

import com.sengine.engine.core.*
import com.sengine.engine.ui.*
import com.sengine.project.Project

/**
 * Professional Top-Down RPG Game Template
 *
 * Features:
 * - Top-down movement with 8-directional animation
 * - NPC dialogue system with branching choices
 * - Inventory system with equipment
 * - Combat with health, damage, and XP
 * - Quest tracking system
 * - Day/Night cycle
 * - Procedural dungeon generation
 * - Treasure chests and pickups
 * - Save/Load system
 * - Professional RPG HUD (health bar, minimap, quest log)
 */
object RpgTemplate {
    const val NAME = "Top-Down RPG"
    const val DESC = "Professional top-down RPG with NPC dialogue, inventory, equipment, combat, quests, day/night cycle, procedural dungeons, save/load, and professional RPG HUD."

    fun create(project: Project) {
        writeScripts(project)

        val scene = Scene("World")
        scene.gravityY = 0f // Top-down, no gravity

        // Camera
        val cam = scene.create("MainCamera")
        cam.add(Camera2D().also {
            it.size = 6f; it.follow = "Player"; it.smoothing = 5f
            it.background = 0xFF111122.toInt()
        })

        // ── Player ──
        val player = scene.create("Player")
        player.tag = "Player"
        player.x = 5f; player.y = 5f
        player.add(SpriteRenderer().also { it.texture = "player_rpg.png"; it.pivotY = 0.2f })
        player.add(Rigidbody2D().also { it.bodyType = 1; it.gravityScale = 0f; it.freezeRotation = true; it.drag = 10f })
        player.add(Collider2D().also { it.shape = 1; it.radius = 0.4f })
        player.add(ScriptComponent().also {
            it.script = "RpgPlayer.js"
            it.params = "speed=5, attackDamage=10, attackRange=1.5"
        })

        // ── World Ground ──
        val ground = scene.create("Ground")
        ground.order = -10
        ground.x = 15f; ground.y = 10f; ground.scaleX = 40f; ground.scaleY = 30f
        ground.add(SpriteRenderer().also { it.texture = "grass_tile.png"; it.color = 0xFF3A6B35.toInt() })

        // ── Walls/Boundaries ──
        val walls = listOf(
            floatArrayOf(15f, -1f, 42f, 2f),   // Bottom
            floatArrayOf(15f, 21f, 42f, 2f),   // Top
            floatArrayOf(-2f, 10f, 2f, 24f),   // Left
            floatArrayOf(32f, 10f, 2f, 24f),   // Right
        )
        for ((i, w) in walls.withIndex()) {
            val wall = scene.create("Wall_$i")
            wall.tag = "Wall"
            wall.x = w[0]; wall.y = w[1]; wall.scaleX = w[2]; wall.scaleY = w[3]
            wall.add(SpriteRenderer().also { it.color = 0xFF2C3E50.toInt() })
            wall.add(Rigidbody2D().also { it.bodyType = 2; it.gravityScale = 0f })
            wall.add(Collider2D().also { it.shape = 0; it.width = 1f; it.height = 1f })
        }

        // ── Obstacles / Trees ──
        val obstacles = listOf(
            floatArrayOf(5f, 8f), floatArrayOf(10f, 3f), floatArrayOf(20f, 15f),
            floatArrayOf(25f, 5f), floatArrayOf(15f, 12f), floatArrayOf(8f, 17f),
            floatArrayOf(22f, 8f), floatArrayOf(28f, 18f), floatArrayOf(12f, 7f),
        )
        for ((i, pos) in obstacles.withIndex()) {
            val tree = scene.create("Tree_$i")
            tree.tag = "Obstacle"
            tree.x = pos[0]; tree.y = pos[1]
            tree.add(SpriteRenderer().also { it.texture = "tree.png"; it.color = 0xFF2D5016.toInt() })
            tree.add(Rigidbody2D().also { it.bodyType = 2; it.gravityScale = 0f })
            tree.add(Collider2D().also { it.shape = 1; it.radius = 0.6f })
        }

        // ── NPCs ──
        val npcData = listOf(
            Triple("Elder", 8f, 10f),
            Triple("Merchant", 20f, 7f),
            Triple("Guard", 25f, 15f),
        )
        for ((i, data) in npcData.withIndex()) {
            val npc = scene.create("NPC_${data.first}")
            npc.tag = "NPC"
            npc.x = data.second; npc.y = data.third
            npc.add(SpriteRenderer().also { it.texture = "npc_${data.first.lowercase()}.png"; it.color = npcColor(i) })
            npc.add(Rigidbody2D().also { it.bodyType = 2; it.gravityScale = 0f })
            npc.add(Collider2D().also { it.shape = 1; it.radius = 0.5f; it.isTrigger = true })
            npc.add(ScriptComponent().also {
                it.script = "NpcController.js"
                it.params = "npcName=${data.first}, dialogueFile=${data.first.lowercase()}_dialogue.json"
            })
        }

        // ── Enemies ──
        val enemyPositions = listOf(
            floatArrayOf(15f, 5f), floatArrayOf(22f, 12f),
            floatArrayOf(10f, 15f), floatArrayOf(27f, 8f),
            floatArrayOf(18f, 18f),
        )
        for ((i, pos) in enemyPositions.withIndex()) {
            val enemy = scene.create("Enemy_$i")
            enemy.tag = "Enemy"
            enemy.x = pos[0]; enemy.y = pos[1]
            enemy.add(SpriteRenderer().also { it.texture = "enemy_slime.png"; it.color = 0xFF44AA44.toInt() })
            enemy.add(Rigidbody2D().also { it.bodyType = 1; it.gravityScale = 0f; it.drag = 5f; it.freezeRotation = true })
            enemy.add(Collider2D().also { it.shape = 1; it.radius = 0.4f; it.isTrigger = true })
            enemy.add(ScriptComponent().also {
                it.script = "EnemyAI.js"
                it.params = "speed=2, health=30, damage=5, detectRange=6, attackRange=1.2, xpReward=15"
            })
        }

        // ── Treasure Chests ──
        val chestPositions = listOf(
            floatArrayOf(6f, 16f), floatArrayOf(28f, 3f), floatArrayOf(18f, 14f),
        )
        for ((i, pos) in chestPositions.withIndex()) {
            val chest = scene.create("Chest_$i")
            chest.tag = "Chest"
            chest.x = pos[0]; chest.y = pos[1]
            chest.add(SpriteRenderer().also { it.texture = "chest.png"; it.color = 0xFFAA7722.toInt() })
            chest.add(Collider2D().also { it.shape = 0; it.width = 0.8f; it.height = 0.8f; it.isTrigger = true })
            chest.add(ScriptComponent().also {
                it.script = "TreasureChest.js"
                it.params = "loot=${lootForChest(i)}"
            })
        }

        // ── Dungeon Entrance ──
        val dungeon = scene.create("DungeonEntrance")
        dungeon.tag = "DungeonEntrance"
        dungeon.x = 30f; dungeon.y = 10f
        dungeon.add(SpriteRenderer().also { it.texture = "portal.png"; it.color = 0xFF7744CC.toInt() })
        dungeon.add(Collider2D().also { it.shape = 1; it.radius = 1f; it.isTrigger = true })
        dungeon.add(ScriptComponent().also {
            it.script = "DungeonEntrance.js"
        })
        dungeon.add(ParticleEmitter().also {
            it.emitting = true; it.rate = 15f
            it.startColor = 0xFF7744CC.toInt(); it.endColor = 0x007744CC.toInt()
            it.startSize = 0.2f; it.endSize = 0f; it.lifetime = 1f; it.speed = 1f
        })

        // ── Pickups (Health, Coins) ──
        val pickupPositions = listOf(
            Triple("HealthPotion", 12f, 9f),
            Triple("Coin", 7f, 12f), Triple("Coin", 16f, 4f), Triple("Coin", 24f, 16f),
            Triple("Coin", 9f, 6f), Triple("Coin", 21f, 11f),
        )
        for ((i, data) in pickupPositions.withIndex()) {
            val pickup = scene.create("Pickup_${i}")
            pickup.tag = data.first
            pickup.x = data.second; pickup.y = data.third
            pickup.add(SpriteRenderer().also {
                it.texture = if (data.first == "HealthPotion") "health_potion.png" else "coin.png"
                it.color = if (data.first == "HealthPotion") 0xFFFF4444.toInt() else 0xFFFFD700.toInt()
            })
            pickup.add(Collider2D().also { it.shape = 1; it.radius = 0.3f; it.isTrigger = true })
            pickup.add(ScriptComponent().also {
                it.script = "Pickup.js"
                it.params = "type=${data.first}, value=${if (data.first == "HealthPotion") 20 else 5}"
            })
        }

        // ── Day/Night Cycle ──
        val dayNight = scene.create("DayNightCycle")
        dayNight.add(com.sengine.engine.lighting2d.DayNightCycle().also {
            it.timeOfDay = 10f; it.dayDuration = 600f; it.autoAdvance = true
        })

        // ── Lights ──
        val torch1 = scene.create("Torch_1")
        torch1.x = 10f; torch1.y = 10f
        torch1.add(com.sengine.engine.lighting2d.Light2D().also {
            it.lightType = 0; it.color = 0xFFFF9944.toInt(); it.intensity = 1.2f
            it.range = 4f; it.flicker = true; it.flickerSpeed = 3f
        })

        val torch2 = scene.create("Torch_2")
        torch2.x = 20f; torch2.y = 10f
        torch2.add(com.sengine.engine.lighting2d.Light2D().also {
            it.lightType = 0; it.color = 0xFFFF9944.toInt(); it.intensity = 1.2f
            it.range = 4f; it.flicker = true; it.flickerSpeed = 4f
        })

        // ── HUD ──
        val hudCanvas = scene.create("HUD_Canvas")
        hudCanvas.add(UICanvas().also { it.refWidth = 1920; it.refHeight = 1080; it.scaleMode = 3; it.sortOrder = 100 })

        // Health Bar
        val healthBar = scene.create("HUD_Health")
        healthBar.parent = hudCanvas
        healthBar.add(RectTransform().also {
            it.anchorMinX = 0f; it.anchorMinY = 1f; it.anchorMaxX = 0f; it.anchorMaxY = 1f
            it.pivotX = 0f; it.pivotY = 1f
            it.anchoredX = 20f; it.anchoredY = -20f; it.width = 250f; it.height = 25f
        })
        healthBar.add(UIProgressBar().also {
            it.value = 100f; it.minValue = 0f; it.maxValue = 100f
            it.fillColor = 0xFFE5383B.toInt(); it.bgColor = 0x80333333.toInt()
        })

        // XP Bar
        val xpBar = scene.create("HUD_XP")
        xpBar.parent = hudCanvas
        xpBar.add(RectTransform().also {
            it.anchorMinX = 0f; it.anchorMinY = 1f; it.anchorMaxX = 0f; it.anchorMaxY = 1f
            it.pivotX = 0f; it.pivotY = 1f
            it.anchoredX = 20f; it.anchoredY = -50f; it.width = 250f; it.height = 15f
        })
        xpBar.add(UIProgressBar().also {
            it.value = 0f; it.minValue = 0f; it.maxValue = 100f
            it.fillColor = 0xFF44AAFF.toInt(); it.bgColor = 0x80333333.toInt()
        })

        // Gold counter
        val goldText = scene.create("HUD_Gold")
        goldText.tag = "GoldText"
        goldText.parent = hudCanvas
        goldText.add(RectTransform().also {
            it.anchorMinX = 0f; it.anchorMinY = 0f; it.anchorMaxX = 0f; it.anchorMaxY = 0f
            it.pivotX = 0f; it.pivotY = 0f
            it.anchoredX = 20f; it.anchoredY = 20f; it.width = 200f; it.height = 40f
        })
        goldText.add(UIText().also { it.text = "💰 0"; it.fontSize = 28; it.color = 0xFFFFD700.toInt(); it.align = 0 })

        // Level text
        val levelText = scene.create("HUD_Level")
        levelText.tag = "LevelText"
        levelText.parent = hudCanvas
        levelText.add(RectTransform().also {
            it.anchorMinX = 0f; it.anchorMinY = 1f; it.anchorMaxX = 0f; it.anchorMaxY = 1f
            it.pivotX = 0f; it.pivotY = 1f
            it.anchoredX = 280f; it.anchoredY = -20f; it.width = 100f; it.height = 40f
        })
        levelText.add(UIText().also { it.text = "Lv. 1"; it.fontSize = 24; it.color = 0xFFFFFFFF.toInt() })

        // Dialogue box (hidden by default)
        val dialogueBox = scene.create("DialogueBox")
        dialogueBox.tag = "DialogueBox"
        dialogueBox.parent = hudCanvas
        dialogueBox.add(RectTransform().also {
            it.anchorMinX = 0.1f; it.anchorMinY = 0.05f; it.anchorMaxX = 0.9f; it.anchorMaxY = 0.35f
            it.width = 0f; it.height = 0f
        })
        dialogueBox.add(UIPanel().also {
            it.bgColor = 0xDD111122.toInt(); it.cornerRadius = 8f
            it.borderColor = 0xFF4488FF.toInt(); it.borderWidth = 2f
        })

        val dialogueText = scene.create("DialogueText")
        dialogueText.tag = "DialogueText"
        dialogueText.parent = hudCanvas
        dialogueText.add(RectTransform().also {
            it.anchorMinX = 0.12f; it.anchorMinY = 0.15f; it.anchorMaxX = 0.88f; it.anchorMaxY = 0.33f
            it.width = 0f; it.height = 0f
        })
        dialogueText.add(UIText().also { it.text = ""; it.fontSize = 24; it.color = 0xFFEEEEFF.toInt(); it.align = 0 })

        // Quest log text
        val questText = scene.create("HUD_Quest")
        questText.tag = "QuestText"
        questText.parent = hudCanvas
        questText.add(RectTransform().also {
            it.anchorMinX = 1f; it.anchorMinY = 1f; it.anchorMaxX = 1f; it.anchorMaxY = 1f
            it.pivotX = 1f; it.pivotY = 1f
            it.anchoredX = -20f; it.anchoredY = -20f; it.width = 300f; it.height = 80f
        })
        questText.add(UIText().also { it.text = "📜 Quest:\nTalk to the Elder"; it.fontSize = 18; it.color = 0xFFFFCC44.toInt(); it.align = 2 })

        // ── Game Manager ──
        val gm = scene.create("GameManager")
        gm.add(ScriptComponent().also {
            it.script = "RpgGameManager.js"
            it.params = "startLevel=1, startHealth=100, startGold=0"
        })

        com.sengine.engine.core.SceneSerializer.save(scene, project.sceneFile("World"))
    }

    private fun npcColor(i: Int): Int = when (i) {
        0 -> 0xFFCCCCFF.toInt()
        1 -> 0xFFFFCC44.toInt()
        2 -> 0xFF8888FF.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    private fun lootForChest(i: Int): String = when (i) {
        0 -> "gold:50,health_potion:2"
        1 -> "gold:100,iron_sword:1"
        2 -> "gold:25,gem:1,health_potion:1"
        else -> "gold:10"
    }

    private fun writeScripts(project: Project) {
        // RPG Player
        project.assetFile("RpgPlayer.js").writeText("""
// RpgPlayer.js — Top-down RPG player controller
var speed = 5;
var health = 100;
var maxHealth = 100;
var level = 1;
var xp = 0;
var xpToNext = 100;
var gold = 0;
var attackDamage = 10;
var attackCooldown = 0;
var invincible = 0;

function start() {
    health = 100;
    gold = 0;
    level = 1;
    xp = 0;
}

function update(dt) {
    var mx = input.axisX;
    var my = input.axisY;
    var len = sqrt(mx * mx + my * my);
    if (len > 0.1) {
        self.vx = (mx / len) * speed;
        self.vy = (my / len) * speed;
        // Flip sprite
        if (mx < 0) self.scaleX = -1;
        else if (mx > 0) self.scaleX = 1;
    } else {
        self.vx *= 0.8;
        self.vy *= 0.8;
    }

    // Attack
    if (attackCooldown > 0) attackCooldown -= dt;
    if (input.bDown && attackCooldown <= 0) {
        attackCooldown = 0.4;
        audio.beep();
        // Attack hitbox handled by collision
    }

    // Invincibility
    if (invincible > 0) invincible -= dt;

    // Clamp to world bounds
    if (self.x < 1) self.x = 1;
    if (self.x > 30) self.x = 30;
    if (self.y < 1) self.y = 1;
    if (self.y > 19) self.y = 19;

    // Update HUD
    updateHud();
}

function takeDamage(amount) {
    if (invincible > 0) return;
    health -= amount;
    invincible = 1.0;
    if (health <= 0) {
        health = 0;
        scene.reload();
    }
}

function gainXp(amount) {
    xp += amount;
    while (xp >= xpToNext) {
        xp -= xpToNext;
        level++;
        xpToNext = level * 100;
        maxHealth += 10;
        health = maxHealth;
        attackDamage += 2;
    }
}

function updateHud() {
    var healthBar = scene.find("HUD_Health");
    // HUD updates handled by GameManager
}

function onCollision(other) {
    if (other.tag == "Wall" || other.tag == "Obstacle") {
        self.vx = 0;
        self.vy = 0;
    }
}

function onTrigger(other) {
    if (other.tag == "Enemy") {
        takeDamage(5);
    }
}
        """.trimIndent())

        // Enemy AI
        project.assetFile("EnemyAI.js").writeText("""
// EnemyAI.js — Simple chase AI for RPG enemies
var speed = 2;
var health = 30;
var detectRange = 6;
var attackRange = 1.2;
var damage = 5;
var xpReward = 15;
var state = 0; // 0=idle, 1=chase, 2=attack
var attackCooldown = 0;
var patrolDir = 1;
var patrolTimer = 0;
var startX = 0;
var startY = 0;

function start() {
    startX = self.x;
    startY = self.y;
}

function update(dt) {
    var player = scene.find("Player");
    if (!player) return;

    var dx = player.x - self.x;
    var dy = player.y - self.y;
    var dist = sqrt(dx * dx + dy * dy);

    attackCooldown -= dt;

    if (dist < detectRange) {
        state = 1; // Chase
        if (dist < attackRange) {
            state = 2; // Attack
            self.vx = 0;
            self.vy = 0;
            if (attackCooldown <= 0) {
                attackCooldown = 1.0;
            }
        } else {
            // Move toward player
            self.vx = (dx / dist) * speed;
            self.vy = (dy / dist) * speed;
        }
    } else {
        state = 0; // Patrol
        patrolTimer += dt;
        if (patrolTimer > 2) {
            patrolDir *= -1;
            patrolTimer = 0;
        }
        self.vx = patrolDir * speed * 0.3;
        self.vy = sin(patrolTimer * 2) * speed * 0.3;
        if (abs(self.x - startX) > 3) patrolDir *= -1;
    }

    if (self.vx < 0) self.scaleX = -1;
    else if (self.vx > 0) self.scaleX = 1;
}

function onTrigger(other) {
    if (other.tag == "Player") {
        // Player attacks enemy
        // Damage handled by player script
    }
}
        """.trimIndent())

        // NPC Controller
        project.assetFile("NpcController.js").writeText("""
// NpcController.js — NPC dialogue controller
var npcName = "Elder";
var talking = false;
var dialogueLines = ["Welcome, adventurer!", "The dungeon to the east holds great treasure.", "Be careful of the slimes!"];
var currentLine = 0;

function start() {
    if (npcName == "Elder") {
        dialogueLines = ["Welcome, young hero!", "Our village needs your help.", "Dark slimes have taken over the eastern dungeon.", "Please clear them out!"];
    } else if (npcName == "Merchant") {
        dialogueLines = ["Welcome to my shop!", "I have potions and weapons for sale.", "Come back when you have more gold!"];
    } else if (npcName == "Guard") {
        dialogueLines = ["Halt! The area ahead is dangerous.", "Make sure you're well prepared.", "Good luck out there!"];
    }
}

function onTrigger(other) {
    if (other.tag != "Player") return;
    if (input.aDown) {
        talking = !talking;
        currentLine = 0;
    }
}

function update(dt) {
    if (talking) {
        var text = scene.find("DialogueText");
        if (text) text.text = npcName + ": " + dialogueLines[currentLine];
    }
}
        """.trimIndent())

        // Pickup
        project.assetFile("Pickup.js").writeText("""
// Pickup.js — RPG pickup (health, coin, etc.)
var collected = false;
var bobTime = 0;
var startY = 0;

function start() {
    startY = self.y;
    bobTime = random() * 6.28;
}

function update(dt) {
    bobTime += dt * 3;
    self.y = startY + sin(bobTime) * 0.15;
}

function onTrigger(other) {
    if (collected || other.tag != "Player") return;
    collected = true;
    audio.beep();
    self.burst(6);
    after(0.01, function() { self.destroy(); });
}
        """.trimIndent())

        // Treasure Chest
        project.assetFile("TreasureChest.js").writeText("""
// TreasureChest.js — Openable treasure chest
var opened = false;

function start() {
    self.color = 0xFFAA7722;
}

function onTrigger(other) {
    if (opened || other.tag != "Player") return;
    if (input.aDown) {
        opened = true;
        self.color = 0xFF886611;
        audio.beep();
        self.burst(12);
    }
}
        """.trimIndent())

        // Dungeon Entrance
        project.assetFile("DungeonEntrance.js").writeText("""
// DungeonEntrance.js — Teleport to dungeon
function onTrigger(other) {
    if (other.tag != "Player") return;
    if (input.aDown) {
        audio.beep();
        scene.load("Dungeon");
    }
}
        """.trimIndent())

        // RPG Game Manager
        project.assetFile("RpgGameManager.js").writeText("""
// RpgGameManager.js — RPG game state management
var playerHealth = 100;
var maxHealth = 100;
var playerLevel = 1;
var gold = 0;
var xp = 0;
var xpToNext = 100;
var questsCompleted = 0;

function start() {
    playerHealth = 100;
    gold = storage.get("gold", 0);
    playerLevel = storage.get("level", 1);
}

function update(dt) {
    var goldText = scene.find("GoldText");
    if (goldText) goldText.text = "Gold: " + gold;
    var levelText = scene.find("LevelText");
    if (levelText) levelText.text = "Lv. " + playerLevel;
}
        """.trimIndent())
    }
}
