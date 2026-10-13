// Crypt of the Ember Sigil -- Crawler.js  (blood monster)
//
// The ranged threat.  It backs off when the warden closes, spits at range, and
// refuses to fire through masonry -- so it is a positioning problem rather than
// a damage race.  Delve keeps the projectile count bounded.
//
// Params: hp=20, speed=2.05, damage=7, range=6.4, keepAway=3.2, cooldown=1.9

var hp = 20;
var fireCd = 0;
var dead = false;
var backoff = false;

function start() { playLoop("idle"); }

function update(dt) {
    if (dead) return;
    if (fireCd > 0) fireCd -= dt;
    var w = scene.find("Warden");
    if (!w) return;
    var dist = self.distanceTo(w);

    // Preference band: inside keepAway it retreats, inside range it fires,
    // beyond range it closes.  A single "chase until in range" rule makes a
    // ranged enemy walk into swords.
    if (dist < keepAway) {
        backoff = true;
        var dx = (self.x - w.x) / Math.max(dist, 0.0001);
        var dy = (self.y - w.y) / Math.max(dist, 0.0001);
        self.vx = dx * speed; self.vy = dy * speed;
        playLoop("walk");
    } else if (dist > range) {
        backoff = false;
        var d2 = Math.max(dist, 0.0001);
        self.vx = ((w.x - self.x) / d2) * speed;
        self.vy = ((w.y - self.y) / d2) * speed;
        playLoop("walk");
    } else {
        backoff = false;
        self.vx = 0; self.vy = 0;
    }
    self.flipX = (w.x - self.x) < 0;

    if (backoff || self.animation === clipName("attack")) {
        playLoop("idle");
    } else if (self.vx === 0 && self.vy === 0) {
        playLoop("idle");
    }

    if (dist < range && fireCd <= 0 && !backoff) {
        fireCd = cooldown;
        playOnce("attack");
        var target = w;
        var me = self;
        after(0.4, function () {
            if (dead) return;
            var del = scene.find("Delve");
            if (del && !del.send("canSpawn")) return;
            var dx2 = target.x - me.x, dy2 = target.y - me.y;
            var dd = Math.max(0.0001, Math.sqrt(dx2 * dx2 + dy2 * dy2));
            var s = scene.spawn("Spit", me.x, me.y + 0.2);
            if (s) {
                s.vx = (dx2 / dd) * 6.2;
                s.vy = (dy2 / dd) * 6.2;
                s.active = true;
            }
            audio.play("sfx_spit.ogg");
        });
    }
}

function takeDamage(amount, dirX, dirY) {
    if (dead) return;
    hp -= amount;
    self.color = "#FFFFFFFF";
    var d = scene.find("Delve");
    if (d) d.send("onEnemyHit", self.worldX, self.worldY);
    if (dirX !== undefined) { self.vx = dirX * 4.5; self.vy = dirY * 4.5; }
    if (hp <= 0) {
        dead = true;
        self.play(clipName("hurt"));       // collapse; this skin has no death row
        var d2 = scene.find("Delve");
        if (d2) d2.send("onEnemyKilled", self.name, self.worldX, self.worldY);
        // Fade out through the sprite colour: the engine multiplies the tint
        // alpha into the quad, and the crawler is the one enemy that has to
        // leave the field without a death animation.
        self.vx = 0; self.vy = 0;
        after(0.25, function () { self.color = "#BFFFFFFF"; });
        after(0.55, function () { self.color = "#7FFFFFFF"; });
        after(0.85, function () { self.color = "#3FFFFFFF"; });
        after(1.2, function () { self.destroy(); });
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
