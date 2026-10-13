#!/usr/bin/env python3
"""Generates games/12_sunken_citadel_3d -- the second of the two 3D games.

Where game 11 is an open valley you wander, this one is a place that was built:
a flooded citadel courtyard of stone platforms, bridges and gates, lit by braziers,
where a knight fights three waves of Hollows and then the thing that keeps the
citadel.  Same kit (Kenney's Nature Kit), different art direction -- dressed
stone, torchlight, and architecture assembled out of the kit's fences, bridges,
cliffs and platforms.

Systems: a knight with a light/heavy attack combo, a dodge roll and stamina; a
wave director with spawn rings and a rest between waves; two enemy archetypes
with a telegraph -> strike -> recover state machine; a boss with an area slam;
loot pickups; a HUD; pooled combat FX.  Motion is script-owned (see Ranger.js in
game 11 for why): the offline harness does not run PhysicsWorld3D, so a game that
depends on it cannot be verified here.
"""

from __future__ import annotations

import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "biggames"))

from scene_kit import (Scene, asset, cam3d, light, manifest, mesh, particles,  # noqa: E402
                       script, sprite, text, write_game)
import kit3d  # noqa: E402
from kit3d import body_part, ground, prop  # noqa: E402

GID = "12_sunken_citadel_3d"
KIT = kit3d.KIT
PARTICLES = "kenney_particle_pack"
UI_PACK = "kenney_ui_pack"
TINY_UI = "tinyrpg_dragonregaliagui_v1_0"

# --------------------------------------------------------------------------- art
#
# A citadel at dusk: dressed grey stone, wet slate, torchlight and black water.
# Colours are this game's own -- the kit's palette is a neon demo palette and
# every model's dominant material still drives which colour it gets.
PALETTE = {
    "stone":      "#9EA0A0",   # citadel masonry
    "stone_light": "#B4B6B2",
    "stone_dark": "#6E7276",
    "slate":      "#5C6266",
    "floor":      "#7F7A6E",
    "water":      "#33505F",
    "water_deep": "#22333E",   # the mere outside the walls
    "mud":        "#4A4438",   # wet shelf under the footings
    "reed":       "#6E7A4C",
    "lily":       "#5B7A55",
    "wood":       "#7A5A3C",
    "wood_dark":  "#5A4029",
    "bronze":     "#B08B4A",
    "leaf":       "#4C6B4A",
    "leaf_dark":  "#35513A",
    "banner":     "#8E3B3B",
    "banner_dark": "#6B2C2C",
    "cloth":      "#C8BFA8",
    "bone":       "#D8D2C4",
    "ember":      "#E8913C",
    "hollow":     "#7D7F86",
    "hollow_dark": "#55575E",
    "hollow_eye": "#C9453B",
}
TINT = {
    "leafsGreen": PALETTE["leaf"], "leafsDark": PALETTE["leaf_dark"],
    "leafsFall": PALETTE["ember"], "grass": PALETTE["leaf"],
    "dirt": PALETTE["stone_dark"], "dirtDark": PALETTE["slate"],
    "stone": PALETTE["stone"], "stoneDark": PALETTE["stone_dark"],
    "wood": PALETTE["wood"], "woodBark": PALETTE["wood_dark"],
    "woodBarkDark": PALETTE["wood_dark"], "woodInner": PALETTE["cloth"],
    "woodDark": PALETTE["wood_dark"], "woodBirch": PALETTE["cloth"],
    "water": PALETTE["water"], "colorRed": PALETTE["banner"],
    "colorRedDark": PALETTE["banner_dark"], "colorYellow": PALETTE["bronze"],
    "colorPurple": PALETTE["stone_dark"], "colorTan": PALETTE["cloth"],
    "colorWhite": PALETTE["cloth"], "corn": PALETTE["bronze"],
    "_defaultMat": PALETTE["stone"],
}

STONE = PALETTE["stone"]
STONE_L = PALETTE["stone_light"]
STONE_D = PALETTE["stone_dark"]
SLATE = PALETTE["slate"]
FLOOR = PALETTE["floor"]
WATER = PALETTE["water"]
WATER_D = PALETTE["water_deep"]
MUD = PALETTE["mud"]
REED = PALETTE["reed"]
LILY = PALETTE["lily"]
WOOD = PALETTE["wood"]
WOOD_D = PALETTE["wood_dark"]
BRONZE = PALETTE["bronze"]
BANNER = PALETTE["banner"]
CLOTH = PALETTE["cloth"]
LEAF = PALETTE["leaf"]
EMBERT = PALETTE["ember"]
HOLLOW = PALETTE["hollow"]
HOLLOW_D = PALETTE["hollow_dark"]
EYE = PALETTE["hollow_eye"]

KNIGHT = "Knight"
COURT = 21.0                 # the courtyard is 42 x 42, walled
KEEP = 9.0                   # the keep floor is a 18 x 18 island in the middle


def prop_(sc, model, x, z, size, **kw):
    kw.setdefault("tag", "Prop")
    return kit3d.prop(sc, model, x, z, size, palette=PALETTE, mapping=TINT, **kw)


def ground_(sc, model, x, z, sx, sz, **kw):
    return kit3d.ground(sc, model, x, z, sx, sz, palette=PALETTE, mapping=TINT, **kw)


def box_(sc, model, x, y, z, sx, sy, sz, color=None, rot_y=0.0, tag="Prop"):
    """A prop with independent axis scale.

    The engine normalises every model by its longest side, so a kit model scaled
    by `prop_()` keeps its shape but cannot become a beam: a 10-unit gate lintel
    would also be 10 units deep.  Anything that has to be a plank, a lintel or a
    wall cap gets its three sizes set here.
    """
    if color is None:
        color = kit3d.tint_for(model, PALETTE, TINT)
    box_._n[0] += 1
    return sc.add(f"{model}_box{box_._n[0]}", x=x, y=y, z=z, rotY=rot_y, sx=sx, sy=sy,
                  sz=sz, tag=tag,
                  comps=[mesh(kind="Custom Model", model=model + ".obj", color=color,
                              specular=0.08, shininess=12.0)])


box_._n = [0]


# --------------------------------------------------------------------------- assets


def build_assets():
    a = []
    models = [
        # citadel fabric
        "ground_grass", "ground_pathOpen", "ground_riverOpen", "platform_beach",
        "platform_grass", "platform_stone",
        "cliff_stone", "cliff_large_stone", "cliff_blockHalf_stone",
        "cliff_steps_stone", "cliff_cornerInner_stone", "cliff_block_stone",
        "cliff_corner_stone", "cliff_blockSlope_stone", "cliff_cave_stone",
        "cliff_diagonal_stone", "cliff_blockSlopeWalls_stone",
        "stone_largeA", "stone_largeC", "stone_smallA", "stone_smallD",
        "stone_tallA", "stone_tallC", "stone_tallF", "rock_smallB", "rock_tallA",
        "path_stone", "path_stoneCircle", "path_stoneCorner", "path_stoneEnd",
        # structures
        "bridge_stone", "bridge_stoneNarrow", "bridge_center_stone",
        "bridge_side_stone", "fence_planks", "fence_planksDouble", "fence_gate",
        "fence_simpleLow", "statue_column", "statue_columnDamaged",
        "statue_obelisk", "statue_ring", "statue_head", "statue_block",
        "bed", "bed_floor", "sign", "pot_large", "pot_small",
        # the courtyard's drowned garden
        "plant_bush", "plant_flatTall", "grass_leafsLarge", "lily_large",
        "lily_small", "hanging_moss", "tree_pineSmallB", "stump_round",
        "log", "log_stack", "campfire_stones", "campfire_logs", "canoe",
    ]
    a += kit3d.model_assets(models)

    for tail, alias in (("PNG (Transparent)/fire_01.png", "fx_fire.png"),
                        ("PNG (Transparent)/smoke_02.png", "fx_smoke.png"),
                        ("PNG (Transparent)/spark_04.png", "fx_spark.png"),
                        ("PNG (Transparent)/magic_05.png", "fx_magic.png"),
                        ("PNG (Transparent)/circle_02.png", "fx_ring.png"),
                        ("PNG (Transparent)/star_04.png", "fx_star.png"),
                        ("PNG (Transparent)/dirt_02.png", "fx_dust.png")):
        a.append(asset(PARTICLES, tail, alias))
    a.append(asset(TINY_UI, "20240707dragon9SlicesA.png", "hud_panel.png"))
    a.append(asset(TINY_UI, "20240707dragon9SlicesB.png", "hud_gem.png"))
    a.append(asset(UI_PACK, "PNG/Grey/Default/button_square_border.png", "ui_slot.png"))
    a.append(asset(UI_PACK, "PNG/Grey/Default/arrow_basic_e.png", "ui_arrow.png"))
    for tail, alias in (("Sounds/click-a.ogg", "sfx_ui.ogg"),
                        ("Sounds/switch-a.ogg", "sfx_hit.ogg"),
                        ("Sounds/tap-a.ogg", "sfx_step.ogg"),
                        ("Sounds/tap-b.ogg", "sfx_heavy.ogg"),
                        ("Sounds/switch-b.ogg", "sfx_slam.ogg"),
                        ("Sounds/click-b.ogg", "sfx_hurt.ogg")):
        a.append(asset(UI_PACK, tail, alias))
    for tail, alias in (("Sounds/tap-a.ogg", "sfx_dodge.ogg"),
                        ("Sounds/switch-a.ogg", "sfx_wave.ogg"),
                        ("Sounds/click-a.ogg", "sfx_loot.ogg"),
                        ("Sounds/switch-b.ogg", "sfx_win.ogg"),
                        ("Sounds/click-b.ogg", "sfx_lose.ogg")):
        a.append(asset(UI_PACK, tail, alias))
    return a


# --------------------------------------------------------------------------- scene


def build_walls(sc):
    """The curtain wall: a 42 x 42 ring of stone with four gates.

    Each wall segment is a cliff cell sized to 1.6 units and stacked two high,
    with a walkway of half-blocks on top -- at that scale the kit's stepped
    silhouettes read as masonry rather than as one giant facet.
    """
    step = 1.6
    n = int(COURT * 2 / step) + 1
    for i in range(n):
        t = -COURT + i * step
        for axis, side in (("x", -1), ("x", 1), ("z", -1), ("z", 1)):
            # gate openings at the middle of each wall: 3 units either side
            if abs(t) < 4.5:
                continue
            x = t if axis == "x" else side * COURT
            z = side * COURT if axis == "x" else t
            idx = f"{axis}{side}{i}"
            for level in range(2):
                prop_(sc, "cliff_stone", x, z, 1.65, y=0.8 + level * 1.55,
                      rot_y=90.0 * level, tag="Wall")
            prop_(sc, "cliff_blockHalf_stone", x, z, 1.7, y=2.85, tag="Wall")
    # Gate columns and lintels, so the openings look built rather than missing.
    for gx, gz, horiz in ((0.0, -COURT, True), (0.0, COURT, True),
                          (-COURT, 0.0, False), (COURT, 0.0, False)):
        for s in (-1, 1):
            px = gx + (s * 5.2 if horiz else 0.0)
            pz = gz + (0.0 if horiz else s * 5.2)
            prop_(sc, "stone_tallA", px, pz, 2.6, y=3.2, tag="Wall")
            prop_(sc, "statue_column", px, pz, 5.4, y=2.7, tag="Wall")
        # The lintel: a beam laid across the opening, not a slab -- see box_().
        box_(sc, "bridge_center_stone", gx, 5.1, gz,
             10.6 if horiz else 2.4, 0.6, 2.4 if horiz else 10.6,
             color=STONE_D, tag="Wall")
    # Plinths along the wall walk, dripping moss: the place was a garden once.
    for i, (x, z) in enumerate(kit3d.ring(16, 0.0, 0.0, COURT - 2.0, 22.5)):
        prop_(sc, "stone_smallD", x, z, 1.1, tag="Wall")
        prop_(sc, "hanging_moss", x, z, 1.5, y=2.6, color=LEAF, tag="Wall")


def build_keep(sc):
    """The keep: a raised stone island ringed by a moat, reached by two bridges."""
    # Moat: a ring of dark water, two units wide, cut visually into the floor.
    for i, (x, z) in enumerate(kit3d.ring(36, 0.0, 0.0, KEEP + 1.6, 5.0)):
        ground_(sc, "ground_riverOpen", x, z, 9.0, 9.0, y=-0.22, tag="Moat")
    ground_(sc, "platform_stone", 0.0, 0.0, 19.0, 19.0, y=0.35, tag="Keep")
    ground_(sc, "path_stoneCircle", 0.0, 0.0, 12.0, 12.0, y=0.42, tag="Keep")
    # Low parapet around the keep, with two gaps for the bridges.
    for i, (x, z) in enumerate(kit3d.ring(24, 0.0, 0.0, 9.2, 7.5)):
        if abs(x) < 3.2 or abs(z) < 3.2:
            continue
        prop_(sc, "cliff_blockHalf_stone", x, z, 1.4, y=0.35 + 0.35, tag="Keep")
    # Two bridges: west (the way in) and east (the way to the boss).
    for s, rot in ((-1, 90.0), (1, 90.0)):
        prop_(sc, "bridge_stone", s * 11.6, 0.0, 7.4, y=0.34, rot_y=rot, tag="Keep")
        prop_(sc, "path_stone", s * 15.4, 0.0, 3.0, y=0.06, rot_y=rot, tag="Keep")
    # The plinth at the centre: the thing the citadel was built around.
    prop_(sc, "statue_block", 0.0, 0.0, 4.2, y=0.55, tag="Keep", color=STONE_D)
    prop_(sc, "statue_obelisk", 0.0, 0.0, 6.4, y=0.9, tag="Keep", color=STONE_L)
    for i, (x, z) in enumerate(kit3d.ring(6, 0.0, 0.0, 5.4, 30.0)):
        prop_(sc, "statue_column", x, z, 5.0, y=0.5, tag="Keep", color=STONE)
        prop_(sc, "statue_head", x, z, 1.9, y=5.4, rot_y=90.0 * i, tag="Keep",
              color=STONE_D)
    # Damaged columns and a fallen head: the citadel is sinking, not pristine.
    prop_(sc, "statue_columnDamaged", 6.4, -5.2, 4.2, rot_y=24.0, tag="Keep")
    prop_(sc, "statue_head", -6.0, 6.2, 2.2, y=0.5, rot_y=-35.0, tag="Keep")
    prop_(sc, "statue_ring", 0.0, 0.0, 12.0, y=0.5, tag="Keep", color=STONE_D)


def build_courtyard(sc):
    """Braziers, banners, benches, a drowned garden and a supply pile."""
    # Braziers: the courtyard's light sources.  Each is a stone ring, a log stack
    # and a real point light plus fire and smoke -- the engine keeps the four
    # nearest point lights, so the ones you stand by are the ones that light you.
    for i, (x, z) in enumerate(((0.0, -COURT + 5.5), (0.0, COURT - 5.5),
                                (-COURT + 5.5, 0.0), (COURT - 5.5, 0.0),
                                (-11.0, -11.0), (11.0, 11.0))):
        prop_(sc, "campfire_stones", x, z, 2.4, y=0.05, tag="Brazier")
        prop_(sc, "campfire_logs", x, z, 2.0, y=0.12, tag="Brazier")
        sc.add(f"BrazierFire_{i}", x=x, y=0.5, z=z, tag="Fire",
               comps=[light(kind="Point", color="#FFFFA24A", intensity=2.4, rng=22.0),
                      particles(color="#FFFFC067", end="#20FF5A12", size=0.38, end_size=0.06,
                                rate=22.0, lifetime=1.1, speed=2.0, direction=90, spread=24,
                                gravity=1.5, max_particles=54, additive=True,
                                texture="fx_fire.png", emitting=True),
                      particles(color="#3CFFFFFF", end="#00FFFFFF", size=0.5, end_size=1.4,
                                rate=5.0, lifetime=2.6, speed=0.8, direction=90, spread=16,
                                gravity=0.3, max_particles=20, additive=True,
                                texture="fx_smoke.png", emitting=True)])
    # Banners along the north wall: fences used as hanging cloth, tinted red.
    for i in range(7):
        x = -13.5 + i * 4.5
        prop_(sc, "fence_planks", x, -COURT + 1.2, 3.4, y=3.1, color=BANNER, tag="Banner")
    for i in range(5):
        z = -9.0 + i * 4.5
        prop_(sc, "fence_planks", COURT - 1.2, z, 3.4, y=3.1, rot_y=90.0, color=BANNER,
              tag="Banner")
    # The drowned garden in the south-west quarter.
    for i, (x, z) in enumerate(kit3d.ring(9, -12.0, 12.0, 6.0, 12.0)):
        ground_(sc, "ground_riverOpen", x, z, 7.0, 7.0, y=-0.18, tag="Pool")
        prop_(sc, "lily_large" if i % 2 else "lily_small", x, z, 1.2, y=-0.16,
              color=LEAF, tag="Pool")
    prop_(sc, "plant_bushLarge", -12.0, 12.0, 1.6, color=LEAF, tag="Pool")
    for i in range(7):
        ang = i * 51.0
        px = -12.0 + math.sin(math.radians(ang)) * 8.5
        pz = 12.0 + math.cos(math.radians(ang)) * 8.5
        prop_(sc, "plant_flatTall" if i % 2 else "grass_leafsLarge", px, pz, 1.3,
              rot_y=ang, color=LEAF, tag="Pool")
    prop_(sc, "tree_pineSmallB", -17.0, 16.0, 4.6, color=PALETTE["leaf_dark"], tag="Pool")
    prop_(sc, "canoe", -8.0, 15.0, 5.0, y=-0.14, rot_y=20.0, color=WOOD, tag="Pool")
    prop_(sc, "stump_round", -15.0, 7.0, 1.4, tag="Pool")
    # Supply pile and benches, so the courtyard is lived in.
    prop_(sc, "pot_large", COURT - 4.5, -COURT + 3.6, 1.8, color=CLOTH, tag="Supply")
    prop_(sc, "pot_small", COURT - 5.8, -COURT + 4.8, 1.2, color=CLOTH, tag="Supply")
    prop_(sc, "log_stack", COURT - 3.2, -COURT + 6.2, 1.9, color=WOOD_D, tag="Supply")
    prop_(sc, "sign", -COURT + 4.0, -5.0, 3.0, rot_y=95.0, color=WOOD, tag="Supply")
    for i, (x, z, r) in enumerate(((-6.5, -6.5, 20.0), (6.5, 6.5, -20.0),
                                   (-6.5, 6.5, 250.0), (6.5, -6.5, 160.0))):
        prop_(sc, "log", x, z, 2.4, y=0.5, rot_y=r, color=WOOD_D, tag="Bench")
    # Stepping stones across the courtyard: the paving of the old processional.
    for i in range(-4, 5):
        prop_(sc, "path_stone", 0.0, i * 4.2, 3.0, y=0.02, color=STONE_D, tag="Paving")
        if abs(i) <= 2:
            prop_(sc, "path_stone", i * 4.2, 0.0, 3.0, y=0.02, color=STONE_D, tag="Paving")
    # Broken columns inside the ring: somewhere to break line of sight and to
    # read the arena's size against, plus the moss the mere has grown over it.
    for i, (x, z) in enumerate(kit3d.ring(6, 0.0, 0.0, 15.6, 22.0)):
        prop_(sc, "statue_columnDamaged", x, z, 4.4, rot_y=i * 37.0,
              color=STONE if i % 2 else STONE_D, tag="Ruin")
    for i, (x, z) in enumerate(kit3d.ring(16, 0.0, 0.0, 17.8, 9.0)):
        prop_(sc, "grass_leafsLarge", x, z, 0.9 + (i % 3) * 0.18, rot_y=i * 47.0,
              color=LEAF if i % 2 else PALETTE["leaf_dark"], tag="Moss")
    for i, (x, z) in enumerate(kit3d.ring(12, 0.0, 0.0, 12.4, 33.0)):
        prop_(sc, "stump_round", x, z, 0.6, color=STONE_D, tag="Moss")


def build_scene():
    sc = Scene(name="Main", gravity=(0.0, 0.0, 0.0), gravity3d=-9.81,
               ambient="#FF5C6272", fog=True, fog_color="#FF8C97A0",
               fog_start=58.0, fog_end=210.0)

    # The citadel is lit for dusk: a low, warm sun and a lot of brazier light.
    sc.add("Sun", rotX=-24.0, rotY=205.0, tag="Sky",
           comps=[light(kind="Directional", color="#FFFFC98E", intensity=0.82, rng=400.0)])
    # The camera is an arena camera: a fixed trailing offset owned by Camera3D's
    # follow, high enough to read the courtyard and short enough that it can never
    # end up inside the south wall (the knight's own limit below keeps him in).
    sc.add("Main Camera", y=9.0, z=10.0, tag="Camera",
           comps=[cam3d(follow=KNIGHT, off=(0.0, 5.8, 6.4), fov=64.0, near=0.15,
                        far=340.0, smooth=8.0, sky_top="#FF2C3A4A",
                        sky_horizon="#FFC99A6A", post="Vignette", fx=0.5)])

    # The mere: the citadel did not fall, it sank.  Everything outside the walls
    # is black water, so the courtyard reads as an island and the fog line eats
    # the far shore instead of showing an empty green plane.
    ground_(sc, "ground_riverOpen", 0.0, 0.0, 340.0, 340.0, color=WATER_D,
            y=-0.45, tag="Water")
    # Wet shelf under the wall footings: without it the walls rise out of nothing.
    ground_(sc, "platform_grass", 0.0, 0.0, COURT * 2.0 + 16.0, COURT * 2.0 + 16.0,
            color=MUD, y=-0.16, tag="Ground")
    ground_(sc, "platform_stone", 0.0, 0.0, COURT * 2.0, COURT * 2.0, y=0.0,
            tag="Ground")
    # Causeway south, out to the fog: the way the knight came in.
    ground_(sc, "platform_grass", 0.0, 0.0, 8.4, 120.0, color=MUD, y=-0.1,
            tag="Ground")
    ground_(sc, "ground_pathOpen", 0.0, 0.0, 5.0, 120.0, y=0.02, tag="Ground")

    build_walls(sc)
    build_keep(sc)
    build_courtyard(sc)

    # Drowned ruins in the mere: towers standing in water up to their knees, so
    # the eye gets depth cues past the walls instead of a flat plane.
    for i, (x, z) in enumerate(kit3d.ring(14, 0.0, 0.0, COURT + 20.0, 12.0)):
        sunk = 1.15 if i % 2 else 0.8
        prop_(sc, "cliff_large_stone", x, z, 4.2 + (i % 3), y=sunk, rot_y=i * 26.0,
              tag="Ruin", color=STONE_D if i % 2 else STONE)
        prop_(sc, "cliff_blockHalf_stone", x, z, 3.4, y=0.4 - 0.25, rot_y=i * 47.0,
              tag="Ruin", color=STONE)
    for i, (x, z) in enumerate(kit3d.ring(9, 0.0, 0.0, COURT + 7.5, 20.0)):
        prop_(sc, "cliff_stone", x, z, 2.2 + (i % 2), y=0.9, rot_y=i * 53.0,
              tag="Ruin", color=MUD)
    # Reeds and lilies on the water line, close enough to read as a shore.
    for i, (x, z) in enumerate(kit3d.ring(22, 0.0, 0.0, COURT + 5.0, 11.0)):
        if abs(x) < 4.0 and z > 0.0:
            continue                       # keep the causeway clear
        prop_(sc, "plant_flatTall", x, z, 1.5 + (i % 3) * 0.35, y=-0.3,
              rot_y=i * 31.0, tag="Shore", color=REED)
    for i, (x, z) in enumerate(kit3d.ring(10, 0.0, 0.0, COURT + 3.4, 40.0)):
        prop_(sc, "lily_small", x, z, 1.5, y=-0.42, tag="Shore", color=LILY)
    return sc


# --------------------------------------------------------------------------- scripts


KNIGHT_JS = r"""
// Sunken Citadel -- Knight.js
//
// The player: a knight with a three-step light combo, a heavy swing, a dodge
// roll with invulnerability frames, and stamina that gates all three.  Attacks
// hit through Combat.js, which owns the hit resolution so enemy scripts and the
// boss all share one damage path.
//
// Params: speed=7.6, turn=220, hp=120, lightDamage=14, heavyDamage=32,
//         lightReach=2.6, heavyReach=3.1, heavyArc=0.5, lightArc=0.75,
//         staminaMax=100, dodgeCost=22, lightCost=9, heavyCost=26,
//         staminaRegen=22, dodgeSpeed=15.5, dodgeTime=0.34, iframes=0.42

var yaw = 0.0;
var hp = 120;
var stamina = 100.0;
var swing = 0.0;             // time left in the current swing
var combo = 0;               // 0..2 within the light chain
var comboTimer = 0.0;
var dodge = 0.0;             // time left rolling
var iframe = 0.0;
var hurtFlash = 0.0;
var dead = false;
var heavy = false;
var hits = 0;
var dodgeDirX = 0.0, dodgeDirZ = 0.0;

function start() {
    yaw = self.rotY;
}

function update(dt) {
    var d = scene.find("Citadel");
    var frozen = d ? d.send("isFrozen") : false;

    if (comboTimer > 0.0) { comboTimer -= dt; if (comboTimer <= 0.0) combo = 0; }
    if (swing > 0.0) swing -= dt;
    if (dodge > 0.0) dodge -= dt;
    if (iframe > 0.0) iframe -= dt;
    if (hurtFlash > 0.0) {
        hurtFlash -= dt;
        self.color = "#FFFFFFFF";
    }
    if (stamina < staminaMax && dodge <= 0.0) {
        stamina = Math.min(staminaMax, stamina + staminaRegen * dt);
    }

    var ix = frozen ? 0.0 : input.axisX;
    var iz = frozen ? 0.0 : input.axisY;
    var mag = Math.sqrt(ix * ix + iz * iz);
    if (mag > 1.0) { ix /= mag; iz /= mag; mag = 1.0; }

    if (dodge > 0.0) {
        // The roll keeps its committed direction: steering mid-roll would make
        // the i-frames cover any direction the player likes.
        self.setPosition(self.x + dodgeDirX * dodgeSpeed * dt, self.y,
                         self.z + dodgeDirZ * dodgeSpeed * dt);
    } else if (mag > 0.05) {
        var wantYaw = Math.atan2(ix, -iz) * 180.0 / Math.PI;
        yaw = turnToward(yaw, wantYaw, turn * dt);
        var speedScale = swing > 0.0 ? 0.28 : 1.0;
        var dx = Math.sin(yaw * Math.PI / 180.0);
        var dz = -Math.cos(yaw * Math.PI / 180.0);
        self.setPosition(self.x + dx * speed * mag * speedScale * dt, self.y,
                         self.z + dz * speed * mag * speedScale * dt);
    }

    if (!frozen && dodge <= 0.0) {
        if (actionPressed("Dodge") && stamina >= dodgeCost) {
            beginDodge(ix, iz);
        } else if (actionPressed("Attack")) {
            beginSwing(false);
        } else if (actionPressed("Heavy")) {
            beginSwing(true);
        }
    }

    // The keep's parapet is decoration; this is what actually keeps the knight
    // inside the courtyard.  The limit is set by the camera, which trails 5.8
    // units south at a fixed height with no collision of its own: 13.0 + 6.4 is
    // still well inside the wall's inner face at 20, and clear of the gate arch
    // the wall carries over the south opening.
    var lim = 13.0;
    if (self.x > lim) self.setPosition(lim, self.y, self.z);
    if (self.x < -lim) self.setPosition(-lim, self.y, self.z);
    if (self.z > lim) self.setPosition(self.x, self.y, lim);
    if (self.z < -lim) self.setPosition(self.x, self.y, -lim);
    self.rotY = yaw;

    animate();
}

function beginDodge(ix, iz) {
    dodge = dodgeTime;
    iframe = iframes;
    stamina -= dodgeCost;
    var len = Math.sqrt(ix * ix + iz * iz);
    if (len < 0.1) {
        dodgeDirX = Math.sin(yaw * Math.PI / 180.0);
        dodgeDirZ = -Math.cos(yaw * Math.PI / 180.0);
    } else {
        dodgeDirX = ix / len;
        dodgeDirZ = iz / len;
    }
    audio.play("sfx_dodge.ogg", 0.6);
    var fx = scene.find("Fx");
    if (fx) fx.send("roll", self.x, self.z);
}

function beginSwing(isHeavy) {
    // Swing assist: a swing that whiffs only because the player is half a degree
    // off the enemy reads as broken on a touch screen.  Snap to the nearest enemy
    // inside the reach before the arc is resolved -- the same soft lock-on every
    // third-person action game ships with.
    aimAssist(isHeavy ? heavyReach : lightReach);
    var cost = isHeavy ? heavyCost : lightCost;
    if (stamina < cost) {
        audio.play("sfx_hurt.ogg", 0.25);
        return;
    }
    stamina -= cost;
    heavy = isHeavy;
    if (isHeavy) {
        combo = 0;
        swing = heavyArc;
        audio.play("sfx_heavy.ogg", 0.7);
    } else {
        combo = (combo + 1) % 3;
        comboTimer = 0.9;
        swing = lightArc;
        audio.play("sfx_hit.ogg", 0.55 + combo * 0.1);
    }
    // Damage goes through Combat: one place decides what a hit is, so the boss,
    // the Hollows and the knight's own swings cannot disagree about reach.
    var c = scene.find("Combat");
    if (c) {
        var n = c.send("strike", self, isHeavy ? heavyDamage : lightDamage,
                       isHeavy ? heavyReach : lightReach, self.rotY);
        hits += n;
        if (n > 0) scene.shake(isHeavy ? 0.3 : 0.14);
        else scene.shake(0.04);
    }
    var fx = scene.find("Fx");
    if (fx) fx.send("swing", self.x, self.y + 1.1, self.z, self.rotY, isHeavy);
}

function aimAssist(reach) {
    var list = scene.findAll("Enemy");
    var best = 999.0, bestAng = yaw;
    for (var i = 0; i < list.length; i++) {
        var ex = list[i].x - self.x, ez = list[i].z - self.z;
        var d = Math.sqrt(ex * ex + ez * ez);
        if (d > reach + 2.2 || d >= best) continue;
        best = d;
        bestAng = Math.atan2(ex, -ez) * 180.0 / Math.PI;
    }
    if (best < 998.0) {
        yaw = bestAng;
        self.rotY = bestAng;
    }
}

function animate() {
    var blade = scene.find("Sword");
    if (!blade) return;
    if (dodge > 0.0) {
        blade.rotX = -70.0;
        self.color = "#C8FFFFFF";
    } else if (swing > 0.0) {
        blade.rotX = heavy ? 40.0 - (swing / heavyArc) * 130.0 : 20.0 - (swing / lightArc) * 110.0;
        self.color = "#FFFFFFFF";
    } else {
        blade.rotX = -12.0;
    }
}

function hurt(amount, fromX, fromZ) {
    if (dead) return;
    if (iframe > 0.0) return;
    hp -= amount;
    iframe = 0.55;
    hurtFlash = 0.28;
    self.color = "#FFFF9A9A";
    audio.play("sfx_hurt.ogg");
    scene.shake(0.4);
    var dx = self.x - fromX, dz = self.z - fromZ;
    var d = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
    self.setPosition(self.x + (dx / d) * 1.5, self.y, self.z + (dz / d) * 1.5);
    var c = scene.find("Citadel");
    if (c) c.send("knightHurt", amount);
    if (hp <= 0.0) {
        dead = true;
        var c2 = scene.find("Citadel");
        if (c2) c2.send("gameOver");
    }
}

function heal(amount) {
    hp = Math.min(120.0, hp + amount);
}

function actionPressed(name) { return input.actionPressed(name); }
function turnToward(from, to, maxStep) {
    var d = ((to - from + 540.0) % 360.0) - 180.0;
    if (Math.abs(d) <= maxStep) return to;
    return from + (d > 0 ? maxStep : -maxStep);
}

function getFacing() { return yaw; }
function getVx() { return 0.0; }
function getHp() { return hp; }
function getStamina() { return stamina; }
function getCombo() { return combo; }
function isDodging() { return dodge > 0.0; }
function isInvulnerable() { return iframe > 0.0; }
function getHits() { return hits; }
"""

CITADEL_JS = r"""
// Sunken Citadel -- Citadel.js
//
// The director: three waves of Hollows, a rest between them, the boss that wakes
// when the third wave falls, and the bookkeeping the HUD reads.  It also owns
// the keep's lamps, so the courtyard gets darker as the fight goes on and the
// boss arrives lit by its own brazier.
//
// Params: waveGap=6.0, restGap=9.0, bossHp=420, scorePerKill=25, waveBonus=250

var wave = 0;
var waveTimer = 0.0;
var spawnTimer = 0.0;
var inWave = false;
var resting = false;
var bossSpawned = false;
var bossAlive = false;
var kills = 0;
var score = 0;
var firstBlood = 0.0;
var bestCombo = 0;
var toast = "";
var toastT = 0.0;
var frozen = false;
var won = false;
var lost = false;

var WAVES = [
    { count: 5, kind: "hollow", hp: 46, speed: 3.1, gap: 1.5 },
    { count: 7, kind: "hollow", hp: 58, speed: 3.5, gap: 1.2 },
    { count: 9, kind: "revenant", hp: 74, speed: 3.9, gap: 1.0 }
];
var spawnedThisWave = 0;
var liveTimer = 0.0;

function start() {
    wave = 0;
    say("The citadel is awake. Stand, knight.");
    after(2.5, function () { beginWave(1); });
}

function update(dt) {
    if (toastT > 0.0) {
        toastT -= dt;
        if (toastT <= 0.0) toast = "";
    }
    if ((lost || won) && input.actionPressed("Jump")) {
        // Rise again / walk it back: one button, one fresh run.
        retry();
        return;
    }
    var k = scene.find("Knight");
    if (k) {
        var c = k.send("getCombo");
        if (c + 1 > bestCombo) bestCombo = c + 1;
    }
    if (resting) {
        waveTimer -= dt;
        if (waveTimer <= 0.0) beginWave(wave + 1);
    } else if (inWave) {
        liveTimer += dt;
        if (spawnedThisWave < WAVES[wave - 1].count) {
            spawnTimer -= dt;
            if (spawnTimer <= 0.0) {
                spawnTimer = WAVES[wave - 1].gap;
                spawnHollow();
            }
        }
    }
    // Waves do not end on a timer -- they end when the courtyard is clear.  The
    // timer above only spaces out the arrivals.
}

function beginWave(n) {
    wave = n;
    inWave = true;
    resting = false;
    spawnedThisWave = 0;
    spawnTimer = waveGap * 0.1;
    liveTimer = 0.0;
    var w = WAVES[n - 1];
    audio.play("sfx_wave.ogg");
    say("Wave " + n + " of " + WAVES.length + " -- " + w.count + " " + w.kind + "s");
    var fx = scene.find("Fx");
    if (fx) fx.send("wave", 0.0, 0.0);
}

function spawnHollow() {
    var w = WAVES[wave - 1];
    // Spawn ring: outside the keep, inside the walls, never on top of the knight.
    var ang = random(0.0, 360.0);
    var r = random(12.0, 17.5);
    var x = Math.sin(ang * Math.PI / 180.0) * r;
    var z = Math.cos(ang * Math.PI / 180.0) * r;
    var proto = w.kind === "revenant" ? "Revenant" : "Hollow";
    var go = scene.spawn(proto, x, 0.0, z);
    if (!go) return;
    spawnedThisWave++;
    go.send("configure", w.hp, w.speed);
    var fx = scene.find("Fx");
    if (fx) fx.send("summon", x, z);
}

function onHollowKilled(name, x, z) {
    kills++;
    score += scorePerKill;
    var fx = scene.find("Fx");
    if (fx) fx.send("crumble", x, z);
    if (inWave && !resting && aliveHollows() === 0 && spawnedThisWave >= WAVES[wave - 1].count) {
        inWave = false;
        if (wave >= WAVES.length) {
            if (!bossSpawned) summonBoss();
        } else {
            resting = true;
            waveTimer = restGap;
            score += waveBonus;
            say("Wave " + wave + " cleared.  +" + waveBonus + "  --  breathe.");
        }
    }
}

function aliveHollows() {
    // The boss is on the same side, so it is excluded here: waves end when the
    // courtyard's Hollows are down, not when the Warden is.
    return scene.findAll("Enemy").length - (bossAlive ? 1 : 0);
}

function summonBoss() {
    bossSpawned = true;
    say("Something in the keep stands up.");
    audio.play("sfx_slam.ogg");
    scene.shake(0.8);
    var go = scene.spawn("Warden", 0.0, 0.0, -12.5);
    if (!go) return;
    bossAlive = true;
    go.send("configure", bossHp, 3.2);
    var fx = scene.find("Fx");
    if (fx) fx.send("summon", 0.0, -12.5);
}

function onBossDown() {
    bossAlive = false;
    score += 1000;
    won = true;
    frozen = true;
    audio.play("sfx_win.ogg");
    say("The Warden falls. The citadel is quiet.  Score " + Math.floor(score));
}

function knightHurt() {
    if (firstBlood <= 0.0) firstBlood = 1.0;
}

function gameOver() {
    if (won) return;
    lost = true;
    frozen = true;
    audio.play("sfx_lose.ogg");
    say("The knight falls.  Press A to rise again.");
}

/** The death screen's one button: the whole scene is rebuilt from scratch. */
function retry() { scene.reload(); }

function say(msg) { toast = msg; toastT = 4.5; }

function isFrozen() { return frozen; }
function hasWon() { return won; }
function hasLost() { return lost; }
function getWave() { return wave; }
function getWaveCount() { return WAVES.length; }
function getKills() { return kills; }
function getScore() { return score; }
function getToast() { return toast; }
function getAlive() { return aliveHollows(); }
function isBossAlive() { return bossAlive; }

"""


HOLLOW_JS = r"""
// Sunken Citadel -- Hollow.js
//
// One of the citadel's Hollows: a stone construct that walks at the knight,
// telegraphs its swing (a wind-up the player can read and dodge), strikes, then
// recovers.  The wind-up is the whole fight: an enemy that damaged you on contact
// would make the dodge pointless.
//
// Params: hp=46, speed=3.1, damage=13, reach=2.3, windup=0.55, recover=0.7,
//         sight=26.0, kind=hollow

var hp = 46;
var maxHp = 46;
var speed = 3.1;
var state = "hunt";          // hunt -> windup -> strike -> recover
var timer = 0.0;
var dead = false;
var hurtFlash = 0.0;
var birth = 0.0;
var eye = null;

function configure(h, s) {
    hp = h;
    maxHp = h;
    speed = s;
}

function start() {
    birth = time.time;
    self.tag = "Enemy";
    // Eye glow: the only bright thing on a Hollow, and it brightens on wind-up.
    eye = scene.spawn("HollowEye", self.x, 1.9, self.z);
    if (eye) eye.setParentTo(self);
    self.color = "#FFFFFFFF";
}

function update(dt) {
    if (dead) return;
    if (hurtFlash > 0.0) {
        hurtFlash -= dt;
        self.color = "#FFFFFFFF";
    }
    timer -= dt;
    var k = scene.find("Knight");
    if (!k) return;
    var d = groundDist(k);
    var c = scene.find("Citadel");
    var frozen = c ? c.send("isFrozen") : false;
    if (frozen) return;

    if (state === "hunt") {
        if (d > sight) {
            // A Hollow never gives up -- but past its sight it only lumbers, so
            // a knight who runs the length of the courtyard buys himself time.
            stepToward(k.x, k.z, speed * 0.5, dt);
            bob();
        } else if (d > reach) {
            stepToward(k.x, k.z, speed, dt);
            bob();
        } else {
            state = "windup";
            timer = windup;
            if (eye) eye.color = "#FFFF4A3A";
        }
    } else if (state === "windup") {
        self.y = 0.0 + Math.sin((windup - timer) * 18.0) * 0.05;
        if (timer <= 0.0) {
            state = "strike";
            timer = 0.16;
            strike(k, d);
        }
    } else if (state === "strike") {
        if (timer <= 0.0) {
            state = "recover";
            timer = recover;
            if (eye) eye.color = "#FFC9453B";
        }
    } else if (state === "recover") {
        if (timer <= 0.0) state = "hunt";
    }
}

function strike(k, d) {
    if (d <= reach + 0.7) {
        k.send("hurt", damage, self.x, self.z);
        audio.play("sfx_slam.ogg", 0.35);
    }
    var fx = scene.find("Fx");
    if (fx) fx.send("impact", self.x, 1.2, self.z);
}

function stepToward(x, z, sp, dt) {
    var dx = x - self.x, dz = z - self.z;
    var dd = Math.sqrt(dx * dx + dz * dz);
    if (dd < 0.05) return;
    var step = Math.min(sp * dt, dd);
    self.setPosition(self.x + (dx / dd) * step, self.y, self.z + (dz / dd) * step);
    self.rotY = Math.atan2(dx, dz) * 180.0 / Math.PI;
}

/** Ground distance: the courtyard is flat, so height must not shrink a gap. */
function groundDist(o) {
    var dx = o.x - self.x, dz = o.z - self.z;
    return Math.sqrt(dx * dx + dz * dz);
}

function bob() {
    self.y = Math.abs(Math.sin(time.time * 6.0 + birth)) * 0.12;
}

function hurt(amount, fromX, fromZ) {
    if (dead) return;
    hp -= amount;
    hurtFlash = 0.22;
    self.color = "#FFFFC0C0";
    var fx = scene.find("Fx");
    if (fx) fx.send("hit", self.x, self.y + 1.4, self.z);
    // A hit interrupts the wind-up: trading blows should favour the attacker.
    if (state === "windup") { state = "recover"; timer = 0.35; }
    if (hp <= 0.0) die(fromX, fromZ);
}

function die(fromX, fromZ) {
    dead = true;
    if (eye) eye.destroy();
    // Removed first, reported second: the director counts the Hollows still
    // standing, and a corpse that has not left the scene yet would keep the
    // courtyard "occupied" and stall the wave forever.
    self.destroy();
    var c = scene.find("Citadel");
    if (c) c.send("onHollowKilled", self.name, self.x, self.z);
    var fx = scene.find("Fx");
    if (fx) fx.send("crumble", self.x, self.z);
}

function getHp() { return hp; }
function getState() { return state; }
function getKind() { return kind; }
"""

REVENANT_JS = r"""
// Sunken Citadel -- Revenant.js
//
// The third wave's enemy: faster than a Hollow, longer reach, and it circles
// before it commits, so a player who only backs away gets punished.  Same three
// states, different numbers and a strafe.
//
// Params: hp=74, speed=3.9, damage=17, reach=2.7, windup=0.42, recover=0.55,
//         sight=30.0, strafeTime=1.4

var hp = 74;
var speed = 3.9;
var state = "hunt";
var timer = 0.0;
var strafe = 1.0;
var strafeTimer = 0.0;
var dead = false;
var hurtFlash = 0.0;
var birth = 0.0;
var eye = null;

function configure(h, s) {
    hp = h;
    speed = s;
}

function start() {
    birth = time.time;
    self.tag = "Enemy";
    strafeTimer = strafeTime;
    eye = scene.spawn("HollowEye", self.x, 2.1, self.z);
    if (eye) eye.setParentTo(self);
}

function update(dt) {
    if (dead) return;
    if (hurtFlash > 0.0) { hurtFlash -= dt; self.color = "#FFFFFFFF"; }
    timer -= dt;
    strafeTimer -= dt;
    var k = scene.find("Knight");
    if (!k) return;
    var d = groundDist(k);
    var c = scene.find("Citadel");
    if (c && c.send("isFrozen")) return;

    if (strafeTimer <= 0.0) { strafe = -strafe; strafeTimer = strafeTime; }

    if (state === "hunt") {
        if (d > sight) {
            stepToward(k.x, k.z, speed * 0.6, dt);
            bob();
            return;
        }
        if (d > reach) {
            if (d < reach + 3.2) {
                // Circle: close faster than a Hollow and refuse to stand still.
                var ax = k.x - self.x, az = k.z - self.z;
                var ad = Math.max(0.001, Math.sqrt(ax * ax + az * az));
                var px = -az / ad * strafe, pz = ax / ad * strafe;
                stepToward(k.x + px * 2.6, k.z + pz * 2.6, speed, dt);
            } else {
                stepToward(k.x, k.z, speed, dt);
            }
            bob();
        } else {
            state = "windup";
            timer = windup;
            if (eye) eye.color = "#FFFF5A48";
        }
    } else if (state === "windup") {
        self.y = Math.sin((windup - timer) * 22.0) * 0.06;
        if (timer <= 0.0) { state = "strike"; timer = 0.14; strike(k, d); }
    } else if (state === "strike") {
        if (timer <= 0.0) { state = "recover"; timer = recover; if (eye) eye.color = "#FFC9453B"; }
    } else if (state === "recover") {
        if (timer <= 0.0) state = "hunt";
    }
}

function bob() {
    self.y = Math.abs(Math.sin(time.time * 7.0 + birth)) * 0.16;
}

function strike(k, d) {
    if (d <= reach + 0.8) k.send("hurt", damage, self.x, self.z);
    var fx = scene.find("Fx");
    if (fx) fx.send("impact", self.x, 1.4, self.z);
}

function stepToward(x, z, sp, dt) {
    var dx = x - self.x, dz = z - self.z;
    var dd = Math.sqrt(dx * dx + dz * dz);
    if (dd < 0.05) return;
    var step = Math.min(sp * dt, dd);
    self.setPosition(self.x + (dx / dd) * step, self.y, self.z + (dz / dd) * step);
    self.rotY = Math.atan2(dx, dz) * 180.0 / Math.PI;
}

/** Ground distance: the courtyard is flat, so height must not shrink a gap. */
function groundDist(o) {
    var dx = o.x - self.x, dz = o.z - self.z;
    return Math.sqrt(dx * dx + dz * dz);
}

function hurt(amount, fromX, fromZ) {
    if (dead) return;
    hp -= amount;
    hurtFlash = 0.22;
    self.color = "#FFFFC0C0";
    var fx = scene.find("Fx");
    if (fx) fx.send("hit", self.x, self.y + 1.6, self.z);
    if (state === "windup") { state = "recover"; timer = 0.3; }
    if (hp <= 0.0) {
        dead = true;
        if (eye) eye.destroy();
        // Same order as the Hollow: gone from the scene, then reported.
        self.destroy();
        var c = scene.find("Citadel");
        if (c) c.send("onHollowKilled", self.name, self.x, self.z);
        var fx2 = scene.find("Fx");
        if (fx2) fx2.send("crumble", self.x, self.z);
    }
}

function getHp() { return hp; }
function getState() { return state; }
"""

WARDEN_JS = r"""
// Sunken Citadel -- Warden.js
//
// The boss: a stone Warden with three attacks and a real rhythm.  It picks a
// swing, telegraphs it for a beat, commits, then opens up for a counter-window --
// the same read-and-react loop as the Hollows, at a scale where getting it wrong
// costs a quarter of your health.
//
// Params: hp=420, speed=3.2, slamDamage=34, sweepDamage=22, chargeDamage=26,
//         slamReach=4.6, sweepReach=5.4, chargeReach=3.0, windup=0.85,
//         recover=1.05, sight=34.0

var hp = 420;
var maxHp = 420;
var speed = 3.2;
var state = "idle";
var timer = 2.0;
var dead = false;
var hurtFlash = 0.0;
var attack = "slam";
var chargeX = 0.0, chargeZ = 0.0;
var eye = null;
var rage = false;

function configure(h, s) {
    hp = h;
    maxHp = h;
    speed = s;
}

function start() {
    self.tag = "Enemy";
    eye = scene.spawn("HollowEye", self.x, 3.4, self.z);
    if (eye) {
        eye.setParentTo(self);
        eye.scaleX = 2.2; eye.scaleY = 2.2; eye.scaleZ = 2.2;
    }
    state = "idle";
    timer = 2.2;
}

function update(dt) {
    if (dead) return;
    if (hurtFlash > 0.0) { hurtFlash -= dt; self.color = "#FFFFFFFF"; }
    timer -= dt;
    var k = scene.find("Knight");
    if (!k) return;
    var d = groundDist(k);
    var c = scene.find("Citadel");
    if (c && c.send("isFrozen")) return;
    if (!rage && hp < maxHp * 0.45) {
        rage = true;
        speed += 1.1;
        var ct = scene.find("Citadel");
        if (ct) ct.send("say", "The Warden cracks -- it moves faster now.");
        scene.shake(0.5);
    }

    if (state === "idle") {
        if (d > sight) {
            // The Warden never comes out of the keep: it is the last fight, and
            // it waits on its plinth.
            timer = Math.max(timer, 1.0);
            return;
        }
        if (d > 6.0) {
            stepToward(k.x, k.z, speed, dt);
            self.y = Math.abs(Math.sin(time.time * 3.4)) * 0.18;
        }
        if (timer <= 0.0) {
            // Attack choice by range: slam up close, sweep at mid, charge far.
            attack = d < slamReach ? "slam" : (d < sweepReach + 3.0 ? "sweep" : "charge");
            state = "windup";
            timer = attack === "charge" ? windup + 0.25 : windup;
            if (eye) eye.color = "#FFFF6A4A";
        }
    } else if (state === "windup") {
        self.y = 0.25 + Math.sin((windup - timer) * 10.0) * 0.12;
        if (timer <= 0.0) {
            state = "strike";
            timer = attack === "charge" ? 0.55 : 0.22;
            if (attack === "charge") {
                var dx = k.x - self.x, dz = k.z - self.z;
                var dd = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
                chargeX = dx / dd; chargeZ = dz / dd;
            }
        }
    } else if (state === "strike") {
        if (attack === "charge") {
            self.setPosition(self.x + chargeX * 17.0 * dt, self.y, self.z + chargeZ * 17.0 * dt);
            if (d < chargeReach + 1.0) k.send("hurt", chargeDamage, self.x, self.z);
        } else if (attack === "slam") {
            if (timer < 0.14 && d < slamReach) k.send("hurt", slamDamage, self.x, self.z);
        } else if (timer < 0.14 && d < sweepReach) {
            k.send("hurt", sweepDamage, self.x, self.z);
        }
        if (timer <= 0.0) {
            state = "recover";
            timer = recover * (rage ? 0.7 : 1.0);
            if (eye) eye.color = "#FFC9453B";
            if (attack === "slam" || attack === "charge") {
                audio.play("sfx_slam.ogg");
                scene.shake(0.65);
                var fx = scene.find("Fx");
                if (fx) fx.send("quake", self.x, self.z);
            }
        }
    } else if (state === "recover") {
        if (timer <= 0.0) { state = "idle"; timer = rage ? 1.1 : 1.7; }
    }
    self.rotY = Math.atan2(k.x - self.x, k.z - self.z) * 180.0 / Math.PI;
}

function stepToward(x, z, sp, dt) {
    var dx = x - self.x, dz = z - self.z;
    var dd = Math.sqrt(dx * dx + dz * dz);
    if (dd < 0.05) return;
    var step = Math.min(sp * dt, dd);
    self.setPosition(self.x + (dx / dd) * step, self.y, self.z + (dz / dd) * step);
}

/** Ground distance: the courtyard is flat, so height must not shrink a gap. */
function groundDist(o) {
    var dx = o.x - self.x, dz = o.z - self.z;
    return Math.sqrt(dx * dx + dz * dz);
}

function hurt(amount, fromX, fromZ) {
    if (dead) return;
    hp -= amount;
    hurtFlash = 0.2;
    self.color = "#FFFFD0D0";
    var fx = scene.find("Fx");
    if (fx) fx.send("hit", self.x, self.y + 2.6, self.z);
    if (hp <= 0.0) {
        dead = true;
        if (eye) eye.destroy();
        state = "dead";
        var c = scene.find("Citadel");
        if (c) c.send("onBossDown");
        var fx2 = scene.find("Fx");
        if (fx2) {
            fx2.send("crumble", self.x, self.z);
            fx2.send("quake", self.x, self.z);
        }
        scene.shake(1.0);
        self.destroy();
    }
}

function getHp() { return hp; }
function getState() { return state; }
function isRaging() { return rage; }
"""

COMBAT_JS = r"""
// Sunken Citadel -- Combat.js
//
// One place resolves a swing, so the knight's combo, a Hollow's strike and the
// Warden's slam cannot disagree about what "in reach" means.  A strike is a cone:
// everything inside `reach` whose bearing is within `arc` degrees of the
// attacker's facing takes the damage and is knocked outward.
//
// Params: arc=62.0, knockback=1.9

var hits = 0;

function update(dt) {
}

/**
 * `strike(attacker, damage, reach, facing)` -- facing is degrees, where 0 looks
 * down -Z (the engine's convention for rotY).
 */
function strike(attacker, damage, reach, facing) {
    var targets;
    if (!attacker.send("isInvulnerable") && attacker.send("getHp") !== null &&
        scene.find("Knight") !== attacker) {
        targets = null;
    }
    // Attackers are the knight or a Hollow/Warden: the knight hits Hollows, the
    // Hollows hit the knight.  One rule, both directions.
    var side = attacker.send("getHits") !== null ? "Enemy" : "Knight";
    var list = scene.findAll(side);
    var n = 0;
    for (var i = 0; i < list.length; i++) {
        var t = list[i];
        var dx = t.x - attacker.x, dz = t.z - attacker.z;
        var d = Math.sqrt(dx * dx + dz * dz);
        if (d > reach) continue;
        var bearing = Math.atan2(dx, -dz) * 180.0 / Math.PI;
        var delta = ((bearing - facing + 540.0) % 360.0) - 180.0;
        if (Math.abs(delta) > arc * 0.5) continue;
        t.send("hurt", damage, attacker.x, attacker.z);
        // Push the target out of the arc: without it a Hollow standing inside
        // your reach takes the whole combo while never leaving the swing.
        var push = knockback / Math.max(0.4, d);
        if (t.move) t.move(dx * push, 0, dz * push);
        n++;
        hits++;
    }
    return n;
}

function getHits() { return hits; }
"""

PICKUP_JS = r"""
// Sunken Citadel -- Pickup.js
//
// Loot: a dropped flask, a bundle of cloth, a coin.  Walking over it collects it,
// because stopping to press a button mid-fight is a worse game.  Drops are pooled
// templates spawned by the director and the enemies.
//
// Params: heal=26, kind=flask, life=26.0

var collected = false;
var age = 0.0;

function update(dt) {
    age += dt;
    var k = scene.find("Knight");
    if (!k) return;
    // Loot is collected by walking over it; the drop floats, so the check is on
    // the floor, not through the air.
    var dx = k.x - self.x, dz = k.z - self.z;
    if (Math.sqrt(dx * dx + dz * dz) < 1.7) {
        collect(k);
        return;
    }
    // A gentle spin so loot reads as an item rather than scenery.
    self.rotY = age * 70.0;
    self.y = 0.75 + Math.sin(age * 2.4) * 0.12;
    if (age > life) self.destroy();
}

function collect(k) {
    if (collected) return;
    collected = true;
    if (kind === "flask") {
        k.send("heal", heal);
        var c = scene.find("Citadel");
        if (c) c.send("say", "You drink.  +" + heal + " health.");
    } else {
        var c2 = scene.find("Citadel");
        if (c2) c2.send("say", "Loot taken.");
    }
    audio.play("sfx_loot.ogg", 0.7);
    var fx = scene.find("Fx");
    if (fx) fx.send("pickup", self.x, self.y, self.z);
    self.destroy();
}
"""

FX_JS = r"""
// Sunken Citadel -- Fx.js
//
// Pooled combat effects: swing arcs, impacts, hit sparks, crumble bursts, the
// quake ring, and the summon flash a Hollow arrives in.  Every one is an
// inactive template cloned by scene.spawn() and destroyed on a timer, which is
// how the engine pools.
//
// Params: shortLife=0.5, ringLife=1.1

function update(dt) {
}

function oneShot(proto, x, y, z, burst, life) {
    var go = scene.spawn(proto, x, y, z);
    if (!go) return null;
    if (burst > 0) go.burst(burst);
    after(life, function () { go.destroy(); });
    return go;
}

function swing(x, y, z, facing, heavy) {
    var go = oneShot("SlashFx", x, y, z, heavy ? 22 : 12, shortLife);
    if (go) { go.rotY = facing; go.scaleX = heavy ? 1.8 : 1.3; go.scaleZ = go.scaleX; }
}

function impact(x, y, z) {
    oneShot("ImpactFx", x, y, z, 16, shortLife);
}

function hit(x, y, z) {
    oneShot("SparkFx", x, y, z, 10, shortLife);
}

function crumble(x, z) {
    oneShot("DebrisFx", x, 0.5, z, 26, ringLife);
}

function quake(x, z) {
    oneShot("QuakeFx", x, 0.1, z, 34, ringLife * 1.4);
}

function summon(x, z) {
    oneShot("SummonFx", x, 0.2, z, 20, ringLife);
}

function roll(x, z) {
    oneShot("DustFx", x, 0.2, z, 14, shortLife);
}

function wave(x, z) {
    oneShot("SummonFx", x, 0.2, z, 40, ringLife * 1.5);
}

function pickup(x, y, z) {
    oneShot("SparkFx", x, y, z, 14, shortLife);
}
"""

HUD_JS = r"""
// Sunken Citadel -- Hud.js
//
// Screen-space HUD: wave banner, health, stamina, combo counter, score and the
// director's toast.  Bars are sprite quads scaled in the UI view, so they track
// the same numbers the scripts use -- there is no second source of truth.
//
// Params: hpWidth=4.4, stamWidth=3.2

var title = "";
var waveLine = "";
var scoreLine = "";
var comboLine = "";
var toastLine = "";
var hpFrac = 1.0;
var stamFrac = 1.0;

function update(dt) {
    var c = scene.find("Citadel");
    var k = scene.find("Knight");
    if (!c || !k) return;

    var lost = c.send("hasLost");
    var won = c.send("hasWon");
    if (won) title = "THE CITADEL IS QUIET";
    else if (lost) title = "THE KNIGHT FALLS";
    else if (c.send("isBossAlive")) title = "THE WARDEN OF THE KEEP";
    else title = "WAVE " + c.send("getWave") + " / " + c.send("getWaveCount");

    waveLine = "hollows " + c.send("getAlive") + "   kills " + c.send("getKills");
    scoreLine = "score " + Math.floor(c.send("getScore"));
    var combo = k.send("getCombo") + 1;
    comboLine = combo > 1 ? "COMBO x" + combo : "";
    toastLine = c.send("getToast");

    var hp = k.send("getHp");
    var st = k.send("getStamina");
    hpFrac = Math.max(0.0, Math.min(1.0, hp / 120.0));
    stamFrac = Math.max(0.0, Math.min(1.0, st / 100.0));

    set("HudTitle", title);
    set("HudWave", waveLine);
    set("HudScore", scoreLine);
    set("HudCombo", comboLine);
    set("HudToast", toastLine);

    var hpBar = scene.find("HpBar");
    if (hpBar) hpBar.scaleX = Math.max(0.001, hpWidth * hpFrac);
    var stBar = scene.find("StamBar");
    if (stBar) stBar.scaleX = Math.max(0.001, stamWidth * stamFrac);
}

function set(name, value) {
    var o = scene.find(name);
    if (o) o.text = value;
}
"""


def build_scene_scripts():
    return {
        "Knight.js": KNIGHT_JS,
        "Citadel.js": CITADEL_JS,
        "Hollow.js": HOLLOW_JS,
        "Revenant.js": REVENANT_JS,
        "Warden.js": WARDEN_JS,
        "Combat.js": COMBAT_JS,
        "Pickup.js": PICKUP_JS,
        "Fx.js": FX_JS,
        "Hud.js": HUD_JS,
    }


def add_units(sc):
    """The knight, the Hollow templates and the Warden template."""
    # ---------------------------------------------------------------- the knight
    root = sc.add(KNIGHT, x=0.0, y=0.0, z=8.0, rotY=180.0, tag="Knight",
                  comps=[script("Knight.js",
                                "speed=7.6, turn=220, hp=120, lightDamage=14, "
                                "heavyDamage=32, lightReach=2.6, heavyReach=3.1, "
                                "heavyArc=0.5, lightArc=0.75, staminaMax=100, "
                                "dodgeCost=22, lightCost=9, heavyCost=26, "
                                "staminaRegen=22, dodgeSpeed=15.5, dodgeTime=0.34, "
                                "iframes=0.42")])
    body_part(sc, "KnightBootL", root, "Cube", -0.19, 0.16, 0.0, 0.24, 0.32, 0.36, WOOD_D)
    body_part(sc, "KnightBootR", root, "Cube", 0.19, 0.16, 0.0, 0.24, 0.32, 0.36, WOOD_D)
    body_part(sc, "KnightLegL", root, "Capsule", -0.2, 0.62, 0.0, 0.26, 0.72, 0.26, STONE_D)
    body_part(sc, "KnightLegR", root, "Capsule", 0.2, 0.62, 0.0, 0.26, 0.72, 0.26, STONE_D)
    body_part(sc, "KnightTorso", root, "Capsule", 0.0, 1.3, 0.0, 0.76, 0.86, 0.6, STONE)
    body_part(sc, "KnightBelt", root, "Cube", 0.0, 0.96, 0.0, 0.78, 0.15, 0.62, BRONZE)
    body_part(sc, "KnightPauldronL", root, "Sphere", -0.46, 1.62, 0.0, 0.42, 0.34, 0.46, STONE_L)
    body_part(sc, "KnightPauldronR", root, "Sphere", 0.46, 1.62, 0.0, 0.42, 0.34, 0.46, STONE_L)
    body_part(sc, "KnightCape", root, "Cube", 0.0, 1.32, -0.34, 0.62, 1.0, 0.12, BANNER)
    body_part(sc, "KnightHead", root, "Sphere", 0.0, 1.94, 0.0, 0.5, 0.5, 0.5, STONE_L)
    body_part(sc, "KnightHelm", root, "Cylinder", 0.0, 2.02, 0.0, 0.6, 0.44, 0.6, STONE)
    body_part(sc, "KnightVisor", root, "Cube", 0.0, 1.94, 0.24, 0.44, 0.12, 0.1, SLATE)
    # Shield on the left arm, sword on the right: both primitives, so the swing
    # animation is a rotation the Knight script owns.
    body_part(sc, "Shield", root, "Cube", -0.58, 1.22, 0.26, 0.72, 0.86, 0.12, STONE_D)
    sword = sc.add("Sword", x=0.6, y=1.3, z=0.34, rotX=-12.0, parent=root, tag="Knight",
                   comps=[])
    body_part(sc, "Blade", sword, "Cube", 0.0, 0.34, 0.0, 0.09, 1.7, 0.16, STONE_L)
    body_part(sc, "Guard", sword, "Cube", 0.0, -0.5, 0.0, 0.44, 0.1, 0.12, BRONZE)
    body_part(sc, "Grip", sword, "Cube", 0.0, -0.66, 0.0, 0.11, 0.3, 0.11, WOOD_D)

    # ---------------------------------------------------------------- enemy templates
    def hollow_body(proto, parent, scale, body_color, eye_height, eye_size):
        body_part(sc, proto + "Torso", parent, "Capsule", 0.0, 1.3 * scale, 0.0,
                  0.8 * scale, 1.1 * scale, 0.7 * scale, body_color)
        body_part(sc, proto + "Head", parent, "Cube", 0.0, 2.0 * scale, 0.0,
                  0.62 * scale, 0.6 * scale, 0.6 * scale, HOLLOW_D)
        body_part(sc, proto + "ArmL", parent, "Cube", -0.62 * scale, 1.35 * scale, 0.1,
                  0.24 * scale, 0.9 * scale, 0.24 * scale, body_color, rot_x=14.0)
        body_part(sc, proto + "ArmR", parent, "Cube", 0.62 * scale, 1.35 * scale, 0.1,
                  0.24 * scale, 0.9 * scale, 0.24 * scale, body_color, rot_x=14.0)
        body_part(sc, proto + "LegL", parent, "Cube", -0.24 * scale, 0.4 * scale, 0.0,
                  0.28 * scale, 0.8 * scale, 0.3 * scale, HOLLOW_D)
        body_part(sc, proto + "LegR", parent, "Cube", 0.24 * scale, 0.4 * scale, 0.0,
                  0.28 * scale, 0.8 * scale, 0.3 * scale, HOLLOW_D)
        # A face plate, not a light: the spawned "HollowEye" template is the one
        # that glows, and one eye per enemy is enough.
        body_part(sc, proto + "Face", parent, "Cube", 0.0, eye_height - 0.2,
                  0.24 * scale, 0.5 * scale, 0.16 * scale, 0.1 * scale, EYE,
                  emission=0.7, specular=0.0)

    hollow_id = sc.add("Hollow", tag="Hollow", active=False,
                       comps=[script("Hollow.js",
                         "hp=46, speed=3.1, damage=13, reach=2.3, windup=0.55, "
                         "recover=0.7, sight=26.0, kind=hollow")])
    hollow_body("Hollow", hollow_id, 1.0, HOLLOW, 2.0, 0.22)

    revenant_id = sc.add("Revenant", tag="Revenant", active=False,
                         comps=[script("Revenant.js",
                         "hp=74, speed=3.9, damage=17, reach=2.7, windup=0.42, "
                         "recover=0.55, sight=30.0, strafeTime=1.4")])
    hollow_body("Revenant", revenant_id, 1.12, PALETTE["stone_dark"], 2.24, 0.26)

    warden_id = sc.add("Warden", tag="Boss", active=False, sy=1.5,
                       comps=[script("Warden.js",
                         "hp=420, speed=3.2, slamDamage=34, sweepDamage=22, "
                         "chargeDamage=26, slamReach=4.6, sweepReach=5.4, "
                         "chargeReach=3.0, windup=0.85, recover=1.05, sight=34.0")])
    body_part(sc, "WardenLegL", warden_id, "Cylinder", -0.5, 0.9, 0.0, 0.6, 1.8, 0.6, SLATE)
    body_part(sc, "WardenLegR", warden_id, "Cylinder", 0.5, 0.9, 0.0, 0.6, 1.8, 0.6, SLATE)
    body_part(sc, "WardenTorso", warden_id, "Cube", 0.0, 2.5, 0.0, 2.0, 1.7, 1.3, STONE_D)
    body_part(sc, "WardenShoulderL", warden_id, "Cube", -1.25, 3.1, 0.0, 0.7, 0.9, 1.0, STONE)
    body_part(sc, "WardenShoulderR", warden_id, "Cube", 1.25, 3.1, 0.0, 0.7, 0.9, 1.0, STONE)
    body_part(sc, "WardenArmL", warden_id, "Cylinder", -1.5, 2.0, 0.2, 0.42, 1.6, 0.42, SLATE)
    body_part(sc, "WardenArmR", warden_id, "Cylinder", 1.5, 2.0, 0.2, 0.42, 1.6, 0.42, SLATE)
    body_part(sc, "WardenHornL", warden_id, "Cone", -0.5, 3.7, 0.1, 0.34, 0.8, 0.34, BRONZE,
              rot_x=-18.0)
    body_part(sc, "WardenHornR", warden_id, "Cone", 0.5, 3.7, 0.1, 0.34, 0.8, 0.34, BRONZE,
              rot_x=-18.0)
    body_part(sc, "WardenHead", warden_id, "Cube", 0.0, 3.5, 0.0, 1.0, 0.8, 0.9, STONE)
    body_part(sc, "WardenCore", warden_id, "Sphere", 0.0, 2.6, 0.62, 0.7, 0.7, 0.5, EMBERT,
              emission=1.9, specular=0.0)


def add_loot(sc):
    """Loot templates and the pre-placed reward crates."""
    sc.add("Flask", tag="Template", active=False,
           comps=[mesh(kind="Sphere", color="#FF6FC8B0", emission=0.5, specular=0.4,
                       shininess=40.0),
                  script("Pickup.js", "heal=26, kind=flask, life=26.0")])
    sc.add("Coin", tag="Template", active=False,
           comps=[mesh(kind="Torus", color=BRONZE, specular=0.7, shininess=60.0,
                       emission=0.25),
                  script("Pickup.js", "heal=0, kind=coin, life=26.0")])
    for i, (x, z) in enumerate(((-13.0, -13.0), (13.0, -13.0), (-13.0, 13.0),
                                (13.0, 13.0), (0.0, -16.0), (0.0, 16.0))):
        prop_(sc, "pot_large" if i % 2 else "pot_small", x, z, 1.8 if i % 2 else 1.2,
              color=CLOTH, tag="Loot")
    # A stack of flasks by the keep, so the fight has a first aid point.
    for i, (x, z) in enumerate(((-6.5, -8.5), (6.5, -8.5), (-6.5, 8.5), (6.5, 8.5))):
        sc.add(f"Keg_{i}", x=x, y=0.5, z=z, tag="Loot",
               comps=[mesh(kind="Cylinder", color=BRONZE, specular=0.4, shininess=30.0)])


def build():
    sc = build_scene()
    add_units(sc)
    add_loot(sc)

    # ---------------------------------------------------------------- fx templates
    sc.add("SlashFx", tag="Template", active=False,
           comps=[particles(color="#C8DCE8FF", end="#00DCE8FF", size=0.3, end_size=1.5,
                            rate=0.0, lifetime=0.35, speed=7.0, direction=0, spread=70,
                            gravity=0.0, max_particles=40, additive=True,
                            texture="fx_ring.png", emitting=False)])
    sc.add("ImpactFx", tag="Template", active=False,
           comps=[particles(color="#FFFFE0A0", end="#00FFB060", size=0.22, end_size=0.05,
                            rate=0.0, lifetime=0.45, speed=6.0, direction=90, spread=180,
                            gravity=-3.0, max_particles=30, additive=True,
                            texture="fx_spark.png", emitting=False)])
    sc.add("SparkFx", tag="Template", active=False,
           comps=[particles(color="#FFFFF0C0", end="#00FFC060", size=0.18, end_size=0.02,
                            rate=0.0, lifetime=0.5, speed=4.5, direction=90, spread=180,
                            gravity=-4.0, max_particles=26, additive=True,
                            texture="fx_star.png", emitting=False)])
    sc.add("DebrisFx", tag="Template", active=False,
           comps=[particles(color="#B0A8A0A8", end="#00A0A0A8", size=0.4, end_size=1.1,
                            rate=0.0, lifetime=1.0, speed=5.0, direction=90, spread=180,
                            gravity=-9.0, max_particles=50, additive=False,
                            texture="fx_dust.png", emitting=False)])
    sc.add("QuakeFx", tag="Template", active=False,
           comps=[particles(color="#C8E0C8A0", end="#00C8C090", size=0.5, end_size=2.6,
                            rate=0.0, lifetime=1.1, speed=9.0, direction=90, spread=180,
                            gravity=-2.0, max_particles=60, additive=True,
                            texture="fx_ring.png", emitting=False)])
    sc.add("SummonFx", tag="Template", active=False,
           comps=[particles(color="#C89070FF", end="#00302060", size=0.4, end_size=1.8,
                            rate=0.0, lifetime=0.9, speed=3.0, direction=90, spread=180,
                            gravity=0.4, max_particles=40, additive=True,
                            texture="fx_magic.png", emitting=False)])
    sc.add("DustFx", tag="Template", active=False,
           comps=[particles(color="#90C8BFA8", end="#00C8BFA8", size=0.4, end_size=1.2,
                            rate=0.0, lifetime=0.6, speed=2.4, direction=90, spread=180,
                            gravity=-2.0, max_particles=24, additive=True,
                            texture="fx_dust.png", emitting=False)])
    sc.add("HollowEye", tag="Template", active=False,
           comps=[mesh(kind="Sphere", color="#FFC9453B", emission=1.6, specular=0.0),
                  light(kind="Point", color="#FFFF4A3A", intensity=0.8, rng=7.0)])

    # ---------------------------------------------------------------- director + hud
    sc.add("Combat", tag="Director", comps=[script("Combat.js", "arc=62.0, knockback=1.9")])
    sc.add("Citadel", tag="Director",
           comps=[script("Citadel.js",
                         "waveGap=6.0, restGap=9.0, bossHp=420, scorePerKill=25, "
                         "waveBonus=250")])
    sc.add("Fx", tag="Fx", comps=[script("Fx.js", "shortLife=0.5, ringLife=1.1")])
    sc.add("Hud", tag="Hud", comps=[script("Hud.js", "hpWidth=4.4, stamWidth=3.2")])

    sc.add("HudPanel", x=-5.4, y=-4.3, sx=7.0, sy=1.05, tag="Hud",
           comps=[sprite("hud_panel.png", order=80, screen=True)])
    sc.add("HudTitle", x=-5.0, y=3.9, tag="Hud",
           comps=[text("WAVE 1 / 3", size=0.62, color="#FFFFE0A8", screen=True)])
    sc.add("HudWave", x=-5.0, y=3.3, tag="Hud",
           comps=[text("hollows 0   kills 0", size=0.44, color="#FFD8D2C0", screen=True)])
    sc.add("HudScore", x=1.9, y=3.9, tag="Hud",
           comps=[text("score 0", size=0.5, color="#FFDCE8FF", screen=True)])
    sc.add("HudCombo", x=1.9, y=3.3, tag="Hud",
           comps=[text("", size=0.5, color="#FFFFB060", screen=True)])
    sc.add("HpBack", x=-4.6, y=-4.15, sx=4.6, sy=0.4, tag="Hud",
           comps=[sprite("ui_slot.png", "#88101418", order=81, screen=True)])
    sc.add("HpBar", x=-6.8, y=-4.15, sx=4.4, sy=0.3, tag="Hud",
           comps=[sprite("ui_slot.png", "#FFC0453B", order=82, screen=True)])
    sc.add("StamBack", x=-4.2, y=-4.6, sx=3.4, sy=0.32, tag="Hud",
           comps=[sprite("ui_slot.png", "#88101418", order=81, screen=True)])
    sc.add("StamBar", x=-5.8, y=-4.6, sx=3.2, sy=0.24, tag="Hud",
           comps=[sprite("ui_slot.png", "#FF86C8A0", order=82, screen=True)])
    sc.add("HudGem", x=6.4, y=-4.3, sx=0.7, sy=0.7, tag="Hud",
           comps=[sprite("hud_gem.png", order=83, screen=True)])
    sc.add("HudToast", y=2.75, tag="Hud",
           comps=[text("", size=0.5, color="#FFFFD98A", align="Center", screen=True)])

    assets = build_assets()
    scripts = build_scene_scripts()
    man = manifest(
        GID, "Sunken Citadel", "3D action arena combat",
        list(scripts.keys()), assets, orientation=0,
        controls={"axis": "left stick / WASD", "Attack": "A / space (light combo)",
                  "Heavy": "heavy attack action", "Dodge": "dodge action (roll)",
                  "Jump": "rise again after the knight falls"},
        systems=["third-person camera", "three-step light combo plus a heavy swing",
                 "dodge roll with invulnerability frames", "stamina economy",
                 "wave director with rest phases", "telegraph/strike/recover enemy AI",
                 "a boss with three ranged-by-distance attacks and a rage phase",
                 "loot pickups", "HUD with health and stamina bars",
                 "pooled combat FX", "brazier point lights"],
        notes=[
            "3D kit: kenney_nature_kit, dressed as a citadel instead of a valley -- "
            "the same pack supplies the walls (cliff cells), the bridges, the moat "
            "(water plates), the keep (stone platforms) and the statues.",
            "One colour per mesh object (Meshes.loadObj ignores mtllib/usemtl), so "
            "every tint is the model's face-dominant material mapped through this "
            "game's palette; see tools/biggames/kit3d.py.",
            "Motion is script-owned rather than Rigidbody3D/Collider3D.  The offline "
            "harness (tools/run_games.js) does not run PhysicsWorld3D, so a "
            "physics-driven combat game could not be verified before shipping; the "
            "scripts own position, and reach is resolved as a cone in Combat.js.",
            "Combat resolves in one place: Combat.strike(attacker, damage, reach, "
            "facing) hits whichever side the attacker is not on, so a Hollow, a "
            "Revenant, the Warden and the knight all share one definition of a hit.",
            "The engine keeps the four point lights nearest the camera "
            "(Renderer3D.setupLights), which is why the braziers are many and small "
            "rather than a few large ones.",
        ])
    return sc, man, assets, scripts


def main():
    sc, man, assets, scripts = build()
    readme = """# Sunken Citadel

A third-person 3D arena-combat game: a knight, a walled citadel and three waves of
Hollows, built from the store's 3D kit (`kenney_nature_kit`) dressed as stone.

## The loop

Waves spawn in a ring around the courtyard and walk at you.  Each enemy telegraphs
its swing before it lands -- a red-eyed wind-up -- and opens up afterwards.  Spend
stamina on a light combo (three swings), a heavy swing (interrupts a wind-up), or a
dodge roll with real invulnerability frames.  Clear three waves, then the Warden of
the Keep wakes up and fights with a slam, a sweep and a charge, faster once it is
below half health.

## Systems

| System | Where |
| --- | --- |
| Movement, combo, heavy, dodge, stamina | `Knight.js` |
| Waves, rest phases, boss summon, score | `Citadel.js` |
| Telegraph/strike/recover melee enemy | `Hollow.js` |
| Faster circling enemy | `Revenant.js` |
| Boss with three attacks and a rage phase | `Warden.js` |
| One definition of a hit | `Combat.js` |
| Walking-over loot | `Pickup.js` |
| Pooled combat FX | `Fx.js` |
| HUD: wave, health, stamina, combo, score | `Hud.js` |

## Reading this game

`tools/run_games.js 12_sunken --frames 900 --dump /tmp/w` runs the waves headless
(an idle knight is killed by wave 2, which is the harness proving the enemies
actually attack), and `tools/preview3d.py 12_sunken_citadel_3d --world /tmp/w/*.json
--out c.png --camera free --target 0,0,0 --dist 60 --pitch 26` renders the
courtyard; `--camera cam` is the knight's own view.
"""
    write_game(GID, man, sc, scripts, readme)
    print(f"  wrote {GID}: {len(sc.objs)} objects, {len(assets)} assets")


if __name__ == "__main__":
    main()
