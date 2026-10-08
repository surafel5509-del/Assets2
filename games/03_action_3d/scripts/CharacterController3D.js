// Hollow Ridge -- CharacterController3D.js
// Camera-relative 3D character controller.
//
// "Camera-relative" is the whole point: input.axisY = up must move the
// character AWAY from the camera, not along world -Z.  So the input vector is
// rotated by the camera's yaw before it becomes velocity.  Without that,
// orbiting the camera makes the controls feel inverted and unpredictable.
//
// Ground handling uses coyote time and jump buffering.  Both exist because on a
// touchscreen the player's thumb lands a few frames late; a controller that
// demands frame-perfect input feels broken even though it is "correct".
//
// Params: moveSpeed=4.6, sprintMultiplier=1.55, jumpForce=7.2, gravity=22,
//         coyoteTime=0.12, jumpBuffer=0.14, turnSpeed=14, skin=hero,
//         states=idle,walk,jump,attack,hurt,death

var velX = 0, velZ = 0, velY = 0;
var grounded = true;
var coyote = 0;
var jumpBuffered = 0;
var facing = 0;               // yaw the model is turning toward
var camYaw = 0;
var attacking = false;
var attackTimer = 0;
var stamina = 1;              // 0..1, pushed in by AdventureHUD via setStamina()

function start() {
    playClip("idle");
}

function update(dt) {
    var ix = input.axisX, iy = input.axisY;

    // Normalise so a diagonal is not faster than a cardinal direction.
    var mag = Math.sqrt(ix * ix + iy * iy);
    if (mag > 1) { ix /= mag; iy /= mag; mag = 1; }

    // Stamina is owned by AdventureHUD (it draws the bar) and pushed in through
    // setStamina().  Sprinting needs some left, which is what makes the bar a
    // constraint rather than decoration.
    var sprint = (input.a && mag > 0.85 && stamina > 0.02) ? sprintMultiplier : 1.0;

    // --- rotate the input by the camera yaw
    var rad = camYaw * Math.PI / 180;
    var cos = Math.cos(rad), sin = Math.sin(rad);
    var wx = ix * cos + iy * sin;      // world-space wish direction
    var wz = -ix * sin + iy * cos;

    var speed = moveSpeed * sprint;
    // Accelerate toward the wish direction instead of snapping: instant velocity
    // changes read as skating, and they also make collision response jittery.
    var accel = grounded ? 18 : 6;     // less air control, as expected
    velX += (wx * speed - velX) * Math.min(1, accel * dt);
    velZ += (wz * speed - velZ) * Math.min(1, accel * dt);

    // --- gravity and grounding
    velY -= gravity * dt;
    self.move(velX * dt, velY * dt, velZ * dt);

    updateGrounding();

    // --- jumping, with coyote time and buffering
    if (coyote > 0) coyote -= dt;
    if (jumpBuffered > 0) jumpBuffered -= dt;

    if (input.axisY > 0.85 && jumpBuffered <= 0) jumpBuffered = jumpBuffer;
    if (jumpBuffered > 0 && (grounded || coyote > 0)) {
        velY = jumpForce;
        grounded = false;
        coyote = 0;
        jumpBuffered = 0;
        playClip("jump");
    }

    // --- facing: turn toward travel direction, but only while moving
    if (mag > 0.15 && !attacking) {
        var want = Math.atan2(wx, wz) * 180 / Math.PI;
        facing = approachAngle(facing, want, turnSpeed * 60 * dt);
        self.rotY = facing;
    }

    animate(mag, sprint);

    // --- attack
    if (input.aDown && !attacking) beginAttack();
    if (attacking) {
        attackTimer -= dt;
        if (attackTimer <= 0) attacking = false;
    }
}

/**
 * Grounding is a short downward probe, not "did y reach 0".  The world has
// cliffs and bridges, so the floor height varies and must be queried.
 */
function updateGrounding() {
    var wasGrounded = grounded;
    var hit = scene.raycast(self.x, self.y + 0.1, self.z, 0, -1, 0, 0.35);
    grounded = hit !== null && hit !== undefined;

    if (grounded) {
        if (velY < 0) velY = 0;
        coyote = coyoteTime;
        // Snap to the surface so walking down a slope does not become a series
        // of tiny falls (which reads as stuttering).
        if (hit && hit.y !== undefined && self.y > hit.y) self.y = hit.y;
    } else if (wasGrounded) {
        // Just walked off a ledge: grant coyote time instead of an instant fall.
        coyote = coyoteTime;
    }
}

function animate(mag, sprint) {
    if (attacking) return;
    if (!grounded) { if (self.animation !== skin + "_jump") playClip("jump"); return; }
    if (mag > 0.15) {
        if (self.animation !== skin + "_walk") playClip("walk");
        // Sprinting plays the same walk cycle faster, which is cheaper than a
        // second animation set and reads correctly at this scale.
        self.setAnimSpeed(sprint > 1 ? 1.45 : 1.0);
    } else {
        self.setAnimSpeed(1.0);
        if (self.animation !== skin + "_idle") playClip("idle");
    }
}

function beginAttack() {
    attacking = true;
    attackTimer = 0.42;
    playClip("attack");
    audio.play("sfx_swing.ogg");

    // Spherical sweep in front of the character.  Combat3D does the actual
    // overlap test so the rules live in one place.
    var c = scene.find("Combat3D");
    if (c) c.send("swing", self.name, facing, 1.9, 110);
}

/** ThirdPersonCamera publishes its yaw here every frame. */
function setCameraYaw(y) { camYaw = y; }

/** AdventureHUD owns the stamina bar; it pushes the 0..1 ratio here each frame. */
function setStamina(ratio) { stamina = clamp(ratio, 0, 1); }
function getStamina() { return stamina; }

function getFacing() { return facing; }
function isGrounded() { return grounded; }
function isAttacking() { return attacking; }

/** Shortest-path angle interpolation, handling the -180/180 wrap. */
function approachAngle(current, target, maxDelta) {
    var d = ((target - current + 540) % 360) - 180;
    if (Math.abs(d) <= maxDelta) return target;
    return current + (d < 0 ? -maxDelta : maxDelta);
}

function applyKnockback(dx, dy, dz, force) {
    velX += dx * force;
    velY += dy * force;
    velZ += dz * force;
    grounded = false;
}

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
