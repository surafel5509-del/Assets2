// Blackout Heist -- Terminal.js
//
// The camera terminal.  One held action and the security grid goes dark for the
// rest of the run, which is the only trade the player can make between risk and
// time: the room it sits in is the most guarded one in the depot.
//
// Params: hold=1.2

var progress = 0.0;
var used = false;
var glow = null;

function start() {
    glow = scene.find("TerminalGlow");
}

function interact(dt) {
    if (used) return 0.0;
    progress += dt;
    if (progress >= hold) {
        used = true;
        audio.play("sfx_cam.ogg", 0.8);
        self.setColor("#FF6A7A8E");
        if (glow) glow.setVisible(false);
        var h = scene.find("Heist");
        if (h) h.send("camerasOff");
        var fx = scene.find("Fx");
        if (fx) fx.send("crack", pack(self.x, self.y));
        return 0.0;
    }
    return progress / hold;
}

function interactCancel() { progress = 0.0; }
function prompt() { return used ? "" : "Hold Interact: cut the camera feed"; }
function isUsed() { return used; }

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}
