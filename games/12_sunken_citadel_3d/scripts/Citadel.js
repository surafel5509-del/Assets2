// Sunken Citadel -- Citadel.js
//
// The director: three waves of Hollows, a rest between them, the boss that wakes
// when the third wave falls, and the bookkeeping the HUD reads.  It also owns
// the keep's lamps, so the courtyard gets darker as the fight goes on and the
// boss arrives lit by its own brazier.
//
// Params: waveGap=6.0, restGap=9.0, bossHp=420, scorePerKill=25, waveBonus=250

var wave = 0;
var waveTimer = 0.0;
var spawnTimer = 0.0;
var inWave = false;
var resting = false;
var bossSpawned = false;
var bossAlive = false;
var kills = 0;
var score = 0;
var firstBlood = 0.0;
var bestCombo = 0;
var toast = "";
var toastT = 0.0;
var frozen = false;
var won = false;
var lost = false;

var WAVES = [
    { count: 5, kind: "hollow", hp: 46, speed: 3.1, gap: 1.5 },
    { count: 7, kind: "hollow", hp: 58, speed: 3.5, gap: 1.2 },
    { count: 9, kind: "revenant", hp: 74, speed: 3.9, gap: 1.0 }
];
var spawnedThisWave = 0;
var liveTimer = 0.0;

function start() {
    wave = 0;
    say("The citadel is awake. Stand, knight.");
    after(2.5, function () { beginWave(1); });
}

function update(dt) {
    if (toastT > 0.0) {
        toastT -= dt;
        if (toastT <= 0.0) toast = "";
    }
    if ((lost || won) && input.actionPressed("Jump")) {
        // Rise again / walk it back: one button, one fresh run.
        retry();
        return;
    }
    var k = scene.find("Knight");
    if (k) {
        var c = k.send("getCombo");
        if (c + 1 > bestCombo) bestCombo = c + 1;
    }
    if (resting) {
        waveTimer -= dt;
        if (waveTimer <= 0.0) beginWave(wave + 1);
    } else if (inWave) {
        liveTimer += dt;
        if (spawnedThisWave < WAVES[wave - 1].count) {
            spawnTimer -= dt;
            if (spawnTimer <= 0.0) {
                spawnTimer = WAVES[wave - 1].gap;
                spawnHollow();
            }
        }
    }
    // Waves do not end on a timer -- they end when the courtyard is clear.  The
    // timer above only spaces out the arrivals.
}

function beginWave(n) {
    wave = n;
    inWave = true;
    resting = false;
    spawnedThisWave = 0;
    spawnTimer = waveGap * 0.1;
    liveTimer = 0.0;
    var w = WAVES[n - 1];
    audio.play("sfx_wave.ogg");
    say("Wave " + n + " of " + WAVES.length + " -- " + w.count + " " + w.kind + "s");
    var fx = scene.find("Fx");
    if (fx) fx.send("wave", 0.0, 0.0);
}

function spawnHollow() {
    var w = WAVES[wave - 1];
    // Spawn ring: outside the keep, inside the walls, never on top of the knight.
    var ang = random(0.0, 360.0);
    var r = random(12.0, 17.5);
    var x = Math.sin(ang * Math.PI / 180.0) * r;
    var z = Math.cos(ang * Math.PI / 180.0) * r;
    var proto = w.kind === "revenant" ? "Revenant" : "Hollow";
    var go = scene.spawn(proto, x, 0.0, z);
    if (!go) return;
    spawnedThisWave++;
    go.send("configure", w.hp, w.speed);
    var fx = scene.find("Fx");
    if (fx) fx.send("summon", x, z);
}

function onHollowKilled(name, x, z) {
    kills++;
    score += scorePerKill;
    var fx = scene.find("Fx");
    if (fx) fx.send("crumble", x, z);
    if (inWave && !resting && aliveHollows() === 0 && spawnedThisWave >= WAVES[wave - 1].count) {
        inWave = false;
        if (wave >= WAVES.length) {
            if (!bossSpawned) summonBoss();
        } else {
            resting = true;
            waveTimer = restGap;
            score += waveBonus;
            say("Wave " + wave + " cleared.  +" + waveBonus + "  --  breathe.");
        }
    }
}

function aliveHollows() {
    // The boss is on the same side, so it is excluded here: waves end when the
    // courtyard's Hollows are down, not when the Warden is.
    return scene.findAll("Enemy").length - (bossAlive ? 1 : 0);
}

function summonBoss() {
    bossSpawned = true;
    say("Something in the keep stands up.");
    audio.play("sfx_slam.ogg");
    scene.shake(0.8);
    var go = scene.spawn("Warden", 0.0, 0.0, -12.5);
    if (!go) return;
    bossAlive = true;
    go.send("configure", bossHp, 3.2);
    var fx = scene.find("Fx");
    if (fx) fx.send("summon", 0.0, -12.5);
}

function onBossDown() {
    bossAlive = false;
    score += 1000;
    won = true;
    frozen = true;
    audio.play("sfx_win.ogg");
    say("The Warden falls. The citadel is quiet.  Score " + Math.floor(score));
}

function knightHurt() {
    if (firstBlood <= 0.0) firstBlood = 1.0;
}

function gameOver() {
    if (won) return;
    lost = true;
    frozen = true;
    audio.play("sfx_lose.ogg");
    say("The knight falls.  Press A to rise again.");
}

/** The death screen's one button: the whole scene is rebuilt from scratch. */
function retry() { scene.reload(); }

function say(msg) { toast = msg; toastT = 4.5; }

function isFrozen() { return frozen; }
function hasWon() { return won; }
function hasLost() { return lost; }
function getWave() { return wave; }
function getWaveCount() { return WAVES.length; }
function getKills() { return kills; }
function getScore() { return score; }
function getToast() { return toast; }
function getAlive() { return aliveHollows(); }
function isBossAlive() { return bossAlive; }
