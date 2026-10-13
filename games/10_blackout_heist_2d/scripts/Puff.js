// Blackout Heist -- Puff.js
//
// A one-shot puff of dust: the emitter fires once when the clone appears and the
// copy removes itself when its life runs out.  Spawning a short-lived clone is
// how this game bursts particles somewhere that has no pooled emitter of its own.
//
// Params: count=12, life=1.1

var left = 0.0;

function start() {
    left = life;
    self.burst(count);
}

function update(dt) {
    left -= dt;
    if (left <= 0.0) self.destroy();
}
