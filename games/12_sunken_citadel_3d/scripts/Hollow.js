// Sunken Citadel -- Hollow.js
//
// One of the citadel's Hollows: a stone construct that walks at the knight,
// telegraphs its swing (a wind-up the player can read and dodge), strikes, then
// recovers.  The wind-up is the whole fight: an enemy that damaged you on contact
// would make the dodge pointless.
//
// Params: hp=46, speed=3.1, damage=13, reach=2.3, windup=0.55, recover=0.7,
//         sight=26.0, kind=hollow

var hp = 46;
var maxHp = 46;
var speed = 3.1;
var state = "hunt";          // hunt -> windup -> strike -> recover
var timer = 0.0;
var dead = false;
var hurtFlash = 0.0;
var birth = 0.0;
var eye = null;

function configure(h, s) {
    hp = h;
    maxHp = h;
    speed = s;
}

function start() {
    birth = time.time;
    self.tag = "Enemy";
    // Eye glow: the only bright thing on a Hollow, and it brightens on wind-up.
    eye = scene.spawn("HollowEye", self.x, 1.9, self.z);
    if (eye) eye.setParentTo(self);
    self.color = "#FFFFFFFF";
}

function update(dt) {
    if (dead) return;
    if (hurtFlash > 0.0) {
        hurtFlash -= dt;
        self.color = "#FFFFFFFF";
    }
    timer -= dt;
    var k = scene.find("Knight");
    if (!k) return;
    var d = groundDist(k);
    var c = scene.find("Citadel");
    var frozen = c ? c.send("isFrozen") : false;
    if (frozen) return;

    if (state === "hunt") {
        if (d > sight) {
            // A Hollow never gives up -- but past its sight it only lumbers, so
            // a knight who runs the length of the courtyard buys himself time.
            stepToward(k.x, k.z, speed * 0.5, dt);
            bob();
        } else if (d > reach) {
            stepToward(k.x, k.z, speed, dt);
            bob();
        } else {
            state = "windup";
            timer = windup;
            if (eye) eye.color = "#FFFF4A3A";
        }
    } else if (state === "windup") {
        self.y = 0.0 + Math.sin((windup - timer) * 18.0) * 0.05;
        if (timer <= 0.0) {
            state = "strike";
            timer = 0.16;
            strike(k, d);
        }
    } else if (state === "strike") {
        if (timer <= 0.0) {
            state = "recover";
            timer = recover;
            if (eye) eye.color = "#FFC9453B";
        }
    } else if (state === "recover") {
        if (timer <= 0.0) state = "hunt";
    }
}

function strike(k, d) {
    if (d <= reach + 0.7) {
        k.send("hurt", damage, self.x, self.z);
        audio.play("sfx_slam.ogg", 0.35);
    }
    var fx = scene.find("Fx");
    if (fx) fx.send("impact", self.x, 1.2, self.z);
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

function bob() {
    self.y = Math.abs(Math.sin(time.time * 6.0 + birth)) * 0.12;
}

function hurt(amount, fromX, fromZ) {
    if (dead) return;
    hp -= amount;
    hurtFlash = 0.22;
    self.color = "#FFFFC0C0";
    var fx = scene.find("Fx");
    if (fx) fx.send("hit", self.x, self.y + 1.4, self.z);
    // A hit interrupts the wind-up: trading blows should favour the attacker.
    if (state === "windup") { state = "recover"; timer = 0.35; }
    if (hp <= 0.0) die(fromX, fromZ);
}

function die(fromX, fromZ) {
    dead = true;
    if (eye) eye.destroy();
    // Removed first, reported second: the director counts the Hollows still
    // standing, and a corpse that has not left the scene yet would keep the
    // courtyard "occupied" and stall the wave forever.
    self.destroy();
    var c = scene.find("Citadel");
    if (c) c.send("onHollowKilled", self.name, self.x, self.z);
    var fx = scene.find("Fx");
    if (fx) fx.send("crumble", self.x, self.z);
}

function getHp() { return hp; }
function getState() { return state; }
function getKind() { return kind; }
