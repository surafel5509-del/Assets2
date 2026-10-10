// Owns game state: waves of zombies spawn on a ring just outside the view,
// health and score are tracked, and A restarts after game over.
var wave = 1;
var toSpawn = 0;
var spawnTimer = 0;
var score = 0;
var hp = 100;
var over = false;
var toast = 0;

function start() {
  toSpawn = 5;
}

function spawnZombie() {
  var p = scene.find("Player");
  if (!p) return;
  var a = random(0, 6.2832);
  var z = scene.spawn("Zombie", p.x + Math.cos(a) * 13, p.y + Math.sin(a) * 13, 0);
  if (z) z.active = true;
}

function update(dt) {
  if (over) {
    if (input.aDown) scene.reload();
    return;
  }
  if (toSpawn > 0) {
    spawnTimer -= dt;
    if (spawnTimer <= 0) {
      spawnTimer = Math.max(0.3, 1.2 - wave * 0.07);
      spawnZombie();
      toSpawn--;
    }
  } else if (scene.count("Zombie") === 0) {
    wave++;
    toSpawn = 4 + wave * 2;
    toast = 2;
    scene.find("HUD_Msg").text = "WAVE " + wave;
  }
  if (toast > 0) {
    toast -= dt;
    if (toast <= 0) scene.find("HUD_Msg").text = "";
  }
  scene.find("HUD_Wave").text = "WAVE " + wave;
  scene.find("HUD_Score").text = "SCORE " + score;
  scene.find("HealthBar").scaleX = Math.max(0.01, hp / 100 * 4.0);
}

function onKill(n) {
  score += 10 * n * wave;
}

function onHurt(dmg) {
  if (over) return;
  hp -= dmg;
  if (hp <= 0) {
    hp = 0;
    over = true;
    scene.find("HUD_Msg").text = "YOU DIED  -  press A to restart";
  }
}
