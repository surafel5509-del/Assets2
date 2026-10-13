// Crypt of the Ember Sigil -- Ghost.js  (drowned bride)
//
// The wall-phasing hunter.  Everything about it is built around one rule: it
// ignores the level grid, so a corridor is not a place to hide.  It is slow
// enough to outrun and it never attacks from behind a wall, so the counter is
// always available -- but the fight happens where it wants, not where the
// player chose.
//
// Params: hp=26, speed=2.55, damage=12, sight=9, reach=1.05, cooldown=1.5

var hp = typeof hp === "undefined" ? 26 : hp;
var attackCd = 0;
var dead = false;
var phase = 0;
var drift = 0;

function start() { playLoop("idle"); }

function update(dt) {
    if (dead) return;
    if (attackCd > 0) attackCd -= dt;
    var w = scene.find("Warden");
    if (!w) return;
    var dist = self.distanceTo(w);

    // A slow vertical bob sells "not walking" without any extra art.
    drift += dt * 2.2;
    self.y = self.y + Math.sin(drift) * 0.0025;

    if (dist < sight) {
        phase = 1;
    } else if (dist > sight * 1.3) {
        phase = 0;
    }

    if (phase === 0) {
        self.vx = 0; self.vy = 0;
        playLoop("idle");
        return;
    }

    if (dist < reach && attackCd <= 0) {
        attackCd = cooldown;
        playOnce("attack");
        var self2 = self;
        after(0.3, function () {
            if (dead) return;
            var t = scene.find("Warden");
            if (t && self2.distanceTo(t) < reach * 1.6) t.send("hurt", damage, self2.x, self2.y);
        });
    } else if (clipName("attack") !== self.animation || self.isAnimationFinished()) {
        playLoop("walk");
    }

    if (dist > 0.6) {
        var dx = (w.x - self.x) / dist, dy = (w.y - self.y) / dist;
        self.vx = dx * speed;
        self.vy = dy * speed;
    } else {
        self.vx = 0; self.vy = 0;
    }
    self.flipX = (w.x - self.x) < 0;
}

function takeDamage(amount, dirX, dirY) {
    if (dead) return;
    hp -= amount;
    self.color = "#FFFFFFFF";
    var d = scene.find("Delve");
    if (d) d.send("onEnemyHit", self.worldX, self.worldY);
    if (dirX !== undefined) { self.vx = dirX * 4; self.vy = dirY * 4; }
    if (hp <= 0) {
        dead = true;
        playOnce("death");
        self.vx = 0; self.vy = 0;
        var d2 = scene.find("Delve");
        if (d2) d2.send("onEnemyKilled", self.name, self.worldX, self.worldY);
        after(1.4, function () { self.destroy(); });
    } else {
        playOnce("hurt");
    }
}

function getHp() { return hp; }

/** "<skin>_<clip>".  Clip names are global in the engine (AnimationSystem
 *  resolves "<name>.anim" in the project assets), so an enemy that played a bare
 *  "idle" would render the *hero's* idle sheet: the call succeeds and the art is
 *  wrong.  Namespacing every clip by skin is what makes that impossible. */
function clipName(state) { return skin + "_" + state; }
/** Play `state` only when it is not already running, so a looping clip is not
 *  restarted from frame 0 sixty times a second. */
function playLoop(state) {
    var n = clipName(state);
    if (self.animation !== n) self.play(n);
}
/** Play `state` to completion, restarting only once the previous pass ended. */
function playOnce(state) {
    var n = clipName(state);
    if (self.animation !== n || self.isAnimationFinished()) self.play(n);
}
