// Sunken Citadel -- Combat.js
//
// One place resolves a swing, so the knight's combo, a Hollow's strike and the
// Warden's slam cannot disagree about what "in reach" means.  A strike is a cone:
// everything inside `reach` whose bearing is within `arc` degrees of the
// attacker's facing takes the damage and is knocked outward.
//
// Params: arc=62.0, knockback=1.9

var hits = 0;

function update(dt) {
}

/**
 * `strike(attacker, damage, reach, facing)` -- facing is degrees, where 0 looks
 * down -Z (the engine's convention for rotY).
 */
function strike(attacker, damage, reach, facing) {
    var targets;
    if (!attacker.send("isInvulnerable") && attacker.send("getHp") !== null &&
        scene.find("Knight") !== attacker) {
        targets = null;
    }
    // Attackers are the knight or a Hollow/Warden: the knight hits Hollows, the
    // Hollows hit the knight.  One rule, both directions.
    var side = attacker.send("getHits") !== null ? "Enemy" : "Knight";
    var list = scene.findAll(side);
    var n = 0;
    for (var i = 0; i < list.length; i++) {
        var t = list[i];
        var dx = t.x - attacker.x, dz = t.z - attacker.z;
        var d = Math.sqrt(dx * dx + dz * dz);
        if (d > reach) continue;
        var bearing = Math.atan2(dx, -dz) * 180.0 / Math.PI;
        var delta = ((bearing - facing + 540.0) % 360.0) - 180.0;
        if (Math.abs(delta) > arc * 0.5) continue;
        t.send("hurt", damage, attacker.x, attacker.z);
        // Push the target out of the arc: without it a Hollow standing inside
        // your reach takes the whole combo while never leaving the swing.
        var push = knockback / Math.max(0.4, d);
        if (t.move) t.move(dx * push, 0, dz * push);
        n++;
        hits++;
    }
    return n;
}

function getHits() { return hits; }
