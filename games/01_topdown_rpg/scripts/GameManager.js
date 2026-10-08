// Emberfall -- GameManager.js
// Owns run state: health, gold, inventory, respawn and HUD plumbing.
// Everything else in the game talks to this object through scene.find("GameManager").send(...).
//
// Attach to the "GameManager" object. No renderer needed.

var STATE = {
    hp: 100,
    maxHp: 100,
    gold: 0,
    level: 1,
    xp: 0,
    xpNext: 50,
    attack: 12,
    defense: 2,
    // item id -> count.  Ids match the icon assets declared in game.json.
    inventory: { "icon_potion.png": 3, "icon_food.png": 1 },
    equipped: { weapon: "icon_sword.png", armor: null },
    kills: 0,
    dead: false
};

function start() {
    log("Emberfall: new run");
    refreshHud();
}

function update(dt) {
    // Slow health regeneration out of combat; keeps long sessions playable
    // without turning the game into a waiting simulator.
    if (!STATE.dead && STATE.hp < STATE.maxHp) {
        regen += dt;
        if (regen > 4) { regen = 0; heal(1); }
    }
}
var regen = 0;

// ------------------------------------------------------------------ messages
/** Other scripts call: scene.find("GameManager").send("damagePlayer", amount) */
function damagePlayer(amount) {
    if (STATE.dead) return 0;
    var real = Math.max(1, amount - STATE.defense);
    STATE.hp -= real;
    refreshHud();
    var player = scene.find("Player");
    if (player) { player.send("onHurt"); scene.shake(0.25); }
    if (STATE.hp <= 0) { STATE.hp = 0; die(); }
    return real;
}

function heal(amount) {
    STATE.hp = clamp(STATE.hp + amount, 0, STATE.maxHp);
    refreshHud();
}

function gainXp(amount) {
    STATE.xp += amount;
    while (STATE.xp >= STATE.xpNext) {
        STATE.xp -= STATE.xpNext;
        STATE.level++;
        STATE.xpNext = Math.floor(STATE.xpNext * 1.6);
        STATE.maxHp += 15;
        STATE.hp = STATE.maxHp;
        STATE.attack += 3;
        STATE.defense += 1;
        log("Level up! Now level " + STATE.level);
        audio.play("sfx_ui.ogg");
        var player = scene.find("Player");
        if (player) player.burst(30);
    }
    refreshHud();
}

function addGold(amount) {
    STATE.gold += amount;
    refreshHud();
}

function addItem(id, n) {
    STATE.inventory[id] = (STATE.inventory[id] || 0) + (n || 1);
    refreshHud();
}

function useItem(id) {
    if (!STATE.inventory[id] || STATE.inventory[id] <= 0) return false;
    STATE.inventory[id]--;
    if (STATE.inventory[id] <= 0) delete STATE.inventory[id];
    if (id === "icon_potion.png") heal(40);
    if (id === "icon_food.png") heal(20);
    audio.play("sfx_ui.ogg");
    refreshHud();
    return true;
}

function die() {
    STATE.dead = true;
    log("You have fallen.");
    var banner = scene.find("DeathBanner");
    if (banner) banner.active = true;
    after(2.5, function () {
        STATE.hp = STATE.maxHp;
        STATE.dead = false;
        STATE.gold = Math.floor(STATE.gold / 2);   // death costs half your gold
        scene.reload();
    });
}

function refreshHud() {
    var hp = scene.find("HealthText");
    if (hp) hp.text = "HP " + Math.ceil(STATE.hp) + "/" + STATE.maxHp;

    var bar = scene.find("HealthBar");
    if (bar) {
        // The bar is a child of a fixed-width backing sprite, so scaling X is
        // the cheapest correct fill.
        bar.scaleX = clamp(STATE.hp / STATE.maxHp, 0.001, 1);
    }

    var gold = scene.find("GoldText");
    if (gold) gold.text = "Gold " + STATE.gold;

    var lvl = scene.find("LevelText");
    if (lvl) lvl.text = "Lv " + STATE.level + "  XP " + STATE.xp + "/" + STATE.xpNext;

    var inv = scene.find("InventoryPanel");
    if (inv && inv.visible) inv.send("rebuild");
}

function getState() { return STATE; }
function isDead() { return STATE.dead; }
