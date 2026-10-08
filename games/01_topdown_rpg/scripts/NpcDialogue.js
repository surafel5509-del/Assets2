// Emberfall -- NpcDialogue.js
// Branching dialogue with typed-out pages.
//
// Each NPC object carries its pages in the Script component's params, e.g.
//   pages=Welcome to Emberfall.|The ruins east of here are not safe.|Take this potion.,gives=icon_potion.png
// A page may end with "?yes/no" to branch; the answer is remembered on the
// GameManager so an NPC does not hand out the same reward twice.
//
// Attach to each NPC.

var pages = [];
var pageIndex = -1;
var charIndex = 0;
var typeSpeed = 38;          // characters per second
var box = null;
var label = null;
var talking = false;
var gaveReward = false;

function start() {
    // Params arrive as strings; split the page list on '|'.
    if (typeof pageText === "string" && pageText.length > 0) {
        pages = pageText.split("|");
    } else {
        pages = ["..."];
    }
    box = scene.find("DialogueBox");
    label = scene.find("DialogueText");
    if (box) box.active = false;
}

function update(dt) {
    if (!talking) { idleAnimate(dt); return; }

    // Type the page out; tap to skip straight to the full line.
    if (charIndex < current().length) {
        charIndex += dt * typeSpeed;
        if (input.tapped || input.aDown) charIndex = current().length;
        if (label) label.text = current().substring(0, Math.floor(charIndex));
    } else if (input.aDown || input.tapped) {
        advance();
    }
}

/** NPCs bob gently when idle so the world does not look frozen. */
var idlePhase = 0;
function idleAnimate(dt) {
    idlePhase += dt;
    self.y = baseY + Math.sin(idlePhase * 1.6) * 0.03;
}
var baseY = 0;
function setBaseY(y) { baseY = y; }

function current() { return pages[pageIndex] || ""; }

/** Player interaction calls npc.send("talk"). */
function talk() {
    if (talking) { advance(); return; }
    talking = true;
    pageIndex = 0;
    charIndex = 0;
    if (box) box.active = true;
    if (label) label.text = "";
    audio.play("sfx_ui.ogg");

    // Face the player: pick the row that looks at them.
    var player = scene.find("Player");
    if (player) {
        var facingLeft = player.worldX < self.worldX;
        self.play(facingLeft ? "walk_left" : "walk_right");
        self.stopAnimation();
    }
}

function advance() {
    pageIndex++;
    charIndex = 0;
    if (pageIndex >= pages.length) { finish(); return; }
    audio.play("sfx_ui.ogg");
}

function finish() {
    talking = false;
    pageIndex = -1;
    if (box) box.active = false;
    if (label) label.text = "";
    self.play("walk_down");
    self.stopAnimation();

    // Hand out the reward once.  The flag lives on this object, which is
    // destroyed with the scene, so a reload re-arms the NPC -- intended, since
    // scene.reload() is also how the player respawns after dying.
    if (!gaveReward && typeof gives === "string" && gives.length > 0) {
        gaveReward = true;
        var gm = scene.find("GameManager");
        if (gm) { gm.send("addItem", gives, 1); log("Received " + gives); }
    }
}

function isTalking() { return talking; }
