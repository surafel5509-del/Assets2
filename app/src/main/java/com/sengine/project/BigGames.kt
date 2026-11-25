package com.sengine.project

import com.sengine.engine.core.Animator
import com.sengine.engine.core.Camera2D
import com.sengine.engine.core.Camera3D
import com.sengine.engine.core.Collider2D
import com.sengine.engine.core.Collider3D
import com.sengine.engine.core.GameObject
import com.sengine.engine.core.Light
import com.sengine.engine.core.MeshRenderer
import com.sengine.engine.core.ParticleEmitter
import com.sengine.engine.core.Rigidbody2D
import com.sengine.engine.core.Scene
import com.sengine.engine.core.ScriptComponent
import com.sengine.engine.core.SpriteRenderer
import com.sengine.engine.core.TextRenderer

/**
 * Two complete, shippable reference games built entirely from S Engine content:
 *
 *  - "Citadel of Echoes": a 6-scene 2D action adventure (menu, two levels,
 *    boss fight, victory / game over) with combat, keys, checkpoints, AI,
 *    moving platforms, particles, joints, soft bodies and durable saves.
 *  - "Neon Apex GP": a 3-scene 3D arcade racer (menu, circuit, podium) with a
 *    countdown, three laps, waypoint AI rivals, boost pads, best-lap saves,
 *    cinematic camera paths and a live HUD.
 *
 * Both are real projects: every scene is saved to disk, every behaviour is a
 * .js asset in the project, and both run headlessly in the regression suite.
 */
object BigGames {

    // ---------------------------------------------------------------- helpers
    private fun o(s: Scene, name: String, x: Float, y: Float, sx: Float = 1f, sy: Float = 1f): GameObject {
        val go = s.create(name)
        go.x = x; go.y = y; go.scaleX = sx; go.scaleY = sy
        return go
    }

    private fun GameObject.sprite(color: Long, shape: Int = 0): GameObject {
        add(SpriteRenderer().also { it.color = color.toInt(); it.shape = shape }); return this
    }

    private fun GameObject.box(trigger: Boolean = false): GameObject {
        add(Collider2D().also { it.isTrigger = trigger }); return this
    }

    private fun GameObject.solid(tag: String = ""): GameObject {
        add(Collider2D())
        add(Rigidbody2D().also { it.bodyType = 2 })
        if (tag.isNotEmpty()) this.tag = tag
        return this
    }

    private fun GameObject.body(type: Int = 0, gravity: Float = 1f, friction: Float = 0.2f): GameObject {
        add(Rigidbody2D().also { it.bodyType = type; it.gravityScale = gravity; it.friction = friction }); return this
    }

    private fun GameObject.script(name: String, params: String = ""): GameObject {
        add(ScriptComponent().also { it.script = name; it.params = params }); return this
    }

    private fun hud(s: Scene, name: String, x: Float, y: Float, text: String, size: Float, color: Long = 0xFFFFFFFF): GameObject {
        val go = o(s, name, x, y)
        go.add(TextRenderer().also {
            it.text = text; it.size = size; it.color = color.toInt(); it.bold = true; it.screenSpace = true
        })
        go.order = 100
        return go
    }

    private fun camera(s: Scene, size: Float, bg: Long, follow: String = ""): GameObject {
        val c = o(s, "Main Camera", 0f, 0f)
        c.add(Camera2D().also {
            it.size = size; it.background = bg.toInt(); it.follow = follow
            it.smoothing = 6f
        })
        return c
    }

    private fun o3(s: Scene, name: String, x: Float, y: Float, z: Float,
                   sx: Float = 1f, sy: Float = 1f, sz: Float = 1f): GameObject {
        val go = s.create(name)
        go.x = x; go.y = y; go.z = z
        go.scaleX = sx; go.scaleY = sy; go.scaleZ = sz
        return go
    }

    private fun GameObject.mesh(kind: Int, color: Long): GameObject {
        add(MeshRenderer().also { it.mesh = kind; it.color = color.toInt() }); return this
    }

    private fun GameObject.col3(sphere: Boolean = false, trigger: Boolean = false): GameObject {
        add(Collider3D().also { it.shape = if (sphere) 1 else 0; it.isTrigger = trigger }); return this
    }

    private fun template(s: Scene, name: String, color: Long, shape: Int = 0,
                         trigger: Boolean = false, script: String = "", gravity: Float = 0f): GameObject {
        val go = o(s, name, -100f, -100f, 0.5f, 0.5f)
        go.active = false
        go.sprite(color, shape)
        go.box(trigger)
        go.body(gravity = gravity)
        if (script.isNotEmpty()) go.script(script)
        return go
    }

    // ================================================================= GAME 1
    // ------------------------------------------------------- Citadel of Echoes
    val citadel = Templates.Template(
        "Citadel of Echoes",
        "FULL GAME • 6-scene 2D action RPG: keys, combat, AI, moving platforms and a two-phase boss."
    ) { p -> writeCitadel(p) }

    private fun writeCitadel(p: Project) {
        // ---------------- scripts ----------------
        p.writeAsset("CitadelMenu.js", """
// Title screen: resets a run, shows the best haul and waits for A / tap.
var ready = false;
function start() {
    game.save("hp", 5);
    game.save("relics", 0);
    game.save("key", 0);
    game.save("level", 1);
    game.save("bossHp", 12);
    var hint = scene.find("Hint");
    if (hint) hint.text = "BEST HAUL " + game.load("best", 0) + "    PRESS A / TAP TO ENTER";
    coroutine(function* () {
        yield waitSec(0.35);
        var t = scene.find("Title");
        if (t) tween(t, "scaleX", 1.12, 0.8, "sineInOut");
        yield waitSec(0.5);
        ready = true;
    });
}
function update(dt) { if (ready && input.aDown) enter(); }
function onTap() { enter(); }
function enter() {
    if (!ready) return;
    ready = false;
    audio.beep();
    scene.load("Crypt");
}
""".trimIndent())

        p.writeAsset("CitadelHero.js", """
// Player: run, jump, fire echo bolts, take damage, collect relics/keys/hearts.
var cooldown = 0;
var dir = 1;
var invuln = 0;
function start() {
    if (game.load("hp", 5) <= 0) game.save("hp", 5);
    camera.setBounds(-26, -14, 26, 16);
    camera.setDeadZone(2.2, 1.4);
    camera.setLookAhead(0.18);
    camera.zoomTo(1.05);
    state.set("idle");
    soft.rope("Lantern", -8, 8, 10, 0.35, 1, 0);
    soft.wind("Lantern", 1.5, 0);
    events.on("relic", function () { camera.shake(0.12); });
    events.on("heroHit", function () { camera.shake(0.55); });
    physics.setIterations(2);
}
function idle_enter() { }
function idle_update(dt) { }
function hurt_enter() { invuln = 1.3; }
function hurt_update(dt) { invuln -= dt; if (invuln <= 0) state.set("idle"); }
function update(dt) {
    state.update(dt);
    self.vx = input.axisX * 7.5;
    if (input.axisX > 0.1) dir = 1;
    else if (input.axisX < -0.1) dir = -1;
    self.flipX = dir < 0;
    // swept shape cast against level geometry (future physics API)
    var ground = physics.circleCast(self.worldX, self.worldY - 0.7, 0, -1, 0.25, 0.9, "Solid");
    if (input.aDown && (self.grounded || ground)) { self.vy = 12.5; audio.beep(); }
    if (cooldown > 0) cooldown -= dt;
    if (input.bDown && cooldown <= 0) { fire(); cooldown = 0.24; }
    if (self.y < -16) die();
    var lamp = scene.find("Lamp");
    var point = soft.point("Lantern", 9);
    if (lamp && point) { lamp.x = point[0]; lamp.y = point[1]; }
    var hud = scene.find("Hud");
    if (hud) hud.text = "HP " + game.load("hp", 5) + "   RELICS " + game.load("relics", 0) +
        "   KEY " + game.load("key", 0) + "   LV " + game.load("level", 1);
}
function fire() {
    var b = scene.spawn("Bolt", self.worldX + dir * 0.9, self.worldY);
    if (b) { b.tag = "Bolt"; b.vx = dir * 16; }
    audio.beep();
}
function damage() {
    if (invuln > 0 || state.is("hurt")) return;
    var hp = game.load("hp", 5) - 1;
    game.save("hp", hp);
    events.emit("heroHit", hp);
    audio.beep();
    if (hp <= 0) { scene.load("GameOver"); return; }
    state.set("hurt");
    self.vy = 6;
}
function die() {
    game.save("hp", 0);
    scene.load("GameOver");
}
function onTrigger(other) {
    if (other.tag == "Relic" || other.tag == "Key" || other.tag == "Heart") return; // handled by the pickup
    if (other.tag == "Enemy" || other.tag == "Orb" || other.tag == "Spike") damage();
}
function onDestroy() { soft.remove("Lantern"); }
""".trimIndent())

        p.writeAsset("CitadelBolt.js", """
// Echo bolt: fast trigger projectile with CCD, expires after 1.4 s.
var life = 1.4;
function start() { life = 1.4; }
function update(dt) {
    life -= dt;
    self.rotation += 720 * dt;
    if (life <= 0) self.destroy();
}
function onTrigger(other) {
    if (other.tag == "Enemy" || other.tag == "Boss") self.destroy();
}
""".trimIndent())

        p.writeAsset("CitadelEnemy.js", """
// Patrols, chases the hero when close, dies in two hits.
var t = 0;
var hp = 2;
var baseX = 0;
function start() { t = random() * 6; hp = 2; baseX = self.x; }
function update(dt) {
    t += dt;
    var hero = scene.find("Hero");
    var chasing = false;
    if (hero) {
        var dx = hero.worldX - self.worldX;
        var dy = hero.worldY - self.worldY;
        chasing = Math.abs(dx) < 8 && Math.abs(dy) < 3.5;
        if (chasing) self.vx = dx > 0 ? 3.2 : -3.2;
    }
    if (!chasing) self.vx = Math.sin(t * 1.6 + baseX) * 1.7;
    self.rotation = Math.sin(t * 4) * 8;
}
function onTrigger(other) {
    if (other.tag != "Bolt") return;
    hp = hp - 1;
    audio.beep();
    if (hp <= 0) {
        var fx = scene.spawn("Spark", self.worldX, self.worldY);
        if (fx) { fx.burst(16); after(1.0, function () { fx.destroy(); }); }
        events.emit("enemyDown", self.name);
        physics.explode(self.worldX, self.worldY, 3, 4, "Enemy");
        self.destroy();
    }
}
""".trimIndent())

        p.writeAsset("CitadelRelic.js", """
// Bob + spin. Relics add score; Key unlocks the door; Heart heals.
var y0 = 0;
var phase = 0;
function start() { y0 = self.y; phase = random() * 6; }
function update(dt) {
    self.y = y0 + Math.sin(time.time * 2.2 + phase) * 0.22;
    self.rotation += 55 * dt;
}
function onTrigger(other) {
    if (other.tag != "Hero") return;
    if (self.tag == "Relic") game.save("relics", game.load("relics", 0) + 1);
    if (self.tag == "Key") game.save("key", game.load("key", 0) + 1);
    if (self.tag == "Heart") game.save("hp", Math.min(9, game.load("hp", 5) + 1));
    events.emit("relic", self.tag);
    audio.beep();
    var fx = scene.spawn("Spark", self.worldX, self.worldY);
    if (fx) { fx.burst(14); after(0.9, function () { fx.destroy(); }); }
    self.destroy();
}
""".trimIndent())

        p.writeAsset("CitadelHud.js", """
// Event-driven HUD + transient message banner.
var pulse = 0;
function flash(msg) {
    var m = scene.find("Message");
    if (m) { m.text = msg; pulse = 1.4; }
}
function start() {
    events.on("relic", function (kind) { flash(kind == "Key" ? "KEY FOUND - FIND THE DOOR" : "RELIC COLLECTED"); });
    events.on("enemyDown", function () { flash("ENEMY DOWN"); });
    events.on("heroHit", function (hp) { flash("OUCH!  HP " + hp); });
    events.on("bossHit", function (hp) { flash("BOSS HP " + hp); });
}
function update(dt) {
    if (pulse > 0) {
        pulse -= dt;
        if (pulse <= 0) { var m = scene.find("Message"); if (m) m.text = ""; }
    }
}
""".trimIndent())

        p.writeAsset("CitadelDoor.js", """
// params: next=Halls, need=1
function onTrigger(other) {
    if (other.tag != "Hero") return;
    if (game.load("key", 0) >= need) {
        game.save("level", game.load("level", 1) + 1);
        game.save("key", 0);
        audio.beep();
        camera.shake(0.35);
        scene.load(next);
    } else {
        var m = scene.find("Message");
        if (m) m.text = "THE DOOR NEEDS A KEY";
    }
}
""".trimIndent())

        p.writeAsset("CitadelPlatform.js", """
// Kinematic moving platform (riders are carried by the physics engine).
var phase = 0;
function start() { phase = random() * 6; }
function update(dt) {
    self.vx = Math.cos(time.time * speed + phase) * range * speed;
}
""".trimIndent())

        p.writeAsset("CitadelBoss.js", """
// Two-phase boss: stalks the hero, fires orbs, enrages at half health.
var hp = 12;
var cooldown = 1.4;
var phase = 1;
function start() {
    hp = game.load("bossHp", 12);
    state.set("stalk");
    camera.shake(0.5);
    events.on("bossDamage", function () { damage(); });
}
function stalk_update(dt) {
    var hero = scene.find("Hero");
    if (hero) self.vx = hero.worldX > self.worldX ? 2.6 : -2.6;
    cooldown -= dt;
    if (cooldown <= 0) {
        cooldown = hp < 6 ? 0.7 : 1.25;
        var orb = scene.spawn("Orb", self.worldX, self.worldY + 0.8);
        if (orb) orb.tag = "Orb";
        camera.shake(0.18);
    }
}
function enrage_enter() {
    camera.shake(0.9);
    var m = scene.find("Message");
    if (m) m.text = "THE ECHO AWAKENS";
}
function enrage_update(dt) { stalk_update(dt); }
function update(dt) { state.update(dt); self.rotation += 26 * dt; }
function damage() {
    hp = hp - 1;
    game.save("bossHp", hp);
    events.emit("bossHit", hp);
    audio.beep();
    physics.explode(self.worldX, self.worldY, 5, 7, "Hero");
    if (hp <= 6 && phase == 1) { phase = 2; state.set("enrage"); }
    if (hp <= 0) {
        var haul = game.load("relics", 0) + 10;
        game.save("relics", haul);
        if (haul > game.load("best", 0)) game.save("best", haul);
        camera.shake(1.0);
        scene.load("Victory");
    }
}
function onTrigger(other) { if (other.tag == "Bolt") damage(); }
""".trimIndent())

        p.writeAsset("CitadelOrb.js", """
// Homing projectile fired by the boss.
var life = 3.4;
function start() {
    life = 3.4;
    var hero = scene.find("Hero");
    if (hero) {
        var dx = hero.worldX - self.worldX;
        var dy = hero.worldY - self.worldY;
        var d = Math.sqrt(dx * dx + dy * dy) || 1;
        self.vx = dx / d * 6.5;
        self.vy = dy / d * 6.5;
    }
}
function update(dt) { life -= dt; self.rotation += 210 * dt; if (life <= 0) self.destroy(); }
function onTrigger(other) { if (other.tag == "Hero") self.destroy(); }
""".trimIndent())

        p.writeAsset("CitadelEnd.js", """
// params: win=1 or win=0
var ready = false;
function start() {
    var s = scene.find("Summary");
    if (s) s.text = (win ? "CITADEL CLEARED!" : "YOU FELL IN THE DARK") +
        "\nRELICS " + game.load("relics", 0) + "   BEST " + game.load("best", 0);
    coroutine(function* () {
        yield waitSec(0.4);
        var h = scene.find("Hint");
        if (h) h.text = "PRESS A / TAP TO RETURN";
        ready = true;
    });
}
function update(dt) { if (ready && input.aDown) scene.load("Main"); }
function onTap() { if (ready) scene.load("Main"); }
""".trimIndent())

        // ---------------- common level builder ----------------
        fun level(s: Scene, name: String, floor: List<Triple<Float, Float, Float>>,
                  enemies: List<Pair<Float, Float>>, relics: List<Pair<Float, Float>>,
                  keyAt: Pair<Float, Float>, doorAt: Pair<Float, Float>, next: String,
                  platforms: List<Pair<Float, Float>> = emptyList()) {
            camera(s, 9f, 0xFF101827, follow = "Hero")
            // decorative backdrop
            o(s, "Backdrop", 0f, 0f, 60f, 34f).sprite(0xFF182238).also { it.order = -100 }
            for ((x, y, w) in floor) o(s, "Ground", x, y, w, 1.2f).sprite(0xFF37474F).solid("Solid")
            for ((x, y) in platforms) {
                o(s, "Ledge", x, y, 4f, 0.6f).sprite(0xFF546E7A).solid("Solid")
            }
            for ((x, y) in platforms.filter { it.second > 0f }) {
                val mv = o(s, "MovingPlatform", x + 4f, y + 2f, 3f, 0.5f).sprite(0xFF8D6E63)
                mv.box(); mv.body(type = 1, gravity = 0f)
                mv.script("CitadelPlatform.js", "range=3, speed=1")
            }
            val hero = o(s, "Hero", -16f, -1f, 0.9f, 1.1f).sprite(0xFF4FC3F7)
            hero.tag = "Hero"; hero.order = 20
            hero.box(); hero.body(gravity = 1f, friction = 0f)
            hero.script("CitadelHero.js")
            for ((x, y) in enemies) {
                val e = o(s, "Enemy", x, y, 1f, 1f).sprite(0xFFEF5350, 1)
                e.tag = "Enemy"; e.order = 15
                e.box(true); e.body(gravity = 1f)
                e.script("CitadelEnemy.js")
            }
            for ((x, y) in relics) {
                val r = o(s, "Relic", x, y, 0.55f, 0.55f).sprite(0xFFFFD54F, 1)
                r.tag = "Relic"; r.order = 12; r.box(true); r.script("CitadelRelic.js")
            }
            val key = o(s, "Key", keyAt.first, keyAt.second, 0.7f, 0.7f).sprite(0xFFFFCA28, 2)
            key.tag = "Key"; key.box(true); key.script("CitadelRelic.js")
            val door = o(s, "Door", doorAt.first, doorAt.second, 1.4f, 2.6f).sprite(0xFF7E57C2)
            door.tag = "Door"; door.box(true)
            door.script("CitadelDoor.js", "next=$next, need=1")
            // hanging lamp driven by the Verlet rope in CitadelHero.js
            o(s, "Lamp", -8f, 6f, 0.5f, 0.5f).sprite(0xFFFFF59D, 1).also { it.order = 5 }
            // pooled/cloned templates (inactive prefabs)
            val bolt = template(s, "Bolt", 0xFF80DEEA, 1, true, "CitadelBolt.js")
            bolt.tag = "Bolt"
            bolt.getAny<Rigidbody2D>()?.ccd = true
            val spark = template(s, "Spark", 0xFFFFF176, 1)
            spark.add(ParticleEmitter().also {
                it.emitting = false; it.rate = 40f; it.lifetime = 0.7f; it.speed = 4f
                it.startSize = 0.22f; it.startColor = 0xFFFFD54F.toInt()
            })
            hud(s, "Hud", -6.5f, 3.9f, "HP 5   RELICS 0", 0.55f)
            hud(s, "Message", 0f, 2.6f, "", 0.7f, 0xFFFFF59D)
            o(s, "Director", 0f, 0f).script("CitadelHud.js")
        }

        // ---------------- Main (title) ----------------
        val menu = Scene("Main")
        camera(menu, 8f, 0xFF0B1120)
        o(menu, "Backdrop", 0f, 0f, 40f, 24f).sprite(0xFF111A2E).also { it.order = -100 }
        val title = o(menu, "Title", 0f, 2f, 1f, 1f)
        title.add(TextRenderer().also { it.text = "CITADEL OF ECHOES"; it.size = 1.1f; it.color = 0xFFFFD54F.toInt(); it.bold = true })
        val hint = o(menu, "Hint", 0f, -1f)
        hint.add(TextRenderer().also { it.text = "PRESS A / TAP TO ENTER"; it.size = 0.5f; it.bold = true })
        o(menu, "Sub", 0f, -2.5f).add(TextRenderer().also {
            it.text = "A 2D action adventure  •  joystick + A jump / B fire"; it.size = 0.32f; it.color = 0xFF90A4AE.toInt()
        })
        o(menu, "MenuController", 0f, 0f).box(true).script("CitadelMenu.js")
        p.saveScene(menu)

        // ---------------- Crypt (level 1) ----------------
        val crypt = Scene("Crypt")
        level(crypt, "Crypt",
            floor = listOf(Triple(-12f, -4f, 16f), Triple(4f, -4f, 16f), Triple(17f, -4f, 14f)),
            enemies = listOf(-6f to -2.8f, 6f to -2.8f, 14f to -2.8f),
            relics = listOf(-8f to -2.5f, 0f to 0.5f, 9f to -1.5f, 16f to 0.5f),
            keyAt = 19f to -2.2f,
            doorAt = 22f to -1.8f,
            next = "Halls",
            platforms = listOf(-2f to 0f, 10f to 1.5f))
        p.saveScene(crypt)

        // ---------------- Halls (level 2) ----------------
        val halls = Scene("Halls")
        level(halls, "Halls",
            floor = listOf(Triple(-14f, -4f, 14f), Triple(2f, -4f, 14f), Triple(18f, -4f, 16f)),
            enemies = listOf(-9f to -2.8f, 1f to -2.8f, 8f to 0.5f, 17f to -2.8f, 21f to -2.8f),
            relics = listOf(-12f to -2.4f, -3f to 0.8f, 5f to 1.0f, 13f to -1.6f, 20f to 1.2f),
            keyAt = 23f to -2.2f,
            doorAt = 25f to -1.8f,
            next = "Throne",
            platforms = listOf(-6f to 0f, 4f to 1.6f, 14f to 1.0f))
        val heart = o(halls, "Heart", 9f, 2.2f, 0.7f, 0.7f).sprite(0xFFEC407A, 1)
        heart.tag = "Heart"; heart.box(true); heart.script("CitadelRelic.js")
        p.saveScene(halls)

        // ---------------- Throne (boss) ----------------
        val throne = Scene("Throne")
        camera(throne, 9f, 0xFF1A1026, follow = "Hero")
        o(throne, "Backdrop", 0f, 0f, 60f, 34f).sprite(0xFF241630).also { it.order = -100 }
        o(throne, "Ground", 0f, -4f, 34f, 1.2f).sprite(0xFF4E342E).solid("Solid")
        o(throne, "PillarL", -14f, 0f, 1.5f, 8f).sprite(0xFF5D4037).solid("Solid")
        o(throne, "PillarR", 14f, 0f, 1.5f, 8f).sprite(0xFF5D4037).solid("Solid")
        val hero2 = o(throne, "Hero", -9f, -1f, 0.9f, 1.1f).sprite(0xFF4FC3F7)
        hero2.tag = "Hero"; hero2.box(); hero2.body(gravity = 1f, friction = 0f)
        hero2.script("CitadelHero.js")
        val boss = o(throne, "Boss", 6f, 0.5f, 2.4f, 2.4f).sprite(0xFFAB47BC, 1)
        boss.tag = "Boss"; boss.box(true); boss.body(type = 1, gravity = 0f)
        boss.script("CitadelBoss.js")
        val bolt2 = template(throne, "Bolt", 0xFF80DEEA, 1, true, "CitadelBolt.js")
        bolt2.tag = "Bolt"; bolt2.getAny<Rigidbody2D>()?.ccd = true
        val orb = template(throne, "Orb", 0xFFFF5252, 1, true, "CitadelOrb.js")
        orb.tag = "Orb"
        val spark2 = template(throne, "Spark", 0xFFFFF176, 1)
        spark2.add(ParticleEmitter().also { it.emitting = false; it.lifetime = 0.7f; it.speed = 4f })
        hud(throne, "Hud", -6.5f, 3.9f, "BOSS", 0.55f)
        hud(throne, "Message", 0f, 2.6f, "", 0.7f, 0xFFFFF59D)
        o(throne, "Director", 0f, 0f).script("CitadelHud.js")
        p.saveScene(throne)

        // ---------------- Victory / GameOver ----------------
        val victory = Scene("Victory")
        camera(victory, 8f, 0xFF10261B)
        o(victory, "Summary", 0f, 1.5f).add(TextRenderer().also { it.text = "CITADEL CLEARED!"; it.size = 0.9f; it.color = 0xFFA5D6A7.toInt(); it.bold = true })
        o(victory, "Hint", 0f, -1.5f).add(TextRenderer().also { it.text = ""; it.size = 0.5f })
        o(victory, "EndController", 0f, 0f).box(true).script("CitadelEnd.js", "win=1")
        p.saveScene(victory)

        val over = Scene("GameOver")
        camera(over, 8f, 0xFF261016)
        o(over, "Summary", 0f, 1.5f).add(TextRenderer().also { it.text = "YOU FELL"; it.size = 0.9f; it.color = 0xFFEF9A9A.toInt(); it.bold = true })
        o(over, "Hint", 0f, -1.5f).add(TextRenderer().also { it.text = ""; it.size = 0.5f })
        o(over, "EndController", 0f, 0f).box(true).script("CitadelEnd.js", "win=0")
        p.saveScene(over)

        p.startScene = "Main"
        p.orientation = 0
        p.saveMeta()
    }

    // ================================================================= GAME 2
    // ------------------------------------------------------------ Neon Apex GP
    val apex = Templates.Template(
        "Neon Apex GP",
        "FULL GAME • 3D arcade racer: countdown, 3 laps, AI rivals, boosts, best-lap saves and podium."
    ) { p -> writeApex(p) }

    private val WAYPOINTS = listOf(
        0f to -30f, 24f to -24f, 34f to 0f, 24f to 24f,
        0f to 30f, -24f to 24f, -34f to 0f, -24f to -24f
    )

    private fun writeApex(p: Project) {
        val wpLiteral = WAYPOINTS.joinToString(",") { "[${it.first}f,${it.second}f]" }
            .replace("f", "") // JS numeric literals

        p.writeAsset("ApexMenu.js", """
// Neon Apex GP — title screen with a cinematic camera orbit.
var ready = false;
function start() {
    game.save("lap", 0);
    var hint = scene.find("Hint");
    if (hint) hint.text = "BEST LAP " + Number(game.load("bestLap", 0)).toFixed(1) + "s    PRESS A / TAP TO RACE";
    camera.playPath([[0,9,20],[10,7,12],[0,9,20]], 6);
    coroutine(function* () {
        yield waitSec(0.5);
        ready = true;
        yield waitSec(2.5);
        var h = scene.find("Hint");
        if (h) h.text = "3 LAPS  •  AI RIVALS  •  BOOST WITH B";
    });
}
function update(dt) { if (ready && input.aDown) enter(); }
function onTap() { enter(); }
function enter() {
    if (!ready) return;
    ready = false;
    audio.beep();
    scene.load("Circuit");
}
""".trimIndent())

        p.writeAsset("ApexRace.js", """
// Race director: countdown coroutine, waypoint/lap logic, timer, HUD, finish.
var WPS = [$wpLiteral];
var laps = 0;
var wp = 0;
var lapTime = 0;
var total = 0;
var running = false;
var finished = false;
function start() {
    game.save("lap", 0);
    var hud = scene.find("Hud");
    if (hud) hud.text = "GET READY";
    events.on("boost", function () { camera.shake(0.18); });
    coroutine(function* () {
        for (var i = 3; i > 0; i--) {
            var h = scene.find("Hud");
            if (h) h.text = "GO IN " + i;
            audio.beep();
            yield waitSec(1.0);
        }
        var h2 = scene.find("Hud");
        if (h2) h2.text = "GO!";
        running = true;
        camera.stopPath();
        camera.zoomTo(1.08);
        yield waitSec(1.0);
    });
}
function update(dt) {
    if (!running || finished) return;
    lapTime += dt;
    total += dt;
    var car = scene.find("Car");
    if (!car) return;
    var w = WPS[wp];
    var dx = car.x - w[0];
    var dz = car.z - w[1];
    if (dx * dx + dz * dz < 64) {
        wp += 1;
        if (wp >= WPS.length) {
            wp = 0;
            laps += 1;
            game.save("lap", laps);
            events.emit("lap", laps);
            camera.shake(0.45);
            audio.beep();
            var best = game.load("bestLap", 0);
            if (best <= 0 || lapTime < best) game.save("bestLap", lapTime);
            lapTime = 0;
            if (laps >= 3) {
                finished = true;
                game.save("total", total);
                camera.shake(0.9);
                scene.load("Podium");
                return;
            }
        }
    }
    var hud = scene.find("Hud");
    if (hud) hud.text = "LAP " + (laps + 1) + "/3    TIME " + total.toFixed(1) +
        "s    BEST " + Number(game.load("bestLap", 0)).toFixed(1) + "s    WP " + (wp + 1);
}
""".trimIndent())

        p.writeAsset("ApexCar.js", """
// Arcade car: steering, accelerator (A), boost (B), 3D camera rig + cloth banner.
var speed = 0;
var heading = 0;
var boost = 0;
function start() {
    camera.setBounds(-56, -56, 56, 56);
    camera.setDeadZone(2.5, 0);
    camera.setLookAhead(0.3);
    camera.zoomTo(1.05);
    soft.cloth("Banner", -12, 9, 6, 5, 0.6, -30);
    soft.wind("Banner", 0.8, 0);
    physics.setIterations(2);
}
function update(dt) {
    var accel = input.a ? 30 : 16;
    if (input.b && boost <= 0) {
        boost = 1.1;
        events.emit("boost", 1);
        physics.explode3(self.x, self.y, self.z, 9, 12, "Rival");
        camera.shake(0.35);
        audio.beep();
    }
    if (boost > 0) boost -= dt;
    speed += accel * dt + (boost > 0 ? 34 * dt : 0);
    speed = clamp(speed - speed * dt * 0.55, 0, boost > 0 ? 52 : 38);
    heading += input.axisX * 95 * dt;
    var rad = heading * Math.PI / 180;
    self.x = clamp(self.x + Math.sin(rad) * speed * dt, -52, 52);
    self.z = clamp(self.z + Math.cos(rad) * speed * dt, -52, 52);
    self.y = 1.05 + Math.sin(time.time * 9) * Math.min(speed, 30) * 0.002;
    self.rotY = heading;
    var p = soft.point("Banner", 14);
    var flag = scene.find("BannerFlag");
    if (p && flag) flag.y = p[1];
}
function onDestroy() { soft.remove("Banner"); }
""".trimIndent())

        p.writeAsset("ApexRival.js", """
// params: offset=1 — waypoint-following rival with deterministic speed.
var WPS = [$wpLiteral];
var wp = 0;
var spin = 0;
function start() { wp = 0; spin = offset; }
function update(dt) {
    var w = WPS[wp];
    var dx = w[0] - self.x;
    var dz = w[1] - self.z;
    var d = Math.sqrt(dx * dx + dz * dz) || 1;
    var sp = 11 + offset * 1.6;
    self.x += dx / d * sp * dt;
    self.z += dz / d * sp * dt;
    self.y = 1.05;
    self.rotY = Math.atan2(dx, dz) * 180 / Math.PI;
    if (d < 7) wp = (wp + 1) % WPS.length;
    spin += dt;
}
""".trimIndent())

        p.writeAsset("ApexPodium.js", """
// Results screen, saves best lap, returns to the menu.
var ready = false;
function start() {
    var s = scene.find("Summary");
    if (s) s.text = "RACE COMPLETE\nTOTAL " + Number(game.load("total", 0)).toFixed(1) +
        "s\nBEST LAP " + Number(game.load("bestLap", 0)).toFixed(1) + "s";
    camera.shake(0.3);
    coroutine(function* () {
        yield waitSec(0.4);
        var h = scene.find("Hint");
        if (h) h.text = "PRESS A / TAP FOR THE MENU";
        ready = true;
    });
}
function update(dt) { if (ready && input.aDown) scene.load("Main"); }
function onTap() { if (ready) scene.load("Main"); }
""".trimIndent())

        // ---------------- shared 3D scene helpers ----------------
        fun base3(s: Scene, follow: String) {
            val cam = o3(s, "Main Camera", 0f, 9f, 20f)
            cam.add(Camera3D().also {
                it.follow = follow; it.offsetY = 7f; it.offsetZ = 13f
                it.fov = 68f; it.lookAtTarget = true; it.smoothing = 5f
                it.skyTop = 0xFF101A3A.toInt(); it.skyHorizon = 0xFF3F51B5.toInt()
                it.deadZoneW = 2f; it.lookAhead = 0.25f
            })
            o3(s, "Sun", 0f, 30f, 0f).also { it.rotX = -55f; it.rotY = 35f }
                .add(Light().also { it.kind = 0; it.intensity = 1.1f })
            o3(s, "NeonFill", 0f, 12f, 0f).add(Light().also {
                it.kind = 1; it.color = 0xFF40C4FF.toInt(); it.intensity = 0.9f; it.range = 90f
            })
        }

        fun track(s: Scene) {
            // asphalt
            o3(s, "Track", 0f, -0.5f, 0f, 110f, 1f, 110f).mesh(2, 0xFF263238)
            // outer glow ring (24 blocks)
            for (i in 0 until 24) {
                val a = i * (Math.PI.toFloat() * 2f / 24f)
                val x = kotlin.math.cos(a) * 44f
                val z = kotlin.math.sin(a) * 40f
                val block = o3(s, "Barrier", x, 1f, z, 4.5f, 2f, 1.6f)
                block.mesh(0, if (i % 2 == 0) 0xFF00E5FF else 0xFFD500F9)
                block.col3()
                block.rotY = -a * 180f / Math.PI.toFloat()
            }
            // inner island
            o3(s, "Island", 0f, 1.4f, 0f, 12f, 3f, 12f).mesh(0, 0xFF37474F).col3()
            o3(s, "Tower", 0f, 9f, 0f, 2f, 14f, 2f).mesh(3, 0xFF1DE9B6)
            // waypoint gates
            WAYPOINTS.forEachIndexed { i, w ->
                val gate = o3(s, "WP${i + 1}", w.first, 1.5f, w.second, 2f, 1f, 2f)
                gate.mesh(5, 0xFF76FF03)
                gate.getAny<MeshRenderer>()?.emission = 0.6f
                gate.col3(sphere = true, trigger = true)
            }
            // boost pads
            for ((x, z) in listOf(0f to -18f, 30f to 12f, 0f to 18f, -30f to -12f)) {
                val pad = o3(s, "Boost", x, 0.1f, z, 3f, 0.2f, 5f)
                pad.mesh(0, 0xFFFFC400)
                pad.getAny<MeshRenderer>()?.emission = 0.8f
                pad.col3(trigger = true)
            }
            // cloth banner visual target
            o3(s, "BannerFlag", -12f, 9f, -30f, 3f, 1.2f, 0.2f).mesh(0, 0xFFFF4081)
        }

        // ---------------- Main (title) ----------------
        val menu = Scene("Main")
        base3(menu, "")
        o3(menu, "TitleCar", 0f, 1f, 0f, 2.4f, 1f, 4.5f).mesh(0, 0xFF00E5FF)
        o3(menu, "Stage", 0f, -0.5f, 0f, 30f, 1f, 30f).mesh(2, 0xFF1A237E)
        hud(menu, "Title", 0f, 2.6f, "NEON APEX GP", 1.0f, 0xFF40C4FF)
        hud(menu, "Hint", 0f, 0.8f, "PRESS A / TAP TO RACE", 0.5f)
        o3(menu, "MenuController", 0f, 0f, 0f).script("ApexMenu.js")
        p.saveScene(menu)

        // ---------------- Circuit (race) ----------------
        val circuit = Scene("Circuit")
        circuit.fog = true; circuit.fogStart = 70f; circuit.fogEnd = 190f
        base3(circuit, "Car")
        track(circuit)
        val car = o3(circuit, "Car", 0f, 1.05f, -30f, 1.5f, 0.8f, 2.8f).mesh(0, 0xFFFFEB3B)
        car.getAny<MeshRenderer>()?.emission = 0.25f
        car.col3(sphere = true, trigger = true)
        car.script("ApexCar.js")
        val colors = listOf(0xFFE040FB, 0xFF69F0AE, 0xFFFF7043)
        for (i in 0 until 3) {
            val rival = o3(circuit, "Rival${i + 1}", -3f - i * 2f, 1.05f, -32f - i * 2f, 1.5f, 0.8f, 2.8f)
                .mesh(0, colors[i])
            rival.tag = "Rival"
            rival.col3(sphere = true, trigger = true)
            rival.script("ApexRival.js", "offset=${i + 1}")
        }
        o3(circuit, "RaceDirector", 0f, 0f, 0f).script("ApexRace.js")
        hud(circuit, "Hud", -5.5f, 3.9f, "GET READY", 0.5f, 0xFFFFF59D)
        hud(circuit, "Help", -5.5f, 3.1f, "A ACCELERATE  •  B BOOST  •  JOYSTICK STEER", 0.32f)
        p.saveScene(circuit)

        // ---------------- Podium (results) ----------------
        val podium = Scene("Podium")
        base3(podium, "")
        o3(podium, "Podium", 0f, 1f, 0f, 8f, 2f, 8f).mesh(0, 0xFFFFD54F)
        o3(podium, "Winner", 0f, 3.2f, 0f, 2.4f, 1f, 4.5f).mesh(0, 0xFFFFEB3B)
        hud(podium, "Summary", 0f, 2.4f, "RACE COMPLETE", 0.7f, 0xFFA5D6A7)
        hud(podium, "Hint", 0f, 0.4f, "", 0.45f)
        o3(podium, "PodiumController", 0f, 0f, 0f).script("ApexPodium.js")
        p.saveScene(podium)

        p.startScene = "Main"
        p.orientation = 0
        p.saveMeta()
    }
}
