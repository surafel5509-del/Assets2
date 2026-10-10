#!/usr/bin/env python3
"""Generates the four showcase games (05-08) for the S Engine.

Each game is written as plain text the engine already loads:
  games/<id>/game.json
  games/<id>/scenes/Main.scene.json
  games/<id>/scripts/*.js
  games/<id>/README.md

Run from the repository root:  python3 tools/build_games.py
The output is deterministic (fixed random seeds) so diffs stay reviewable.
"""
import json
import math
import os
import random

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "games")


# --------------------------------------------------------------------------
# Scene helpers. Property names must match the Kotlin Prop names exactly.
# --------------------------------------------------------------------------
class Scene:
    def __init__(self, name, env):
        self.name = name
        self.env = env
        self.objs = []
        self.next = 1

    def add(self, name, x=0.0, y=0.0, z=0.0, rotX=0.0, rotY=0.0, rotation=0.0,
            sx=1.0, sy=1.0, sz=1.0, tag="Untagged", active=True, order=0,
            comps=(), parent=None):
        oid = self.next
        self.next += 1
        o = {
            "id": oid, "name": name, "tag": tag, "active": active, "order": order,
            "x": float(x), "y": float(y), "z": float(z),
            "rotX": float(rotX), "rotY": float(rotY), "rotation": float(rotation),
            "scaleX": float(sx), "scaleY": float(sy), "scaleZ": float(sz),
            "components": list(comps),
        }
        if parent is not None:
            o["parent"] = parent
        self.objs.append(o)
        return oid

    def dump(self):
        out = {"name": self.name}
        out.update(self.env)
        out["nextId"] = self.next
        out["objects"] = self.objs
        return out


def mesh(kind, color, tiling=1.0, spec=0.25, shin=24.0, emis=0.0, unlit=False):
    return {"type": "MeshRenderer", "enabled": True, "Mesh": kind, "Model (.obj)": "",
            "Color": color, "Texture": "", "Tiling": tiling, "Specular": spec,
            "Shininess": shin, "Emission": emis, "Unlit": unlit, "Shader": "",
            "Shader Param": 1.0}


def sprite(color, shape="Square", screen=False):
    return {"type": "SpriteRenderer", "enabled": True, "Shape": shape, "Color": color,
            "Texture": "", "Flip X": False, "Flip Y": False, "Shader": "",
            "Shader Param": 1.0, "Screen Space UI": screen}


def text(value, size=0.5, color="#FFFFFFFF", align="Left", screen=True):
    return {"type": "TextRenderer", "enabled": True, "Text": value, "Size": size,
            "Color": color, "Align": align, "Bold": True, "Screen Space UI": screen}


def script(name):
    return {"type": "Script", "enabled": True, "Script": name, "Params": ""}


def cam3d(follow="", ox=0.0, oy=4.0, oz=8.0, fov=62.0, smooth=6.0,
          top="#FF3B7BD4", hor="#FFBFD8F0"):
    return {"type": "Camera3D", "enabled": True, "Field of View": fov, "Near": 0.1,
            "Far": 300.0, "Sky Top": top, "Sky Horizon": hor, "Follow Target": follow,
            "Offset X": ox, "Offset Y": oy, "Offset Z": oz, "Look At Target": True,
            "Follow Smoothing": smooth, "Post FX": "None", "FX Intensity": 1.0,
            "FX Shader": ""}


def cam2d(size=6.0, bg="#FF0E1116", follow="", smooth=5.0):
    return {"type": "Camera", "enabled": True, "Size": size, "Background": bg,
            "Follow Target": follow, "Follow Smoothing": smooth, "Post FX": "None",
            "FX Intensity": 1.0, "FX Shader": ""}


def sun(color="#FFFFE8C0", intensity=1.2):
    return {"type": "Light", "enabled": True, "Type": "Directional", "Color": color,
            "Intensity": intensity, "Range": 100.0}


def burst_fx(color="#FFFFC060", end="#00FF4010", size=0.22):
    return {"type": "ParticleEmitter", "enabled": True, "Emitting": False, "Rate": 0.0,
            "Lifetime": 0.7, "Speed": 4.5, "Direction": 90.0, "Spread": 180.0,
            "Start Size": size, "End Size": 0.0, "Start Color": color, "End Color": end,
            "Gravity": -6.0, "Max Particles": 80, "Additive Blend": True, "Texture": ""}


def rng_pts(seed, n, lo, hi, avoid=None, avoid_r=0.0):
    r = random.Random(seed)
    pts = []
    tries = 0
    while len(pts) < n and tries < n * 50:
        tries += 1
        x, z = r.uniform(lo, hi), r.uniform(lo, hi)
        if avoid and math.hypot(x - avoid[0], z - avoid[1]) < avoid_r:
            continue
        if any(math.hypot(x - px, z - pz) < 2.2 for px, pz in pts):
            continue
        pts.append((x, z))
    return pts


def write_game(gid, manifest, scene, scripts, readme):
    base = os.path.join(ROOT, gid)
    os.makedirs(os.path.join(base, "scenes"), exist_ok=True)
    os.makedirs(os.path.join(base, "scripts"), exist_ok=True)
    with open(os.path.join(base, "game.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    with open(os.path.join(base, "scenes", "Main.scene.json"), "w", encoding="utf-8") as f:
        json.dump(scene.dump(), f, indent=2)
        f.write("\n")
    for name, src in scripts.items():
        with open(os.path.join(base, "scripts", name), "w", encoding="utf-8") as f:
            f.write(src.strip() + "\n")
    with open(os.path.join(base, "README.md"), "w", encoding="utf-8") as f:
        f.write(readme.strip() + "\n")


def manifest(gid, title, genre, orientation, scripts, engine_notes, systems, controls):
    return {
        "id": gid, "title": title, "genre": genre, "orientation": orientation,
        "startScene": "Main",
        "scenes": ["Main"],
        "networking": False, "ai_assist": False,
        "scripts": scripts,
        "controls": controls,
        "systems": [f"{k}: {v}" for k, v in systems.items()] if isinstance(systems, dict) else systems,
        "engineNotes": {"notes": engine_notes},
        "assets": [],
    }


# ==========================================================================
# 05 - Sector Zero: low-poly 3D arena shooter
# ==========================================================================
def build_shooter():
    gid = "05_shooter_lowpoly_3d"
    s = Scene("Main", {
        "gravityX": 0.0, "gravityY": 0.0, "gravityZ": 0.0, "gravity3D": -9.8,
        "ambient": "#FF4A5262", "fog": True, "fogColor": "#FF1B2130",
        "fogStart": 34.0, "fogEnd": 95.0,
    })
    s.add("Main Camera", 0, 0, 0, rotX=-58, comps=[cam3d("Player", 0, 13, 9, fov=55, smooth=7)])
    s.add("Sun", 0, 12, 0, rotX=-55, rotY=30, comps=[sun()])
    s.add("Floor", 0, 0, 0, sx=40, sy=1, sz=40, tag="Floor",
          comps=[mesh("Plane", "#FF3A4250", tiling=10, spec=0.05)])
    # Four low-poly boundary walls.
    for name, x, z, sx, sz in [("WallN", 0, -20.5, 41, 1), ("WallS", 0, 20.5, 41, 1),
                               ("WallW", -20.5, 0, 1, 41), ("WallE", 20.5, 0, 1, 41)]:
        s.add(name, x, 1.0, z, sx=sx, sy=2.0, sz=sz, tag="Wall",
              comps=[mesh("Cube", "#FF6A7388", spec=0.1)])
    # Pillars the player and enemies path around.
    for i, (px, pz) in enumerate(rng_pts(7, 9, -15, 15, avoid=(0, 0), avoid_r=4.5)):
        s.add("Pillar%02d" % i, px, 1.5, pz, rotY=i * 17, sx=1.6, sy=3.0, sz=1.6,
              tag="Pillar", comps=[mesh("Cube", "#FF8B93A6", spec=0.15)])
    s.add("Player", 0, 0.6, 0, sx=0.8, sy=1.2, sz=0.8, tag="Player",
          comps=[mesh("Cube", "#FF4FC3F7", spec=0.6, shin=64), script("ShooterPlayer.js")])
    s.add("Director", tag="Director", comps=[script("WaveDirector.js")])
    # Templates are inactive; the scripts clone them with scene.spawn().
    s.add("Grunt", 0, 0.7, 0, sx=1.1, sy=1.4, sz=1.1, tag="Grunt", active=False,
          comps=[mesh("Cube", "#FFE5484D", spec=0.2), script("Grunt.js")])
    s.add("Bullet", 0, 0.8, 0, sx=0.25, sy=0.25, sz=0.25, tag="Bullet", active=False,
          comps=[mesh("Sphere", "#FFFFE35C", unlit=True), script("Bullet.js")])
    s.add("Burst", 0, 0.5, 0, tag="Template", active=False,
          comps=[burst_fx("#FFFFA040", "#00FF6040"), script("Fx.js")])
    s.add("HUD_Hp", -7.6, 4.1, tag="HUD", comps=[text("HP 100", 0.55, "#FFBFFFD0", "Left")])
    s.add("HUD_Wave", 0.0, 4.1, tag="HUD", comps=[text("WAVE 1", 0.55, "#FFFFFFFF", "Center")])
    s.add("HUD_Score", 7.6, 4.1, tag="HUD", comps=[text("SCORE 0", 0.55, "#FFFFE35C", "Right")])
    s.add("HUD_Hint", -7.6, -4.1, tag="HUD",
          comps=[text("Left stick move   A fire (hold)", 0.4, "#CCFFFFFF", "Left")])
    s.add("HUD_Msg", 0.0, 0.8, tag="HUD", comps=[text("", 0.75, "#FFFFD54F", "Center")])

    scripts = {
        "ShooterPlayer.js": """
// Third-person arena shooter. Left stick moves and turns the player;
// holding A fires a bullet straight ahead of the facing direction.
var speed = 6.5;
var fireCooldown = 0;
var pillarRadius = 1.6;

function update(dt) {
  var ax = input.axisX;
  var az = -input.axisY;
  var mag = Math.sqrt(ax * ax + az * az);
  if (mag > 0.08) {
    var k = Math.min(1, mag) / mag;
    self.move(ax * k * speed * dt, 0, az * k * speed * dt);
    self.rotY = Math.atan2(ax, az) * 180 / Math.PI;
  }
  // Keep the player inside the arena walls.
  var x = clamp(self.x, -18.5, 18.5);
  var z = clamp(self.z, -18.5, 18.5);
  // Push out of pillars (circle-vs-circle).
  var pillars = scene.findAll("Pillar");
  for (var i = 0; i < pillars.length; i++) {
    var p = pillars[i];
    var dx = x - p.x, dz = z - p.z;
    var d = Math.sqrt(dx * dx + dz * dz);
    var min = pillarRadius + 0.5;
    if (d < min && d > 0.0001) {
      x = p.x + dx / d * min;
      z = p.z + dz / d * min;
    }
  }
  self.setPosition(x, 0.6, z);

  fireCooldown -= dt;
  if (input.a && fireCooldown <= 0) {
    fireCooldown = 0.16;
    var r = self.rotY * Math.PI / 180;
    var b = scene.spawn("Bullet", self.x + Math.sin(r) * 0.9, 0.8, self.z + Math.cos(r) * 0.9);
    if (b) {
      b.rotY = self.rotY;
      b.active = true;
    }
    audio.beep(880, 0.04);
  }
}
""",
        "Bullet.js": """
// Fast projectile. Flies along its rotY and hits the first Grunt it touches.
var life = 1.1;
var speed = 20;

function update(dt) {
  var r = self.rotY * Math.PI / 180;
  self.move(Math.sin(r) * speed * dt, 0, Math.cos(r) * speed * dt);
  life -= dt;
  if (life <= 0) { self.destroy(); return; }
  if (Math.abs(self.x) > 20 || Math.abs(self.z) > 20) { self.destroy(); return; }
  var grunts = scene.findAll("Grunt");
  for (var i = 0; i < grunts.length; i++) {
    if (self.distanceTo3(grunts[i]) < 1.0) {
      grunts[i].send("onShot", 1);
      self.destroy();
      return;
    }
  }
}
""",
        "Grunt.js": """
// Melee enemy: walks straight at the player and hits on contact.
var hp = 3;
var speed = 2.3;
var hitCooldown = 0;
var dying = false;

function update(dt) {
  var p = scene.find("Player");
  if (!p) return;
  var dx = p.x - self.x, dz = p.z - self.z;
  var d = Math.sqrt(dx * dx + dz * dz);
  if (d > 0.9) self.move(dx / d * speed * dt, 0, dz / d * speed * dt);
  self.rotY = Math.atan2(dx, dz) * 180 / Math.PI;
  hitCooldown -= dt;
  if (d < 1.3 && hitCooldown <= 0) {
    hitCooldown = 0.8;
    scene.find("Director").send("onHurt", 8);
  }
}

function onShot(dmg) {
  hp -= dmg;
  if (hp <= 0 && !dying) {
    dying = true;
    var fx = scene.spawn("Burst", self.x, 0.8, self.z);
    if (fx) fx.burst(24);
    scene.find("Director").send("onKill", 1);
    self.destroy();
  }
}
""",
        "Fx.js": """
// One-shot effect: the emitter bursts once, then the copy removes itself.
var life = 1.4;

function update(dt) {
  life -= dt;
  if (life <= 0) self.destroy();
}
""",
        "WaveDirector.js": """
// Owns the game state: spawns waves of Grunts from the arena edge,
// tracks HP and score, and drives the HUD. Press A on game over to restart.
var wave = 1;
var toSpawn = 0;
var spawnTimer = 0;
var score = 0;
var hp = 100;
var over = false;
var toast = 0;

function start() {
  toSpawn = 4;
  refresh();
  msg("");
}

function msg(t) { scene.find("HUD_Msg").text = t; }

function refresh() {
  scene.find("HUD_Hp").text = "HP " + Math.max(0, Math.round(hp));
  scene.find("HUD_Score").text = "SCORE " + score;
  scene.find("HUD_Wave").text = "WAVE " + wave;
}

function spawnGrunt() {
  var side = Math.floor(random(0, 4));
  var t = random(-18, 18);
  var x = side === 0 ? -18 : (side === 1 ? 18 : t);
  var z = side === 2 ? -18 : (side === 3 ? 18 : t);
  var g = scene.spawn("Grunt", x, 0.7, z);
  if (g) g.active = true;
}

function update(dt) {
  if (over) {
    if (input.aDown) scene.reload();
    return;
  }
  if (toSpawn > 0) {
    spawnTimer -= dt;
    if (spawnTimer <= 0) {
      spawnTimer = Math.max(0.35, 1.4 - wave * 0.08);
      spawnGrunt();
      toSpawn--;
    }
  } else if (scene.count("Grunt") === 0) {
    wave++;
    toSpawn = 3 + wave * 2;
    toast = 2;
    msg("WAVE " + wave);
  }
  if (toast > 0) {
    toast -= dt;
    if (toast <= 0) msg("");
  }
  refresh();
}

function onKill(n) {
  score += 100 * n * wave;
  scene.shake(0.15);
}

function onHurt(dmg) {
  if (over) return;
  hp -= dmg;
  if (hp <= 0) {
    hp = 0;
    over = true;
    msg("GAME OVER  -  press A to restart");
  }
}
""",
    }
    manifest_ = manifest(
        gid, "Sector Zero", "3D low-poly arena shooter", 0,
        ["ShooterPlayer.js", "Bullet.js", "Grunt.js", "Fx.js", "WaveDirector.js"],
        ["Third-person camera follows the Player object with Look At Target.",
         "Enemies and bullets are cloned from inactive template objects via scene.spawn().",
         "Waves scale with the wave counter; game-over restarts with scene.reload()."],
        {"physics": "script-driven (circle tests)", "ui": "scene text HUD",
         "particles": "pooled burst emitters with self-destroying copies"},
        {"move": "left stick", "fire": "action button A (hold to fire)"})
    readme = """
# 05 - Sector Zero (3D low-poly shooter)

Hold **A** to fire, move with the **left stick**. Survive escalating Grunt waves
in a walled arena with pillar cover.

- Templates `Grunt`, `Bullet`, `Burst` are inactive scene objects cloned at runtime.
- `WaveDirector` owns HP, score, wave progression and the HUD text.
- Hits and kills use distance checks, so no physics engine is required.
- Generated by `tools/build_games.py`.
"""
    write_game(gid, manifest_, s, _scripts_only(scripts), readme)


def _scripts_only(d):
    return {k: v for k, v in d.items()}


# ==========================================================================
# 06 - Skyline Drift: open-world 3D driving
# ==========================================================================
def build_open_world():
    gid = "06_open_world_3d"
    s = Scene("Main", {
        "gravityX": 0.0, "gravityY": 0.0, "gravityZ": 0.0, "gravity3D": -9.8,
        "ambient": "#FF5A6470", "fog": True, "fogColor": "#FFB8CAD8",
        "fogStart": 45.0, "fogEnd": 150.0,
    })
    s.add("Main Camera", 0, 0, 0, comps=[cam3d("Car", 0, 4.2, -9.5, fov=60, smooth=5,
                                              top="#FF4D8FD9", hor="#FFD7E8F2")])
    s.add("Sun", 0, 20, 0, rotX=-50, rotY=-35, comps=[sun("#FFFFF0D0", 1.35)])
    s.add("Ground", 0, 0, 0, sx=200, sy=1, sz=200, tag="Ground",
          comps=[mesh("Plane", "#FF4C7A3A", tiling=24, spec=0.02)])
    # A simple road running the length of the map.
    s.add("Road", 0, 0.02, 0, sx=8, sy=1, sz=200, tag="Road",
          comps=[mesh("Plane", "#FF30343A", tiling=4, spec=0.1)])
    # Trees: each culled by World when far from the car.
    for i, (x, z) in enumerate(rng_pts(11, 170, -96, 96, avoid=(0, 0), avoid_r=8)):
        r = random.Random(100 + i)
        sc = r.uniform(1.7, 3.1)
        s.add("Tree%03d" % i, x, sc * 0.9, z, rotY=r.uniform(0, 360), sx=sc, sy=sc * 1.6, sz=sc,
              tag="Tree", comps=[mesh("Cone", r.choice(["#FF2F7A3A", "#FF3D8C45", "#FF24643A"]),
                                     spec=0.05)])
    for i, (x, z) in enumerate(rng_pts(23, 70, -96, 96, avoid=(0, 0), avoid_r=8)):
        r = random.Random(500 + i)
        sc = r.uniform(0.6, 1.9)
        s.add("Rock%03d" % i, x, sc * 0.4, z, sx=sc, sy=sc * 0.8, sz=sc * 1.1, tag="Rock",
              comps=[mesh("Sphere", "#FF7D7F86", spec=0.08)])
    # Collectible coins scattered along the map.
    for i, (x, z) in enumerate(rng_pts(31, 45, -90, 90, avoid=(0, 0), avoid_r=12)):
        s.add("Coin%02d" % i, x, 1.1, z, sx=0.9, sy=0.9, sz=0.9, tag="Coin",
              comps=[mesh("Sphere", "#FFFFC93C", emis=0.6, spec=0.9, shin=80)])
    s.add("Car", 0, 0.5, 0, sx=1.6, sy=0.8, sz=2.6, tag="Car",
          comps=[mesh("Cube", "#FF2E6BE0", spec=0.8, shin=72), script("CarDrive.js")])
    s.add("World", tag="Manager", comps=[script("World.js")])
    s.add("Burst", 0, 0.5, 0, tag="Template", active=False,
          comps=[burst_fx("#FFFFD040", "#00FFE080"), script("Fx.js")])
    s.add("HUD_Speed", -7.6, -3.9, tag="HUD", comps=[text("0 km/h", 0.6, "#FFFFFFFF", "Left")])
    s.add("HUD_Coins", 7.6, 4.1, tag="HUD", comps=[text("COINS 0/45", 0.55, "#FFFFD54F", "Right")])
    s.add("HUD_Culled", -7.6, 4.1, tag="HUD", comps=[text("", 0.4, "#AAFFFFFF", "Left")])
    s.add("HUD_Msg", 0, 2.6, tag="HUD", comps=[text("", 0.65, "#FFFFFFFF", "Center")])

    scripts = {
        "CarDrive.js": """
// Arcade car: A accelerates, B brakes/reverses, left stick steers.
// Steering strength scales with speed so the car cannot turn on the spot.
var speed = 0;
var maxSpeed = 24;
var collected = 0;
var total = 45;

function update(dt) {
  var thr = 0;
  if (input.a) thr = 1;
  if (input.b) thr = -0.6;
  speed += thr * 15 * dt;
  speed -= speed * 0.35 * dt;                    // rolling drag
  if (thr === 0 && Math.abs(speed) < 0.2) speed = 0;
  speed = clamp(speed, -9, maxSpeed);

  var dir = speed >= 0 ? 1 : -1;
  var steer = Math.min(1, Math.abs(speed) / 6);
  self.rotY += input.axisX * 75 * steer * dir * dt;

  var r = self.rotY * Math.PI / 180;
  self.move(Math.sin(r) * speed * dt, 0, Math.cos(r) * speed * dt);
  self.setPosition(clamp(self.x, -95, 95), 0.5, clamp(self.z, -95, 95));

  var coins = scene.findAll("Coin");
  for (var i = 0; i < coins.length; i++) {
    var c = coins[i];
    if (self.distanceTo3(c) < 2.2) {
      c.destroy();
      collected++;
      var fx = scene.spawn("Burst", c.x, 1.0, c.z);
      if (fx) fx.burst(12);
      audio.beep(1320, 0.05);
    }
  }
  scene.find("HUD_Speed").text = Math.round(Math.abs(speed) * 3.6) + " km/h";
  scene.find("HUD_Coins").text = "COINS " + collected + "/" + total;
  if (collected === total) scene.find("HUD_Msg").text = "ALL COINS COLLECTED!";
}
""",
        "World.js": """
// Level manager: culls trees and rocks beyond draw distance (cheap LOD) and
// spins coins. Culling runs on a timer, not every frame.
var trees = [];
var rocks = [];
var cullRadius = 75;
var cullTimer = 0;

function start() {
  trees = scene.findAll("Tree");
  rocks = scene.findAll("Rock");
}

function update(dt) {
  var coins = scene.findAll("Coin");
  for (var i = 0; i < coins.length; i++) coins[i].rotY += 150 * dt;

  cullTimer -= dt;
  if (cullTimer > 0) return;
  cullTimer = 0.25;
  var car = scene.find("Car");
  if (!car) return;
  var shown = 0;
  for (var t = 0; t < trees.length; t++) {
    var near = car.distanceTo3(trees[t]) < cullRadius;
    trees[t].active = near;
    if (near) shown++;
  }
  for (var k = 0; k < rocks.length; k++) {
    var nearRock = car.distanceTo3(rocks[k]) < cullRadius;
    rocks[k].active = nearRock;
    if (nearRock) shown++;
  }
  scene.find("HUD_Culled").text = "Props drawn: " + shown;
}
""",
        "Fx.js": """
// One-shot effect: the emitter bursts once, then the copy removes itself.
var life = 1.4;

function update(dt) {
  life -= dt;
  if (life <= 0) self.destroy();
}
""",
    }
    m = manifest(
        gid, "Skyline Drift", "3D open-world driving", 0,
        ["CarDrive.js", "World.js", "Fx.js"],
        ["Open map of 200 x 200 units with 170 trees and 70 rocks.",
         "World.js culls far props on a 0.25 s timer to keep draw calls bounded.",
         "Coins spin in a single manager update instead of one script per coin."],
        {"physics": "arcade (speed-based steering)", "ui": "scene text HUD",
         "performance": "distance culling, timer-based LOD, pooled burst FX"},
        {"drive": "left stick steers", "accelerate": "action button A", "brake/reverse": "action button B"})
    readme = """
# 06 - Skyline Drift (3D open world)

Drive the blue car across a 200 x 200 unit open map. **A** accelerates,
**B** brakes or reverses, the **left stick** steers. Collect all 45 coins.

- `World.js` culls trees and rocks outside 75 units from the car.
- Coins are picked up by distance; each pickup spawns a cloned burst effect.
- Generated by `tools/build_games.py`.
"""
    write_game(gid, m, s, scripts, readme)


# ==========================================================================
# 07 - Dirt Rush: 2D motorbike
# ==========================================================================
def ground_y(x):
    return -2.0 + 0.6 * math.sin(x * 0.18) + 0.35 * math.sin(x * 0.47 + 1.3)


def build_motorbike():
    gid = "07_motorbike_2d"
    track_end = 260
    s = Scene("Main", {
        "gravityX": 0.0, "gravityY": 0.0, "gravityZ": 0.0, "gravity3D": 0.0,
        "ambient": "#FFFFFFFF", "fog": False, "fogColor": "#FF000000",
        "fogStart": 0.0, "fogEnd": 1.0,
    })
    s.add("Main Camera", 0, 0, 0, comps=[cam2d(7.0, "#FF9CC9F0", "Bike", 6.0)])
    # Terrain: thick ground blocks with a grass top, rotated to match the slope.
    x = -4
    while x < track_end:
        xc = x + 1.0
        slope = (ground_y(xc + 0.05) - ground_y(xc - 0.05)) / 0.1
        rot = math.degrees(math.atan(slope))
        yc = ground_y(xc)
        s.add("Dirt%03d" % x, xc, yc - 3.0, 0, rotation=rot, sx=2.1, sy=6.0, tag="Ground",
              comps=[sprite("#FF7A5230")])
        s.add("Grass%03d" % x, xc, yc + 0.12, 0, rotation=rot, sx=2.1, sy=0.3, tag="Ground",
              comps=[sprite("#FF4E9A3A")])
        x += 2
    # Coins every 9 units along the track.
    cx = 12
    i = 0
    while cx < track_end - 10:
        s.add("Coin%02d" % i, cx, ground_y(cx) + 1.8, 0, sx=0.5, sy=0.5, tag="Coin",
              comps=[sprite("#FFFFC93C", "Circle")])
        cx += 9
        i += 1
    s.add("FinishLine", track_end, ground_y(track_end) + 2.5, 0, sx=0.4, sy=6.0, tag="Finish",
          comps=[sprite("#FFFFD54F")])
    # The bike is a body sprite with two wheel children.
    bike = s.add("Bike", 0, ground_y(0) + 0.55, 0, sx=1.8, sy=0.55, tag="Bike",
                 comps=[sprite("#FFE5484D"), script("BikeRide.js")])
    s.add("WheelBack", -0.7, -0.4, 0, sx=0.7, sy=0.7, parent=bike, order=1,
          comps=[sprite("#FF222222", "Circle")])
    s.add("WheelFront", 0.7, -0.4, 0, sx=0.7, sy=0.7, parent=bike, order=1,
          comps=[sprite("#FF222222", "Circle")])
    s.add("Rider", 0.1, 0.55, 0, sx=0.6, sy=0.6, parent=bike, order=2,
          comps=[sprite("#FF3F6AE0")])
    s.add("Burst", 0, 0, 0, tag="Template", active=False,
          comps=[burst_fx("#FFD9B07A", "#00D9B07A", size=0.3), script("Fx.js")])
    s.add("HUD_Dist", -7.6, 4.1, tag="HUD", comps=[text("0 m", 0.55, "#FFFFFFFF", "Left")])
    s.add("HUD_Coins", 7.6, 4.1, tag="HUD", comps=[text("COINS 0", 0.55, "#FFFFD54F", "Right")])
    s.add("FuelFrame", -4.0, -3.7, tag="HUD", sx=3.2, sy=0.3,
          comps=[sprite("#FF30343A", screen=True)])
    s.add("FuelBar", -5.6, -3.7, tag="HUD", sx=3.0, sy=0.22,
          comps=[sprite("#FF7CFF6B", screen=True)])
    s.add("HUD_Msg", 0, 2.4, tag="HUD", comps=[text("", 0.7, "#FFFFFFFF", "Center")])

    scripts = {
        "BikeRide.js": """
// Side-scrolling motorbike with simple suspension-free physics.
// The bike follows the terrain height function; A or axisY-up is gas,
// B is brake, B-press in the air is not used (jump is B's rising edge).
var speed = 0;
var vy = 0;
var grounded = true;
var fuel = 100;
var coins = 0;
var state = "ride";           // ride | crash | finish
var gravity = -22;
var finishX = 260;

function groundY(x) { return -2 + 0.6 * Math.sin(x * 0.18) + 0.35 * Math.sin(x * 0.47 + 1.3); }
function slope(x) { return (groundY(x + 0.05) - groundY(x - 0.05)) / 0.1; }

function msg(t) { scene.find("HUD_Msg").text = t; }

function update(dt) {
  if (state !== "ride") {
    if (input.aDown) scene.reload();
    return;
  }
  var gas = (input.a || input.axisY > 0.3) ? 1 : 0;
  var brake = input.axisY < -0.3 ? 1 : 0;
  if (fuel > 0 && gas) fuel = Math.max(0, fuel - 3 * dt);

  // Engine push, brake, drag, and gravity along the slope.
  speed += (gas * 10 * (fuel > 0 ? 1 : 0) - brake * 12 - speed * 0.25) * dt;
  speed -= slope(self.x) * 7 * dt;
  speed = clamp(speed, -4, 24);
  self.x += speed * dt;

  var targetY = groundY(self.x) + 0.55;
  vy += gravity * dt;
  var y = self.y + vy * dt;
  if (y <= targetY) {
    if (!grounded && Math.abs(self.rotation) > 100) {
      state = "crash";
      speed = 0;
      msg("CRASH  -  press A to retry");
    }
    if (!grounded && vy < -6) {
      var dust = scene.spawn("Burst", self.x, targetY - 0.5, 0);
      if (dust) dust.burst(14);
    }
    y = targetY;
    vy = 0;
    grounded = true;
  } else {
    grounded = false;
  }
  if (input.bDown && grounded) {
    vy = 9.5;
    grounded = false;
  }
  self.setPosition(self.x, y);

  if (grounded) {
    self.rotation = Math.atan(slope(self.x)) * 180 / Math.PI;
  } else {
    self.rotation += -input.axisX * 130 * dt;   // lean/wheelie in the air
  }

  var wheelSpin = speed * 60 * dt;
  scene.find("WheelBack").rotation -= wheelSpin;
  scene.find("WheelFront").rotation -= wheelSpin;

  var coinObjs = scene.findAll("Coin");
  for (var i = 0; i < coinObjs.length; i++) {
    var c = coinObjs[i];
    if (self.distanceTo(c) < 1.1) {
      c.destroy();
      coins++;
      fuel = Math.min(100, fuel + 6);
      audio.beep(1200, 0.04);
    }
  }

  scene.find("HUD_Dist").text = Math.floor(self.x) + " m";
  scene.find("HUD_Coins").text = "COINS " + coins;
  scene.find("FuelBar").scaleX = Math.max(0.01, fuel / 100 * 3.0);
  // The bar is anchored at its left edge (-5.6) and shrinks toward it.
  scene.find("FuelBar").x = -5.6 + (fuel / 100 * 3.0) / 2;

  if (self.x >= finishX && state === "ride") {
    state = "finish";
    msg("FINISH!  Coins " + coins + "  -  press A to ride again");
  }
  if (fuel <= 0 && speed < 0.5 && state === "ride" && grounded) {
    state = "crash";
    msg("OUT OF FUEL  -  press A to retry");
  }
}
""",
        "Fx.js": """
// One-shot effect: the emitter bursts once, then the copy removes itself.
var life = 1.0;

function update(dt) {
  life -= dt;
  if (life <= 0) self.destroy();
}
""",
    }
    m = manifest(
        gid, "Dirt Rush", "2D side-scrolling motorbike", 0,
        ["BikeRide.js", "Fx.js"],
        ["Terrain is a sum-of-sines height function; the scene holds matching ground blocks.",
         "Bike physics: gravity, landing, slope-following rotation, and crash on flipped landing.",
         "Wheels are child objects that spin with speed."],
        {"physics": "custom 2D (gravity, slope-follow)", "ui": "scene text + fuel bar",
         "camera": "2D orthographic follow"},
        {"steer/lean": "left stick (X in air, Y to gas/brake)", "gas": "action button A or stick up", "jump": "action button B"})
    readme = """
# 07 - Dirt Rush (2D motorbike)

Gas with **A** or the stick up, brake with stick down, **B** to jump, and
lean in the air with the left stick. Collect coins (each one refills fuel) and
reach the finish line before the fuel runs out. Flipping the bike on landing
crashes it.

- The terrain is generated by `tools/build_games.py` from the same height
  function the bike script uses.
- Generated by `tools/build_games.py`.
"""
    write_game(gid, m, s, scripts, readme)


# ==========================================================================
# 08 - Dead Blocks: top-down zombie survival
# ==========================================================================
def build_zombies():
    gid = "08_zombie_topdown"
    s = Scene("Main", {
        "gravityX": 0.0, "gravityY": 0.0, "gravityZ": 0.0, "gravity3D": 0.0,
        "ambient": "#FFFFFFFF", "fog": False, "fogColor": "#FF000000",
        "fogStart": 0.0, "fogEnd": 1.0,
    })
    s.add("Main Camera", 0, 0, 0, comps=[cam2d(9.0, "#FF1A2A1F", "Player", 6.0)])
    s.add("Floor", 0, 0, 0, sx=70, sy=70, tag="Floor",
          comps=[sprite("#FF2B3A2E")])
    # Lighter patches so movement reads well.
    for i, (x, y) in enumerate(rng_pts(41, 26, -28, 28, avoid=(0, 0), avoid_r=3)):
        r = random.Random(900 + i)
        s.add("Patch%02d" % i, x, y, 0, sx=r.uniform(2, 4), sy=r.uniform(2, 4), tag="Floor",
              comps=[sprite(r.choice(["#FF334738", "#FF24322A", "#FF3A4B3B"]))])
    for i, (x, y) in enumerate(rng_pts(43, 34, -26, 26, avoid=(0, 0), avoid_r=5)):
        r = random.Random(1200 + i)
        s.add("Crate%02d" % i, x, y, 0, rotation=r.uniform(0, 90), sx=1.6, sy=1.6,
              tag="Wall", comps=[sprite("#FF7A5A38")])
    s.add("Player", 0, 0, 0, sx=0.8, sy=0.8, tag="Player",
          comps=[sprite("#FF4FC3F7", "Circle"), script("TopDownPlayer.js")])
    s.add("Director", tag="Director", comps=[script("ZombieDirector.js")])
    s.add("Zombie", 0, 0, 0, sx=0.9, sy=0.9, tag="Zombie", active=False,
          comps=[sprite("#FF6DBE45", "Circle"), script("Zombie.js")])
    s.add("Bullet", 0, 0, 0, sx=0.22, sy=0.22, tag="Bullet", active=False,
          comps=[sprite("#FFFFE35C", "Circle"), script("Bullet.js")])
    s.add("Gore", 0, 0, 0, tag="Template", active=False,
          comps=[burst_fx("#FFB0202A", "#00B0202A", size=0.25), script("Fx.js")])
    s.add("HealthFrame", -6.2, 3.8, tag="HUD", sx=4.2, sy=0.42,
          comps=[sprite("#FF1B1B1B", screen=True)])
    s.add("HealthBar", -8.1, 3.8, tag="HUD", sx=4.0, sy=0.3,
          comps=[sprite("#FFE5484D", screen=True)])
    s.add("HUD_Wave", 7.8, 4.1, tag="HUD", comps=[text("WAVE 1", 0.5, "#FFFFFFFF", "Right")])
    s.add("HUD_Score", 7.8, 3.5, tag="HUD", comps=[text("SCORE 0", 0.45, "#FFFFE35C", "Right")])
    s.add("HUD_Msg", 0, 1.8, tag="HUD", comps=[text("", 0.7, "#FFFFFFFF", "Center")])
    s.add("HUD_Hint", -7.8, -4.0, tag="HUD",
          comps=[text("Move: left stick   Hold A: auto-aim fire", 0.38, "#BBFFFFFF", "Left")])

    scripts = {
        "TopDownPlayer.js": """
// Top-down survivor. Left stick moves and is blocked by crates. Holding A
// fires at the nearest zombie (auto-aim) on a short cooldown.
var speed = 4.6;
var radius = 0.4;
var cooldown = 0;

function update(dt) {
  var mx = input.axisX;
  var my = input.axisY;
  var mag = Math.sqrt(mx * mx + my * my);
  var x = self.x, y = self.y;
  if (mag > 0.08) {
    var k = Math.min(1, mag) / mag;
    x += mx * k * speed * dt;
    y += my * k * speed * dt;
  }
  // Push out of crates (circle-vs-circle, crates are 0.8 radius).
  var crates = scene.findAll("Wall");
  for (var i = 0; i < crates.length; i++) {
    var dx = x - crates[i].x, dy = y - crates[i].y;
    var d = Math.sqrt(dx * dx + dy * dy);
    var min = radius + 0.8;
    if (d < min && d > 0.0001) {
      x = crates[i].x + dx / d * min;
      y = crates[i].y + dy / d * min;
    }
  }
  self.setPosition(clamp(x, -33, 33), clamp(y, -33, 33));

  cooldown -= dt;
  if (input.a && cooldown <= 0) {
    var target = nearestZombie();
    if (target) {
      cooldown = 0.2;
      var ang = Math.atan2(target.y - self.y, target.x - self.x) * 180 / Math.PI;
      var r = ang * Math.PI / 180;
      var b = scene.spawn("Bullet", self.x + Math.cos(r) * 0.6, self.y + Math.sin(r) * 0.6, 0);
      if (b) {
        b.rotation = ang;
        b.active = true;
      }
      audio.beep(700, 0.03);
    }
  }
}

function nearestZombie() {
  var zs = scene.findAll("Zombie");
  var best = null;
  var bestD = 1e9;
  for (var i = 0; i < zs.length; i++) {
    var d = self.distanceTo(zs[i]);
    if (d < bestD) { bestD = d; best = zs[i]; }
  }
  return best;
}
""",
        "Bullet.js": """
// Short-lived projectile that flies along its rotation and hits the first zombie.
var life = 0.9;
var speed = 16;

function update(dt) {
  var r = self.rotation * Math.PI / 180;
  self.move(Math.cos(r) * speed * dt, Math.sin(r) * speed * dt);
  life -= dt;
  if (life <= 0) { self.destroy(); return; }
  var zs = scene.findAll("Zombie");
  for (var i = 0; i < zs.length; i++) {
    if (self.distanceTo(zs[i]) < 0.6) {
      zs[i].send("onShot", 1);
      self.destroy();
      return;
    }
  }
}
""",
        "Zombie.js": """
// Chases the player and bites on contact. Speed rises with the wave.
var hp = 2;
var speed = 1.5;
var biteCooldown = 0;
var dying = false;

function update(dt) {
  var p = scene.find("Player");
  if (!p) return;
  var dx = p.x - self.x, dy = p.y - self.y;
  var d = Math.sqrt(dx * dx + dy * dy);
  if (d > 0.5) {
    self.move(dx / d * speed * dt, dy / d * speed * dt);
  }
  self.rotation = Math.atan2(dy, dx) * 180 / Math.PI;
  biteCooldown -= dt;
  if (d < 0.85 && biteCooldown <= 0) {
    biteCooldown = 0.9;
    scene.find("Director").send("onHurt", 7);
  }
}

function onShot(dmg) {
  hp -= dmg;
  if (hp <= 0 && !dying) {
    dying = true;
    var fx = scene.spawn("Gore", self.x, self.y, 0);
    if (fx) fx.burst(20);
    scene.find("Director").send("onKill", 1);
    self.destroy();
  }
}
""",
        "ZombieDirector.js": """
// Owns game state: waves of zombies spawn on a ring just outside the view,
// health and score are tracked, and A restarts after game over.
var wave = 1;
var toSpawn = 0;
var spawnTimer = 0;
var score = 0;
var hp = 100;
var over = false;
var toast = 0;

function start() {
  toSpawn = 5;
}

function spawnZombie() {
  var p = scene.find("Player");
  if (!p) return;
  var a = random(0, 6.2832);
  var z = scene.spawn("Zombie", p.x + Math.cos(a) * 13, p.y + Math.sin(a) * 13, 0);
  if (z) z.active = true;
}

function update(dt) {
  if (over) {
    if (input.aDown) scene.reload();
    return;
  }
  if (toSpawn > 0) {
    spawnTimer -= dt;
    if (spawnTimer <= 0) {
      spawnTimer = Math.max(0.3, 1.2 - wave * 0.07);
      spawnZombie();
      toSpawn--;
    }
  } else if (scene.count("Zombie") === 0) {
    wave++;
    toSpawn = 4 + wave * 2;
    toast = 2;
    scene.find("HUD_Msg").text = "WAVE " + wave;
  }
  if (toast > 0) {
    toast -= dt;
    if (toast <= 0) scene.find("HUD_Msg").text = "";
  }
  scene.find("HUD_Wave").text = "WAVE " + wave;
  scene.find("HUD_Score").text = "SCORE " + score;
  scene.find("HealthBar").scaleX = Math.max(0.01, hp / 100 * 4.0);
}

function onKill(n) {
  score += 10 * n * wave;
}

function onHurt(dmg) {
  if (over) return;
  hp -= dmg;
  if (hp <= 0) {
    hp = 0;
    over = true;
    scene.find("HUD_Msg").text = "YOU DIED  -  press A to restart";
  }
}
""",
        "Fx.js": """
// One-shot effect: the emitter bursts once, then the copy removes itself.
var life = 1.0;

function update(dt) {
  life -= dt;
  if (life <= 0) self.destroy();
}
""",
    }
    m = manifest(
        gid, "Dead Blocks", "Top-down zombie survival", 0,
        ["TopDownPlayer.js", "Bullet.js", "Zombie.js", "ZombieDirector.js", "Fx.js"],
        ["Top-down camera follows the Player; crates block movement via circle tests.",
         "Auto-aim picks the nearest zombie each time the fire cooldown ends.",
         "Zombies spawn on a ring just outside the view and chase the player."],
        {"physics": "script-driven (circle tests)", "ui": "scene text + health bar",
         "particles": "pooled gore bursts with self-destroying copies"},
        {"move": "left stick", "fire": "action button A (hold, auto-aims)", "restart": "action button A on game over"})
    readme = """
# 08 - Dead Blocks (top-down zombie survival)

Move with the **left stick**. Hold **A** to fire at the nearest zombie. Zombies
pour in from outside the view in growing waves. Crates block movement. Press **A**
after the game ends to restart.

- `ZombieDirector` owns waves, score and HP.
- Zombies and bullets are cloned from inactive templates.
- Generated by `tools/build_games.py`.
"""
    write_game(gid, m, s, scripts, readme)


if __name__ == "__main__":
    build_shooter()
    build_open_world()
    build_motorbike()
    build_zombies()
    # Same rule as tools/build_asset_store.py: the index is every directory
    # that has a game.json, in sorted order.
    ids = [d for d in sorted(os.listdir(ROOT)) if os.path.isfile(os.path.join(ROOT, d, "game.json"))]
    with open(os.path.join(ROOT, "index.json"), "w", encoding="utf-8") as f:
        json.dump(ids, f, indent=2)
        f.write("\n")
    print("generated games 05-08 under", os.path.normpath(ROOT))
