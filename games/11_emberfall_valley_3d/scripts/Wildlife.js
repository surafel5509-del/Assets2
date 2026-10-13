// Emberfall Valley -- Wildlife.js
//
// Herd animals assembled from primitives: graze, wander, and bolt if the ranger
// gets close.  Three states, no combat -- this game is about surviving the
// valley, not clearing it.
//
// Params: kind=deer, speed=2.1, fleeSpeed=7.6, sight=9.0, homeRadius=16.0

var state = "graze";
var timer = 2.0;
var homeX = 0.0, homeZ = 0.0;
var goalX = 0.0, goalZ = 0.0;
var bobT = 0.0;

function start() {
    homeX = self.x;
    homeZ = self.z;
    goalX = self.x;
    goalZ = self.z;
    timer = random(1.0, 4.0);
}

/** Ground distance between two objects: height must not shrink a walk. */
function groundDist(a, b) {
    var dx = a.x - b.x, dz = a.z - b.z;
    return Math.sqrt(dx * dx + dz * dz);
}

function update(dt) {
    timer -= dt;
    bobT += dt;
    var w = scene.find("Ranger");
    var d = w ? groundDist(self, w) : 999.0;

    if (d < sight) state = "flee";
    else if (state === "flee" && d > sight * 1.6) { state = "graze"; timer = 1.5; }

    if (state === "graze") {
        if (timer <= 0.0) {
            timer = random(2.5, 7.0);
            var a = random(0.0, 360.0);
            var r = random(1.5, homeRadius * 0.5);
            goalX = homeX + Math.sin(a * Math.PI / 180.0) * r;
            goalZ = homeZ + Math.cos(a * Math.PI / 180.0) * r;
        }
        self.y = (kind === "deer" ? 1.15 : 0.78) + Math.sin(bobT * 1.6) * 0.03;
        stepToward(goalX, goalZ, speed * 0.6, dt);
    } else if (state === "flee" && w) {
        var dx = self.x - w.x, dz = self.z - w.z;
        var dist = Math.sqrt(dx * dx + dz * dz) || 1.0;
        goalX = self.x + (dx / dist) * 12.0;
        goalZ = self.z + (dz / dist) * 12.0;
        stepToward(goalX, goalZ, fleeSpeed, dt);
        self.y = (kind === "deer" ? 1.15 : 0.78) + Math.abs(Math.sin(bobT * 9.0)) * 0.12;
    }

    // Keep the herd inside the valley and out of the river.
    if (self.z > 47.0) self.z = 47.0;
    if (self.z < -47.0) self.z = -47.0;
}

function stepToward(x, z, speed, dt) {
    var dx = x - self.x, dz = z - self.z;
    var d = Math.sqrt(dx * dx + dz * dz);
    if (d < 0.35) return;
    var step = Math.min(speed * dt, d);
    self.setPosition(self.x + (dx / d) * step, self.y, self.z + (dz / d) * step);
    self.rotY = Math.atan2(dx, dz) * 180.0 / Math.PI;
}

function getState() { return state; }
