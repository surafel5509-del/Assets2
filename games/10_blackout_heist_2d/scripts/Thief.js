// Blackout Heist -- Thief.js
//
// The player.  Eight-way movement with the grid tested on each axis separately,
// so a body slides along a wall instead of sticking to it.  Three gaits:
//
//   creep   (Sneak toggled)  1.5 u/s, a 1.2-unit noise, 55% as easy to spot
//   walk    (stick pushed a little)  ~3 u/s
//   run     (stick pushed hard)  ~4.6 u/s, a 5.4-unit noise
//
// Everything else the thief does -- take the keycard, crack a crate, cut the
// camera feed, climb into the van -- is one held action pointed at whatever is
// nearest and close enough, which is the only interface a touch screen needs.
//
// Params: creepSpeed=1.5, walkSpeed=3.0, runSpeed=4.6, radius=0.34,
//         interactRange=1.35, creepNoise=1.2, walkNoise=2.4, runNoise=5.4

var MAP = [
  "############################################################",
  "############################################################",
  "############################################################",
  "############################################################",
  "############################################################",
  "############################################################",
  "############################################################",
  "############################################################",
  "############################################################",
  "############################################################",
  "#########################.............####...............###",
  "#########################.............####...............###",
  "###x...............x#####.x.x.x.x.x.x.####......x.....xx.###",
  "###..............x..#####....................x...........###",
  "###..............x..#####...........................x....###",
  "###..x..x..x..x..x..#####................................###",
  "###.................#####.x.x.x.x.x.x.####...............###",
  "###.................#####...x.........####...............###",
  "###.................#####.............####....x..........###",
  "###..x..x..x..x..x..#####.............####...............###",
  "###.................#########...############################",
  "###.................###.................................####",
  "###.........x.............x....x....x....x....x....xx...####",
  "###x...............x........x...............x...........####",
  "########...#########....................................####",
  "########...#####################################...#########",
  "########...#####################################...#########",
  "########...###############################x.............x###",
  "########...###############################............x..###",
  "########...###############################...............###",
  "###x........x....xx.######################...............###",
  "###....xx.....xx....######################...............###",
  "###....x......xx....######################...xx..xx......###",
  "###xx.........xx....######################...x...x.......###",
  "###xx.........xx....######################...............###",
  "###xx...............######################...............###",
  "###...x...........x.######################...............###",
  "###.x...............######################...............###",
  "###.x...............####################..x.x...........x###",
  "###.x...............####################...#################",
  "###......x...x........................x....#################",
  "##########..................x..............#################",
  "##########.....x...........................#################",
  "############################################################"
];
var MAP_W = 60;
var MAP_H = 44;
var facing = "down";
var sneaking = false;
var noise = 0.0;
var busy = 0.0;
var target = null;
var promptText = "";
var caught = false;
var escaped = false;
var stepPhase = 0.0;

function cellX(x) { return Math.floor(x + MAP_W * 0.5); }
function cellY(y) { return Math.floor(MAP_H * 0.5 - y); }

function cellSolid(cx, cy) {
    if (cx < 0 || cy < 0 || cx >= MAP_W || cy >= MAP_H) return true;
    return MAP[cy].charAt(cx) !== ".";
}

/** Does the thief's footprint touch a blocked cell at (x, y)? */
function blocked(x, y) {
    var r = radius;
    return cellSolid(cellX(x - r), cellY(y - r)) || cellSolid(cellX(x + r), cellY(y - r)) ||
           cellSolid(cellX(x - r), cellY(y + r)) || cellSolid(cellX(x + r), cellY(y + r));
}

function start() {
    self.Order = 10;
}

function update(dt) {
    var h = scene.find("Heist");
    var over = h ? h.send("isOver") : false;
    if (caught || escaped || over) {
        noise = 0.0;
        self.play("thief_idle_" + facing);
        return;
    }

    // Sneak is a toggle: a hold on a virtual stick is a fight with the thumb,
    // and a toggle is also what the headless harness can press.
    if (input.actionPressed("Sneak")) {
        sneaking = !sneaking;
        if (h) h.send("onSneak", sneaking ? 1.0 : 0.0);
    }

    var ix = input.axisX, iy = input.axisY;
    var mag = Math.sqrt(ix * ix + iy * iy);
    if (mag > 1.0) { ix /= mag; iy /= mag; mag = 1.0; }

    var speed = sneaking ? creepSpeed : walkSpeed + (runSpeed - walkSpeed) * mag;
    noise = 0.0;
    if (mag > 0.08) {
        facing = Math.abs(ix) > Math.abs(iy) ? (ix > 0.0 ? "right" : "left")
                                            : (iy > 0.0 ? "up" : "down");
        var nx = self.x + ix * speed * dt;
        if (!blocked(nx, self.y)) self.setPosition(nx, self.y, 0.0);
        var ny = self.y + iy * speed * dt;
        if (!blocked(self.x, ny)) self.setPosition(self.x, ny, 0.0);

        // The noise radius is what the guards hear through the walls, and it is
        // drawn on screen as the dust the boots kick up.
        noise = sneaking ? creepNoise : (mag > 0.7 ? runNoise : walkNoise);
        stepPhase += dt * (sneaking ? 4.0 : 9.0 + 4.0 * mag);
        if (stepPhase > 1.0) {
            stepPhase = 0.0;
            audio.play("sfx_step.ogg", sneaking ? 0.1 : 0.28);
        }
        self.play("thief_walk_" + facing);
        self.setAnimSpeed(sneaking ? 0.75 : 1.0 + 0.35 * mag);
    } else {
        self.play("thief_idle_" + facing);
        self.setAnimSpeed(1.0);
    }

    // ---- interactables: nearest in range, then held
    var best = null, bestD = interactRange;
    var list = scene.findAll("Interact");
    for (var i = 0; i < list.length; i++) {
        var o = list[i];
        var dx = o.x - self.x, dy = o.y - self.y;
        var d = Math.sqrt(dx * dx + dy * dy);
        if (d < bestD) { bestD = d; best = o; }
    }
    if (best !== target) {
        if (target) target.send("interactCancel");
        target = best;
        busy = 0.0;
    }
    promptText = best ? (best.send("prompt") || "") : "";
    if (best && input.action("Interact")) {
        var p = best.send("interact", dt);
        busy = p === null || p === undefined ? 0.0 : p;
    } else {
        busy = 0.0;
        if (best) best.send("interactCancel");
    }
    var hud = scene.find("Hud");
    if (hud) { hud.send("prompt", promptText); hud.send("hold", busy); }
}

function say(msg) {
    var h = scene.find("Heist");
    if (h) h.send("say", msg);
}

function getFacing() { return facing; }
function isSneaking() { return sneaking; }
function getNoise() { return noise; }
function isCaught() { return caught; }
function isEscaped() { return escaped; }

/** How well lit the thief is: the pools on the floor are the light. */
function isLit() {
    var lights = scene.findAll("Light");
    for (var i = 0; i < lights.length; i++) {
        var o = lights[i];
        var r = Math.max(o.scaleX, o.scaleY) * 0.5;
        var dx = o.x - self.x, dy = o.y - self.y;
        if (dx * dx + dy * dy < r * r) return true;
    }
    return false;
}

/** Called by a guard that has closed the distance. */
function bust() {
    if (caught || escaped) return;
    caught = true;
    noise = 0.0;
    audio.play("sfx_caught.ogg", 0.9);
    var fx = scene.find("Fx");
    if (fx) fx.send("alarm", pack(self.x, self.y));
    var h = scene.find("Heist");
    if (h) h.send("onCaught");
}

/** Called by the van once the loot is on board. */
function escape() {
    if (caught || escaped) return;
    escaped = true;
    noise = 0.0;
    self.setVisible(false);
    var h = scene.find("Heist");
    if (h) h.send("onEscape");
}

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}
