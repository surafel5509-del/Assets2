// Neon Overdrive -- CameraRig.js
// Chase camera with a speed-driven FOV kick.
//
// The FOV kick is the cheapest possible speed feedback and the most effective:
// widening the field of view as the car accelerates makes the same world units
// per second feel dramatically faster without changing the actual speed.  It is
// clamped hard, because past ~85 degrees the edges of the screen start to
// distort badly and players report motion sickness.
//
// Params: distance=9.5, height=4.2, baseFov=62, maxFov=82, smoothing=7

var camX = 0, camY = 4.2, camZ = 9.5;

function start() {
    var car = scene.find("Vehicle");
    if (car) { camX = car.x; camZ = car.z + distance; camY = car.y + height; }
}

function update(dt) {
    var car = scene.find("Vehicle");
    if (!car) return;

    var speed = car.send("getSpeed") || 0;
    var maxSpeed = 52;
    var t = clamp(speed / maxSpeed, 0, 1);

    // --- position: behind and above, with a lateral lead into the turn.
    // Leading slightly toward the car's lateral velocity keeps the car off the
    // screen edge during hard steering, which is where you most need to see it.
    var leadX = clamp((car.x - camX) * 0.35, -2.2, 2.2);
    var wantX = car.x * 0.55 + leadX;
    var wantY = car.y + height + (car.send("isAirborne") ? 0.8 : 0);
    var wantZ = car.z + distance + t * 1.6;      // pull back a touch with speed

    // Frame-rate independent smoothing, same reasoning as the RPG camera:
    // a per-frame lerp makes the camera behave differently at 30 vs 120Hz.
    var k = 1 - Math.exp(-smoothing * dt);
    camX += (wantX - camX) * k;
    camY += (wantY - camY) * (1 - Math.exp(-5 * dt));   // vertical eases slower
    camZ += (wantZ - camZ) * k;

    self.setPosition(camX, camY, camZ);

    // --- aim slightly above the car so the track ahead stays in frame
    var dx = car.x - camX, dy = (car.y + 0.8) - camY, dz = car.z - camZ;
    var horiz = Math.sqrt(dx * dx + dz * dz);
    self.rotY = Math.atan2(dx, -dz) * 180 / Math.PI;
    self.rotX = -Math.atan2(dy, horiz) * 180 / Math.PI;

    // --- FOV kick
    var wantFov = baseFov + (maxFov - baseFov) * t;
    if (car.send("isNitroActive")) wantFov += 4;
    // Was send("setFov"), which the engine dispatched to nothing: Camera3D.fov
    // is now a scriptable property, so the speed kick actually reaches it.
    self.fov = clamp(wantFov, 55, maxFov + 4);

    // Shake on nitro, not on normal speed: constant shake is just annoying.
    if (car.send("isNitroActive") && time.frame % 4 === 0) scene.shake(0.02);
}

/** Cut straight to the target -- used when respawning so there is no fly-in. */
function snap() {
    var car = scene.find("Vehicle");
    if (!car) return;
    camX = car.x; camY = car.y + height; camZ = car.z + distance;
    self.setPosition(camX, camY, camZ);
}
