// Sunken Citadel -- Warden.js
//
// The boss: a stone Warden with three attacks and a real rhythm.  It picks a
// swing, telegraphs it for a beat, commits, then opens up for a counter-window --
// the same read-and-react loop as the Hollows, at a scale where getting it wrong
// costs a quarter of your health.
//
// Params: hp=420, speed=3.2, slamDamage=34, sweepDamage=22, chargeDamage=26,
//         slamReach=4.6, sweepReach=5.4, chargeReach=3.0, windup=0.85,
//         recover=1.05, sight=34.0

var hp = 420;
var maxHp = 420;
var speed = 3.2;
var state = "idle";
var timer = 2.0;
var dead = false;
var hurtFlash = 0.0;
var attack = "slam";
var chargeX = 0.0, chargeZ = 0.0;
var eye = null;
var rage = false;

function configure(h, s) {
    hp = h;
    maxHp = h;
    speed = s;
}

function start() {
    self.tag = "Enemy";
    eye = scene.spawn("HollowEye", self.x, 3.4, self.z);
    if (eye) {
        eye.setParentTo(self);
        eye.scaleX = 2.2; eye.scaleY = 2.2; eye.scaleZ = 2.2;
    }
    state = "idle";
    timer = 2.2;
}

function update(dt) {
    if (dead) return;
    if (hurtFlash > 0.0) { hurtFlash -= dt; self.color = "#FFFFFFFF"; }
    timer -= dt;
    var k = scene.find("Knight");
    if (!k) return;
    var d = groundDist(k);
    var c = scene.find("Citadel");
    if (c && c.send("isFrozen")) return;
    if (!rage && hp < maxHp * 0.45) {
        rage = true;
        speed += 1.1;
        var ct = scene.find("Citadel");
        if (ct) ct.send("say", "The Warden cracks -- it moves faster now.");
        scene.shake(0.5);
    }

    if (state === "idle") {
        if (d > sight) {
            // The Warden never comes out of the keep: it is the last fight, and
            // it waits on its plinth.
            timer = Math.max(timer, 1.0);
            return;
        }
        if (d > 6.0) {
            stepToward(k.x, k.z, speed, dt);
            self.y = Math.abs(Math.sin(time.time * 3.4)) * 0.18;
        }
        if (timer <= 0.0) {
            // Attack choice by range: slam up close, sweep at mid, charge far.
            attack = d < slamReach ? "slam" : (d < sweepReach + 3.0 ? "sweep" : "charge");
            state = "windup";
            timer = attack === "charge" ? windup + 0.25 : windup;
            if (eye) eye.color = "#FFFF6A4A";
        }
    } else if (state === "windup") {
        self.y = 0.25 + Math.sin((windup - timer) * 10.0) * 0.12;
        if (timer <= 0.0) {
            state = "strike";
            timer = attack === "charge" ? 0.55 : 0.22;
            if (attack === "charge") {
                var dx = k.x - self.x, dz = k.z - self.z;
                var dd = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
                chargeX = dx / dd; chargeZ = dz / dd;
            }
        }
    } else if (state === "strike") {
        if (attack === "charge") {
            self.setPosition(self.x + chargeX * 17.0 * dt, self.y, self.z + chargeZ * 17.0 * dt);
            if (d < chargeReach + 1.0) k.send("hurt", chargeDamage, self.x, self.z);
        } else if (attack === "slam") {
            if (timer < 0.14 && d < slamReach) k.send("hurt", slamDamage, self.x, self.z);
        } else if (timer < 0.14 && d < sweepReach) {
            k.send("hurt", sweepDamage, self.x, self.z);
        }
        if (timer <= 0.0) {
            state = "recover";
            timer = recover * (rage ? 0.7 : 1.0);
            if (eye) eye.color = "#FFC9453B";
            if (attack === "slam" || attack === "charge") {
                audio.play("sfx_slam.ogg");
                scene.shake(0.65);
                var fx = scene.find("Fx");
                if (fx) fx.send("quake", self.x, self.z);
            }
        }
    } else if (state === "recover") {
        if (timer <= 0.0) { state = "idle"; timer = rage ? 1.1 : 1.7; }
    }
    self.rotY = Math.atan2(k.x - self.x, k.z - self.z) * 180.0 / Math.PI;
}

function stepToward(x, z, sp, dt) {
    var dx = x - self.x, dz = z - self.z;
    var dd = Math.sqrt(dx * dx + dz * dz);
    if (dd < 0.05) return;
    var step = Math.min(sp * dt, dd);
    self.setPosition(self.x + (dx / dd) * step, self.y, self.z + (dz / dd) * step);
}

/** Ground distance: the courtyard is flat, so height must not shrink a gap. */
function groundDist(o) {
    var dx = o.x - self.x, dz = o.z - self.z;
    return Math.sqrt(dx * dx + dz * dz);
}

function hurt(amount, fromX, fromZ) {
    if (dead) return;
    hp -= amount;
    hurtFlash = 0.2;
    self.color = "#FFFFD0D0";
    var fx = scene.find("Fx");
    if (fx) fx.send("hit", self.x, self.y + 2.6, self.z);
    if (hp <= 0.0) {
        dead = true;
        if (eye) eye.destroy();
        state = "dead";
        var c = scene.find("Citadel");
        if (c) c.send("onBossDown");
        var fx2 = scene.find("Fx");
        if (fx2) {
            fx2.send("crumble", self.x, self.z);
            fx2.send("quake", self.x, self.z);
        }
        scene.shake(1.0);
        self.destroy();
    }
}

function getHp() { return hp; }
function getState() { return state; }
function isRaging() { return rage; }
