// Owns the game state: spawns waves of Grunts from the arena edge,
// tracks HP and score, and drives the HUD. Press A on game over to restart.
var wave = 1;
var toSpawn = 0;
var spawnTimer = 0;
var score = 0;
var hp = 100;
var over = false;
var toast = 0;

function start() {
  toSpawn = 4;
  refresh();
  msg("");
}

function msg(t) { scene.find("HUD_Msg").text = t; }

function refresh() {
  scene.find("HUD_Hp").text = "HP " + Math.max(0, Math.round(hp));
  scene.find("HUD_Score").text = "SCORE " + score;
  scene.find("HUD_Wave").text = "WAVE " + wave;
}

function spawnGrunt() {
  var side = Math.floor(random(0, 4));
  var t = random(-18, 18);
  var x = side === 0 ? -18 : (side === 1 ? 18 : t);
  var z = side === 2 ? -18 : (side === 3 ? 18 : t);
  var g = scene.spawn("Grunt", x, 0.7, z);
  if (g) g.active = true;
}

function update(dt) {
  if (over) {
    if (input.aDown) scene.reload();
    return;
  }
  if (toSpawn > 0) {
    spawnTimer -= dt;
    if (spawnTimer <= 0) {
      spawnTimer = Math.max(0.35, 1.4 - wave * 0.08);
      spawnGrunt();
      toSpawn--;
    }
  } else if (scene.count("Grunt") === 0) {
    wave++;
    toSpawn = 3 + wave * 2;
    toast = 2;
    msg("WAVE " + wave);
  }
  if (toast > 0) {
    toast -= dt;
    if (toast <= 0) msg("");
  }
  refresh();
}

function onKill(n) {
  score += 100 * n * wave;
  scene.shake(0.15);
}

function onHurt(dmg) {
  if (over) return;
  hp -= dmg;
  if (hp <= 0) {
    hp = 0;
    over = true;
    msg("GAME OVER  -  press A to restart");
  }
}
