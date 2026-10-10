// Top-down survivor. Left stick moves and is blocked by crates. Holding A
// fires at the nearest zombie (auto-aim) on a short cooldown.
var speed = 4.6;
var radius = 0.4;
var cooldown = 0;

function update(dt) {
  var mx = input.axisX;
  var my = input.axisY;
  var mag = Math.sqrt(mx * mx + my * my);
  var x = self.x, y = self.y;
  if (mag > 0.08) {
    var k = Math.min(1, mag) / mag;
    x += mx * k * speed * dt;
    y += my * k * speed * dt;
  }
  // Push out of crates (circle-vs-circle, crates are 0.8 radius).
  var crates = scene.findAll("Wall");
  for (var i = 0; i < crates.length; i++) {
    var dx = x - crates[i].x, dy = y - crates[i].y;
    var d = Math.sqrt(dx * dx + dy * dy);
    var min = radius + 0.8;
    if (d < min && d > 0.0001) {
      x = crates[i].x + dx / d * min;
      y = crates[i].y + dy / d * min;
    }
  }
  self.setPosition(clamp(x, -33, 33), clamp(y, -33, 33));

  cooldown -= dt;
  if (input.a && cooldown <= 0) {
    var target = nearestZombie();
    if (target) {
      cooldown = 0.2;
      var ang = Math.atan2(target.y - self.y, target.x - self.x) * 180 / Math.PI;
      var r = ang * Math.PI / 180;
      var b = scene.spawn("Bullet", self.x + Math.cos(r) * 0.6, self.y + Math.sin(r) * 0.6, 0);
      if (b) {
        b.rotation = ang;
        b.active = true;
      }
      audio.beep(700, 0.03);
    }
  }
}

function nearestZombie() {
  var zs = scene.findAll("Zombie");
  var best = null;
  var bestD = 1e9;
  for (var i = 0; i < zs.length; i++) {
    var d = self.distanceTo(zs[i]);
    if (d < bestD) { bestD = d; best = zs[i]; }
  }
  return best;
}
