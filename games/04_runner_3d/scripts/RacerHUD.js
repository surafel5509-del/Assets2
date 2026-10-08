// Neon Overdrive -- RacerHUD.js
// Speed readout, score, combo flash, nitro bar and the run-summary screen.
//
// Speed is shown as a number AND as the FOV kick (CameraRig), because a number
// alone does not communicate velocity and velocity is the entire feel of a
// runner.  The HUD and the camera have to agree on the same speed value or the
// two readouts contradict each other.
//
// Params: kmhScale=3.6, nitroMax=100, nearMissPoints=25, nearMissStreak=5

var score = 0;
var comboFlash = 0;
var bestDistance = 0;

var nearMisses = 0;

function start() {
    score = 0;
    var best = scene.find("BestText");
    if (best) best.text = "";
}

function update(dt) {
    var car = scene.find("Vehicle");
    var track = scene.find("TrackGenerator");

    if (car) {
        var spd = car.send("getSpeed") || 0;
        var t = scene.find("SpeedText");
        if (t) t.text = String(Math.round(spd * kmhScale));

        var n = car.send("getNitro") || 0;
        var bar = scene.find("NitroFill");
        if (bar) {
            bar.scaleX = Math.max(0.001, n / nitroMax);
            // The bar glows while nitro is burning so the player can tell
            // "empty" from "in use" without reading a number.
            bar.color = car.send("isNitroActive") ? "#FF6FE0FF" : "#FF4C8DFF";
        }
    }

    if (track) {
        var d = track.send("getDistance") || 0;
        var dt_ = scene.find("DistanceText");
        if (dt_) dt_.text = Math.floor(d) + " m";
        bestDistance = Math.max(bestDistance, d);

        // Distance itself scores, so a careful slow run still earns something.
        addScore(Math.max(0, Math.floor(spdOf(car) * dt)));
    }

    if (comboFlash > 0) {
        comboFlash -= dt;
        var c = scene.find("ComboText");
        if (c) c.active = comboFlash > 0;
    }
}

function spdOf(car) { return car ? (car.send("getSpeed") || 0) : 0; }

function addScore(n) {
    score += n;
    var t = scene.find("ScoreText");
    if (t) t.text = pad(score);
}

function showCombo(n) {
    comboFlash = 0.9;
    var c = scene.find("ComboText");
    if (!c) return;
    c.active = true;
    c.text = "x" + n + " COMBO";
    // Pop the text so a combo is felt, not just read.
    c.scaleX = c.scaleY = 1.4;
    after(0.12, function () { if (c) { c.scaleX = c.scaleY = 1.0; } });
}

/**
 * Called by ObstacleSpawner when the car passes close to a hazard without
 * hitting it.  ObstacleSpawner already grants the nitro; this is the score and
 * the feedback half.
 *
 * This handler did not exist.  send() to an unknown name is silently dropped by
 * the engine, so near-misses awarded nitro and played the sound but never scored
 * -- the risk/reward mechanic the spawner's own comment describes was half wired.
 */
function onNearMiss() {
    nearMisses++;
    addScore(nearMissPoints);
    // Streaks are what make threading a tight gap worth attempting twice.
    if (nearMisses % nearMissStreak === 0) showCombo(nearMisses);
}

function onCrash() {
    var panel = scene.find("GameOverPanel");
    if (panel) panel.active = true;
    var d = scene.find("FinalDistance");
    if (d) d.text = Math.floor(bestDistance) + " m";
    var s = scene.find("FinalScore");
    if (s) s.text = pad(score);
}

function pad(n) {
    var s = String(Math.floor(n));
    while (s.length < 7) s = "0" + s;
    return s;
}

function getScore() { return score; }
function getDistance() { return bestDistance; }

function reset() {
    nearMisses = 0;
    score = 0;
    comboFlash = 0;
    var p = scene.find("GameOverPanel");
    if (p) p.active = false;
}
