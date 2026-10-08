// Hollow Ridge -- EnemyAI3D.js
// 3D enemy AI: patrol -> investigate -> pursue -> attack -> stagger -> die.
//
// Perception is two-tiered on purpose.  A cheap distance check runs every frame
// for every enemy; the expensive line-of-sight raycast only runs when the
// distance check has already passed.  Doing it the other way round means N
// raycasts per frame for N enemies, which is the single easiest way to lose a
// frame budget on a phone.
//
// Params: skin=creature, states=idle,walk,attack,hurt,death,
//         sightRange=14, hearRange=7, attackRange=2.2, attackCooldown=1.4,
//         moveSpeed=3.2, hp=40, damage=10, patrolRadius=8

var state = "patrol";
var hp = 40;
var patrolTarget = null;
var cooldown = 0;
var stagger = 0;
var homeX = 0, homeZ = 0;
var losCache = false;
var losTimer = 0;
var dead = false;

function start() {
    homeX = self.x; homeZ = self.z;
    hp = maxHp !== undefined ? maxHp : hp;
    pickPatrolTarget();
    playClip("idle");
}

function update(dt) {
    if (dead) return;
    if (cooldown > 0) cooldown -= dt;
    if (stagger > 0) { stagger -= dt; self.vx = 0; self.vz = 0; return; }

    var hero = scene.find("Hero");
    var dist = hero ? distanceToHero(hero) : 999;

    // Line of sight is re-evaluated at 10Hz, not every frame.  At 60fps that is
    // a 6x saving on the most expensive call in this script, and a 100ms lag in
    // an enemy noticing you is imperceptible.
    losTimer -= dt;
    if (losTimer <= 0) {
        losTimer = 0.1;
        losCache = hero ? canSee(hero) : false;
    }

    if (state === "patrol")      tickPatrol(dt, hero, dist);
    else if (state === "pursue") tickPursue(dt, hero, dist);
    else if (state === "attack") tickAttack(dt, hero, dist);
}

function tickPatrol(dt, hero, dist) {
    if (!patrolTarget) pickPatrolTarget();
    moveToward(patrolTarget[0], patrolTarget[1], moveSpeed * 0.5, dt);

    if (distanceToPoint(patrolTarget[0], patrolTarget[1]) < 0.8) pickPatrolTarget();

    if (hero && dist < sightRange && losCache) { setState("pursue"); return; }
    // Hearing: a sprinting hero is audible well outside sight range.
    if (hero && dist < hearRange) { setState("pursue"); }
}

function tickPursue(dt, hero, dist) {
    if (!hero) { setState("patrol"); return; }
    if (dist > sightRange * 1.6) { setState("patrol"); return; }

    moveToward(hero.x, hero.z, moveSpeed, dt);
    faceToward(hero.x, hero.z, dt);

    if (dist < attackRange && cooldown <= 0) setState("attack");
}

function tickAttack(dt, hero, dist) {
    self.vx = 0; self.vz = 0;
    if (self.animation !== "attack") {
        playClip("attack");
        // The hit lands partway through the swing, not on the first frame:
        // giving the player a readable wind-up is what makes it dodgeable.
        after(0.22, function () {
            if (dead) return;
            var h = scene.find("Hero");
            if (h && distanceToHero(h) < attackRange * 1.35) {
                var hud = scene.find("AdventureHUD");
                if (hud) hud.send("damageHero", damage);
                var d = Math.max(distanceToHero(h), 0.0001);
                h.send("applyKnockback", (h.x - self.x) / d, 0.25, (h.z - self.z) / d, 4.0);
            }
            cooldown = attackCooldown;
        });
    }
    if (self.isAnimationFinished()) {
        setState(hero && dist < attackRange * 1.5 ? "attack" : "pursue");
    }
}

function moveToward(tx, tz, speed, dt) {
    var dx = tx - self.x, dz = tz - self.z;
    var d = Math.sqrt(dx * dx + dz * dz);
    if (d < 0.001) return;
    self.move((dx / d) * speed * dt, 0, (dz / d) * speed * dt);
    if (self.animation !== skin + "_walk") playClip("walk");
}

function faceToward(tx, tz, dt) {
    var want = Math.atan2(tx - self.x, tz - self.z) * 180 / Math.PI;
    var d = ((want - self.rotY + 540) % 360) - 180;
    var maxDelta = 10 * 60 * dt;
    self.rotY += Math.abs(d) <= maxDelta ? d : (d < 0 ? -maxDelta : maxDelta);
}

function pickPatrolTarget() {
    var a = random(0, Math.PI * 2);
    var r = random(0.3, 1.0) * patrolRadius;
    patrolTarget = [homeX + Math.cos(a) * r, homeZ + Math.sin(a) * r];
}

function canSee(hero) {
    // Ray from chest height to chest height, so the enemy does not "see"
    // through the floor or spot you over a wall from ankle level.
    var hit = scene.raycast(self.x, self.y + 1.0, self.z,
        hero.x - self.x, (hero.y + 1.0) - (self.y + 1.0), hero.z - self.z,
        sightRange);
    if (!hit) return true;
    return hit.name === "Hero";
}

function setState(next) {
    if (state === next) return;
    state = next;
    if (next === "patrol") { pickPatrolTarget(); playClip("idle"); }
    if (next === "pursue") playClip("walk");
}

function distanceToHero(hero) {
    var dx = hero.x - self.x, dz = hero.z - self.z;
    return Math.sqrt(dx * dx + dz * dz);
}
function distanceToPoint(tx, tz) {
    var dx = tx - self.x, dz = tz - self.z;
    return Math.sqrt(dx * dx + dz * dz);
}

/** Combat3D calls this when the hero's swing connects. */
function takeDamage(amount, dirX, dirZ) {
    if (dead) return;
    hp -= amount;
    stagger = 0.3;
    playClip("hurt");
    audio.play("sfx_hit.ogg");
    if (dirX !== undefined) self.send("applyKnockback", dirX, 0.15, dirZ, 3.0);
    // Being hit reveals the hero even from behind -- otherwise enemies could be
    // farmed indefinitely from out of sight.
    setState("pursue");
    if (hp <= 0) die();
}

function applyKnockback(dx, dy, dz, force) {
    self.move(dx * force * 0.1, dy * force * 0.1, dz * force * 0.1);
}

function die() {
    dead = true;
    playClip("death");
    var hud = scene.find("AdventureHUD");
    if (hud) hud.send("onEnemyKilled");
    after(1.2, function () { self.destroy(); });
}

function getState() { return state; }
function getHp() { return hp; }

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
