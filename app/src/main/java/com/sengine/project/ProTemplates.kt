package com.sengine.project

import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.GrassField
import com.sengine.engine.core.Human
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.ParticleEmitter3D
import com.sengine.engine.core.ParticlePresets
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.Sun
import com.sengine.engine.core.Terrain
import com.sengine.engine.core.TextRenderer
import com.sengine.engine.core.UIPanel
import com.sengine.engine.core.Water

/**
 * The two new full game templates of the Professional upgrade:
 *  - "Sunny Valley 3D"      — third-person human adventure with terrain, grass,
 *                             water, a real sky texture, the Sun editor, day cycle
 *                             and 3D particles.
 *  - "Crystal Cavern 2D"    — a complete 2D particle platformer with UI panels,
 *                             torches, rain of sparks, enemies and a water hazard.
 */
object ProTemplates {

    private fun install(p: Project, vararg titles: String) {
        for (t in titles) {
            val item = AssetLibrary.items.firstOrNull { it.title == t } ?: continue
            try { item.install(p) } catch (_: Throwable) { /* textures need Android graphics; fine on JVM */ }
        }
    }

    private fun obj3(s: Scene, name: String, x: Float, y: Float, z: Float,
                     sx: Float = 1f, sy: Float = 1f, sz: Float = 1f, parent: GameObject? = null): GameObject {
        val go = s.create(name, parent)
        go.x = x; go.y = y; go.z = z; go.scaleX = sx; go.scaleY = sy; go.scaleZ = sz
        return go
    }

    private fun obj2(s: Scene, name: String, x: Float, y: Float, sx: Float = 1f, sy: Float = 1f): GameObject {
        val go = s.create(name)
        go.x = x; go.y = y; go.scaleX = sx; go.scaleY = sy
        return go
    }

    // ================================================================ SUNNY VALLEY 3D
    val sunnyValley = Templates.Template(
        "Sunny Valley 3D",
        "Full 3D adventure: walk a human through terrain, grass and a pond under a real sky. Collect 6 sun crystals!"
    ) { p ->
        install(p, "Real Sky 360°", "Grass Tile", "Wooden Crate", "Wood Planks", "Low-Poly Tree", "Rock", "Crystal", "Coin Pickup", "Jump")
        p.writeAsset("ValleyPlayer.js", """
// Sunny Valley hero. Move: joystick / WASD. Jump: A / Space.
// The Human component animates the walk cycle automatically.
var speed = 6, jump = 7.5;
function update(dt) {
    self.vx = input.axisX * speed;
    self.vz = -input.axisY * speed;
    if (input.axisX != 0 || input.axisY != 0)
        self.rotY = Math.atan2(input.axisX, input.axisY) * 180 / Math.PI;
    if (input.aDown && self.grounded) { self.vy = jump; audio.play("jump.wav"); }
    self.setWalking(Math.abs(self.vx) > 0.2 || Math.abs(self.vz) > 0.2);
    if (self.y < -20) scene.reload();
}
""".trimIndent())
        p.writeAsset("Crystal.js", """
// Spinning sun crystal pickup.
var baseY;
function start() { baseY = self.y; }
function update(dt) {
    self.rotY += 90 * dt;
    self.y = baseY + Math.sin(time.time * 2.5 + self.x) * 0.25;
}
function onTrigger(other) {
    if (other.tag != "Player") return;
    audio.play("coin.wav");
    events.emit("crystal", 1);
    var left = scene.count("Crystal") - 1;
    var label = scene.find("ScoreText");
    if (label) label.text = left > 0 ? "Sun Crystals: " + (6 - left) + " / 6" : "You restored the valley sun! You win!";
    if (left <= 0) world.setSunshine(2.2);
    self.destroy();
}
""".trimIndent())
        p.writeAsset("Campfire.js", """
// Keeps the fire crackling and occasionally pops sparks.
function start() { self.burstPreset("Fire", 30); }
""".trimIndent())

        val s = Scene("Main")
        s.fog = true; s.fogStart = 30f; s.fogEnd = 95f

        // camera with a real 360° sky texture
        val cam = obj3(s, "Main Camera", 0f, 6f, 11f)
        cam.rotX = -18f
        cam.add(Camera3D().also {
            it.follow = "Player"; it.offsetY = 5.5f; it.offsetZ = 10f
            it.skyMode = 1; it.skyTexture = "RealSky.png"
            it.skyTop = 0xFF2E6FD8.toInt(); it.skyHorizon = 0xFFCDEBFF.toInt()
            it.postFx = 3; it.postIntensity = 0.5f
        })

        // ---- Sun editor showcase: warm afternoon sun, slow day cycle available in the Inspector
        obj3(s, "Sun", 0f, 12f, 0f).add(Sun().also {
            it.sunshine = 1.35f; it.temperature = 5200f; it.elevation = 48f; it.azimuth = 35f
            it.sunSize = 0.045f; it.halo = 0.6f; it.shader = 1; it.animateDay = false
        })

        // ---- procedural terrain ground + invisible flat physics floor
        obj3(s, "Valley Terrain", 0f, 0f, 0f).add(Terrain().also {
            it.width = 70f; it.depth = 70f; it.height = 1.1f; it.segments = 56
            it.texture = "Grass.png"; it.tiling = 10f; it.color = 0xFF77B255.toInt()
        })
        val floor = obj3(s, "Ground Collider", 0f, -0.55f, 0f, 70f, 1f, 70f)
        floor.add(Collider3D())
        floor.add(Rigidbody3D().also { it.bodyType = 2 })

        // ---- grass + water systems
        obj3(s, "Meadow Grass", 0f, 0.06f, 0f).add(GrassField().also {
            it.blades = 2600; it.areaX = 46f; it.areaZ = 46f; it.bladeHeight = 0.65f
            it.windStrength = 0.3f; it.windSpeed = 1.8f
        })
        obj3(s, "Pond", 10f, 0.32f, 7f).add(Water().also {
            it.sizeX = 13f; it.sizeZ = 11f; it.waveHeight = 0.09f; it.opacity = 0.8f
        })

        // ---- the human hero (physics root + procedural body child)
        val player = obj3(s, "Player", 0f, 1.05f, 4f, 1f, 1f, 1f)
        player.tag = "Player"
        player.add(Collider3D().also { it.sizeY = 1.9f; it.centerY = 0f })
        player.add(Rigidbody3D().also { it.friction = 0.15f })
        player.add(ScriptComponent().also { it.script = "ValleyPlayer.js"; it.params = "speed=6, jump=7.5" })
        val body = obj3(s, "Body", 0f, -1.0f, 0f, 1f, 1f, 1f, parent = player)
        body.add(Human().also { it.shirtColor = 0xFFE4572E.toInt(); it.height = 1.85f })

        // ---- scenery: trees, rocks and a per-face textured crate house block
        for ((x, z) in listOf(-12f to -9f, 9f to -13f, -16f to 5f, 15f to 13f, -4f to -18f, 18f to -4f, -20f to -14f))
            obj3(s, "Tree", x, 0f, z, 2.4f, 2.4f, 2.4f).add(MeshRenderer().also { it.mesh = 8; it.model = "Tree.obj"; it.color = 0xFF5FA85B.toInt() })
        for ((x, z) in listOf(6f to -3f, -8f to -4f, 13f to 1f))
            obj3(s, "Rock", x, 0.25f, z, 1.4f, 1.4f, 1.4f).add(MeshRenderer().also { it.mesh = 8; it.model = "Rock.obj"; it.color = 0xFF8D8D8D.toInt() })
        val hut = obj3(s, "Hut Block", -7f, 1.25f, -11f, 5f, 2.5f, 5f)
        hut.add(MeshRenderer().also {
            it.mesh = 0; it.textureMode = 1
            it.textureTop = "Wood.png"; it.textureFront = "Crate.png"; it.textureBack = "Crate.png"
            it.textureLeft = "Wood.png"; it.textureRight = "Wood.png"; it.textureBottom = "Crate.png"
            it.tiling = 1f
        })
        hut.add(Collider3D())
        hut.add(Rigidbody3D().also { it.bodyType = 2 })
        for (i in 0 until 4) {
            val crate = obj3(s, "Crate", 4f + (i % 2) * 1.1f, 0.55f + (i / 2) * 1.1f, -7f)
            crate.add(MeshRenderer().also { it.mesh = 0; it.texture = "Crate.png"; it.color = 0xFFFFFFFF.toInt() })
            crate.add(Collider3D())
            crate.add(Rigidbody3D())
        }

        // ---- pickups + particles
        val crystalPos = listOf(5f to -5f, -10f to 3f, 12f to 10f, -14f to -12f, 2f to -16f, 16f to -10f)
        for ((x, z) in crystalPos) {
            val c = obj3(s, "Sun Crystal", x, 1.3f, z, 0.8f, 1.1f, 0.8f)
            c.tag = "Crystal"
            c.add(MeshRenderer().also { it.mesh = 8; it.model = "Crystal.obj"; it.color = 0xFFFFD54F.toInt(); it.emission = 0.5f })
            c.add(Collider3D().also { it.shape = 1; it.isTrigger = true })
            c.add(ScriptComponent().also { it.script = "Crystal.js" })
        }
        val fire = obj3(s, "Campfire", -3f, 0.5f, -6f)
        fire.add(ParticleEmitter3D().also { ParticlePresets.apply3D(it, ParticlePresets.indexOf("Fire")); it.rate = 60f })
        fire.add(ScriptComponent().also { it.script = "Campfire.js" })
        obj3(s, "Pond Sparkles", 10f, 0.7f, 7f).add(ParticleEmitter3D().also {
            ParticlePresets.apply3D(it, ParticlePresets.indexOf("Bubbles")); it.shape = 2; it.shapeSize = 4f; it.rate = 25f
        })
        obj3(s, "Fireflies", 0f, 2f, -8f).add(ParticleEmitter3D().also {
            ParticlePresets.apply3D(it, ParticlePresets.indexOf("Stars")); it.shape = 2; it.shapeSize = 9f; it.rate = 18f
        })

        // ---- UI: panel + score text
        val panel = obj2(s, "Score Panel", 0f, 4.35f, 7.2f, 1.05f)
        panel.order = 90
        panel.add(UIPanel().also { it.text = ""; it.color = 0xB3101C2A.toInt() })
        val score = obj2(s, "ScoreText", 0f, 4.35f)
        score.order = 100
        score.add(TextRenderer().also { it.text = "Sun Crystals: 0 / 6"; it.size = 0.5f; it.bold = true; it.screenSpace = true })
        val hint = obj2(s, "HintText", 0f, -4.3f)
        hint.order = 100
        hint.add(TextRenderer().also { it.text = "Joystick: move • A: jump • Find 6 sun crystals"; it.size = 0.34f; it.screenSpace = true })

        p.saveScene(s)
        p.startScene = "Main"
        p.orientation = 0
    }

    // ================================================================ CRYSTAL CAVERN 2D
    val crystalCavern = Templates.Template(
        "Crystal Cavern 2D",
        "Complete 2D particle platformer: torches, spark rain, patrol slimes, water hazard and 8 crystals to collect."
    ) { p ->
        install(p, "Grass Tile", "Brick Wall", "Coin Pickup", "Jump", "Hit", "Hero", "Soft Particle")
        p.writeAsset("CavernPlayer.js", """
// Cavern hero. Params: speed, jump
var speed = 6, jump = 11, crystals = 0, hp = 3;
function update(dt) {
    self.vx = input.axisX * speed;
    if (input.axisX < -0.1) self.flipX = true; else if (input.axisX > 0.1) self.flipX = false;
    if (input.aDown && self.grounded) { self.vy = jump; audio.play("jump.wav"); }
    if (self.y < -14) scene.reload();
}
function onTrigger(other) {
    if (other.tag == "Crystal") {
        crystals++;
        var fx = scene.spawn("CollectFX", other.worldX, other.worldY);
        if (fx) { fx.burstPreset("Magic Trail", 26); after(1.2, function () { fx.destroy(); }); }
        other.destroy(); audio.play("coin.wav");
        events.emit("crystal", crystals);
        var label = scene.find("ScoreText");
        label.text = scene.count("Crystal") <= 1 ? "You found every crystal! You win!" : "Crystals: " + crystals + " / 8";
        if (scene.count("Crystal") <= 1) { game.timeScale = 0.35; after(1.2, function () { game.timeScale = 1; }); }
    }
    if (other.tag == "Hazard") scene.reload();
}
function hurt(n) {
    hp -= n || 1; scene.shake(0.5); audio.play("hit.wav");
    if (hp <= 0) scene.reload();
    var h = scene.find("HealthText"); if (h) h.text = "HP: " + hp;
}
""".trimIndent())
        p.writeAsset("Patrol.js", """
// Back-and-forth patrol enemy. Params: distance, speed
var distance = 3, speed = 1.6, startX = 0, dir = 1;
function start() { startX = self.x; }
function update(dt) {
    self.x += dir * speed * dt;
    if (self.x > startX + distance) dir = -1;
    if (self.x < startX - distance) dir = 1;
    self.flipX = dir < 0;
}
function onCollision(other) { if (other.tag == "Player") other.send("hurt", 1); }
""".trimIndent())
        p.writeAsset("Bob.js", """
var baseY;
function start() { baseY = self.y; }
function update(dt) { self.y = baseY + Math.sin(time.time * 3 + self.x * 1.7) * 0.18; self.rotation += 60 * dt; }
""".trimIndent())

        val s = Scene("Main")
        val cam = obj2(s, "Main Camera", 0f, 0f)
        cam.add(Camera2D().also { it.size = 6f; it.background = 0xFF10182B.toInt(); it.follow = "Player"; it.smoothing = 5f })

        // HUD panels (the panel adding system)
        val hp1 = obj2(s, "Score Panel", 0f, 4.5f, 7.6f, 1.0f); hp1.order = 90
        hp1.add(UIPanel().also { it.color = 0xB3101C2A.toInt(); it.borderColor = 0xFF7C4DFF.toInt() })
        val score = obj2(s, "ScoreText", -1.6f, 4.5f); score.order = 100
        score.add(TextRenderer().also { it.text = "Crystals: 0 / 8"; it.size = 0.44f; it.bold = true; it.screenSpace = true })
        val health = obj2(s, "HealthText", 2.4f, 4.5f); health.order = 100
        health.add(TextRenderer().also { it.text = "HP: 3"; it.size = 0.44f; it.bold = true; it.screenSpace = true })

        fun platform(x: Float, y: Float, w: Float, tex: String = "Brick.png") {
            val vis = obj2(s, "Platform", x, y, w, 1f); vis.order = -1
            vis.add(SpriteRenderer().also { it.texture = tex })
            val col = obj2(s, "Platform Collider", x, y, w, 1f)
            col.add(Collider2D())
        }
        platform(0f, -3.5f, 26f, "Grass.png")
        platform(6f, -0.5f, 4f); platform(-6f, 0.5f, 4f); platform(11f, 2f, 4f)
        platform(-11f, 2.5f, 4f); platform(2f, 3.2f, 3f)

        // water hazard pool (bubbles + reload trigger)
        val water = obj2(s, "Water Pool", 8.5f, -4.6f, 5f, 0.8f); water.order = 2
        water.add(SpriteRenderer().also { it.color = 0x8033B5E5.toInt() })
        val hazard = obj2(s, "Hazard Zone", 8.5f, -4.4f, 5f, 1f); hazard.tag = "Hazard"
        hazard.add(Collider2D().also { it.isTrigger = true })
        obj2(s, "Pool Bubbles", 8.5f, -4.4f).add(ParticleEmitter().also {
            ParticlePresets.apply2D(it, ParticlePresets.indexOf("Bubbles")); it.rate = 22f; it.texture = "SoftDot.png"
        })

        // hero
        val hero = obj2(s, "Player", -9f, -2f, 0.9f, 0.9f); hero.tag = "Player"; hero.order = 10
        hero.add(SpriteRenderer().also { it.texture = "Hero.png"; it.color = 0xFFFFFFFF.toInt() })
        hero.add(Collider2D().also { it.width = 0.65f; it.height = 0.9f })
        hero.add(Rigidbody2D().also { it.friction = 0f })
        hero.add(ScriptComponent().also { it.script = "CavernPlayer.js"; it.params = "speed=6, jump=11" })

        // crystals + patrol enemies
        for ((x, y) in listOf(6f to 0.6f, -6f to 1.6f, 11f to 3.1f, -11f to 3.6f, 2f to 4.3f, 9f to -2.2f, -3f to -2.2f, 0f to 1.8f)) {
            val c = obj2(s, "Crystal", x, y, 0.55f, 0.55f); c.tag = "Crystal"; c.order = 5
            c.add(SpriteRenderer().also { it.color = 0xFFB388FF.toInt(); it.shape = 2 })
            c.add(Collider2D().also { it.shape = 1; it.isTrigger = true })
            c.add(ScriptComponent().also { it.script = "Bob.js" })
        }
        for ((x, y) in listOf(3f to -2.4f, -8f to -2.4f)) {
            val e = obj2(s, "Slime", x, y, 0.85f, 0.7f); e.tag = "Enemy"; e.order = 8
            e.add(SpriteRenderer().also { it.color = 0xFF69F0AE.toInt(); it.shape = 1 })
            e.add(Collider2D())
            e.add(Rigidbody2D().also { it.bodyType = 1 })
            e.add(ScriptComponent().also { it.script = "Patrol.js"; it.params = "distance=2.5, speed=1.6" })
        }

        // ---- the 2D particle showcase: fire torches, spark rain, dust, magic portal
        for (x in listOf(-12f, 0f, 12f)) {
            val torch = obj2(s, "Torch", x, -2.2f); torch.order = 6
            torch.add(ParticleEmitter().also {
                ParticlePresets.apply2D(it, ParticlePresets.indexOf("Fire")); it.texture = "SoftDot.png"; it.rate = 55f
            })
        }
        obj2(s, "Spark Rain", 0f, 5.5f).add(ParticleEmitter().also {
            ParticlePresets.apply2D(it, ParticlePresets.indexOf("Sparks")); it.spread = 160f; it.rate = 30f
        })
        obj2(s, "Cavern Dust", -4f, 2f).add(ParticleEmitter().also {
            ParticlePresets.apply2D(it, ParticlePresets.indexOf("Dust")); it.rate = 14f
        })
        val portal = obj2(s, "Magic Portal", 13.5f, -2.2f, 1.2f, 1.6f); portal.order = 4
        portal.add(SpriteRenderer().also { it.color = 0xFF7C4DFF.toInt(); it.shape = 1 })
        obj2(s, "Portal Magic", 13.5f, -2.2f).add(ParticleEmitter().also {
            ParticlePresets.apply2D(it, ParticlePresets.indexOf("Magic Trail")); it.rate = 45f; it.texture = "SoftDot.png"
        })

        // inactive collect-effect template (spawned by the player script)
        val fx = obj2(s, "CollectFX", 0f, 0f); fx.active = false
        fx.add(ParticleEmitter().also { it.emitting = false; it.texture = "SoftDot.png" })

        p.saveScene(s)
        p.startScene = "Main"
        p.orientation = 0
    }
}
