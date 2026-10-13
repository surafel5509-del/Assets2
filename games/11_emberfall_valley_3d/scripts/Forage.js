// Emberfall Valley -- Forage.js
//
// A gatherable: a berry bush, a stone cairn, a fallen log.  Holding the action
// near it yields a resource, then the node goes bare and comes back after
// `respawn`.  The node's own state is what the HUD and the ranger read, so a
// bare node simply refuses to answer.
//
// Params: kind=berry, amount=2, respawn=42, reach=3.2

var ready = true;
var timer = 0.0;
var glow = null;

function start() {
    glow = null;
}

function update(dt) {
    if (!ready) {
        timer -= dt;
        if (timer <= 0.0) {
            ready = true;
            if (glow) glow.burst(8);
        }
    }
}

function canInteract() { return ready; }
function prompt() {
    if (!ready) return "";
    return "Gather " + kind + "  (" + reach + "m)";
}

function interact() {
    if (!ready) return false;
    ready = false;
    timer = respawn;
    var w = scene.find("Ranger");
    if (!w) return false;
    w.send("addResource", kind, amount);
    var v = scene.find("Valley");
    if (v) {
        v.send(kind === "berry" ? "onBerry" : "onWood");
        v.send("say", "Gathered " + amount + " " + kind + ".");
    }
    audio.play("sfx_pick.ogg", 0.7);
    self.color = "#FF8A7F6A";
    after(0.25, function () { self.color = "#FFFFFFFF"; });
    var fx = scene.find("Fx");
    if (fx) fx.send("pickup", self.x, self.y + 0.6, self.z);
    return true;
}
