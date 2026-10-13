// Blackout Heist -- Cam.js
//
// A security camera, and -- with a wider beam and a slower sweep -- the
// searchlight over the loading dock.  Same script, different numbers: both
// point, both fill the alarm while they hold you, and both stop existing when
// the terminal in the server room is used.
//
// Params: centre=180, sweep=46, speed=24, sight=10, cone=56, rate=0.62

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
var t = 0.0;
var down = false;
var beam = null;
var facing = 0.0;
var hot = false;

function start() {
    beam = scene.find(self.name + "Beam");
    facing = centre;
}

function update(dt) {
    var h = scene.find("Heist");
    if (!h) return;
    if (down) return;
    if (h.send("areCamerasOff")) {
        if (beam) beam.setVisible(false);
        return;
    }
    t += dt;
    facing = centre + Math.sin(t * speed * Math.PI / 180.0) * sweep;
    if (beam) {
        beam.setVisible(true);
        beam.setPosition(self.x, self.y - 0.25, 0.0);
        beam.rotZ = -facing;
    }

    var p = scene.find("Thief");
    if (!p || !p.getVisible() || h.send("isOver")) { setCold(false); return; }
    var dx = p.x - self.x, dy = p.y - self.y;
    var d = Math.sqrt(dx * dx + dy * dy);
    if (d > sight) { setCold(false); return; }
    var ang = Math.atan2(dx, dy) * 180.0 / Math.PI;
    var delta = ((ang - facing + 540.0) % 360.0) - 180.0;
    if (Math.abs(delta) > cone * 0.5) { setCold(false); return; }
    var steps = Math.max(2, Math.round(d * 2));
    for (var i = 1; i < steps; i++) {
        var f = i / steps;
        if (cellSolid(Math.floor(self.x + dx * f + MAP_W * 0.5),
                      Math.floor(MAP_H * 0.5 - (self.y + dy * f)))) { setCold(false); return; }
    }
    // A camera is not a guard: it never chases, it reports.  The further off
    // axis the thief is, the less of the beam they actually cross.
    setCold(true);
    var gain = rate * dt * (0.35 + 0.65 * (1.0 - Math.abs(delta) / (cone * 0.5))) * (1.0 - 0.4 * d / sight);
    h.send("cameraSees", gain);
}

function setCold(isHot) {
    hot = isHot;
    if (beam) beam.setColor(isHot ? "#66FF5A48" : "#22C8E0FF");
}

function cellSolid(cx, cy) {
    if (cx < 0 || cy < 0 || cx >= MAP_W || cy >= MAP_H) return true;
    return MAP[cy].charAt(cx) !== ".";
}

function disable() { down = true; if (beam) beam.setVisible(false); }
function getFacing() { return facing; }
function isDown() { return down; }
function isHot() { return hot; }
