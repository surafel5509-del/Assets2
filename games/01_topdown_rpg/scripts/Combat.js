// Emberfall -- Combat.js
// Shared combat helpers: hitbox construction, resolution and the damage number
// popups.  Kept out of PlayerController/EnemyAI so both sides agree on the
// rules (a disagreement between attacker and defender is how dupe-damage and
// invincible-enemy bugs get shipped).
//
// Attach to the "Combat" manager object.

var popups = [];

function start() {
    log("Combat online");
}

function update(dt) {
    // Damage numbers drift up and fade, then clean themselves up.
    for (var i = popups.length - 1; i >= 0; i--) {
        var p = popups[i];
        p.life -= dt;
        if (p.life <= 0) {
            if (p.obj) p.obj.destroy();
            popups.splice(i, 1);
            continue;
        }
        if (p.obj) {
            p.obj.y += dt * 1.4;
            // Fade by scaling the sprite down in the last third of its life:
            // this engine's sprite colour has no per-instance alpha channel, so
            // shrink + rise is the readable substitute.
            if (p.life < 0.35) p.obj.scaleX = p.obj.scaleY = p.life / 0.35;
        }
    }
}

/**
 * AABB overlap test in world space.  Both boxes are centred on their object,
 * half-extents scaled by the object's transform.
 */
function boxesOverlap(ax, ay, ahw, ahh, bx, by, bhw, bhh) {
    return Math.abs(ax - bx) < (ahw + bhw) && Math.abs(ay - by) < (ahh + bhh);
}

/**
 * True when `target` is inside a directional arc originating at (ox, oy).
 *   facing: unit vector
 *   range:  radius
 *   minDot: cosine of the half-angle (0.35 ~ a 138 degree swing)
 */
function inArc(ox, oy, facingX, facingY, range, minDot, tx, ty) {
    var dx = tx - ox, dy = ty - oy;
    var d = Math.sqrt(dx * dx + dy * dy);
    if (d > range || d < 0.0001) return false;
    return ((dx / d) * facingX + (dy / d) * facingY) >= minDot;
}

/**
 * Applies damage and shows the number.  Centralised so crits, defence and the
 * popup all stay consistent between player and enemy attacks.
 */
function dealDamage(victim, amount, isCrit) {
    var final = Math.max(1, Math.round(amount));
    if (victim && victim.send) victim.send("takeDamage", final);
    spawnPopup(victim ? victim.worldX : 0, victim ? victim.worldY : 0, final, isCrit);
    return final;
}

function spawnPopup(x, y, amount, isCrit) {
    var obj = scene.spawn("DamageNumber", x + random(-0.15, 0.15), y + 0.3);
    if (!obj) return;
    obj.text = (isCrit ? "!" : "") + amount;
    obj.color = isCrit ? "#FFFFD24A" : "#FFFFFFFF";
    popups.push({ obj: obj, life: 0.85 });
}

/** Knockback that respects the target's mass, if it has one. */
function knockback(target, dirX, dirY, force) {
    if (!target || !target.send) return;
    target.send("applyKnockback", dirX, dirY, force);
}

function clearPopups() {
    for (var i = 0; i < popups.length; i++) if (popups[i].obj) popups[i].obj.destroy();
    popups.length = 0;
}

function onStop() { clearPopups(); }
