// Blade & Bulwark -- CharacterSelect.js
// Roster definition and the pre-match select screen.
//
// The roster is data, not code: adding a fighter means adding an entry here
// plus importing its sheets.  Everything downstream (portraits, move names,
// stat bars) reads from ROSTER, so the select screen cannot drift out of sync
// with the fighters it is choosing between.
//
// Attach to the "CharacterSelect" object.

var ROSTER = [
    {
        id: "soldier",
        name: "SOLDIER",
        blurb: "Sword and shield. Even damage, fast recovery.",
        portrait: "soldier_idle.png",
        stats: { power: 3, speed: 3, defense: 4 },
        health: 100,
        walk: 2.6,
        jump: 7.5,
        // texture stem: "<stem>_idle.png", "<stem>_walk.png", ...
        stem: "soldier"
    },
    {
        id: "slime",
        name: "SLIME",
        blurb: "Fast and slippery. Lower health, quicker startup.",
        portrait: "slime_idle.png",
        stats: { power: 2, speed: 5, defense: 2 },
        health: 82,
        walk: 3.4,
        jump: 8.6,
        stem: "slime"
    }
];

var cursor = 0;
var chosen = { P1: null, P2: null };
var active = true;
var confirmPhase = "P1";
var blink = 0;

function start() {
    var panel = scene.find("SelectPanel");
    if (panel) panel.active = true;
    // Fighters stay hidden until the match is confirmed.
    hide("Fighter_P1"); hide("Fighter_P2");
    updateReadout();
}

function update(dt) {
    if (!active) return;
    blink += dt;

    if (input.tapped || input.aDown) {
        chosen[confirmPhase] = ROSTER[cursor];
        audio.play("sfx_select.ogg");
        if (confirmPhase === "P1") {
            confirmPhase = "P2";
            // Mirror the default so player 2 does not start on the same slot.
            cursor = (cursor + 1) % ROSTER.length;
        } else {
            finish();
            return;
        }
        updateReadout();
    }

    // Roster navigation.
    if (input.axisX > 0.5)  { stepCursor(1); }
    if (input.axisX < -0.5) { stepCursor(-1); }
}

var stepCooldown = 0;
function stepCursor(dir) {
    // Without a cooldown, one held direction scrolls the whole roster in a
    // fraction of a second and the selection becomes unaimable.
    if (time.frame - stepCooldown < 12) return;
    stepCooldown = time.frame;
    cursor = (cursor + dir + ROSTER.length) % ROSTER.length;
    audio.play("sfx_select.ogg");
    updateReadout();
}

function updateReadout() {
    var r = ROSTER[cursor];
    var name = scene.find("SelectName");
    if (name) name.text = (confirmPhase === "P1" ? "P1  " : "P2  ") + r.name;
    var blurb = scene.find("SelectBlurb");
    if (blurb) blurb.text = r.blurb;

    var portrait = scene.find("SelectPortrait");
    if (portrait) portrait.setTexture(r.portrait);

    renderStats(r.stats);
    renderChosen();
}

function renderStats(stats) {
    // Stat bars are 5 pips; light as many as the value.
    var kinds = ["power", "speed", "defense"];
    for (var k = 0; k < kinds.length; k++) {
        var v = stats[kinds[k]] || 0;
        for (var i = 1; i <= 5; i++) {
            var pip = scene.find("StatPip_" + kinds[k] + "_" + i);
            if (pip) pip.color = (i <= v) ? "#FF4C8DFF" : "#FF33363C";
        }
    }
}

function renderChosen() {
    var p1 = scene.find("ChosenP1");
    if (p1) {
        p1.text = chosen.P1 ? chosen.P1.name : "- - -";
        p1.color = chosen.P1 ? "#FFFFD24A" : "#FF6A6E76";
    }
    var p2 = scene.find("ChosenP2");
    if (p2) {
        p2.text = chosen.P2 ? chosen.P2.name : "- - -";
        p2.color = chosen.P2 ? "#FFFFD24A" : "#FF6A6E76";
    }
}

function finish() {
    active = false;
    var panel = scene.find("SelectPanel");
    if (panel) panel.active = false;

    configure("Fighter_P1", chosen.P1);
    configure("Fighter_P2", chosen.P2);

    var rm = scene.find("RoundManager");
    if (rm) rm.send("startRound");
    log("Match: " + chosen.P1.name + " vs " + chosen.P2.name);
}

/**
 * Applies the roster entry to a fighter object.  Textures are swapped per
 * animation state by Fighter.js, so what is set here is only the initial frame
 * plus the numeric stats the frame data does not cover.
 */
function configure(fighterName, entry) {
    var f = scene.find(fighterName);
    if (!f || !entry) return;
    f.active = true;
    f.setTexture(entry.stem + "_idle.png");
    f.send("applyRoster", entry);
}

function hide(name) {
    var f = scene.find(name);
    if (f) f.active = false;
}

/** HealthBarUI calls this to label the bars. */
function nameFor(fighterName) {
    var e = fighterName === "Fighter_P1" ? chosen.P1 : chosen.P2;
    return e ? e.name : null;
}

function roster() { return ROSTER; }
function isActive() { return active; }
