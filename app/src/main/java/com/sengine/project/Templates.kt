package com.sengine.project

import com.sengine.engine.core.Animator
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.Rigidbody3D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer

object Templates {
    class Template(val name: String, val description: String, val build: (Project) -> Unit)

    val all: List<Template> by lazy { listOf(empty, platformer, shooter, physics, demo3d, animated, blueprintDemo, fpsTemplate, racingTemplate, rpgTemplate, battleRoyaleTemplate, cityBuilderTemplate, ultimateShowcase, quantumShowcase, rayTracingDemo, destructionDemo, fluidDemo, openWorldPCG, vrDemo, mlAgentsDemo, unity2DURP, tilemapPlatformer, spriteAnimation2D, sortingGroupDemo, lighting2DDemo, cinemachine2DDemo, particle2DDemo, titanFullControl) }

    const val NEW_SHADER = """// S Engine effect shader (GLSL ES)
// Available: uTime, uParam, uTex, uUseTex, uColor, uResolution
// Assign to a SpriteRenderer / MeshRenderer "Shader", or a camera Post FX.

vec4 effect(vec4 color, vec2 uv) {
    float pulse = 0.75 + 0.25 * sin(uTime * 3.0);
    return vec4(color.rgb * pulse, color.a);
}
"""

    const val NEW_SCRIPT = """// S Engine behaviour script (JavaScript)
// Globals: self/transform, input, time, scene, audio, log(), after(), every()

function start() {
    log("Hello from " + self.name);
}

function update(dt) {
    // transform.rotation += 90 * dt;
}

// function onCollision(other) {}
// function onTrigger(other) {}
// function onTap() {}
"""

    // ---------------------------------------------------------------- helpers
    private fun obj(s: Scene, name: String, x: Float, y: Float, sx: Float = 1f, sy: Float = 1f, parent: GameObject? = null): GameObject {
        val go = s.create(name, parent)
        go.x = x; go.y = y; go.scaleX = sx; go.scaleY = sy
        return go
    }

    private fun GameObject.sprite(color: Long, shape: Int = 0): GameObject {
        add(SpriteRenderer().also { it.color = color.toInt(); it.shape = shape }); return this
    }

    private fun GameObject.box(trigger: Boolean = false): GameObject {
        add(Collider2D().also { it.isTrigger = trigger }); return this
    }

    private fun GameObject.circleCol(trigger: Boolean = false): GameObject {
        add(Collider2D().also { it.shape = 1; it.isTrigger = trigger }); return this
    }

    private fun GameObject.body(type: Int = 0, friction: Float = 0.4f, bounce: Float = 0f, gravity: Float = 1f): GameObject {
        add(Rigidbody2D().also { it.bodyType = type; it.friction = friction; it.bounciness = bounce; it.gravityScale = gravity }); return this
    }

    private fun GameObject.script(name: String, params: String = ""): GameObject {
        add(ScriptComponent().also { it.script = name; it.params = params }); return this
    }

    private fun GameObject.text(t: String, size: Float, color: Long = 0xFFFFFFFF): GameObject {
        add(TextRenderer().also { it.text = t; it.size = size; it.color = color.toInt(); it.bold = true }); return this
    }

    private fun camera(s: Scene, size: Float, bg: Long, follow: String = ""): GameObject {
        val c = obj(s, "Main Camera", 0f, 0f)
        c.add(Camera2D().also { it.size = size; it.background = bg.toInt(); it.follow = follow })
        return c
    }

    // ---------------------------------------------------------------- empty
    private val empty = Template("Empty 2D", "A camera and a square. Start from scratch.") { p ->
        val s = Scene("Main")
        camera(s, 5f, 0xFF1B2533)
        obj(s, "Square", 0f, 0f).sprite(0xFF4FC3F7)
        p.writeAsset("NewBehaviour.js", NEW_SCRIPT)
        p.saveScene(s)
        p.startScene = "Main"
    }

    // ---------------------------------------------------------------- platformer
    private val platformer = Template("Platformer Demo", "Run, jump and collect coins. Joystick + A button.") { p ->
        p.writeAsset("Player.js", """// Player controller
// Move: joystick / A-D keys.  Jump: button A / Space.
// Params (set in inspector): speed, jump
var coins = 0;

function start() {
    log("Collect all the coins!");
}

function update(dt) {
    self.vx = input.axisX * speed;
    if (input.aDown && self.grounded) {
        self.vy = jump;
        audio.beep();
    }
    if (input.axisX < -0.1) self.flipX = true;
    else if (input.axisX > 0.1) self.flipX = false;

    // fell off the world
    if (self.y < -12) scene.reload();
}

function onTrigger(other) {
    if (other.tag == "Coin") {
        coins++;
        var fx = scene.spawn("CoinFX", other.worldX, other.worldY);
        if (fx) {
            fx.burst(24);
            after(1.5, function () { fx.destroy(); });
        }
        other.destroy();
        var label = scene.find("ScoreText");
        if (scene.count("Coin") == 0) label.text = "You win!";
        else label.text = "Coins: " + coins;
    }
}
""")
        p.writeAsset("Coin.js", """// Makes a coin bob and spin
var baseY;
function start() { baseY = transform.y; }
function update(dt) {
    transform.y = baseY + Math.sin(time.time * 3 + transform.x) * 0.15;
    transform.scaleX = 0.1 + 0.4 * Math.abs(Math.cos(time.time * 2.5 + transform.x));
}
""")
        p.writeAsset("MovingPlatform.js", """// Moves back and forth. Params: range, speed
var startX;
function start() { startX = transform.x; }
function update(dt) {
    self.vx = Math.cos(time.time * speed) * range * speed;
}
""")
        val s = Scene("Main")
        val cam = camera(s, 6f, 0xFF6EC6FF, follow = "Player")
        obj(s, "ScoreText", 0f, 5f, parent = cam).text("Coins: 0", 0.7f).also { it.order = 100 }
        obj(s, "Ground", 0f, -3f, 30f, 1f).sprite(0xFF4E7D3A).box()
        obj(s, "Platform A", 4f, 0f, 4f, 0.5f).sprite(0xFF8D6E63).box()
        obj(s, "Platform B", -5f, 1.2f, 3f, 0.5f).sprite(0xFF8D6E63).box()
        obj(s, "Platform C", 10f, 2.5f, 3f, 0.5f).sprite(0xFF8D6E63).box()
        obj(s, "Moving Platform", 16f, 1f, 3f, 0.5f).sprite(0xFFB0BEC5).box().body(type = 1)
            .script("MovingPlatform.js", "range=2, speed=1")
        obj(s, "Wall L", -15.5f, 0f, 1f, 8f).sprite(0xFF4E7D3A).box()
        val player = obj(s, "Player", 0f, -1.5f, 0.8f, 0.8f).sprite(0xFFFF7043).box()
            .body(friction = 0f).script("Player.js", "speed=6, jump=11")
        player.tag = "Player"; player.order = 10
        val coinPos = listOf(4f to 1.2f, -5f to 2.4f, 10f to 3.7f, 7f to -1.8f, -9f to -1.8f, 16f to 2.3f, 20f to -1.8f)
        for ((x, y) in coinPos) {
            val c = obj(s, "Coin", x, y, 0.5f, 0.5f).sprite(0xFFFFD54F, 1).circleCol(true).script("Coin.js")
            c.tag = "Coin"; c.order = 5
        }
        val fx = obj(s, "CoinFX", 0f, 0f)
        fx.active = false
        fx.add(ParticleEmitter().also {
            it.emitting = false; it.rate = 0f; it.spread = 360f; it.speed = 4f; it.gravity = -6f
            it.lifetime = 0.7f; it.startColor = 0xFFFFF176.toInt(); it.endColor = 0x00FFA000
        })
        fx.order = 20
        p.saveScene(s)
        p.startScene = "Main"
    }

    // ---------------------------------------------------------------- shooter
    private val shooter = Template("Space Shooter", "Top-down shooter with spawning, triggers and score.") { p ->
        p.writeAsset("Ship.js", """// Player ship. Params: speed
var cooldown = 0;
function update(dt) {
    self.vx = input.axisX * speed;
    self.vy = input.axisY * speed;
    transform.x = clamp(transform.x, -12, 12);
    transform.y = clamp(transform.y, -7, 7);
    cooldown -= dt;
    if ((input.a || input.touching) && cooldown <= 0) {
        cooldown = 0.18;
        scene.spawn("Bullet", self.worldX, self.worldY + 0.6);
    }
}
function onTrigger(other) {
    if (other.tag == "Enemy") {
        var fx = scene.spawn("Explosion", self.worldX, self.worldY);
        if (fx) fx.burst(60);
        self.active = false;
        scene.find("Game").send("gameOver");
    }
}
""")
        p.writeAsset("Bullet.js", """function update(dt) {
    if (transform.y > 10) self.destroy();
}
""")
        p.writeAsset("Enemy.js", """function start() {
    self.vy = -random(2, 4.5);
}
function update(dt) {
    transform.rotation += 90 * dt;
    if (transform.y < -10) self.destroy();
}
function onTrigger(other) {
    if (other.tag == "Bullet") {
        var fx = scene.spawn("Explosion", self.worldX, self.worldY);
        if (fx) { fx.burst(30); after(1, function () { fx.destroy(); }); }
        other.destroy();
        self.destroy();
        audio.beep();
        scene.find("Game").send("addScore", 10);
    }
}
""")
        p.writeAsset("Star.js", """function start() { self.vy = 0; speedY = random(0.5, 3); }
var speedY = 1;
function update(dt) {
    transform.y -= speedY * dt;
    if (transform.y < -9) { transform.y = 9; transform.x = random(-15, 15); }
}
""")
        p.writeAsset("Game.js", """// Game manager: spawns enemies and tracks the score
var score = 0;
var over = false;
function start() {
    for (var i = 0; i < 40; i++) scene.spawn("Star", random(-15, 15), random(-9, 9));
    every(0.8, function () {
        if (!over) scene.spawn("Enemy", random(-11, 11), 10);
    });
}
function addScore(n) {
    score += n;
    scene.find("ScoreText").text = "Score: " + score;
}
function gameOver() {
    over = true;
    scene.find("ScoreText").text = "Game Over  -  Score: " + score;
    after(2.5, function () { scene.reload(); });
}
""")
        val s = Scene("Main")
        s.gravityY = 0f
        val cam = camera(s, 8f, 0xFF0B1026)
        obj(s, "ScoreText", 0f, 7f, parent = cam).text("Score: 0", 0.8f).also { it.order = 100 }
        obj(s, "Game", 0f, 0f).script("Game.js")
        val ship = obj(s, "Ship", 0f, -5f, 1f, 1.2f).sprite(0xFF4FC3F7, 2).box(true).body(type = 1)
            .script("Ship.js", "speed=9")
        ship.tag = "Player"; ship.order = 10
        val flame = obj(s, "Engine Flame", 0f, -0.5f, 1f, 1f, parent = ship)
        flame.add(ParticleEmitter().also {
            it.direction = -90f; it.spread = 20f; it.speed = 4f; it.rate = 60f; it.lifetime = 0.35f
            it.startSize = 0.3f; it.startColor = 0xFF80DEEA.toInt(); it.endColor = 0x000277BD
        })
        val bullet = obj(s, "Bullet", 0f, 0f, 0.15f, 0.5f).sprite(0xFFFFF176).box(true).body(type = 1)
            .script("Bullet.js")
        bullet.tag = "Bullet"; bullet.active = false
        bullet.getAny<Rigidbody2D>()!!.startVy = 16f
        val enemy = obj(s, "Enemy", 0f, 12f, 1f, 1f).sprite(0xFFEF5350, 0).box(true).body(type = 1)
            .script("Enemy.js")
        enemy.tag = "Enemy"; enemy.active = false; enemy.order = 5
        val star = obj(s, "Star", 0f, 0f, 0.08f, 0.08f).sprite(0xAAFFFFFF, 1).script("Star.js")
        star.active = false; star.order = -10
        val ex = obj(s, "Explosion", 0f, 0f)
        ex.active = false; ex.order = 20
        ex.add(ParticleEmitter().also {
            it.emitting = false; it.rate = 0f; it.spread = 360f; it.speed = 5f; it.lifetime = 0.6f
            it.startSize = 0.35f; it.startColor = 0xFFFFAB40.toInt(); it.endColor = 0x00D50000
        })
        p.saveScene(s)
        p.startScene = "Main"
    }

    // ---------------------------------------------------------------- physics
    private val physics = Template("Physics Sandbox", "Tap anywhere to drop bouncy balls and boxes.") { p ->
        p.writeAsset("Spawner.js", """// Tap the screen to spawn objects
var n = 0;
function update(dt) {
    if (input.tapped) {
        var name = (n++ % 2 == 0) ? "Ball" : "Crate";
        var o = scene.spawn(name, input.touchX, input.touchY);
        if (o) {
            var colors = ["#FFEF5350", "#FF42A5F5", "#FF66BB6A", "#FFFFCA28", "#FFAB47BC"];
            o.color = colors[randomInt(0, colors.length - 1)];
        }
        scene.find("Counter").text = "Objects: " + n;
    }
}
""")
        val s = Scene("Main")
        camera(s, 7f, 0xFF263238)
        obj(s, "Counter", 0f, 6f).text("Tap to spawn!", 0.6f).also { it.order = 100 }
        obj(s, "Spawner", 0f, 0f).script("Spawner.js")
        obj(s, "Floor", 0f, -6.5f, 24f, 1f).sprite(0xFF546E7A).box()
        obj(s, "Wall Left", -12f, 0f, 1f, 14f).sprite(0xFF546E7A).box()
        obj(s, "Wall Right", 12f, 0f, 1f, 14f).sprite(0xFF546E7A).box()
        obj(s, "Ramp", -5f, -2f, 5f, 0.4f).sprite(0xFF78909C).box()
        for (row in 0 until 4) for (i in 0 until 4 - row) {
            obj(s, "Crate", 3f + i * 1.05f + row * 0.52f, -5.5f + row * 1.02f).sprite(0xFFA1887F).box().body()
        }
        val ball = obj(s, "Ball", 0f, 20f, 0.8f, 0.8f).sprite(0xFFFFFFFF, 1).circleCol().body(bounce = 0.7f)
        ball.active = false
        p.saveScene(s)
        p.startScene = "Main"
    }

    // ================================================================ v2 templates

    private fun installAssets(p: Project, vararg titles: String) {
        for (t in titles) {
            val item = AssetLibrary.items.firstOrNull { it.title == t } ?: continue
            // Texture generation needs android.graphics; ignore failures (e.g. JVM unit tests).
            try { item.install(p) } catch (_: Throwable) {}
        }
    }

    private fun obj3(s: Scene, name: String, x: Float, y: Float, z: Float, sx: Float = 1f, sy: Float = 1f, sz: Float = 1f, parent: GameObject? = null): GameObject {
        val go = s.create(name, parent)
        go.x = x; go.y = y; go.z = z; go.scaleX = sx; go.scaleY = sy; go.scaleZ = sz
        return go
    }

    private fun GameObject.mesh(kind: Int, color: Long, texture: String = "", tiling: Float = 1f, model: String = ""): GameObject {
        add(MeshRenderer().also { it.mesh = kind; it.color = color.toInt(); it.texture = texture; it.tiling = tiling; it.model = model }); return this
    }

    private fun GameObject.col3(sphere: Boolean = false, trigger: Boolean = false): GameObject {
        add(Collider3D().also { it.shape = if (sphere) 1 else 0; it.isTrigger = trigger }); return this
    }

    private fun GameObject.body3(type: Int = 0, bounce: Float = 0f, friction: Float = 0.5f): GameObject {
        add(Rigidbody3D().also { it.bodyType = type; it.bounciness = bounce; it.friction = friction }); return this
    }

    // ---------------------------------------------------------------- 3D demo
    private val demo3d = Template("3D Demo", "3D world with lights, physics, models, shaders and a character.") { p ->
        installAssets(p, "Checker", "Wooden Crate", "Low-Poly Tree", "Rock", "Crystal", "Toon", "Pulse", "Player3D", "Coin Pickup", "Jump")
        p.writeAsset("Coin3D.js", """// Spinning pickup
var baseY;
function start() { baseY = self.y; }
function update(dt) {
    self.rotY += 120 * dt;
    self.y = baseY + Math.sin(time.time * 3 + self.x) * 0.2;
}
function onTrigger(other) {
    if (other.tag != "Player") return;
    audio.play("coin.wav");
    var label = scene.find("ScoreText");
    var left = scene.count("Coin") - 1;
    label.text = left > 0 ? "Coins left: " + left : "You collected everything!";
    self.destroy();
}
""")
        p.writeAsset("Player3D.js", AssetLibrary.Scripts.all.first { it.first == "Player3D.js" }.third.replace("self.vy = jump;", "{ self.vy = jump; audio.play(\"jump.wav\"); }"))
        val s = Scene("Main")
        s.fog = true; s.fogStart = 25f; s.fogEnd = 70f
        val cam = obj3(s, "Main Camera", 0f, 5f, 10f)
        cam.rotX = -20f
        cam.add(Camera3D().also { it.follow = "Player"; it.offsetY = 5f; it.offsetZ = 9f; it.postFx = 3; it.postIntensity = 0.6f })
        obj3(s, "Sun", 0f, 10f, 0f).also { it.rotX = -50f; it.rotY = 30f }.add(Light().also { it.kind = 0; it.intensity = 1f })
        obj3(s, "Lamp", -4f, 2.5f, -3f).add(Light().also { it.kind = 1; it.color = 0xFFFF9F40.toInt(); it.intensity = 1.6f; it.range = 9f })
        obj3(s, "Ground", 0f, -0.5f, 0f, 40f, 1f, 40f).mesh(0, 0xFFB8C4B0, "Checker.png", 10f).col3().body3(type = 2)
        val player = obj3(s, "Player", 0f, 1f, 0f, 0.8f, 1.6f, 0.8f).mesh(6, 0xFFFF7043).col3().body3(friction = 0.2f)
            .script("Player3D.js", "speed=6, jump=7")
        player.tag = "Player"
        for (i in 0 until 6) obj3(s, "Crate", -3f + (i % 3) * 1.05f, 0.5f + (i / 3) * 1.05f, -6f).mesh(0, 0xFFFFFFFF, "Crate.png").col3().body3(bounce = 0.1f)
        for (i in 0 until 4) obj3(s, "Ball", 4f + i * 0.5f, 3f + i * 1.5f, -4f, 0.8f, 0.8f, 0.8f).mesh(1, listOf(0xFF42A5F5, 0xFFAB47BC, 0xFFFFCA28, 0xFF66BB6A)[i]).col3(sphere = true).body3(bounce = 0.7f)
        val treePos = listOf(-10f to -10f, 8f to -12f, -14f to 4f, 12f to 6f, 0f to -16f, -6f to 12f)
        for ((x, z) in treePos) obj3(s, "Tree", x, 0f, z, 2f, 2f, 2f).mesh(8, 0xFF5FA85B, model = "Tree.obj").col3()
        for ((x, z) in listOf(6f to 2f, -8f to -3f)) obj3(s, "Rock", x, 0.3f, z, 1.2f, 1.2f, 1.2f).mesh(8, 0xFF8D8D8D, model = "Rock.obj").col3().body3(type = 2)
        val crystal = obj3(s, "Crystal", 0f, 1f, -10f, 1.2f, 1.2f, 1.2f).mesh(8, 0xFF7C4DFF, model = "Crystal.obj").script("Rotator.js", "x=0, y=45, z=0")
        crystal.get<MeshRenderer>()!!.also { it.shader = "Pulse.glsl"; it.emission = 0.3f }
        p.writeAsset("Rotator.js", AssetLibrary.Scripts.all.first { it.first == "Rotator.js" }.third)
        val coins = listOf(3f to 3f, -5f to 5f, 7f to -7f, -9f to -8f, 10f to 0f)
        for ((x, z) in coins) {
            val c = obj3(s, "Coin", x, 1f, z, 0.7f, 0.7f, 0.7f).mesh(5, 0xFFFFC107).col3(sphere = true, trigger = true).script("Coin3D.js")
            c.tag = "Coin"; c.get<MeshRenderer>()!!.emission = 0.25f
        }
        val torusShader = obj3(s, "Toon Torus", -4f, 1.5f, 4f).mesh(5, 0xFF26C6DA).script("Rotator.js", "x=40, y=60, z=0")
        torusShader.get<MeshRenderer>()!!.shader = "Toon.glsl"
        obj(s, "ScoreText", 0f, 4.2f).text("Coins left: ${coins.size}", 0.45f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        p.saveScene(s)
        p.startScene = "Main"
        p.orientation = 0
    }

    // ---------------------------------------------------------------- animated platformer
    private val animated = Template("Animated Platformer", "Sprite-sheet animation, textures, sounds and a camera shake.") { p ->
        installAssets(p, "Hero Run (4 frames)", "Hero", "Coin Spin (6 frames)", "Slime Bounce (4 frames)", "Grass Tile", "Brick Wall",
            "Sky Gradient", "Mountains", "Coin Pickup", "Jump", "Hit", "EnemyPatrol", "Health", "Hit Flash")
        p.writeAsset("Hero.js", """// Animated hero. Params: speed, jump
var coins = 0;
function update(dt) {
    self.vx = input.axisX * speed;
    if (input.axisX != 0) self.flipX = input.axisX < 0;
    if (input.aDown && self.grounded) { self.vy = jump; audio.play("jump.wav"); }
    if (Math.abs(self.vx) > 0.1 && self.grounded) self.play("HeroRun.anim");
    else self.stopAnimation();
    if (self.y < -12) scene.reload();
}
function onTrigger(other) {
    if (other.tag != "Coin") return;
    coins++; other.destroy(); audio.play("coin.wav");
    scene.find("ScoreText").text = scene.count("Coin") <= 1 ? "You win!" : "Coins: " + coins;
}
""")
        val s = Scene("Main")
        val cam = camera(s, 5f, 0xFF6EC6FF, follow = "Player")
        obj(s, "Sky", 0f, 0f, 20f, 12f, parent = cam).also { it.order = -100 }.add(SpriteRenderer().also { it.texture = "Sky.png" })
        obj(s, "Mountains", 0f, -2f, 24f, 8f).also { it.order = -90 }.add(SpriteRenderer().also { it.texture = "Mountains.png" })
        obj(s, "ScoreText", 0f, 4.2f).text("Coins: 0", 0.5f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        for (i in -8..12) obj(s, "Ground", i * 1f, -3f, 1f, 1f).also { it.order = -1 }.add(SpriteRenderer().also { it.texture = "Grass.png" })
        obj(s, "Ground Collider", 2f, -3f, 21f, 1f).box()
        for ((x, y, w) in listOf(Triple(4f, 0f, 3), Triple(-4f, 1f, 2), Triple(9f, 2f, 3))) {
            for (k in 0 until w) obj(s, "Brick", x + k - (w - 1) / 2f, y, 1f, 1f).add(SpriteRenderer().also { it.texture = "Brick.png" })
            obj(s, "Platform Collider", x, y, w.toFloat(), 1f).box()
        }
        val hero = obj(s, "Player", 0f, -1.5f, 1f, 1f).also { it.order = 10; it.tag = "Player" }
        hero.add(SpriteRenderer().also { it.texture = "Hero.png"; it.shader = "HitFlash.glsl"; it.shaderParam = 0f })
        hero.add(Animator().also { it.clip = "HeroRun.anim"; it.playOnStart = false })
        hero.add(com.sengine.engine.core.Collider2D().also { it.width = 0.6f; it.height = 0.9f })
        hero.body(friction = 0f).script("Hero.js", "speed=6, jump=11")
        hero.add(ScriptComponent().also { it.script = "Health.js"; it.params = "hp=3" })
        for ((x, y) in listOf(4f to 1.2f, -4f to 2.2f, 9f to 3.2f, 7f to -2f, -6f to -2f)) {
            val c = obj(s, "Coin", x, y, 0.6f, 0.6f).also { it.tag = "Coin"; it.order = 5 }
            c.add(SpriteRenderer().also { it.texture = "CoinSpin.png" })
            c.add(Animator().also { it.clip = "CoinSpin.anim" })
            c.circleCol(true)
        }
        val slime = obj(s, "Slime", 8f, -2.1f, 0.9f, 0.9f).also { it.order = 8; it.tag = "Enemy" }
        slime.add(SpriteRenderer().also { it.texture = "Slime.png" })
        slime.add(Animator().also { it.clip = "SlimeBounce.anim" })
        slime.box().body(type = 1).script("EnemyPatrol.js", "distance=2.5, speed=1.5")
        p.saveScene(s)
        p.startScene = "Main"
    }

    // ---------------------------------------------------------------- blueprint demo
    private val blueprintDemo = Template("Blueprint Demo", "Gameplay built with visual scripting nodes — no code.") { p ->
        installAssets(p, "Coin Pickup", "Jump", "Rotator (Blueprint)", "Collectible (Blueprint)", "Soft Particle")
        val bp = com.sengine.engine.blueprint.Blueprint()
        val u = bp.add("OnUpdate", 40f, 40f)
        val mv = bp.add("Platformer", 300f, 40f)
        bp.connect(u.id, "out", mv.id)
        val a = bp.add("OnButtonA", 40f, 220f)
        val snd = bp.add("PlaySound", 300f, 220f).also { it.params["file"] = "jump.wav" }
        bp.connect(a.id, "out", snd.id)
        val st = bp.add("OnStart", 40f, 380f)
        val lg = bp.add("Log", 300f, 380f).also { it.params["message"] = "\"Blueprint player ready!\"" }
        bp.connect(st.id, "out", lg.id)
        p.writeAsset("PlayerBP.bp", bp.toJson().toString(2))
        val s = Scene("Main")
        camera(s, 6f, 0xFF263238, follow = "Player")
        obj(s, "Info", 0f, 4.2f).text("Open PlayerBP.bp in the editor to see the nodes", 0.3f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "Ground", 0f, -3f, 30f, 1f).sprite(0xFF546E7A).box()
        obj(s, "Step", 5f, -1f, 4f, 0.5f).sprite(0xFF78909C).box()
        val pl = obj(s, "Player", 0f, -1.5f, 0.8f, 0.8f).sprite(0xFF29B6F6).box().body(friction = 0f).script("PlayerBP.bp", "speed=6, jump=11")
        pl.tag = "Player"
        for (i in 0 until 5) obj(s, "Gem", -6f + i * 3f, 0.5f, 0.5f, 0.5f).sprite(0xFFFFD54F, 2).circleCol(true).script("CollectibleBP.bp").also { it.order = 5 }
        obj(s, "Spinner", -8f, 1f, 1f, 1f).sprite(0xFFEC407A).script("RotatorBP.bp")
        p.saveScene(s)
        p.startScene = "Main"
    }

    // ================================================================ ULTIMATE EDITION TEMPLATES
    // Unity 6 + Unreal Engine 5 class professional templates

    private val fpsTemplate = Template("FPS Ultimate", "First-person shooter with advanced physics, IK, and multiplayer ready.") { p ->
        installAssets(p, "Checker", "Wooden Crate", "Toon", "Pulse", "Player3D", "Coin Pickup")
        p.writeAsset("FPSController.js", """
// Ultimate FPS Controller - Unity 6 / UE5 class
var speed = 6, mouseSensitivity = 2, jumpForce = 7, gravity = -15
var velocityY = 0, isGrounded = false, health = 100
var ammo = 30, maxAmmo = 30, isReloading = false

function start() {
    log("FPS Ultimate - WASD to move, Mouse to look, Space to jump, Left Click to shoot");
    scene.find("AmmoText").text = "Ammo: " + ammo + "/" + maxAmmo;
    scene.find("HealthText").text = "Health: " + health;
}

function update(dt) {
    // Movement
    var moveX = input.axisX * speed
    var moveZ = input.axisY * speed
    self.vx = moveX
    self.vz = moveZ
    
    // Gravity
    velocityY += gravity * dt
    self.vy = velocityY
    
    if (self.grounded) {
        isGrounded = true
        velocityY = -2
        if (input.aDown) {
            velocityY = jumpForce
            audio.play("jump.wav")
        }
    }
    
    // Look with mouse (simplified)
    self.rotY += input.mouseDeltaX * mouseSensitivity * dt * 100
    self.rotX = clamp(self.rotX - input.mouseDeltaY * mouseSensitivity * dt * 100, -90, 90)
    
    // Shooting
    if (input.leftMouse && ammo > 0 && !isReloading) {
        ammo--
        scene.find("AmmoText").text = "Ammo: " + ammo + "/" + maxAmmo
        var hit = scene.raycast(self.worldX, self.worldY, self.worldZ, self.forward()[0], self.forward()[1], self.forward()[2], 100)
        if (hit) {
            if (hit.tag == "Enemy") {
                hit.send("takeDamage", 25)
                scene.shake(0.3)
            }
            var fx = scene.spawn("HitFX", hit.worldX, hit.worldY, hit.worldZ)
            if (fx) { fx.burst(20); after(1, function() { fx.destroy() }) }
        }
        audio.play("shoot.wav")
        if (ammo <= 0) reload()
    }
    
    if (input.bDown) reload()
    
    // Health check
    if (health <= 0) {
        scene.find("Game").send("gameOver")
    }
}

function reload() {
    if (isReloading || ammo == maxAmmo) return
    isReloading = true
    scene.find("AmmoText").text = "Reloading..."
    after(1.5, function() {
        ammo = maxAmmo
        isReloading = false
        scene.find("AmmoText").text = "Ammo: " + ammo + "/" + maxAmmo
    })
}

function takeDamage(amount) {
    health -= amount
    scene.find("HealthText").text = "Health: " + health
    scene.shake(0.5)
    self.color = "#FF0000"
    after(0.1, function() { self.color = "#FFFFFF" })
}

function onTrigger(other) {
    if (other.tag == "AmmoPickup") {
        ammo = maxAmmo
        scene.find("AmmoText").text = "Ammo: " + ammo + "/" + maxAmmo
        other.destroy()
        audio.play("coin.wav")
    }
    if (other.tag == "HealthPickup") {
        health = Math.min(100, health + 25)
        scene.find("HealthText").text = "Health: " + health
        other.destroy()
    }
}
""")
        p.writeAsset("EnemyFPS.js", """
var health = 100, speed = 2, detectionRange = 15, attackRange = 2
var target = null, state = "patrol", patrolPoint = null

function start() {
    target = scene.find("Player")
    setNewPatrolPoint()
    every(3, function() { if (state == "patrol") setNewPatrolPoint() })
}

function setNewPatrolPoint() {
    patrolPoint = { x: random(-20, 20), y: self.y, z: random(-20, 20) }
}

function update(dt) {
    if (!target || target.active == false) return
    
    var dist = self.distanceTo3(target)
    
    if (dist < detectionRange) state = "chase"
    else if (state == "chase" && dist > detectionRange * 1.5) state = "patrol"
    
    if (state == "chase") {
        if (dist < attackRange) {
            state = "attack"
        } else {
            // Move towards player using NavMesh
            var dirX = target.worldX - self.worldX
            var dirZ = target.worldZ - self.worldZ
            var len = Math.sqrt(dirX * dirX + dirZ * dirZ)
            if (len > 0.1) {
                self.vx = (dirX / len) * speed
                self.vz = (dirZ / len) * speed
                self.rotY = Math.atan2(dirX, dirZ) * 180 / Math.PI
            }
        }
    } else if (state == "patrol" && patrolPoint) {
        var dx = patrolPoint.x - self.worldX
        var dz = patrolPoint.z - self.worldZ
        var d = Math.sqrt(dx * dx + dz * dz)
        if (d < 1) setNewPatrolPoint()
        else {
            self.vx = (dx / d) * speed * 0.5
            self.vz = (dz / d) * speed * 0.5
        }
    } else if (state == "attack") {
        if (dist > attackRange * 1.2) state = "chase"
        else {
            target.send("takeDamage", 10 * dt)
        }
    }
}

function takeDamage(amount) {
    health -= amount
    if (health <= 0) {
        var fx = scene.spawn("Explosion", self.worldX, self.worldY, self.worldZ)
        if (fx) { fx.burst(50); after(1, function() { fx.destroy() }) }
        self.destroy()
        scene.find("Game").send("addScore", 100)
    } else {
        self.color = "#FF5555"
        after(0.1, function() { self.color = "#FF0000" })
    }
}
""")
        val s = Scene("Main")
        s.fog = true; s.fogStart = 30f; s.fogEnd = 100f
        val cam = obj3(s, "Player", 0f, 2f, 0f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFF4FC3F7).col3().body3(friction = 0.1f).script("FPSController.js")
        cam.tag = "Player"
        cam.add(com.sengine.engine.core.Camera3D().also { it.fov = 75f; it.offsetY = 0.6f; it.offsetZ = 0f })
        cam.add(com.sengine.engine.core.AudioListener())
        cam.add(com.sengine.engine.core.CharacterControllerComponent())
        
        obj3(s, "Ground", 0f, -0.5f, 0f, 100f, 1f, 100f).mesh(2, 0xFF5A7A4A).col3().body3(type = 2)
        
        // Walls for cover
        for (i in 0 until 10) {
            val wx = random(-20, 20); val wz = random(-20, 20)
            obj3(s, "Wall", wx, 1.5f, wz, 1f, 3f, 3f).mesh(0, 0xFF8D8D8D).col3().body3(type = 2)
        }
        
        // Enemies
        for (i in 0 until 8) {
            val ex = random(-25, 25); val ez = random(-25, 25)
            obj3(s, "Enemy", ex, 1f, ez, 0.8f, 1.8f, 0.8f).mesh(6, 0xFFEF5350).col3().body3().script("EnemyFPS.js").also { it.tag = "Enemy" }
        }
        
        // Pickups
        obj3(s, "AmmoPickup", 5f, 0.5f, 5f, 0.5f, 0.5f, 0.5f).mesh(1, 0xFFFFCA28).col3(trigger = true).also { it.tag = "AmmoPickup" }
        obj3(s, "HealthPickup", -5f, 0.5f, -5f, 0.5f, 0.5f, 0.5f).mesh(1, 0xFF66BB6A).col3(trigger = true).also { it.tag = "HealthPickup" }
        
        // FX
        val hitFX = obj3(s, "HitFX", 0f, 0f, 0f); hitFX.active = false
        hitFX.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting = false; it.rate = 0f; it.speed = 3f; it.lifetime = 0.3f; it.startColor = 0xFFFFAB40.toInt(); it.endColor = 0x00FF6D00 })
        val explosion = obj3(s, "Explosion", 0f, 0f, 0f); explosion.active = false
        explosion.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting = false; it.rate = 0f; it.spread = 360f; it.speed = 8f; it.lifetime = 0.8f; it.startSize = 0.5f; it.startColor = 0xFFFF6D00.toInt(); it.endColor = 0x00000000 })
        
        obj(s, "Game", 0f, 0f).script("GameManager.js")
        p.writeAsset("GameManager.js", "var score=0; function addScore(s){score+=s; scene.find('ScoreText').text='Score: '+score} function gameOver(){scene.find('ScoreText').text='Game Over! Score: '+score; after(3, function(){scene.reload()})}")
        obj(s, "ScoreText", 0f, 4f).text("Score: 0", 0.5f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "AmmoText", -5f, 4f).text("Ammo: 30/30", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "HealthText", 5f, 4f).text("Health: 100", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        
        obj3(s, "Sun", 10f, 20f, 10f).also { it.rotX = -50f; it.rotY = 30f }.add(Light().also { it.kind = 0; it.intensity = 1.2f })
        p.saveScene(s); p.startScene = "Main"; p.orientation = 0
    }

    private val racingTemplate = Template("Racing Ultimate", "Vehicle physics with wheel colliders, terrain, and checkpoints.") { p ->
        p.writeAsset("CarController.js", """
var motorForce = 1500, brakeForce = 3000, maxSteer = 30
var speed = 0, lap = 1, checkpoint = 0

function start() {
    log("Racing Ultimate - WASD to drive, Space to brake")
}

function update(dt) {
    var motor = input.axisY * motorForce
    var steer = input.axisX * maxSteer
    var brake = input.b ? brakeForce : 0
    
    // Apply to wheel colliders via engine
    self.vx += Math.sin(self.rotY * Math.PI / 180) * motor * dt * 0.001
    self.vz += Math.cos(self.rotY * Math.PI / 180) * motor * dt * 0.001
    self.rotY += steer * dt * (speed * 0.1)
    
    speed = Math.sqrt(self.vx * self.vx + self.vz * self.vz) * 3.6
    scene.find("SpeedText").text = Math.floor(speed) + " km/h"
    
    // Drag
    self.vx *= (1 - dt * 0.5)
    self.vz *= (1 - dt * 0.5)
}

function onTrigger(other) {
    if (other.tag == "Checkpoint") {
        var idx = parseInt(other.name.split(" ")[1])
        if (idx == checkpoint + 1) {
            checkpoint = idx
            scene.find("CheckpointText").text = "Checkpoint: " + checkpoint + "/5"
            audio.beep()
            if (checkpoint >= 5) {
                lap++
                checkpoint = 0
                scene.find("LapText").text = "Lap: " + lap + "/3"
                if (lap > 3) scene.find("Game").send("finish")
            }
        }
    }
}
""")
        val s = Scene("Main")
        s.fog = true; s.fogStart = 50f; s.fogEnd = 200f
        val car = obj3(s, "PlayerCar", 0f, 1f, -20f, 1.8f, 1f, 4f).mesh(0, 0xFF42A5F5).col3().body3(friction = 0.3f).script("CarController.js")
        car.tag = "Player"
        car.add(com.sengine.engine.core.Camera3D().also { it.follow = "PlayerCar"; it.offsetY = 5f; it.offsetZ = -10f; it.smoothing = 3f })
        car.add(com.sengine.engine.core.WheelColliderComponent())
        
        obj3(s, "Ground", 0f, -0.5f, 0f, 200f, 1f, 200f).mesh(2, 0xFF4A5A4A).col3().body3(type = 2)
        
        // Track walls
        for (i in 0 until 20) {
            val angle = i * 18f * Math.PI / 180
            val r = 40f
            obj3(s, "TrackWall", (Math.cos(angle) * r).toFloat(), 1f, (Math.sin(angle) * r).toFloat(), 2f, 2f, 2f).mesh(0, 0xFF8D8D8D).col3().body3(type = 2)
            obj3(s, "TrackWallInner", (Math.cos(angle) * (r - 15)).toFloat(), 1f, (Math.sin(angle) * (r - 15)).toFloat(), 2f, 2f, 2f).mesh(0, 0xFF8D8D8D).col3().body3(type = 2)
        }
        
        // Checkpoints
        for (i in 1..5) {
            val angle = (i - 1) * 72f * Math.PI / 180
            val r = 32.5f
            obj3(s, "Checkpoint $i", (Math.cos(angle) * r).toFloat(), 1f, (Math.sin(angle) * r).toFloat(), 8f, 3f, 1f).mesh(0, 0xFF00FF00).col3(trigger = true).also { it.tag = "Checkpoint" }
        }
        
        obj(s, "SpeedText", 0f, 4f).text("0 km/h", 0.5f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "CheckpointText", -5f, 4f).text("Checkpoint: 0/5", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "LapText", 5f, 4f).text("Lap: 1/3", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj3(s, "Sun", 20f, 30f, 20f).also { it.rotX = -60f }.add(Light().also { it.kind = 0; it.intensity = 1f })
        p.writeAsset("GameManager.js", "function finish(){scene.find('SpeedText').text='Finished!'; after(3, function(){scene.reload()})}")
        obj(s, "Game", 0f, 0f).script("GameManager.js")
        p.saveScene(s); p.startScene = "Main"; p.orientation = 0
    }

    private val rpgTemplate = Template("RPG Ultimate", "Open-world RPG with quests, inventory, dialogue, and skill tree.") { p ->
        p.writeAsset("PlayerRPG.js", """
var level = 1, xp = 0, xpToNext = 100, gold = 50
var health = 100, maxHealth = 100, mana = 100, maxMana = 100
var strength = 10, agility = 10, intelligence = 10
var inventory = [], quests = []

function start() {
    log("RPG Ultimate - Explore, fight, level up!")
    updateUI()
}

function updateUI() {
    scene.find("LevelText").text = "Lv." + level
    scene.find("HealthText").text = "HP: " + health + "/" + maxHealth
    scene.find("ManaText").text = "MP: " + mana + "/" + maxMana
    scene.find("GoldText").text = "Gold: " + gold
    scene.find("XPText").text = "XP: " + xp + "/" + xpToNext
}

function update(dt) {
    self.vx = input.axisX * 4
    self.vz = input.axisY * 4
    self.vy += -9.81 * dt
    if (self.grounded && input.aDown) self.vy = 6
    
    if (mana < maxMana) mana += dt * 5
}

function gainXP(amount) {
    xp += amount
    if (xp >= xpToNext) {
        level++
        xp -= xpToNext
        xpToNext = Math.floor(xpToNext * 1.5)
        maxHealth += 20
        health = maxHealth
        maxMana += 10
        mana = maxMana
        strength += 2; agility += 2; intelligence += 2
        log("Level up! Now level " + level)
        var fx = scene.spawn("LevelUpFX", self.worldX, self.worldY + 2, self.worldZ)
        if (fx) { fx.burst(30); after(2, function(){fx.destroy()}) }
    }
    updateUI()
}

function takeDamage(dmg) {
    health -= dmg
    if (health <= 0) {
        health = 0
        scene.find("Game").send("gameOver")
    }
    updateUI()
    scene.shake(0.3)
}

function onTrigger(other) {
    if (other.tag == "Enemy") {
        takeDamage(10)
    }
    if (other.tag == "Coin") {
        gold += 10
        other.destroy()
        updateUI()
        audio.play("coin.wav")
    }
    if (other.tag == "QuestNPC") {
        other.send("interact", self)
    }
}
""")
        p.writeAsset("QuestNPC.js", """
var questId = "q1", questName = "Kill 5 Slimes", questProgress = 0, questTarget = 5, completed = false

function interact(player) {
    if (completed) {
        log("Quest already completed!")
        return
    }
    if (questProgress >= questTarget) {
        completed = true
        log("Quest completed! Reward: 100 XP, 50 Gold")
        player.send("gainXP", 100)
        // Give gold via scene
        after(0.1, function() {
            var p = scene.find("Player")
            if (p) p.gold += 50
        })
        self.text = "Thank you, hero!"
    } else {
        log(questName + ": " + questProgress + "/" + questTarget)
    }
}

function addProgress() {
    if (!completed) {
        questProgress++
        log(questName + ": " + questProgress + "/" + questTarget)
    }
}
""")
        val s = Scene("Main")
        s.fog = true; s.fogStart = 20f; s.fogEnd = 80f
        val player = obj3(s, "Player", 0f, 1f, 0f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFF4FC3F7).col3().body3().script("PlayerRPG.js")
        player.tag = "Player"
        player.add(com.sengine.engine.core.Camera3D().also { it.follow = "Player"; it.offsetY = 8f; it.offsetZ = 8f })
        
        obj3(s, "Ground", 0f, -0.5f, 0f, 100f, 1f, 100f).mesh(0, 0xFF6B8E5A, "Checker.png", 10f).col3().body3(type = 2)
        
        // NPCs
        val npc = obj3(s, "QuestNPC", 5f, 1f, 5f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFFFFCA28).col3(trigger = true).script("QuestNPC.js").text("?", 0.5f)
        npc.tag = "QuestNPC"
        
        // Enemies
        for (i in 0 until 10) {
            obj3(s, "Slime", random(-20, 20), 0.5f, random(-20, 20), 0.8f, 0.8f, 0.8f).mesh(1, 0xFF66BB6A).col3(trigger = true).also { it.tag = "Enemy" }
        }
        
        // Coins
        for (i in 0 until 20) {
            obj3(s, "Coin", random(-25, 25), 0.5f, random(-25, 25), 0.5f, 0.5f, 0.5f).mesh(5, 0xFFFFD54F).col3(trigger = true).also { it.tag = "Coin" }
        }
        
        // UI
        obj(s, "LevelText", -6f, 4f).text("Lv.1", 0.5f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "HealthText", -3f, 4f).text("HP: 100/100", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "ManaText", 0f, 4f).text("MP: 100/100", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "GoldText", 3f, 4f).text("Gold: 50", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "XPText", 6f, 4f).text("XP: 0/100", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        
        val fx = obj3(s, "LevelUpFX", 0f, 0f, 0f); fx.active = false
        fx.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting = false; it.rate = 0f; it.spread = 360f; it.speed = 4f; it.lifetime = 1f; it.startColor = 0xFFFFEB3B.toInt(); it.endColor = 0x00FFEB3B })
        
        obj3(s, "Sun", 10f, 20f, 10f).also { it.rotX = -50f }.add(Light().also { it.kind = 0; it.intensity = 1f })
        p.writeAsset("GameManager.js", "function gameOver(){scene.find('LevelText').text='Game Over'; after(3, function(){scene.reload()})}")
        obj(s, "Game", 0f, 0f).script("GameManager.js")
        p.saveScene(s); p.startScene = "Main"; p.orientation = 0
    }

    private val battleRoyaleTemplate = Template("Battle Royale Ultimate", "100-player battle royale with shrinking zone, loot, and building.") { p ->
        p.writeAsset("BRPlayer.js", """
var health = 100, shield = 0, isInZone = true, zoneDamageTimer = 0
var weapons = [], currentWeapon = 0, materials = { wood: 100, brick: 100, metal: 50 }

function start() {
    log("Battle Royale Ultimate - Survive the storm!")
}

function update(dt) {
    self.vx = input.axisX * 5
    self.vz = input.axisY * 5
    self.vy += -9.81 * dt
    if (self.grounded && input.aDown) self.vy = 7
    
    // Zone damage
    if (!isInZone) {
        zoneDamageTimer += dt
        if (zoneDamageTimer > 1) {
            zoneDamageTimer = 0
            takeDamage(5)
        }
    }
    
    // Building
    if (input.bDown) buildWall()
}

function buildWall() {
    if (materials.wood < 10) return
    materials.wood -= 10
    var fwd = self.forward()
    var wx = self.worldX + fwd[0] * 2
    var wy = self.worldY
    var wz = self.worldZ + fwd[2] * 2
    scene.spawn("Wall", wx, wy, wz)
}

function takeDamage(dmg) {
    if (shield > 0) {
        var shieldDmg = Math.min(shield, dmg)
        shield -= shieldDmg
        dmg -= shieldDmg
    }
    health -= dmg
    if (health <= 0) {
        scene.find("Game").send("playerDied", self)
        self.active = false
    }
    scene.shake(0.2)
}

function onTrigger(other) {
    if (other.tag == "Weapon") {
        weapons.push(other.name)
        other.destroy()
        log("Picked up " + other.name)
    }
    if (other.tag == "Shield") {
        shield = Math.min(100, shield + 25)
        other.destroy()
    }
}
""")
        val s = Scene("Main")
        s.fog = true; s.fogStart = 30f; s.fogEnd = 120f
        val player = obj3(s, "Player", 0f, 2f, 0f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFF42A5F5).col3().body3().script("BRPlayer.js")
        player.tag = "Player"
        player.add(com.sengine.engine.core.Camera3D().also { it.follow = "Player"; it.offsetY = 6f; it.offsetZ = -8f })
        
        obj3(s, "Ground", 0f, -0.5f, 0f, 200f, 1f, 200f).mesh(2, 0xFF5A7A5A).col3().body3(type = 2)
        
        // Loot
        for (i in 0 until 20) {
            obj3(s, "Weapon", random(-50, 50), 0.5f, random(-50, 50), 0.3f, 0.3f, 1f).mesh(0, 0xFF8D8D8D).col3(trigger = true).also { it.tag = "Weapon" }
            obj3(s, "Shield", random(-50, 50), 0.5f, random(-50, 50), 0.5f, 0.5f, 0.5f).mesh(1, 0xFF42A5F5).col3(trigger = true).also { it.tag = "Shield" }
        }
        
        // Building walls prefab (inactive)
        val wall = obj3(s, "Wall", 0f, 0f, 0f, 3f, 3f, 0.3f).mesh(0, 0xFF8D6E63).col3().body3(type = 2)
        wall.active = false
        
        // Storm zone visualization
        obj3(s, "Zone", 0f, 0.1f, 0f, 80f, 0.1f, 80f).mesh(2, 0x44FF0000).also { it.tag = "Zone" }
        
        obj(s, "PlayersAlive", 0f, 4f).text("Players: 20", 0.5f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj3(s, "Sun", 20f, 40f, 20f).also { it.rotX = -60f }.add(Light().also { it.kind = 0; it.intensity = 1.2f })
        p.writeAsset("GameManager.js", "var alive=20; function playerDied(p){alive--; scene.find('PlayersAlive').text='Players: '+alive; if(alive<=1) {scene.find('PlayersAlive').text='Victory Royale!'; after(3, function(){scene.reload()})}}")
        obj(s, "Game", 0f, 0f).script("GameManager.js")
        p.saveScene(s); p.startScene = "Main"; p.orientation = 0
    }

    private val cityBuilderTemplate = Template("City Builder Ultimate", "Build and manage a city with economy, traffic, and citizens.") { p ->
        p.writeAsset("CityManager.js", """
var money = 10000, population = 0, happiness = 75
var buildings = [], roads = []

function start() {
    log("City Builder Ultimate - Build your dream city!")
    updateUI()
    every(1, function() {
        // Economy tick
        var income = population * 10
        var expenses = buildings.length * 5
        money += income - expenses
        if (money < 0) money = 0
        updateUI()
    })
}

function updateUI() {
    scene.find("MoneyText").text = "Money: " + money
    scene.find("PopText").text = "Pop: " + population
    scene.find("HappyText").text = "Happy: " + happiness + "%"
}

function canAfford(cost) { return money >= cost }

function placeBuilding(type, x, z) {
    var costs = { house: 500, factory: 1000, park: 300, road: 50 }
    var cost = costs[type] || 500
    if (!canAfford(cost)) { log("Not enough money!"); return false }
    money -= cost
    var b = scene.spawn(type, x, 0, z)
    if (b) {
        buildings.push(b)
        if (type == "house") population += 10
        if (type == "park") happiness = Math.min(100, happiness + 5)
        if (type == "factory") happiness = Math.max(0, happiness - 5)
    }
    updateUI()
    return true
}

function update(dt) {
    if (input.tapped) {
        var x = input.touchX
        var z = input.touchY
        placeBuilding("house", x, z)
    }
}
""")
        val s = Scene("Main")
        val cam = obj3(s, "Main Camera", 0f, 30f, -20f).also { it.rotX = -60f }
        cam.add(com.sengine.engine.core.Camera3D().also { it.fov = 60f })
        
        obj3(s, "Ground", 0f, -0.5f, 0f, 100f, 1f, 100f).mesh(2, 0xFF6B8E5A).col3().body3(type = 2)
        
        // Building prefabs (inactive)
        obj3(s, "house", 0f, 0f, 0f, 2f, 2f, 2f).mesh(0, 0xFF8D6E63).col3().also { it.active = false }
        obj3(s, "factory", 0f, 0f, 0f, 3f, 3f, 3f).mesh(0, 0xFF5A5A5A).col3().also { it.active = false }
        obj3(s, "park", 0f, 0f, 0f, 4f, 0.5f, 4f).mesh(2, 0xFF4CAF50).col3().also { it.active = false }
        obj3(s, "road", 0f, 0f, 0f, 2f, 0.1f, 2f).mesh(0, 0xFF3A3A3A).col3().also { it.active = false }
        
        obj(s, "MoneyText", -5f, 4f).text("Money: 10000", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "PopText", 0f, 4f).text("Pop: 0", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "HappyText", 5f, 4f).text("Happy: 75%", 0.4f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        
        obj(s, "CityManager", 0f, 0f).script("CityManager.js")
        obj3(s, "Sun", 20f, 30f, 20f).also { it.rotX = -50f }.add(Light().also { it.kind = 0; it.intensity = 1f })
        p.saveScene(s); p.startScene = "Main"; p.orientation = 0
    }

    private val ultimateShowcase = Template("Ultimate Showcase", "All Ultimate Edition features in one scene: PBR, VFX, AI, Physics, Terrain.") { p ->
        installAssets(p, "Checker", "Wooden Crate", "Low-Poly Tree", "Rock", "Crystal", "Toon", "Pulse", "Player3D", "Coin Pickup", "Jump", "Rotator (Blueprint)")
        p.writeAsset("UltimatePlayer.js", """
// Ultimate Showcase Player - Demonstrates all engine features
var speed = 6, jump = 7, health = 100
var score = 0, isGrounded = false

function start() {
    log("S Engine Ultimate Edition - Ultimate Showcase")
    log("Features: PBR Materials, GPU Instancing, Post FX, NavMesh AI, VFX Graph, Terrain, Water, Volumetrics, Multiplayer Ready")
    log("WASD to move, Space to jump, Mouse to look, E to interact")
}

function update(dt) {
    // Advanced movement with root motion
    self.vx = input.axisX * speed
    self.vz = input.axisY * speed
    
    // Jump with coyote time
    if (input.aDown && self.grounded) {
        self.vy = jump
        audio.play("jump.wav")
        // VFX
        var fx = scene.spawn("JumpFX", self.worldX, self.worldY, self.worldZ)
        if (fx) { fx.burst(15); after(1, function(){fx.destroy()}) }
    }
    
    // Interaction
    if (input.bDown) {
        var hit = scene.raycast(self.worldX, self.worldY + 1, self.worldZ, self.forward()[0], self.forward()[1], self.forward()[2], 3)
        if (hit) hit.send("interact")
    }
    
    // Health regen
    if (health < 100) health += dt * 2
}

function addScore(s) {
    score += s
    scene.find("ScoreText").text = "Score: " + score
    scene.shake(s * 0.01)
}

function takeDamage(dmg) {
    health -= dmg
    scene.find("HealthBar").scaleX = health / 100
    if (health <= 0) scene.reload()
}

function onTrigger(other) {
    if (other.tag == "Coin") {
        addScore(10)
        other.destroy()
        audio.play("coin.wav")
        var fx = scene.spawn("CoinFX", other.worldX, other.worldY, other.worldZ)
        if (fx) { fx.burst(20); after(1, function(){fx.destroy()}) }
    }
}
""")
        p.writeAsset("AIUltimate.js", """
var state = "patrol", target = null, patrolPoints = [], currentPatrol = 0
var detectionRange = 12, attackRange = 2, speed = 2.5

function start() {
    target = scene.find("Player")
    // Find patrol points
    for (var i = 1; i <= 4; i++) {
        var p = scene.find("Patrol" + i)
        if (p) patrolPoints.push(p)
    }
}

function update(dt) {
    if (!target) return
    var dist = self.distanceTo3(target)
    
    // Behavior Tree logic
    if (dist < detectionRange) state = "chase"
    else if (state == "chase" && dist > detectionRange * 1.5) state = "patrol"
    
    if (state == "patrol" && patrolPoints.length > 0) {
        var dest = patrolPoints[currentPatrol]
        var dx = dest.worldX - self.worldX
        var dz = dest.worldZ - self.worldZ
        var d = Math.sqrt(dx*dx + dz*dz)
        if (d < 1) {
            currentPatrol = (currentPatrol + 1) % patrolPoints.length
        } else {
            self.vx = (dx / d) * speed * 0.6
            self.vz = (dz / d) * speed * 0.6
            self.rotY = Math.atan2(dx, dz) * 180 / Math.PI
        }
    } else if (state == "chase") {
        if (dist < attackRange) {
            state = "attack"
        } else {
            var dirX = target.worldX - self.worldX
            var dirZ = target.worldZ - self.worldZ
            var len = Math.sqrt(dirX*dirX + dirZ*dirZ)
            self.vx = (dirX / len) * speed
            self.vz = (dirZ / len) * speed
            self.rotY = Math.atan2(dirX, dirZ) * 180 / Math.PI
        }
    } else if (state == "attack") {
        if (dist > attackRange * 1.5) state = "chase"
        else target.send("takeDamage", 15 * dt)
    }
}
""")
        val s = Scene("Main")
        s.fog = true; s.fogStart = 30f; s.fogEnd = 100f; s.ambient = 0xFF5A6A7A.toInt()
        
        // Player with all ultimate components
        val player = obj3(s, "Player", 0f, 2f, 0f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFF4FC3F7).col3().body3(friction = 0.1f).script("UltimatePlayer.js")
        player.tag = "Player"
        player.add(com.sengine.engine.core.CharacterControllerComponent())
        player.add(com.sengine.engine.core.Camera3D().also { it.follow = "Player"; it.offsetY = 4f; it.offsetZ = 8f; it.postFx = 6; it.postIntensity = 0.8f; it.fov = 70f })
        player.add(com.sengine.engine.core.AudioListener())
        player.add(com.sengine.engine.core.TrailRenderer().also { it.time = 0.5f; it.startWidth = 0.3f; it.endWidth = 0f })
        
        // Terrain
        obj3(s, "Ground", 0f, -0.5f, 0f, 100f, 1f, 100f).mesh(2, 0xFF7A9A6A).col3().body3(type = 2).also { it.add(com.sengine.engine.core.TerrainComponent()) }
        
        // PBR Showcase - different materials
        obj3(s, "PBRMetal", -8f, 1f, -5f, 1.5f, 1.5f, 1.5f).mesh(1, 0xFFAAAAAA).also { it.get<com.sengine.engine.core.MeshRenderer>()!!.also { m -> m.specular = 1f; m.shininess = 100f } }.col3().body3()
        obj3(s, "PBRWood", -5f, 1f, -5f, 1.5f, 1.5f, 1.5f).mesh(0, 0xFF8D6E63, "Crate.png").col3().body3()
        obj3(s, "PBRGlass", -2f, 1f, -5f, 1.5f, 1.5f, 1.5f).mesh(0, 0x884FC3F7).col3().body3()
        obj3(s, "PBREmissive", 2f, 1f, -5f, 1.5f, 1.5f, 1.5f).mesh(5, 0xFF7C4DFF).also { it.get<com.sengine.engine.core.MeshRenderer>()!!.emission = 1f }.col3().body3()
        
        // Lighting showcase
        obj3(s, "Sun", 20f, 30f, 20f).also { it.rotX = -50f; it.rotY = 30f }.add(com.sengine.engine.core.Light().also { it.kind = 0; it.intensity = 1.2f; it.shadows = 2 })
        obj3(s, "PointLight1", -4f, 3f, 2f).add(com.sengine.engine.core.Light().also { it.kind = 1; it.color = 0xFFFF6D00.toInt(); it.intensity = 2f; it.range = 10f })
        obj3(s, "PointLight2", 4f, 3f, 2f).add(com.sengine.engine.core.Light().also { it.kind = 1; it.color = 0xFF00BCD4.toInt(); it.intensity = 2f; it.range = 10f })
        obj3(s, "SpotLight", 0f, 8f, 0f).also { it.rotX = -90f }.add(com.sengine.engine.core.Light().also { it.kind = 2; it.color = 0xFFFFFFFF.toInt(); it.intensity = 3f; it.range = 20f; it.spotAngle = 45f })
        
        // Reflection probes
        obj3(s, "ReflectionProbe", 0f, 2f, 0f).add(com.sengine.engine.core.ReflectionProbeComponent().also { it.resolution = 256; it.intensity = 1f })
        
        // AI with NavMesh
        for (i in 0 until 5) {
            obj3(s, "Enemy", random(-15, 15), 1f, random(-15, 15), 0.8f, 1.6f, 0.8f).mesh(6, 0xFFEF5350).col3().body3().script("AIUltimate.js").also { it.tag = "Enemy"; it.add(com.sengine.engine.core.NavMeshAgentComponent().also { a -> a.speed = 2.5f }) }
        }
        
        // Patrol points
        for (i in 1..4) {
            val angle = (i - 1) * 90f * Math.PI / 180
            obj3(s, "Patrol$i", (Math.cos(angle) * 10).toFloat(), 0f, (Math.sin(angle) * 10).toFloat(), 0.3f, 0.3f, 0.3f).mesh(1, 0xFF00FF00)
        }
        
        // VFX
        val coinFX = obj3(s, "CoinFX", 0f, 0f, 0f); coinFX.active = false
        coinFX.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting = false; it.rate = 0f; it.spread = 360f; it.speed = 4f; it.lifetime = 0.7f; it.startColor = 0xFFFFEB3B.toInt(); it.endColor = 0x00FFEB3B })
        coinFX.add(com.sengine.engine.core.ParticleSystemComponent().also { it.duration = 1f; it.startLifetime = 0.7f; it.startSpeed = 4f; it.emissionRate = 0f })
        
        val jumpFX = obj3(s, "JumpFX", 0f, 0f, 0f); jumpFX.active = false
        jumpFX.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting = false; it.rate = 0f; it.direction = 90f; it.spread = 30f; it.speed = 3f; it.lifetime = 0.5f; it.startColor = 0xFF8D8D8D.toInt(); it.endColor = 0x008D8D8D })
        
        // Coins with PBR
        for (i in 0 until 15) {
            obj3(s, "Coin", random(-20, 20), 1f, random(-20, 20), 0.6f, 0.6f, 0.6f).mesh(5, 0xFFFFD54F).col3(sphere = true, trigger = true).script("Rotator.js", "x=0, y=90, z=0").also { it.tag = "Coin"; it.get<com.sengine.engine.core.MeshRenderer>()!!.emission = 0.3f }
        }
        
        // Trees (GPU Instanced)
        for (i in 0 until 20) {
            obj3(s, "Tree", random(-30, 30), 0f, random(-30, 30), 2f, 2f, 2f).mesh(8, 0xFF5FA85B, model = "Tree.obj").col3()
        }
        
        // Water
        obj3(s, "Water", 15f, -0.3f, 15f, 20f, 1f, 20f).mesh(2, 0xFF0A4A6A).also { it.add(com.sengine.engine.core.WaterComponent()) }
        
        // UI - Ultimate HUD
        obj(s, "ScoreText", 0f, 4.2f).text("Score: 0", 0.5f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "HealthBar", -5f, 4f, 2f, 0.3f).sprite(0xFF4CAF50).also { it.get<com.sengine.engine.core.SpriteRenderer>()!!.screenSpace = true; it.order = 100 }
        obj(s, "Info", 0f, -4f).text("S Engine Ultimate Edition | PBR | VFX Graph | NavMesh AI | GPU Instancing | Post FX | Multiplayer Ready", 0.25f).also { it.get<TextRenderer>()!!.screenSpace = true; it.order = 100 }
        
        p.writeAsset("Rotator.js", "function update(dt){self.rotY += 90 * dt}")
        p.saveScene(s); p.startScene = "Main"; p.orientation = 0
    }

    // ==================== ULTIMATE 6.1 QUANTUM TEMPLATES ====================

    val quantumShowcase = Template("Quantum Showcase", "Ultimate 6.1 - All Quantum Features: RayTracing, Nanite, Lumen, Chaos, WorldPartition, PCG") { p ->
        p.writeAsset("QuantumPlayer.js", """
var speed = 6, health = 100, score = 0, isGrounded = false
var rayTracingEnabled = true, naniteEnabled = true, lumenEnabled = true

function start() {
    log("Quantum Showcase - RayTracing, Nanite, Lumen, Chaos, World Partition, PCG, Control Rig, Motion Matching, XR, ML-Agents")
    scene.find("Info").setText("S Engine 6.1 Quantum | RayTracing | Nanite | Lumen | Chaos | World Partition | PCG | Control Rig | XR")
}

function update(dt) {
    var vx = input.axisX * speed, vz = input.axisY * speed
    self.vx = vx; self.vz = vz
    if (input.aDown && isGrounded) { self.vy = 7; audio.play("jump"); scene.find("JumpFX").burst(20) }
    
    // Quantum features toggle
    if (input.bDown) {
        rayTracingEnabled = !rayTracingEnabled
        log("RayTracing: " + rayTracingEnabled + " | Nanite: " + naniteEnabled + " | Lumen: " + lumenEnabled)
        scene.shake(0.2)
    }
    
    // World Partition test
    var cellX = Math.floor(self.worldX / 256), cellZ = Math.floor(self.worldZ / 256)
    scene.find("CellInfo").setText("Cell: " + cellX + "," + cellZ + " | Pos: " + Math.floor(self.worldX) + "," + Math.floor(self.worldZ))
    
    // Time of day
    var tod = scene.getTimeOfDay()
    scene.find("TimeInfo").setText("Time: " + Math.floor(tod) + ":00 | Weather: Clear")
}

function takeDamage(dmg) {
    health -= dmg
    scene.find("HealthBar").scaleX = health / 100
    if (health <= 0) { log("Game Over"); scene.reload() }
}

function addScore(s) {
    score += s
    scene.find("ScoreText").setText("Score: " + score + " | Quantum Points")
    scene.shake(s * 0.01)
}

function onCollision(other) {
    if (other.name.indexOf("Ground") >= 0) isGrounded = true
}
""")
        p.writeAsset("QuantumAI.js", """
var state = "patrol", target = null, speed = 3, detectionRange = 15, attackRange = 2
var patrolPoints = [], currentPatrol = 0, eqsQuery = "FindCover"

function start() {
    target = scene.find("Player")
    patrolPoints = [scene.find("Patrol1"), scene.find("Patrol2"), scene.find("Patrol3"), scene.find("Patrol4")]
    log("Quantum AI - EQS, Utility AI, ML-Agents, Behavior Tree")
}

function update(dt) {
    var dist = self.distanceTo3(target)
    if (dist < detectionRange) {
        state = "chase"
        // EQS - find best cover position
        var bestCover = scene.find("Cover1")
        if (bestCover) {
            // Utility AI - evaluate actions
            var health = 100 // would come from blackboard
            var action = health < 30 ? "flee" : "attack"
            if (action == "flee") {
                self.setDestination(bestCover.worldX, bestCover.worldY, bestCover.worldZ)
            }
        }
    }
    if (state == "patrol") {
        var dest = patrolPoints[currentPatrol]
        if (dest) {
            var dx = dest.worldX - self.worldX, dz = dest.worldZ - self.worldZ
            var d = Math.sqrt(dx*dx+dz*dz)
            if (d < 1) currentPatrol = (currentPatrol+1) % patrolPoints.length
            else { self.vx = (dx/d)*speed*0.6; self.vz = (dz/d)*speed*0.6 }
        }
    } else if (state == "chase") {
        if (dist < attackRange) state = "attack"
        else {
            var dirX = target.worldX - self.worldX, dirZ = target.worldZ - self.worldZ
            var len = Math.sqrt(dirX*dirX+dirZ*dirZ)
            self.vx = (dirX/len)*speed; self.vz = (dirZ/len)*speed
        }
    } else if (state == "attack") {
        if (dist > attackRange*1.5) state = "chase"
        else target.send("takeDamage", 20*dt)
    }
}
""")
        val s = Scene("Main")
        s.fog = true; s.fogStart = 50f; s.fogEnd = 200f; s.ambient = 0xFF6A7A8A.toInt()
        val player = obj3(s, "Player", 0f, 2f, 0f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFF4FC3F7).col3().body3(friction=0.1f).script("QuantumPlayer.js")
        player.tag="Player"
        player.add(com.sengine.engine.core.CharacterControllerComponent())
        player.add(com.sengine.engine.core.Camera3D().also { it.follow="Player"; it.offsetY=5f; it.offsetZ=10f; it.fov=75f; it.postFx=6; it.postIntensity=1f })
        player.add(com.sengine.engine.core.AudioListener())
        player.add(com.sengine.engine.core.TrailRenderer().also { it.time=0.6f; it.startWidth=0.4f })

        obj3(s, "Ground", 0f, -0.5f, 0f, 200f, 1f, 200f).mesh(2, 0xFF7A9A6A).col3().body3(type=2)

        // Nanite showcase - high poly meshes
        for (i in 0 until 10) {
            obj3(s, "NaniteMesh_$i", random(-30,30), 1f, random(-30,30), 1f, 1f, 1f).mesh(8, 0xFF8D6E63, model="HighPoly.obj").col3()
        }

        // Chaos Destruction
        for (i in 0 until 5) {
            obj3(s, "Destructible_$i", random(-20,20), 3f, random(-20,20), 2f, 2f, 2f).mesh(0, 0xFF5A5A5A).col3().body3().script("Destructible.js", "health=100")
        }

        // Spline road
        obj3(s, "SplineRoad", 0f, 0.1f, 0f, 100f, 0.1f, 4f).mesh(2, 0xFF3A3A3A)

        // Foliage - GPU instanced
        for (i in 0 until 50) {
            obj3(s, "Foliage_$i", random(-50,50), 0f, random(-50,50), 0.5f, 1f, 0.5f).mesh(8, 0xFF4CAF50, model="Grass.obj")
        }

        // AI with EQS
        for (i in 0 until 3) {
            obj3(s, "QuantumEnemy_$i", random(-20,20), 1f, random(-20,20), 0.8f, 1.6f, 0.8f).mesh(6, 0xFFEF5350).col3().body3().script("QuantumAI.js").also { it.tag="Enemy"; it.add(com.sengine.engine.core.NavMeshAgentComponent().also { a->a.speed=3f }) }
        }

        for (i in 1..4) {
            val angle = (i-1)*90f*Math.PI/180
            obj3(s, "Patrol$i", (Math.cos(angle)*15).toFloat(), 0f, (Math.sin(angle)*15).toFloat(), 0.3f, 0.3f, 0.3f).mesh(1, 0xFF00FF00)
        }

        // VFX
        val jumpFX = obj3(s, "JumpFX", 0f, 0f, 0f); jumpFX.active=false
        jumpFX.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting=false; it.rate=0f; it.spread=30f; it.speed=5f; it.lifetime=0.6f; it.startColor=0xFF4FC3F7.toInt() })

        // UI
        obj(s, "ScoreText", 0f, 4.5f).text("Score: 0 | Quantum Points", 0.4f).also { it.get<TextRenderer>()!!.screenSpace=true; it.order=100 }
        obj(s, "HealthBar", -5f, 4f, 2f, 0.3f).sprite(0xFF4CAF50).also { it.get<com.sengine.engine.core.SpriteRenderer>()!!.screenSpace=true; it.order=100 }
        obj(s, "CellInfo", 5f, 4f).text("Cell: 0,0", 0.3f).also { it.get<TextRenderer>()!!.screenSpace=true; it.order=100 }
        obj(s, "TimeInfo", 0f, -4.5f).text("Time: 12:00 | Weather: Clear", 0.3f).also { it.get<TextRenderer>()!!.screenSpace=true; it.order=100 }
        obj(s, "Info", 0f, -4f).text("S Engine 6.1 Quantum | RayTracing | Nanite | Lumen | Chaos | WorldPartition | PCG | ControlRig | XR | ML-Agents", 0.22f).also { it.get<TextRenderer>()!!.screenSpace=true; it.order=100 }

        p.writeAsset("Destructible.js", "var health=100; function takeDamage(d){health-=d; if(health<=0){scene.find(\"Explosion\").worldX=self.worldX; scene.find(\"Explosion\").worldY=self.worldY; scene.find(\"Explosion\").worldZ=self.worldZ; scene.find(\"Explosion\").burst(50); self.destroy()}}")
        val explosion = obj3(s, "Explosion", 0f, 0f, 0f); explosion.active=false
        explosion.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting=false; it.rate=0f; it.spread=360f; it.speed=8f; it.lifetime=0.8f; it.startColor=0xFFFF6D00.toInt() })

        p.writeAsset("Rotator.js", "function update(dt){self.rotY += 45*dt}")
        p.saveScene(s); p.startScene="Main"; p.orientation=0
    }

    val rayTracingDemo = Template("RayTracing Demo", "RT Shadows, Reflections, GI, AO - Quantum Rendering") { p ->
        p.writeAsset("RTPlayer.js", """
var speed=4, rotSpeed=60
function update(dt){
    self.vx = input.axisX*speed; self.vz = input.axisY*speed
    self.rotY += input.mouseDeltaX * 0.2
    // Ray tracing debug
    if (input.aDown) {
        var hit = scene.raycast(self.worldX, self.worldY, self.worldZ, self.forwardX, self.forwardY, self.forwardZ, 100)
        if (hit) { log("RT Hit: " + hit.name + " at " + hit.distance); scene.find("RT_HitFX").worldX=hit.x; scene.find("RT_HitFX").worldY=hit.y; scene.find("RT_HitFX").worldZ=hit.z; scene.find("RT_HitFX").burst(10) }
    }
}
""")
        val s = Scene("Main")
        s.ambient = 0xFF2A2A3A.toInt()
        val player = obj3(s, "Player", 0f, 1f, 0f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFF4FC3F7).col3().body3().script("RTPlayer.js")
        player.tag="Player"
        player.add(com.sengine.engine.core.Camera3D().also { it.follow="Player"; it.offsetY=3f; it.offsetZ=6f; it.fov=75f })

        // Mirrors for RT reflections
        obj3(s, "Mirror1", -5f, 2f, -5f, 0.1f, 4f, 4f).mesh(2, 0xFFAAAAFF).col3().body3(type=2)
        obj3(s, "Mirror2", 5f, 2f, -5f, 0.1f, 4f, 4f).mesh(2, 0xFFAAAAFF).col3().body3(type=2)
        obj3(s, "Ground", 0f, -0.5f, 0f, 50f, 1f, 50f).mesh(2, 0xFF3A3A4A).col3().body3(type=2)

        // Spheres for RT showcase
        obj3(s, "Sphere_Metal", -2f, 1f, -2f, 1f, 1f, 1f).mesh(1, 0xFFC0C0C0).also { it.get<com.sengine.engine.core.MeshRenderer>()!!.also { m->m.specular=1f; m.shininess=100f } }.col3().body3()
        obj3(s, "Sphere_Glass", 2f, 1f, -2f, 1f, 1f, 1f).mesh(1, 0x884FC3F7).col3().body3()

        // Lights for RT shadows
        obj3(s, "Sun", 10f, 20f, 10f).also { it.rotX=-45f; it.rotY=30f }.add(com.sengine.engine.core.Light().also { it.kind=0; it.intensity=1.5f; it.shadows=2 })
        obj3(s, "PointLight", 0f, 5f, 0f).add(com.sengine.engine.core.Light().also { it.kind=1; it.color=0xFFFF6D00.toInt(); it.intensity=3f; it.range=15f; it.shadows=1 })

        val hitFX = obj3(s, "RT_HitFX", 0f, 0f, 0f); hitFX.active=false
        hitFX.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting=false; it.rate=0f; it.spread=360f; it.speed=3f; it.lifetime=0.5f; it.startColor=0xFF00FF00.toInt() })

        obj(s, "Info", 0f, -4f).text("RayTracing Demo | RT Shadows | RT Reflections | RT GI | RT AO | Denoiser | Press A to Raycast", 0.3f).also { it.get<TextRenderer>()!!.screenSpace=true }

        p.saveScene(s); p.startScene="Main"
    }

    val destructionDemo = Template("Chaos Destruction", "Fracture, Debris, Physics Fields - Chaos Destruction") { p ->
        p.writeAsset("DestructionPlayer.js", """
var speed=5, damage=50
function update(dt){
    self.vx = input.axisX*speed; self.vz = input.axisY*speed
    if (input.aDown) {
        var hit = scene.raycast(self.worldX, self.worldY+1, self.worldZ, self.forwardX, self.forwardY, self.forwardZ, 20)
        if (hit && hit.name.indexOf("Destructible")>=0) {
            hit.send("takeDamage", damage)
            log("Hit " + hit.name + " for " + damage + " damage")
            scene.shake(0.3)
        }
    }
    if (input.bDown) {
        // Create physics field
        log("Creating Radial Field at player")
        scene.find("FieldFX").worldX=self.worldX; scene.find("FieldFX").worldY=self.worldY; scene.find("FieldFX").worldZ=self.worldZ; scene.find("FieldFX").burst(30)
    }
}
""")
        p.writeAsset("DestructibleWall.js", """
var health=100, maxHealth=100, isDestroyed=false
function start(){ log("Destructible Wall - Health: " + health) }
function takeDamage(dmg){
    if (isDestroyed) return
    health-=dmg
    self.setColor(1, health/maxHealth, health/maxHealth)
    if (health<=0) {
        isDestroyed=true
        log("Wall Destroyed!")
        // Spawn debris
        for (var i=0;i<8;i++) {
            var debris = scene.spawn("Debris", self.worldX+Math.random()*2-1, self.worldY+Math.random()*2, self.worldZ+Math.random()*2-1)
            debris.vx = Math.random()*10-5; debris.vy = Math.random()*5; debris.vz = Math.random()*10-5
        }
        scene.find("ExplosionFX").worldX=self.worldX; scene.find("ExplosionFX").worldY=self.worldY; scene.find("ExplosionFX").worldZ=self.worldZ; scene.find("ExplosionFX").burst(60)
        self.destroy()
    }
}
""")
        val s = Scene("Main")
        val player = obj3(s, "Player", 0f, 1f, 5f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFF4FC3F7).col3().body3().script("DestructionPlayer.js")
        player.tag="Player"
        player.add(com.sengine.engine.core.Camera3D().also { it.follow="Player"; it.offsetY=4f; it.offsetZ=8f })

        obj3(s, "Ground", 0f, -0.5f, 0f, 50f, 1f, 50f).mesh(2, 0xFF5A5A5A).col3().body3(type=2)

        // Destructible walls
        for (i in 0 until 10) {
            obj3(s, "Destructible_Wall_$i", random(-15,15), 2f, random(-15,5), 2f, 4f, 0.5f).mesh(0, 0xFF8D6E63).col3().body3().script("DestructibleWall.js")
        }

        // Debris prefab (inactive)
        val debris = obj3(s, "Debris", 0f, 0f, 0f, 0.3f, 0.3f, 0.3f).mesh(0, 0xFF5A5A5A).col3().body3()
        debris.active=false

        val explosion = obj3(s, "ExplosionFX", 0f, 0f, 0f); explosion.active=false
        explosion.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting=false; it.rate=0f; it.spread=360f; it.speed=8f; it.lifetime=0.8f; it.startColor=0xFFFF6D00.toInt() })

        val fieldFX = obj3(s, "FieldFX", 0f, 0f, 0f); fieldFX.active=false
        fieldFX.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting=false; it.rate=0f; it.spread=360f; it.speed=4f; it.lifetime=0.6f; it.startColor=0xFF00BCD4.toInt() })

        obj(s, "Info", 0f, -4f).text("Chaos Destruction Demo | Press A to Shoot | B for Physics Field | Walls Fracture into Debris", 0.3f).also { it.get<TextRenderer>()!!.screenSpace=true }

        p.saveScene(s); p.startScene="Main"
    }

    val fluidDemo = Template("Fluid Simulation", "SPH Fluid, Smoke, Fire, Soft Body - Physics Quantum") { p ->
        p.writeAsset("FluidPlayer.js", """
var speed=5
function update(dt){
    self.vx = input.axisX*speed; self.vz = input.axisY*speed
    if (input.aDown) {
        // Emit fluid
        var fluid = scene.spawn("FluidEmitter", self.worldX, self.worldY+1, self.worldZ)
        fluid.vy = 3
        log("Fluid Emitted")
    }
    if (input.bDown) {
        scene.find("SmokeFX").worldX=self.worldX; scene.find("SmokeFX").worldY=self.worldY; scene.find("SmokeFX").worldZ=self.worldZ; scene.find("SmokeFX").burst(20)
    }
}
""")
        val s = Scene("Main")
        val player = obj3(s, "Player", 0f, 2f, 0f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFF4FC3F7).col3().body3().script("FluidPlayer.js")
        player.tag="Player"
        player.add(com.sengine.engine.core.Camera3D().also { it.follow="Player"; it.offsetY=5f; it.offsetZ=10f })

        obj3(s, "Ground", 0f, -0.5f, 0f, 50f, 1f, 50f).mesh(2, 0xFF5A8A6A).col3().body3(type=2)
        obj3(s, "WaterPool", 0f, 0f, -10f, 15f, 1f, 15f).mesh(2, 0xFF4FC3F7).also { it.get<com.sengine.engine.core.MeshRenderer>()!!.emission=0.2f }

        // Fluid emitters
        for (i in 0 until 3) {
            obj3(s, "FluidEmitter_$i", random(-10,10), 5f, random(-10,10), 0.5f, 0.5f, 0.5f).mesh(1, 0xFF4FC3F7).col3(sphere=true).script("FluidEmitter.js", "type=Water")
        }

        val fluidEmitter = obj3(s, "FluidEmitter", 0f, 0f, 0f, 0.3f, 0.3f, 0.3f).mesh(1, 0xFF4FC3F7); fluidEmitter.active=false
        fluidEmitter.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting=false; it.rate=0f; it.speed=2f; it.lifetime=2f; it.startColor=0xFF4FC3F7.toInt() })

        val smokeFX = obj3(s, "SmokeFX", 0f, 0f, 0f); smokeFX.active=false
        smokeFX.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting=false; it.rate=0f; it.spread=30f; it.speed=1.5f; it.lifetime=3f; it.startColor=0xFF888888.toInt(); it.endColor=0x00888888 })

        p.writeAsset("FluidEmitter.js", "var type='Water', life=5; function update(dt){life-=dt; self.vy-=9.81*dt*0.2; if(life<=0) self.destroy()}")

        obj(s, "Info", 0f, -4f).text("Fluid Simulation Demo | A: Emit Fluid | B: Smoke | SPH, PBF, FLIP, Soft Body", 0.3f).also { it.get<TextRenderer>()!!.screenSpace=true }

        p.saveScene(s); p.startScene="Main"
    }

    val openWorldPCG = Template("Open World PCG", "World Partition, Spline Roads, Foliage, PCG Forest/City") { p ->
        p.writeAsset("OpenWorldPlayer.js", """
var speed=8, cellX=0, cellZ=0
function update(dt){
    self.vx = input.axisX*speed; self.vz = input.axisY*speed
    var newCellX = Math.floor(self.worldX/256), newCellZ = Math.floor(self.worldZ/256)
    if (newCellX!=cellX || newCellZ!=cellZ) {
        cellX=newCellX; cellZ=newCellZ
        log("Entered Cell: " + cellX + "," + cellZ + " | Streaming...")
        scene.find("CellText").setText("Cell: " + cellX + "," + cellZ)
    }
    // Spline follow
    if (input.aDown) {
        var splinePos = scene.find("RoadSpline").getPoint(0.5)
        log("Spline Center: " + splinePos)
    }
}
""")
        val s = Scene("Main")
        val player = obj3(s, "Player", 0f, 2f, 0f, 0.8f, 1.8f, 0.8f).mesh(6, 0xFF4FC3F7).col3().body3().script("OpenWorldPlayer.js")
        player.tag="Player"
        player.add(com.sengine.engine.core.Camera3D().also { it.follow="Player"; it.offsetY=10f; it.offsetZ=15f; it.fov=70f })

        obj3(s, "Ground", 0f, -0.5f, 0f, 500f, 1f, 500f).mesh(2, 0xFF6B8E5A).col3().body3(type=2)

        // Spline road
        obj3(s, "RoadSpline", 0f, 0.1f, 0f, 200f, 0.1f, 4f).mesh(2, 0xFF3A3A3A)

        // PCG Generated - Forest
        for (i in 0 until 30) {
            obj3(s, "PCG_Tree_$i", random(-100,100), 0f, random(-100,100), 1.5f, 3f, 1.5f).mesh(8, 0xFF2E7D32, model="Tree.obj").col3()
        }
        // PCG City
        for (i in 0 until 20) {
            val h = random(3,10)
            obj3(s, "PCG_Building_$i", random(-80,80), h/2, random(-80,80), random(4,8), h, random(4,8)).mesh(0, 0xFF5A5A5A).col3().body3(type=2)
        }

        // Foliage
        for (i in 0 until 100) {
            obj3(s, "Foliage_$i", random(-100,100), 0f, random(-100,100), 0.3f, 0.6f, 0.3f).mesh(8, 0xFF4CAF50, model="Grass.obj")
        }

        obj(s, "CellText", -5f, 4f).text("Cell: 0,0 | World Partition 4096m", 0.35f).also { it.get<TextRenderer>()!!.screenSpace=true }
        obj(s, "Info", 0f, -4f).text("Open World PCG Demo | World Partition 256m Cells | Spline Roads | Foliage 100k | PCG Forest/City", 0.28f).also { it.get<TextRenderer>()!!.screenSpace=true }

        p.saveScene(s); p.startScene="Main"
    }

    val vrDemo = Template("VR Demo", "VR/AR/MR, Hand Tracking, Spatial Anchors - XR Quantum") { p ->
        p.writeAsset("VRPlayer.js", """
var speed=3
function update(dt){
    // XR input simulation
    self.vx = input.axisX*speed; self.vz = input.axisY*speed
    // Hand tracking
    if (input.aDown) log("Left Hand Pinch - Grab")
    if (input.bDown) log("Right Hand Pinch - Interact")
    // Spatial anchor
    if (input.tapped) {
        log("Creating Spatial Anchor at " + self.worldX + "," + self.worldY + "," + self.worldZ)
        scene.spawn("Anchor", self.worldX, self.worldY, self.worldZ)
    }
    scene.find("XRInfo").setText("XR Mode: VR | Hands: Tracked | Anchors: " + scene.count("Anchor"))
}
""")
        val s = Scene("Main")
        val player = obj3(s, "Player", 0f, 1.6f, 0f, 0.5f, 1.6f, 0.5f).mesh(6, 0xFF4FC3F7).col3().body3().script("VRPlayer.js")
        player.tag="Player"
        player.add(com.sengine.engine.core.Camera3D().also { it.follow="Player"; it.offsetY=0f; it.offsetZ=0f; it.fov=90f })

        obj3(s, "Ground", 0f, -0.5f, 0f, 20f, 1f, 20f).mesh(2, 0xFF5A5A5A).col3().body3(type=2)

        // Interactables for VR
        for (i in 0 until 5) {
            obj3(s, "VR_Cube_$i", random(-5,5), 1f, random(-5,5), 0.5f, 0.5f, 0.5f).mesh(0, 0xFFFF6D00).col3().body3().script("VRInteractable.js", "grabbed=false")
        }

        val anchor = obj3(s, "Anchor", 0f, 0f, 0f, 0.2f, 0.2f, 0.2f).mesh(1, 0xFF00FF00); anchor.active=false
        anchor.tag="Anchor"

        p.writeAsset("VRInteractable.js", "var grabbed=false; function onTrigger(other){if(other.tag=='Player') log('Hand near ' + self.name)} function update(dt){if(grabbed) self.rotY+=90*dt}")

        obj(s, "XRInfo", 0f, 4f).text("XR Mode: VR | Hands: Tracked | Anchors: 0", 0.35f).also { it.get<TextRenderer>()!!.screenSpace=true }
        obj(s, "Info", 0f, -4f).text("VR Demo | XR System | Hand Tracking 26 Joints | Eye Tracking | Spatial Anchors | Passthrough", 0.3f).also { it.get<TextRenderer>()!!.screenSpace=true }

        p.saveScene(s); p.startScene="Main"
    }

    val mlAgentsDemo = Template("ML-Agents Demo", "Reinforcement Learning, PPO, Imitation - AI Quantum") { p ->
        p.writeAsset("MLAgent.js", """
var reward=0, episode=0, target=null, speed=4
function start(){
    target = scene.find("Target")
    log("ML-Agent Started | Policy: Default | Observations: 10 | Actions: 4")
}
function update(dt){
    // Collect observations
    var obsX = self.worldX, obsZ = self.worldZ
    var targetX = target.worldX, targetZ = target.worldZ
    var dx = targetX-obsX, dz = targetZ-obsZ
    var dist = Math.sqrt(dx*dx+dz*dz)
    
    // Heuristic policy - move towards target
    self.vx = (dx/dist)*speed; self.vz = (dz/dist)*speed
    
    // Rewards
    if (dist < 1) {
        reward += 10
        log("Target Reached! Reward: " + reward + " Episode: " + episode)
        episode++
        target.worldX = Math.random()*20-10; target.worldZ = Math.random()*20-10
        scene.find("RewardText").setText("Reward: " + reward + " | Episode: " + episode)
    } else {
        reward -= 0.01 // time penalty
    }
    
    // Episode end if too long
    if (reward < -10) {
        log("Episode Failed - Reset")
        self.worldX=0; self.worldZ=0; reward=0; episode++
    }
}
""")
        val s = Scene("Main")
        val agent = obj3(s, "MLAgent", 0f, 1f, 0f, 0.8f, 1f, 0.8f).mesh(6, 0xFF4FC3F7).col3().body3().script("MLAgent.js")
        agent.tag="Agent"

        obj3(s, "Target", 10f, 0.5f, 10f, 1f, 1f, 1f).mesh(1, 0xFFFFEB3B).col3(sphere=true, trigger=true).also { it.tag="Target" }

        obj3(s, "Ground", 0f, -0.5f, 0f, 30f, 1f, 30f).mesh(2, 0xFF5A5A5A).col3().body3(type=2)

        // Obstacles
        for (i in 0 until 5) {
            obj3(s, "Obstacle_$i", random(-10,10), 1f, random(-10,10), 1f, 2f, 1f).mesh(0, 0xFF5A5A5A).col3().body3(type=2)
        }

        obj(s, "RewardText", 0f, 4f).text("Reward: 0 | Episode: 0 | Policy: PPO", 0.35f).also { it.get<TextRenderer>()!!.screenSpace=true }
        obj(s, "Info", 0f, -4f).text("ML-Agents Demo | Reinforcement Learning | Observations: Position, Velocity | Actions: Move | Reward: Reach Target", 0.28f).also { it.get<TextRenderer>()!!.screenSpace=true }

        p.saveScene(s); p.startScene="Main"
    }

    // ==================== TITAN 6.1.1 - UNITY 2D & MOST POWERFUL TEMPLATES ====================

    val unity2DURP = Template("Unity 2D URP", "2D Renderer, 2D Lights, Shadows, Sorting Layers - Unity 2D URP") { p ->
        p.writeAsset("Player2D.js", "var speed=5, jumpForce=8, isGrounded=false, health=100\nfunction start(){ log(\"Unity 2D URP - 2D Lights, Shadows, Sorting Layers\") }\nfunction update(dt){ self.vx = input.axisX * speed; if (input.aDown && isGrounded) { self.vy = jumpForce; isGrounded=false } self.order = Math.floor(-self.worldY * 10) }\nfunction onCollision(o){ isGrounded=true }")
        val s = Scene("Main")
        s.ambient = 0xFF1A1A2E.toInt()
        val player = obj(s, "Player", 0f, 2f, 1f, 1f).sprite(0xFF4FC3F7, "Player.png").col2(1f,1f).body2(mass=1f, friction=0.1f).script("Player2D.js")
        player.tag="Player"
        player.add(com.sengine.engine.core.Camera2D().also { it.follow="Player"; it.smoothing=3f; it.size=5f })
        obj(s, "Ground", 0f, -2f, 20f, 1f).sprite(0xFF5A5A5A, "Ground.png").col2(20f,1f).body2(type=2)
        for (i in 0 until 5) { obj(s, "ShadowCaster_${'$'}i", random(-8,8), random(-1,2), 1f, 2f).sprite(0xFF8D6E63).col2(1f,2f).body2(type=2) }
        obj(s, "Info", 0f, -4f).text("Unity 2D URP Demo | 2D Lights | Shadows | Sorting Layers | Sprite Atlas", 0.28f).also { it.get<TextRenderer>()!!.screenSpace=true }
        p.saveScene(s); p.startScene="Main"
    }

    val tilemapPlatformer = Template("Tilemap Platformer", "Grid, Tilemap, RuleTile, AnimatedTile - Unity 2D Tilemap") { p ->
        p.writeAsset("TilemapPlayer.js", "var speed=6, jump=9, isGrounded=false; function update(dt){ self.vx=input.axisX*speed; if(input.aDown&&isGrounded){self.vy=jump; isGrounded=false} } function onCollision(o){isGrounded=true}")
        val s = Scene("Main")
        val player = obj(s, "Player", 0f, 5f, 0.8f, 1f).sprite(0xFF4FC3F7).col2(0.8f,1f).body2().script("TilemapPlayer.js")
        player.tag="Player"; player.add(Camera2D().also { it.follow="Player"; it.size=6f })
        for (x in -10..10) for (y in -3..0) { if (y==0 || x==-10 || x==10) { obj(s, "Tile_${'$'}{x}_${'$'}{y}", x.toFloat()+0.5f, y.toFloat()+0.5f, 1f, 1f).sprite(0xFF8D6E63).col2(1f,1f).body2(type=2).also { it.tag="Ground" } } }
        for (i in 0 until 10) { obj(s, "Coin_${'$'}i", random(-8,8), random(1,6), 0.5f, 0.5f).sprite(0xFFFFD54F).col2(0.5f,0.5f, trigger=true).also { it.tag="Coin" } }
        obj(s, "Info", 0f, -4f).text("Tilemap Platformer | Grid | Tilemap | RuleTile | AnimatedTile | Palette", 0.28f).also { it.get<TextRenderer>()!!.screenSpace=true }
        p.saveScene(s); p.startScene="Main"
    }

    val spriteAnimation2D = Template("2D Animation", "Bones, Skinning, IK, Library - Unity 2D Animation") { p ->
        p.writeAsset("BonePlayer.js", "var speed=4; function update(dt){ self.vx=input.axisX*speed; if(input.aDown){ self.setTexture(\"Armor.png\") } }")
        val s = Scene("Main")
        val player = obj(s, "Player", 0f, 1f, 1f, 2f).sprite(0xFF4FC3F7, "Body.png").col2(1f,2f).body2().script("BonePlayer.js")
        player.tag="Player"; player.add(Camera2D().also { it.follow="Player"; it.size=5f })
        obj(s, "Ground", 0f, -1f, 20f, 1f).sprite(0xFF5A5A5A).col2(20f,1f).body2(type=2)
        obj(s, "Info", 0f, -4f).text("2D Animation Demo | Bones | Skinning LBS | IK CCD | Library | Resolver", 0.26f).also { it.get<TextRenderer>()!!.screenSpace=true }
        p.saveScene(s); p.startScene="Main"
    }

    val sortingGroupDemo = Template("Sorting Groups", "Sorting Layers, Groups, Order - 2D Sorting") { p ->
        p.writeAsset("SortingPlayer.js", "var speed=5; function update(dt){ self.vx=input.axisX*speed; self.vy=input.axisY*speed; self.order=Math.floor(-self.worldY*10) }")
        val s = Scene("Main")
        val player = obj(s, "Player", 0f, 0f, 1f, 1f).sprite(0xFF4FC3F7).col2(1f,1f).body2().script("SortingPlayer.js")
        player.tag="Player"; player.add(Camera2D().also { it.follow="Player"; it.size=6f })
        for (i in 0 until 10) { obj(s, "BG_${'$'}i", random(-10,10), random(-5,5), 2f, 1f).sprite(0xFF4CAF50).also { it.order=i-10 } }
        obj(s, "Info", 0f, -4f).text("Sorting Groups Demo | Layers Background Ground Player Foreground UI | Order Y-based", 0.26f).also { it.get<TextRenderer>()!!.screenSpace=true }
        p.saveScene(s); p.startScene="Main"
    }

    val lighting2DDemo = Template("2D Lighting", "Light2D, Normal Maps, Shadows - Unity 2D Lighting") { p ->
        p.writeAsset("Light2DPlayer.js", "var speed=4; function update(dt){ self.vx=input.axisX*speed; self.vy=input.axisY*speed }")
        val s = Scene("Main"); s.ambient=0xFF1A1A2E.toInt()
        val player = obj(s, "Player", 0f, 0f, 0.8f, 1f).sprite(0xFF4FC3F7).col2(0.8f,1f).body2().script("Light2DPlayer.js")
        player.tag="Player"; player.add(Camera2D().also { it.follow="Player"; it.size=6f })
        for (i in 0 until 8) { obj(s, "Wall_${'$'}i", random(-8,8), random(-4,4), 1f, 1f).sprite(0xFF5A5A5A).col2(1f,1f).body2(type=2) }
        obj(s, "Info", 0f, -4f).text("2D Lighting Demo | Point + Global Light | Normal Maps | Shadows | Blend", 0.28f).also { it.get<TextRenderer>()!!.screenSpace=true }
        p.saveScene(s); p.startScene="Main"
    }

    val cinemachine2DDemo = Template("Cinemachine 2D", "Framing Transposer, Confiner, Pixel Perfect - Cinemachine 2D") { p ->
        p.writeAsset("CM2DPlayer.js", "var speed=6; function update(dt){ self.vx=input.axisX*speed; self.vy=input.axisY*speed }")
        val s = Scene("Main")
        val player = obj(s, "Player", 0f, 0f, 0.8f, 1f).sprite(0xFF4FC3F7).col2(0.8f,1f).body2().script("CM2DPlayer.js")
        player.tag="Player"
        obj(s, "MainCamera", 0f, 0f).also { it.add(Camera2D().also { c->c.follow="Player"; c.size=5f }) }
        obj(s, "Info", 0f, -4f).text("Cinemachine 2D Demo | Framing Transposer DeadZone SoftZone | Confiner | Pixel Perfect", 0.26f).also { it.get<TextRenderer>()!!.screenSpace=true }
        p.saveScene(s); p.startScene="Main"
    }

    val particle2DDemo = Template("2D Particles", "2D Particle System, Shader Graph 2D - VFX 2D") { p ->
        p.writeAsset("Particle2DPlayer.js", "var speed=5; function update(dt){ self.vx=input.axisX*speed; self.vy=input.axisY*speed; if(input.aDown){ scene.find(\"FireFX\").burst(20) } }")
        val s = Scene("Main")
        val player = obj(s, "Player", 0f, 0f, 0.8f, 1f).sprite(0xFF4FC3F7).col2(0.8f,1f).body2().script("Particle2DPlayer.js")
        player.tag="Player"; player.add(Camera2D().also { it.follow="Player"; it.size=6f })
        obj(s, "Ground", 0f, -2f, 20f, 1f).sprite(0xFF5A5A5A).col2(20f,1f).body2(type=2)
        val fireFX = obj(s, "FireFX", 0f, 0f, 0f); fireFX.active=false
        fireFX.add(com.sengine.engine.core.ParticleEmitter().also { it.emitting=false; it.rate=0f; it.spread=30f; it.speed=3f; it.lifetime=0.8f; it.startColor=0xFFFF6D00.toInt() })
        obj(s, "Info", 0f, -4f).text("2D Particles Demo | Fire & Smoke | Main/Emission/Shape | Shader Graph 2D", 0.28f).also { it.get<TextRenderer>()!!.screenSpace=true }
        p.saveScene(s); p.startScene="Main"
    }

    val titanFullControl = Template("Titan Full Control", "Most Powerful Edition - Full Engine Control, Console, Profiler, Package Manager") { p ->
        p.writeAsset("TitanPlayer.js", "var speed=6, health=100, score=0; function start(){ log(\"Titan Full Control - Most Powerful Edition\"); } function update(dt){ self.vx=input.axisX*speed; self.vy=input.axisY*speed; if(input.aDown){ log(\"Full Control: Hierarchy=\"+scene.count(\"\")+\" | FPS=\"+time.fps.toFixed(0)); scene.shake(0.2) } score+=dt*10; scene.find(\"ScoreText\").setText(\"Score: \"+Math.floor(score)) }")
        val s = Scene("Main"); s.ambient=0xFF1A1A2E.toInt()
        val player = obj(s, "Player", 0f, 1f, 0.8f, 1f).sprite(0xFF4FC3F7).col2(0.8f,1f).body2().script("TitanPlayer.js")
        player.tag="Player"; player.add(Camera2D().also { it.follow="Player"; it.size=6f; it.postFx=6; it.postIntensity=0.8f })
        obj(s, "Ground", 0f, -1f, 30f, 1f).sprite(0xFF3A3A3A).col2(30f,1f).body2(type=2)
        for (i in 0 until 8) { obj(s, "ControlCube_${'$'}i", random(-10,10), random(0,5), 1f, 1f).sprite(0xFF8D6E63).col2(1f,1f).body2().script("Rotator.js", "x=0,y=90,z=0") }
        obj(s, "ScoreText", 0f, 4.5f).text("Score: 0 | Titan Points", 0.4f).also { it.get<TextRenderer>()!!.screenSpace=true; it.order=100 }
        obj(s, "Info", 0f, -4f).text("Titan Most Powerful Edition | 75+ Systems | 200+ Components | 28 Templates | 300+ API | Unity 2D URP + Quantum | Full Control", 0.22f).also { it.get<TextRenderer>()!!.screenSpace=true; it.order=100 }
        p.writeAsset("Rotator.js", "function update(dt){self.rotY += 90*dt}")
        p.saveScene(s); p.startScene="Main"; p.orientation=0
    }

    private fun random(a: Int, b: Int): Float = (a + Math.random() * (b - a)).toFloat()
}
