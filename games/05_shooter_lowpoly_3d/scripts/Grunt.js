// Melee enemy: walks straight at the player and hits on contact.
var hp = 3;
var speed = 2.3;
var hitCooldown = 0;
var dying = false;

function update(dt) {
  var p = scene.find("Player");
  if (!p) return;
  var dx = p.x - self.x, dz = p.z - self.z;
  var d = Math.sqrt(dx * dx + dz * dz);
  if (d > 0.9) self.move(dx / d * speed * dt, 0, dz / d * speed * dt);
  self.rotY = Math.atan2(dx, dz) * 180 / Math.PI;
  hitCooldown -= dt;
  if (d < 1.3 && hitCooldown <= 0) {
    hitCooldown = 0.8;
    scene.find("Director").send("onHurt", 8);
  }
}

function onShot(dmg) {
  hp -= dmg;
  if (hp <= 0 && !dying) {
    dying = true;
    var fx = scene.spawn("Burst", self.x, 0.8, self.z);
    if (fx) fx.burst(24);
    scene.find("Director").send("onKill", 1);
    self.destroy();
  }
}
