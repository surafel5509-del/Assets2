// Emberfall -- PlayerController.js
// 8-direction movement over a 4-direction sprite sheet.
//
// Movement is genuinely 8-way (the velocity vector is not snapped), but the
// sheet only has down/left/right/up art, so the *sprite* picks the dominant
// axis and falls back to the nearest cardinal.  That is the standard trade-off
// for top-down RPGs on a 4-direction sheet and it reads correctly in motion.
//
// Params (set on the Script component in the Inspector):
//   speed=5.2, attackRange=1.1, attackCooldown=0.42, iframeTime=0.6

var face = "down";        // current sprite direction
var moving = false;
var cooldown = 0;
var iframe = 0;
var knockX = 0, knockY = 0;

function start() {
    self.play("walk_down");
    self.stopAnimation();               // idle on the stance frame
}

function update(dt) {
    var gm = scene.find("GameManager");
    if (gm && gm.send("isDead")) { self.setVelocity(0, 0); return; }

    if (cooldown > 0) cooldown -= dt;
    if (iframe > 0) {
        iframe -= dt;
        // Flicker while invulnerable: cheap, universally understood feedback.
        self.visible = (Math.floor(time.time * 20) % 2) === 0;
    } else {
        self.visible = true;
    }

    // --- input -> 8-direction intent
    var ix = input.axisX, iy = input.axisY;
    var mag = Math.sqrt(ix * ix + iy * iy);
    if (mag > 1) { ix /= mag; iy /= mag; mag = 1; }   // no diagonal speed boost
    moving = mag > 0.15;

    // --- knockback decays into the movement velocity
    knockX *= Math.pow(0.001, dt);
    knockY *= Math.pow(0.001, dt);

    if (moving) {
        self.vx = ix * speed + knockX;
        self.vy = iy * speed + knockY;
        face = pickDirection(ix, iy);
    } else {
        self.vx = knockX;
        self.vy = knockY;
    }

    animate();

    // --- attack
    if (input.aDown && cooldown <= 0) attack();

    // --- interact with whatever we are facing
    if (input.bDown) interact();
}

/**
 * Chooses a sprite row from the movement vector.
 * Diagonals resolve to the dominant axis; a tie goes to the horizontal, which
 * keeps strafing looking intentional instead of flickering between rows.
 */
function pickDirection(x, y) {
    var ax = Math.abs(x), ay = Math.abs(y);
    if (ax < 0.2 && ay < 0.2) return face;
    if (ax >= ay) return x < 0 ? "left" : "right";
    // Screen Y grows downward in this engine, so +y is "down".
    return y < 0 ? "up" : "down";
}

function animate() {
    var want = moving ? ("walk_" + face) : ("walk_" + face);
    if (self.animation !== want || self.isAnimationFinished()) {
        self.play(want);
        if (!moving) self.stopAnimation();    // hold the stance frame
    }
    // Face the travel direction without mirroring the sheet: the pack has real
    // left AND right rows, so flipping would double-apply the turn.
    self.flipX = false;
}

function attack() {
    cooldown = attackCooldown;
    self.play("act_" + face);
    audio.play("sfx_hit.ogg");

    // Directional arc in front of the player.
    var dir = dirVector(face);
    var hx = self.worldX + dir[0] * attackRange * 0.6;
    var hy = self.worldY + dir[1] * attackRange * 0.6;

    var fx = scene.spawn("SlashFX", hx, hy);
    if (fx) { fx.burst(10); after(0.25, function () { fx.destroy(); }); }

    // Hit every enemy inside the arc.  The dot-product test is what makes it a
    // swing rather than a circle: enemies behind the player are untouched.
    var gm = scene.find("GameManager");
    var atk = gm ? gm.send("getState").attack : 10;
    var enemies = scene.findAll("Enemy");
    for (var i = 0; i < enemies.length; i++) {
        var e = enemies[i];
        var dx = e.worldX - self.worldX, dy = e.worldY - self.worldY;
        var d = Math.sqrt(dx * dx + dy * dy);
        if (d > attackRange || d < 0.0001) continue;
        var dot = (dx / d) * dir[0] + (dy / d) * dir[1];
        if (dot < 0.35) continue;                      // outside the ~138 deg arc
        e.send("takeDamage", atk, dir[0], dir[1]);
    }
}

function dirVector(f) {
    if (f === "left") return [-1, 0];
    if (f === "right") return [1, 0];
    if (f === "up") return [0, -1];
    return [0, 1];
}

function interact() {
    var npcs = scene.findAll("NPC");
    var best = null, bestD = 1.4;
    for (var i = 0; i < npcs.length; i++) {
        var d = self.distanceTo(npcs[i]);
        if (d < bestD) { bestD = d; best = npcs[i]; }
    }
    if (best) { best.send("talk"); return; }

    var chests = scene.findAll("Chest");
    for (var j = 0; j < chests.length; j++) {
        if (self.distanceTo(chests[j]) < 1.2) { chests[j].send("open"); return; }
    }
    audio.play("sfx_ui.ogg");
}

/** Called by Combat/EnemyAI through send(). */
function onHurt() {
    iframe = iframeTime;
    self.play("walk_" + face);
}

function applyKnockback(dx, dy, force) {
    knockX += dx * force;
    knockY += dy * force;
}

function facing() { return face; }
