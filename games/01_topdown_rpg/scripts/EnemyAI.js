// Emberfall -- EnemyAI.js
// Finite-state enemy: patrol -> chase -> attack -> hurt -> die.
//
// One script drives every enemy; the difference between a skeleton and a
// vampire is the params on its Script component:
//   skeleton: hp=30, speed=1.6, sight=4.5, damage=8,  xp=12, gold=5
//   vampire:  hp=55, speed=2.4, sight=6.0, damage=14, xp=25, gold=12
//
// Sprites are swapped per state rather than blended: the pack ships separate
// strips per action (idle / movement / attack / take_damage / death), so a
// state change means a new texture plus a new clip.

var state = "patrol";
var hp = 30;
var hitFlash = 0;
var attackTimer = 0;
var patrolTimer = 0;
var patrolDirX = 0, patrolDirY = 0;
var homeX = 0, homeY = 0;
var dying = false;

// Which texture strip belongs to which state.  Names come from game.json.
var SKIN = {
    skeleton: { idle: "skeleton_idle.png", walk: "skeleton_walk.png",
                attack: "skeleton_attack.png", hurt: "skeleton_hurt.png",
                death: "skeleton_death.png" },
    vampire:  { idle: "vampire_idle.png", walk: "vampire_walk.png",
                attack: "vampire_attack.png", hurt: null,
                death: "vampire_death.png" }
};

function start() {
    homeX = self.x; homeY = self.y;
    hp = maxHp === undefined ? hp : maxHp;
    pickPatrolDirection();
    setSkin("idle");
}

function update(dt) {
    if (dying) return;
    if (hitFlash > 0) {
        hitFlash -= dt;
        self.color = hitFlash > 0 ? "#FFFF8080" : "#FFFFFFFF";
    }

    var player = scene.find("Player");
    if (!player) { setState("patrol"); }
    var dist = player ? self.distanceTo(player) : 999;

    if (state === "patrol")  updatePatrol(dt, player, dist);
    else if (state === "chase")  updateChase(dt, player, dist);
    else if (state === "attack") updateAttack(dt, player, dist);
}

// ------------------------------------------------------------------- states
function updatePatrol(dt, player, dist) {
    patrolTimer -= dt;
    if (patrolTimer <= 0) pickPatrolDirection();

    // Wander, but never stray far from the spawn point -- otherwise enemies
    // slowly leak out of their room and the level empties itself.
    var dxh = homeX - self.x, dyh = homeY - self.y;
    var homeDist = Math.sqrt(dxh * dxh + dyh * dyh);
    if (homeDist > 4) { patrolDirX = dxh / homeDist; patrolDirY = dyh / homeDist; }

    self.vx = patrolDirX * speed * 0.45;
    self.vy = patrolDirY * speed * 0.45;
    faceVelocity();

    if (player && dist < sight) setState("chase");
}

function updateChase(dt, player, dist) {
    if (!player) { setState("patrol"); return; }
    if (dist > sight * 1.5) { setState("patrol"); return; }

    var dx = player.worldX - self.worldX, dy = player.worldY - self.worldY;
    var d = Math.max(dist, 0.0001);
    self.vx = (dx / d) * speed;
    self.vy = (dy / d) * speed;
    faceVelocity();

    if (dist < attackRange) setState("attack");
}

function updateAttack(dt, player, dist) {
    self.vx = 0; self.vy = 0;
    attackTimer -= dt;

    if (self.isAnimationFinished()) {
        // The swing connects once, at the end of the wind-up.
        if (player && dist < attackRange * 1.3) {
            var gm = scene.find("GameManager");
            if (gm) gm.send("damagePlayer", damage);
            var d = Math.max(dist, 0.0001);
            player.send("applyKnockback",
                (player.worldX - self.worldX) / d,
                (player.worldY - self.worldY) / d, 4.5);
        }
        attackTimer = attackCooldown;
        setState(dist < attackRange * 1.4 ? "attack" : "chase");
    }
}

function setState(next) {
    if (state === next) return;
    state = next;
    if (next === "chase") setSkin("walk");
    else if (next === "attack") { setSkin("attack"); attackTimer = attackCooldown; }
    else if (next === "patrol") { setSkin("idle"); }
}

function pickPatrolDirection() {
    var a = random(0, Math.PI * 2);
    patrolDirX = Math.cos(a); patrolDirY = Math.sin(a);
    patrolTimer = random(1.2, 3.0);
}

function faceVelocity() {
    // The enemy strips are drawn facing left, so an enemy walking right is
    // mirrored.  Determining this from vx (not from the player) means enemies
    // keep looking where they walk while retreating.
    if (self.vx > 0.05) self.flipX = true;
    else if (self.vx < -0.05) self.flipX = false;
}

function setSkin(kind) {
    var tex = SKIN[skin] ? SKIN[skin][kind] : null;
    if (!tex) return;                      // this skin has no strip for that state
    self.setTexture(tex);
    // Each strip is its own declared clip, named after the sheet: skeleton_walk,
    // vampire_idle, and so on.  Playing the bare state name ("walk") resolved to
    // nothing and the sprite drew its whole sheet.
    self.play(tex.replace(/\.png$/, ""));
}

// ------------------------------------------------------------------ combat
/** Player attacks call: enemy.send("takeDamage", amount, dirX, dirY) */
function takeDamage(amount, dirX, dirY) {
    if (dying) return;
    hp -= amount;
    hitFlash = 0.18;
    self.color = "#FFFF8080";
    audio.play("sfx_hit.ogg");

    var fx = scene.spawn("HitFX", self.worldX, self.worldY);
    if (fx) { fx.burst(8); after(0.3, function () { fx.destroy(); }); }

    if (dirX !== undefined) self.send("applyKnockback", dirX, dirY, 3.0);

    if (hp <= 0) { die(); return; }
    // Getting hit interrupts whatever the enemy was doing.
    var prev = state;
    setSkin("hurt");
    after(0.28, function () { if (!dying) setState(prev === "attack" ? "chase" : prev); });
}

function applyKnockback(dx, dy, force) {
    self.vx += (dx || 0) * force;
    self.vy += (dy || 0) * force;
}

function die() {
    dying = true;
    setState("die");
    setSkin("death");
    self.vx = 0; self.vy = 0;
    var gm = scene.find("GameManager");
    if (gm) { gm.send("gainXp", xp); gm.send("addGold", gold); }

    // Enemies drop what they carry; the drop table is weighted, not uniform.
    var roll = random(0, 1);
    if (roll < 0.35) scene.spawn("PickupPotion", self.worldX, self.worldY);
    else if (roll < 0.55) scene.spawn("PickupGold", self.worldX, self.worldY);

    var puff = scene.spawn("DeathFX", self.worldX, self.worldY);
    if (puff) { puff.burst(20); after(0.6, function () { puff.destroy(); }); }

    // The death strip is long (17 frames at 16fps ~ 1s); wait for it.
    after(1.1, function () { self.destroy(); });
}

function getState() { return state; }
function getHp() { return hp; }
