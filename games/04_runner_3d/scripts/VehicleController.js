// Neon Overdrive -- VehicleController.js
// High-speed vehicle: constant forward drive, clamped lateral steering,
// jump, drift and nitro.
//
// The car is NOT a rigid body.  At runner speeds a physics solver produces
// tunnelling and unpredictable bounces, and the player has no way to recover.
// Instead the car is driven kinematically and collisions are discrete overlap
// tests, which keeps the outcome of a crash legible and identical every time.
//
// Speed ramps toward a ceiling rather than being constant, because a run that
// starts at top speed has no arc and no sense of risk building.
//
// Params: skin=car, states=idle,drive,jump,crash,
//         baseSpeed=18, maxSpeed=52, accel=3.2, steerSpeed=11, lateralClamp=13,
//         jumpForce=9.5, gravity=26, nitroBoost=1.6, driftGrip=0.45

var speed = 18;
var lateralVel = 0;
var velY = 0;
var airborne = false;
var nitro = 0;
var nitroActive = false;
var drifting = false;
var alive = true;
var tilt = 0;

function start() {
    speed = baseSpeed;
    playClip("idle");
}

function update(dt) {
    if (!alive) return;

    var track = scene.find("TrackGenerator");
    var halfWidth = track ? track.send("getWidth") / 2 : 6.5;

    // --- speed ramp with nitro
    var target = nitroActive ? maxSpeed * nitroBoost : maxSpeed;
    speed += (target - speed) * Math.min(1, (accel / maxSpeed) * dt);

    // --- steering
    var steer = input.axisX;
    if (input.touchX !== undefined && input.touchX >= 0 && input.tapped === false) {
        // drag steering, only when the joystick is idle
        if (Math.abs(steer) < 0.05) steer = clamp(input.touchX * 2.2, -1, 1);
    }

    drifting = input.b && Math.abs(steer) > 0.3 && !airborne;
    var grip = drifting ? driftGrip : 1.0;

    // Steering authority falls as speed rises: at 50 u/s a full stick should
    // change lanes, not teleport across the track.
    var authority = steerSpeed * (baseSpeed / Math.max(speed, baseSpeed));
    lateralVel += steer * authority * grip * dt;
    lateralVel = clamp(lateralVel, -lateralClamp, lateralClamp);
    // Return to centre when the stick is released, or the car drifts forever.
    if (Math.abs(steer) < 0.08) lateralVel *= Math.pow(0.02, dt);

    // --- forward motion (the track runs along -Z)
    self.move(lateralVel * dt, 0, -speed * dt);

    // --- jump and gravity
    if (input.aDown && !airborne) {
        velY = jumpForce;
        airborne = true;
    }
    if (airborne) {
        velY -= gravity * dt;
        self.move(0, velY * dt, 0);
        if (self.y <= 0) { self.y = 0; velY = 0; airborne = false; land(); }
    }

    // --- keep the car on the road.  Hitting the edge scrubs speed instead of
    // killing the run: a runner that ends on a minor mistake feels unfair.
    if (Math.abs(self.x) > halfWidth) {
        self.x = clamp(self.x, -halfWidth, halfWidth);
        lateralVel *= -0.3;
        speed *= 0.94;
        scrapeFX();
    }

    // --- nitro
    if (input.bDown && nitro >= 25 && !nitroActive) activateNitro();
    if (nitroActive) {
        nitro -= 22 * dt;
        if (nitro <= 0) { nitro = 0; nitroActive = false; }
    }

    applyTilt(dt, steer);
    animate();
}

/**
 * The car leans into the turn and pitches with jumps.  Without this the model
 * slides sideways like a sprite on rails, which is the single most common tell
 * of a cheap 3D runner.
 */
function applyTilt(dt, steer) {
    var wantRoll = -lateralVel * 1.4;
    var wantPitch = airborne ? clamp(velY * 1.2, -18, 18) : 0;
    var k = 1 - Math.exp(-9 * dt);
    tilt += (wantRoll - tilt) * k;
    self.rotZ = clamp(tilt, -26, 26);
    self.rotX = wantPitch;
}

function animate() {
    // Wheel spin scales with speed; the sprite sheet only has one loop, so
    // playback rate is the only lever available and it reads correctly.
    self.setAnimSpeed(clamp(speed / baseSpeed, 0.6, 2.6));
    if (airborne && self.animation !== skin + "_jump") playClip("jump");
    else if (!airborne && self.animation !== skin + "_drive") playClip("drive");
}

function land() {
    scene.shake(0.08);
    self.burst(8);
}

function activateNitro() {
    nitroActive = true;
    audio.play("sfx_nitro.ogg");
    scene.shake(0.15);
    log("NITRO");
}

function addNitro(amount) {
    nitro = clamp(nitro + amount, 0, 100);
}

function scrapeFX() {
    self.burst(4);
    if (time.frame % 8 === 0) audio.play("sfx_crash.ogg", 0.25);
}

/** ObstacleSpawner calls this on a hit. */
function crash() {
    if (!alive) return;
    alive = false;
    speed *= 0.25;
    scene.shake(0.5);
    audio.play("sfx_crash.ogg");
    playClip("crash");
    var hud = scene.find("RacerHUD");
    if (hud) hud.send("onCrash");
    after(1.6, function () { scene.reload(); });
}

function getSpeed() { return speed; }
function isNitroActive() { return nitroActive; }
function getNitro() { return nitro; }
function isAirborne() { return airborne; }
function isAlive() { return alive; }
function isDrifting() { return drifting; }

/** Pickups call this. */
function bumpSpeed(amount) { speed = clamp(speed + amount, baseSpeed, maxSpeed * 1.4); }

/**
 * Plays "<skin>_<state>" if that clip exists, else falls back to idle.
 *
 * Clip names come from the `states` param, which lists what this skin actually
 * ships.  Playing a clip that was never declared resolves to nothing in the
 * engine and the sprite silently draws its whole sheet, so the supported set is
 * data rather than something the script has to guess.
 */
var __states = null;
function playClip(state) {
    if (__states === null) {
        __states = {};
        // Params are split on commas by the engine, so the list uses '|'.
        var list = (typeof states === "string") ? states.split("|") : [];
        for (var i = 0; i < list.length; i++) __states[list[i].trim()] = true;
    }
    var name = skin + "_" + state;
    if (!__states[state]) name = skin + "_idle";
    self.play(name);
}
