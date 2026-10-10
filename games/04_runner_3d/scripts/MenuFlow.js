// Neon Overdrive -- MenuFlow.js
// Title -> run -> game over -> retry.
//
// The menu is deliberately one screen deep.  A runner that needs a menu tree
// before you can play loses the thing that makes it good: being able to start a
// run in under two seconds.
//
// Params: none

var phase = "title";      // title | running | over
var elapsed = 0;

function start() {
    phase = "title";
    setScreen("TitlePanel", true);
    setScreen("GameOverPanel", false);
    setRunning(false);
    // The track still runs behind the title, so the menu has motion rather than
    // sitting on a static image.  The car is hidden, not stopped.
    var car = scene.find("Vehicle");
    if (car) car.active = false;
}

function update(dt) {
    elapsed += dt;

    if (phase === "title") {
        if (input.tapped || input.aDown) beginRun();
        return;
    }

    if (phase === "over") {
        // A short input lockout after the crash.  Without it the player taps to
        // dismiss the crash and immediately restarts by accident, which reads as
        // the game ignoring them.
        if (elapsed > 1.2 && (input.tapped || input.aDown)) beginRun();
    }
}

function beginRun() {
    elapsed = 0;
    phase = "running";
    setScreen("TitlePanel", false);
    setScreen("GameOverPanel", false);
    setRunning(true);

    var car = scene.find("Vehicle");
    if (car) { car.active = true; car.setPosition(0, 0, 0); }

    var hud = scene.find("RacerHUD");
    if (hud) hud.send("reset");

    var cam = scene.find("Main Camera");
    if (cam) cam.send("snap");

    audio.play("sfx_ui.ogg");
    log("Run started");
}

function setRunning(on) {
    // Only the car hides between the menu and a run; the systems stay live so a
    // restart does not have to rebuild the track.  This used to send("__noop__")
    // to each of them as a way of touching them, but the engine silently ignores
    // an unknown handler, so the loop did literally nothing.
    var v = scene.find("Vehicle");
    if (v) v.visible = on;
}

function setScreen(name, on) {
    var p = scene.find(name);
    if (p) p.active = on;
}

/** RacerHUD calls this when the car crashes. */
function onCrash() {
    phase = "over";
    elapsed = 0;
    setScreen("GameOverPanel", true);
}

function getPhase() { return phase; }

function quitToTitle() {
    phase = "title";
    setScreen("TitlePanel", true);
    setScreen("GameOverPanel", false);
    var car = scene.find("Vehicle");
    if (car) car.active = false;
}
