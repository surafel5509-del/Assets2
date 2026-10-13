// Crypt of the Ember Sigil -- Grunt.js  (skeletal swordsman)
//
// A three-state melee AI: patrol between two anchors, chase on line of sight,
// attack in range.  Line of sight uses the dungeon grid through Delve.isSolid
// rather than a physics raycast, because the level *is* a grid and the grid
// answer is exact.
//
// Params: hp=34, speed=1.85, damage=9, sight=7.5, reach=1.15, cooldown=1.15,
//         skin=skeleton

var state = "patrol";
var hp = 34;
var attackCd = 0;
var homeX = 0, homeY = 0;
var patrolT = 0;
var dead = false;
var hurtFlash = 0;
var patrolGoalX = 0, patrolGoalY = 0;

function start() {
    homeX = self.x; homeY = self.y;
    playLoop("idle");
}

function update(dt) {
    if (dead) return;
    if (attackCd > 0) attackCd -= dt;
    if (hurtFlash > 0) {
        hurtFlash -= dt;
        self.color = "#FFFFFFFF";
    }

    var w = scene.find("Warden");
    if (!w) return;
    var dist = self.distanceTo(w);
    var sees = dist < sight && hasLineOfSight(w);

    if (state === "patrol") {
        patrolT -= dt;
        if (patrolT <= 0) {
            patrolT = 1.6;
            var a = random(0, Math.PI * 2);
            var r = random(0.8, 2.6);
            patrolGoalX = homeX + Math.cos(a) * r;
            patrolGoalY = homeY + Math.sin(a) * r;
        }
        stepToward(patrolGoalX, patrolGoalY, speed * 0.45, dt);
        playLoop("walk");
        if (sees) setState("chase");
    } else if (state === "chase") {
        if (dist < reach && attackCd <= 0) { setState("attack"); return; }
        if (dist > sight * 1.5) { setState("patrol"); return; }
        stepToward(w.x, w.y, speed, dt);
        playLoop("walk");
    } else if (state === "attack") {
        self.vx = 0; self.vy = 0;
        playOnce("attack");
        if (attackCd <= 0) {
            attackCd = cooldown;
            // The hit lands on the animation's contact frame, not on frame 0.
            after(0.24, function () {
                if (dead) return;
                var t = scene.find("Warden");
                if (t && self.distanceTo(t) < reach * 1.5) t.send("hurt", damage, self.x, self.y);
            });
        }
        if (dist > reach * 1.4) setState("chase");
    }
}

function setState(next) {
    if (state === next) return;
    state = next;
    if (next === "chase") playLoop("walk");
    if (next === "attack") { attackCd = 0.35; playOnce("attack"); }
}

function stepToward(tx, ty, sp, dt) {
    var dx = tx - self.x, dy = ty - self.y;
    var d = Math.sqrt(dx * dx + dy * dy);
    if (d < 0.05) { self.vx = 0; self.vy = 0; return; }
    var step = Math.min(sp, d / Math.max(dt, 0.0001));
    self.vx = (dx / d) * step;
    self.vy = (dy / d) * step;
    self.flipX = dx < 0;
}

function hasLineOfSight(target) {
    var d = scene.find("Delve");
    if (!d) return true;
    return d.send("clearPath", self.worldX, self.worldY, target.worldX, target.worldY);
}

/** Called by Warden.js and Bolt.js. */
function takeDamage(amount, dirX, dirY) {
    if (dead) return;
    hp -= amount;
    hurtFlash = 0.12;
    self.color = "#FFFFFFFF";
    var d = scene.find("Delve");
    if (d) d.send("onEnemyHit", self.worldX, self.worldY);
    if (dirX !== undefined) {
        self.vx = dirX * 5; self.vy = dirY * 5;
    }
    if (hp <= 0) die(); else { setState("chase"); playOnce("hurt"); }
}

function die() {
    dead = true;
    playOnce("death");
    self.vx = 0; self.vy = 0;
    var d = scene.find("Delve");
    if (d) d.send("onEnemyKilled", self.name, self.worldX, self.worldY);
    after(1.4, function () { self.destroy(); });
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
