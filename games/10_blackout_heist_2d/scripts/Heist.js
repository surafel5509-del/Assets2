// Blackout Heist -- Heist.js
//
// The director.  It owns the objective chain, the alarm, the clock and the two
// ways the run ends, so the mission can be read from exactly one place -- and
// the HUD reads it from here rather than keeping its own copy of the truth.
//
// Params: lootNeeded=3, alarmDecay=0.14, alarmLimit=1.0, parTime=210

var loot = 0;
var keyTaken = false;
var gridDown = false;
var alarm = 0.0;
var cameras = 0.0;
var caught = false;
var escaped = false;
var toast = "";
var toastT = 0.0;
var time = 0.0;
var spottedBy = "";
var par = 210.0;

function start() {
    par = parTime;
    toast = "Loading dock.  Keycard, crates, van.  Par is " + Math.round(parTime / 60) + " minutes.";
    toastT = 6.0;
}

function update(dt) {
    time += dt;
    if (toastT > 0.0) {
        toastT -= dt;
        if (toastT <= 0.0) toast = "";
    }
    if (caught || escaped) {
        if (input.actionPressed("Interact")) scene.reload();
        return;
    }
    // The alarm cools only while nobody is chasing; a camera keeps it warm.
    var hot = false;
    var guards = scene.findAll("Guard");
    for (var i = 0; i < guards.length; i++) {
        var s = guards[i].send("getState");
        if (s === "chase") hot = true;
    }
    if (!hot) alarm = Math.max(0.0, alarm - alarmDecay * dt);
    cameras = Math.max(0.0, cameras - alarmDecay * dt);
    if (Math.max(alarm, cameras) >= alarmLimit) {
        caught = true;
        audio.play("sfx_caught.ogg", 1.0);
    }
}

function say(msg) { toast = msg; toastT = 4.5; }

function onSneak(on) {
    if (on > 0.5) say("Creeping. Quiet, but slow.");
}

function onLoot(kind) {
    if (kind === "key") {
        keyTaken = true;
        say("Keycard. The vault is open to you now.");
        return;
    }
    if (kind === "safe") {
        say("Safe cracked. Nothing in it but dust.");
        return;
    }
    loot++;
    if (loot >= lootNeeded) say("All " + lootNeeded + " crates. Back to the dock.");
    else say("Crate " + loot + " of " + lootNeeded + ".");
}

function camerasOff() {
    gridDown = true;
    cameras = 0.0;
    say("Camera grid down. Nothing is watching but the guards.");
}

function cameraSees(amount) {
    if (caught || escaped || gridDown) return;
    cameras = Math.min(alarmLimit, cameras + amount);
}

function spotted(name) {
    spottedBy = name;
    alarm = Math.min(alarmLimit, alarm + 0.25);
    say("Spotted by " + name + ". Move.");
}

function onCaught() {
    if (caught) return;
    caught = true;
    alarm = alarmLimit;
}

function onEscape() {
    escaped = true;
    audio.play("sfx_win.ogg", 0.9);
}

function isOver() { return caught || escaped; }
function hasWon() { return escaped; }
/** The run is scored against par: inside it is a clean getaway. */
function underPar() { return escaped && time <= parTime; }
function getPar() { return parTime; }
function hasLost() { return caught; }
function getLoot() { return loot; }
function getLootNeeded() { return lootNeeded; }
function getAlarm() { return Math.max(alarm, gridDown ? 0.0 : cameras); }
function getCameras() { return cameras; }
function hasKey() { return keyTaken; }
function areCamerasOff() { return gridDown; }
function wasSpotted() { return spottedBy !== ""; }
function getSpottedBy() { return spottedBy; }
function getToast() { return toast; }
function getTime() { return time; }
