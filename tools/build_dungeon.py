#!/usr/bin/env python3
"""
build_dungeon.py -- "Crypt of the Ember Sigil": the showcase 2D game.

A top-down action-RPG built out of the store's Penusbmic dungeon art plus the
Legacy Fantasy forest pack.  The level is not hand-placed in the scene: it is
*generated* at runtime by DeepDelve.js from a room/corridor definition, the same
way a shipped roguelike works, and this builder only supplies the room grammar.

Why a generated level rather than a baked scene:

  * a 72x56 tile map is 4032 tiles.  As one object per tile that is 4032 draw
    calls and 4032 objects in the editor; the generator emits the same map as
    63 dominant-tile chunks plus a handful of overlays, which is what makes it
    openable on a phone at all.
  * the walls have to be *colliders*, and colliders need merging.  Baking them
    into the scene JSON would mean thousands of hand-written collider entries
    that nothing can check; the generator merges wall runs and the validator can
    then reason about the result.

The tile atlas is Penusbmic's `Dungeon_Tileset.png` (160x160, 10x10 cells of
16px).  The cells used are named in the TILES table below with the pixel
evidence for each -- see tools/probe_png.py, which is what was used to read the
atlas: the pack ships no metadata, the store refuses to guess a grid for opaque
art, and picking a cell by eye is exactly the kind of choice that silently
samples the wrong art.

Run:  python3 tools/build_dungeon.py
"""

from __future__ import annotations

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "biggames"))
from scene_kit import (Scene, animator, asset, cam2d, clip, collider2d, collider3d,  # noqa: E402
                       light, manifest, mesh, particles, rigid2d, rigid3d, script,
                       sprite, text, write_game)

# --------------------------------------------------------------------------
# The level: rooms in cell coordinates on a 72x56 grid, tile = 1 world unit.
#
# Coordinates are (col, row) with row 0 at the top.  Rooms are listed in the
# order the player meets them; corridors connect consecutive rooms, and the two
# extra links listed in LOOPS give the map cycles so it is not a tree (a tree
# reads as a long corridor with branches, a cycle reads as a place).
# --------------------------------------------------------------------------

MAP_W, MAP_H = 72, 56
CHUNK = 8

ROOMS = [
    # name,        col, row,  w,  h
    ("Antechamber", 32, 46, 8, 7),      # start
    ("West Hall", 12, 44, 12, 6),
    ("Ossuary", 8, 30, 10, 9),
    ("Font of Ash", 24, 33, 9, 8),
    ("Long Gallery", 40, 32, 16, 6),
    ("Ember Shrine", 60, 26, 9, 8),     # the objective sits here
    ("Kitehen", 44, 16, 8, 6),
    ("Barrow", 20, 14, 12, 9),
    ("Vault Gate", 4, 6, 10, 7),
    ("Deep Vault", 20, 2, 14, 8),       # the objective
    ("Watchpost", 52, 44, 8, 6),
]

LOOPS = [("Font of Ash", "Ossuary"), ("Kitehen", "Long Gallery"), ("Watchpost", "East Hall")]

# tile id -> (atlas cell, solid, note).  Cells verified against the chart
# rendered by `probe_png.py --chart`: 1/2/3 are the wall-top bands, 33 is the
# arched doorway, 80 the crate, 86 the coin, 90 the lit wall torch.
TILES = {
    "f": (6, False),     # flat crypt floor    (cell 6,  #3C243A, variance 12)
    "F": (22, False),    # floor variant       (cell 22, #3C243A, variance 12)
    "c": (9, False),     # carpet / inlay strip(cell 9,  #3C243A)
    "w": (1, True),      # wall face           (cell 1,  #4C313C)
    "W": (3, True),      # wall face variant   (cell 3,  #4B303B)
    "b": (54, True),     # barred gate         (cells 54/55, dark opening)
    "d": (33, False),    # arch / doorway      (cell 33)
    "C": (80, True),     # wooden crate        (cell 80)
    "T": (90, False),    # lit wall torch      (cell 90)
    "P": (74, False),    # pot                 (cell 74)
    "S": (95, False),    # skeleton bones      (cell 95)
}

# Which of the above the *floor* pass paints.  Everything else is a prop that
# the generator places explicitly, so a crate is never smeared across a room.
FLOOR_TILES = ("f", "F", "c")
WALL_TILES = ("w", "W", "b", "C")
PROP_TILES = ("T", "P", "S", "d")


def carve_level():
    """Room/corridor grid as rows of tile ids.

    Deterministic.  Rooms are punched out first, then connected in order with
    L-shaped corridors that avoid cutting through the middle of a room's north
    wall (so doorways stay readable), then the wall shell is grown around
    everything open.
    """
    grid = [["." for _ in range(MAP_W)] for _ in range(MAP_H)]

    def carve(x0, y0, x1, y1):
        for y in range(max(0, y0), min(MAP_H, y1)):
            for x in range(max(0, x0), min(MAP_W, x1)):
                grid[y][x] = "f"

    room_by_name = {}
    for name, cx, cy, w, h in ROOMS:
        room_by_name[name] = (cx, cy, w, h)
        carve(cx, cy, cx + w, cy + h)

    def corridor(a, b):
        ax, ay, aw, ah = room_by_name[a]
        bx, by, bw, bh = room_by_name[b]
        acx, acy = ax + aw // 2, ay + ah // 2
        bcx, bcy = bx + bw // 2, by + bh // 2
        # Horizontal leg first when the rooms are far apart in x, vertical when
        # they are stacked -- the corridor then leaves through a side wall
        # instead of punching a hole in the face of a room.
        if abs(bcx - acx) >= abs(bcy - acy):
            carve(min(acx, bcx), acy - 1, max(acx, bcx) + 1, acy + 2)
            carve(bcx - 1, min(acy, bcy), bcx + 2, max(acy, bcy) + 1)
        else:
            carve(acx - 1, min(acy, bcy), acx + 2, max(acy, bcy) + 1)
            carve(min(acx, bcx), bcy - 1, max(acx, bcx) + 1, bcy + 2)

    order = [r[0] for r in ROOMS]
    for i in range(len(order) - 1):
        corridor(order[i], order[i + 1])
    for a, b in LOOPS:
        if a in room_by_name and b in room_by_name:
            corridor(a, b)

    # Non-square masonry: floor variants are seeded off a fixed pattern so the
    # same cell always gets the same tile (a diff then shows real changes only).
    for y in range(MAP_H):
        for x in range(MAP_W):
            if grid[y][x] == "f" and (x * 7 + y * 13) % 11 == 0:
                grid[y][x] = "F"
    for name, cx, cy, w, h in ROOMS:
        # A carpet strip down the long axis, two tiles in from the wall.
        if w >= h:
            for x in range(cx + 2, cx + w - 2):
                for y in (cy + h // 2,):
                    grid[y][x] = "c"
        else:
            for y in range(cy + 2, cy + h - 2):
                for x in (cx + w // 2,):
                    grid[y][x] = "c"

    # Wall shell: any empty cell touching open space becomes masonry.
    solid = []
    for y in range(MAP_H):
        for x in range(MAP_W):
            if grid[y][x] != ".":
                continue
            near = any(
                0 <= x + dx < MAP_W and 0 <= y + dy < MAP_H and grid[y + dy][x + dx] in FLOOR_TILES
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
            )
            if near:
                grid[y][x] = "W" if (x * 5 + y * 3) % 7 == 0 else "w"
                solid.append((x, y))
    return grid, room_by_name, solid


def wall_runs(solid):
    """Merge wall cells into horizontal runs, one collider per run.

    Per-cell colliders would be ~1400 objects for this map and every one of them
    is tested against every other body each physics step.  Merging turns them
    into ~200, and the result is visually identical because the walls are
    axis-aligned rectangles either way.
    """
    by_row = {}
    for (x, y) in solid:
        by_row.setdefault(y, []).append(x)
    runs = []
    for y in sorted(by_row):
        xs = sorted(by_row[y])
        start = prev = xs[0]
        for x in xs[1:]:
            if x == prev + 1:
                prev = x
                continue
            runs.append((start, y, prev - start + 1))
            start = prev = x
        runs.append((start, y, prev - start + 1))
    return runs


def cell_to_world(cx, cy):
    return cx - MAP_W / 2 + 0.5, -(cy - MAP_H / 2 + 0.5)


# --------------------------------------------------------------------------
# Scripts
# --------------------------------------------------------------------------

WARDEN_JS = r"""
// Crypt of the Ember Sigil -- Warden.js
// The player: 8-direction movement, a sword arc, a carried torch and a heal
// charge on a cooldown.
//
// Params: speed=4.6, hp=100, damage=12, swingRange=1.25, swingArc=0.42,
//         swingCooldown=0.36, iframes=0.7, regenDelay=8, regenRate=2.5

var facing = "down";
var swing = 0;                 // seconds left in the current swing animation
var cooldown = 0;
var iframe = 0;
var hurtTimer = 0;
var sinceHurt = 99;            // drives out-of-combat regeneration
var killCount = 0;
var dead = false;

function start() {
    playLoop("idle");
}

function update(dt) {
    var d = scene.find("Delve");
    if (d && d.send("isOver")) { self.vx = 0; self.vy = 0; return; }

    if (cooldown > 0) cooldown -= dt;
    if (swing > 0) swing -= dt;
    if (hurtTimer > 0) hurtTimer -= dt;

    if (iframe > 0) {
        iframe -= dt;
        self.visible = (Math.floor(time.time * 24) % 2) === 0;
    } else {
        self.visible = true;
    }

    // --- regeneration.  A crawler with no healing at all is a one-way trip; a
    // crawler with constant healing has no tension.  The delay is the mechanic.
    sinceHurt += dt;
    if (sinceHurt > regenDelay) {
        var d2 = scene.find("Delve");
        if (d2) d2.send("heal", regenRate * dt);
    }

    var ix = input.axisX, iy = input.axisY;
    var mag = Math.sqrt(ix * ix + iy * iy);
    if (mag > 1) { ix /= mag; iy /= mag; mag = 1; }

    if (swing <= 0) {
        self.vx = ix * speed;
        self.vy = iy * speed;
        if (mag > 0.15) facing = pickDirection(ix, iy);
    } else {
        // Committing to a swing: the warden slows to half speed and keeps facing.
        self.vx = ix * speed * 0.45;
        self.vy = iy * speed * 0.45;
    }
    animate(mag);

    if (input.aDown && cooldown <= 0) beginSwing();
    // Hold the torch out in front, so the pool of light leads the swing.
    var torch = scene.find("WardenTorch");
    if (torch) {
        var dir = dirVector(facing);
        torch.setPosition(self.worldX + dir[0] * 1.1, self.worldY + dir[1] * 1.1 + 0.15, 0);
    }
}

function pickDirection(x, y) {
    var ax = Math.abs(x), ay = Math.abs(y);
    if (ax < 0.2 && ay < 0.2) return facing;
    if (ax >= ay) return x < 0 ? "left" : "right";
    return y < 0 ? "up" : "down";
}

function dirVector(f) {
    if (f === "left") return [-1, 0];
    if (f === "right") return [1, 0];
    if (f === "up") return [0, -1];
    return [0, 1];
}

function animate(mag) {
    if (dead) return;
    if (swing > 0) { playOnce("attack"); return; }
    // The knight's sheet has no hurt row, so damage reads as a red flash plus
    // the knockback and screen shake, rather than as a missing animation.
    if (hurtTimer > 0) { return; }
    if (mag > 0.15) {
        playLoop("walk");
        self.setAnimSpeed(1.15);
        // The knight sheet faces right; the left-facing pose is the mirror.
        self.flipX = facing === "left";
    } else {
        self.setAnimSpeed(1.0);
        playLoop("idle");
    }
}

function clipName(state) { return "hero_" + state; }
function playLoop(state) {
    var n = clipName(state);
    if (self.animation !== n) self.play(n);
}
function playOnce(state) {
    var n = clipName(state);
    if (self.animation !== n || self.isAnimationFinished()) self.play(n);
}

function beginSwing() {
    cooldown = swingCooldown;
    swing = 0.26;
    playOnce("attack");
    audio.play("sfx_swing.ogg");

    var dir = dirVector(facing);
    var hx = self.worldX + dir[0] * swingRange * 0.7;
    var hy = self.worldY + dir[1] * swingRange * 0.7;
    var fx = scene.spawn("SlashFx", hx, hy);
    if (fx) { fx.burst(12); after(0.22, function () { fx.destroy(); }); }

    // Dot-product cone, so enemies behind the warden are untouched.  A plain
    // radius test would let the player farm a crowd by spinning on the spot.
    var enemies = scene.findAll("Enemy");
    var hits = 0;
    for (var i = 0; i < enemies.length; i++) {
        var e = enemies[i];
        var dx = e.worldX - self.worldX, dy = e.worldY - self.worldY;
        var dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > swingRange || dist < 0.0001) continue;
        if ((dx / dist) * dir[0] + (dy / dist) * dir[1] < swingArc) continue;
        e.send("takeDamage", damage, dir[0], dir[1]);
        hits++;
    }
    if (hits > 2) scene.shake(0.12);
}

function hurt(amount, fromX, fromY) {
    if (iframe > 0) return;
    iframe = iframes;
    hurtTimer = 0.3;
    sinceHurt = 0;
    self.color = "#FFFF8080";
    after(0.18, function () { self.color = "#FFFFFFFF"; });
    audio.play("sfx_hurt.ogg");
    var dx = self.worldX - fromX, dy = self.worldY - fromY;
    var d = Math.max(0.0001, Math.sqrt(dx * dx + dy * dy));
    // Knockback is applied through the velocity, not the transform: the
    // physics step owns the position, so writing x/y directly gets overwritten.
    self.vx = (dx / d) * 7;
    self.vy = (dy / d) * 7;
    scene.shake(0.25);
    var del = scene.find("Delve");
    if (del) del.send("hurtWarden", amount);
}

function onEnemyKilled() { killCount++; }

/** Called by the director when the warden's health reaches zero. */
function onDeath() {
    if (dead) return;
    dead = true;
    self.vx = 0; self.vy = 0;
    self.color = "#FFFFFFFF";
    self.play(clipName("death"));
}

function getHp() { return hp; }
function getKills() { return killCount; }
function getFacing() { return facing; }

/** "<state>" resolved against this knight's own sheet.  Every clip in the
 *  project is namespaced by skin (see the monster scripts): there is one global
 *  clip namespace, so "idle" alone would be whatever sheet was declared first. */
function clipName(state) { return "hero_" + state; }
function playLoop(state) {
    var n = clipName(state);
    if (self.animation !== n) self.play(n);
}
function playOnce(state) {
    var n = clipName(state);
    if (self.animation !== n || self.isAnimationFinished()) self.play(n);
}
"""

GRUNT_JS = r"""
// Crypt of the Ember Sigil -- Grunt.js  (skeletal swordsman)
//
// A three-state melee AI: patrol between two anchors, chase on line of sight,
// attack in range.  Line of sight uses the dungeon grid through Delve.isSolid
// rather than a physics raycast, because the level *is* a grid and the grid
// answer is exact.
//
// Params: hp=34, speed=1.85, damage=9, sight=7.5, reach=1.15, cooldown=1.15,
//         skin=skeleton

var state = "patrol";
var hp = 34;
var attackCd = 0;
var homeX = 0, homeY = 0;
var patrolT = 0;
var dead = false;
var hurtFlash = 0;
var patrolGoalX = 0, patrolGoalY = 0;

function start() {
    homeX = self.x; homeY = self.y;
    playLoop("idle");
}

function update(dt) {
    if (dead) return;
    if (attackCd > 0) attackCd -= dt;
    if (hurtFlash > 0) {
        hurtFlash -= dt;
        self.color = "#FFFFFFFF";
    }

    var w = scene.find("Warden");
    if (!w) return;
    var dist = self.distanceTo(w);
    var sees = dist < sight && hasLineOfSight(w);

    if (state === "patrol") {
        patrolT -= dt;
        if (patrolT <= 0) {
            patrolT = 1.6;
            var a = random(0, Math.PI * 2);
            var r = random(0.8, 2.6);
            patrolGoalX = homeX + Math.cos(a) * r;
            patrolGoalY = homeY + Math.sin(a) * r;
        }
        stepToward(patrolGoalX, patrolGoalY, speed * 0.45, dt);
        playLoop("walk");
        if (sees) setState("chase");
    } else if (state === "chase") {
        if (dist < reach && attackCd <= 0) { setState("attack"); return; }
        if (dist > sight * 1.5) { setState("patrol"); return; }
        stepToward(w.x, w.y, speed, dt);
        playLoop("walk");
    } else if (state === "attack") {
        self.vx = 0; self.vy = 0;
        playOnce("attack");
        if (attackCd <= 0) {
            attackCd = cooldown;
            // The hit lands on the animation's contact frame, not on frame 0.
            after(0.24, function () {
                if (dead) return;
                var t = scene.find("Warden");
                if (t && self.distanceTo(t) < reach * 1.5) t.send("hurt", damage, self.x, self.y);
            });
        }
        if (dist > reach * 1.4) setState("chase");
    }
}

function setState(next) {
    if (state === next) return;
    state = next;
    if (next === "chase") playLoop("walk");
    if (next === "attack") { attackCd = 0.35; playOnce("attack"); }
}

function stepToward(tx, ty, sp, dt) {
    var dx = tx - self.x, dy = ty - self.y;
    var d = Math.sqrt(dx * dx + dy * dy);
    if (d < 0.05) { self.vx = 0; self.vy = 0; return; }
    var step = Math.min(sp, d / Math.max(dt, 0.0001));
    self.vx = (dx / d) * step;
    self.vy = (dy / d) * step;
    self.flipX = dx < 0;
}

function hasLineOfSight(target) {
    var d = scene.find("Delve");
    if (!d) return true;
    return d.send("clearPath", self.worldX, self.worldY, target.worldX, target.worldY);
}

/** Called by Warden.js and Bolt.js. */
function takeDamage(amount, dirX, dirY) {
    if (dead) return;
    hp -= amount;
    hurtFlash = 0.12;
    self.color = "#FFFFFFFF";
    var d = scene.find("Delve");
    if (d) d.send("onEnemyHit", self.worldX, self.worldY);
    if (dirX !== undefined) {
        self.vx = dirX * 5; self.vy = dirY * 5;
    }
    if (hp <= 0) die(); else { setState("chase"); playOnce("hurt"); }
}

function die() {
    dead = true;
    playOnce("death");
    self.vx = 0; self.vy = 0;
    var d = scene.find("Delve");
    if (d) d.send("onEnemyKilled", self.name, self.worldX, self.worldY);
    after(1.4, function () { self.destroy(); });
}

function getHp() { return hp; }

/** "<skin>_<clip>".  Clip names are global in the engine (AnimationSystem
 *  resolves "<name>.anim" in the project assets), so an enemy that played a bare
 *  "idle" would render the *hero's* idle sheet: the call succeeds and the art is
 *  wrong.  Namespacing every clip by skin is what makes that impossible. */
function clipName(state) { return skin + "_" + state; }
/** Play `state` only when it is not already running, so a looping clip is not
 *  restarted from frame 0 sixty times a second. */
function playLoop(state) {
    var n = clipName(state);
    if (self.animation !== n) self.play(n);
}
/** Play `state` to completion, restarting only once the previous pass ended. */
function playOnce(state) {
    var n = clipName(state);
    if (self.animation !== n || self.isAnimationFinished()) self.play(n);
}
"""

GHOST_JS = r"""
// Crypt of the Ember Sigil -- Ghost.js  (drowned bride)
//
// The wall-phasing hunter.  Everything about it is built around one rule: it
// ignores the level grid, so a corridor is not a place to hide.  It is slow
// enough to outrun and it never attacks from behind a wall, so the counter is
// always available -- but the fight happens where it wants, not where the
// player chose.
//
// Params: hp=26, speed=2.55, damage=12, sight=9, reach=1.05, cooldown=1.5

var hp = 26;
var attackCd = 0;
var dead = false;
var phase = 0;
var drift = 0;

function start() { playLoop("idle"); }

function update(dt) {
    if (dead) return;
    if (attackCd > 0) attackCd -= dt;
    var w = scene.find("Warden");
    if (!w) return;
    var dist = self.distanceTo(w);

    // A slow vertical bob sells "not walking" without any extra art.
    drift += dt * 2.2;
    self.y = self.y + Math.sin(drift) * 0.0025;

    if (dist < sight) {
        phase = 1;
    } else if (dist > sight * 1.3) {
        phase = 0;
    }

    if (phase === 0) {
        self.vx = 0; self.vy = 0;
        playLoop("idle");
        return;
    }

    if (dist < reach && attackCd <= 0) {
        attackCd = cooldown;
        playOnce("attack");
        var self2 = self;
        after(0.3, function () {
            if (dead) return;
            var t = scene.find("Warden");
            if (t && self2.distanceTo(t) < reach * 1.6) t.send("hurt", damage, self2.x, self2.y);
        });
    } else if (clipName("attack") !== self.animation || self.isAnimationFinished()) {
        playLoop("walk");
    }

    if (dist > 0.6) {
        var dx = (w.x - self.x) / dist, dy = (w.y - self.y) / dist;
        self.vx = dx * speed;
        self.vy = dy * speed;
    } else {
        self.vx = 0; self.vy = 0;
    }
    self.flipX = (w.x - self.x) < 0;
}

function takeDamage(amount, dirX, dirY) {
    if (dead) return;
    hp -= amount;
    self.color = "#FFFFFFFF";
    var d = scene.find("Delve");
    if (d) d.send("onEnemyHit", self.worldX, self.worldY);
    if (dirX !== undefined) { self.vx = dirX * 4; self.vy = dirY * 4; }
    if (hp <= 0) {
        dead = true;
        playOnce("death");
        self.vx = 0; self.vy = 0;
        var d2 = scene.find("Delve");
        if (d2) d2.send("onEnemyKilled", self.name, self.worldX, self.worldY);
        after(1.4, function () { self.destroy(); });
    } else {
        playOnce("hurt");
    }
}

function getHp() { return hp; }

/** "<skin>_<clip>".  Clip names are global in the engine (AnimationSystem
 *  resolves "<name>.anim" in the project assets), so an enemy that played a bare
 *  "idle" would render the *hero's* idle sheet: the call succeeds and the art is
 *  wrong.  Namespacing every clip by skin is what makes that impossible. */
function clipName(state) { return skin + "_" + state; }
/** Play `state` only when it is not already running, so a looping clip is not
 *  restarted from frame 0 sixty times a second. */
function playLoop(state) {
    var n = clipName(state);
    if (self.animation !== n) self.play(n);
}
/** Play `state` to completion, restarting only once the previous pass ended. */
function playOnce(state) {
    var n = clipName(state);
    if (self.animation !== n || self.isAnimationFinished()) self.play(n);
}
"""

CRAWLER_JS = r"""
// Crypt of the Ember Sigil -- Crawler.js  (blood monster)
//
// The ranged threat.  It backs off when the warden closes, spits at range, and
// refuses to fire through masonry -- so it is a positioning problem rather than
// a damage race.  Delve keeps the projectile count bounded.
//
// Params: hp=20, speed=2.05, damage=7, range=6.4, keepAway=3.2, cooldown=1.9

var hp = 20;
var fireCd = 0;
var dead = false;
var backoff = false;

function start() { playLoop("idle"); }

function update(dt) {
    if (dead) return;
    if (fireCd > 0) fireCd -= dt;
    var w = scene.find("Warden");
    if (!w) return;
    var dist = self.distanceTo(w);

    // Preference band: inside keepAway it retreats, inside range it fires,
    // beyond range it closes.  A single "chase until in range" rule makes a
    // ranged enemy walk into swords.
    if (dist < keepAway) {
        backoff = true;
        var dx = (self.x - w.x) / Math.max(dist, 0.0001);
        var dy = (self.y - w.y) / Math.max(dist, 0.0001);
        self.vx = dx * speed; self.vy = dy * speed;
        playLoop("walk");
    } else if (dist > range) {
        backoff = false;
        var d2 = Math.max(dist, 0.0001);
        self.vx = ((w.x - self.x) / d2) * speed;
        self.vy = ((w.y - self.y) / d2) * speed;
        playLoop("walk");
    } else {
        backoff = false;
        self.vx = 0; self.vy = 0;
    }
    self.flipX = (w.x - self.x) < 0;

    if (backoff || self.animation === clipName("attack")) {
        playLoop("idle");
    } else if (self.vx === 0 && self.vy === 0) {
        playLoop("idle");
    }

    if (dist < range && fireCd <= 0 && !backoff) {
        fireCd = cooldown;
        playOnce("attack");
        var target = w;
        var me = self;
        after(0.4, function () {
            if (dead) return;
            var del = scene.find("Delve");
            if (del && !del.send("canSpawn")) return;
            var dx2 = target.x - me.x, dy2 = target.y - me.y;
            var dd = Math.max(0.0001, Math.sqrt(dx2 * dx2 + dy2 * dy2));
            var s = scene.spawn("Spit", me.x, me.y + 0.2);
            if (s) {
                s.vx = (dx2 / dd) * 6.2;
                s.vy = (dy2 / dd) * 6.2;
                s.active = true;
            }
            audio.play("sfx_spit.ogg");
        });
    }
}

function takeDamage(amount, dirX, dirY) {
    if (dead) return;
    hp -= amount;
    self.color = "#FFFFFFFF";
    var d = scene.find("Delve");
    if (d) d.send("onEnemyHit", self.worldX, self.worldY);
    if (dirX !== undefined) { self.vx = dirX * 4.5; self.vy = dirY * 4.5; }
    if (hp <= 0) {
        dead = true;
        self.play(clipName("hurt"));       // collapse; this skin has no death row
        var d2 = scene.find("Delve");
        if (d2) d2.send("onEnemyKilled", self.name, self.worldX, self.worldY);
        // Fade out through the sprite colour: the engine multiplies the tint
        // alpha into the quad, and the crawler is the one enemy that has to
        // leave the field without a death animation.
        self.vx = 0; self.vy = 0;
        after(0.25, function () { self.color = "#BFFFFFFF"; });
        after(0.55, function () { self.color = "#7FFFFFFF"; });
        after(0.85, function () { self.color = "#3FFFFFFF"; });
        after(1.2, function () { self.destroy(); });
    } else {
        playOnce("hurt");
    }
}

function getHp() { return hp; }

/** "<skin>_<clip>".  Clip names are global in the engine (AnimationSystem
 *  resolves "<name>.anim" in the project assets), so an enemy that played a bare
 *  "idle" would render the *hero's* idle sheet: the call succeeds and the art is
 *  wrong.  Namespacing every clip by skin is what makes that impossible. */
function clipName(state) { return skin + "_" + state; }
/** Play `state` only when it is not already running, so a looping clip is not
 *  restarted from frame 0 sixty times a second. */
function playLoop(state) {
    var n = clipName(state);
    if (self.animation !== n) self.play(n);
}
/** Play `state` to completion, restarting only once the previous pass ended. */
function playOnce(state) {
    var n = clipName(state);
    if (self.animation !== n || self.isAnimationFinished()) self.play(n);
}
"""

SPIT_JS = r"""
// Crypt of the Ember Sigil -- Spit.js
// Acid.  Travels, damages the warden, dies on masonry.  The masonry test is the
// interesting part: without it a ranged enemy shoots through the level and the
// player cannot use cover at all.

var life = 2.4;

function update(dt) {
    life -= dt;
    if (life <= 0) { self.destroy(); return; }
    // The projectile owns its own damage number so the emitter does not have to
    // pass it through a spawn API that has no parameter channel.
    var w = scene.find("Warden");
    if (w && self.distanceTo(w) < 0.55 && w.visible) {
        // The warden's ouch test owns its own invulnerability window, so a
        // stream of spit cannot stack damage in one frame.
        w.send("hurt", damage, self.x, self.y);
        self.destroy();
        return;
    }
    var d = scene.find("Delve");
    if (d && d.send("isSolidAt", self.worldX, self.worldY)) {
        var fx = scene.spawn("SplatFx", self.x, self.y);
        if (fx) fx.burst(8);
        self.destroy();
    }
}
"""

BOLT_JS = r"""
// Crypt of the Ember Sigil -- Bolt.js
// The warden's thrown ember.  Hits the first enemy within reach, dies on walls.

var life = 1.6;

function update(dt) {
    life -= dt;
    if (life <= 0) { self.destroy(); return; }
    var enemies = scene.findAll("Enemy");
    for (var i = 0; i < enemies.length; i++) {
        if (self.distanceTo(enemies[i]) < 0.62) {
            enemies[i].send("takeDamage", 16, self.vx * 0.1, self.vy * 0.1);
            var fx = scene.spawn("SplatFx", self.x, self.y);
            if (fx) fx.burst(10);
            self.destroy();
            return;
        }
    }
    var d = scene.find("Delve");
    if (d && d.send("isSolidAt", self.worldX, self.worldY)) self.destroy();
}
"""

FX_JS = r"""
// Crypt of the Ember Sigil -- Fx.js
// One-shot particle burst: the emitter fires once when the clone is spawned and
// the copy removes itself when its life runs out.  Pooling the burst objects is
// Delve's job; this script only owns its own lifetime.
var life = 1.1;

function start() { self.burst(14); }

function update(dt) {
    life -= dt;
    if (life <= 0) self.destroy();
}
"""

DELVE_JS = r"""
// Crypt of the Ember Sigil -- DeepDelve.js
//
// The whole game, in one place: it generates the crypt from the room grammar,
// paints it in 8x8 tile chunks, builds the merged wall colliders, seeds the
// loot and props, and then runs the wave director, the HUD and the win state.
//
// Why one script rather than five: the level, the spawner and the HUD all need
// the same three answers -- where is floor, where did the player come from, is
// the run over -- and splitting them into separate objects means every question
// becomes a send() across the scene, which cannot be type-checked and fails
// silently when a handler is missing.  One owner, one state machine.
//
// Params: chunk=8, sigils=3, waveBase=4, waveStep=2, waveGap=3.4

// --- level grammar, mirroring tools/build_dungeon.py -------------------------
var MAP_W = 72, MAP_H = 56;
var ROOMS = [
    ["Antechamber", 32, 46, 8, 7],
    ["West Hall", 12, 44, 12, 6],
    ["Ossuary", 8, 30, 10, 9],
    ["Font of Ash", 24, 33, 9, 8],
    ["Long Gallery", 40, 32, 16, 6],
    ["Ember Shrine", 60, 26, 9, 8],
    ["Kitehen", 44, 16, 8, 6],
    ["Barrow", 20, 14, 12, 9],
    ["Vault Gate", 4, 6, 10, 7],
    ["Deep Vault", 20, 2, 14, 8],
    ["Watchpost", 52, 44, 8, 6]
];
var LOOPS = [["Font of Ash", "Ossuary"], ["Kitehen", "Long Gallery"], ["Watchpost", "West Hall"]];

// floor id -> atlas cell.  DeepDelve picks the cell; the *clips* that turn a
// cell into a drawable sub-rectangle are declared in game.json.
var CELL = { f: 6, F: 22, c: 9, w: 1, W: 3 };
var SOLID = { w: 1, W: 1, C: 1 };

var grid = [];
var roomAt = {};      // "x,y" -> room name, for spawning and messages
var floorCells = [];
var torchCells = [];
var doors = [];
var sigils = [];
var sigilIndex = 0;

// Open cells in a fixed shuffled order: the spawn cursor walks this.
var openCells = [];
var spawnCursor = 0;

var hp = 100, maxHp = 100;
var dead = false;
var score = 0, kills = 0;
var wave = 0, toSpawn = 0, spawnTimer = 0;
var over = false, won = false;
var toast = 0;
var enemyCap = 26;
var spawned = 0;

function start() {
    generate();
    paintFloor();
    buildWalls();
    placeTorches();
    placeLoot();
    placeSigils();
    scene.find("HUD_Sigils").text = "SIGILS 0/" + sigils;
    refresh();
    msg("The crypt wakes. Find the sigils.");
}

// --------------------------------------------------------------- generation
function generate() {
    for (var y = 0; y < MAP_H; y++) {
        var row = [];
        for (var x = 0; x < MAP_W; x++) row.push(".");
        grid.push(row);
    }
    for (var i = 0; i < ROOMS.length; i++) {
        var r = ROOMS[i];
        carveRoom(r[1], r[2], r[3], r[4], r[0]);
    }
    for (var j = 0; j < ROOMS.length - 1; j++) connect(ROOMS[j], ROOMS[j + 1]);
    for (var k = 0; k < LOOPS.length; k++) {
        var a = findRoom(LOOPS[k][0]), b = findRoom(LOOPS[k][1]);
        if (a && b) connect(a, b);
    }
    // floor variants, then the wall shell
    for (var yy = 0; yy < MAP_H; yy++) {
        for (var xx = 0; xx < MAP_W; xx++) {
            if (grid[yy][xx] === "f" && (xx * 7 + yy * 13) % 11 === 0) grid[yy][xx] = "F";
        }
    }
    for (var y2 = 0; y2 < MAP_H; y2++) {
        for (var x2 = 0; x2 < MAP_W; x2++) {
            if (grid[y2][x2] === "." && touchesFloor(x2, y2)) grid[y2][x2] = "w";
        }
    }
    // Collect every open cell once, so spawning never has to search the grid.
    for (var oy = 0; oy < MAP_H; oy++) {
        for (var ox = 0; ox < MAP_W; ox++) {
            var t2 = grid[oy][ox];
            if (t2 === "f" || t2 === "F" || t2 === "c") openCells.push([ox, oy]);
        }
    }
    shuffle(openCells);
    var start = findRoom("Antechamber");
    scene.find("Warden").setWorldPosition(cellX(start[1] + start[3] / 2), cellY(start[2] + start[4] / 2));
}

function findRoom(name) {
    for (var i = 0; i < ROOMS.length; i++) if (ROOMS[i][0] === name) return ROOMS[i];
    return null;
}

function carveRoom(cx, cy, w, h, name) {
    for (var y = cy; y < cy + h; y++) {
        for (var x = cx; x < cx + w; x++) {
            grid[y][x] = "f";
            roomAt[x + "," + y] = name;
        }
    }
}

function connect(a, b) {
    var acx = a[1] + Math.floor(a[3] / 2), acy = a[2] + Math.floor(a[4] / 2);
    var bcx = b[1] + Math.floor(b[3] / 2), bcy = b[2] + Math.floor(b[4] / 2);
    if (Math.abs(bcx - acx) >= Math.abs(bcy - acy)) {
        carveCorridor(Math.min(acx, bcx), acy - 1, Math.max(acx, bcx) + 1, acy + 2);
        carveCorridor(bcx - 1, Math.min(acy, bcy), bcx + 2, Math.max(acy, bcy) + 1);
    } else {
        carveCorridor(acx - 1, Math.min(acy, bcy), acx + 2, Math.max(acy, bcy) + 1);
        carveCorridor(Math.min(acx, bcx), bcy - 1, Math.max(acx, bcx) + 1, bcy + 2);
    }
}

function carveCorridor(x0, y0, x1, y1) {
    for (var y = clamp(y0, 0, MAP_H - 1); y < clamp(y1, 0, MAP_H); y++) {
        for (var x = clamp(x0, 0, MAP_W - 1); x < clamp(x1, 0, MAP_W); x++) {
            if (grid[y][x] === ".") grid[y][x] = "f";
        }
    }
}

function touchesFloor(x, y) {
    var d = [[1, 0], [-1, 0], [0, 1], [0, -1]];
    for (var i = 0; i < 4; i++) {
        var nx = x + d[i][0], ny = y + d[i][1];
        if (nx < 0 || ny < 0 || nx >= MAP_W || ny >= MAP_H) continue;
        var t = grid[ny][nx];
        if (t === "f" || t === "F" || t === "c") return true;
    }
    return false;
}

// -------------------------------------------------------------------- paint
function paintFloor() {
    for (var y = 0; y < MAP_H; y += chunk) {
        for (var x = 0; x < MAP_W; x += chunk) {
            var counts = {};
            var w = Math.min(chunk, MAP_W - x), h = Math.min(chunk, MAP_H - y);
            for (var yy = y; yy < y + h; yy++) {
                for (var xx = x; xx < x + w; xx++) {
                    var t = grid[yy][xx];
                    counts[t] = (counts[t] || 0) + 1;
                }
            }
            var dominant = ".", best = -1;
            for (var key in counts) if (counts[key] > best) { best = counts[key]; dominant = key; }
            var o = scene.spawn("FloorChunk", cellX(x) + w / 2, cellY(y) - h / 2);
            if (!o) continue;
            o.tag = "FloorTile";
            o.order = -100;
            o.scaleX = w;
            o.scaleY = h;
            paint(o, dominant);
            // A chunk straddling two materials gets one overlay per minority
            // tile, placed on the *centroid* of that tile's cells and sized to
            // cover them -- so a floor/carpet boundary is a seam in the right
            // place rather than a blotch in the middle of the chunk.
            for (var k2 in counts) {
                if (k2 === dominant) continue;
                var sumX = 0, sumY = 0, n = 0;
                var minX = 999, maxX = -999, minY = 999, maxY = -999;
                for (var oy = y; oy < y + h; oy++) {
                    for (var ox = x; ox < x + w; ox++) {
                        if (grid[oy][ox] !== k2) continue;
                        sumX += ox; sumY += oy; n++;
                        if (ox < minX) minX = ox;
                        if (ox > maxX) maxX = ox;
                        if (oy < minY) minY = oy;
                        if (oy > maxY) maxY = oy;
                    }
                }
                if (n === 0) continue;
                var ov = scene.spawn("FloorOverlay",
                    cellX(minX + (maxX - minX + 1) / 2), cellY(minY + (maxY - minY + 1) / 2));
                if (!ov) continue;
                ov.tag = "FloorTile";
                ov.order = -99;
                ov.scaleX = (maxX - minX + 1);
                ov.scaleY = (maxY - minY + 1);
                paint(ov, k2);
            }
        }
    }
}

function paint(o, tile) {
    if (o === null) return;
    o.setTexture("tiles_atlas.png");
    if (CELL[tile] === undefined) { o.play("tile_floor"); return; }
    if (tile === "f") o.play("tile_floor");
    else if (tile === "F") o.play("tile_floor_alt");
    else if (tile === "c") o.play("tile_carpet");
    else if (tile === "w") o.play("tile_wall");
    else if (tile === "W") o.play("tile_wall_alt");
    else o.play("tile_floor");
}

function buildWalls() {
    // Colliders are per merged run; the *sprite* is capped at three tiles so the
    // 16px atlas pattern is not smeared into a stripe.  One object carries both,
    // so a wall is never a collider without a face.
    for (var y = 0; y < MAP_H; y++) {
        var x = 0;
        while (x < MAP_W) {
            var t = grid[y][x];
            if (!SOLID[t]) { x++; continue; }
            var run = 1;
            while (x + run < MAP_W && grid[y][x + run] === t) run++;
            var done = 0;
            while (done < run) {
                var piece = Math.min(3, run - done);
                var wall = scene.spawn("WallRun", cellX(x + done) + piece / 2, cellY(y));
                if (wall) {
                    wall.tag = "Wall";
                    wall.order = 6;
                    wall.scaleX = piece;
                    wall.scaleY = 1;
                    paint(wall, t);
                }
                done += piece;
            }
            x += run;
        }
    }
}

function placeTorches() {
    // A torch every 6 cells along a wall beside open floor: enough light to
    // read the layout, few enough that the light list stays short.
    for (var y = 1; y < MAP_H - 1; y++) {
        for (var x = 1; x < MAP_W - 1; x++) {
            if (!SOLID[grid[y][x]]) continue;
            if ((x * 3 + y * 5) % 6 !== 0) continue;
            var below = grid[y + 1][x];
            if (below !== "f" && below !== "F" && below !== "c") continue;
            var t = scene.spawn("Torch", cellX(x), cellY(y) - 0.35);
            if (!t) continue;
            t.tag = "Torch";
            t.order = 8;
            t.setTexture("prop_torch.png");
            torchCells.push([cellX(x), cellY(y) + 0.4]);
            if (torchCells.length >= 8) return;    // light budget
        }
    }
}

function placeLoot() {
    // Chests sit against a wall inside a room, caches in the middle of corridors.
    var placed = 0;
    for (var i = 0; i < ROOMS.length && placed < 7; i++) {
        var r = ROOMS[i];
        if (r[0] === "Antechamber") continue;
        var cx = r[1] + 1 + (i % 2) * (r[3] - 3);
        var cy = r[2] + 1;
        if (isSolidCell(cx, cy)) continue;
        var c = scene.spawn("Chest", cellX(cx), cellY(cy));
        if (!c) continue;
        c.tag = "Chest";
        c.order = 7;
        c.setTexture("prop_chest.png");
        placed++;
    }
    var caches = 0;
    for (var j = 0; j < ROOMS.length && caches < 6; j++) {
        var rr = ROOMS[j];
        var px = rr[1] + rr[3] - 2;
        var py = rr[2] + rr[4] - 2;
        if (isSolidCell(px, py)) continue;
        var p = scene.spawn("Cache", cellX(px), cellY(py));
        if (!p) continue;
        p.tag = "Cache";
        p.order = 5;
        p.setTexture("prop_crate.png");
        caches++;
    }
}

function placeSigils() {
    // Three sigils: one near the start, one mid-map, one in the Deep Vault.
    var spots = [["Ember Shrine"], ["Barrow"], ["Deep Vault"]];
    for (var i = 0; i < spots.length; i++) {
        var r = findRoom(spots[i][0]);
        if (!r) continue;
        var s = scene.spawn("Sigil", cellX(r[1] + Math.floor(r[3] / 2)), cellY(r[2] + Math.floor(r[4] / 2)));
        if (!s) continue;
        s.tag = "Sigil";
        s.order = 9;
        s.setEmitting(true);
        sigils.push(s);
    }
}

// ------------------------------------------------------------------- helpers
function cellX(cx) { return cx - MAP_W / 2 + 0.5; }
function cellY(cy) { return -(cy - MAP_H / 2 + 0.5); }
function cellOfX(wx) { return Math.floor(wx + MAP_W / 2); }
function cellOfY(wy) { return Math.floor(-wy + MAP_H / 2); }

function tileAt(wx, wy) {
    var cx = cellOfX(wx), cy = cellOfY(wy);
    if (cx < 0 || cy < 0 || cx >= MAP_W || cy >= MAP_H) return "w";
    return grid[cy][cx];
}

function isSolidAt(wx, wy) {
    var t = tileAt(wx, wy);
    return t !== "f" && t !== "F" && t !== "c";
}

function isSolidCell(cx, cy) {
    if (cx < 0 || cy < 0 || cx >= MAP_W || cy >= MAP_H) return true;
    var t = grid[cy][cx];
    return t !== "f" && t !== "F" && t !== "c";
}

/** Sampled line-of-sight used by the melee AI. */
function clearPath(x0, y0, x1, y1) {
    var dx = x1 - x0, dy = y1 - y0;
    var steps = Math.ceil(Math.sqrt(dx * dx + dy * dy) * 3);
    for (var i = 1; i < steps; i++) {
        var t = i / steps;
        if (isSolidAt(x0 + dx * t, y0 + dy * t)) return false;
    }
    return true;
}

/**
 * An open floor position, at least `minDist` from the warden.
 *
 * This walks the pre-computed list of open cells from a rotating cursor rather
 * than sampling random points and testing them.  Two reasons, one of them a bug
 * this replaced: rejection sampling on a 72x56 grid with a 60-attempt budget
 * fails outright whenever the sampler is unlucky or low-entropy (in the offline
 * test harness random() is a constant, so *every* attempt landed on the same
 * wall cell and the crypt shipped with no enemies at all).  The cursor also
 * spreads consecutive spawns across the whole level instead of clumping them.
 */
function freeSpot(minDist) {
    var n = openCells.length;
    if (n === 0) return null;
    var w = scene.find("Warden");
    var start = spawnCursor % n;
    var fallback = null;
    for (var i = 0; i < n; i++) {
        var c = openCells[(start + i) % n];
        var wx = cellX(c[0]), wy = cellY(c[1]);
        var d = w ? dist2d(wx, wy, w.worldX, w.worldY) : 999;
        if (d < 2.2) continue;                   // never on top of the player
        if (d >= minDist) { spawnCursor = (start + i + 1) % n; return [wx, wy]; }
        if (fallback === null) fallback = [wx, wy];
    }
    return fallback;
}

function canSpawn() { return !over && spawned < enemyCap; }

function msg(t) { scene.find("HUD_Msg").text = t; }

function refresh() {
    scene.find("HUD_Hp").text = "HP " + Math.max(0, Math.round(hp));
    scene.find("HUD_Score").text = "SCORE " + score;
    scene.find("HUD_Wave").text = "WAVE " + wave;
    scene.find("HUD_Sigils").text = "SIGILS " + sigilIndex + "/" + sigils;
}

// ---------------------------------------------------------------- game loop
function update(dt) {
    if (over) {
        if (input.aDown) scene.reload();
        if (toast > 0) { toast -= dt; if (toast <= 0) msg(""); }
        return;
    }

    // Wave director: keep a floor of enemies alive and grow the cap, but never
    // dump more than `enemyCap` into the level.
    var alive = scene.count("Enemy");
    if (toSpawn > 0) {
        spawnTimer -= dt;
        if (spawnTimer <= 0 && alive < enemyCap) {
            spawnTimer = Math.max(0.5, waveGap - wave * 0.12);
            spawnOne();
            toSpawn--;
        }
    } else if (alive === 0) {
        wave++;
        toSpawn = waveBase + wave * waveStep;
        toast = 2.2;
        msg("WAVE " + wave + " -- they are coming");
    }
    if (toast > 0) { toast -= dt; if (toast <= 0) msg(""); }

    // Sigil pickup: the objective.  Collected by proximity rather than a trigger
    // because the sigil is a sprite with no collider (it costs a body otherwise).
    var w = scene.find("Warden");
    for (var i = sigils.length - 1; i >= 0; i--) {
        var s = sigils[i];
        if (!s || s.active === false) continue;
        if (w && w.distanceTo(s) < 0.9) {
            var pos = [s.worldX, s.worldY];
            s.destroy();
            sigils.splice(i, 1);
            sigilIndex++;
            score += 250;
            toast = 2.0;
            audio.play("sfx_sigil.ogg");
            var fx = scene.spawn("SplatFx", pos[0], pos[1]);
            if (fx) fx.burst(18);
            if (sigilIndex >= 3) win();
            else msg("SIGIL CLAIMED  " + sigilIndex + "/3");
        }
    }
    refresh();
}

function spawnOne() {
    var spot = freeSpot(9.5);
    if (!spot) return;
    var roll = random(0, 1);
    var kind = "Grunt";
    if (wave >= 2 && roll > 0.62) kind = "Ghost";
    if (wave >= 3 && roll > 0.84) kind = "Crawler";
    var e = scene.spawn(kind, spot[0], spot[1]);
    if (e) {
        e.tag = "Enemy";
        e.active = true;
        spawned++;
    }
}

function onEnemyKilled(name, x, y) {
    kills++;
    score += 40 + wave * 10;
    var fx = scene.spawn("SplatFx", x, y);
    if (fx) fx.burst(16);
    var w = scene.find("Warden");
    if (w) w.send("onEnemyKilled");
}

function onEnemyHit(x, y) {
    var fx = scene.spawn("HitFx", x, y);
    if (fx) fx.burst(6);
    scene.shake(0.06);
}

/** Called by the warden through send("hurtWarden", amount). */
function hurtWarden(amount) {
    if (over) return;
    hp -= amount;
    if (hp <= 0) { hp = 0; gameOver(); }
    refresh();
}

function heal(amount) {
    if (over) return;
    hp = Math.min(maxHp, hp + amount);
}

function win() {
    over = true;
    won = true;
    msg("EVERY SIGIL CLAIMED  --  press A for a new crypt");
    audio.play("sfx_win.ogg");
    refresh();
}

function gameOver() {
    over = true;
    won = false;
    var w = scene.find("Warden");
    if (w) w.send("onDeath");
    msg("YOU DIED IN THE DARK  --  press A to try again");
    audio.play("sfx_death.ogg");
}

function isOver() { return over; }
function getScore() { return score; }
function getHp() { return hp; }
function isWon() { return won; }
function getWave() { return wave; }
"""


# --------------------------------------------------------------------------
# game.json
# --------------------------------------------------------------------------

DUNGEON_PACK = "2d_pixel_dungeon_asset_pack_v2_0"
FOREST_PACK = "legacy_fantasy_high_forest_2_0"
UI_PACK = "all_assets"
KENNEY_UI = "kenney_ui_pack"
TINY_UI = "tinyrpg_dragonregaliagui_v1_0"
ENEMY_PACK = "enemy_animations_set"

# Hero: Legacy Fantasy's own 80x96 knight, which is the character that ships with
# the forest pack -- one art style for the whole game rather than two.
# The knight's own sheets, straight out of Legacy Fantasy - High Forest.
# Every clip is a full animation row (96x96 frames for the free character pack,
# high-res sheets here), so the hero gets idle/walk/attack/death without any
# hand-rolled frame timer -- Animator + AnimationClip do the stepping.
HERO_SHEETS = {
    # name:  (path tail, frames, fps, loop)
    "idle":   ("Character/Idle/Idle-Sheet.png", 4, 6, True),
    "walk":   ("Character/Run/Run-Sheet.png", 8, 12, True),
    "attack": ("Character/Attack-01/Attack-01-Sheet.png", 8, 14, False),
    "death":  ("Character/Dead/Dead-Sheet.png", 8, 10, False),
}

# Monsters: Penzilla's enemy set.  Five real sheets per skin (32x32 frames), so
# each enemy type animates through the same state names the hero uses.
MONSTER_SHEETS = {
    "grunt":   ("enemies-skeleton1", {"idle": (6, 6, True), "walk": (10, 10, True),
                                      "attack": (9, 12, False), "hurt": (5, 10, False),
                                      "death": (17, 10, False)}),
    "ghost":   ("enemies-vampire",   {"idle": (6, 6, True), "walk": (8, 9, True),
                                      "attack": (16, 12, False), "hurt": (5, 10, False),
                                      "death": (14, 10, False)}),
    # skeleton2 is the only skin without a usable death sheet: the store's
    # detector finds no admissible grid in enemies-skeleton2_death.png (no
    # transparent gutter between frames), so declaring an animation block for it
    # would be a claim the store contradicts.  The crawler collapses through its
    # damage frames and fades instead -- see Crawler.js.
    "crawler": ("enemies-skeleton2", {"idle": (6, 6, True), "walk": (10, 10, True),
                                      "attack": (15, 12, False), "hurt": (5, 10, False)}),
}
# The sheet files are named by state, not uniformly ("movemen" is the pack's own
# spelling, and skeleton2's death sheet carries no sheet metadata).
MONSTER_FILES = {"idle": "idle", "walk": "movement", "attack": "attack",
                 "hurt": "take_damage", "death": "death"}


def build():
    grid, room_by_name, solid = carve_level()
    runs = wall_runs(solid)
    print(f"  level: {MAP_W}x{MAP_H} cells, {len(solid)} wall cells merged into {len(runs)} runs")

    # ---- assets
    assets = []

    # Tileset: the atlas has no metadata in the pack, so the grid is declared
    # here (10x10 of 16px, verified with tools/probe_png.py) and every tile is a
    # one-frame clip, which is how the engine addresses a cell.
    tile_clips = {
        "tile_floor": clip([6], 1, False),
        "tile_floor_alt": clip([22], 1, False),
        "tile_carpet": clip([9], 1, False),
        "tile_wall": clip([1], 1, False),
        "tile_wall_alt": clip([3], 1, False),
    }
    assets.append(asset(DUNGEON_PACK,
                        "character and tileset/Dungeon_Tileset.png",
                        "tiles_atlas.png",
                        tileset={"tileSize": 16, "columns": 10, "rows": 10},
                        clips=tile_clips))

    # Props: single-frame cells of the same atlas.
    props = {
        "prop_torch": ("items and trap_animation/torch/torch_1.png", 1, 1),
        "prop_crate": ("items and trap_animation/box_1/box_1_1.png", 1, 1),
        "prop_chest": ("items and trap_animation/chest/chest_1.png", 1, 1),
        "prop_coin": ("items and trap_animation/coin/coin_1.png", 1, 1),
        "prop_key": ("items and trap_animation/keys/keys_1_1.png", 1, 1),
        "prop_flask": ("items and trap_animation/flasks/flasks_1_1.png", 1, 1),
    }
    for name, (path, cols, rows) in props.items():
        assets.append(asset(DUNGEON_PACK, path, name + ".png"))

    # Enemies: one sheet per state per skin, 32x32 frames.  The state names are
    # namespaced by skin ("grunt_walk"), because the engine resolves a clip name
    # against the whole project: a bare "walk" would be the first sheet declared.
    for alias, (stem, states) in MONSTER_SHEETS.items():
        for state, (frames, fps, loop) in states.items():
            variant = MONSTER_FILES[state]
            # skeleton2's movement sheet is spelled "movemen" in the pack.
            tail = "enemies-skeleton2_movemen.png" if (stem == "enemies-skeleton2" and state == "walk") \
                else f"{stem}_{variant}.png"
            assets.append(asset(ENEMY_PACK, tail,
                                f"{alias}_{state}.png",
                                anim={"columns": frames, "rows": 1, "frames": frames},
                                clips={f"{alias}_{state}": clip(list(range(frames)), fps, loop)}))

    # Hero: the forest pack's own knight sheets.  One asset per sheet, with the
    # clip named "<skin>_<state>" -- see the scripts for why the prefix matters.
    for name, (path, frames, fps, loop) in HERO_SHEETS.items():
        asset_name = "hero_" + name + ".png"
        assets.append(asset(FOREST_PACK, path,
                            asset_name,
                            anim={"columns": frames, "rows": 1, "frames": frames},
                            clips={"hero_" + name: clip(list(range(frames)), fps, loop)}))
    # The hero's starting texture (scene SpriteRenderer) must be a declared
    # asset, so the idle frame is listed with its own clip name too.
    assets.append(asset(FOREST_PACK, HERO_SHEETS["idle"][0],
                        "hero_start.png",
                        anim={"columns": 4, "rows": 1, "frames": 4},
                        clips={"hero_idle": clip([0], 6, True)}))

    # Forest pack props that work in a crypt: bones, rocks, logs, mushrooms.
    for tail, alias in (
        ("Assets/Tiles.png", "forest_tiles.png"),
        ("Assets/Props-Rocks.png", "forest_rocks.png"),
        ("Assets/Buildings.png", "forest_buildings.png"),
        ("Trees/Green-Tree.png", "forest_tree.png"),
        ("Background/Background.png", "forest_sky.png"),
        ("HUD/Base-01.png", "hud_frame.png"),
    ):
        assets.append(asset(FOREST_PACK, tail, alias))

    # UI icons and panel.
    for tail, alias in (
        ("dfgui_icon-sword.png", "icon_sword.png"),
        ("dfgui_icon-shield.png", "icon_shield.png"),
        ("dfgui_icon-helmet.png", "icon_helmet.png"),
        ("dfgui_icon-pouch.png", "icon_pouch.png"),
        ("dfgui_icon-food.png", "icon_food.png"),
        ("dfgui_icon-redbook.png", "icon_book.png"),
        ("dfgui_icon-goblet.png", "icon_potion.png"),
        ("dfgui_icon-chest.png", "icon_chest.png"),
        ("dfgui_icon-crown.png", "icon_crown.png"),
        ("dfgui_icon-clock.png", "icon_clock.png"),
        ("dfgui_icon-axe.png", "icon_axe.png"),
        ("dfgui_icon-pickaxe.png", "icon_pickaxe.png"),
        ("dfgui_foodbar.png", "ui_healthbar.png"),
        ("dfgui_mapicons.png", "ui_mapicons.png"),
        ("dfgui_smallbutton.png", "ui_button.png"),
        ("dfgui_portraitframe.png", "ui_portrait.png"),
    ):
        assets.append(asset(UI_PACK, tail, alias))

    assets.append(asset(TINY_UI, "20240707dragon9SlicesA.png", "ui_panel.png"))
    assets.append(asset(TINY_UI, "20240707dragonHeaderA.png", "ui_header.png"))

    # Particles + audio.
    for tail, alias in (
        ("PNG (Transparent)/fire_01.png", "fx_fire.png"),
        ("PNG (Transparent)/smoke_02.png", "fx_smoke.png"),
        ("PNG (Transparent)/circle_02.png", "fx_slash.png"),
        ("PNG (Transparent)/spark_04.png", "fx_spark.png"),
        ("PNG (Transparent)/dirt_02.png", "fx_dust.png"),
    ):
        assets.append(asset("kenney_particle_pack", tail, alias))

    for tail, alias in (
        ("Sounds/click-a.ogg", "sfx_ui.ogg"),
        ("Sounds/switch-a.ogg", "sfx_hit.ogg"),
        ("Sounds/tap-a.ogg", "sfx_step.ogg"),
        ("Sounds/switch-b.ogg", "sfx_swing.ogg"),
        ("Sounds/click-b.ogg", "sfx_hurt.ogg"),
        ("Sounds/tap-b.ogg", "sfx_spit.ogg"),
    ):
        assets.append(asset(KENNEY_UI, tail, alias))
    # Three more, aliased from the same source pack: the store ships six UI
    # sounds, and a game needs a distinct cue per event, so the same six files
    # are reused under different names rather than shipping a silent event.
    for tail, alias in (
        ("Sounds/click-a.ogg", "sfx_sigil.ogg"),
        ("Sounds/switch-b.ogg", "sfx_death.ogg"),
        ("Sounds/tap-a.ogg", "sfx_win.ogg"),
    ):
        assets.append(asset(KENNEY_UI, tail, alias))

    # ---- scene
    # No fog: Scene.fog is read by Renderer3D only, so enabling it in a 2D scene
    # would be inert configuration that reads as a moody depth cue.  Depth here
    # comes from draw order and from the warm torch glows, which are particles.
    s = Scene(gravity=(0.0, 0.0, 0.0), ambient="#FF6E6888")
    s.add("Main Camera", comps=[cam2d(size=7.5, bg="#FF0B0710", follow="Warden",
                                      smooth=8.0, post="Vignette", fx=0.55)])
    s.add("Delve", tag="Director", comps=[script("DeepDelve.js", "chunk=8, waveBase=4, waveStep=2, waveGap=3.4")])
    s.add("Warden", tag="Warden", order=10, sx=1.15, sy=1.15,
          comps=[
              sprite("hero_start.png", order=10),
              script("Warden.js", "speed=4.6, damage=12, swingRange=1.3, swingCooldown=0.36, "
                                  "iframes=0.7, regenDelay=8, regenRate=2.5"),
          ])
    # The warden's carried torch: a sprite plus the warm pool it casts.  The
    # pool is a ParticleEmitter, not a Light -- Scene.Light is read by
    # Renderer3D alone, so a Light in a 2D scene changes nothing on screen.
    s.add("WardenTorch", parent=3, order=9,
          comps=[sprite("prop_torch.png", order=9),
                 particles(color="#4CFFB055", end="#00FF8020", size=1.9, end_size=1.6,
                           rate=12.0, lifetime=1.2, speed=0.3, direction=90, spread=180,
                           gravity=0.2, max_particles=32, additive=True, texture="fx_fire.png"),
                 particles(color="#FFFFD080", end="#40FF4000", size=0.34, lifetime=0.6,
                           speed=1.3, direction=90, spread=40, gravity=2.0, max_particles=14,
                           additive=True, texture="fx_fire.png")])
    # Templates: inactive objects cloned by scene.spawn().  A template is how the
    # engine pools -- one definition, N clones, no factory code per type.
    s.add("FloorChunk", tag="Template", active=False,
          comps=[sprite("tiles_atlas.png", "#FFB0A4C8", order=-100)])
    s.add("FloorOverlay", tag="Template", active=False,
          comps=[sprite("tiles_atlas.png", "#FFD8C0F0", order=-99)])
    s.add("WallRun", tag="Template", active=False, order=6,
          comps=[sprite("tiles_atlas.png", "#FFE8DCF4", order=6), collider2d("Box", 1, 1)])
    s.add("Torch", tag="Template", active=False, order=8,
          comps=[sprite("tiles_atlas.png", order=8), animator(),
                 # Two emitters on the torch: a bright additive pool (the light
                 # the 2D renderer cannot give us) and the flame licks on top.
                 particles(color="#59FFB055", end="#00FF8020", size=2.6, end_size=2.2,
                           rate=14.0, lifetime=1.4, speed=0.35, direction=90, spread=180,
                           gravity=0.2, max_particles=48, additive=True, texture="fx_fire.png"),
                 particles(color="#FFFFD080", end="#40FF4000", size=0.45, lifetime=0.7,
                           speed=1.6, direction=90, spread=40, gravity=2.0, max_particles=20,
                           additive=True, texture="fx_fire.png")])
    s.add("Chest", tag="Template", active=False, order=7, comps=[sprite("prop_chest.png", order=7)])
    s.add("Cache", tag="Template", active=False, order=5, comps=[sprite("prop_crate.png", order=5)])
    s.add("Sigil", tag="Template", active=False, order=9,
          comps=[sprite("prop_coin.png", order=9),
                 particles(color="#FF9BE8FF", end="#0033AAFF", size=0.28, rate=18.0,
                           lifetime=1.1, speed=1.0, direction=270, spread=25, gravity=0.6,
                           max_particles=40, additive=True, texture="fx_spark.png")])
    s.add("Grunt", tag="Template", active=False, order=8, sx=1.1, sy=1.1,
          comps=[sprite("grunt_idle.png", order=8), animator(),
                 collider2d("Box", 0.8, 0.9, trigger=True),
                 script("Grunt.js", "skin=grunt, hp=34, speed=1.85, damage=9, sight=7.5, reach=1.15, cooldown=1.15")])
    s.add("Ghost", tag="Template", active=False, order=8, sx=1.2, sy=1.2,
          comps=[sprite("ghost_idle.png", order=8), animator(),
                 collider2d("Box", 0.8, 0.9, trigger=True),
                 script("Ghost.js", "skin=ghost, hp=26, speed=2.55, damage=12, sight=9, reach=1.05, cooldown=1.5")])
    s.add("Crawler", tag="Template", active=False, order=8, sx=1.0, sy=1.0,
          comps=[sprite("crawler_idle.png", order=8), animator(),
                 collider2d("Box", 0.7, 0.8, trigger=True),
                 script("Crawler.js", "skin=crawler, hp=20, speed=2.05, range=6.4, keepAway=3.2, cooldown=1.9")])
    s.add("Spit", tag="Template", active=False, order=12,
          comps=[sprite("prop_flask.png", "#FFA8FF6A", order=12),
                 script("Spit.js", "damage=7, life=2.4")])
    s.add("Bolt", tag="Template", active=False, order=12,
          comps=[sprite("prop_key.png", "#FFFFE08A", order=12)])
    s.add("SplatFx", tag="Template", active=False, order=14,
          comps=[particles(color="#FFB14CFF", end="#00401020", size=0.22, lifetime=0.7,
                           speed=4.5, direction=90, spread=180, gravity=-5.0,
                           max_particles=40, additive=True, texture="fx_spark.png"),
                 script("Fx.js")])
    s.add("HitFx", tag="Template", active=False, order=14,
          comps=[particles(color="#FFFFE9A0", end="#00402000", size=0.18, lifetime=0.5,
                           speed=5.0, direction=90, spread=180, gravity=-4.0,
                           max_particles=24, additive=True, texture="fx_spark.png"),
                 script("Fx.js")])
    s.add("SlashFx", tag="Template", active=False, order=13,
          comps=[particles(color="#FFD8E8FF", end="#00406080", size=0.3, lifetime=0.35,
                           speed=3.4, direction=90, spread=180, gravity=0.0,
                           max_particles=18, additive=True, texture="fx_slash.png"),
                 script("Fx.js")])

    # ---- HUD
    hud = [
        ("HUD_Panel", -7.2, 4.25, 2.4, 1.0, "ui_panel.png", 90),
        ("HUD_Icon", -6.6, 4.3, 0.55, 0.55, "icon_sword.png", 92),
        ("HUD_Icon2", -6.6, 3.9, 0.55, 0.55, "icon_shield.png", 92),
        ("HUD_Bar", -6.0, 3.55, 2.2, 0.28, "ui_healthbar.png", 91),
        ("HUD_SigilIcon", 5.4, 4.3, 0.6, 0.6, "icon_crown.png", 92),
        ("HUD_Potion", 6.6, 4.3, 0.5, 0.5, "icon_potion.png", 92),
        ("HUD_MapIcon", 7.4, 4.3, 0.5, 0.5, "ui_mapicons.png", 92),
    ]
    for name, x, y, sx, sy, tex, order in hud:
        s.add(name, x, y, sx=sx, sy=sy, order=order,
              comps=[sprite(tex, order=order, screen=True)])

    s.add("HUD_Hp", -5.9, 4.25, order=95, comps=[text("HP 100", 0.42, "#FFE8F0FF", "Left")])
    s.add("HUD_Wave", 0.0, 4.35, order=95, comps=[text("WAVE 0", 0.5, "#FFFFE0A0", "Center")])
    s.add("HUD_Sigils", 0.0, 3.95, order=95, comps=[text("SIGILS 0/3", 0.42, "#FFA8E8FF", "Center")])
    s.add("HUD_Score", 6.4, 4.35, order=95, comps=[text("SCORE 0", 0.42, "#FFFFF0C0", "Right")])
    s.add("HUD_Hint", -8.9, -4.35, order=95,
          comps=[text("Left stick move    A swing", 0.36, "#CCD8E0FF", "Left")])
    s.add("HUD_Msg", 0.0, 2.9, order=96, comps=[text("", 0.6, "#FFFFD070", "Center")])

    # ---- manifest
    scripts = ["Warden.js", "Grunt.js", "Ghost.js", "Crawler.js", "Spit.js", "Bolt.js",
               "Fx.js", "DeepDelve.js"]
    g = manifest(
        "09_dungeon_crawler_2d", "Crypt of the Ember Sigil",
        "2D top-down dungeon crawler / action-RPG", scripts, assets,
        controls={"move": "virtual joystick (8-direction)",
                  "attack": "action button A (sword arc)",
                  "restart": "action button A on the end screen"},
        systems=[
            "level: generated at runtime from an 11-room grammar on a 72x56 tile grid",
            "rendering: 8x8 tile chunks (63 chunk sprites + overlays) instead of 4032 tile objects",
            "collision: wall runs merged into ~200 axis-aligned colliders",
            "enemy AI: melee patrol/chase/attack, a wall-phasing hunter, a ranged kiter",
            "combat: cone sword arcs, hit-stop, knockback via velocity, out-of-combat regeneration",
            "objectives: 3 sigils to claim, waves escalate while they are missing",
        ],
        notes=[
            "The tile atlas is Penusbmic's Dungeon_Tileset.png.  The store refuses to guess a "
            "grid for opaque art, so the 10x10/16px grid and the specific cells used are declared "
            "in game.json and were verified by rendering the atlas with tools/probe_png.py.",
            "Enemies come from the same pack's monster idle frames (4 per monster).  The engine's "
            "Animator plays one texture per clip, so the four frames are four assets and the "
            "scripts advance them on a timer rather than shipping a fake sheet.",
            "The hero is the High Forest knight (80x96) rather than the dungeon pack's wizard, "
            "because the forest pack is the only one in the store with a full run/attack/death set.",
            "There is no 2D lighting or fog in the renderer (Renderer3D owns both), so the level "
            "carries no Light component at all: the warm pools around torches and the warden's "
            "ember are additive ParticleEmitters.  A Light in a 2D scene would be inert -- which is "
            "why one is not declared here.",
        ],
        extra={"spriteDirections": {
            "note": "Hero sheets are single rows; direction is chosen by swapping the clip and "
                    "mirroring, never by spinning the sprite.",
            "clips": {k: list(v) for k, v in HERO_SHEETS.items()}},
        })

    scripts_src = {
        "Warden.js": WARDEN_JS, "Grunt.js": GRUNT_JS, "Ghost.js": GHOST_JS,
        "Crawler.js": CRAWLER_JS, "Spit.js": SPIT_JS, "Bolt.js": BOLT_JS,
        "Fx.js": FX_JS, "DeepDelve.js": DELVE_JS,
    }

    readme = """
# 09 — Crypt of the Ember Sigil

A top-down dungeon crawler. Move with the **left stick**, swing the sword with
**A**. Three sigils are buried in the crypt; claim all three and the run ends,
but the longer you take the more the crypt fills with its own dead.

| | |
|---|---|
| Genre | 2D top-down action-RPG |
| Level | 72 x 56 tiles, 11 rooms, generated at runtime from `DeepDelve.js` |
| Art | Penusbmic *2D Pixel Dungeon* (tiles, monsters, props) + Legacy Fantasy *High Forest* (hero) |
| Enemies | Skeletal swordsman (melee), drowned bride (walks through walls), blood monster (ranged) |

## What makes it a game and not a demo

* **A generated level.** The room grammar is data; the map, the wall colliders,
  the torch placement and the loot are produced at load. Change `ROOMS` in
  `DeepDelve.js` and the crypt changes.
* **Three threats with three counters.** The swordsman is a straight fight. The
  bride ignores walls, so corridors are not safety. The blood monster backs off
  and spits, so standing still loses.
* **Escalation with a deadline.** Waves grow while sigils are unclaimed, which
  turns "explore" into a cost rather than a pause.
* **Regeneration as a mechanic.** Out of combat for 8 seconds and the warden
  heals; the delay, not the rate, is what makes a fight committal.

## Files

```
game.json                 assets, clips and the declared tile cells
scenes/Main.scene.json    camera, warden, HUD and the inactive templates
scripts/DeepDelve.js      level generation, waves, HUD, win/lose
scripts/Warden.js         player controller and sword arc
scripts/Grunt.js          melee AI (patrol / chase / attack)
scripts/Ghost.js          wall-phasing hunter
scripts/Crawler.js        ranged kiter
scripts/Spit.js Bol.js    projectiles
scripts/Fx.js             one-shot particle bursts
```

Built by `tools/build_dungeon.py`; verified by `tools/validate_games.py`,
`tools/run_games.js` and rendered offline with `tools/preview_scene.py`.
"""

    write_game("09_dungeon_crawler_2d", g, s, scripts_src, readme)
    print(f"  wrote games/09_dungeon_crawler_2d ({len(s.objs)} scene objects, {len(assets)} assets)")


if __name__ == "__main__":
    build()
