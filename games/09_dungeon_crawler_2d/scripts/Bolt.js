// Crypt of the Ember Sigil -- Bolt.js
// The warden's thrown ember.  Hits the first enemy within reach, dies on walls.

var life = 1.6;

function update(dt) {
    life -= dt;
    if (life <= 0) { self.destroy(); return; }
    var enemies = scene.findAll("Enemy");
    for (var i = 0; i < enemies.length; i++) {
        if (self.distanceTo(enemies[i]) < 0.62) {
            enemies[i].send("takeDamage", 16, self.vx * 0.1, self.vy * 0.1);
            var fx = scene.spawn("SplatFx", self.x, self.y);
            if (fx) fx.burst(10);
            self.destroy();
            return;
        }
    }
    var d = scene.find("Delve");
    if (d && d.send("isSolidAt", self.worldX, self.worldY)) self.destroy();
}
