// Blackout Heist -- Loot.js
//
// The keycard, the three vault crates and the safe are the same object with
// different numbers, because they are all "hold the button here long enough".
// The vault is locked until the keycard is in hand, which is what turns three
// rooms into one route.
//
// Params: kind=crate, hold=1.5, sound=sfx_pick.ogg, locked=false, openTex=...
//
// Every param is read through a guard.  The engine's script scope throws on an
// undeclared global, so a scene that leaves `openTex` to its default would kill
// this script the moment the loot was picked up -- which is exactly what the
// keycard did before this was fixed.

var _kind = typeof kind === "undefined" ? "crate" : kind;
var _hold = typeof hold === "undefined" ? 1.5 : hold;
var _sound = typeof sound === "undefined" ? "" : sound;
var _locked = typeof locked === "undefined" ? false : locked;
var _openTex = typeof openTex === "undefined" ? "" : openTex;

var progress = 0.0;
var taken = false;
var glow = null;

function start() {
    glow = scene.find(self.name + "Glow");
}

function unlocked() {
    if (!_locked) return true;
    var h = scene.find("Heist");
    return h ? h.send("hasKey") : false;
}

function interact(dt) {
    if (taken) return 0.0;
    if (!unlocked()) return 0.0;
    progress += dt;
    if (progress >= _hold) {
        taken = true;
        if (_sound) audio.play(_sound, 0.75);
        var fx = scene.find("Fx");
        if (fx) fx.send("crack", pack(self.x, self.y));
        var h = scene.find("Heist");
        if (h) h.send("onLoot", _kind);
        if (glow) glow.setVisible(false);
        if (_openTex) self.setTexture(_openTex);
        else self.setVisible(false);
        return 0.0;
    }
    return progress / _hold;
}

function interactCancel() { progress = 0.0; }

function prompt() {
    if (taken) return "";
    if (!unlocked()) return "Locked -- find the keycard";
    if (_kind === "key") return "Hold Interact: take the keycard";
    if (kind === "safe") return "Hold Interact: crack the safe";
    return "Hold Interact: crack the crate";
}

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}

function isTaken() { return taken; }
function getKind() { return kind; }
