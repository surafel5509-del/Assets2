#!/usr/bin/env python3
"""
build_heist.py -- "Blackout Heist": the second big 2D game.

A top-down stealth heist.  You are a thief inside a darkened depot; the guards
carry the only eyes, the lamps carry the only light, and the whole level is one
held breath away from the alarm going up.  The objective chain *is* the level
design -- the keycard is in the store room, the three crates are in the vault,
and the van is back at the loading dock -- so the player crosses the depot three
times and learns its routes, its lamps and its patrols before the last run.

The level is generated here and emitted as scene objects: a dark base plate, the
floor of every room, the brick band around every wall, and the dressing.  The
same grid is baked into the scripts that walk it, so collision and line of sight
are exact rather than approximate, and the builder refuses to emit a level whose
rooms are not connected or whose props block a spawn, a patrol or a doorway.

Two things are deliberately script-owned:

  * movement.  Actors test the baked grid and move themselves.  A Rigidbody2D
    would have to be re-tuned per actor and cannot be verified offline; a grid
    lookup is exact and is what the line-of-sight test needs anyway.
  * light.  Scene Light components are read by Renderer3D alone, so every 2D
    light in this game is an additive emitter that also draws the pool it lights.
    The pool's size *is* the radius the guards can see you in -- one number, one
    meaning, and it is visible on screen.

Art: the 2D Pixel Dungeon pack (floors, walls, dressing), the RPG Top-Down
Characters pack (the thief and the guards: 128x256 sheets, four directions, idle
and walk rows), the Top-Down Car pack (the van and the parked cars), the Super
Pixel Objects sample (the keycard and the crates), the Kenney Pixel UI pack and
Kenney particle pack (HUD, light, dust) and the Kenney UI pack's sound set.

Run:  python3 tools/build_heist.py
"""

from __future__ import annotations

import json
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "biggames"))
from scene_kit import (Scene, animator, asset, cam2d, clip, collider2d, manifest,  # noqa: E402
                       particles, script, sprite, text, to_rows, write_game)

GID = "10_blackout_heist_2d"
MANIFEST = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "app", "src", "main", "assets", "store", "store_manifest.json")
_SIZES = None


def store_size(pack, path):
    """Pixel size of a store file, straight out of store_manifest.json.

    The vehicles are scaled to a length in world units rather than to a magic
    number, so a different car -- or the same car re-measured -- keeps its
    proportions.  A file the manifest does not know is a bug in the asset map.
    """
    global _SIZES
    if _SIZES is None:
        m = json.load(open(MANIFEST, encoding="utf-8"))
        _SIZES = {(p["id"], f["path"]): (f.get("w") or 0, f.get("h") or 0)
                  for p in m["packs"] for f in p["files"]}
    size = _SIZES.get((pack, path))
    assert size and size[0] and size[1], f"the manifest has no size for {pack}/{path}"
    return size


DUNGEON = "2d_pixel_dungeon_asset_pack_v2_0"
TOPDOWN = "rpg_top_down_character_asset_pack_free"
CARS = "all_cars"
SUPER = "super_pixel_objects_sample"
PIXUI = "kenney_pixel_ui_pack"
UIPACK = "kenney_ui_pack"
PARTS = "kenney_particle_pack"

# --------------------------------------------------------------------------- level
#
# One cell is one world unit.  Row 0 is the north edge of the map and the world
# origin is the centre, so cell (c, r) is world (c - MAP_W/2 + 0.5,
# MAP_H/2 - r - 0.5).  Rooms are rectangles carved out of a solid block and the
# walls are whatever is left between them -- the same way a real floorplan is
# drawn, and the reason a corridor is never an accident of two rooms being close
# together.

MAP_W, MAP_H = 60, 44

# name, (col, row, w, h), floor material
ROOMS = [
    ("Dock", (3, 30, 17, 11), "stone"),
    ("WestLink", (8, 24, 3, 7), "stone"),
    ("Stores", (3, 12, 17, 12), "masonry"),
    ("WestDoor", (20, 22, 3, 3), "stone"),
    ("Hall", (23, 21, 33, 4), "stone"),
    ("NorthDoor", (29, 19, 3, 3), "stone"),
    ("Server", (25, 10, 13, 10), "planks"),
    ("VaultLink", (38, 13, 4, 3), "stone"),
    ("Vault", (42, 10, 15, 10), "planks"),
    ("EastDoor", (48, 25, 3, 3), "stone"),
    ("Break", (42, 27, 15, 12), "masonry"),
    ("Crawl", (10, 40, 33, 3), "stone"),
    ("CrawlEast", (40, 38, 3, 3), "stone"),
]

# Tile ids, all read off the pack's atlas with tools/probe_png.py: the atlas is
# 160x160 -- ten columns of ten 16px cells -- and carries no metadata, so the
# grid is declared in game.json and the cells are named here.
TILE_FLOOR = 6        # plain dark stone
TILE_MASONRY = 22     # stone with mortar marks
TILE_PLANKS = 56      # wooden planks (the vault and the server floor)
TILE_WALL = 1         # brick
TILE_WALL_ALT = 3     # brick, second face
TILE_CRATEWALL = 36   # stacked crates, used as a wall face
TILE_SHELF = 66       # shelf unit
TILE_STACK = 49       # barrel/box stack
TILE_BONES = 64       # bones
TILE_COIN = 86        # coin pile
TILE_TABLE = 94       # small table
TILE_STOOL = 96       # stool
TILE_PILLAR = 47      # timber pillar

MATERIAL_TILE = {"stone": ("floor_stone", TILE_FLOOR, "#FF6E7484"),
                 "masonry": ("floor_masonry", TILE_MASONRY, "#FF7A7266"),
                 "planks": ("floor_planks", TILE_PLANKS, "#FF8C7A5E")}
# The floor one cell in from a wall sits in that wall's shadow.  Baking the
# shade into the tint (instead of adding an overlay object per cell) costs
# nothing at run time and is what stops a tiled room reading as a flat carpet.
MATERIAL_EDGE = {"stone": "#FF4B5060",
                 "masonry": "#FF555044",
                 "planks": "#FF63543F"}
WALL_TINT = {"stone": "#FFC4BEB0", "masonry": "#FFCCC0A8", "planks": "#FFD0B490"}

START_CELL = (11, 38)          # the thief comes over the dock fence
VAN_CELL = (15, 33)
QUEST_KEY = (16, 13)           # the keycard, on the desk in the store room
QUEST_TERMINAL = (26, 17)      # the camera terminal, in the server room
QUEST_CRATES = [(45, 13), (48, 12), (52, 14)]
SAFE_CELL = (54, 12)

GUARDS = [
    # name, patrol cells, tint
    ("Rook", [(5, 34), (17, 34), (17, 39), (5, 39)], "#FFC6C8EA"),
    ("Vale", [(25, 22), (53, 22), (53, 23), (25, 23)], "#FFC6C8EA"),
    ("Sable", [(27, 14), (35, 14), (35, 18), (27, 18)], "#FFC6C8EA"),
]
EYES = [
    # name, cell, sweep centre (deg), sweep, speed, sight, cone, rate
    ("CamHall", (34, 24), 180.0, 46.0, 24.0, 10.0, 56.0, 0.62),
    ("CamVault", (41, 14), 180.0, 40.0, 20.0, 9.0, 52.0, 0.62),
    ("Searchlight", (10, 31), 180.0, 62.0, 17.0, 12.0, 66.0, 0.5),
]


def carve_level():
    """Solid everywhere, then paint the rooms; returns (grid, materials, rooms)."""
    grid = [["#"] * MAP_W for _ in range(MAP_H)]
    mats = [[""] * MAP_W for _ in range(MAP_H)]
    rooms = {}
    for name, (cx, cy, w, h), mat in ROOMS:
        rooms[name] = (cx, cy, w, h)
        assert cx >= 1 and cy >= 1 and cx + w <= MAP_W - 1 and cy + h <= MAP_H - 1, name
        for y in range(cy, cy + h):
            for x in range(cx, cx + w):
                grid[y][x] = "."
                mats[y][x] = mat
    return grid, mats, rooms


def cell_to_world(cx, cy):
    """Centre of a cell, in world units."""
    return (cx - MAP_W * 0.5 + 0.5, MAP_H * 0.5 - cy - 0.5)


def block(grid, cx, cy, w=1, h=1):
    """Mark a footprint as blocked by a prop (never overwrites a wall)."""
    marked = 0
    for y in range(cy, cy + h):
        for x in range(cx, cx + w):
            if 0 <= x < MAP_W and 0 <= y < MAP_H and grid[y][x] == ".":
                grid[y][x] = "x"
                marked += 1
    return marked


def flood(grid, start):
    seen = {start}
    stack = [start]
    while stack:
        x, y = stack.pop()
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < MAP_W and 0 <= ny < MAP_H and (nx, ny) not in seen \
                    and grid[ny][nx] == ".":
                seen.add((nx, ny))
                stack.append((nx, ny))
    return seen


def on_edge(mats, x, y):
    """True when the cell at x,y has a neighbour that is not floor."""
    for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
        if nx < 0 or ny < 0 or nx >= MAP_W or ny >= MAP_H or not mats[ny][nx]:
            return True
    return False


def floor_pieces(grid, mats):
    """Horizontal runs of floor, split so no tile is stretched more than 4x.

    The floor of a big room is not one stretched sprite: a 16px tile smeared
    across seventeen cells loses the pattern that makes it read as a floor.  A
    run is therefore capped at four cells, which keeps the texture intact and
    still keeps the object count in the hundreds rather than the thousands.

    Runs also break wherever the shade changes, so the ring of cells that touch
    a wall is its own darker run and the room reads as lit in the middle and
    shadowed at the edges.
    """
    out = []
    for y in range(MAP_H):
        x = 0
        while x < MAP_W:
            if grid[y][x] in (".", "x") and mats[y][x]:
                mat = mats[y][x]
                edge = on_edge(mats, x, y)
                x0 = x
                while (x < MAP_W and mats[y][x] == mat
                       and on_edge(mats, x, y) == edge):
                    x += 1
                run = x - x0
                done = 0
                while done < run:
                    piece = min(4, run - done)
                    out.append((x0 + done, y, piece, mat, edge))
                    done += piece
            else:
                x += 1
    return out


def wall_faces(grid):
    """Brick, only where a wall meets open floor, as merged runs of at most 3.

    The inside of the block is never drawn: the whole map sits on one dark base
    plate, and what a player sees is the band of brick around the rooms.  That is
    the difference between ~180 wall objects and ~550.
    """
    open_cells = {(x, y) for y in range(MAP_H) for x in range(MAP_W)
                  if grid[y][x] in (".", "x")}
    faces = set()
    for (x, y) in open_cells:
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < MAP_W and 0 <= ny < MAP_H and grid[ny][nx] == "#":
                faces.add((nx, ny))
    out = []
    for y in range(MAP_H):
        x = 0
        while x < MAP_W:
            if (x, y) in faces:
                x0 = x
                while x < MAP_W and (x, y) in faces:
                    x += 1
                run = x - x0
                done = 0
                while done < run:
                    piece = min(3, run - done)
                    out.append((x0 + done, y, piece))
                    done += piece
            else:
                x += 1
    return out


# --------------------------------------------------------------------------- scripts
#
# The map is baked into three scripts -- the thief walks it, the guards walk it
# and test line of sight across it, and the cameras test line of sight across it.
# They are all written from the same grid by this builder, so they cannot
# disagree; '.' is walkable, 'x' is blocked by a prop and '#' is wall.

THIEF_JS = r"""
// Blackout Heist -- Thief.js
//
// The player.  Eight-way movement with the grid tested on each axis separately,
// so a body slides along a wall instead of sticking to it.  Three gaits:
//
//   creep   (Sneak toggled)  1.5 u/s, a 1.2-unit noise, 55% as easy to spot
//   walk    (stick pushed a little)  ~3 u/s
//   run     (stick pushed hard)  ~4.6 u/s, a 5.4-unit noise
//
// Everything else the thief does -- take the keycard, crack a crate, cut the
// camera feed, climb into the van -- is one held action pointed at whatever is
// nearest and close enough, which is the only interface a touch screen needs.
//
// Params: creepSpeed=1.5, walkSpeed=3.0, runSpeed=4.6, radius=0.34,
//         interactRange=1.35, creepNoise=1.2, walkNoise=2.4, runNoise=5.4

var MAP = __MAP__;
var MAP_W = __MAPW__;
var MAP_H = __MAPH__;
var facing = "down";
var sneaking = false;
var noise = 0.0;
var busy = 0.0;
var target = null;
var promptText = "";
var caught = false;
var escaped = false;
var stepPhase = 0.0;

function cellX(x) { return Math.floor(x + MAP_W * 0.5); }
function cellY(y) { return Math.floor(MAP_H * 0.5 - y); }

function cellSolid(cx, cy) {
    if (cx < 0 || cy < 0 || cx >= MAP_W || cy >= MAP_H) return true;
    return MAP[cy].charAt(cx) !== ".";
}

/** Does the thief's footprint touch a blocked cell at (x, y)? */
function blocked(x, y) {
    var r = radius;
    return cellSolid(cellX(x - r), cellY(y - r)) || cellSolid(cellX(x + r), cellY(y - r)) ||
           cellSolid(cellX(x - r), cellY(y + r)) || cellSolid(cellX(x + r), cellY(y + r));
}

function start() {
    self.Order = 10;
}

function update(dt) {
    var h = scene.find("Heist");
    var over = h ? h.send("isOver") : false;
    if (caught || escaped || over) {
        noise = 0.0;
        self.play("thief_idle_" + facing);
        return;
    }

    // Sneak is a toggle: a hold on a virtual stick is a fight with the thumb,
    // and a toggle is also what the headless harness can press.
    if (input.actionPressed("Sneak")) {
        sneaking = !sneaking;
        if (h) h.send("onSneak", sneaking ? 1.0 : 0.0);
    }

    var ix = input.axisX, iy = input.axisY;
    var mag = Math.sqrt(ix * ix + iy * iy);
    if (mag > 1.0) { ix /= mag; iy /= mag; mag = 1.0; }

    var speed = sneaking ? creepSpeed : walkSpeed + (runSpeed - walkSpeed) * mag;
    noise = 0.0;
    if (mag > 0.08) {
        facing = Math.abs(ix) > Math.abs(iy) ? (ix > 0.0 ? "right" : "left")
                                            : (iy > 0.0 ? "up" : "down");
        var nx = self.x + ix * speed * dt;
        if (!blocked(nx, self.y)) self.setPosition(nx, self.y, 0.0);
        var ny = self.y + iy * speed * dt;
        if (!blocked(self.x, ny)) self.setPosition(self.x, ny, 0.0);

        // The noise radius is what the guards hear through the walls, and it is
        // drawn on screen as the dust the boots kick up.
        noise = sneaking ? creepNoise : (mag > 0.7 ? runNoise : walkNoise);
        stepPhase += dt * (sneaking ? 4.0 : 9.0 + 4.0 * mag);
        if (stepPhase > 1.0) {
            stepPhase = 0.0;
            audio.play("sfx_step.ogg", sneaking ? 0.1 : 0.28);
        }
        self.play("thief_walk_" + facing);
        self.setAnimSpeed(sneaking ? 0.75 : 1.0 + 0.35 * mag);
    } else {
        self.play("thief_idle_" + facing);
        self.setAnimSpeed(1.0);
    }

    // ---- interactables: nearest in range, then held
    var best = null, bestD = interactRange;
    var list = scene.findAll("Interact");
    for (var i = 0; i < list.length; i++) {
        var o = list[i];
        var dx = o.x - self.x, dy = o.y - self.y;
        var d = Math.sqrt(dx * dx + dy * dy);
        if (d < bestD) { bestD = d; best = o; }
    }
    if (best !== target) {
        if (target) target.send("interactCancel");
        target = best;
        busy = 0.0;
    }
    promptText = best ? (best.send("prompt") || "") : "";
    if (best && input.action("Interact")) {
        var p = best.send("interact", dt);
        busy = p === null || p === undefined ? 0.0 : p;
    } else {
        busy = 0.0;
        if (best) best.send("interactCancel");
    }
    var hud = scene.find("Hud");
    if (hud) { hud.send("prompt", promptText); hud.send("hold", busy); }
}

function say(msg) {
    var h = scene.find("Heist");
    if (h) h.send("say", msg);
}

function getFacing() { return facing; }
function isSneaking() { return sneaking; }
function getNoise() { return noise; }
function isCaught() { return caught; }
function isEscaped() { return escaped; }

/** How well lit the thief is: the pools on the floor are the light. */
function isLit() {
    var lights = scene.findAll("Light");
    for (var i = 0; i < lights.length; i++) {
        var o = lights[i];
        var r = Math.max(o.scaleX, o.scaleY) * 0.5;
        var dx = o.x - self.x, dy = o.y - self.y;
        if (dx * dx + dy * dy < r * r) return true;
    }
    return false;
}

/** Called by a guard that has closed the distance. */
function bust() {
    if (caught || escaped) return;
    caught = true;
    noise = 0.0;
    audio.play("sfx_caught.ogg", 0.9);
    var fx = scene.find("Fx");
    if (fx) fx.send("alarm", pack(self.x, self.y));
    var h = scene.find("Heist");
    if (h) h.send("onCaught");
}

/** Called by the van once the loot is on board. */
function escape() {
    if (caught || escaped) return;
    escaped = true;
    noise = 0.0;
    self.setVisible(false);
    var h = scene.find("Heist");
    if (h) h.send("onEscape");
}

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}
"""

GUARD_JS = r"""
// Blackout Heist -- Guard.js
//
// One guard, four states: patrol, suspect, chase, search.
//
// The vision test is the character.  A guard sees you only when all three are
// true -- inside its range, inside its cone, and with an unbroken line of sight
// across the level grid -- which is what makes a corner worth trusting.  Range
// grows by 45% if you are standing in a lamp pool, shrinks if you are creeping,
// and a running thief is heard through the walls at `noise + hearing`.
//
// Suspicion is per guard and visible: a marker above its head goes pale yellow,
// amber, red as the meter fills, so the player is never told "spotted" without
// warning.  The shared alarm lives in the director, not here.
//
// Params: speed=2.4, chaseSpeed=4.2, sight=7.5, cone=95, nearCone=160,
//         nearSight=2.6, litBonus=1.45, detectRate=0.8, decay=0.45,
//         hearing=3.2, hearRate=0.75, catchRange=0.62, route=x:y|x:y|...

var MAP = __MAP__;
var MAP_W = __MAPW__;
var MAP_H = __MAPH__;
var wp = [];                                   // waypoint pairs, parsed in start()
var route = typeof route === "undefined" ? "" : route;
var wpi = 0;
var facing = 0.0;
var state = "patrol";
var detect = 0.0;
var lastX = 0.0, lastY = 0.0;
var searchT = 0.0;
var stuckT = 0.0;
var mark = null;
var alarm = 0.0;
var seen = false;

function cellX(x) { return Math.floor(x + MAP_W * 0.5); }
function cellY(y) { return Math.floor(MAP_H * 0.5 - y); }
function cellSolid(cx, cy) {
    if (cx < 0 || cy < 0 || cx >= MAP_W || cy >= MAP_H) return true;
    return MAP[cy].charAt(cx) !== ".";
}
function blocked(x, y) {
    var r = 0.34;
    return cellSolid(cellX(x - r), cellY(y - r)) || cellSolid(cellX(x + r), cellY(y - r)) ||
           cellSolid(cellX(x - r), cellY(y + r)) || cellSolid(cellX(x + r), cellY(y + r));
}

function start() {
    var parts = ("" + route).split("|");
    for (var i = 0; i < parts.length; i++) {
        var xy = parts[i].split(":");
        wp.push([parseFloat(xy[0]), parseFloat(xy[1])]);
    }
    mark = scene.find(self.name + "Mark");
    self.play("guard_idle_up");
}

function update(dt) {
    var t = scene.find("Thief");
    var h = scene.find("Heist");
    if (!t || !h) return;
    var over = h.send("isOver");
    alarm = h.send("getAlarm");

    seen = !over && t.getVisible() && canSee(t);
    if (seen) {
        detect = Math.min(1.0, detect + detectRate * dt * visibility(t));
        lastX = t.x; lastY = t.y;
        if (detect >= 1.0) {
            if (state !== "chase") {
                audio.play("sfx_alarm.ogg", 0.75);
                var fx = scene.find("Fx");
                if (fx) fx.send("spot", pack(self.x, self.y));
                h.send("spotted", self.name);
            }
            state = "chase";
        } else if (state !== "chase") {
            state = "suspect";
        }
    } else {
        detect = Math.max(0.0, detect - decay * dt);
        // Ears: a running thief is audible through a wall, a creeping one is not.
        var heard = over ? 0.0 : (t.send("getNoise") || 0.0);
        if (heard > 0.0) {
            var dxh = t.x - self.x, dyh = t.y - self.y;
            var dh = Math.sqrt(dxh * dxh + dyh * dyh);
            if (dh < heard + hearing) {
                detect = Math.min(1.0, detect + hearRate * dt * (1.0 - dh / (heard + hearing)));
                lastX = t.x; lastY = t.y;
                if (state === "patrol") state = "suspect";
            }
        }
        if (state === "chase" && detect < 0.25) { state = "search"; searchT = 5.0; }
    }
    paint();

    var tx, ty, sp;
    if (state === "chase") {
        tx = t.x; ty = t.y; sp = chaseSpeed;
        if (over) { tx = self.x; ty = self.y; sp = 0.0; }
        else if (seen && distTo(t.x, t.y) < catchRange) { t.send("bust"); return; }
    } else if (state === "search") {
        searchT -= dt;
        tx = lastX; ty = lastY; sp = speed * 1.1;
        if (searchT <= 0.0) state = "patrol";
    } else if (state === "suspect") {
        tx = lastX; ty = lastY; sp = speed * 1.2;
        if (Math.abs(self.x - tx) < 0.4 && Math.abs(self.y - ty) < 0.4) state = "patrol";
    } else {
        var p = wp[wpi];
        tx = p[0]; ty = p[1]; sp = speed;
        if (alarm > 0.5) sp = speed * 1.35;     // a guard at the alarm hurries
        if (Math.abs(self.x - tx) < 0.3 && Math.abs(self.y - ty) < 0.3) {
            wpi = (wpi + 1) % wp.length;
        }
    }

    var dx = tx - self.x, dy = ty - self.y;
    var d = Math.sqrt(dx * dx + dy * dy);
    if (d > 0.05 && sp > 0.0) {
        var step = Math.min(sp * dt, d);
        var nx = self.x + dx / d * step;
        var ny = self.y + dy / d * step;
        var moved = false;
        if (!blocked(nx, self.y)) { self.setPosition(nx, self.y, 0.0); moved = true; }
        if (!blocked(self.x, ny)) { self.setPosition(self.x, ny, 0.0); moved = true; }
        if (!moved) {
            // Slide along whatever is in the way rather than stopping dead: a
            // guard that sticks to a corner reads as broken, not as careful.
            stuckT += dt;
            var sx = -dy / d * step, sy = dx / d * step;
            if (stuckT % 0.6 < 0.3) {
                if (!blocked(self.x + sx, self.y + sy)) self.setPosition(self.x + sx, self.y + sy, 0.0);
            } else if (!blocked(self.x - sx, self.y - sy)) {
                self.setPosition(self.x - sx, self.y - sy, 0.0);
            }
            if (stuckT > 3.0) { stuckT = 0.0; wpi = (wpi + 1) % wp.length; }
        } else {
            stuckT = 0.0;
        }
        facing = Math.atan2(dx, dy) * 180.0 / Math.PI;
        self.rotZ = 0.0;
        self.play("guard_walk_" + dirName(dx, dy));
    } else {
        self.play("guard_idle_" + dirName(Math.sin(facing * Math.PI / 180.0),
                                          Math.cos(facing * Math.PI / 180.0)));
    }
}

function dirName(dx, dy) {
    if (Math.abs(dx) > Math.abs(dy)) return dx > 0.0 ? "right" : "left";
    return dy > 0.0 ? "up" : "down";
}

function distTo(x, y) {
    var dx = x - self.x, dy = y - self.y;
    return Math.sqrt(dx * dx + dy * dy);
}

/** Range, cone (wider up close), then an exact line-of-sight walk. */
function canSee(t) {
    var dx = t.x - self.x, dy = t.y - self.y;
    var d = Math.sqrt(dx * dx + dy * dy);
    var reach = sight * (t.send("isLit") ? litBonus : 1.0);
    if (d > reach) return false;
    if (determine(t, dx, dy, d) === false) return false;
    return clearPath(dx, dy, d);
}

function determine(t, dx, dy, d) {
    var ang = Math.atan2(dx, dy) * 180.0 / Math.PI;
    var delta = ((ang - facing + 540.0) % 360.0) - 180.0;
    var half = (d < nearSight ? nearCone : cone) * 0.5;
    return Math.abs(delta) <= half;
}

/** How fast suspicion fills: closer, lit and running all matter. */
function visibility(t) {
    var m = t.send("isSneaking") ? 0.55 : 1.0;
    if (t.send("isLit")) m *= 1.3;
    return m;
}

function clearPath(dx, dy, d) {
    var steps = Math.max(2, Math.round(d * 2));
    for (var i = 1; i < steps; i++) {
        var f = i / steps;
        if (cellSolid(cellX(self.x + dx * f), cellY(self.y + dy * f))) return false;
    }
    return true;
}

function paint() {
    if (!mark) return;
    if (detect <= 0.01 && state !== "chase" && state !== "search") {
        mark.setVisible(false);
        return;
    }
    mark.setVisible(true);
    mark.setColor(detect >= 1.0 ? "#FFFF4A38" : (detect > 0.45 ? "#FFFFC24A" : "#FFEDE08A"));
    var s = 0.3 + detect * 0.5;
    mark.scaleX = s; mark.scaleY = s;
    mark.setPosition(self.x, self.y + 0.95, 0.0);
}

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}

function getState() { return state; }
function getDetect() { return detect; }
"""

CAM_JS = r"""
// Blackout Heist -- Cam.js
//
// A security camera, and -- with a wider beam and a slower sweep -- the
// searchlight over the loading dock.  Same script, different numbers: both
// point, both fill the alarm while they hold you, and both stop existing when
// the terminal in the server room is used.
//
// Params: centre=180, sweep=46, speed=24, sight=10, cone=56, rate=0.62

var MAP = __MAP__;
var MAP_W = __MAPW__;
var MAP_H = __MAPH__;
var t = 0.0;
var down = false;
var beam = null;
var facing = 0.0;
var hot = false;

function start() {
    beam = scene.find(self.name + "Beam");
    facing = centre;
}

function update(dt) {
    var h = scene.find("Heist");
    if (!h) return;
    if (down) return;
    if (h.send("areCamerasOff")) {
        if (beam) beam.setVisible(false);
        return;
    }
    t += dt;
    facing = centre + Math.sin(t * speed * Math.PI / 180.0) * sweep;
    if (beam) {
        beam.setVisible(true);
        beam.setPosition(self.x, self.y - 0.25, 0.0);
        beam.rotZ = -facing;
    }

    var p = scene.find("Thief");
    if (!p || !p.getVisible() || h.send("isOver")) { setCold(false); return; }
    var dx = p.x - self.x, dy = p.y - self.y;
    var d = Math.sqrt(dx * dx + dy * dy);
    if (d > sight) { setCold(false); return; }
    var ang = Math.atan2(dx, dy) * 180.0 / Math.PI;
    var delta = ((ang - facing + 540.0) % 360.0) - 180.0;
    if (Math.abs(delta) > cone * 0.5) { setCold(false); return; }
    var steps = Math.max(2, Math.round(d * 2));
    for (var i = 1; i < steps; i++) {
        var f = i / steps;
        if (cellSolid(Math.floor(self.x + dx * f + MAP_W * 0.5),
                      Math.floor(MAP_H * 0.5 - (self.y + dy * f)))) { setCold(false); return; }
    }
    // A camera is not a guard: it never chases, it reports.  The further off
    // axis the thief is, the less of the beam they actually cross.
    setCold(true);
    var gain = rate * dt * (0.35 + 0.65 * (1.0 - Math.abs(delta) / (cone * 0.5))) * (1.0 - 0.4 * d / sight);
    h.send("cameraSees", gain);
}

function setCold(isHot) {
    hot = isHot;
    if (beam) beam.setColor(isHot ? "#66FF5A48" : "#22C8E0FF");
}

function cellSolid(cx, cy) {
    if (cx < 0 || cy < 0 || cx >= MAP_W || cy >= MAP_H) return true;
    return MAP[cy].charAt(cx) !== ".";
}

function disable() { down = true; if (beam) beam.setVisible(false); }
function getFacing() { return facing; }
function isDown() { return down; }
function isHot() { return hot; }
"""

LOOT_JS = r"""
// Blackout Heist -- Loot.js
//
// The keycard, the three vault crates and the safe are the same object with
// different numbers, because they are all "hold the button here long enough".
// The vault is locked until the keycard is in hand, which is what turns three
// rooms into one route.
//
// Params: kind=crate, hold=1.5, sound=sfx_pick.ogg, locked=false, openTex=...
//
// Every param is read through a guard.  The engine's script scope throws on an
// undeclared global, so a scene that leaves `openTex` to its default would kill
// this script the moment the loot was picked up -- which is exactly what the
// keycard did before this was fixed.

var _kind = typeof kind === "undefined" ? "crate" : kind;
var _hold = typeof hold === "undefined" ? 1.5 : hold;
var _sound = typeof sound === "undefined" ? "" : sound;
var _locked = typeof locked === "undefined" ? false : locked;
var _openTex = typeof openTex === "undefined" ? "" : openTex;

var progress = 0.0;
var taken = false;
var glow = null;

function start() {
    glow = scene.find(self.name + "Glow");
}

function unlocked() {
    if (!_locked) return true;
    var h = scene.find("Heist");
    return h ? h.send("hasKey") : false;
}

function interact(dt) {
    if (taken) return 0.0;
    if (!unlocked()) return 0.0;
    progress += dt;
    if (progress >= _hold) {
        taken = true;
        if (_sound) audio.play(_sound, 0.75);
        var fx = scene.find("Fx");
        if (fx) fx.send("crack", pack(self.x, self.y));
        var h = scene.find("Heist");
        if (h) h.send("onLoot", _kind);
        if (glow) glow.setVisible(false);
        if (_openTex) self.setTexture(_openTex);
        else self.setVisible(false);
        return 0.0;
    }
    return progress / _hold;
}

function interactCancel() { progress = 0.0; }

function prompt() {
    if (taken) return "";
    if (!unlocked()) return "Locked -- find the keycard";
    if (_kind === "key") return "Hold Interact: take the keycard";
    if (kind === "safe") return "Hold Interact: crack the safe";
    return "Hold Interact: crack the crate";
}

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}

function isTaken() { return taken; }
function getKind() { return kind; }
"""

TERMINAL_JS = r"""
// Blackout Heist -- Terminal.js
//
// The camera terminal.  One held action and the security grid goes dark for the
// rest of the run, which is the only trade the player can make between risk and
// time: the room it sits in is the most guarded one in the depot.
//
// Params: hold=1.2

var progress = 0.0;
var used = false;
var glow = null;

function start() {
    glow = scene.find("TerminalGlow");
}

function interact(dt) {
    if (used) return 0.0;
    progress += dt;
    if (progress >= hold) {
        used = true;
        audio.play("sfx_cam.ogg", 0.8);
        self.setColor("#FF6A7A8E");
        if (glow) glow.setVisible(false);
        var h = scene.find("Heist");
        if (h) h.send("camerasOff");
        var fx = scene.find("Fx");
        if (fx) fx.send("crack", pack(self.x, self.y));
        return 0.0;
    }
    return progress / hold;
}

function interactCancel() { progress = 0.0; }
function prompt() { return used ? "" : "Hold Interact: cut the camera feed"; }
function isUsed() { return used; }

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}
"""

EXIT_JS = r"""
// Blackout Heist -- Exit.js
//
// The back of the van.  It only opens once the loot is on board, and it says so
// -- a heist that ends because you walked over a marker is not a heist, it is a
// door.
//
// Params: need=3, hold=1.2

var progress = 0.0;

function start() {
    self.setVisible(false);
}

function update(dt) {
    var h = scene.find("Heist");
    var have = h ? h.send("getLoot") : 0;
    self.setVisible(have >= need);
}

function interact(dt) {
    var h = scene.find("Heist");
    var have = h ? h.send("getLoot") : 0;
    if (have < need) return 0.0;
    progress += dt;
    if (progress >= hold) {
        var fx = scene.find("Fx");
        if (fx) fx.send("escape", pack(self.x, self.y));
        var t = scene.find("Thief");
        if (t) t.send("escape");
        return 0.0;
    }
    return progress / hold;
}

function interactCancel() { progress = 0.0; }

function prompt() {
    var h = scene.find("Heist");
    var have = h ? h.send("getLoot") : 0;
    if (have < need) return "The van needs " + need + " crates (" + have + ")";
    return "Hold Interact: load up and drive";
}

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}
"""

HEIST_JS = r"""
// Blackout Heist -- Heist.js
//
// The director.  It owns the objective chain, the alarm, the clock and the two
// ways the run ends, so the mission can be read from exactly one place -- and
// the HUD reads it from here rather than keeping its own copy of the truth.
//
// Params: lootNeeded=3, alarmDecay=0.14, alarmLimit=1.0, parTime=210

var loot = 0;
var keyTaken = false;
var gridDown = false;
var alarm = 0.0;
var cameras = 0.0;
var caught = false;
var escaped = false;
var toast = "";
var toastT = 0.0;
var time = 0.0;
var spottedBy = "";
var par = 210.0;

function start() {
    par = parTime;
    toast = "Loading dock.  Keycard, crates, van.  Par is " + Math.round(parTime / 60) + " minutes.";
    toastT = 6.0;
}

function update(dt) {
    time += dt;
    if (toastT > 0.0) {
        toastT -= dt;
        if (toastT <= 0.0) toast = "";
    }
    if (caught || escaped) {
        if (input.actionPressed("Interact")) scene.reload();
        return;
    }
    // The alarm cools only while nobody is chasing; a camera keeps it warm.
    var hot = false;
    var guards = scene.findAll("Guard");
    for (var i = 0; i < guards.length; i++) {
        var s = guards[i].send("getState");
        if (s === "chase") hot = true;
    }
    if (!hot) alarm = Math.max(0.0, alarm - alarmDecay * dt);
    cameras = Math.max(0.0, cameras - alarmDecay * dt);
    if (Math.max(alarm, cameras) >= alarmLimit) {
        caught = true;
        audio.play("sfx_caught.ogg", 1.0);
    }
}

function say(msg) { toast = msg; toastT = 4.5; }

function onSneak(on) {
    if (on > 0.5) say("Creeping. Quiet, but slow.");
}

function onLoot(kind) {
    if (kind === "key") {
        keyTaken = true;
        say("Keycard. The vault is open to you now.");
        return;
    }
    if (kind === "safe") {
        say("Safe cracked. Nothing in it but dust.");
        return;
    }
    loot++;
    if (loot >= lootNeeded) say("All " + lootNeeded + " crates. Back to the dock.");
    else say("Crate " + loot + " of " + lootNeeded + ".");
}

function camerasOff() {
    gridDown = true;
    cameras = 0.0;
    say("Camera grid down. Nothing is watching but the guards.");
}

function cameraSees(amount) {
    if (caught || escaped || gridDown) return;
    cameras = Math.min(alarmLimit, cameras + amount);
}

function spotted(name) {
    spottedBy = name;
    alarm = Math.min(alarmLimit, alarm + 0.25);
    say("Spotted by " + name + ". Move.");
}

function onCaught() {
    if (caught) return;
    caught = true;
    alarm = alarmLimit;
}

function onEscape() {
    escaped = true;
    audio.play("sfx_win.ogg", 0.9);
}

function isOver() { return caught || escaped; }
function hasWon() { return escaped; }
/** The run is scored against par: inside it is a clean getaway. */
function underPar() { return escaped && time <= parTime; }
function getPar() { return parTime; }
function hasLost() { return caught; }
function getLoot() { return loot; }
function getLootNeeded() { return lootNeeded; }
function getAlarm() { return Math.max(alarm, gridDown ? 0.0 : cameras); }
function getCameras() { return cameras; }
function hasKey() { return keyTaken; }
function areCamerasOff() { return gridDown; }
function wasSpotted() { return spottedBy !== ""; }
function getSpottedBy() { return spottedBy; }
function getToast() { return toast; }
function getTime() { return time; }
"""

HUD_JS = r"""
// Blackout Heist -- Hud.js
//
// Screen-space HUD: what to do next, how many crates are in hand, the alarm, the
// meter for whatever is being held, and one line of mission text.  It reads the
// director and nothing else, so the HUD can never disagree with the game.
//
// Note for anyone moving it: the runtime draws screen-space objects through a
// fixed +/-5-unit view (SceneRenderer.drawScreenUI), not through the game camera,
// so this layout is inside +/-6.6 x +/-4.8 to stay on screen from 4:3 to 20:9.
//
// Params: pips=3

var promptText = "";
var holdAmount = 0.0;

function start() {
    for (var i = 0; i < pips; i++) {
        var p = scene.find("Pip" + i);
        if (p) p.setColor("#FF2C2E3A");
    }
}

function update(dt) {
    var h = scene.find("Heist");
    if (!h) return;
    var loot = h.send("getLoot");
    var need = h.send("getLootNeeded");
    var alarm = h.send("getAlarm");
    var won = h.send("hasWon");
    var lost = h.send("hasLost");

    var tasks = "  " + tick(h.send("hasKey")) + " keycard\n" +
                "  " + tick(loot >= need) + " crates " + loot + "/" + need + "\n" +
                "  " + tick(won) + " back to the van";
    setText("HudTasks", tasks);
    setText("HudObjective", lost ? "CAUGHT -- Interact to try again"
                                 : (won ? (h.send("underPar") ? "CLEAN GETAWAY -- under par, Interact to run it again"
                                                              : "GETAWAY -- over par, Interact to run it again")
                                        : (h.send("areCamerasOff") ? "Camera grid is dark"
                                                                   : "Objectives")));
    setText("HudPrompt", promptText);
    setText("HudStatus", "T+" + clock(h.send("getTime")) + "  /  par " + clock(h.send("getPar")) +
            (h.send("areCamerasOff") ? "   cameras down" :
             (h.send("getCameras") > 0.05 ? "   camera on you" : "")) +
            (h.send("wasSpotted") ? "   seen by " + h.send("getSpottedBy") : ""));
    setText("HudToast", h.send("getToast"));

    var key = scene.find("HudKey");
    if (key) key.setVisible(h.send("hasKey"));
    for (var i = 0; i < pips; i++) {
        var p = scene.find("Pip" + i);
        if (p) p.setColor(i < loot ? "#FFE8C46A" : "#FF2C2E3A");
    }
    var bar = scene.find("AlarmBar");
    if (bar) {
        bar.scaleX = 4.4 * Math.min(1.0, alarm);
        bar.setColor(alarm > 0.66 ? "#FFFF4A38" : "#FFFFC24A");
        bar.setVisible(alarm > 0.01);
    }
    var holdBar = scene.find("HoldBar");
    if (holdBar) {
        holdBar.scaleX = 1.7 * Math.max(0.0, Math.min(1.0, holdAmount));
        holdBar.setVisible(holdAmount > 0.01);
    }
    var frame = scene.find("HoldFrame");
    if (frame) frame.setVisible(holdAmount > 0.01);
}

function tick(done) { return done ? "[x]" : "[ ]"; }

function clock(sec) {
    var m = Math.floor(sec / 60.0);
    var s = Math.floor(sec) % 60;
    return m + ":" + (s < 10 ? "0" + s : "" + s);
}

function setText(name, value) {
    var o = scene.find(name);
    if (o) o.setText(value);
}

function prompt(msg) { promptText = msg; }
function hold(p) { holdAmount = p; }
"""

FX_JS = r"""
// Blackout Heist -- Burst.js
//
// One-shot effects, fired onto pooled emitters: a footstep, a cracked crate, a
// guard's shout and the alarm.  The 2D renderer has no lights, so an additive
// burst is what a noise looks like in this game.
//

function start() {
    // The pooled emitters are burst-only: if one of them ever started emitting on
    // its own, the level would fill with dust nobody asked for.
    self.setEmitting(false);
}

function burst(name, payload, n, color) {
    var parts = ("" + payload).split(":");
    var x = parseFloat(parts[0]), y = parseFloat(parts[1]);
    var e = scene.find(name);
    if (!e) return;
    e.setPosition(x, y, 0.0);
    e.setColor(color);
    e.burst(n);
}

function noise(payload) { burst("FxNoise", payload, 6, "#66C8E0FF"); }
function crack(payload) { burst("FxCrack", payload, 16, "#FFE0C060"); }
function spot(payload) { burst("FxSpot", payload, 12, "#FFFFC24A"); }
function alarm(payload) { burst("FxSpot", payload, 26, "#FFFF4A38"); }

/** A van pulling away: smoke, thrown back, in two directions. */
function escape(payload) {
    var parts = ("" + payload).split(":");
    var x = parseFloat(parts[0]), y = parseFloat(parts[1]);
    scene.spawn("Puff", x, y - 1.0);
    scene.spawn("Puff", x - 0.8, y - 1.6);
    scene.spawn("Puff", x + 0.8, y - 1.6);
    audio.play("sfx_door.ogg", 0.7);
}
"""

PUFF_JS = r"""
// Blackout Heist -- Puff.js
//
// A one-shot puff of dust: the emitter fires once when the clone appears and the
// copy removes itself when its life runs out.  Spawning a short-lived clone is
// how this game bursts particles somewhere that has no pooled emitter of its own.
//
// Params: count=12, life=1.1

var left = 0.0;

function start() {
    left = life;
    self.burst(count);
}

function update(dt) {
    left -= dt;
    if (left <= 0.0) self.destroy();
}
"""


# --------------------------------------------------------------------------- assets


def build_assets():
    a = []

    # One atlas, fourteen named cells.  The pack ships no metadata, so the grid
    # is declared here -- 10x10 of 16px, read off the PNG with tools/probe_png.py
    # -- and every tile and every piece of dressing is a one-frame clip.
    cells = [("tile_floor", TILE_FLOOR), ("tile_masonry", TILE_MASONRY),
             ("tile_planks", TILE_PLANKS), ("tile_wall", TILE_WALL),
             ("tile_wall_alt", TILE_WALL_ALT), ("tile_cratewall", TILE_CRATEWALL),
             ("tile_shelf", TILE_SHELF), ("tile_stack", TILE_STACK),
             ("tile_bones", TILE_BONES), ("tile_coin", TILE_COIN),
             ("tile_table", TILE_TABLE), ("tile_stool", TILE_STOOL),
             ("tile_pillar", TILE_PILLAR)]
    clips = {name: clip([cell], 1.0, False) for name, cell in cells}
    a.append(asset(DUNGEON, "character and tileset/Dungeon_Tileset.png", "tiles_atlas.png",
                   tileset={"tileSize": 16, "columns": 10, "rows": 10}, clips=clips))

    # Single-cell props from the same pack, as their own files: these are the
    # objects the pack animates as sequences of separate PNGs, so one frame out of
    # each sequence is imported and the open state is reached by a texture swap.
    for name, path in {
        "prop_torch": "items and trap_animation/torch/torch_1.png",
        "prop_candle": "items and trap_animation/torch/candlestick_1_1.png",
        "prop_chest": "items and trap_animation/chest/chest_1.png",
        "prop_chest_open": "items and trap_animation/chest/chest_open_1.png",
        "prop_coin": "items and trap_animation/coin/coin_1.png",
        "prop_keys": "items and trap_animation/keys/keys_1_1.png",
        "prop_flask": "items and trap_animation/flasks/flasks_1_1.png",
        "prop_box": "items and trap_animation/box_1/box_1_1.png",
        "prop_minibox": "items and trap_animation/mini_box_1/mini_box_1_1.png",
        "prop_flag": "items and trap_animation/flag/flag_1.png",
    }.items():
        a.append(asset(DUNGEON, path, name + ".png"))

    # The thief and the guards: full 128x256 sheets, four columns of 32px cells.
    # The layout was read off the sheet with probe_png (rows 0-3 are the idle
    # poses for down/left/right/up and rows 4-7 the walk cycle for the same four
    # directions; within a row the first three cells are distinct and the fourth
    # repeats the first, so the clips are three frames long, not four).
    for alias, path in (("thief", "RPG Top Down Characters - Free Version/Blonde Man/blonde_man.png"),
                        ("guard", "RPG Top Down Characters - Free Version/Blue Haired Woman/blue_haired_woman.png")):
        clips = {}
        for row, direction in enumerate(("down", "left", "right", "up")):
            base = row * 4
            clips[f"{alias}_idle_{direction}"] = clip([base, base + 1], 3.0, True)
            walk = 16 + base
            clips[f"{alias}_walk_{direction}"] = clip([walk, walk + 1, walk + 2], 9.0, True)
        a.append(asset(TOPDOWN, path, alias + ".png",
                       tileset={"tileSize": 32, "columns": 4, "rows": 8}, clips=clips))

    # Vehicles: top-down cars from the car pack.  The longest one is the van.
    for name, path in (("car_van", "car_116.png"), ("car_a", "car_001.png"),
                       ("car_b", "car_057.png"), ("car_c", "car_090.png")):
        a.append(asset(CARS, path, name + ".png"))

    # Loot and props: the Super Pixel Objects sample, outlined for readability.
    for name, path in (("keycard", "outline_light/key_large/key_A_blue.png"),
                       ("crate", "outline_light/chest_large/frame0000.png"),
                       ("crate_open", "outline_light/chest_large/frame0007.png"),
                       ("mug", "outline_light/potion_large/potion_A_blue_full.png"),
                       ("meat", "outline_light/meat_large/meat.png"),
                       ("gem", "outline_light/gem_large/frame0000.png")):
        a.append(asset(SUPER, path, name + ".png"))

    # HUD: 9-slice panels stretched as plain sprites.  No nine-slice renderer in
    # the engine, so the frames are used as the blocks they look like.
    for name, path in (("ui_panel", "9-Slice/Colored/grey.png"),
                       ("ui_dark", "9-Slice/space.png"),
                       ("ui_alert", "9-Slice/Colored/red.png"),
                       ("ui_gold", "9-Slice/Colored/yellow.png"),
                       ("ui_green", "9-Slice/Colored/green.png"),
                       ("ui_screen", "9-Slice/Colored/blue.png"),
                       ("ui_frame", "9-Slice/Outline/blue.png")):
        a.append(asset(PIXUI, path, name + ".png"))

    # Light, dust and effects.
    for name, path in (("fx_glow", "PNG (Transparent)/circle_02.png"),
                       ("fx_light", "PNG (Transparent)/light_01.png"),
                       ("fx_flare", "PNG (Transparent)/flare_01.png"),
                       ("fx_spark", "PNG (Transparent)/spark_04.png"),
                       ("fx_dust", "PNG (Transparent)/dirt_02.png"),
                       ("fx_smoke", "PNG (Transparent)/smoke_02.png"),
                       ("fx_flame", "PNG (Transparent)/fire_01.png")):
        a.append(asset(PARTS, path, name + ".png"))

    # Sound, aliased per event.
    for name, path in (("sfx_step", "Sounds/tap-a.ogg"), ("sfx_ui", "Sounds/click-a.ogg"),
                       ("sfx_pick", "Sounds/click-b.ogg"), ("sfx_alarm", "Sounds/switch-a.ogg"),
                       ("sfx_door", "Sounds/switch-b.ogg"), ("sfx_win", "Sounds/tap-b.ogg"),
                       ("sfx_caught", "Sounds/switch-a.ogg"), ("sfx_cam", "Sounds/click-a.ogg")):
        a.append(asset(UIPACK, path, name + ".ogg"))
    return a


# --------------------------------------------------------------------------- scene


class Build:
    """Level + scene under construction: every helper here places one object."""

    def __init__(self, grid, mats, rooms):
        self.grid = grid
        self.mats = mats
        self.rooms = rooms
        self.scene = Scene(name="Main", gravity=(0.0, 0.0, 0.0), gravity3d=0.0,
                           ambient="#FF5E6274")
        self.n = {}
        self.props = 0

    def _name(self, prefix):
        self.n[prefix] = self.n.get(prefix, 0) + 1
        return f"{prefix}_{self.n[prefix]:03d}"

    # -- tiles -------------------------------------------------------------
    def floor_piece(self, cx, cy, w, mat, edge=False):
        clip_name, _cell, tint = MATERIAL_TILE[mat]
        if edge:
            tint = MATERIAL_EDGE[mat]
        wx, wy = cell_to_world(cx, cy)
        self.scene.add(self._name("Floor"), x=wx + (w - 1) * 0.5, y=wy, tag="Floor",
                       order=-100, sx=float(w), sy=1.0,
                       comps=[sprite("tiles_atlas.png", tint, order=-100),
                              animator(clip_name, play_on_start=True)])

    def wall_piece(self, cx, cy, w, mat):
        wx, wy = cell_to_world(cx, cy)
        tint = WALL_TINT.get(mat, WALL_TINT["stone"])
        self.scene.add(self._name("Wall"), x=wx + (w - 1) * 0.5, y=wy, tag="Wall",
                       order=6, sx=float(w), sy=1.0,
                       comps=[sprite("tiles_atlas.png", tint, order=6),
                              animator("tile_wall", play_on_start=True),
                              collider2d("Box", float(w), 1.0)])

    # -- props -------------------------------------------------------------
    def tile_prop(self, clip_name, cx, cy, sx=1.0, sy=1.0, tint="#FFFFFFFF",
                  order=7, tag="Prop", solid=False, comps=(), extra=None):
        wx, wy = cell_to_world(cx, cy)
        parts = [sprite("tiles_atlas.png", tint, order=order),
                 animator(clip_name, play_on_start=True)]
        parts += list(comps)
        oid = self.scene.add(self._name("Prop"), x=wx, y=wy, tag=tag, order=order,
                             sx=sx, sy=sy, comps=parts)
        if solid:
            block(self.grid, cx, cy)
        self.props += 1
        return oid

    def png_prop(self, texture, cx, cy, sx=1.0, sy=1.0, tint="#FFFFFFFF", order=7,
                 tag="Prop", solid=False, comps=(), ox=0.0, oy=0.0):
        wx, wy = cell_to_world(cx, cy)
        parts = [sprite(texture, tint, order=order)] + list(comps)
        oid = self.scene.add(self._name("Prop"), x=wx + ox, y=wy + oy, tag=tag,
                             order=order, sx=sx, sy=sy, comps=parts)
        if solid:
            block(self.grid, cx, cy)
        self.props += 1
        return oid

    def car(self, texture, source, cx, cy, length, tint="#FFFFFFFF"):
        """A vehicle drawn at its true proportions, `length` world units long.

        One world unit is one tile, and a tile is one character's stride: the car
        pack's sprites are 4-6 tiles long at native scale, which would park a bus
        across the room.  Scaling to a length makes the vehicles the size of the
        building they are parked in.
        """
        w, h = store_size(CARS, source)
        scale = length / h
        wx, wy = cell_to_world(cx, cy)
        self.scene.add(self._name("Car"), x=wx, y=wy, tag="Vehicle", order=5,
                       sx=w * scale, sy=h * scale,
                       comps=[sprite(texture, tint, order=5)])
        block(self.grid, cx - int(round(w * scale)) // 2, cy - int(round(h * scale)) // 2,
              int(round(w * scale)), int(round(h * scale)))
        self.props += 1

    # -- light -------------------------------------------------------------
    def lamp(self, cx, cy, radius, texture="prop_torch.png", big=False):
        """A lamp is two objects: the fixture, and the pool it casts.

        The pool is the object tagged `Light`, and its drawn size *is* the radius
        the scripts test against, so there is exactly one number and it is the one
        on screen.
        """
        wx, wy = cell_to_world(cx, cy)
        self.scene.add(self._name("Lamp"), x=wx, y=wy + 0.15, tag="Prop", order=8,
                       comps=[sprite(texture, order=8),
                              particles(color="#80FFD080", end="#00FF8000", size=0.5,
                                        lifetime=0.9, speed=0.9, direction=90, spread=50,
                                        gravity=0.6, max_particles=12, additive=True,
                                        emitting=True, texture="fx_flame.png")])
        self.scene.add(self._name("Pool"), x=wx, y=wy - 0.1, tag="Light", order=70,
                       sx=radius * 2.0, sy=radius * 2.0,
                       comps=[sprite("fx_light.png",
                                     "#4CFFB864" if not big else "#5CFFC070",
                                     order=70)])
        self.props += 1


def dress(b):
    """Every prop, lamp and quest object in the depot, room by room."""
    g, rooms = b.grid, b.rooms
    st = "tile_stack"
    shelf, bones, coinpile = "tile_shelf", "tile_bones", "tile_coin"
    table, stool, pillar = "tile_table", "tile_stool", "tile_pillar"
    cratewall = "tile_cratewall"

    # ---- Dock: the way in, the way out, and the searchlight over both.
    b.car("car_van.png", "car_116.png", VAN_CELL[0], VAN_CELL[1], 3.6)     # the way out
    b.car("car_a.png", "car_001.png", 4, 34, 2.6)
    b.car("car_b.png", "car_057.png", 4, 38, 2.6)
    for cell in [(7, 31), (8, 31), (7, 32), (12, 30), (17, 30), (18, 30)]:
        b.tile_prop(cratewall, cell[0], cell[1], tint="#FFC8BCA8", solid=True)
    for cell in [(3, 30), (6, 36), (9, 40), (13, 40), (18, 36)]:
        b.tile_prop(st, cell[0], cell[1], tint="#FFC0B4A0", solid=True)
    b.png_prop("prop_flag.png", 3, 39, tint="#FFE0B0A0")
    b.png_prop("prop_minibox.png", 10, 31)
    b.png_prop("prop_minibox.png", 19, 33)
    b.lamp(6, 30, 3.6, big=True)
    b.lamp(17, 30, 3.6, big=True)

    # ---- WestLink and the store room.
    for cell in [(8, 26), (10, 28)]:
        b.png_prop("prop_crate.png" if False else "prop_box.png", cell[0], cell[1], tint="#FFC8BCA8")
    for row in (15, 19):
        for cx in range(5, 18, 3):
            b.tile_prop(shelf, cx, row, tint="#FFC8BEA4", solid=True)
    b.tile_prop(table, QUEST_KEY[0] + 1, QUEST_KEY[1], tint="#FFC8BCA8", solid=True)
    b.tile_prop(stool, QUEST_KEY[0] + 1, QUEST_KEY[1] + 1, 0.8, 0.8, solid=True)
    for cell in [(3, 12), (19, 12), (3, 23), (19, 23), (12, 22)]:
        b.tile_prop(st, cell[0], cell[1], tint="#FFC0B4A0", solid=True)
    for cell in [(4, 20), (7, 21), (15, 21), (18, 18)]:
        b.png_prop("prop_box.png", cell[0], cell[1], tint="#FFC8BCA8")
    b.png_prop("prop_coin.png", 12, 17, tint="#FFF0D090")
    b.png_prop("prop_coin.png", 13, 17, tint="#FFF0D090")
    b.lamp(5, 12, 3.4)
    b.lamp(14, 12, 3.4)
    b.lamp(5, 22, 3.4)
    b.lamp(15, 22, 3.4)

    # ---- The keycard on the desk, with a glow so a dark room still reads.
    wx, wy = cell_to_world(*QUEST_KEY)
    b.scene.add("Keycard", x=wx, y=wy, tag="Interact", order=9,
                sx=0.5, sy=0.5,
                comps=[sprite("keycard.png", order=9),
                       script("Loot.js", "kind=key, hold=0.9, sound=sfx_pick.ogg, locked=false")])
    b.scene.add("KeycardGlow", x=wx, y=wy, tag="Loot", order=69,
                comps=[particles(color="#70FFE8A0", end="#00FFE8A0", size=0.8, end_size=1.2,
                                 rate=7.0, lifetime=1.3, speed=0.15, direction=90, spread=180,
                                 gravity=0.0, max_particles=14, additive=True, emitting=True,
                                 texture="fx_spark.png")])

    # ---- Hall: pillars down the middle, lamps on the walls.
    for cx in range(26, 54, 5):
        b.tile_prop(pillar, cx, 22, 1.0, 1.0, tint="#FFC0A888", solid=True)
    for cell in [(24, 24), (30, 24), (36, 24), (42, 24), (48, 24), (54, 24),
                 (24, 21), (32, 21), (40, 21), (48, 21)]:
        b.lamp(cell[0], cell[1] - 0.2, 3.2, texture="prop_candle.png")
    for cell in [(28, 23), (44, 23), (52, 22)]:
        b.tile_prop(st, cell[0], cell[1], 0.9, 0.9, tint="#FFC0B4A0", solid=True)

    # ---- Server room: racks in rows, the terminal at the top of the room.
    for row in (12, 16):
        for cx in range(26, 37, 2):
            b.tile_prop(shelf, cx, row, tint="#FFA8B4C0", solid=True,
                        comps=[particles(color="#30A0E0FF", end="#00A0E0FF", size=0.5,
                                         end_size=0.9, rate=5.0, lifetime=1.4, speed=0.2,
                                         direction=90, spread=180, gravity=0.0,
                                         max_particles=10, additive=True, emitting=True,
                                         texture="fx_glow.png")])
    term_x, term_y = QUEST_TERMINAL
    wx, wy = cell_to_world(term_x, term_y)
    b.scene.add("Terminal", x=wx, y=wy, tag="Interact", order=8, sx=1.1, sy=1.1,
                comps=[sprite("ui_screen.png", "#FF9AD8FF", order=8),
                       script("Terminal.js", "hold=1.2")])
    b.scene.add("TerminalGlow", x=wx, y=wy, tag="Loot", order=69,
                comps=[particles(color="#60A0E8FF", end="#00A0E8FF", size=0.9, end_size=1.4,
                                 rate=8.0, lifetime=1.2, speed=0.15, direction=90, spread=180,
                                 gravity=0.0, max_particles=14, additive=True, emitting=True,
                                 texture="fx_spark.png")])
    b.tile_prop(table, term_x - 2, term_y, tint="#FFB0BCC8", solid=True)
    b.tile_prop(table, term_x + 2, term_y, tint="#FFB0BCC8", solid=True)
    b.lamp(26, 10, 3.2, texture="prop_candle.png")
    b.lamp(36, 10, 3.2, texture="prop_candle.png")

    # ---- Vault: clean planks, crates on pallets, coin on the floor.
    for i, (cx, cy) in enumerate(QUEST_CRATES):
        wx, wy = cell_to_world(cx, cy)
        b.scene.add(f"Crate{chr(65 + i)}", x=wx, y=wy, tag="Interact", order=9,
                    sx=0.48, sy=0.48,
                    comps=[sprite("crate.png", order=9),
                           script("Loot.js", "kind=crate, hold=1.6, sound=sfx_pick.ogg, "
                                             "locked=true, openTex=crate_open.png")])
        b.scene.add(f"Crate{chr(65 + i)}Glow", x=wx, y=wy, tag="Loot", order=69,
                    comps=[particles(color="#60FFD070", end="#00FFD070", size=1.0,
                                     end_size=1.5, rate=9.0, lifetime=1.2, speed=0.15,
                                     direction=90, spread=180, gravity=0.0, max_particles=16,
                                     additive=True, emitting=True, texture="fx_spark.png")])
        block(g, cx, cy)
    safe_x, safe_y = SAFE_CELL
    wx, wy = cell_to_world(safe_x, safe_y)
    b.scene.add("Safe", x=wx, y=wy, tag="Interact", order=8, sx=0.7, sy=0.7,
                comps=[sprite("prop_chest.png", order=8),
                       script("Loot.js", "kind=safe, hold=2.2, sound=sfx_pick.ogg, "
                                         "locked=false, openTex=prop_chest_open.png")])
    b.scene.add("SafeGlow", x=wx, y=wy, tag="Loot", order=69,
                comps=[particles(color="#50E8E0B0", end="#00E8E0B0", size=0.7, end_size=1.1,
                                 rate=6.0, lifetime=1.2, speed=0.15, direction=90, spread=180,
                                 gravity=0.0, max_particles=12, additive=True, emitting=True,
                                 texture="fx_spark.png")])
    block(g, safe_x, safe_y)
    for cell in [(43, 10), (50, 10), (56, 10), (43, 19), (50, 19), (56, 19), (56, 15)]:
        b.tile_prop(coinpile, cell[0], cell[1], tint="#FFF0D090")
    for cell in [(46, 18), (55, 12)]:
        b.tile_prop(st, cell[0], cell[1], tint="#FFC0B4A0", solid=True)
    b.lamp(43, 12, 3.2, texture="prop_candle.png")
    b.lamp(55, 18, 3.2, texture="prop_candle.png")

    # ---- Break room: the guard post.  A table nobody has cleared.
    for cx, cy in [(45, 32), (49, 32)]:
        b.tile_prop(table, cx, cy, tint="#FFC8BCA8", solid=True)
        b.tile_prop(stool, cx, cy + 1, 0.8, 0.8, solid=True)
        b.tile_prop(stool, cx + 1, cy, 0.8, 0.8, solid=True)
    b.png_prop("mug.png", 46, 32, 0.5, 0.5)
    b.png_prop("meat.png", 50, 32, 0.5, 0.5)
    b.png_prop("gem.png", 52, 33, 0.4, 0.4, tint="#FFB0E0FF")
    for cell in [(42, 27), (56, 27), (42, 38), (56, 38), (44, 38), (54, 28)]:
        b.tile_prop(st, cell[0], cell[1], tint="#FFC0B4A0", solid=True)
    b.png_prop("prop_flask.png", 47, 34)
    b.png_prop("prop_flask.png", 48, 34)
    b.lamp(43, 30, 3.4, big=True)
    b.lamp(53, 36, 3.4, big=True)

    # ---- The crawlway under the loading dock: the quiet way east.
    for cell in [(12, 41), (18, 42), (24, 41), (30, 42), (36, 41)]:
        b.tile_prop(bones, cell[0], cell[1], tint="#FFD8D0C0")
    for cell in [(15, 42), (28, 41), (38, 40)]:
        b.tile_prop(st, cell[0], cell[1], 0.9, 0.9, tint="#FFB8AC9C", solid=True)
    b.lamp(11, 41, 3.0, texture="prop_candle.png")
    b.lamp(24, 41, 3.0, texture="prop_candle.png")
    b.lamp(37, 41, 3.0, texture="prop_candle.png")
    b.scene.add("CrawlDust", x=cell_to_world(25, 41)[0], y=cell_to_world(25, 41)[1],
                tag="Prop", order=13,
                comps=[particles(color="#22C8C0B0", end="#00C8C0B0", size=0.25, end_size=0.5,
                                 rate=4.0, lifetime=2.4, speed=0.3, direction=90, spread=180,
                                 gravity=0.0, max_particles=20, additive=True, emitting=True,
                                 texture="fx_dust.png")])


def build_scene(b, grid, mats):
    s = b.scene

    # ---- camera: the game view.  The HUD is drawn separately (see Hud.js).
    s.add("Main Camera", comps=[cam2d(size=7.5, bg="#FF07070C", follow="Thief",
                                      smooth=9.0, post="Vignette", fx=0.5)])

    # ---- the base plate: the mass the whole depot is cut out of.  One object,
    # because the inside of a wall is never seen and never drawn.
    mx, my = cell_to_world(MAP_W // 2, MAP_H // 2)
    s.add("Bedrock", x=mx, y=my, tag="Bedrock", order=-110,
          sx=float(MAP_W), sy=float(MAP_H),
          comps=[sprite("", "#FF0C0B12", order=-110)])

    # ---- floor and walls
    for cx, cy, w, mat, edge in floor_pieces(grid, mats):
        b.floor_piece(cx, cy, w, mat, edge)
    for cx, cy, w in wall_faces(grid):
        mat = mats[cy][cx - 1] if cx > 0 and mats[cy][cx - 1] else \
              (mats[cy][cx + 1] if cx + 1 < MAP_W and mats[cy][cx + 1] else "stone")
        b.wall_piece(cx, cy, w, mat)

    # ---- dressing
    dress(b)

    # ---- the thief
    tx, ty = cell_to_world(*START_CELL)
    s.add("Thief", x=tx, y=ty, tag="Thief", order=10, sx=2.0, sy=2.0,
          comps=[sprite("thief.png", order=10),
                 animator("thief_idle_down", play_on_start=True),
                 script("Thief.js",
                        "creepSpeed=1.5, walkSpeed=3.0, runSpeed=4.6, radius=0.34, "
                        "interactRange=1.35, creepNoise=1.2, walkNoise=2.4, runNoise=5.4")])

    # ---- the guards
    for name, route, tint in GUARDS:
        cells = []
        for (cx, cy) in route:
            wx, wy = cell_to_world(cx, cy)
            cells.append(f"{round(wx, 3)}:{round(wy, 3)}")
        wx, wy = cell_to_world(*route[0])
        s.add(name, x=wx, y=wy, tag="Guard", order=10, sx=2.0, sy=2.0,
              comps=[sprite("guard.png", tint, order=10),
                     animator("guard_idle_up", play_on_start=True),
                     script("Guard.js",
                            "speed=2.4, chaseSpeed=4.2, sight=7.5, cone=95, nearCone=160, "
                            "nearSight=2.6, litBonus=1.45, detectRate=0.8, decay=0.45, "
                            "hearing=3.2, hearRate=0.75, catchRange=0.62, "
                            "route=" + "|".join(cells))])
        s.add(f"{name}Mark", x=wx, y=wy + 0.95, tag="Mark", order=12,
              active=False,
              comps=[sprite("fx_flare.png", "#FFEDE08A", order=12)])

    # ---- the eyes: two ceiling cameras and the searchlight over the dock
    for name, cell, centre, sweep, speed, sight, cone, rate in EYES:
        wx, wy = cell_to_world(*cell)
        s.add(name, x=wx, y=wy, tag="Eye", order=11,
              comps=[sprite("tiles_atlas.png", "#FF8E96A8", order=11),
                     animator("tile_pillar", play_on_start=True),
                     script("Cam.js", f"centre={centre}, sweep={sweep}, speed={speed}, "
                                      f"sight={sight}, cone={cone}, rate={rate}")])
        s.add(f"{name}Beam", x=wx, y=wy - 0.25, tag="Beam", order=68,
              comps=[particles(color="#22C8E0FF", end="#00C8E0FF", size=0.5, end_size=3.6,
                               rate=30.0, lifetime=1.0, speed=4.2, direction=270, spread=10,
                               gravity=0.0, max_particles=52, additive=True, emitting=True,
                               texture="fx_glow.png")])

    # ---- the way out
    for cell in [(VAN_CELL[0] - 1, VAN_CELL[1] + 2)]:
        wx, wy = cell_to_world(*cell)
        s.add("VanStep", x=wx, y=wy, tag="Interact", order=9,
          comps=[sprite("fx_light.png", "#4640E0A0", order=9),
                 script("Exit.js", "need=3, hold=1.2")])
    block(grid, VAN_CELL[0] - 1, VAN_CELL[1] - 2, 2, 4)

    # ---- director, HUD and effects
    s.add("Heist", tag="Director",
          comps=[script("Heist.js", "lootNeeded=3, alarmDecay=0.14, alarmLimit=1.0, parTime=210")])
    s.add("Fx", tag="Fx", comps=[script("Burst.js")])
    for name, color, tex in (("FxNoise", "#66C8E0FF", "fx_dust.png"),
                             ("FxCrack", "#FFE0C060", "fx_spark.png"),
                             ("FxSpot", "#FFFFC24A", "fx_spark.png")):
        s.add(name, tag="Fx", order=13,
              comps=[particles(color=color, end="#00FFFFFF", size=0.3, end_size=1.2,
                               rate=0.0, lifetime=0.7, speed=2.6, direction=90, spread=180,
                               gravity=0.0, max_particles=32, additive=True, emitting=False,
                               texture=tex)])

    # HUD: the runtime draws screen-space objects through a fixed +/-5-unit view,
    # so everything here stays inside +/-6.6 x +/-4.8.
    s.add("Hud", tag="Hud", comps=[script("Hud.js", "pips=3")])
    s.add("HudPanel", x=-4.4, y=3.6, tag="Hud", order=80, sx=4.2, sy=2.6,
          comps=[sprite("ui_panel.png", "#B0101220", order=80, screen=True)])
    s.add("HudTasks", x=-6.4, y=3.9, tag="Hud", order=82,
          comps=[text("", 0.26, "#FFE8E0C8", "Left", screen=True)])
    s.add("HudObjective", x=-6.4, y=2.35, tag="Hud", order=82,
          comps=[text("", 0.24, "#FFA8B4C8", "Left", screen=True)])
    s.add("HudStatus", x=-6.4, y=-4.3, tag="Hud", order=82,
          comps=[text("", 0.24, "#FF8E96A8", "Left", screen=True)])
    s.add("AlarmFrame", x=5.2, y=4.3, tag="Hud", order=80, sx=4.6, sy=0.5,
          comps=[sprite("ui_frame.png", "#FF7E8696", order=80, screen=True)])
    s.add("AlarmBar", x=3.0, y=4.3, tag="Hud", order=81, sx=0.0, sy=0.34,
          comps=[sprite("ui_alert.png", "#FFFFC24A", order=81, screen=True)])
    s.add("AlarmLabel", x=5.2, y=4.75, tag="Hud", order=82,
          comps=[text("alarm", 0.22, "#FF9AA2B4", "Center", screen=True)])
    s.add("HudKey", x=-2.6, y=-4.3, tag="Hud", order=83, sx=0.6, sy=0.6,
          comps=[sprite("keycard.png", order=83, screen=True)])
    s.add("HudKeyLabel", x=-1.9, y=-4.3, tag="Hud", order=82,
          comps=[text("keycard", 0.22, "#FFE8E0C8", "Left", screen=True)])
    for i in range(3):
        s.add(f"Pip{i}", x=1.2 + i * 0.7, y=-4.3, tag="Hud", order=83, sx=0.5, sy=0.5,
              comps=[sprite("ui_gold.png", "#FF2C2E3A", order=83, screen=True)])
    s.add("PipLabel", x=2.0, y=-3.85, tag="Hud", order=82,
          comps=[text("loot", 0.22, "#FF9AA2B4", "Center", screen=True)])
    s.add("HoldFrame", x=0.0, y=-3.5, tag="Hud", order=80, sx=1.9, sy=0.34,
          comps=[sprite("ui_frame.png", "#FF7E8696", order=80, screen=True)])
    s.add("HoldBar", x=-0.85, y=-3.5, tag="Hud", order=81, sx=0.0, sy=0.24,
          comps=[sprite("ui_green.png", "#FF86E8B0", order=81, screen=True)])
    s.add("HudPrompt", x=0.0, y=-2.9, tag="Hud", order=82,
          comps=[text("", 0.3, "#FFE8E0C8", "Center", screen=True)])
    s.add("HudToast", x=0.0, y=2.9, tag="Hud", order=82,
          comps=[text("", 0.28, "#FFFFD98A", "Center", screen=True)])

    # ---- spawnable one-shot effect
    s.add("Puff", tag="Template", active=False, order=14,
          comps=[particles(color="#44D8D0C0", end="#00D8D0C0", size=0.6, end_size=2.4,
                           rate=0.0, lifetime=0.9, speed=1.8, direction=90, spread=180,
                           gravity=-0.2, max_particles=24, additive=True, emitting=False,
                           texture="fx_smoke.png"),
                 script("Puff.js", "count=12, life=1.1")])
    return s


# --------------------------------------------------------------------------- build


def level_report(grid, rooms):
    walk = sum(row.count(".") for row in grid)
    blocked = sum(row.count("x") for row in grid)
    print(f"  level: {MAP_W}x{MAP_H} cells -- {walk} open, {blocked} blocked by props, "
          f"{MAP_W * MAP_H - walk - blocked} wall")
    reach = flood(grid, START_CELL)
    must = {("Dock", (START_CELL[0] + 1, START_CELL[1] - 2)),
            ("Stores", (10, 18)), ("Hall", (38, 22)), ("Server", (31, 15)),
            ("Vault", (47, 15)), ("Break", (46, 35)), ("Crawl", (25, 41))}
    for label, cell in must:
        assert cell in reach, f"{label} at {cell} is not reachable from the start"
    print(f"  reachable from the start: {len(reach)} cells, every room included")
    return reach


def build():
    grid, mats, rooms = carve_level()
    b = Build(grid, mats, rooms)
    build_scene(b, grid, mats)
    reach = level_report(grid, rooms)

    # Every spawn, patrol point and quest object must still be reachable once the
    # props are down: this is the check that keeps dressing from walling a room off.
    doorways = [(START_CELL[0] - 1, START_CELL[1]), (VAN_CELL[0] - 1, VAN_CELL[1] + 2)]
    for name, route, _tint in GUARDS:
        for cell in route:
            assert grid[cell[1]][cell[0]] == ".", f"{name}'s patrol point {cell} is blocked"
    for cell in doorways:
        assert cell in reach, f"a doorway at {cell} is walled off"
    for cell in [QUEST_KEY, QUEST_TERMINAL] + QUEST_CRATES + list(SAFE_CELL and [SAFE_CELL]):
        free = [(cell[0] + dx, cell[1] + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))]
        assert any(c in reach for c in free), f"nothing can reach the objective at {cell}"

    sc = b.scene
    assets = build_assets()

    rows = to_rows(grid)
    map_literal = "[\n" + ",\n".join(f'  "{r}"' for r in rows) + "\n]"

    scripts = {}
    for name, src in (("Thief.js", THIEF_JS), ("Guard.js", GUARD_JS), ("Cam.js", CAM_JS),
                      ("Loot.js", LOOT_JS), ("Terminal.js", TERMINAL_JS), ("Exit.js", EXIT_JS),
                      ("Heist.js", HEIST_JS), ("Hud.js", HUD_JS), ("Burst.js", FX_JS),
                      ("Puff.js", PUFF_JS)):
        src = src.strip()
        if "__MAP__" in src:
            src = src.replace("__MAP__", map_literal)
        src = src.replace("__MAPW__", str(MAP_W)).replace("__MAPH__", str(MAP_H))
        scripts[name] = src

    guards = ", ".join(g[0] for g in GUARDS)
    man = manifest(
        GID, "Blackout Heist", "2D top-down stealth heist",
        list(scripts.keys()), assets, orientation=0,
        controls={"axis": "left stick / WASD -- move (push further to run)",
                  "Sneak": "toggle -- creep, quiet and much harder to spot",
                  "Interact": "hold to take the keycard, crack a crate or safe, cut the "
                              "camera feed, or climb into the van",
                  "Interact (aftermath)": "start the run again"},
        systems=["grid-locked movement with script-owned collision and prop blocking",
                 "guard vision: range, cone (wider up close), and an exact line of sight "
                 "walk over the level grid",
                 "per-guard detection meter drawn above the guard, feeding a shared alarm",
                 "light as a gameplay value: a lamp pool is the radius guards can see you in",
                 "noise: running is heard through walls, creeping is not",
                 "three sweeping eyes -- two cameras and the dock searchlight -- and one "
                 "terminal that blinds the grid for the rest of the run",
                 "hold-to-interact chain: keycard, three vault crates, the van",
                 "procedurally carved rooms with a connectivity proof in the builder"],
        notes=["The level grid is baked into Thief.js, Guard.js and Cam.js as a string "
               "table -- '.' open, 'x' blocked by a prop, '#' wall -- and all three are "
               "written from the same grid by tools/build_heist.py, so they cannot drift.",
               "The builder refuses to emit a level whose rooms are not connected, whose "
               "props block a patrol point, or whose objectives cannot be reached.",
               "There are no Light components in this scene: Scene.Light is read by "
               "Renderer3D only, so every lamp is an additive emitter whose drawn size is "
               "the radius the scripts test -- one number, one meaning, visible on screen.",
               "Movement is script-owned.  Wall runs carry Collider2D for the editor, but "
               "the actors test the baked grid, because exact collision is what a stealth "
               "game is played on.",
               "Character animation comes from the RPG Top-Down pack's 128x256 sheets: "
               "rows 0-3 are the idle poses for down/left/right/up and rows 4-7 the walk "
               "cycle for the same directions; the fourth cell of each row repeats the "
               "first, so the clips are three frames long.",
               "The HUD is laid out inside +/-6.6 x +/-4.8 because the runtime draws "
               "screen-space objects through a fixed +/-5-unit view, independent of the "
               "game camera (see SceneRenderer.drawScreenUI).",
               f"Cast: the thief, {guards}, and three sweeping eyes."])
    return sc, man, assets, scripts


README = """
# Blackout Heist

A top-down stealth heist.

One thief, three guards, two cameras and a searchlight, in a depot that is only
lit by the lamps on its walls.  The thief comes in over the fence at the loading
dock and has to get out the same way he came in.

**The objective chain.**  The keycard is on a desk in the store room.  The vault
will not open without it.  Three crates come out of the vault, and the van at the
loading dock will not start until they are on board.  The camera terminal in the
server room is optional and worth the trip: it blinds the whole security grid for
the rest of the run.

**The rule the game is played on.**  A guard sees you only when all three of
these are true: you are inside its range, you are inside its cone (which opens up
close, because peripheral vision is real), and there is an unbroken line of sight
across the level.  Break any one of them and you are gone again.  A guard's marker
goes from pale yellow to amber to red as its suspicion fills; red is the last
stage before it runs at you.

**Light and noise.**  Every lamp draws a pool on the floor, and that pool is also
the extra range a guard can pick you out at -- so a lit room is a room to cross
quickly and a dark corner is worth walking to.  Running is loud enough to be
heard through a wall; creeping is slow, quiet and much harder to spot.

**Controls.**  Left stick / WASD to move, push further to run.  Toggle Sneak to
creep.  Hold Interact to take the keycard, crack a crate or the safe, cut the
camera feed, or climb into the van.

**Winning and losing.**  A guard that reaches you ends the run, and so does the
alarm filling up while a camera or the searchlight holds you.  Press Interact on
the aftermath screen to start again.  Three crates in the van and you drive.

## Files

* `scenes/Main.scene.json` -- the depot: floor and wall tiles, dressing, the
  thief, three guards, three eyes, the HUD and the director.
* `scripts/Thief.js` -- player movement, the sneak toggle, the held interact.
* `scripts/Guard.js` -- patrol / suspect / chase / search, and the vision test.
* `scripts/Cam.js` -- the sweeping camera beam, and the dock searchlight.
* `scripts/Loot.js`, `scripts/Terminal.js`, `scripts/Exit.js` -- the objectives.
* `scripts/Heist.js` -- objectives, alarm, clock, win and loss.
* `scripts/Hud.js` -- objectives, loot, alarm bar and the hold meter.
* `scripts/Burst.js`, `scripts/Puff.js` -- noise, cracks and dust.

## Regenerating

```
python3 tools/build_heist.py
```

Rooms, corridors and their floor materials come from the `ROOMS` table at the top
of the builder; the walls are whatever is left between them.  Change a room and
the tiles, the walkable grid baked into the scripts and the connectivity proof
all follow.
"""


def prune_scripts(declared):
    """A rename in this file must not leave the old script behind in games/.

    write_game() only writes, so a regenerated game would otherwise ship a script
    nobody declares -- which the validator reports and a reader wastes time on.
    """
    d = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                     "games", GID, "scripts")
    for f in sorted(os.listdir(d)):
        if f.endswith(".js") and f not in declared:
            os.remove(os.path.join(d, f))
            print(f"  pruned stale script {f}")


def main():
    sc, man, assets, scripts = build()
    base = write_game(GID, man, sc, scripts, README)
    prune_scripts(set(scripts))
    print(f"  wrote {GID}: {len(sc.objs)} objects, {len(assets)} assets, "
          f"{len(scripts)} scripts")
    print(f"  -> {base}")


if __name__ == "__main__":
    main()
