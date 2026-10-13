// Blackout Heist -- Hud.js
//
// Screen-space HUD: what to do next, how many crates are in hand, the alarm, the
// meter for whatever is being held, and one line of mission text.  It reads the
// director and nothing else, so the HUD can never disagree with the game.
//
// Note for anyone moving it: the runtime draws screen-space objects through a
// fixed +/-5-unit view (SceneRenderer.drawScreenUI), not through the game camera,
// so this layout is inside +/-6.6 x +/-4.8 to stay on screen from 4:3 to 20:9.
//
// Params: pips=3

var promptText = "";
var holdAmount = 0.0;

function start() {
    for (var i = 0; i < pips; i++) {
        var p = scene.find("Pip" + i);
        if (p) p.setColor("#FF2C2E3A");
    }
}

function update(dt) {
    var h = scene.find("Heist");
    if (!h) return;
    var loot = h.send("getLoot");
    var need = h.send("getLootNeeded");
    var alarm = h.send("getAlarm");
    var won = h.send("hasWon");
    var lost = h.send("hasLost");

    var tasks = "  " + tick(h.send("hasKey")) + " keycard\n" +
                "  " + tick(loot >= need) + " crates " + loot + "/" + need + "\n" +
                "  " + tick(won) + " back to the van";
    setText("HudTasks", tasks);
    setText("HudObjective", lost ? "CAUGHT -- Interact to try again"
                                 : (won ? (h.send("underPar") ? "CLEAN GETAWAY -- under par, Interact to run it again"
                                                              : "GETAWAY -- over par, Interact to run it again")
                                        : (h.send("areCamerasOff") ? "Camera grid is dark"
                                                                   : "Objectives")));
    setText("HudPrompt", promptText);
    setText("HudStatus", "T+" + clock(h.send("getTime")) + "  /  par " + clock(h.send("getPar")) +
            (h.send("areCamerasOff") ? "   cameras down" :
             (h.send("getCameras") > 0.05 ? "   camera on you" : "")) +
            (h.send("wasSpotted") ? "   seen by " + h.send("getSpottedBy") : ""));
    setText("HudToast", h.send("getToast"));

    var key = scene.find("HudKey");
    if (key) key.setVisible(h.send("hasKey"));
    for (var i = 0; i < pips; i++) {
        var p = scene.find("Pip" + i);
        if (p) p.setColor(i < loot ? "#FFE8C46A" : "#FF2C2E3A");
    }
    var bar = scene.find("AlarmBar");
    if (bar) {
        bar.scaleX = 4.4 * Math.min(1.0, alarm);
        bar.setColor(alarm > 0.66 ? "#FFFF4A38" : "#FFFFC24A");
        bar.setVisible(alarm > 0.01);
    }
    var holdBar = scene.find("HoldBar");
    if (holdBar) {
        holdBar.scaleX = 1.7 * Math.max(0.0, Math.min(1.0, holdAmount));
        holdBar.setVisible(holdAmount > 0.01);
    }
    var frame = scene.find("HoldFrame");
    if (frame) frame.setVisible(holdAmount > 0.01);
}

function tick(done) { return done ? "[x]" : "[ ]"; }

function clock(sec) {
    var m = Math.floor(sec / 60.0);
    var s = Math.floor(sec) % 60;
    return m + ":" + (s < 10 ? "0" + s : "" + s);
}

function setText(name, value) {
    var o = scene.find(name);
    if (o) o.setText(value);
}

function prompt(msg) { promptText = msg; }
function hold(p) { holdAmount = p; }
