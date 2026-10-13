// Blackout Heist -- Burst.js
//
// One-shot effects, fired onto pooled emitters: a footstep, a cracked crate, a
// guard's shout and the alarm.  The 2D renderer has no lights, so an additive
// burst is what a noise looks like in this game.
//

function start() {
    // The pooled emitters are burst-only: if one of them ever started emitting on
    // its own, the level would fill with dust nobody asked for.
    self.setEmitting(false);
}

function burst(name, payload, n, color) {
    var parts = ("" + payload).split(":");
    var x = parseFloat(parts[0]), y = parseFloat(parts[1]);
    var e = scene.find(name);
    if (!e) return;
    e.setPosition(x, y, 0.0);
    e.setColor(color);
    e.burst(n);
}

function noise(payload) { burst("FxNoise", payload, 6, "#66C8E0FF"); }
function crack(payload) { burst("FxCrack", payload, 16, "#FFE0C060"); }
function spot(payload) { burst("FxSpot", payload, 12, "#FFFFC24A"); }
function alarm(payload) { burst("FxSpot", payload, 26, "#FFFF4A38"); }

/** A van pulling away: smoke, thrown back, in two directions. */
function escape(payload) {
    var parts = ("" + payload).split(":");
    var x = parseFloat(parts[0]), y = parseFloat(parts[1]);
    scene.spawn("Puff", x, y - 1.0);
    scene.spawn("Puff", x - 0.8, y - 1.6);
    scene.spawn("Puff", x + 0.8, y - 1.6);
    audio.play("sfx_door.ogg", 0.7);
}
