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
        var arch = scene.spawn("SigilArch", s.x, s.y - 0.35);
        if (arch) arch.order = 7;
        if (spots[i][0] === "Deep Vault") {
            var door = scene.spawn("VaultDoor", s.x + 1.0, s.y - 0.1);
            if (door) door.order = 7;
        }
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
        // Every third wave the Blood Monster walks in.  It is the same AI with
        // twelve times the health, so the threat curve gets a step, not a slope.
        if (wave % 3 === 0) spawnBoss();
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

function spawnBoss() {
    var spot = freeSpot(11.0);
    if (!spot) return;
    var b = scene.spawn("BloodMonster", spot[0], spot[1]);
    if (!b) return;
    b.tag = "Enemy";
    b.active = true;
    spawned++;
    toast = 2.4;
    msg("BLOOD MONSTER");
    audio.play("sfx_boss.ogg", 0.5);
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
