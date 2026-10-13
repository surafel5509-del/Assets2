// Emberfall Valley -- Valley.js
//
// The director: time of day, weather, the resource economy the HUD reads, the
// objective chain, and the win/lose bookkeeping.
//
// The sun is a real Directional Light whose rotation and colour this script
// drives, and the sky is the Camera3D's two gradient colours -- both are things
// Renderer3D reads every frame, so the valley really does get darker.
//
// Params: dayLength=180, dusk=19.5, dawn=5.5, coldFrom=20.0, coldTo=5.0,
//         beacons=3, targetBerry=6

var clock = 7.25;              // hours, 0..24
var frozen = false;
var won = false;
var lost = false;
var prompt = "";
var beaconsLit = 0;
var berryFound = 0;
var woodFound = 0;
var phase = "morning";
var cold = 0.0;
var toast = "";
var toastT = 0.0;
var weatherT = 34.0;
var weather = "clear";
var snow = 0.0;

function start() {
    clock = 7.25;
    setPhase("morning");
    say("Emberfall Valley -- gather wood, light the three beacons before nightfall.");
}

function update(dt) {
    clock += (24.0 / dayLength) * dt;
    if (clock >= 24.0) clock -= 24.0;

    updateSun();
    updateWeather(dt);

    var w = scene.find("Ranger");
    if (w) {
        var night = clock < coldFrom && clock > coldTo;
        // dusk and dawn bound the cold window above; they also name the phase
        // the HUD prints, so one clock drives both the light and the label.
        if (clock < dawn) setPhase("night");
        else if (clock < 11.0) setPhase("morning");
        else if (clock < dusk) setPhase("afternoon");
        else setPhase("night");
        // Cold drains stamina away from fire; the camp and lit beacons warm you.
        if (night && !nearFire(w)) {
            cold += dt;
            if (cold > 6.0) { cold = 0.0; w.send("chill", 6); }
        } else {
            cold = 0.0;
        }
    }

    if (toastT > 0.0) {
        toastT -= dt;
        if (toastT <= 0.0) toast = "";
    }

    checkWin();
}

/** Sun colour and angle by hour: a real day/night cycle, not a filter. */
function updateSun() {
    var sun = scene.find("Sun");
    var cam = scene.find("Main Camera");
    var t = clock;
    // Dawn at 5.5, noon 12.5, dusk 19.5, midnight 0.
    var daylight = 1.0;
    if (t < 5.5) daylight = 0.06;
    else if (t < 7.5) daylight = 0.06 + (t - 5.5) / 2.0 * 0.94;
    else if (t < 17.0) daylight = 1.0;
    else if (t < 20.0) daylight = 1.0 - (t - 17.0) / 3.0 * 0.94;
    else daylight = 0.06;

    if (sun) {
        var pitch = -12.0 - 46.0 * daylight;
        sun.rotX = pitch;
        sun.rotY = 90.0 + (t / 24.0) * 360.0;
        var warm = 1.0 - Math.abs(t - 12.5) / 8.0;
        sun.lightIntensity = 0.18 + 1.05 * daylight;
        sun.lightColor = lerpColor("#FF3A4C86", lerpColor("#FFFFC178", "#FFFFF3D6", warm),
                                   Math.min(1.0, daylight * 1.15));
    }
    if (cam) {
        cam.skyTop = lerpColor("#FF0A1030", "#FF4B8ED6", daylight);
        cam.skyHorizon = lerpColor("#FF2A3A56", "#FFDCE9E4", daylight);
    }
}

function updateWeather(dt) {
    weatherT -= dt;
    if (weatherT <= 0.0) {
        weatherT = 26.0 + random(0, 22);
        weather = weather === "clear" ? (random(0, 1) > 0.45 ? "mist" : "rain") : "clear";
        if (weather === "mist") say("A cold mist rolls down off the ridge.");
        else if (weather === "rain") say("Rain sweeps across the valley.");
    }
    var want = weather === "mist" ? 1.0 : 0.0;
    snow += (want - snow) * Math.min(1.0, dt * 0.6);
    var fx = scene.find("Weather");
    if (fx) fx.setEmitting(snow > 0.35);
}

/** Ground distance between two objects: height must not shrink a walk. */
function groundDist(a, b) {
    var dx = a.x - b.x, dz = a.z - b.z;
    return Math.sqrt(dx * dx + dz * dz);
}

function nearFire(w) {
    // "Near the fire" is about standing beside it, not about the height of the
    // flames: measure on the ground.
    var fires = scene.findAll("Fire");
    for (var i = 0; i < fires.length; i++) {
        if (groundDist(w, fires[i]) < 11.0) return true;
    }
    return false;
}

function onBeaconLit(index) {
    beaconsLit++;
    audio.play("sfx_fire.ogg");
    say("Beacon " + index + " burns. " + beaconsLit + " of " + beacons + " lit.");
}

function onBerry() { berryFound++; }
function getTargetBerry() { return targetBerry; }
function onWood() { woodFound++; }

function checkWin() {
    if (won) return;
    if (beaconsLit >= beacons) {
        won = true;
        audio.play("sfx_win.ogg");
        say("All three beacons burn. The valley holds the light.");
    }
}

function gameOver() {
    if (won) return;
    lost = true;
    frozen = true;
    audio.play("sfx_deny.ogg");
    say("The cold takes the valley. Press A to begin again.");
}

function say(msg) {
    toast = msg;
    toastT = 5.0;
}

function setClock(hours) { clock = hours; }
function setPhase(p) { phase = p; }
function setPrompt(p) { prompt = p; }
function isFrozen() { return frozen || won; }
function hasWon() { return won; }
function hasLost() { return lost; }
function getClock() { return clock; }
function getPhase() { return phase; }
function getPrompt() { return prompt; }
function getToast() { return toast; }
function getWeather() { return weather; }
function getBeaconsLit() { return beaconsLit; }
function getBeaconGoal() { return beacons; }
function getBerryFound() { return berryFound; }
function getTargetBerry() { return targetBerry; }
function restart() { self.reload(); }

/** "#AARRGGBB" blend, so the sun and sky can be interpolated by daylight. */
function lerpColor(aHex, bHex, t) {
    if (t < 0.0) t = 0.0;
    if (t > 1.0) t = 1.0;
    var a = hexParts(aHex), b = hexParts(bHex);
    var r = Math.round(a[1] + (b[1] - a[1]) * t);
    var g = Math.round(a[2] + (b[2] - a[2]) * t);
    var bl = Math.round(a[3] + (b[3] - a[3]) * t);
    return "#FF" + hex2(r) + hex2(g) + hex2(bl);
}
function hexParts(h) {
    return [0, parseInt(h.substr(3, 2), 16), parseInt(h.substr(5, 2), 16), parseInt(h.substr(7, 2), 16)];
}
function hex2(v) {
    var s = v.toString(16).toUpperCase();
    return s.length < 2 ? "0" + s : s;
}
