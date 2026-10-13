// Emberfall Valley -- Beacon.js
//
// One of the three ridges braziers.  Lighting it costs wood and turns on the
// brazier's own particle emitter plus a point light, and it then counts as a
// warming fire for the night.  The light is created on demand: three point
// lights exist at the start of the game only if they are needed, and
// Renderer3D keeps the four nearest anyway.
//
// Params: index=1, reach=3.6, cost=3

var lit = false;
var fire = null;

function start() {
    fire = null;
}

function canInteract() { return !lit; }
function prompt() {
    if (lit) return "";
    return "Light beacon " + index + "  (" + cost + " wood, " + reach + "m)";
}

function interact() {
    if (lit) return false;
    var w = scene.find("Ranger");
    if (!w) return false;
    if (!w.send("takeResource", "wood", cost)) {
        var v0 = scene.find("Valley");
        if (v0) v0.send("say", "Not enough wood -- bring " + cost + " from the valley.");
        audio.play("sfx_deny.ogg", 0.6);
        return false;
    }
    lit = true;
    self.setEmitting(true);
    self.send("igniteLight");
    audio.play("sfx_fire.ogg");
    var v = scene.find("Valley");
    if (v) v.send("onBeaconLit", index);
    return true;
}

/** The light is a child object so it can flicker without moving the brazier. */
function igniteLight() {
    if (fire) { fire.lightIntensity = 3.0; return; }
    fire = scene.spawn("BeaconFire", self.x, self.y + 1.2, self.z);
    if (fire) fire.name = "BeaconFire_" + index;
}

function update(dt) {
    if (fire && lit) {
        fire.lightIntensity = 2.6 + Math.sin(time.time * 7.3) * 0.5;
    }
}
