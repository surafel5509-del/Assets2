// Blade & Bulwark -- RoundManager.js
// Best-of-three round flow: intro, fight, KO slow motion, winner banner.
//
// The round timer and the KO freeze are the two things that make a fighting
// game feel like a fighting game rather than two health bars drifting down, so
// both are handled here rather than in Fighter.js.
//
// Params: roundsToWin=2, roundTime=60, koSlowSeconds=1.4

var phase = "intro";      // intro | fight | ko | matchover
var roundNumber = 1;
var wins = { P1: 0, P2: 0 };
var timer = 60;
var phaseTimer = 0;
var timeScale = 1;

function start() {
    resetMatch();
}

function update(dt) {
    phaseTimer += dt;

    if (phase === "intro") {
        if (phaseTimer > 1.8) beginFight();
        return;
    }

    if (phase === "fight") {
        timer -= dt;
        var clock = scene.find("TimerText");
        if (clock) clock.text = String(Math.max(0, Math.ceil(timer)));
        if (timer <= 0) timeUp();
        return;
    }

    if (phase === "ko") {
        // Slow motion sells the KO; it also gives the death animation time to
        // play before the round resets.
        if (phaseTimer > koSlowSeconds) {
            timeScale = 1;
            afterRound();
        }
        return;
    }
}

function resetMatch() {
    wins.P1 = 0; wins.P2 = 0;
    roundNumber = 1;
    startRound();
}

function startRound() {
    phase = "intro";
    phaseTimer = 0;
    timer = roundTime;
    timeScale = 1;

    var cs = scene.find("ComboSystem");
    if (cs) cs.send("reset");

    place("Fighter_P1", -2.6, 1);
    place("Fighter_P2", 2.6, -1);

    setBanner("ROUND " + roundNumber);
    var pips = scene.find("RoundPips");
    if (pips) pips.send("refresh", wins.P1, wins.P2);
    log("Round " + roundNumber + " - fight!");
}

function place(name, x, facing) {
    var f = scene.find(name);
    if (!f) return;
    f.setPosition(x, 0);
    f.send("resetRound", facing);
}

function beginFight() {
    phase = "fight";
    phaseTimer = 0;
    setBanner("FIGHT!");
    audio.play("sfx_select.ogg");
    after(0.9, function () { setBanner(""); });
}

/** Fighter.js calls this when someone's health reaches zero. */
function onKnockout(loserName) {
    if (phase !== "fight") return;
    phase = "ko";
    phaseTimer = 0;
    timeScale = 0.25;                       // slow motion

    var winner = loserName === "Fighter_P1" ? "P2" : "P1";
    wins[winner]++;
    setBanner("K.O.");
    log(winner + " wins round " + roundNumber);

    var pips = scene.find("RoundPips");
    if (pips) pips.send("refresh", wins.P1, wins.P2);
}

function timeUp() {
    // Time over: whoever has more health left takes the round.  Equal health is
    // a draw, which awards nobody -- a real fighting-game rule that prevents
    // turtling from being a winning strategy.
    var a = scene.find("Fighter_P1"), b = scene.find("Fighter_P2");
    var ha = a ? a.send("getHealth") : 0;
    var hb = b ? b.send("getHealth") : 0;
    phase = "ko";
    phaseTimer = 0;
    setBanner("TIME OVER");
    if (Math.abs(ha - hb) < 0.5) { setBanner("DRAW GAME"); return; }
    if (ha > hb) wins.P1++; else wins.P2++;
}

function afterRound() {
    if (wins.P1 >= roundsToWin || wins.P2 >= roundsToWin) {
        phase = "matchover";
        var champ = wins.P1 >= roundsToWin ? "PLAYER 1" : "PLAYER 2";
        setBanner(champ + " WINS");
        audio.play("sfx_ko.ogg");
        after(3.0, function () { resetMatch(); });
        return;
    }
    roundNumber++;
    startRound();
}

function setBanner(text) {
    var b = scene.find("BannerText");
    if (!b) return;
    b.text = text;
    b.active = text.length > 0;
}

// --- forwarded to ComboSystem so Fighter.js only needs one manager reference
function noteAttackStart(who) {
    var cs = scene.find("ComboSystem");
    if (cs) cs.send("noteAttackStart", who);
}
function noteHit(who, landed) {
    var cs = scene.find("ComboSystem");
    if (cs) cs.send("noteHit", who, landed);
}
function comboScale(who) {
    var cs = scene.find("ComboSystem");
    if (!cs) return 1.0;
    var s = cs.send("comboScale", who);
    return (s === null || s === undefined) ? 1.0 : s;
}

function getPhase() { return phase; }
function getTimeScale() { return timeScale; }
function getWins() { return wins; }
