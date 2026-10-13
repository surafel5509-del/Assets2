// Sunken Citadel -- Knight.js
//
// The player: a knight with a three-step light combo, a heavy swing, a dodge
// roll with invulnerability frames, and stamina that gates all three.  Attacks
// hit through Combat.js, which owns the hit resolution so enemy scripts and the
// boss all share one damage path.
//
// Params: speed=7.6, turn=220, hp=120, lightDamage=14, heavyDamage=32,
//         lightReach=2.6, heavyReach=3.1, heavyArc=0.5, lightArc=0.75,
//         staminaMax=100, dodgeCost=22, lightCost=9, heavyCost=26,
//         staminaRegen=22, dodgeSpeed=15.5, dodgeTime=0.34, iframes=0.42

var yaw = 0.0;
var hp = typeof hp === "undefined" ? 120 : hp;
var stamina = 100.0;
var swing = 0.0;             // time left in the current swing
var combo = 0;               // 0..2 within the light chain
var comboTimer = 0.0;
var dodge = 0.0;             // time left rolling
var iframe = 0.0;
var hurtFlash = 0.0;
var dead = false;
var heavy = false;
var hits = 0;
var dodgeDirX = 0.0, dodgeDirZ = 0.0;

function start() {
    yaw = self.rotY;
}

function update(dt) {
    var d = scene.find("Citadel");
    var frozen = d ? d.send("isFrozen") : false;

    if (comboTimer > 0.0) { comboTimer -= dt; if (comboTimer <= 0.0) combo = 0; }
    if (swing > 0.0) swing -= dt;
    if (dodge > 0.0) dodge -= dt;
    if (iframe > 0.0) iframe -= dt;
    if (hurtFlash > 0.0) {
        hurtFlash -= dt;
        self.color = "#FFFFFFFF";
    }
    if (stamina < staminaMax && dodge <= 0.0) {
        stamina = Math.min(staminaMax, stamina + staminaRegen * dt);
    }

    var ix = frozen ? 0.0 : input.axisX;
    var iz = frozen ? 0.0 : input.axisY;
    var mag = Math.sqrt(ix * ix + iz * iz);
    if (mag > 1.0) { ix /= mag; iz /= mag; mag = 1.0; }

    if (dodge > 0.0) {
        // The roll keeps its committed direction: steering mid-roll would make
        // the i-frames cover any direction the player likes.
        self.setPosition(self.x + dodgeDirX * dodgeSpeed * dt, self.y,
                         self.z + dodgeDirZ * dodgeSpeed * dt);
    } else if (mag > 0.05) {
        var wantYaw = Math.atan2(ix, -iz) * 180.0 / Math.PI;
        yaw = turnToward(yaw, wantYaw, turn * dt);
        var speedScale = swing > 0.0 ? 0.28 : 1.0;
        var dx = Math.sin(yaw * Math.PI / 180.0);
        var dz = -Math.cos(yaw * Math.PI / 180.0);
        self.setPosition(self.x + dx * speed * mag * speedScale * dt, self.y,
                         self.z + dz * speed * mag * speedScale * dt);
    }

    if (!frozen && dodge <= 0.0) {
        if (actionPressed("Dodge") && stamina >= dodgeCost) {
            beginDodge(ix, iz);
        } else if (actionPressed("Attack")) {
            beginSwing(false);
        } else if (actionPressed("Heavy")) {
            beginSwing(true);
        }
    }

    // The keep's parapet is decoration; this is what actually keeps the knight
    // inside the courtyard.  The limit is set by the camera, which trails 5.8
    // units south at a fixed height with no collision of its own: 13.0 + 6.4 is
    // still well inside the wall's inner face at 20, and clear of the gate arch
    // the wall carries over the south opening.
    var lim = 13.0;
    if (self.x > lim) self.setPosition(lim, self.y, self.z);
    if (self.x < -lim) self.setPosition(-lim, self.y, self.z);
    if (self.z > lim) self.setPosition(self.x, self.y, lim);
    if (self.z < -lim) self.setPosition(self.x, self.y, -lim);
    self.rotY = yaw;

    animate();
}

function beginDodge(ix, iz) {
    dodge = dodgeTime;
    iframe = iframes;
    stamina -= dodgeCost;
    var len = Math.sqrt(ix * ix + iz * iz);
    if (len < 0.1) {
        dodgeDirX = Math.sin(yaw * Math.PI / 180.0);
        dodgeDirZ = -Math.cos(yaw * Math.PI / 180.0);
    } else {
        dodgeDirX = ix / len;
        dodgeDirZ = iz / len;
    }
    audio.play("sfx_dodge.ogg", 0.6);
    var fx = scene.find("Fx");
    if (fx) fx.send("roll", self.x, self.z);
}

function beginSwing(isHeavy) {
    // Swing assist: a swing that whiffs only because the player is half a degree
    // off the enemy reads as broken on a touch screen.  Snap to the nearest enemy
    // inside the reach before the arc is resolved -- the same soft lock-on every
    // third-person action game ships with.
    aimAssist(isHeavy ? heavyReach : lightReach);
    var cost = isHeavy ? heavyCost : lightCost;
    if (stamina < cost) {
        audio.play("sfx_hurt.ogg", 0.25);
        return;
    }
    stamina -= cost;
    heavy = isHeavy;
    if (isHeavy) {
        combo = 0;
        swing = heavyArc;
        audio.play("sfx_heavy.ogg", 0.7);
    } else {
        combo = (combo + 1) % 3;
        comboTimer = 0.9;
        swing = lightArc;
        audio.play("sfx_hit.ogg", 0.55 + combo * 0.1);
    }
    // Damage goes through Combat: one place decides what a hit is, so the boss,
    // the Hollows and the knight's own swings cannot disagree about reach.
    var c = scene.find("Combat");
    if (c) {
        var n = c.send("strike", self, isHeavy ? heavyDamage : lightDamage,
                       isHeavy ? heavyReach : lightReach, self.rotY);
        hits += n;
        if (n > 0) scene.shake(isHeavy ? 0.3 : 0.14);
        else scene.shake(0.04);
    }
    var fx = scene.find("Fx");
    if (fx) fx.send("swing", self.x, self.y + 1.1, self.z, self.rotY, isHeavy);
}

function aimAssist(reach) {
    var list = scene.findAll("Enemy");
    var best = 999.0, bestAng = yaw;
    for (var i = 0; i < list.length; i++) {
        var ex = list[i].x - self.x, ez = list[i].z - self.z;
        var d = Math.sqrt(ex * ex + ez * ez);
        if (d > reach + 2.2 || d >= best) continue;
        best = d;
        bestAng = Math.atan2(ex, -ez) * 180.0 / Math.PI;
    }
    if (best < 998.0) {
        yaw = bestAng;
        self.rotY = bestAng;
    }
}

function animate() {
    var blade = scene.find("Sword");
    if (!blade) return;
    if (dodge > 0.0) {
        blade.rotX = -70.0;
        self.color = "#C8FFFFFF";
    } else if (swing > 0.0) {
        blade.rotX = heavy ? 40.0 - (swing / heavyArc) * 130.0 : 20.0 - (swing / lightArc) * 110.0;
        self.color = "#FFFFFFFF";
    } else {
        blade.rotX = -12.0;
    }
}

function hurt(amount, fromX, fromZ) {
    if (dead) return;
    if (iframe > 0.0) return;
    hp -= amount;
    iframe = 0.55;
    hurtFlash = 0.28;
    self.color = "#FFFF9A9A";
    audio.play("sfx_hurt.ogg");
    scene.shake(0.4);
    var dx = self.x - fromX, dz = self.z - fromZ;
    var d = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
    self.setPosition(self.x + (dx / d) * 1.5, self.y, self.z + (dz / d) * 1.5);
    var c = scene.find("Citadel");
    if (c) c.send("knightHurt", amount);
    if (hp <= 0.0) {
        dead = true;
        var c2 = scene.find("Citadel");
        if (c2) c2.send("gameOver");
    }
}

function heal(amount) {
    hp = Math.min(120.0, hp + amount);
}

function actionPressed(name) { return input.actionPressed(name); }
function turnToward(from, to, maxStep) {
    var d = ((to - from + 540.0) % 360.0) - 180.0;
    if (Math.abs(d) <= maxStep) return to;
    return from + (d > 0 ? maxStep : -maxStep);
}

function getFacing() { return yaw; }
function getVx() { return 0.0; }
function getHp() { return hp; }
function getStamina() { return stamina; }
function getCombo() { return combo; }
function isDodging() { return dodge > 0.0; }
function isInvulnerable() { return iframe > 0.0; }
function getHits() { return hits; }
