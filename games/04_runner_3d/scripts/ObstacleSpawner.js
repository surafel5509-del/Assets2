// Neon Overdrive -- ObstacleSpawner.js
// Places obstacles on recycled track segments and resolves crashes.
//
// Two rules keep a procedural runner fair:
//   1. There is ALWAYS a gap wide enough to drive through.  A generator that
//      rolls obstacles independently will eventually wall the track off, and
//      the player dies to something they could not have avoided.
//   2. Difficulty changes the DENSITY and PATTERN, never the impossibility.
//
// Obstacles come from a prototype object and are deactivated, not destroyed.
//
// Params: poolSize=48, carHalfWidth=0.9, minGap=3.2

var pool = [];
var poolIndex = 0;
var carHalfWidth = 0.9;
var lastPattern = -1;

function start() {
    pool.length = 0;
    for (var i = 0; i < poolSize; i++) {
        var o = scene.spawn("Obstacle", 0, 0, 0);
        if (!o) continue;
        o.active = false;
        pool.push(o);
    }
    log("Obstacle pool: " + pool.length);
}

function update(dt) {
    var car = scene.find("Vehicle");
    if (!car || !car.send("isAlive")) return;

    checkCollisions(car);
    checkNearMiss(car);
}

/**
 * TrackGenerator calls this when a segment is recycled.
 *   difficulty 0..1
 *
 * A segment is divided into lanes; the generator picks a pattern that always
 * leaves at least one clear lane.
 */
function populateSegment(segment, difficulty) {
    var track = scene.find("TrackGenerator");
    var len = track ? track.send("getSegmentLength") : 24;
    var halfW = track ? track.send("getWidth") / 2 : 6.5;

    var lanes = 5;
    var laneW = (halfW * 2) / lanes;

    // Blocked-lane count grows with difficulty but never reaches `lanes`,
    // which is the invariant that keeps the track passable.
    var blocked = 1 + Math.floor(difficulty * (lanes - 2));
    var start = Math.floor(random(0, lanes - blocked + 0.999));

    // Vary the pattern so the run does not read as the same wall repeated.
    var pattern = (lastPattern + 1 + Math.floor(random(0, 3))) % 3;
    lastPattern = pattern;

    for (var l = 0; l < lanes; l++) {
        var isBlocked = false;
        if (pattern === 0) isBlocked = (l >= start && l < start + blocked);
        else if (pattern === 1) isBlocked = (l % 2 === (start % 2));       // alternating
        else isBlocked = (l === start || l === lanes - 1 - start);          // pincers

        // Never block every lane: the safety net for pattern 1 and 2.
        if (isBlocked && countClear(lanes, start, blocked, pattern) < 1) isBlocked = false;

        if (!isBlocked) continue;

        var x = -halfW + laneW * (l + 0.5);
        var z = segment.z - random(2, len - 2);
        place(segment.x + x, z, pickKind(difficulty));
    }
}

function countClear(lanes, start, blocked, pattern) {
    var clear = 0;
    for (var l = 0; l < lanes; l++) {
        var b;
        if (pattern === 0) b = (l >= start && l < start + blocked);
        else if (pattern === 1) b = (l % 2 === (start % 2));
        else b = (l === start || l === lanes - 1 - start);
        if (!b) clear++;
    }
    return clear;
}

function place(x, z, kind) {
    var o = pool[poolIndex % pool.length];
    poolIndex++;
    if (!o) return;
    o.setPosition(x, 0, z);
    o.active = true;
    o.name = kind;                       // the HUD and collider read this
    o.scaleX = o.scaleY = o.scaleZ = kind === "LogHazard" ? 1.6 : 1.0;
}

function pickKind(difficulty) {
    var r = random(0, 1);
    if (r < 0.45) return "RockHazard";
    if (r < 0.75) return "TreeHazard";
    return "LogHazard";
}

/**
 * Collision is an AABB overlap against the car.  Testing the car against the
 * pool (48 objects) every frame is cheaper than 48 rigid bodies in a solver,
 * and it is exact enough at this scale.
 */
function checkCollisions(car) {
    for (var i = 0; i < pool.length; i++) {
        var o = pool[i];
        if (!o || !o.active) continue;
        // Skip anything far away before doing the full test.
        var dz = o.z - car.z;
        if (dz > 6 || dz < -24) continue;
        if (overlaps(o, car)) { car.send("crash"); return; }
    }
}

function overlaps(o, car) {
    var dx = Math.abs(o.x - car.x);
    var dz = Math.abs(o.z - car.z);
    var hw = o.scaleX * 0.5 + carHalfWidth;
    var hl = o.scaleZ * 0.5 + 1.4;
    if (dx > hw || dz > hl) return false;
    // Airborne clears low obstacles: jumping over a log is the reward for
    // timing the jump, so height has to be part of the test.
    if (car.send("isAirborne") && car.y > 0.9) return false;
    return true;
}

/**
 * Passing close to an obstacle without hitting it awards points and nitro.
 * This is the mechanic that turns "avoid things" into "take risks", and it is
 * why the run has a skill ceiling.
 */
function checkNearMiss(car) {
    if (time.frame % 3 !== 0) return;
    for (var i = 0; i < pool.length; i++) {
        var o = pool[i];
        if (!o || !o.active) continue;
        var dz = o.z - car.z;
        if (dz > 2 || dz < -2) continue;
        var dx = Math.abs(o.x - car.x);
        if (dx > 1.6 && dx < 3.0) {
            var hud = scene.find("RacerHUD");
            if (hud) hud.send("onNearMiss");
            car.send("addNitro", 4);
            audio.play("sfx_nearmiss.ogg", 0.3);
        }
    }
}

function deactivateAll() {
    for (var i = 0; i < pool.length; i++) if (pool[i]) pool[i].active = false;
}
