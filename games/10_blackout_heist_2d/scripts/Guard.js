// Blackout Heist -- Guard.js
//
// One guard, four states: patrol, suspect, chase, search.
//
// The vision test is the character.  A guard sees you only when all three are
// true -- inside its range, inside its cone, and with an unbroken line of sight
// across the level grid -- which is what makes a corner worth trusting.  Range
// grows by 45% if you are standing in a lamp pool, shrinks if you are creeping,
// and a running thief is heard through the walls at `noise + hearing`.
//
// Suspicion is per guard and visible: a marker above its head goes pale yellow,
// amber, red as the meter fills, so the player is never told "spotted" without
// warning.  The shared alarm lives in the director, not here.
//
// Params: speed=2.4, chaseSpeed=4.2, sight=7.5, cone=95, nearCone=160,
//         nearSight=2.6, litBonus=1.45, detectRate=0.8, decay=0.45,
//         hearing=3.2, hearRate=0.75, catchRange=0.62, route=x:y|x:y|...

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
var wp = [];                                   // waypoint pairs, parsed in start()
var route = typeof route === "undefined" ? "" : route;
var wpi = 0;
var facing = 0.0;
var state = "patrol";
var detect = 0.0;
var lastX = 0.0, lastY = 0.0;
var searchT = 0.0;
var stuckT = 0.0;
var mark = null;
var alarm = 0.0;
var seen = false;

function cellX(x) { return Math.floor(x + MAP_W * 0.5); }
function cellY(y) { return Math.floor(MAP_H * 0.5 - y); }
function cellSolid(cx, cy) {
    if (cx < 0 || cy < 0 || cx >= MAP_W || cy >= MAP_H) return true;
    return MAP[cy].charAt(cx) !== ".";
}
function blocked(x, y) {
    var r = 0.34;
    return cellSolid(cellX(x - r), cellY(y - r)) || cellSolid(cellX(x + r), cellY(y - r)) ||
           cellSolid(cellX(x - r), cellY(y + r)) || cellSolid(cellX(x + r), cellY(y + r));
}

function start() {
    var parts = ("" + route).split("|");
    for (var i = 0; i < parts.length; i++) {
        var xy = parts[i].split(":");
        wp.push([parseFloat(xy[0]), parseFloat(xy[1])]);
    }
    mark = scene.find(self.name + "Mark");
    self.play("guard_idle_up");
}

function update(dt) {
    var t = scene.find("Thief");
    var h = scene.find("Heist");
    if (!t || !h) return;
    var over = h.send("isOver");
    alarm = h.send("getAlarm");

    seen = !over && t.getVisible() && canSee(t);
    if (seen) {
        detect = Math.min(1.0, detect + detectRate * dt * visibility(t));
        lastX = t.x; lastY = t.y;
        if (detect >= 1.0) {
            if (state !== "chase") {
                audio.play("sfx_alarm.ogg", 0.75);
                var fx = scene.find("Fx");
                if (fx) fx.send("spot", pack(self.x, self.y));
                h.send("spotted", self.name);
            }
            state = "chase";
        } else if (state !== "chase") {
            state = "suspect";
        }
    } else {
        detect = Math.max(0.0, detect - decay * dt);
        // Ears: a running thief is audible through a wall, a creeping one is not.
        var heard = over ? 0.0 : (t.send("getNoise") || 0.0);
        if (heard > 0.0) {
            var dxh = t.x - self.x, dyh = t.y - self.y;
            var dh = Math.sqrt(dxh * dxh + dyh * dyh);
            if (dh < heard + hearing) {
                detect = Math.min(1.0, detect + hearRate * dt * (1.0 - dh / (heard + hearing)));
                lastX = t.x; lastY = t.y;
                if (state === "patrol") state = "suspect";
            }
        }
        if (state === "chase" && detect < 0.25) { state = "search"; searchT = 5.0; }
    }
    paint();

    var tx, ty, sp;
    if (state === "chase") {
        tx = t.x; ty = t.y; sp = chaseSpeed;
        if (over) { tx = self.x; ty = self.y; sp = 0.0; }
        else if (seen && distTo(t.x, t.y) < catchRange) { t.send("bust"); return; }
    } else if (state === "search") {
        searchT -= dt;
        tx = lastX; ty = lastY; sp = speed * 1.1;
        if (searchT <= 0.0) state = "patrol";
    } else if (state === "suspect") {
        tx = lastX; ty = lastY; sp = speed * 1.2;
        if (Math.abs(self.x - tx) < 0.4 && Math.abs(self.y - ty) < 0.4) state = "patrol";
    } else {
        var p = wp[wpi];
        tx = p[0]; ty = p[1]; sp = speed;
        if (alarm > 0.5) sp = speed * 1.35;     // a guard at the alarm hurries
        if (Math.abs(self.x - tx) < 0.3 && Math.abs(self.y - ty) < 0.3) {
            wpi = (wpi + 1) % wp.length;
        }
    }

    var dx = tx - self.x, dy = ty - self.y;
    var d = Math.sqrt(dx * dx + dy * dy);
    if (d > 0.05 && sp > 0.0) {
        var step = Math.min(sp * dt, d);
        var nx = self.x + dx / d * step;
        var ny = self.y + dy / d * step;
        var moved = false;
        if (!blocked(nx, self.y)) { self.setPosition(nx, self.y, 0.0); moved = true; }
        if (!blocked(self.x, ny)) { self.setPosition(self.x, ny, 0.0); moved = true; }
        if (!moved) {
            // Slide along whatever is in the way rather than stopping dead: a
            // guard that sticks to a corner reads as broken, not as careful.
            stuckT += dt;
            var sx = -dy / d * step, sy = dx / d * step;
            if (stuckT % 0.6 < 0.3) {
                if (!blocked(self.x + sx, self.y + sy)) self.setPosition(self.x + sx, self.y + sy, 0.0);
            } else if (!blocked(self.x - sx, self.y - sy)) {
                self.setPosition(self.x - sx, self.y - sy, 0.0);
            }
            if (stuckT > 3.0) { stuckT = 0.0; wpi = (wpi + 1) % wp.length; }
        } else {
            stuckT = 0.0;
        }
        facing = Math.atan2(dx, dy) * 180.0 / Math.PI;
        self.rotZ = 0.0;
        self.play("guard_walk_" + dirName(dx, dy));
    } else {
        self.play("guard_idle_" + dirName(Math.sin(facing * Math.PI / 180.0),
                                          Math.cos(facing * Math.PI / 180.0)));
    }
}

function dirName(dx, dy) {
    if (Math.abs(dx) > Math.abs(dy)) return dx > 0.0 ? "right" : "left";
    return dy > 0.0 ? "up" : "down";
}

function distTo(x, y) {
    var dx = x - self.x, dy = y - self.y;
    return Math.sqrt(dx * dx + dy * dy);
}

/** Range, cone (wider up close), then an exact line-of-sight walk. */
function canSee(t) {
    var dx = t.x - self.x, dy = t.y - self.y;
    var d = Math.sqrt(dx * dx + dy * dy);
    var reach = sight * (t.send("isLit") ? litBonus : 1.0);
    if (d > reach) return false;
    if (determine(t, dx, dy, d) === false) return false;
    return clearPath(dx, dy, d);
}

function determine(t, dx, dy, d) {
    var ang = Math.atan2(dx, dy) * 180.0 / Math.PI;
    var delta = ((ang - facing + 540.0) % 360.0) - 180.0;
    var half = (d < nearSight ? nearCone : cone) * 0.5;
    return Math.abs(delta) <= half;
}

/** How fast suspicion fills: closer, lit and running all matter. */
function visibility(t) {
    var m = t.send("isSneaking") ? 0.55 : 1.0;
    if (t.send("isLit")) m *= 1.3;
    return m;
}

function clearPath(dx, dy, d) {
    var steps = Math.max(2, Math.round(d * 2));
    for (var i = 1; i < steps; i++) {
        var f = i / steps;
        if (cellSolid(cellX(self.x + dx * f), cellY(self.y + dy * f))) return false;
    }
    return true;
}

function paint() {
    if (!mark) return;
    if (detect <= 0.01 && state !== "chase" && state !== "search") {
        mark.setVisible(false);
        return;
    }
    mark.setVisible(true);
    mark.setColor(detect >= 1.0 ? "#FFFF4A38" : (detect > 0.45 ? "#FFFFC24A" : "#FFEDE08A"));
    var s = 0.3 + detect * 0.5;
    mark.scaleX = s; mark.scaleY = s;
    mark.setPosition(self.x, self.y + 0.95, 0.0);
}

function pack(x, y) {
    return "" + Math.round(x * 10.0) / 10.0 + ":" + Math.round(y * 10.0) / 10.0;
}

function getState() { return state; }
function getDetect() { return detect; }
