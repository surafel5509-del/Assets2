// Sunken Citadel -- Fx.js
//
// Pooled combat effects: swing arcs, impacts, hit sparks, crumble bursts, the
// quake ring, and the summon flash a Hollow arrives in.  Every one is an
// inactive template cloned by scene.spawn() and destroyed on a timer, which is
// how the engine pools.
//
// Params: shortLife=0.5, ringLife=1.1

function update(dt) {
}

function oneShot(proto, x, y, z, burst, life) {
    var go = scene.spawn(proto, x, y, z);
    if (!go) return null;
    if (burst > 0) go.burst(burst);
    after(life, function () { go.destroy(); });
    return go;
}

function swing(x, y, z, facing, heavy) {
    var go = oneShot("SlashFx", x, y, z, heavy ? 22 : 12, shortLife);
    if (go) { go.rotY = facing; go.scaleX = heavy ? 1.8 : 1.3; go.scaleZ = go.scaleX; }
}

function impact(x, y, z) {
    oneShot("ImpactFx", x, y, z, 16, shortLife);
}

function hit(x, y, z) {
    oneShot("SparkFx", x, y, z, 10, shortLife);
}

function crumble(x, z) {
    oneShot("DebrisFx", x, 0.5, z, 26, ringLife);
}

function quake(x, z) {
    oneShot("QuakeFx", x, 0.1, z, 34, ringLife * 1.4);
}

function summon(x, z) {
    oneShot("SummonFx", x, 0.2, z, 20, ringLife);
}

function roll(x, z) {
    oneShot("DustFx", x, 0.2, z, 14, shortLife);
}

function wave(x, z) {
    oneShot("SummonFx", x, 0.2, z, 40, ringLife * 1.5);
}

function pickup(x, y, z) {
    oneShot("SparkFx", x, y, z, 14, shortLife);
}
