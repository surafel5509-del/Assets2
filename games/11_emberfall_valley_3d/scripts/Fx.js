// Emberfall Valley -- Fx.js
//
// The pooled one-shot effects: footfall dust, a landing puff, a gather sparkle,
// and the mist that drifts down the valley when the weather turns.  Effects are
// spawned from an inactive template and destroy themselves, which is the
// engine's idiomatic way to pool -- one definition, N clones, no factory code
// per effect.
//
// Params: dustLife=0.7, sparkLife=0.6

function start() {
    var w = scene.find("Weather");
    if (w) w.setEmitting(false);
}

function update(dt) {
}

function dust(x, z) {
    var go = scene.spawn("DustFx", x, 0.15, z);
    if (go) after(dustLife, function () { go.destroy(); });
}

function landing(x, z) {
    var go = scene.spawn("DustFx", x, 0.1, z);
    if (go) {
        go.burst(10);
        after(dustLife * 1.4, function () { go.destroy(); });
    }
}

function pickup(x, y, z) {
    var go = scene.spawn("SparkFx", x, y, z);
    if (go) {
        go.burst(14);
        after(sparkLife, function () { go.destroy(); });
    }
}
