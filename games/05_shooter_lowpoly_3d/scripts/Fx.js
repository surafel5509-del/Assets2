// One-shot effect: the emitter bursts once, then the copy removes itself.
var life = 1.4;

function update(dt) {
  life -= dt;
  if (life <= 0) self.destroy();
}
