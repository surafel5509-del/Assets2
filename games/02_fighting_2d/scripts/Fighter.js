// Blade & Bulwark -- Fighter.js
// Frame-data driven fighting-game character.
//
// Every attack is a timeline in 60fps frames:
//
//   |---- startup ----|---- active ----|-------- recovery --------|
//   cannot hit yet      hitbox is live   vulnerable, cannot act
//
// The hitbox only exists during `active`, which is what makes a move
// interruptible, punishable and combo-able instead of a damage aura.
//
// Hurtbox and hitbox are separate rectangles: the hurtbox is the body the
// opponent can hit, the hitbox is what this fighter's swing occupies.  Both are
// expressed in the fighter's local space and flipped with facing.
//
// Params: skin=soldier, maxHealth=100, walkSpeed=2.6, jumpForce=7.5, facing=1,
//         states=idle,walk,block,hurt,jump,attack1,attack2,death

var F = 1 / 60;                       // one frame in seconds
var state = "idle";
var frame = 0;                        // frames elapsed in the current attack
var move = null;                      // frame data for the attack in progress
var health = 100;
var hitstun = 0;
var blockstun = 0;
var hasHitThisSwing = false;          // one swing, one hit
var blocking = false;
var airbourne = false;
var vy = 0;
var dead = false;

// Local-space boxes: [x, y, halfW, halfH], +x is "in front of" the fighter.
var HURTBOX = [0, 0.0, 0.30, 0.70];
var HITBOX_LIGHT = [0.55, 0.10, 0.42, 0.26];
var HITBOX_HEAVY = [0.62, 0.05, 0.52, 0.34];

var FRAMES = {
    light: { startup: 4, active: 3, recovery: 7,  damage: 6,  hitstun: 12, blockstun: 6,  pushback: 1.2, clip: "attack1", box: HITBOX_LIGHT },
    heavy: { startup: 9, active: 4, recovery: 16, damage: 14, hitstun: 22, blockstun: 10, pushback: 3.0, clip: "attack2", box: HITBOX_HEAVY }
};

function start() {
    health = maxHealth;
    setState("idle");
}

function update(dt) {
    if (dead) { self.vx = 0; return; }

    var foe = opponent();

    if (hitstun > 0) { hitstun--; decay(dt); return; }
    if (blockstun > 0) { blockstun--; self.vx = 0; return; }

    if (state === "attack") { updateAttack(dt, foe); return; }

    if (airbourne) { updateAir(dt); }

    // --- movement
    var ix = input.axisX;
    blocking = false;

    if (foe) {
        // Holding away from the opponent blocks; holding toward walks in.
        var toward = (foe.worldX > self.worldX) ? 1 : -1;
        if (ix * toward < -0.4) { blocking = true; ix = 0; }
        // Auto-face the opponent: fighting games never let you walk backwards
        // into a cross-up by accident.
        self.flipX = toward < 0;
    }

    self.vx = ix * walkSpeed;
    if (Math.abs(ix) > 0.15 && !airbourne) setState("walk");
    else if (!airbourne) setState(blocking ? "block" : "idle");

    // --- jump
    if (input.axisY > 0.6 && !airbourne) {
        vy = jumpForce; airbourne = true; setState("jump");
    }

    // --- attacks
    if (input.aDown) beginAttack("light");
    else if (input.bDown) beginAttack("heavy");

    decay(dt);
}

function updateAttack(dt, foe) {
    frame++;
    var f = move;
    var total = f.startup + f.active + f.recovery;

    // Slight forward drift on the active frames gives attacks "reach" without
    // moving the fighter during startup (which would make spacing a lottery).
    if (frame > f.startup && frame <= f.startup + f.active) {
        self.vx = facingSign() * 0.9;
        if (foe && !hasHitThisSwing) resolveHit(foe, f);
    } else {
        self.vx = 0;
    }

    if (frame >= total) { hasHitThisSwing = false; setState("idle"); }
    decay(dt);
}

function updateAir(dt) {
    vy -= 22 * dt;                       // gravity, in world units
    self.y += vy * dt;
    if (self.y <= 0) { self.y = 0; vy = 0; airbourne = false; setState("idle"); }
}

function decay(dt) { /* reserved for per-frame friction tuning */ }

function beginAttack(kind) {
    if (state === "attack" || hitstun > 0 || blockstun > 0) return;
    move = FRAMES[kind];
    if (!move) return;
    frame = 0;
    hasHitThisSwing = false;
    state = "attack";
    playClip(move.clip);
    audio.play("sfx_swing.ogg");

    // Tell the combo tracker a swing started, so a follow-up inside the window
    // is counted as a combo rather than a fresh hit.
    var rm = scene.find("RoundManager");
    if (rm) rm.send("noteAttackStart", self.name);
}

/**
 * Hit resolution.  Both boxes are converted to world space using the fighter's
 * facing, then tested with a plain AABB overlap.
 */
function resolveHit(foe, f) {
    var hb = f.box;
    var sign = facingSign();
    var hx = self.worldX + hb[0] * sign * self.scaleX;
    var hy = self.worldY + hb[1] * self.scaleY;
    var hw = hb[2] * self.scaleX;
    var hh = hb[3] * self.scaleY;

    var ub = foe.send("getHurtboxWorld");
    if (!ub) return;
    if (!overlap(hx, hy, hw, hh, ub[0], ub[1], ub[2], ub[3])) return;

    hasHitThisSwing = true;

    var wasBlocking = foe.send("isBlocking");
    var comboScale = 1.0;
    var rm = scene.find("RoundManager");
    if (rm) comboScale = rm.send("comboScale", self.name);

    if (wasBlocking) {
        // Blocked: chip damage only, less stun, both fighters pushed apart.
        var chip = Math.max(1, Math.floor(f.damage * 0.12));
        foe.send("onBlocked", chip, f.blockstun, sign * f.pushback * 0.5);
        self.vx = -sign * f.pushback * 0.4;
        audio.play("sfx_block.ogg");
        spawnSpark(ub[0], ub[1], "#FF9AD0FF");
        if (rm) rm.send("noteHit", self.name, false);
        return;
    }

    var dmg = Math.max(1, Math.round(f.damage * comboScale));
    foe.send("onHit", dmg, f.hitstun, sign * f.pushback);
    audio.play("sfx_hit.ogg");
    scene.shake(0.18 + f.damage * 0.01);
    spawnSpark(ub[0], ub[1], "#FFFFD24A");
    if (rm) rm.send("noteHit", self.name, true);
}

function overlap(ax, ay, ahw, ahh, bx, by, bhw, bhh) {
    return Math.abs(ax - bx) < (ahw + bhw) && Math.abs(ay - by) < (ahh + bhh);
}

function spawnSpark(x, y, color) {
    var s = scene.spawn("ImpactFX", x, y);
    if (s) { s.color = color; s.burst(14); after(0.3, function () { s.destroy(); }); }
}

// -------------------------------------------------------------- incoming hits
function onHit(damage, stunFrames, pushX) {
    if (dead) return;
    health -= damage;
    hitstun = stunFrames;
    blocking = false;
    self.vx = pushX * 3.0;
    setState("hurt");

    var hp = scene.find("HealthBars");
    if (hp) hp.send("refresh");

    if (health <= 0) { health = 0; knockout(); }
}

function onBlocked(chipDamage, stunFrames, pushX) {
    health -= chipDamage;
    blockstun = stunFrames;
    self.vx = pushX * 3.0;
    setState("block");
    var hp = scene.find("HealthBars");
    if (hp) hp.send("refresh");
    if (health <= 0) { health = 0; knockout(); }
}

function knockout() {
    dead = true;
    state = "ko";
    playClip("death");
    audio.play("sfx_ko.ogg");
    scene.shake(0.6);
    var rm = scene.find("RoundManager");
    if (rm) rm.send("onKnockout", self.name);
}

// --------------------------------------------------------------------- state
function setState(next) {
    if (state === next) return;
    state = next;
    playClip(next);
}

function facingSign() { return self.flipX ? -1 : 1; }

function opponent() {
    var all = scene.findAll("Fighter");
    for (var i = 0; i < all.length; i++) if (all[i].name !== self.name) return all[i];
    return null;
}

// ------------------------------------------------------------- script API
function getHurtboxWorld() {
    var b = HURTBOX;
    return [
        self.worldX + b[0] * facingSign() * self.scaleX,
        self.worldY + b[1] * self.scaleY,
        b[2] * self.scaleX,
        b[3] * self.scaleY
    ];
}

/** Used by HitboxDebug to draw the active hitbox. */
function getHitboxWorld() {
    if (state !== "attack" || !move) return null;
    if (frame <= move.startup || frame > move.startup + move.active) return null;
    var b = move.box;
    return [
        self.worldX + b[0] * facingSign() * self.scaleX,
        self.worldY + b[1] * self.scaleY,
        b[2] * self.scaleX,
        b[3] * self.scaleY
    ];
}

function isBlocking() { return blocking; }
function getHealth() { return health; }
function getMaxHealth() { return maxHealth; }
function getState() { return state; }
function isDead() { return dead; }
function getFrame() { return frame; }

/**
 * Applies a roster entry chosen on the character-select screen.
 *
 * CharacterSelect.js sends this the moment a fighter is confirmed.  The engine
 * dispatches send() by name and silently ignores a name it cannot find, so until
 * this existed the message was dropped and every fighter kept the stats from the
 * Inspector -- the select screen appeared to work and changed nothing.
 */
function applyRoster(entry) {
    if (!entry) return;
    if (entry.health) maxHealth = entry.health;
    if (entry.walk)   walkSpeed = entry.walk;
    if (entry.jump)   jumpForce = entry.jump;
    if (entry.stem)   skin = entry.stem;
    health = maxHealth;
}

function resetRound(newFacing) {
    health = maxHealth;
    dead = false;
    hitstun = 0; blockstun = 0;
    airbourne = false; vy = 0;
    frame = 0; move = null;
    hasHitThisSwing = false;
    self.y = 0;
    self.flipX = newFacing < 0;
    setState("idle");
}

/**
 * Plays "<skin>_<state>" if that clip exists, else falls back to idle.
 *
 * Clip names come from the `states` param, which lists what this skin actually
 * ships.  Playing a clip that was never declared resolves to nothing in the
 * engine and the sprite silently draws its whole sheet, so the supported set is
 * data rather than something the script has to guess.
 */
var __states = null;
function playClip(state) {
    if (__states === null) {
        __states = {};
        // Params are split on commas by the engine, so the list uses '|'.
        var list = (typeof states === "string") ? states.split("|") : [];
        for (var i = 0; i < list.length; i++) __states[list[i].trim()] = true;
    }
    var name = skin + "_" + state;
    if (!__states[state]) name = skin + "_idle";
    self.play(name);
}
