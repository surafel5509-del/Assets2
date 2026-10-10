// Third-person arena shooter. Left stick moves and turns the player;
// holding A fires a bullet straight ahead of the facing direction.
var speed = 6.5;
var fireCooldown = 0;
var pillarRadius = 1.6;

function update(dt) {
  var ax = input.axisX;
  var az = -input.axisY;
  var mag = Math.sqrt(ax * ax + az * az);
  if (mag > 0.08) {
    var k = Math.min(1, mag) / mag;
    self.move(ax * k * speed * dt, 0, az * k * speed * dt);
    self.rotY = Math.atan2(ax, az) * 180 / Math.PI;
  }
  // Keep the player inside the arena walls.
  var x = clamp(self.x, -18.5, 18.5);
  var z = clamp(self.z, -18.5, 18.5);
  // Push out of pillars (circle-vs-circle).
  var pillars = scene.findAll("Pillar");
  for (var i = 0; i < pillars.length; i++) {
    var p = pillars[i];
    var dx = x - p.x, dz = z - p.z;
    var d = Math.sqrt(dx * dx + dz * dz);
    var min = pillarRadius + 0.5;
    if (d < min && d > 0.0001) {
      x = p.x + dx / d * min;
      z = p.z + dz / d * min;
    }
  }
  self.setPosition(x, 0.6, z);

  fireCooldown -= dt;
  if (input.a && fireCooldown <= 0) {
    fireCooldown = 0.16;
    var r = self.rotY * Math.PI / 180;
    var b = scene.spawn("Bullet", self.x + Math.sin(r) * 0.9, 0.8, self.z + Math.cos(r) * 0.9);
    if (b) {
      b.rotY = self.rotY;
      b.active = true;
    }
    audio.beep(880, 0.04);
  }
}
