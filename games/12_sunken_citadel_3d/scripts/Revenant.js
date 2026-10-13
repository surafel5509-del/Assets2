// Sunken Citadel -- Revenant.js
//
// The third wave's enemy: faster than a Hollow, longer reach, and it circles
// before it commits, so a player who only backs away gets punished.  Same three
// states, different numbers and a strafe.
//
// Params: hp=74, speed=3.9, damage=17, reach=2.7, windup=0.42, recover=0.55,
//         sight=30.0, strafeTime=1.4

var hp = typeof hp === "undefined" ? 74 : hp;
var speed = typeof speed === "undefined" ? 3.9 : speed;
var state = "hunt";
var timer = 0.0;
var strafe = 1.0;
var strafeTimer = 0.0;
var dead = false;
var hurtFlash = 0.0;
var birth = 0.0;
var eye = null;

function configure(h, s) {
    hp = h;
    speed = s;
}

function start() {
    birth = time.time;
    self.tag = "Enemy";
    strafeTimer = strafeTime;
    eye = scene.spawn("HollowEye", self.x, 2.1, self.z);
    if (eye) eye.setParentTo(self);
}

function update(dt) {
    if (dead) return;
    if (hurtFlash > 0.0) { hurtFlash -= dt; self.color = "#FFFFFFFF"; }
    timer -= dt;
    strafeTimer -= dt;
    var k = scene.find("Knight");
    if (!k) return;
    var d = groundDist(k);
    var c = scene.find("Citadel");
    if (c && c.send("isFrozen")) return;

    if (strafeTimer <= 0.0) { strafe = -strafe; strafeTimer = strafeTime; }

    if (state === "hunt") {
        if (d > sight) {
            stepToward(k.x, k.z, speed * 0.6, dt);
            bob();
            return;
        }
        if (d > reach) {
            if (d < reach + 3.2) {
                // Circle: close faster than a Hollow and refuse to stand still.
                var ax = k.x - self.x, az = k.z - self.z;
                var ad = Math.max(0.001, Math.sqrt(ax * ax + az * az));
                var px = -az / ad * strafe, pz = ax / ad * strafe;
                stepToward(k.x + px * 2.6, k.z + pz * 2.6, speed, dt);
            } else {
                stepToward(k.x, k.z, speed, dt);
            }
            bob();
        } else {
            state = "windup";
            timer = windup;
            if (eye) eye.color = "#FFFF5A48";
        }
    } else if (state === "windup") {
        self.y = Math.sin((windup - timer) * 22.0) * 0.06;
        if (timer <= 0.0) { state = "strike"; timer = 0.14; strike(k, d); }
    } else if (state === "strike") {
        if (timer <= 0.0) { state = "recover"; timer = recover; if (eye) eye.color = "#FFC9453B"; }
    } else if (state === "recover") {
        if (timer <= 0.0) state = "hunt";
    }
}

function bob() {
    self.y = Math.abs(Math.sin(time.time * 7.0 + birth)) * 0.16;
}

function strike(k, d) {
    if (d <= reach + 0.8) k.send("hurt", damage, self.x, self.z);
    var fx = scene.find("Fx");
    if (fx) fx.send("impact", self.x, 1.4, self.z);
}

function stepToward(x, z, sp, dt) {
    var dx = x - self.x, dz = z - self.z;
    var dd = Math.sqrt(dx * dx + dz * dz);
    if (dd < 0.05) return;
    var step = Math.min(sp * dt, dd);
    self.setPosition(self.x + (dx / dd) * step, self.y, self.z + (dz / dd) * step);
    self.rotY = Math.atan2(dx, dz) * 180.0 / Math.PI;
}

/** Ground distance: the courtyard is flat, so height must not shrink a gap. */
function groundDist(o) {
    var dx = o.x - self.x, dz = o.z - self.z;
    return Math.sqrt(dx * dx + dz * dz);
}

function hurt(amount, fromX, fromZ) {
    if (dead) return;
    hp -= amount;
    hurtFlash = 0.22;
    self.color = "#FFFFC0C0";
    var fx = scene.find("Fx");
    if (fx) fx.send("hit", self.x, self.y + 1.6, self.z);
    if (state === "windup") { state = "recover"; timer = 0.3; }
    if (hp <= 0.0) {
        dead = true;
        if (eye) eye.destroy();
        // Same order as the Hollow: gone from the scene, then reported.
        self.destroy();
        var c = scene.find("Citadel");
        if (c) c.send("onHollowKilled", self.name, self.x, self.z);
        var fx2 = scene.find("Fx");
        if (fx2) fx2.send("crumble", self.x, self.z);
    }
}

function getHp() { return hp; }
function getState() { return state; }
