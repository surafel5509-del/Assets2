// Hollow Ridge -- InteractionSystem.js
// Context-sensitive world interaction: levers, chests, campfires and signs.
//
// The rule for what is interactable is "closest eligible object inside range",
// resolved every frame but only re-resolved when the answer could have changed.
// The prompt is a single reused object rather than one per interactable, so
// walking through a village does not allocate.
//
// Params: range=2.4

var current = null;
var promptTimer = 0;

function start() {
    var p = scene.find("InteractPrompt");
    if (p) p.active = false;
}

function update(dt) {
    var hero = scene.find("Hero");
    if (!hero) { clearPrompt(); return; }

    var best = null, bestD = range;
    best = scan(hero, "Interactable", best, bestD);
    bestD = best ? best.distanceTo(hero) : range;

    if (best !== current) {
        current = best;
        if (best) showPrompt(best);
        else clearPrompt();
    }

    // Bob the prompt so it reads as a UI element, not world geometry.
    if (current) {
        promptTimer += dt;
        var p = scene.find("InteractPrompt");
        if (p) p.y = current.y + 1.6 + Math.sin(promptTimer * 3) * 0.06;
    }

    if (input.bDown && current) {
        current.send("interact");
        audio.play("sfx_ui.ogg");
    }
}

function scan(hero, tag, best, bestD) {
    var all = scene.findAll(tag);
    for (var i = 0; i < all.length; i++) {
        if (!all[i].active) continue;
        var d = all[i].distanceTo(hero);
        if (d < bestD) { bestD = d; best = all[i]; }
    }
    return best;
}

function showPrompt(obj) {
    var p = scene.find("InteractPrompt");
    if (!p) return;
    p.active = true;
    p.setPosition(obj.x, obj.y + 1.6, obj.z);
    var label = scene.find("InteractLabel");
    if (label) label.text = labelFor(obj);
}

/** The verb depends on what the object is, which is what makes it "context". */
function labelFor(obj) {
    var t = (obj.tag || "").toLowerCase();
    var n = (obj.name || "").toLowerCase();
    if (n.indexOf("chest") >= 0) return "Open chest";
    if (n.indexOf("lever") >= 0) return "Pull lever";
    if (n.indexOf("campfire") >= 0) return "Rest at campfire";
    if (n.indexOf("sign") >= 0) return "Read sign";
    if (n.indexOf("door") >= 0) return "Open door";
    if (t === "npc") return "Talk";
    return "Interact";
}

function clearPrompt() {
    var p = scene.find("InteractPrompt");
    if (p) p.active = false;
    current = null;
}

function getCurrent() { return current; }
