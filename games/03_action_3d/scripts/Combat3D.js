// Hollow Ridge -- Combat3D.js
// The single place 3D combat rules live.
//
// Attacks are a horizontal cone: a radius check plus an angular test around the
// attacker's facing.  A pure radius check would let the hero hit enemies behind
// them; a pure box would clip at the corners.  The cone is both cheap (one sqrt,
// one dot) and matches what the swing animation shows.
//
// Attach to the "Combat3D" object.

var recentHits = [];      // guards against one swing hitting twice

function start() {
    log("Combat3D online");
}

function update(dt) {
    // Expire the per-swing hit ledger.  Without this, a swing that overlaps an
    // enemy for several frames would apply damage every frame.
    for (var i = recentHits.length - 1; i >= 0; i--) {
        recentHits[i].t -= dt;
        if (recentHits[i].t <= 0) recentHits.splice(i, 1);
    }
}

/**
 * CharacterController3D calls this when the hero swings.
 *   attackerName: who swung
 *   facingDeg:    yaw in degrees
 *   radius:       reach in world units
 *   arcDeg:       total cone angle
 */
function swing(attackerName, facingDeg, radius, arcDeg) {
    var attacker = scene.find(attackerName);
    if (!attacker) return;

    var rad = facingDeg * Math.PI / 180;
    var fx = Math.sin(rad), fz = Math.cos(rad);
    var minDot = Math.cos((arcDeg * 0.5) * Math.PI / 180);

    var hud = scene.find("AdventureHUD");
    var base = hud ? (hud.send("getHeroAttack") || 10) : 10;

    var hitAny = false;
    var enemies = scene.findAll("Enemy");
    for (var i = 0; i < enemies.length; i++) {
        var e = enemies[i];
        var dx = e.x - attacker.x, dz = e.z - attacker.z;
        var d = Math.sqrt(dx * dx + dz * dz);
        if (d > radius || d < 0.0001) continue;

        // Cone test in the horizontal plane only.  Including Y would make it
        // impossible to hit an enemy standing on a slightly different height,
        // which on uneven terrain feels like the attack simply did not register.
        var dot = (dx / d) * fx + (dz / d) * fz;
        if (dot < minDot) continue;

        if (alreadyHit(attackerName, e.name)) continue;
        markHit(attackerName, e.name);

        // Damage falls off with distance: the tip of the sword does more than
        // the hilt.  It rewards spacing without any extra UI to explain it.
        var falloff = 1.0 - 0.35 * (d / radius);
        e.send("takeDamage", Math.max(1, Math.round(base * falloff)), dx / d, dz / d);
        hitAny = true;

        spawnImpact(e.x, e.y + 1.0, e.z);
    }
    if (hitAny) scene.shake(0.12);
    return hitAny;
}

function alreadyHit(attacker, victim) {
    for (var i = 0; i < recentHits.length; i++) {
        if (recentHits[i].a === attacker && recentHits[i].v === victim) return true;
    }
    return false;
}

function markHit(attacker, victim) {
    recentHits.push({ a: attacker, v: victim, t: 0.45 });
}

function spawnImpact(x, y, z) {
    var fx = scene.spawn("ImpactFX3D", x, y, z);
    if (fx) { fx.burst(12); after(0.35, function () { fx.destroy(); }); }
}

/** Area damage for the campfire "rest" interaction and environmental hazards. */
function damageInRadius(x, y, z, radius, amount, tag) {
    var all = scene.findAll(tag || "Enemy");
    var n = 0;
    for (var i = 0; i < all.length; i++) {
        var dx = all[i].x - x, dy = all[i].y - y, dz = all[i].z - z;
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > radius) continue;
        all[i].send("takeDamage", amount);
        n++;
    }
    return n;
}

function clearLedger() { recentHits.length = 0; }
