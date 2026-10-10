// Short-lived projectile that flies along its rotation and hits the first zombie.
var life = 0.9;
var speed = 16;

function update(dt) {
  var r = self.rotation * Math.PI / 180;
  self.move(Math.cos(r) * speed * dt, Math.sin(r) * speed * dt);
  life -= dt;
  if (life <= 0) { self.destroy(); return; }
  var zs = scene.findAll("Zombie");
  for (var i = 0; i < zs.length; i++) {
    if (self.distanceTo(zs[i]) < 0.6) {
      zs[i].send("onShot", 1);
      self.destroy();
      return;
    }
  }
}
