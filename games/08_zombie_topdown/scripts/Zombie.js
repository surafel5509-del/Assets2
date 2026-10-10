// Chases the player and bites on contact. Speed rises with the wave.
var hp = 2;
var speed = 1.5;
var biteCooldown = 0;
var dying = false;

function update(dt) {
  var p = scene.find("Player");
  if (!p) return;
  var dx = p.x - self.x, dy = p.y - self.y;
  var d = Math.sqrt(dx * dx + dy * dy);
  if (d > 0.5) {
    self.move(dx / d * speed * dt, dy / d * speed * dt);
  }
  self.rotation = Math.atan2(dy, dx) * 180 / Math.PI;
  biteCooldown -= dt;
  if (d < 0.85 && biteCooldown <= 0) {
    biteCooldown = 0.9;
    scene.find("Director").send("onHurt", 7);
  }
}

function onShot(dmg) {
  hp -= dmg;
  if (hp <= 0 && !dying) {
    dying = true;
    var fx = scene.spawn("Gore", self.x, self.y, 0);
    if (fx) fx.burst(20);
    scene.find("Director").send("onKill", 1);
    self.destroy();
  }
}
