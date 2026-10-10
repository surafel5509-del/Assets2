// Side-scrolling motorbike with simple suspension-free physics.
// The bike follows the terrain height function; A or axisY-up is gas,
// B is brake, B-press in the air is not used (jump is B's rising edge).
var speed = 0;
var vy = 0;
var grounded = true;
var fuel = 100;
var coins = 0;
var state = "ride";           // ride | crash | finish
var gravity = -22;
var finishX = 260;

function groundY(x) { return -2 + 0.6 * Math.sin(x * 0.18) + 0.35 * Math.sin(x * 0.47 + 1.3); }
function slope(x) { return (groundY(x + 0.05) - groundY(x - 0.05)) / 0.1; }

function msg(t) { scene.find("HUD_Msg").text = t; }

function update(dt) {
  if (state !== "ride") {
    if (input.aDown) scene.reload();
    return;
  }
  var gas = (input.a || input.axisY > 0.3) ? 1 : 0;
  var brake = input.axisY < -0.3 ? 1 : 0;
  if (fuel > 0 && gas) fuel = Math.max(0, fuel - 3 * dt);

  // Engine push, brake, drag, and gravity along the slope.
  speed += (gas * 10 * (fuel > 0 ? 1 : 0) - brake * 12 - speed * 0.25) * dt;
  speed -= slope(self.x) * 7 * dt;
  speed = clamp(speed, -4, 24);
  self.x += speed * dt;

  var targetY = groundY(self.x) + 0.55;
  vy += gravity * dt;
  var y = self.y + vy * dt;
  if (y <= targetY) {
    if (!grounded && Math.abs(self.rotation) > 100) {
      state = "crash";
      speed = 0;
      msg("CRASH  -  press A to retry");
    }
    if (!grounded && vy < -6) {
      var dust = scene.spawn("Burst", self.x, targetY - 0.5, 0);
      if (dust) dust.burst(14);
    }
    y = targetY;
    vy = 0;
    grounded = true;
  } else {
    grounded = false;
  }
  if (input.bDown && grounded) {
    vy = 9.5;
    grounded = false;
  }
  self.setPosition(self.x, y);

  if (grounded) {
    self.rotation = Math.atan(slope(self.x)) * 180 / Math.PI;
  } else {
    self.rotation += -input.axisX * 130 * dt;   // lean/wheelie in the air
  }

  var wheelSpin = speed * 60 * dt;
  scene.find("WheelBack").rotation -= wheelSpin;
  scene.find("WheelFront").rotation -= wheelSpin;

  var coinObjs = scene.findAll("Coin");
  for (var i = 0; i < coinObjs.length; i++) {
    var c = coinObjs[i];
    if (self.distanceTo(c) < 1.1) {
      c.destroy();
      coins++;
      fuel = Math.min(100, fuel + 6);
      audio.beep(1200, 0.04);
    }
  }

  scene.find("HUD_Dist").text = Math.floor(self.x) + " m";
  scene.find("HUD_Coins").text = "COINS " + coins;
  scene.find("FuelBar").scaleX = Math.max(0.01, fuel / 100 * 3.0);
  // The bar is anchored at its left edge (-5.6) and shrinks toward it.
  scene.find("FuelBar").x = -5.6 + (fuel / 100 * 3.0) / 2;

  if (self.x >= finishX && state === "ride") {
    state = "finish";
    msg("FINISH!  Coins " + coins + "  -  press A to ride again");
  }
  if (fuel <= 0 && speed < 0.5 && state === "ride" && grounded) {
    state = "crash";
    msg("OUT OF FUEL  -  press A to retry");
  }
}
