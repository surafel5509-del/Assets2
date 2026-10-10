// Fast projectile. Flies along its rotY and hits the first Grunt it touches.
var life = 1.1;
var speed = 20;

function update(dt) {
  var r = self.rotY * Math.PI / 180;
  self.move(Math.sin(r) * speed * dt, 0, Math.cos(r) * speed * dt);
  life -= dt;
  if (life <= 0) { self.destroy(); return; }
  if (Math.abs(self.x) > 20 || Math.abs(self.z) > 20) { self.destroy(); return; }
  var grunts = scene.findAll("Grunt");
  for (var i = 0; i < grunts.length; i++) {
    if (self.distanceTo3(grunts[i]) < 1.0) {
      grunts[i].send("onShot", 1);
      self.destroy();
      return;
    }
  }
}
