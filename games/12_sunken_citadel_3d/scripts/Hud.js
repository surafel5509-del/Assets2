// Sunken Citadel -- Hud.js
//
// Screen-space HUD: wave banner, health, stamina, combo counter, score and the
// director's toast.  Bars are sprite quads scaled in the UI view, so they track
// the same numbers the scripts use -- there is no second source of truth.
//
// Params: hpWidth=4.4, stamWidth=3.2

var title = "";
var waveLine = "";
var scoreLine = "";
var comboLine = "";
var toastLine = "";
var hpFrac = 1.0;
var stamFrac = 1.0;

function update(dt) {
    var c = scene.find("Citadel");
    var k = scene.find("Knight");
    if (!c || !k) return;

    var lost = c.send("hasLost");
    var won = c.send("hasWon");
    if (won) title = "THE CITADEL IS QUIET";
    else if (lost) title = "THE KNIGHT FALLS";
    else if (c.send("isBossAlive")) title = "THE WARDEN OF THE KEEP";
    else title = "WAVE " + c.send("getWave") + " / " + c.send("getWaveCount");

    waveLine = "hollows " + c.send("getAlive") + "   kills " + c.send("getKills");
    scoreLine = "score " + Math.floor(c.send("getScore"));
    var combo = k.send("getCombo") + 1;
    comboLine = combo > 1 ? "COMBO x" + combo : "";
    toastLine = c.send("getToast");

    var hp = k.send("getHp");
    var st = k.send("getStamina");
    hpFrac = Math.max(0.0, Math.min(1.0, hp / 120.0));
    stamFrac = Math.max(0.0, Math.min(1.0, st / 100.0));

    set("HudTitle", title);
    set("HudWave", waveLine);
    set("HudScore", scoreLine);
    set("HudCombo", comboLine);
    set("HudToast", toastLine);

    var hpBar = scene.find("HpBar");
    if (hpBar) hpBar.scaleX = Math.max(0.001, hpWidth * hpFrac);
    var stBar = scene.find("StamBar");
    if (stBar) stBar.scaleX = Math.max(0.001, stamWidth * stamFrac);
}

function set(name, value) {
    var o = scene.find(name);
    if (o) o.text = value;
}
