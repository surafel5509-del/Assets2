// Blade & Bulwark -- HitboxDebug.js
// Draws the live hurtboxes and the active hitboxes.
//
// Frame data is invisible by nature: a move that feels unfair is almost always
// a hitbox that is active one frame longer than intended, or a hurtbox that
// extends past the art.  This overlay makes both checkable in seconds, which is
// why it ships in the game rather than living only in the editor.
//
// Toggle with the on-screen "HITBOX" button, or scene.find("HitboxDebug").send("toggle").
// Params: none.

var enabled = false;
var boxes = [];
var HURT_COLOR = "#664CC26A";      // green, translucent
var HIT_COLOR = "#99D0453F";       // red, translucent

function start() {
    enabled = false;
    var btn = scene.find("HitboxButton");
    if (btn) btn.active = true;
}

function update(dt) {
    if (!enabled) { clearBoxes(); return; }

    clearBoxes();
    draw("Fighter_P1");
    draw("Fighter_P2");
}

function draw(fighterName) {
    var f = scene.find(fighterName);
    if (!f || !f.active) return;

    var hurt = f.send("getHurtboxWorld");
    if (hurt) spawnBox(hurt, HURT_COLOR, -1);

    // The hitbox only exists on active frames; drawing it any other time would
    // misrepresent exactly the thing this overlay is for.
    var hit = f.send("getHitboxWorld");
    if (hit) spawnBox(hit, HIT_COLOR, 1);
}

function spawnBox(b, color, order) {
    var o = scene.spawn("DebugBox", b[0], b[1]);
    if (!o) return;
    o.color = color;
    o.scaleX = b[2] * 2;      // the debug sprite is a unit square, so scale = size
    o.scaleY = b[3] * 2;
    o.order = 200 + order;
    boxes.push(o);
}

function clearBoxes() {
    for (var i = 0; i < boxes.length; i++) {
        if (boxes[i]) boxes[i].destroy();
    }
    boxes.length = 0;
}

function toggle() {
    enabled = !enabled;
    var label = scene.find("HitboxButton");
    if (label) label.text = enabled ? "HITBOX ON" : "HITBOX";
    if (!enabled) clearBoxes();
    log("Hitbox overlay: " + (enabled ? "on" : "off"));
}

function onTap() { toggle(); }

function onStop() { clearBoxes(); }
