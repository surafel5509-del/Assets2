// Arcade car: A accelerates, B brakes/reverses, left stick steers.
// Steering strength scales with speed so the car cannot turn on the spot.
var speed = 0;
var maxSpeed = 24;
var collected = 0;
var total = 45;

function update(dt) {
  var thr = 0;
  if (input.a) thr = 1;
  if (input.b) thr = -0.6;
  speed += thr * 15 * dt;
  speed -= speed * 0.35 * dt;                    // rolling drag
  if (thr === 0 && Math.abs(speed) < 0.2) speed = 0;
  speed = clamp(speed, -9, maxSpeed);

  var dir = speed >= 0 ? 1 : -1;
  var steer = Math.min(1, Math.abs(speed) / 6);
  self.rotY += input.axisX * 75 * steer * dir * dt;

  var r = self.rotY * Math.PI / 180;
  self.move(Math.sin(r) * speed * dt, 0, Math.cos(r) * speed * dt);
  self.setPosition(clamp(self.x, -95, 95), 0.5, clamp(self.z, -95, 95));

  var coins = scene.findAll("Coin");
  for (var i = 0; i < coins.length; i++) {
    var c = coins[i];
    if (self.distanceTo3(c) < 2.2) {
      c.destroy();
      collected++;
      var fx = scene.spawn("Burst", c.x, 1.0, c.z);
      if (fx) fx.burst(12);
      audio.beep(1320, 0.05);
    }
  }
  scene.find("HUD_Speed").text = Math.round(Math.abs(speed) * 3.6) + " km/h";
  scene.find("HUD_Coins").text = "COINS " + collected + "/" + total;
  if (collected === total) scene.find("HUD_Msg").text = "ALL COINS COLLECTED!";
}
