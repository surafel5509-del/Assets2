// Emberfall Valley -- Ranger.js
//
// The player character: a primitive-built body, third-person movement relative
// to the camera, a stamina-limited sprint, a hop, and one context action.
//
// The engine has a 3D physics solver (PhysicsWorld3D), but this game does not
// use it for the ranger: the terrain is a flat plane with a known boundary, so
// the script owns gravity and clamping.  That keeps the game identical in the
// engine and in the offline harness -- which is where it is tested -- instead of
// depending on a solver the harness does not run.
//
// Params: speed=7.2, sprint=11.4, turn=14.0, hop=5.2, gravity=-19.5,
//         staminaMax=100, staminaDrain=17, staminaRegen=13, reach=3.2

var yaw = 180.0;            // facing, degrees; 180 = looking down -Z
var vy = 0.0;               // vertical speed, owned by this script
var y = 0.0;
var grounded = true;
var stamina = 100.0;
var sprinting = false;
var bob = 0.0;
var stepTimer = 0.0;
var carrying = { wood: 0, stone: 0, berry: 0 };
var near = null;            // the interactable the ranger is standing next to
var prompt = "";
var hops = 0, picked = 0, steps = 0;

function start() {
    yaw = self.rotY;
    y = self.y;
}

function update(dt) {
    var v = scene.find("Valley");
    var frozen = v ? v.send("isFrozen") : false;

    if (stamina < staminaMax) {
        var regen = staminaRegen * (sprinting ? 0.0 : 1.0);
        if (!sprinting) stamina = Math.min(staminaMax, stamina + regen * dt);
    }

    var ix = frozen ? 0.0 : input.axisX;
    var iz = frozen ? 0.0 : input.axisY;
    var mag = Math.sqrt(ix * ix + iz * iz);
    if (mag > 1.0) { ix /= mag; iz /= mag; mag = 1.0; }

    if (mag > 0.05) {
        // Move in the *world* frame the camera looks along: the camera trails the
        // ranger at +Z, so screen-up is -Z.
        var wantYaw = Math.atan2(ix, -iz) * 180.0 / Math.PI;
        yaw = turnToward(yaw, wantYaw, turn * dt);
        sprinting = stamina > 8.0 && mag > 0.75 && actionHeld("Sprint");
        var sp = sprinting ? sprint : speed;
        var dx = Math.sin(yaw * Math.PI / 180.0);
        var dz = -Math.cos(yaw * Math.PI / 180.0);
        self.setPosition(self.x + dx * sp * mag * dt, y + bob, self.z + dz * sp * mag * dt);
        if (sprinting) stamina = Math.max(0.0, stamina - staminaDrain * dt);
        stepTimer -= dt * (sprinting ? 1.6 : 1.0);
        if (stepTimer <= 0.0 && grounded) {
            stepTimer = 0.34;
            steps++;
            audio.play("sfx_step.ogg", 0.35);
            var d = scene.find("Fx");
            if (d) d.send("dust", self.x, self.z);
        }
    } else {
        sprinting = false;
    }

    // Hop: a script-owned arc, landed when it comes back down.
    if (grounded && actionPressed("Jump") && !frozen) {
        vy = hop;
        grounded = false;
        hops++;
    }
    if (!grounded) {
        vy += gravity * dt;
        y += vy * dt;
        if (y <= 0.0) { y = 0.0; vy = 0.0; grounded = true;
            var fx = scene.find("Fx");
            if (fx) fx.send("landing", self.x, self.z);
        }
    }

    // Stay inside the valley.  The cliff ring is decoration; this is what
    // actually stops you walking into it -- and the limit has to leave room for
    // the camera, which trails 9.4 units behind with no collision of its own.
    var lim = 39.0;
    if (self.x > lim) self.setPosition(lim, self.y, self.z);
    if (self.x < -lim) self.setPosition(-lim, self.y, self.z);
    if (self.z > lim) self.setPosition(self.x, self.y, lim);
    if (self.z < -lim) self.setPosition(self.x, self.y, -lim);

    self.rotY = yaw;
    bob = grounded && mag > 0.05 ? Math.sin(time.time * 12.0) * 0.06 : 0.0;
    self.y = y + bob;

    scanForInteractable();
    if (prompt !== "" && actionPressed("Interact") && near) {
        var ok = near.send("interact", 1);
        if (ok) picked++;
    }
}

function actionHeld(name) { return input.action(name); }
function actionPressed(name) { return input.actionPressed(name); }

/** Shortest-arc turn, so the ranger never spins the long way round. */
function turnToward(from, to, maxStep) {
    var d = ((to - from + 540.0) % 360.0) - 180.0;
    if (Math.abs(d) <= maxStep) return to;
    return from + (d > 0 ? maxStep : -maxStep);
}


/** Ground distance between two objects: height must not shrink a walk. */
function groundDist(a, b) {
    var dx = a.x - b.x, dz = a.z - b.z;
    return Math.sqrt(dx * dx + dz * dz);
}

/** The nearest thing that answers "interact", within reach. */
function scanForInteractable() {
    near = null;
    prompt = "";
    var best = 1e9;
    var groups = [["Forage", reach + 0.6], ["Beacon", reach], ["Camp", reach + 0.4]];
    for (var g = 0; g < groups.length; g++) {
        var list = scene.findAll(groups[g][0]);
        for (var i = 0; i < list.length; i++) {
            var o = list[i];
            // Ground distance: the valley is 3D, so a node up a slope must not be
            // "in reach" because a 2D check measured x against y.
            var d = groundDist(self, o);
            if (d > groups[g][1] || d >= best) continue;
            if (!o.send("canInteract", null)) continue;
            best = d;
            near = o;
            prompt = o.send("prompt", null) || "";
        }
    }
    var v = scene.find("Valley");
    if (v) v.send("setPrompt", prompt);
}

/** Cold: called by the director when night catches the ranger away from fire. */
function chill(amount) {
    stamina = Math.max(0.0, stamina - amount);
    self.color = "#FFB0C4E8";
    after(0.3, function () { self.color = "#FFFFFFFF"; });
    if (stamina <= 0.0) {
        var v = scene.find("Valley");
        if (v) v.send("gameOver");
    }
}

function addResource(kind, amount) {
    carrying[kind] = (carrying[kind] || 0) + amount;
}
function takeResource(kind, amount) {
    if ((carrying[kind] || 0) < amount) return false;
    carrying[kind] -= amount;
    return true;
}
function getStamina() { return stamina; }
function getStaminaMax() { return staminaMax; }
function getCarrying() { return carrying; }
function getEmitted() { return vy; }
function getGroundY() { return y; }
function isGrounded() { return grounded; }
function getSteps() { return steps; }
