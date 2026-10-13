// Crypt of the Ember Sigil -- Warden.js
// The player: 8-direction movement, a sword arc, a carried torch and a heal
// charge on a cooldown.
//
// Params: speed=4.6, hp=100, damage=12, swingRange=1.25, swingArc=0.42,
//         swingCooldown=0.36, iframes=0.7, regenDelay=8, regenRate=2.5

var facing = "down";
var swing = 0;                 // seconds left in the current swing animation
var cooldown = 0;
var iframe = 0;
var hurtTimer = 0;
var sinceHurt = 99;            // drives out-of-combat regeneration
var killCount = 0;
var dead = false;

function start() {
    playLoop("idle");
}

function update(dt) {
    var d = scene.find("Delve");
    if (d && d.send("isOver")) { self.vx = 0; self.vy = 0; return; }

    if (cooldown > 0) cooldown -= dt;
    if (swing > 0) swing -= dt;
    if (hurtTimer > 0) hurtTimer -= dt;

    if (iframe > 0) {
        iframe -= dt;
        self.visible = (Math.floor(time.time * 24) % 2) === 0;
    } else {
        self.visible = true;
    }

    // --- regeneration.  A crawler with no healing at all is a one-way trip; a
    // crawler with constant healing has no tension.  The delay is the mechanic.
    sinceHurt += dt;
    if (sinceHurt > regenDelay) {
        var d2 = scene.find("Delve");
        if (d2) d2.send("heal", regenRate * dt);
    }

    var ix = input.axisX, iy = input.axisY;
    var mag = Math.sqrt(ix * ix + iy * iy);
    if (mag > 1) { ix /= mag; iy /= mag; mag = 1; }

    if (swing <= 0) {
        self.vx = ix * speed;
        self.vy = iy * speed;
        if (mag > 0.15) facing = pickDirection(ix, iy);
    } else {
        // Committing to a swing: the warden slows to half speed and keeps facing.
        self.vx = ix * speed * 0.45;
        self.vy = iy * speed * 0.45;
    }
    animate(mag);

    if (input.aDown && cooldown <= 0) beginSwing();
    // Hold the torch out in front, so the pool of light leads the swing.
    var torch = scene.find("WardenTorch");
    if (torch) {
        var dir = dirVector(facing);
        torch.setPosition(self.worldX + dir[0] * 1.1, self.worldY + dir[1] * 1.1 + 0.15, 0);
    }
}

function pickDirection(x, y) {
    var ax = Math.abs(x), ay = Math.abs(y);
    if (ax < 0.2 && ay < 0.2) return facing;
    if (ax >= ay) return x < 0 ? "left" : "right";
    return y < 0 ? "up" : "down";
}

function dirVector(f) {
    if (f === "left") return [-1, 0];
    if (f === "right") return [1, 0];
    if (f === "up") return [0, -1];
    return [0, 1];
}

function animate(mag) {
    if (dead) return;
    if (swing > 0) { playOnce("attack"); return; }
    // The knight's sheet has no hurt row, so damage reads as a red flash plus
    // the knockback and screen shake, rather than as a missing animation.
    if (hurtTimer > 0) { return; }
    if (mag > 0.15) {
        playLoop("walk");
        self.setAnimSpeed(1.15);
        // The knight sheet faces right; the left-facing pose is the mirror.
        self.flipX = facing === "left";
    } else {
        self.setAnimSpeed(1.0);
        playLoop("idle");
    }
}

function clipName(state) { return "hero_" + state; }
function playLoop(state) {
    var n = clipName(state);
    if (self.animation !== n) self.play(n);
}
function playOnce(state) {
    var n = clipName(state);
    if (self.animation !== n || self.isAnimationFinished()) self.play(n);
}

function beginSwing() {
    cooldown = swingCooldown;
    swing = 0.26;
    playOnce("attack");
    audio.play("sfx_swing.ogg");

    var dir = dirVector(facing);
    var hx = self.worldX + dir[0] * swingRange * 0.7;
    var hy = self.worldY + dir[1] * swingRange * 0.7;
    var fx = scene.spawn("SlashFx", hx, hy);
    if (fx) { fx.burst(12); after(0.22, function () { fx.destroy(); }); }

    // Dot-product cone, so enemies behind the warden are untouched.  A plain
    // radius test would let the player farm a crowd by spinning on the spot.
    var enemies = scene.findAll("Enemy");
    var hits = 0;
    for (var i = 0; i < enemies.length; i++) {
        var e = enemies[i];
        var dx = e.worldX - self.worldX, dy = e.worldY - self.worldY;
        var dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > swingRange || dist < 0.0001) continue;
        if ((dx / dist) * dir[0] + (dy / dist) * dir[1] < swingArc) continue;
        e.send("takeDamage", damage, dir[0], dir[1]);
        hits++;
    }
    if (hits > 2) scene.shake(0.12);
}

function hurt(amount, fromX, fromY) {
    if (iframe > 0) return;
    iframe = iframes;
    hurtTimer = 0.3;
    sinceHurt = 0;
    self.color = "#FFFF8080";
    after(0.18, function () { self.color = "#FFFFFFFF"; });
    audio.play("sfx_hurt.ogg");
    var dx = self.worldX - fromX, dy = self.worldY - fromY;
    var d = Math.max(0.0001, Math.sqrt(dx * dx + dy * dy));
    // Knockback is applied through the velocity, not the transform: the
    // physics step owns the position, so writing x/y directly gets overwritten.
    self.vx = (dx / d) * 7;
    self.vy = (dy / d) * 7;
    scene.shake(0.25);
    var del = scene.find("Delve");
    if (del) del.send("hurtWarden", amount);
}

function onEnemyKilled() { killCount++; }

/** Called by the director when the warden's health reaches zero. */
function onDeath() {
    if (dead) return;
    dead = true;
    self.vx = 0; self.vy = 0;
    self.color = "#FFFFFFFF";
    self.play(clipName("death"));
}

function getHp() { return hp; }
function getKills() { return killCount; }
function getFacing() { return facing; }

/** "<state>" resolved against this knight's own sheet.  Every clip in the
 *  project is namespaced by skin (see the monster scripts): there is one global
 *  clip namespace, so "idle" alone would be whatever sheet was declared first. */
function clipName(state) { return "hero_" + state; }
function playLoop(state) {
    var n = clipName(state);
    if (self.animation !== n) self.play(n);
}
function playOnce(state) {
    var n = clipName(state);
    if (self.animation !== n || self.isAnimationFinished()) self.play(n);
}
