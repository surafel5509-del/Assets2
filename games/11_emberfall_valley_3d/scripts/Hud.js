// Emberfall Valley -- Hud.js
//
// Screen-space HUD: the objective, the clock, the resource counts, a stamina bar
// and the interaction prompt.  Every label is a TextRenderer in the UI view, so
// it scales with the screen the way the engine's 2D UI does.
//
// Params: barWidth=3.0

var objective = "";
var clockLine = "";
var bagLine = "";
var staminaFrac = 1.0;
var promptLine = "";
var toastLine = "";
var bar = null;

function start() {
    bar = scene.find("StaminaBar");
    set("HudObjective", objective);
    set("HudClock", clockLine);
    set("HudBag", bagLine);
    set("HudPrompt", promptLine);
    set("HudToast", toastLine);
}

function update(dt) {
    var v = scene.find("Valley");
    var w = scene.find("Ranger");
    if (!v || !w) return;

    var lit = v.send("getBeaconsLit");
    var goal = v.send("getBeaconGoal");
    var clock = v.send("getClock");
    var phase = v.send("getPhase");
    var carry = w.send("getCarrying");
    var stam = w.send("getStamina");
    var stamMax = w.send("getStaminaMax");

    objective = "B E A C O N S   " + Math.floor(lit) + " / " + Math.floor(goal);
    var hh = Math.floor(clock);
    var mm = Math.floor((clock - hh) * 60.0);
    clockLine = phase.toUpperCase() + "  " + pad(hh) + ":" + pad(mm) +
                "   " + v.send("getWeather");
    bagLine = "wood " + (carry.wood || 0) + "   stone " + (carry.stone || 0) +
              "   berry " + (carry.berry || 0);
    staminaFrac = stamMax > 0 ? stam / stamMax : 0.0;
    promptLine = v.send("getPrompt");
    toastLine = v.send("getToast");

    var lost = v.send("hasLost");
    var won = v.send("hasWon");
    if (won) objective = "THE VALLEY HOLDS THE LIGHT";
    else if (lost) objective = "YOU FROZE IN THE DARK";

    set("HudObjective", objective);
    set("HudClock", clockLine);
    set("HudBag", bagLine);
    set("HudPrompt", promptLine);
    set("HudToast", toastLine);
    if (bar) {
        bar.scaleX = Math.max(0.001, barWidth * staminaFrac);
    }
}

function pad(n) { return n < 10 ? "0" + n : "" + n; }
function set(name, value) {
    var o = scene.find(name);
    if (o) o.text = value;
}
