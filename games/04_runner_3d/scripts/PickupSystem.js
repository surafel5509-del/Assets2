// Neon Overdrive -- PickupSystem.js
// Coins and fuel cans, with a combo window.
//
// Pickups are placed in arcs and lines rather than scattered, because a
// scattered field teaches nothing while a line teaches "stay here".  The combo
// window rewards holding that line.
//
// Params: poolSize=40, comboWindow=1.4

var pool = [];
var poolIndex = 0;
var combo = 0;
var comboTimer = 0;
var totalCollected = 0;

function start() {
    pool.length = 0;
    for (var i = 0; i < poolSize; i++) {
        var p = scene.spawn("Pickup", 0, 0, 0);
        if (!p) continue;
        p.active = false;
        pool.push(p);
    }
}

function update(dt) {
    if (comboTimer > 0) {
        comboTimer -= dt;
        if (comboTimer <= 0) endCombo();
    }

    var car = scene.find("Vehicle");
    if (!car) return;
    collect(car);

    // Spin and bob so pickups read as collectible rather than as scenery.
    for (var i = 0; i < pool.length; i++) {
        var p = pool[i];
        if (!p || !p.active) continue;
        p.rotY = (p.rotY + 160 * dt) % 360;
        p.y = 0.9 + Math.sin(time.time * 3 + p.x) * 0.12;
    }
}

function populateSegment(segment, difficulty) {
    var track = scene.find("TrackGenerator");
    var len = track ? track.send("getSegmentLength") : 24;
    var halfW = track ? track.send("getWidth") / 2 : 6.5;

    // 70% of segments carry a pickup run; the rest stay clean so the track does
    // not become a wall of icons.
    if (random(0, 1) > 0.7) return;

    var laneX = random(-halfW + 1.5, halfW - 1.5);
    var z0 = segment.z - random(3, len - 6);
    var kind = random(0, 1) < 0.22 ? "FuelPickup" : "CoinPickup";

    // A gentle arc, not a straight line: following it means steering, which is
    // what makes collecting feel like driving rather than like collecting.
    var count = 5 + Math.floor(random(0, 4));
    for (var i = 0; i < count; i++) {
        var t = i / Math.max(1, count - 1);
        var x = laneX + Math.sin(t * Math.PI) * 2.2;
        place(x, z0 - i * 2.2, kind);
    }
}

function place(x, z, kind) {
    var p = pool[poolIndex % pool.length];
    poolIndex++;
    if (!p) return;
    p.setPosition(x, 0.9, z);
    p.active = true;
    p.name = kind;
}

function collect(car) {
    for (var i = 0; i < pool.length; i++) {
        var p = pool[i];
        if (!p || !p.active) continue;
        var dz = p.z - car.z;
        if (dz > 3 || dz < -3) continue;
        if (Math.abs(p.x - car.x) > 1.2) continue;

        p.active = false;
        onPickup(p.name, car);
    }
}

function onPickup(kind, car) {
    totalCollected++;
    combo++;
    comboTimer = comboWindow;

    var value = kind === "FuelPickup" ? 5 : 1;
    var hud = scene.find("RacerHUD");

    if (kind === "FuelPickup") {
        car.send("addNitro", 28);
        log("Fuel +" + 28);
    } else {
        if (hud) hud.send("addScore", value * combo);
    }
    audio.play("sfx_pickup.ogg", 0.6);

    if (combo > 1 && hud) hud.send("showCombo", combo);
}

function endCombo() {
    if (combo > 3) log("Combo x" + combo + " ended");
    combo = 0;
}

function getCombo() { return combo; }
function getCollected() { return totalCollected; }
