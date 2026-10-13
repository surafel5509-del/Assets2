// Crypt of the Ember Sigil -- Fx.js
// One-shot particle burst: the emitter fires once when the clone is spawned and
// the copy removes itself when its life runs out.  Pooling the burst objects is
// Delve's job; this script only owns its own lifetime.
var life = 1.1;

function start() { self.burst(14); }

function update(dt) {
    life -= dt;
    if (life <= 0) self.destroy();
}
