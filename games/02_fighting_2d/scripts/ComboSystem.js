// Blade & Bulwark -- ComboSystem.js
// Tracks consecutive hits and applies combo damage scaling.
//
// Scaling matters more than it looks: without it, a light attack that hits
// eight times out-damages a heavy, so the optimal strategy collapses into
// mashing the fastest button.  Each successive hit in a combo does less, and
// the combo drops if the window expires.
//
// Attach to the "ComboSystem" object.

var STEP = 0.82;          // multiplier applied per successive hit
var FLOOR = 0.25;         // a combo hit never does less than this fraction
var WINDOW = 1.4;         // seconds of grace before the combo drops

var combos = {};          // attacker name -> { count, timer }

function start() {
    log("Combo system online");
}

function update(dt) {
    for (var who in combos) {
        var c = combos[who];
        c.timer -= dt;
        if (c.timer <= 0) {
            if (c.count >= 3) announce(who, c.count);
            delete combos[who];
        }
    }
}

/** Fighter.js calls this when a swing starts. */
function noteAttackStart(who) {
    var c = combos[who];
    if (c) c.timer = WINDOW;
}

/**
 * Fighter.js calls this once a hit resolves.
 * `landed` distinguishes a clean hit from a blocked one: blocked hits extend
 * the window (you are still pressuring) but do not increase the count.
 */
function noteHit(who, landed) {
    var c = combos[who];
    if (!c) { c = { count: 0, timer: WINDOW }; combos[who] = c; }
    c.timer = WINDOW;
    if (landed) {
        c.count++;
        if (c.count >= 2) showCounter(who, c.count);
    }
}

/** Damage multiplier for the attacker's next hit. */
function comboScale(who) {
    var c = combos[who];
    if (!c || c.count <= 0) return 1.0;
    return Math.max(FLOOR, Math.pow(STEP, c.count - 1));
}

function comboCount(who) {
    var c = combos[who];
    return c ? c.count : 0;
}

function showCounter(who, count) {
    var label = scene.find("ComboText");
    if (!label) return;
    label.text = count + " HIT COMBO";
    label.active = true;
    // Pop-and-settle: scale up then relax, which reads far better than a fade.
    label.scaleX = label.scaleY = 1.6;
    after(0.18, function () {
        var l = scene.find("ComboText");
        if (l) { l.scaleX = 1.0; l.scaleY = 1.0; }
    });
}

function announce(who, count) {
    if (count < 3) return;
    var label = scene.find("ComboText");
    if (label) {
        label.text = count + " HITS!";
        after(1.2, function () {
            var l = scene.find("ComboText");
            if (l) l.active = false;
        });
    }
}

function reset() {
    combos = {};
    var label = scene.find("ComboText");
    if (label) label.active = false;
}

function onStop() { reset(); }
