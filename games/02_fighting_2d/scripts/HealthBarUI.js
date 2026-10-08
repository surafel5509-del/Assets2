// Blade & Bulwark -- HealthBarUI.js
// Fighting-game HUD: two mirrored health bars with a lagging "damage ghost",
// round win pips and a combo counter.
//
// The ghost bar is the important detail.  When health drops, the bright bar
// snaps down immediately but a pale bar behind it drains over ~0.4s, so the
// player can read how much damage a move actually did even mid-combo.
//
// Attach to the "HealthBars" object.

var ghost = { P1: 1, P2: 1 };       // 0..1, trails the real value
var GHOST_SPEED = 2.2;              // fraction of the bar per second
var GHOST_DELAY = 0.35;             // hold before the ghost starts draining
var pending = { P1: 0, P2: 0 };

function start() { refresh(); }

function update(dt) {
    tick("P1", "Fighter_P1", "HealthGhostP1", dt);
    tick("P2", "Fighter_P2", "HealthGhostP2", dt);
}

function tick(side, fighterName, ghostName, dt) {
    var f = scene.find(fighterName);
    if (!f) return;
    var max = f.send("getMaxHealth") || 100;
    var cur = (f.send("getHealth") || 0) / max;

    if (cur < ghost[side]) {
        // Hold briefly, then drain.  The hold is what makes a big hit legible.
        if (pending[side] < GHOST_DELAY) { pending[side] += dt; return; }
        ghost[side] = Math.max(cur, ghost[side] - GHOST_SPEED * dt);
    } else {
        ghost[side] = cur;      // healing / round reset: snap back
        pending[side] = 0;
    }
    applyScale(ghostName, ghost[side], side);
}

/**
 * Bars are children of a fixed-width backing sprite and anchored at their
 * outer edge, so scaling X shrinks them toward the screen edge -- the direction
// a fighting-game health bar is expected to drain in.
 */
function applyScale(name, frac, side) {
    var bar = scene.find(name);
    if (!bar) return;
    bar.scaleX = Math.max(0.001, frac);
}

/** Fighter.js calls this on every health change. */
function refresh() {
    setBar("HealthFillP1", "Fighter_P1");
    setBar("HealthFillP2", "Fighter_P2");

    var n1 = scene.find("NameP1"), n2 = scene.find("NameP2");
    if (n1) n1.text = displayName("Fighter_P1");
    if (n2) n2.text = displayName("Fighter_P2");
}

function setBar(name, fighterName) {
    var f = scene.find(fighterName);
    var bar = scene.find(name);
    if (!f || !bar) return;
    var max = f.send("getMaxHealth") || 100;
    bar.scaleX = Math.max(0.001, (f.send("getHealth") || 0) / max);

    // Colour shifts as health falls: green -> amber -> red.  Doing it by
    // threshold rather than a gradient keeps the read instant.
    var frac = (f.send("getHealth") || 0) / max;
    bar.color = frac > 0.5 ? "#FF4CC26A" : (frac > 0.25 ? "#FFE0A040" : "#FFD0453F");
}

function displayName(fighterName) {
    var sel = scene.find("CharacterSelect");
    if (sel) {
        var n = sel.send("nameFor", fighterName);
        if (n) return n;
    }
    return fighterName === "Fighter_P1" ? "SOLDIER" : "SLIME";
}

/** Round win pips: one lit dot per round taken. */
function refreshPips(w1, w2) {
    for (var i = 1; i <= 2; i++) {
        var a = scene.find("PipP1_" + i);
        if (a) a.color = (i <= w1) ? "#FFFFD24A" : "#FF3A3D44";
        var b = scene.find("PipP2_" + i);
        if (b) b.color = (i <= w2) ? "#FFFFD24A" : "#FF3A3D44";
    }
}

function resetGhosts() {
    ghost.P1 = 1; ghost.P2 = 1;
    pending.P1 = 0; pending.P2 = 0;
}
