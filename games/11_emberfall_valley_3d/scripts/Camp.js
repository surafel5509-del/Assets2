// Emberfall Valley -- Camp.js
//
// The camp fire: the one place the night cannot reach, and the place the ranger
// can sleep through it.  Sleeping is a real state change -- the director's clock
// jumps to the next morning and the ranger is warmed.
//
// Params: reach=3.4, sleepFrom=19.5, sleepTo=5.5

var cooldown = 0.0;

function update(dt) {
    if (cooldown > 0.0) cooldown -= dt;
    var light = self.lightIntensity;
    self.lightIntensity = 2.5 + Math.sin(time.time * 9.1) * 0.45;
}

function canInteract() { return cooldown <= 0.0; }
function prompt() { return "Rest at the fire  (" + reach + "m)"; }

function interact() {
    if (cooldown > 0.0) return false;
    var v = scene.find("Valley");
    var w = scene.find("Ranger");
    if (!v || !w) return false;
    var clock = v.send("getClock");
    cooldown = 3.0;
    if (clock > sleepFrom || clock < sleepTo) {
        v.send("setClock", sleepTo);
        v.send("say", "You sleep by the fire. Dawn comes cold and clear.");
        audio.play("sfx_dawn.ogg");
    } else {
        w.send("addResource", "berry", 1);
        v.send("say", "You eat by the fire and rest a while.");
        audio.play("sfx_ui.ogg", 0.6);
    }
    return true;
}
