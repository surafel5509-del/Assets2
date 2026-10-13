// Crypt of the Ember Sigil -- Spit.js
// Acid.  Travels, damages the warden, dies on masonry.  The masonry test is the
// interesting part: without it a ranged enemy shoots through the level and the
// player cannot use cover at all.

var life = typeof life === "undefined" ? 2.4 : life;

function update(dt) {
    life -= dt;
    if (life <= 0) { self.destroy(); return; }
    // The projectile owns its own damage number so the emitter does not have to
    // pass it through a spawn API that has no parameter channel.
    var w = scene.find("Warden");
    if (w && self.distanceTo(w) < 0.55 && w.visible) {
        // The warden's ouch test owns its own invulnerability window, so a
        // stream of spit cannot stack damage in one frame.
        w.send("hurt", damage, self.x, self.y);
        self.destroy();
        return;
    }
    var d = scene.find("Delve");
    if (d && d.send("isSolidAt", self.worldX, self.worldY)) {
        var fx = scene.spawn("SplatFx", self.x, self.y);
        if (fx) fx.burst(8);
        self.destroy();
    }
}
