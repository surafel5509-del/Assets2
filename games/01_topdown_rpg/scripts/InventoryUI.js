// Emberfall -- InventoryUI.js
// Builds and drives the inventory / equipment panel at runtime.
//
// The panel is authored in the scene as an inactive "InventoryPanel" with a
// backing sprite; this script fills it with a slot grid the first time it is
// opened, then only re-lays-out when the contents change.  Rebuilding every
// frame would allocate constantly on a phone.
//
// Params: columns=6, slotSize=0.55, padX=-2.6, padY=1.4

var built = false;
var shown = false;
var slots = [];

function start() {
    // 'I' on a hardware keyboard toggles the panel; on touch, the bag button
    // calls scene.find("InventoryPanel").send("toggle").
    //
    // Hiding is done with `visible`, not `active`.  ScriptSystem.update() skips
    // any object that is not active in the hierarchy, so a panel that hid itself
    // with active=false also stopped receiving update() -- and update() is what
    // listens for the key that reopens it.  The panel could be closed but never
    // opened again.
    shown = false;
    self.visible = false;
}

function update(dt) {
    if (input.bDown) toggle();
}

function toggle() {
    shown = !shown;
    self.visible = shown;
    if (shown) { rebuild(); audio.play("sfx_ui.ogg"); }
}

function open()  { shown = true;  self.visible = true;  rebuild(); }
function close() { shown = false; self.visible = false; }
function isOpen() { return shown; }

/**
 * Lays the grid out as children of this object.  Positions are local, so the
 * panel can be dragged around the screen without touching slot maths.
 */
function rebuild() {
    var gm = scene.find("GameManager");
    if (!gm) return;
    var state = gm.send("getState");
    if (!state) return;

    // Recycle existing slots instead of destroying and re-creating them.
    var ids = Object.keys(state.inventory);
    var equipped = [];
    if (state.equipped.weapon) equipped.push(state.equipped.weapon);
    if (state.equipped.armor) equipped.push(state.equipped.armor);
    var entries = equipped.concat(ids);

    var count = Math.max(entries.length, columns * 2);
    ensureSlots(count);

    for (var i = 0; i < slots.length; i++) {
        var slot = slots[i];
        if (i < entries.length) {
            var id = entries[i];
            slot.active = true;
            slot.setTexture(id);
            var label = slot.child("Count");
            var n = state.inventory[id];
            if (label) {
                label.text = (n !== undefined) ? String(n)
                             : (id === state.equipped.weapon ? "E" : "");
            }
        } else {
            slot.active = false;
        }
    }

    var title = self.child("Title");
    if (title) title.text = "Inventory  -  Gold " + state.gold;

    var hint = self.child("Hint");
    if (hint) hint.text = "Tap a slot to use it.  B to close.";
    built = true;
}

function ensureSlots(count) {
    while (slots.length < count) {
        var i = slots.length;
        var col = i % columns, row = Math.floor(i / columns);
        var s = scene.spawn("InvSlot", 0, 0);
        if (!s) return;
        s.setParentTo(self);
        s.setPosition(padX + col * (slotSize + 0.12), padY - row * (slotSize + 0.12));
        s.scaleX = slotSize; s.scaleY = slotSize;
        s.tag = "InvSlot";
        s.order = 60 + i;
        var label = s.child("Count");
        if (label) { label.size = 0.5; label.color = "#FFFFFFFF"; }
        // The slot's position in the grid is its index in `slots`; there is no
        // `index` property on a GameObject to stash it on.
        slots.push(s);
    }
}

/**
 * Slot taps arrive here through send("useSlot", index).  Consumables are used
 * immediately; weapons and armour are equipped.
 */
function useSlot(index) {
    var gm = scene.find("GameManager");
    if (!gm || index === undefined) return;
    var state = gm.send("getState");
    var ids = Object.keys(state.inventory);
    var equipped = [];
    if (state.equipped.weapon) equipped.push(state.equipped.weapon);
    if (state.equipped.armor) equipped.push(state.equipped.armor);
    var entries = equipped.concat(ids);
    var id = entries[index];
    if (!id) return;

    if (id === "icon_sword.png" || id === "icon_axe.png") {
        state.equipped.weapon = id;
        log("Equipped " + id);
    } else if (id === "icon_shield.png" || id === "icon_helmet.png" || id === "icon_shirt.png") {
        state.equipped.armor = id;
        state.defense += 1;
        log("Equipped " + id);
    } else {
        gm.send("useItem", id);
    }
    audio.play("sfx_ui.ogg");
    rebuild();
}

function onTap() { /* panel background absorbs taps so they do not hit the world */ }
