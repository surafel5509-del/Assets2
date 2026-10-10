// Level manager: culls trees and rocks beyond draw distance (cheap LOD) and
// spins coins. Culling runs on a timer, not every frame.
var trees = [];
var rocks = [];
var cullRadius = 75;
var cullTimer = 0;

function start() {
  trees = scene.findAll("Tree");
  rocks = scene.findAll("Rock");
}

function update(dt) {
  var coins = scene.findAll("Coin");
  for (var i = 0; i < coins.length; i++) coins[i].rotY += 150 * dt;

  cullTimer -= dt;
  if (cullTimer > 0) return;
  cullTimer = 0.25;
  var car = scene.find("Car");
  if (!car) return;
  var shown = 0;
  for (var t = 0; t < trees.length; t++) {
    var near = car.distanceTo3(trees[t]) < cullRadius;
    trees[t].active = near;
    if (near) shown++;
  }
  for (var k = 0; k < rocks.length; k++) {
    var nearRock = car.distanceTo3(rocks[k]) < cullRadius;
    rocks[k].active = nearRock;
    if (nearRock) shown++;
  }
  scene.find("HUD_Culled").text = "Props drawn: " + shown;
}
