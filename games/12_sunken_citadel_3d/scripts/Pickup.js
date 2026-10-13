// Sunken Citadel -- Pickup.js
//
// Loot: a dropped flask, a bundle of cloth, a coin.  Walking over it collects it,
// because stopping to press a button mid-fight is a worse game.  Drops are pooled
// templates spawned by the director and the enemies.
//
// Params: heal=26, kind=flask, life=26.0

var collected = false;
var age = 0.0;

function update(dt) {
    age += dt;
    var k = scene.find("Knight");
    if (!k) return;
    // Loot is collected by walking over it; the drop floats, so the check is on
    // the floor, not through the air.
    var dx = k.x - self.x, dz = k.z - self.z;
    if (Math.sqrt(dx * dx + dz * dz) < 1.7) {
        collect(k);
        return;
    }
    // A gentle spin so loot reads as an item rather than scenery.
    self.rotY = age * 70.0;
    self.y = 0.75 + Math.sin(age * 2.4) * 0.12;
    if (age > life) self.destroy();
}

function collect(k) {
    if (collected) return;
    collected = true;
    if (kind === "flask") {
        k.send("heal", heal);
        var c = scene.find("Citadel");
        if (c) c.send("say", "You drink.  +" + heal + " health.");
    } else {
        var c2 = scene.find("Citadel");
        if (c2) c2.send("say", "Loot taken.");
    }
    audio.play("sfx_loot.ogg", 0.7);
    var fx = scene.find("Fx");
    if (fx) fx.send("pickup", self.x, self.y, self.z);
    self.destroy();
}
