// Neon Overdrive -- TrackGenerator.js
// Procedural track via segment recycling.
//
// The track is a ring of SEGMENTS pieces.  Each piece sits at a fixed Z, and
// when the vehicle passes one, that piece is moved to the far end and
// re-decorated.  Live geometry is therefore constant no matter how far the run
// goes -- the difference between a runner that plays fine for 30 seconds and one
// that plays fine forever.
//
// Nothing is created or destroyed in the loop; pieces are deactivated and
// reused.  Allocation during a run is what causes the periodic hitch.
//
// Params: segments=14, segmentLength=24, laneHalfWidth=6.5

var pieces = [];
var nextZ = 0;
var distance = 0;
var curveSeed = 1337;

function start() {
    curveSeed = self.tag ? hashString(self.tag) : 1337;
    pieces.length = 0;
    for (var i = 0; i < segments; i++) {
        var p = scene.find("TrackSegment");
        var piece = p ? scene.spawn("TrackSegment", 0, 0, nextZ) : null;
        if (!piece) continue;
        piece.active = true;
        piece.name = "Track_" + i;
        decorate(piece, i);
        pieces.push(piece);
        nextZ -= segmentLength;
    }
    log("Track: " + pieces.length + " live segments");
}

function update(dt) {
    var car = scene.find("Vehicle");
    if (!car) return;

    distance = Math.max(distance, -car.z);

    // Recycle anything the car has left behind.  Comparing against the car's Z
    // rather than a timer means the recycle is exactly in step with motion, so
    // no segment can ever be visible-and-empty.
    for (var i = 0; i < pieces.length; i++) {
        var p = pieces[i];
        if (!p) continue;
        if (p.z > car.z + segmentLength * 1.5) {
            p.setPosition(0, 0, nextZ);
            decorate(p, i);
            nextZ -= segmentLength;
        }
    }
}

/**
 * Decorates a recycled segment: sets its curve and tells the obstacle spawner
 * what to put on it.  Difficulty scales with distance so the run ramps without
 * any level data.
 */
function decorate(piece, index) {
    var difficulty = clamp(distance / 2500, 0, 1);

    // Curve amplitude grows with difficulty.  Keeping it smooth (a sine over the
    // segment index rather than random per segment) matters: a track that
    // changes direction every segment is unreadable at speed.
    var amp = 1.5 + difficulty * 5.0;
    var phase = (index + curveSeed % 97) * 0.35;
    piece.x = Math.sin(phase) * amp;
    piece.rotY = Math.cos(phase) * amp * 1.6;

    var spawner = scene.find("ObstacleSpawner");
    if (spawner) spawner.send("populateSegment", piece, difficulty);

    var pickups = scene.find("PickupSystem");
    if (pickups) pickups.send("populateSegment", piece, difficulty);
}

function getDistance() { return distance; }
function getSegmentLength() { return segmentLength; }
function getWidth() { return laneHalfWidth * 2; }

/** Cheap 32-bit hash so a run can be reproduced from a seed string. */
function hashString(s) {
    var h = 2166136261;
    for (var i = 0; i < s.length; i++) {
        h ^= s.charCodeAt(i);
        h = (h * 16777619) >>> 0;
    }
    return h >>> 0;
}

/** Seeded PRNG: same seed, same track, same obstacles. */
function nextRandom() {
    curveSeed = (curveSeed * 1103515245 + 12345) >>> 0;
    return (curveSeed >>> 8) / 16777216;
}

function setSeed(n) { curveSeed = n >>> 0; }
function getSeed() { return curveSeed; }
