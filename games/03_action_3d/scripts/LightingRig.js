// Hollow Ridge -- LightingRig.js
// Day/night cycle driving the directional light, the sky gradient and the
// campfire point lights.
//
// A full cycle is long by default (600s) because a fast cycle makes the scene
// flicker and makes it hard to judge whether a lighting change was intentional.
// The editor can set cycleSeconds low to preview a whole day in seconds.
//
// Params: cycleSeconds=600, startHour=9, nightIntensity=0.18

var hour = 9;
var torchLit = false;

function start() {
    hour = startHour;
    apply();
}

function update(dt) {
    hour += (24 / cycleSeconds) * dt;
    if (hour >= 24) hour -= 24;
    apply();
}

function apply() {
    var sun = scene.find("Sun");
    var sky = scene.find("Main Camera");

    // Sun elevation: a cosine over the day, so dawn and dusk spend more time at
    // low angles -- which is where the interesting light is.
    var t = (hour - 6) / 24 * Math.PI * 2;
    var elevation = Math.sin(t);                    // -1 .. 1
    var dayFactor = clamp(elevation * 1.6 + 0.35, 0, 1);

    if (sun) {
        sun.rotX = -elevation * 60;
        sun.rotY = (hour / 24) * 360 - 90;

        // Intensity and colour both ramp.  Only ramping intensity gives the
        // flat grey look that reads as "someone turned a dimmer down"; ramping
        // colour toward amber is what actually sells sunset.
        // These are real properties on the Light component now.  They used to go
        // through send("setLightIntensity"), and because the engine's message
        // dispatch silently ignores an unknown handler, the sun never changed --
        // the day-night cycle ran and nothing moved.
        sun.lightIntensity = lerp(nightIntensity, 1.25, dayFactor);
        sun.lightColor = mixColor("#FF3A3560", "#FFFFE8C0", dayFactor);
    }

    if (sky) {
        sky.skyTop = mixColor("#FF0A0E1E", "#FF3B7BD4", dayFactor);
        sky.skyHorizon = mixColor("#FF2A2438", "#FFBFD8F0", dayFactor);
    }

    // Torchlight only earns its cost at night; keeping point lights off during
    // the day is a free win on a mobile GPU.
    var wantLit = dayFactor < 0.35;
    if (wantLit !== torchLit) {
        torchLit = wantLit;
        var fires = scene.findAll("Campfire");
        for (var i = 0; i < fires.length; i++) {
            fires[i].send("setLit", wantLit);
        }
    }

    var clock = scene.find("ClockText");
    if (clock) clock.text = formatHour(hour);
}

function formatHour(h) {
    var hh = Math.floor(h);
    var mm = Math.floor((h - hh) * 60);
    return (hh < 10 ? "0" : "") + hh + ":" + (mm < 10 ? "0" : "") + mm;
}

/** "#RRGGBB" interpolation, done per channel in integer space. */
function mixColor(a, b, t) {
    var ca = parseHex(a), cb = parseHex(b);
    var r = Math.round(lerp(ca[0], cb[0], t));
    var g = Math.round(lerp(ca[1], cb[1], t));
    var bl = Math.round(lerp(ca[2], cb[2], t));
    return "#" + hex2(r) + hex2(g) + hex2(bl);
}

function parseHex(s) {
    var h = s.replace("#", "");
    if (h.length === 8) h = h.substring(2);      // drop alpha
    return [parseInt(h.substring(0, 2), 16),
            parseInt(h.substring(2, 4), 16),
            parseInt(h.substring(4, 6), 16)];
}

function hex2(v) {
    var s = clamp(v, 0, 255).toString(16).toUpperCase();
    return s.length < 2 ? "0" + s : s;
}

function getHour() { return hour; }
function isNight() { return torchLit; }
function setHour(h) { hour = h; apply(); }
