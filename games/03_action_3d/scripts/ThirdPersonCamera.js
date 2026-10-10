// Hollow Ridge -- ThirdPersonCamera.js
// Orbit camera: yaw/pitch from touch drag, smoothed follow, and collision
// pull-in so the camera never ends up inside a cliff.
//
// The camera is the thing a third-person game is judged on.  Two details do
// most of the work:
//   1. Smoothing is frame-rate independent (exponential decay, not lerp-per-frame).
//   2. The rig is positioned from the TARGET outward along the view ray and
//      pulled in on obstruction -- moving the target instead would fight the
//      follow smoothing and make the camera feel drunk.
//
// Params: distance=7.5, height=3.2, minPitch=-35, maxPitch=55, sensitivity=0.28

var yaw = 0;
var pitch = 18;
var targetX = 0, targetY = 1.0, targetZ = 0;
var camX = 0, camY = 4, camZ = 8;
var currentDist = 7.5;

function start() {
    var hero = scene.find("Hero");
    if (hero) { targetX = hero.x; targetY = hero.y + height; targetZ = hero.z; }
    currentDist = distance;
}

function update(dt) {
    var hero = scene.find("Hero");
    if (!hero) return;

    readLookInput();

    // --- follow with frame-rate independent smoothing.
    // k = 1 - e^(-rate*dt) is the correct discrete form of exponential decay;
    // using `lerp(a, b, rate*dt)` drifts with frame rate and is the usual cause
    // of "camera feels different on a 120Hz phone".
    var k = 1 - Math.exp(-6.5 * dt);
    targetX += (hero.x - targetX) * k;
    targetY += ((hero.y + height) - targetY) * k;
    targetZ += (hero.z - targetZ) * k;

    collide(hero, dt);

    // --- spherical offset from the target
    var cp = Math.cos(pitch * Math.PI / 180);
    var sp = Math.sin(pitch * Math.PI / 180);
    var desiredX = targetX - Math.sin(yaw * Math.PI / 180) * cp * currentDist;
    var desiredZ = targetZ - Math.cos(yaw * Math.PI / 180) * cp * currentDist;
    var desiredY = targetY + sp * currentDist;

    var ck = 1 - Math.exp(-12 * dt);       // the camera itself snaps faster
    camX += (desiredX - camX) * ck;
    camY += (desiredY - camY) * ck;
    camZ += (desiredZ - camZ) * ck;

    self.setPosition(camX, camY, camZ);
    lookAt(targetX, targetY, targetZ);

    // Publish yaw so CharacterController3D can make movement camera-relative.
    var cc = hero.getAnyScript ? null : null;
    hero.send("setCameraYaw", yaw);
}

function readLookInput() {
    // Drag on the right half of the screen orbits.  Using the drag delta rather
    // than the absolute touch position means the camera does not jump when the
    // player lifts and replaces their thumb.
    if (!input.touching) { lastTouchX = null; lastTouchY = null; return; }
    if (input.touchX < 0) return;                 // left half is the joystick

    if (lastTouchX === null) {
        lastTouchX = input.touchX; lastTouchY = input.touchY;
        return;
    }
    var dx = input.touchX - lastTouchX;
    var dy = input.touchY - lastTouchY;
    lastTouchX = input.touchX; lastTouchY = input.touchY;

    yaw = (yaw + dx * sensitivity * 180) % 360;
    pitch = clamp(pitch - dy * sensitivity * 180, minPitch, maxPitch);
}
var lastTouchX = null, lastTouchY = null;

/**
 * Pulls the camera in when geometry is between it and the target.  A raycast
 * from the target toward the camera finds the obstruction; the camera is then
 * placed just in front of it.
 */
function collide(hero, dt) {
    var dirX = camX - targetX, dirY = camY - targetY, dirZ = camZ - targetZ;
    var len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
    if (len < 0.0001) return;
    dirX /= len; dirY /= len; dirZ /= len;

    var hit = scene.raycast(targetX, targetY, targetZ, dirX, dirY, dirZ, distance);
    var wanted = distance;
    if (hit) wanted = Math.max(1.2, hero.distanceTo3(hit) - 0.4);

    // Ease back out slowly, snap in fast: the asymmetry stops the camera from
    // strobing when the player stands in a doorway.
    var rate = wanted < currentDist ? 18 : 4;
    currentDist += (wanted - currentDist) * (1 - Math.exp(-rate * dt));
}

/** Aims the camera using pitch/yaw directly rather than a lookAt matrix. */
function lookAt(tx, ty, tz) {
    self.rotY = yaw;
    self.rotX = -pitch;
}

function getYaw() { return yaw; }
function getPitch() { return pitch; }

function addYaw(delta) { yaw = (yaw + delta) % 360; }

/** Used by the HUD compass. */
function compassLabel() {
    var a = ((yaw % 360) + 360) % 360;
    if (a < 22.5 || a >= 337.5) return "N";
    if (a < 67.5) return "NE";
    if (a < 112.5) return "E";
    if (a < 157.5) return "SE";
    if (a < 202.5) return "S";
    if (a < 247.5) return "SW";
    if (a < 292.5) return "W";
    return "NW";
}
