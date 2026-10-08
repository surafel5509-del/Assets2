// Emberfall -- TilemapBuilder.js
// Builds the overworld at runtime from a tileset atlas plus a compact map.
//
// The map is a string of hex digits, one per tile, row-major, with newlines for
// readability.  Each digit indexes a tile in a small lookup table below, which
// maps to a cell in tileset_overworld.png (416x464 at 16px = 26 columns).
//
// Spawning one GameObject per tile would mean ~3000 objects for a 60x50 map, so
// tiles are emitted as *chunks*: one sprite per 8x8 block, with the block drawn
// by scaling a sub-rectangle of the atlas.  That keeps the object count near 50
// and the draw calls with it.
//
// Params: tileSize=16, chunk=8, atlasCols=26

// tile id -> [atlasCol, atlasRow, solid]
var TILES = {
    "0": [1, 1, 0],    // grass
    "1": [2, 1, 0],    // grass variant
    "2": [3, 1, 0],    // path
    "3": [4, 2, 1],    // tree
    "4": [5, 2, 1],    // rock
    "5": [6, 3, 1],    // wall
    "6": [7, 3, 0],    // floor
    "7": [8, 4, 1],    // water
    "8": [9, 4, 0],    // flowers
    "9": [10, 5, 1]    // fence
};

var MAP = [
    "3333333333333333333333333333",
    "3000000000000000000000000003",
    "3001100000000000000000110003",
    "3001100002222222222000011003",
    "3000000002000000002000000003",
    "3000400002000666002000040003",
    "3000000002000666002000000003",
    "3008800002000000002000088003",
    "3000000002222222222000000003",
    "3000000000000000000000000003",
    "3000000770000000000770000003",
    "3000000770000000000770000003",
    "3000000000000000000000000003",
    "3000999990000000000999990003",
    "3000000000000000000000000003",
    "3333333333333333333333333333"
];

var ATLAS = "tileset_overworld.png";

var solid = [];
var mapCols = 0, mapRows = 0;

function start() {
    var rows = MAP;
    mapRows = rows.length;
    mapCols = rows[0].length;

    for (var y = 0; y < mapRows; y++) {
        for (var x = 0; x < mapCols; x++) {
            var t = TILES[rows[y].charAt(x)] || TILES["0"];
            solid.push(t[2]);
            // Colliders are only created for solid tiles, and only one per tile:
            // the physics broadphase is per-object, so 500 static bodies costs
            // real time every step.
            if (t[2]) {
                var c = scene.spawn("SolidTile", x - mapCols / 2, -(y - mapRows / 2));
                if (c) { c.tag = "Solid"; c.order = -10; }
            }
        }
    }
    emitChunks();
    log("Tilemap built: " + mapCols + "x" + mapRows + " (" + (mapCols * mapRows) + " tiles)");
}

function update(dt) { /* static geometry; nothing to do per frame */ }

/**
 * Emits one sprite per chunk.  The chunk's texture is the whole atlas and the
 * visible cell is selected by playing a single-frame clip: AnimationClip.cellUv
 * turns the cell index into a UV sub-rectangle, which is the only sub-rect path
 * the engine actually has.  Rarer tiles in the chunk get their own overlay
 * sprite, which still collapses ~64 objects into a handful.
 */
function emitChunks() {
    var rows = MAP;
    for (var cy = 0; cy < mapRows; cy += chunk) {
        for (var cx = 0; cx < mapCols; cx += chunk) {
            var counts = {};
            for (var y = cy; y < Math.min(cy + chunk, mapRows); y++) {
                for (var x = cx; x < Math.min(cx + chunk, mapCols); x++) {
                    var id = rows[y].charAt(x);
                    counts[id] = (counts[id] || 0) + 1;
                }
            }
            // The dominant tile paints the chunk background; rarer tiles get
            // their own overlay sprite so detail is not lost.
            var dominant = "0", best = -1;
            for (var k in counts) if (counts[k] > best) { best = counts[k]; dominant = k; }

            var bg = scene.spawn("GroundChunk",
                (cx + chunk / 2) - mapCols / 2,
                -((cy + chunk / 2) - mapRows / 2));
            if (bg) {
                bg.tag = "Ground";
                bg.order = -100;
                bg.scaleX = chunk; bg.scaleY = chunk;
                paintTile(bg, dominant);
            }

            for (var k2 in counts) {
                if (k2 === dominant) continue;
                var cell = TILES[k2] || TILES["0"];
                var o = scene.spawn("GroundDecal",
                    (cx + chunk / 2) - mapCols / 2,
                    -((cy + chunk / 2) - mapRows / 2));
                if (o) {
                    o.tag = "Ground";
                    o.order = -90;
                    paintTile(o, k2);
                    o.scaleX = chunk * 0.5; o.scaleY = chunk * 0.5;
                }
            }
        }
    }
}

/**
 * Points a sprite at the atlas and selects one cell from it.
 *
 * The cell is chosen by playing a single-frame clip declared in game.json under
 * the tileset's `clips`.  This replaces an earlier "tileset_overworld.png#27"
 * texture name, which looked like an atlas index but was not one: Textures
 * resolves a texture name straight to a file, so that string named a file that
 * does not exist and every tile drew blank.
 */
function paintTile(sprite, id) {
    if (!sprite) return;
    sprite.setTexture(ATLAS);
    var clip = TILES[id] ? "tile_" + id : "tile_0";
    sprite.play(clip);
}

/** Grid query used by the AI to avoid walking into walls. */
function isSolidAt(worldX, worldY) {
    var gx = Math.floor(worldX + mapCols / 2);
    var gy = Math.floor(-worldY + mapRows / 2);
    if (gx < 0 || gy < 0 || gx >= mapCols || gy >= mapRows) return true;
    return solid[gy * mapCols + gx] === 1;
}

/** Simple line-of-sight for enemy aggro, so they do not chase through walls. */
function hasLineOfSight(x0, y0, x1, y1) {
    var dx = x1 - x0, dy = y1 - y0;
    var steps = Math.ceil(Math.sqrt(dx * dx + dy * dy) * 4);
    for (var i = 1; i < steps; i++) {
        var t = i / steps;
        if (isSolidAt(x0 + dx * t, y0 + dy * t)) return false;
    }
    return true;
}
