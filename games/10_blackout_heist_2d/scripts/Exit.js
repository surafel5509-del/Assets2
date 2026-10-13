// Blackout Heist -- Exit.js
//
// The back of the van.  It only opens once the loot is on board, and it says so
// -- a heist that ends because you walked over a marker is not a heist, it is a
// door.
//
// Params: need=3, hold=1.2

var progress = 0.0;

function start() {
    self.setVisible(false);
}

function update(dt) {
    var h = scene.find("Heist");
    var have = h ? h.send("getLoot") : 0;
    self.setVisible(have >= need);
}

function interact(dt) {
    var h = scene.find("Heist");
    var have = h ? h.send("getLoot") : 0;
    if (have < need) return 0.0;
    progress += dt;
    if (progress >= hold) {
        var fx = scene.find("Fx");
        if (fx) fx.send("escape", pack(self.x, self.y));
        var t = scene.find("Thief");
        if (t) t.send("escape");
        return 0.0;
    }
    return progress / hold;
}

function interactCancel() { progress = 0.0; }

function prompt() {
    var h = scene.find("Heist");
    var have = h ? h.send("getLoot") : 0;
    if (have < need) return "The van needs " + need + " crates (" + have + ")";
    return "Hold Interact: load up and drive";
}

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}
