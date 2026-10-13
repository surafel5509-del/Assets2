#!/usr/bin/env python3
"""Generates games/11_emberfall_valley_3d -- the first of the two 3D games.

A third-person open-world survival-exploration game built out of Kenney's Nature
Kit (the store's only 3D model pack: 329 OBJs, 23 flat materials, no textures).

What the engine can and cannot do with this kit is documented in
tools/biggames/nature.py; the two facts that shape every number in this file are

  * a loaded model is one unit across and centred on its origin, so a ground
    prop is placed at y = size / 2 and a mesh object carries its own size, and
  * one object is one colour, so the tint is the model's face-dominant material
    (read from the pack's .mtl files) unless the scene deliberately overrides it.

The valley is authored as terrain sections (meadow, river, cliffs, farm, camp,
stone circle, beacon hills) with deterministic scatter on top, then nine scripts
drive it: a ranger you steer, a day/night cycle with weather, forageable nodes,
three beacons to light, wildlife herds, a camp you can sleep at, a HUD, FX, and
the director that ties the goals together.
"""

from __future__ import annotations

import math
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "biggames"))

from scene_kit import (Scene, asset, audio_src, cam3d, clip, collider3d, light,  # noqa: E402
                       manifest, mesh, particles, rigid3d, scatter, script, text,
                       sprite, write_game)
import nature  # noqa: E402
import kit3d  # noqa: E402
from kit3d import body_part, ground, prop  # noqa: E402

# Default tints come from kit3d, which maps each model's dominant kit material
# through this valley's palette.
_TINT_CTX = {"palette": {}, "mapping": {}}


def _kprop(sc, model, x, z, size, **kw):
    return kit3d.prop(sc, model, x, z, size, palette=PALETTE,
                      mapping=TINT, **_TINT_CTX and kw)


def _kground(sc, model, x, z, sx, sz, **kw):
    return kit3d.ground(sc, model, x, z, sx, sz, palette=PALETTE, mapping=TINT, **kw)

GID = "11_emberfall_valley_3d"
KIT = nature.PACK_ID
PARTICLES = "kenney_particle_pack"
UI_PACK = "kenney_ui_pack"
TINY_UI = "tinyrpg_dragonregaliagui_v1_0"

PAL = nature.palette()
IDX = nature.model_index()

# --------------------------------------------------------------------------- art
#
# The kit's material palette is Kenney's demo palette: grass #2CD8B8 is a bright
# teal, dirt #E28357 a hot orange.  Rendered with flat shading across a whole
# valley it reads as neon, so the valley uses its own palette below.  Every entry
# is a deliberate art direction for this game, not a kit colour -- the model
# *choice* is still driven by the kit's dominant material (nature.py), and the
# kit's own hex is the fallback for anything not listed here.
PALETTE = {
    "ground_grass":   "#8FB26A",   # muted meadow
    "ground_pathOpen": "#A8814F",
    "ground_riverOpen": "#4E8FB5",
    "platform_beach": "#D8C79A",
    "cliff":          "#B4AFA2",   # warm grey rock
    "cliff_dark":     "#8E8A7E",
    "rock":           "#A8A093",
    "stone":          "#A9A79B",
    "stone_dark":     "#84837A",
    "wood":           "#9C6B43",
    "wood_bark":      "#7A5232",
    "wood_inner":     "#C6A377",
    "leaf":           "#5C8F4A",
    "leaf_dark":      "#3F6B39",
    "leaf_fall":      "#C6803C",
    "water":          "#4E8FB5",
    "sand":           "#D8C79A",
    "red":            "#9E4A44",
    "yellow":         "#D9A94A",
    "purple":         "#7A6BB5",
    "tan":            "#D9B48C",
    "corn":           "#D8B75B",
    "white":          "#E8E4DA",
}
GRASS = PALETTE["ground_grass"]
DIRT = PALETTE["rock"]
DIRT_DARK = PALETTE["ground_pathOpen"]
STONE = PALETTE["stone"]
STONE_DARK = PALETTE["stone_dark"]
WATER = PALETTE["water"]
WOOD = PALETTE["wood"]
WOOD_BARK = PALETTE["wood_bark"]
WOOD_INNER = PALETTE["wood_inner"]
LEAF = PALETTE["leaf"]
LEAF_DARK = PALETTE["leaf_dark"]
LEAF_FALL = PALETTE["leaf_fall"]
SAND = PALETTE["sand"]
RED = PALETTE["red"]
YELLOW = PALETTE["yellow"]
PURPLE = PALETTE["purple"]
WHITE = PALETTE["white"]
TAN = PALETTE["tan"]
CORN = PALETTE["corn"]

# The valley: 120 x 120 units, walkable from -52..52 in x and z.
HALF = 52.0
BANK = 10.0          # the river runs north-south at x = BANK .. BANK + BANK_W
BANK_W = 9.0
BRIDGE_Z = 0.0

# Kit material name -> valley palette.  `prop()` falls back to this, so a model
# without an explicit tint still lands inside the game's art direction.
TINT = {
    "leafsGreen": LEAF, "leafsDark": LEAF_DARK, "leafsFall": LEAF_FALL,
    "grass": GRASS, "dirt": DIRT, "dirtDark": DIRT_DARK, "stone": STONE,
    "stoneDark": STONE_DARK, "wood": WOOD, "woodBark": WOOD_BARK,
    "woodBarkDark": WOOD_BARK, "woodInner": WOOD_INNER, "woodDark": WOOD_BARK,
    "woodBirch": WOOD_INNER, "water": WATER, "colorRed": RED, "colorRedDark": RED,
    "colorYellow": YELLOW, "colorPurple": PURPLE, "colorTan": TAN,
    "colorWhite": WHITE, "corn": CORN, "_defaultMat": STONE,
}

PLAYER = "Ranger"


# --------------------------------------------------------------------------- assets


def build_assets():
    a = []

    def obj(name, as_=None):
        entry = nature.model_asset(name, as_ or (name + ".obj"))
        a.append(asset(KIT, entry["path"], entry["as"]))

    # --- terrain
    for name in ("ground_grass", "ground_pathOpen", "ground_riverOpen",
                 "platform_beach"):
        obj(name)
    # --- cliffs and rocks (stone family, then dirt/grass variants)
    for name in ("cliff_stone", "cliff_large_stone", "cliff_corner_stone",
                 "cliff_cornerInner_stone", "cliff_diagonal_stone",
                 "cliff_blockHalf_stone", "cliff_cave_stone", "cliff_steps_stone",
                 "cliff_blockSlope_stone", "cliff_rock", "cliff_cave_rock",
                 "cliff_blockSlope_rock", "cliff_cornerInnerLarge_rock",
                 "cliff_large_rock"):
        obj(name)
    for name in ("rock_smallA", "rock_smallC", "rock_smallE", "rock_smallG",
                 "rock_tallA", "rock_tallC", "rock_largeA", "stone_smallA",
                 "stone_smallD", "stone_tallA", "stone_tallD", "stone_largeA"):
        obj(name)
    # --- vegetation
    for name in ("plant_bush", "plant_bushDetailed", "plant_bushLarge",
                 "plant_bushLargeTriangle", "plant_bushSmall", "plant_flatShort",
                 "plant_flatTall", "grass", "grass_large", "grass_leafs",
                 "grass_leafsLarge", "hanging_moss"):
        obj(name)
    for name in ("tree_default", "tree_pineTallB", "tree_pineRoundC",
                 "tree_oak", "tree_thin", "tree_default_fall", "stump_round",
                 "stump_oldTall", "log", "log_stack", "log_large"):
        obj(name)
    for name in ("mushroom_red", "mushroom_redGroup", "mushroom_tan",
                 "flower_redA", "flower_yellowA", "flower_purpleA", "cactus_short",
                 "cactus_tall"):
        obj(name)
    # --- camp, farm, structures
    for name in ("tent_detailedOpen", "tent_smallClosed", "bed", "campfire_logs",
                 "campfire_stones", "campfire_planks", "fence_planks",
                 "fence_planksDouble", "fence_gate", "bridge_stone",
                 "bridge_stoneNarrow", "path_stone", "path_stoneCircle",
                 "sign", "pot_large", "pot_small", "platform_grass", "bridge_center_wood",
                 "crops_dirtSingle",
                 "crops_dirtRow", "crops_wheatStageB", "crops_cornStageD",
                 "crop_pumpkin", "crop_carrot", "statue_ring", "statue_obelisk",
                 "statue_column", "statue_head", "canoe", "lily_small",
                 "lily_large", "bed_floor"):
        obj(name)

    # --- FX textures (the kit ships no particles; Kenney's particle pack does)
    for tail, alias in (("PNG (Transparent)/smoke_02.png", "fx_smoke.png"),
                        ("PNG (Transparent)/fire_01.png", "fx_fire.png"),
                        ("PNG (Transparent)/spark_04.png", "fx_spark.png"),
                        ("PNG (Transparent)/dirt_02.png", "fx_dust.png"),
                        ("PNG (Transparent)/star_04.png", "fx_star.png"),
                        ("PNG (Transparent)/magic_05.png", "fx_magic.png"),
                        ("PNG (Transparent)/circle_02.png", "fx_ring.png")):
        a.append(asset(PARTICLES, tail, alias))

    # --- HUD art + audio
    a.append(asset(TINY_UI, "20240707dragon9SlicesA.png", "hud_panel.png"))
    a.append(asset(TINY_UI, "20240707dragonHeaderA.png", "hud_header.png"))
    a.append(asset(TINY_UI, "20240707dragon9SlicesB.png", "hud_gem.png"))
    a.append(asset(UI_PACK, "PNG/Grey/Default/arrow_basic_e.png", "ui_arrow.png"))
    a.append(asset(UI_PACK, "PNG/Grey/Default/button_square_border.png", "ui_slot.png"))
    for tail, alias in (("Sounds/click-a.ogg", "sfx_ui.ogg"),
                        ("Sounds/switch-a.ogg", "sfx_pick.ogg"),
                        ("Sounds/tap-a.ogg", "sfx_step.ogg"),
                        ("Sounds/tap-b.ogg", "sfx_place.ogg"),
                        ("Sounds/switch-b.ogg", "sfx_fire.ogg"),
                        ("Sounds/click-b.ogg", "sfx_deny.ogg")):
        a.append(asset(UI_PACK, tail, alias))
    for tail, alias in (("Sounds/click-a.ogg", "sfx_win.ogg"),
                        ("Sounds/switch-b.ogg", "sfx_dawn.ogg"),
                        ("Sounds/tap-a.ogg", "sfx_night.ogg")):
        a.append(asset(UI_PACK, tail, alias))
    return a


# --------------------------------------------------------------------------- scene


def build_scene():
    sc = Scene(name="Main", gravity=(0.0, 0.0, 0.0), gravity3d=-9.81,
               ambient="#FF5D6460", fog=True, fog_color="#FFBCC6C0",
               fog_start=74.0, fog_end=250.0)

    # ---------------------------------------------------------------- camera + sun
    sc.add("Sun", rotX=-38.0, rotY=135.0, tag="Sky",
           comps=[light(kind="Directional", color="#FFFFF0D2", intensity=1.05, rng=400.0)])
    sc.add("Main Camera", y=13.0, z=15.0, tag="Camera",
           comps=[cam3d(follow=PLAYER, off=(0.0, 4.6, 9.4), fov=62.0, near=0.15,
                        far=320.0, smooth=7.0, sky_top="#FF4B8ED6",
                        sky_horizon="#FFCFDCD6", post="Vignette", fx=0.35)])

    # ---------------------------------------------------------------- terrain
    # One plane per material region.  A scaled flat colour plane is free: the kit
    # has no textures, so stretching a 1x1 ground tile over 120 units costs
    # nothing and saves a thousand draw calls.
    ground(sc, "ground_grass", 0.0, 0.0, 240.0, 240.0, GRASS, y=-0.02)
    # The road: a straight run north-south on the west bank, a fork to the camp.
    ground(sc, "ground_pathOpen", -22.0, 0.0, 5.2, 92.0, DIRT_DARK, y=0.01)
    ground(sc, "ground_pathOpen", -22.0, -30.0, 24.0, 5.2, DIRT_DARK, y=0.02)
    ground(sc, "ground_pathOpen", -22.0, 34.0, 20.0, 5.2, DIRT_DARK, y=0.03)
    # The river, north to south, with sand banks.
    ground(sc, "ground_riverOpen", BANK + BANK_W / 2, 0.0, BANK_W, 240.0, WATER, y=-0.28)
    ground(sc, "platform_beach", BANK - 1.6, 0.0, 4.0, 240.0, SAND, y=-0.16)
    ground(sc, "platform_beach", BANK + BANK_W + 1.6, 0.0, 4.0, 240.0, SAND, y=-0.16)

    # ---------------------------------------------------------------- valley walls
    # Rock cliffs ringing the play area: individual 1-unit pieces on the west and
    # east rims, stretched pieces on the far runs.  They double as the movement
    # boundary -- the ranger's script clamps to the same rectangle.
    # Two rows deep, 2-unit pieces, heights stepping 2..4.  The front row is
    # `cliff_stone` (a full cell), the back row `cliff_large_stone` (a slab) so
    # the ridge has a face and a bulk behind it.
    z, i = -HALF - 2.0, 0
    while z <= HALF + 2.0:
        for side, x in ((-1, -HALF - 1.2), (1, HALF + 1.2)):
            h = 2.0 + ((i + (side > 0)) % 3)
            sc.add(f"Wall_{'W' if side < 0 else 'E'}_{i}", x=x, y=h / 2.0, z=z,
                   sx=2.1, sy=h, sz=2.1, rotY=(i * 90) % 360, tag="Cliff",
                   comps=[mesh(kind="Custom Model", model="cliff_stone.obj",
                               color=STONE, specular=0.05, shininess=10.0)])
            sc.add(f"Ridge_{'W' if side < 0 else 'E'}_{i}", x=x + side * 2.0,
                   y=3.0 + (i % 2) * 0.6, z=z, sx=2.3, sy=6.0 + (i % 2) * 1.2, sz=2.3,
                   rotY=(i * 90) % 360, tag="Cliff",
                   comps=[mesh(kind="Custom Model", model="cliff_large_stone.obj",
                               color=STONE_DARK, specular=0.05, shininess=10.0)])
        z += 2.0
        i += 1
    x, i = -HALF - 2.0, 0
    while x <= HALF + 2.0:
        for side, zz in ((-1, -HALF - 1.2), (1, HALF + 1.2)):
            h = 2.0 + ((i + (side > 0)) % 3)
            sc.add(f"Wall_{'N' if side < 0 else 'S'}_{i}", x=x, y=h / 2.0, z=zz,
                   sx=2.1, sy=h, sz=2.1, tag="Cliff",
                   comps=[mesh(kind="Custom Model", model="cliff_stone.obj",
                               color=STONE, specular=0.05, shininess=10.0)])
            sc.add(f"Ridge_{'N' if side < 0 else 'S'}_{i}", x=x, y=3.0 + (i % 2) * 0.6,
                   z=zz + side * 2.0, sx=2.3, sy=6.0 + (i % 2) * 1.2, sz=2.3, tag="Cliff",
                   comps=[mesh(kind="Custom Model", model="cliff_large_stone.obj",
                               color=STONE_DARK, specular=0.05, shininess=10.0)])
        x += 2.0
        i += 1

    # ---------------------------------------------------------------- the camp
    # A terraced camp in the north-west: tents, a fire, a bed, a sign and a
    # paddock.  This is the safe island at night, and the valley's only light.
    CAMP_X, CAMP_Z = -36.0, -34.0
    ground(sc, "ground_grass", CAMP_X, CAMP_Z, 26.0, 22.0, GRASS, y=-0.01)
    prop(sc, "tent_detailedOpen", CAMP_X + 3.0, CAMP_Z - 3.0, 4.4, rot_y=200.0,
         color=RED, tag="Camp")
    prop(sc, "tent_smallClosed", CAMP_X - 3.2, CAMP_Z - 4.4, 3.6, rot_y=150.0,
         color=TAN, tag="Camp")
    prop(sc, "bed_floor", CAMP_X + 3.0, CAMP_Z - 4.6, 4.4, y=0.05, rot_y=200.0,
         color=WOOD, tag="Camp")
    prop(sc, "bed", CAMP_X + 3.0, CAMP_Z - 4.6, 4.0, y=0.32, rot_y=200.0,
         color=RED, tag="Camp")
    prop(sc, "fence_planks", CAMP_X - 8.0, CAMP_Z + 2.0, 3.6, tag="Camp")
    prop(sc, "fence_planks", CAMP_X + 8.0, CAMP_Z + 2.0, 3.6, tag="Camp")
    prop(sc, "sign", CAMP_X - 1.0, CAMP_Z + 6.5, 3.0, rot_y=-20.0, color=WOOD, tag="Camp")
    prop(sc, "pot_large", CAMP_X + 6.5, CAMP_Z - 1.0, 1.4, color=WOOD_BARK, tag="Camp")

    # The fire the camp is named for: a real point light plus embers and smoke.
    cfire = sc.add("CampFire", x=CAMP_X, y=0.0, z=CAMP_Z, tag="Fire",
                  comps=[light(kind="Point", color="#FFFFA24A", intensity=2.6, rng=26.0),
                         particles(color="#FFFFC067", end="#20FF5A12", size=0.34, end_size=0.05,
                                   rate=26.0, lifetime=1.1, speed=1.9, direction=90, spread=26,
                                   gravity=1.4, max_particles=60, additive=True,
                                   texture="fx_fire.png", emitting=True),
                         particles(color="#59FFFFFF", end="#00FFFFFF", size=0.55, end_size=1.5,
                                   rate=7.0, lifetime=2.8, speed=0.9, direction=90, spread=18,
                                   gravity=0.35, max_particles=26, additive=True,
                                   texture="fx_smoke.png", emitting=True),
                         script("Camp.js", "reach=3.4, sleepFrom=19.5, sleepTo=5.5")])
    prop(sc, "campfire_stones", CAMP_X, CAMP_Z, 2.1, y=0.06, tag="Camp")
    prop(sc, "campfire_logs", CAMP_X, CAMP_Z, 1.8, y=0.12, tag="Camp")
    prop(sc, "log", CAMP_X - 3.0, CAMP_Z + 1.2, 2.0, rot_y=18.0, tag="Camp")
    prop(sc, "stump_round", CAMP_X + 1.8, CAMP_Z + 2.4, 1.0, tag="Camp")
    prop(sc, "stump_round", CAMP_X + 3.4, CAMP_Z + 2.0, 1.0, tag="Camp")
    return sc, cfire


def build_farm(sc):
    # A worked farm on the east bank: dirt rows, wheat, corn, pumpkins and a
    # paddock, so the far side of the river is worth crossing the bridge for.
    FX, FZ = 34.0, -26.0
    ground(sc, "ground_grass", FX, FZ, 30.0, 26.0, GRASS, y=-0.01)
    prop(sc, "crops_dirtRow", FX, FZ, 20.0, y=0.05, rot_y=90.0, color=DIRT_DARK, tag="Farm")
    for i in range(5):
        prop(sc, "crops_wheatStageB", FX - 8.0 + i * 4.0, FZ - 6.0, 2.8, y=0.1, color=CORN,
             tag="Farm")
        prop(sc, "crops_cornStageD", FX - 8.0 + i * 4.0, FZ + 5.0, 2.8, y=0.1, color=CORN,
             tag="Farm")
    for i in range(4):
        prop(sc, "crop_pumpkin", FX - 6.0 + i * 4.0, FZ + 0.0, 1.1, y=0.2, color=YELLOW,
             tag="Farm")
    prop(sc, "crops_dirtSingle", FX, FZ, 22.0, y=0.04, rot_y=90.0, color=DIRT_DARK, tag="Farm")
    prop(sc, "fence_planks", FX - 11.0, FZ, 11.0, y=0.0, rot_y=90.0, tag="Farm")
    prop(sc, "fence_planks", FX + 11.0, FZ, 11.0, y=0.0, rot_y=90.0, tag="Farm")
    prop(sc, "fence_gate", FX, FZ + 13.0, 3.2, y=0.0, color=WOOD, tag="Farm")
    prop(sc, "pot_small", FX + 8.0, FZ - 9.0, 1.2, color=WOOD_BARK, tag="Farm")
    prop(sc, "log_stack", FX - 9.0, FZ - 9.0, 1.4, color=WOOD_INNER, tag="Farm")


def build_pond(sc):
    """A spring pond in the south-east with a dock and lilies.

    Water is a plane on top of the meadow: the valley floor is flat because the
    ranger's gravity is script-owned (there is no terrain collision), so anything
    that looks like a hole would be walkable -- a pond reads as water and is
    shallow enough to cross, which is honest about what the engine does here.
    """
    px, pz, r = 30.0, 30.0, 9.0
    ground(sc, "platform_beach", px, pz, 22.0, 22.0, SAND, y=-0.14)
    ground(sc, "ground_riverOpen", px, pz, 17.0, 17.0, WATER, y=-0.24)
    for i in range(14):
        ang = i * (360.0 / 14)
        prop(sc, "lily_large" if i % 2 else "lily_small",
             px + math.sin(math.radians(ang)) * (3.0 + (i % 3)),
             pz + math.cos(math.radians(ang)) * (3.0 + (i % 3)), 1.15, y=-0.22,
             rot_y=ang, color=LEAF_DARK, tag="River")
    prop(sc, "canoe", px + 3.0, pz + 5.6, 5.4, y=-0.18, rot_y=35.0, color=WOOD, tag="River")
    # A plank dock out over the water: the kit's fences laid flat make a boardwalk.
    for i in range(4):
        prop(sc, "fence_planks", px - 8.4, pz - 7.0 + i * 2.2, 2.4, y=0.06, rot_y=90.0,
             color=WOOD_INNER, tag="River")


def build_watchtower(sc):
    """A ranger watchtower on the west bank, built from fences and stone.

    The kit has no building models, so the tower is assembled from what it does
    have: four fence posts, stone plinths, a stair of crates and a plank roof.
    """
    tx, tz = -8.0, -26.0
    for i, (dx, dz) in enumerate(((-2.2, -2.2), (2.2, -2.2), (-2.2, 2.2), (2.2, 2.2))):
        prop(sc, "stone_largeA", tx + dx, tz + dz, 2.2, y=0.5, rot_y=i * 45.0,
             color=STONE_DARK, tag="Tower")
        prop(sc, "fence_planksDouble", tx + dx, tz + dz, 6.4, y=3.2,
             rot_y=i * 45.0, color=WOOD, tag="Tower")
    prop(sc, "platform_grass", tx, tz, 6.6, y=6.4, color=WOOD_INNER, tag="Tower")
    prop(sc, "fence_planks", tx, tz - 3.3, 6.4, y=6.9, color=WOOD_BARK, tag="Tower")
    prop(sc, "fence_planks", tx, tz + 3.3, 6.4, y=6.9, color=WOOD_BARK, tag="Tower")
    prop(sc, "bridge_center_wood", tx, tz, 5.6, y=6.7, rot_y=45.0, color=WOOD_BARK,
         tag="Tower")
    prop(sc, "sign", tx - 6.0, tz + 2.0, 3.0, rot_y=80.0, color=WOOD, tag="Tower")
    # The ladder: crates and a log, so the tower reads as climbable.
    prop(sc, "log_stack", tx - 4.2, tz + 0.6, 1.6, y=0.2, color=WOOD_INNER, tag="Tower")
    prop(sc, "log", tx - 3.0, tz + 1.0, 2.0, y=1.0, rot_y=70.0, tag="Tower")


def build_stone_circle(sc):
    # A stone circle on the south bank, with an obelisk at its centre: the
    # landmark the beacons are lit from.
    SX, SZ = -6.0, 33.0
    ground(sc, "ground_grass", SX, SZ, 24.0, 24.0, GRASS, y=-0.01)
    prop(sc, "statue_ring", SX, SZ, 6.2, y=0.35, color=STONE, tag="Circle")
    for i in range(10):
        ang = i * 36.0
        r = 4.2
        px = SX + math.sin(math.radians(ang)) * r
        pz = SZ + math.cos(math.radians(ang)) * r
        model = ("stone_tallA", "stone_tallD", "rock_tallA", "stone_tallA")[i % 4]
        prop(sc, model, px, pz, 1.9 + (i % 3) * 0.35, rot_y=ang, color=STONE_DARK, tag="Circle")
    prop(sc, "statue_obelisk", SX, SZ, 5.0, y=0.4, color=STONE, tag="Circle")
    prop(sc, "statue_column", SX + 3.4, SZ - 2.6, 3.2, rot_y=25.0, color=STONE, tag="Circle")
    prop(sc, "statue_head", SX - 3.2, SZ + 2.8, 2.4, rot_y=-40.0, color=STONE, tag="Circle")


def build_bridge(sc):
    # A stone bridge over the river, wide enough to walk a cart across.
    z = BRIDGE_Z
    for i in range(-2, 3):
        model = "bridge_stone" if i == 0 else "bridge_stoneNarrow"
        sc.add(f"Bridge_{i}", x=BANK + BANK_W / 2, y=0.24, z=z + i * 2.0,
               sx=3.4, sy=0.85, sz=2.2, tag="Bridge",
               comps=[mesh(kind="Custom Model", model=model + ".obj", color=STONE,
                           specular=0.05, shininess=14.0)])
    prop(sc, "path_stone", BANK - 1.2, z, 3.0, y=0.09, color=STONE_DARK, tag="Bridge")
    prop(sc, "path_stone", BANK + BANK_W + 1.2, z, 3.0, y=0.09, color=STONE_DARK, tag="Bridge")


def build_beacons(sc):
    """Three beacon rings on the ridges, each with a brazier the ranger can light."""
    spots = ((42.0, 40.0), (-44.0, 8.0), (10.0, -44.0))
    for i, (bx, bz) in enumerate(spots, start=1):
        sc.add(f"Beacon_{i}", x=bx, y=0.0, z=bz, tag="Beacon",
               comps=[script("Beacon.js", f"index={i}, reach=3.6, cost=3"),
                      particles(color="#00FFD27A", end="#00FF5A12", size=0.42, end_size=0.08,
                                rate=0.0, lifetime=1.3, speed=2.2, direction=90, spread=24,
                                gravity=1.2, max_particles=70, additive=True,
                                texture="fx_fire.png", emitting=False)])
        # The brazier itself: a ring of dressed stone, a stack of logs inside.
        prop(sc, "statue_ring", bx, bz, 4.2, y=0.3, color=STONE, tag="Beacon")
        prop(sc, "log_stack", bx, bz, 1.9, y=0.3, color=WOOD_INNER, tag="Beacon")
        prop(sc, "stone_tallA", bx + 2.6, bz + 1.1, 2.0, rot_y=30.0, color=STONE_DARK,
             tag="Beacon")
        prop(sc, "stone_tallD", bx - 2.4, bz - 1.5, 1.9, rot_y=-25.0, color=STONE_DARK,
             tag="Beacon")


# ------------------------------------------------------------------ scatter passes


def scatter_valley(sc):
    """Bushes, tufts, mushrooms, logs and rocks: the valley's texture."""
    blocked = [(-36.0, -34.0), (34.0, -26.0), (-18.0, 30.0), (0.0, 0.0),
               (42.0, 40.0), (-44.0, 8.0), (10.0, -44.0)]
    for i, (x, z) in enumerate(scatter(7, 150, -HALF + 2, HALF - 2, min_dist=3.8,
                                       avoid=blocked)):
        model = ("plant_bush", "plant_bushDetailed", "plant_bushLarge",
                 "plant_bushSmall", "plant_flatTall", "grass_large", "grass_leafsLarge")[i % 7]
        size = 0.85 + (i % 5) * 0.22
        if BANK - 2.5 < x < BANK + BANK_W + 2.5:
            continue
        prop(sc, model, x, z, size, rot_y=(i * 37) % 360,
             color=(LEAF, LEAF_DARK, LEAF)[i % 3], tag="Foliage")
    for i, (x, z) in enumerate(scatter(11, 100, -HALF + 2, HALF - 2, min_dist=2.4)):
        if BANK - 2.5 < x < BANK + BANK_W + 2.5:
            continue
        model = ("grass", "grass_leafs", "plant_flatShort", "plant_flatTall")[i % 4]
        prop(sc, model, x, z, 0.55 + (i % 3) * 0.18, rot_y=(i * 53) % 360, color=LEAF,
             tag="Foliage")
    for i, (x, z) in enumerate(scatter(13, 46, -HALF + 3, HALF - 3, min_dist=7.0,
                                       avoid=blocked)):
        if BANK - 2.5 < x < BANK + BANK_W + 2.5:
            continue
        model = ("mushroom_redGroup", "mushroom_tan", "flower_redA", "flower_yellowA",
                 "flower_purpleA")[i % 5]
        tint = {"mushroom_redGroup": RED, "mushroom_tan": TAN, "flower_redA": RED,
                "flower_yellowA": YELLOW, "flower_purpleA": PURPLE}[model]
        prop(sc, model, x, z, 0.6 + (i % 3) * 0.16, rot_y=(i * 71) % 360, color=tint,
             tag="Flora")
    for i, (x, z) in enumerate(scatter(17, 54, -HALF + 3, HALF - 3, min_dist=5.0,
                                       avoid=blocked)):
        model = ("rock_smallA", "rock_smallC", "rock_smallE", "rock_smallG",
                 "stone_smallA", "stone_smallD")[i % 6]
        size = 0.7 + (i % 4) * 0.3
        if BANK - 2.5 < x < BANK + BANK_W + 2.5:
            continue
        prop(sc, model, x, z, size, rot_y=(i * 29) % 360, color=STONE if "stone" in model else DIRT,
             tag="Rock")
    for i, (x, z) in enumerate(scatter(19, 16, -HALF + 4, HALF - 4, min_dist=9.0,
                                       avoid=blocked)):
        model = ("log", "stump_round", "stump_oldTall", "log_large")[i % 4]
        if BANK - 2.5 < x < BANK + BANK_W + 2.5:
            continue
        prop(sc, model, x, z, 1.1 + (i % 3) * 0.3, rot_y=(i * 41) % 360,
             color=WOOD_BARK if "stump" in model else WOOD_INNER, tag="Timber")
    # Dry south-west corner: the kit's cacti, tinted to the valley's palette.
    for i in range(6):
        prop(sc, "cactus_short" if i % 2 else "cactus_tall",
             -46.0 + (i % 3) * 3.0, 42.0 + (i // 3) * 3.4, 1.8, rot_y=(i * 33) % 360,
             color=LEAF_DARK, tag="Flora")
    # Riverside dressing: lilies on the water, reeds on the bank, a canoe.
    for i in range(14):
        z = -46.0 + i * 7.0
        x = BANK + 1.2 + (i % 3) * 2.6
        prop(sc, "lily_large" if i % 2 else "lily_small", x, z, 1.2, y=-0.24,
             rot_y=(i * 47) % 360, color=LEAF_DARK, tag="River")
    for i in range(12):
        z = -44.0 + i * 8.0
        prop(sc, "plant_flatTall", BANK - 1.0, z, 1.0, rot_y=(i * 23) % 360, color=LEAF_DARK,
             tag="River")
    prop(sc, "canoe", BANK + 6.6, 18.0, 6.0, y=-0.2, rot_y=88.0, color=WOOD, tag="River")
    prop(sc, "canoe", BANK + 2.6, -22.0, 6.0, y=-0.2, rot_y=92.0, color=WOOD, tag="River")
    # A crossing of stepping stones where the road meets the water.
    for i in range(4):
        prop(sc, "path_stone", BANK + 1.4 + i * 2.1, 18.0, 1.3, y=0.06, color=STONE_DARK,
             tag="River")


def scatter_forest(sc):
    """The forest belt along the west and north rims.

    Canopied trees render with a green trunk (one colour per object -- see
    nature.py), so they are placed as a back-lit silhouette belt at the valley
    edge and the near ground is left to shrubs, logs and standing stones which
    read correctly in a single colour.
    """
    belt = []
    for i in range(58):
        t = i / 58.0
        ang = 118.0 + t * 168.0
        r = HALF - 3.0 + ((i % 3) - 1) * 1.6
        x = math.sin(math.radians(ang)) * r
        z = math.cos(math.radians(ang)) * r
        belt.append((x, z))
    for i, (x, z) in enumerate(belt):
        model = ("tree_pineTallB", "tree_default", "tree_oak", "tree_pineRoundC",
                 "tree_thin", "tree_default_fall")[i % 6]
        size = 5.6 + (i % 4) * 0.9
        color = {"tree_default_fall": LEAF_FALL, "tree_pineTallB": LEAF_DARK,
                 "tree_pineRoundC": LEAF_DARK}.get(model, LEAF)
        prop(sc, model, x, z, size, rot_y=(i * 61) % 360, color=color, tag="Tree")


# --------------------------------------------------------------------------- scripts


RANGER_JS = r"""
// Emberfall Valley -- Ranger.js
//
// The player character: a primitive-built body, third-person movement relative
// to the camera, a stamina-limited sprint, a hop, and one context action.
//
// The engine has a 3D physics solver (PhysicsWorld3D), but this game does not
// use it for the ranger: the terrain is a flat plane with a known boundary, so
// the script owns gravity and clamping.  That keeps the game identical in the
// engine and in the offline harness -- which is where it is tested -- instead of
// depending on a solver the harness does not run.
//
// Params: speed=7.2, sprint=11.4, turn=14.0, hop=5.2, gravity=-19.5,
//         staminaMax=100, staminaDrain=17, staminaRegen=13, reach=3.2

var yaw = 180.0;            // facing, degrees; 180 = looking down -Z
var vy = 0.0;               // vertical speed, owned by this script
var y = 0.0;
var grounded = true;
var stamina = 100.0;
var sprinting = false;
var bob = 0.0;
var stepTimer = 0.0;
var carrying = { wood: 0, stone: 0, berry: 0 };
var near = null;            // the interactable the ranger is standing next to
var prompt = "";
var hops = 0, picked = 0, steps = 0;

function start() {
    yaw = self.rotY;
    y = self.y;
}

function update(dt) {
    var v = scene.find("Valley");
    var frozen = v ? v.send("isFrozen") : false;

    if (stamina < staminaMax) {
        var regen = staminaRegen * (sprinting ? 0.0 : 1.0);
        if (!sprinting) stamina = Math.min(staminaMax, stamina + regen * dt);
    }

    var ix = frozen ? 0.0 : input.axisX;
    var iz = frozen ? 0.0 : input.axisY;
    var mag = Math.sqrt(ix * ix + iz * iz);
    if (mag > 1.0) { ix /= mag; iz /= mag; mag = 1.0; }

    if (mag > 0.05) {
        // Move in the *world* frame the camera looks along: the camera trails the
        // ranger at +Z, so screen-up is -Z.
        var wantYaw = Math.atan2(ix, -iz) * 180.0 / Math.PI;
        yaw = turnToward(yaw, wantYaw, turn * dt);
        sprinting = stamina > 8.0 && mag > 0.75 && actionHeld("Sprint");
        var sp = sprinting ? sprint : speed;
        var dx = Math.sin(yaw * Math.PI / 180.0);
        var dz = -Math.cos(yaw * Math.PI / 180.0);
        self.setPosition(self.x + dx * sp * mag * dt, y + bob, self.z + dz * sp * mag * dt);
        if (sprinting) stamina = Math.max(0.0, stamina - staminaDrain * dt);
        stepTimer -= dt * (sprinting ? 1.6 : 1.0);
        if (stepTimer <= 0.0 && grounded) {
            stepTimer = 0.34;
            steps++;
            audio.play("sfx_step.ogg", 0.35);
            var d = scene.find("Fx");
            if (d) d.send("dust", self.x, self.z);
        }
    } else {
        sprinting = false;
    }

    // Hop: a script-owned arc, landed when it comes back down.
    if (grounded && actionPressed("Jump") && !frozen) {
        vy = hop;
        grounded = false;
        hops++;
    }
    if (!grounded) {
        vy += gravity * dt;
        y += vy * dt;
        if (y <= 0.0) { y = 0.0; vy = 0.0; grounded = true;
            var fx = scene.find("Fx");
            if (fx) fx.send("landing", self.x, self.z);
        }
    }

    // Stay inside the valley.  The cliff ring is decoration; this is what
    // actually stops you walking into it -- and the limit has to leave room for
    // the camera, which trails 9.4 units behind with no collision of its own.
    var lim = 39.0;
    if (self.x > lim) self.setPosition(lim, self.y, self.z);
    if (self.x < -lim) self.setPosition(-lim, self.y, self.z);
    if (self.z > lim) self.setPosition(self.x, self.y, lim);
    if (self.z < -lim) self.setPosition(self.x, self.y, -lim);

    self.rotY = yaw;
    bob = grounded && mag > 0.05 ? Math.sin(time.time * 12.0) * 0.06 : 0.0;
    self.y = y + bob;

    scanForInteractable();
    if (prompt !== "" && actionPressed("Interact") && near) {
        var ok = near.send("interact", 1);
        if (ok) picked++;
    }
}

function actionHeld(name) { return input.action(name); }
function actionPressed(name) { return input.actionPressed(name); }

/** Shortest-arc turn, so the ranger never spins the long way round. */
function turnToward(from, to, maxStep) {
    var d = ((to - from + 540.0) % 360.0) - 180.0;
    if (Math.abs(d) <= maxStep) return to;
    return from + (d > 0 ? maxStep : -maxStep);
}


/** Ground distance between two objects: height must not shrink a walk. */
function groundDist(a, b) {
    var dx = a.x - b.x, dz = a.z - b.z;
    return Math.sqrt(dx * dx + dz * dz);
}

/** The nearest thing that answers "interact", within reach. */
function scanForInteractable() {
    near = null;
    prompt = "";
    var best = 1e9;
    var groups = [["Forage", reach + 0.6], ["Beacon", reach], ["Camp", reach + 0.4]];
    for (var g = 0; g < groups.length; g++) {
        var list = scene.findAll(groups[g][0]);
        for (var i = 0; i < list.length; i++) {
            var o = list[i];
            // Ground distance: the valley is 3D, so a node up a slope must not be
            // "in reach" because a 2D check measured x against y.
            var d = groundDist(self, o);
            if (d > groups[g][1] || d >= best) continue;
            if (!o.send("canInteract", null)) continue;
            best = d;
            near = o;
            prompt = o.send("prompt", null) || "";
        }
    }
    var v = scene.find("Valley");
    if (v) v.send("setPrompt", prompt);
}

/** Cold: called by the director when night catches the ranger away from fire. */
function chill(amount) {
    stamina = Math.max(0.0, stamina - amount);
    self.color = "#FFB0C4E8";
    after(0.3, function () { self.color = "#FFFFFFFF"; });
    if (stamina <= 0.0) {
        var v = scene.find("Valley");
        if (v) v.send("gameOver");
    }
}

function addResource(kind, amount) {
    carrying[kind] = (carrying[kind] || 0) + amount;
}
function takeResource(kind, amount) {
    if ((carrying[kind] || 0) < amount) return false;
    carrying[kind] -= amount;
    return true;
}
function getStamina() { return stamina; }
function getStaminaMax() { return staminaMax; }
function getCarrying() { return carrying; }
function getEmitted() { return vy; }
function getGroundY() { return y; }
function isGrounded() { return grounded; }
function getSteps() { return steps; }
"""

VALLEY_JS = r"""
// Emberfall Valley -- Valley.js
//
// The director: time of day, weather, the resource economy the HUD reads, the
// objective chain, and the win/lose bookkeeping.
//
// The sun is a real Directional Light whose rotation and colour this script
// drives, and the sky is the Camera3D's two gradient colours -- both are things
// Renderer3D reads every frame, so the valley really does get darker.
//
// Params: dayLength=180, dusk=19.5, dawn=5.5, coldFrom=20.0, coldTo=5.0,
//         beacons=3, targetBerry=6

var clock = 7.25;              // hours, 0..24
var frozen = false;
var won = false;
var lost = false;
var prompt = "";
var beaconsLit = 0;
var berryFound = 0;
var woodFound = 0;
var phase = "morning";
var cold = 0.0;
var toast = "";
var toastT = 0.0;
var weatherT = 34.0;
var weather = "clear";
var snow = 0.0;

function start() {
    clock = 7.25;
    setPhase("morning");
    say("Emberfall Valley -- gather wood, light the three beacons before nightfall.");
}

function update(dt) {
    clock += (24.0 / dayLength) * dt;
    if (clock >= 24.0) clock -= 24.0;

    updateSun();
    updateWeather(dt);

    var w = scene.find("Ranger");
    if (w) {
        var night = clock < coldFrom && clock > coldTo;
        // dusk and dawn bound the cold window above; they also name the phase
        // the HUD prints, so one clock drives both the light and the label.
        if (clock < dawn) setPhase("night");
        else if (clock < 11.0) setPhase("morning");
        else if (clock < dusk) setPhase("afternoon");
        else setPhase("night");
        // Cold drains stamina away from fire; the camp and lit beacons warm you.
        if (night && !nearFire(w)) {
            cold += dt;
            if (cold > 6.0) { cold = 0.0; w.send("chill", 6); }
        } else {
            cold = 0.0;
        }
    }

    if (toastT > 0.0) {
        toastT -= dt;
        if (toastT <= 0.0) toast = "";
    }

    checkWin();
}

/** Sun colour and angle by hour: a real day/night cycle, not a filter. */
function updateSun() {
    var sun = scene.find("Sun");
    var cam = scene.find("Main Camera");
    var t = clock;
    // Dawn at 5.5, noon 12.5, dusk 19.5, midnight 0.
    var daylight = 1.0;
    if (t < 5.5) daylight = 0.06;
    else if (t < 7.5) daylight = 0.06 + (t - 5.5) / 2.0 * 0.94;
    else if (t < 17.0) daylight = 1.0;
    else if (t < 20.0) daylight = 1.0 - (t - 17.0) / 3.0 * 0.94;
    else daylight = 0.06;

    if (sun) {
        var pitch = -12.0 - 46.0 * daylight;
        sun.rotX = pitch;
        sun.rotY = 90.0 + (t / 24.0) * 360.0;
        var warm = 1.0 - Math.abs(t - 12.5) / 8.0;
        sun.lightIntensity = 0.18 + 1.05 * daylight;
        sun.lightColor = lerpColor("#FF3A4C86", lerpColor("#FFFFC178", "#FFFFF3D6", warm),
                                   Math.min(1.0, daylight * 1.15));
    }
    if (cam) {
        cam.skyTop = lerpColor("#FF0A1030", "#FF4B8ED6", daylight);
        cam.skyHorizon = lerpColor("#FF2A3A56", "#FFDCE9E4", daylight);
    }
}

function updateWeather(dt) {
    weatherT -= dt;
    if (weatherT <= 0.0) {
        weatherT = 26.0 + random(0, 22);
        weather = weather === "clear" ? (random(0, 1) > 0.45 ? "mist" : "rain") : "clear";
        if (weather === "mist") say("A cold mist rolls down off the ridge.");
        else if (weather === "rain") say("Rain sweeps across the valley.");
    }
    var want = weather === "mist" ? 1.0 : 0.0;
    snow += (want - snow) * Math.min(1.0, dt * 0.6);
    var fx = scene.find("Weather");
    if (fx) fx.setEmitting(snow > 0.35);
}

/** Ground distance between two objects: height must not shrink a walk. */
function groundDist(a, b) {
    var dx = a.x - b.x, dz = a.z - b.z;
    return Math.sqrt(dx * dx + dz * dz);
}

function nearFire(w) {
    // "Near the fire" is about standing beside it, not about the height of the
    // flames: measure on the ground.
    var fires = scene.findAll("Fire");
    for (var i = 0; i < fires.length; i++) {
        if (groundDist(w, fires[i]) < 11.0) return true;
    }
    return false;
}

function onBeaconLit(index) {
    beaconsLit++;
    audio.play("sfx_fire.ogg");
    say("Beacon " + index + " burns. " + beaconsLit + " of " + beacons + " lit.");
}

function onBerry() { berryFound++; }
function getTargetBerry() { return targetBerry; }
function onWood() { woodFound++; }

function checkWin() {
    if (won) return;
    if (beaconsLit >= beacons) {
        won = true;
        audio.play("sfx_win.ogg");
        say("All three beacons burn. The valley holds the light.");
    }
}

function gameOver() {
    if (won) return;
    lost = true;
    frozen = true;
    audio.play("sfx_deny.ogg");
    say("The cold takes the valley. Press A to begin again.");
}

function say(msg) {
    toast = msg;
    toastT = 5.0;
}

function setClock(hours) { clock = hours; }
function setPhase(p) { phase = p; }
function setPrompt(p) { prompt = p; }
function isFrozen() { return frozen || won; }
function hasWon() { return won; }
function hasLost() { return lost; }
function getClock() { return clock; }
function getPhase() { return phase; }
function getPrompt() { return prompt; }
function getToast() { return toast; }
function getWeather() { return weather; }
function getBeaconsLit() { return beaconsLit; }
function getBeaconGoal() { return beacons; }
function getBerryFound() { return berryFound; }
function getTargetBerry() { return targetBerry; }
function restart() { self.reload(); }

/** "#AARRGGBB" blend, so the sun and sky can be interpolated by daylight. */
function lerpColor(aHex, bHex, t) {
    if (t < 0.0) t = 0.0;
    if (t > 1.0) t = 1.0;
    var a = hexParts(aHex), b = hexParts(bHex);
    var r = Math.round(a[1] + (b[1] - a[1]) * t);
    var g = Math.round(a[2] + (b[2] - a[2]) * t);
    var bl = Math.round(a[3] + (b[3] - a[3]) * t);
    return "#FF" + hex2(r) + hex2(g) + hex2(bl);
}
function hexParts(h) {
    return [0, parseInt(h.substr(3, 2), 16), parseInt(h.substr(5, 2), 16), parseInt(h.substr(7, 2), 16)];
}
function hex2(v) {
    var s = v.toString(16).toUpperCase();
    return s.length < 2 ? "0" + s : s;
}
"""

FORAGE_JS = r"""
// Emberfall Valley -- Forage.js
//
// A gatherable: a berry bush, a stone cairn, a fallen log.  Holding the action
// near it yields a resource, then the node goes bare and comes back after
// `respawn`.  The node's own state is what the HUD and the ranger read, so a
// bare node simply refuses to answer.
//
// Params: kind=berry, amount=2, respawn=42, reach=3.2

var ready = true;
var timer = 0.0;
var glow = null;

function start() {
    glow = null;
}

function update(dt) {
    if (!ready) {
        timer -= dt;
        if (timer <= 0.0) {
            ready = true;
            if (glow) glow.burst(8);
        }
    }
}

function canInteract() { return ready; }
function prompt() {
    if (!ready) return "";
    return "Gather " + kind + "  (" + reach + "m)";
}

function interact() {
    if (!ready) return false;
    ready = false;
    timer = respawn;
    var w = scene.find("Ranger");
    if (!w) return false;
    w.send("addResource", kind, amount);
    var v = scene.find("Valley");
    if (v) {
        v.send(kind === "berry" ? "onBerry" : "onWood");
        v.send("say", "Gathered " + amount + " " + kind + ".");
    }
    audio.play("sfx_pick.ogg", 0.7);
    self.color = "#FF8A7F6A";
    after(0.25, function () { self.color = "#FFFFFFFF"; });
    var fx = scene.find("Fx");
    if (fx) fx.send("pickup", self.x, self.y + 0.6, self.z);
    return true;
}
"""

BEACON_JS = r"""
// Emberfall Valley -- Beacon.js
//
// One of the three ridges braziers.  Lighting it costs wood and turns on the
// brazier's own particle emitter plus a point light, and it then counts as a
// warming fire for the night.  The light is created on demand: three point
// lights exist at the start of the game only if they are needed, and
// Renderer3D keeps the four nearest anyway.
//
// Params: index=1, reach=3.6, cost=3

var lit = false;
var fire = null;

function start() {
    fire = null;
}

function canInteract() { return !lit; }
function prompt() {
    if (lit) return "";
    return "Light beacon " + index + "  (" + cost + " wood, " + reach + "m)";
}

function interact() {
    if (lit) return false;
    var w = scene.find("Ranger");
    if (!w) return false;
    if (!w.send("takeResource", "wood", cost)) {
        var v0 = scene.find("Valley");
        if (v0) v0.send("say", "Not enough wood -- bring " + cost + " from the valley.");
        audio.play("sfx_deny.ogg", 0.6);
        return false;
    }
    lit = true;
    self.setEmitting(true);
    self.send("igniteLight");
    audio.play("sfx_fire.ogg");
    var v = scene.find("Valley");
    if (v) v.send("onBeaconLit", index);
    return true;
}

/** The light is a child object so it can flicker without moving the brazier. */
function igniteLight() {
    if (fire) { fire.lightIntensity = 3.0; return; }
    fire = scene.spawn("BeaconFire", self.x, self.y + 1.2, self.z);
    if (fire) fire.name = "BeaconFire_" + index;
}

function update(dt) {
    if (fire && lit) {
        fire.lightIntensity = 2.6 + Math.sin(time.time * 7.3) * 0.5;
    }
}
"""

CAMP_JS = r"""
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
"""

WILDLIFE_JS = r"""
// Emberfall Valley -- Wildlife.js
//
// Herd animals assembled from primitives: graze, wander, and bolt if the ranger
// gets close.  Three states, no combat -- this game is about surviving the
// valley, not clearing it.
//
// Params: kind=deer, speed=2.1, fleeSpeed=7.6, sight=9.0, homeRadius=16.0

var state = "graze";
var timer = 2.0;
var homeX = 0.0, homeZ = 0.0;
var goalX = 0.0, goalZ = 0.0;
var bobT = 0.0;

function start() {
    homeX = self.x;
    homeZ = self.z;
    goalX = self.x;
    goalZ = self.z;
    timer = random(1.0, 4.0);
}

/** Ground distance between two objects: height must not shrink a walk. */
function groundDist(a, b) {
    var dx = a.x - b.x, dz = a.z - b.z;
    return Math.sqrt(dx * dx + dz * dz);
}

function update(dt) {
    timer -= dt;
    bobT += dt;
    var w = scene.find("Ranger");
    var d = w ? groundDist(self, w) : 999.0;

    if (d < sight) state = "flee";
    else if (state === "flee" && d > sight * 1.6) { state = "graze"; timer = 1.5; }

    if (state === "graze") {
        if (timer <= 0.0) {
            timer = random(2.5, 7.0);
            var a = random(0.0, 360.0);
            var r = random(1.5, homeRadius * 0.5);
            goalX = homeX + Math.sin(a * Math.PI / 180.0) * r;
            goalZ = homeZ + Math.cos(a * Math.PI / 180.0) * r;
        }
        self.y = (kind === "deer" ? 1.15 : 0.78) + Math.sin(bobT * 1.6) * 0.03;
        stepToward(goalX, goalZ, speed * 0.6, dt);
    } else if (state === "flee" && w) {
        var dx = self.x - w.x, dz = self.z - w.z;
        var dist = Math.sqrt(dx * dx + dz * dz) || 1.0;
        goalX = self.x + (dx / dist) * 12.0;
        goalZ = self.z + (dz / dist) * 12.0;
        stepToward(goalX, goalZ, fleeSpeed, dt);
        self.y = (kind === "deer" ? 1.15 : 0.78) + Math.abs(Math.sin(bobT * 9.0)) * 0.12;
    }

    // Keep the herd inside the valley and out of the river.
    if (self.z > 47.0) self.z = 47.0;
    if (self.z < -47.0) self.z = -47.0;
}

function stepToward(x, z, speed, dt) {
    var dx = x - self.x, dz = z - self.z;
    var d = Math.sqrt(dx * dx + dz * dz);
    if (d < 0.35) return;
    var step = Math.min(speed * dt, d);
    self.setPosition(self.x + (dx / d) * step, self.y, self.z + (dz / d) * step);
    self.rotY = Math.atan2(dx, dz) * 180.0 / Math.PI;
}

function getState() { return state; }
"""

HUD_JS = r"""
// Emberfall Valley -- Hud.js
//
// Screen-space HUD: the objective, the clock, the resource counts, a stamina bar
// and the interaction prompt.  Every label is a TextRenderer in the UI view, so
// it scales with the screen the way the engine's 2D UI does.
//
// Params: barWidth=3.0

var objective = "";
var clockLine = "";
var bagLine = "";
var staminaFrac = 1.0;
var promptLine = "";
var toastLine = "";
var bar = null;

function start() {
    bar = scene.find("StaminaBar");
    set("HudObjective", objective);
    set("HudClock", clockLine);
    set("HudBag", bagLine);
    set("HudPrompt", promptLine);
    set("HudToast", toastLine);
}

function update(dt) {
    var v = scene.find("Valley");
    var w = scene.find("Ranger");
    if (!v || !w) return;

    var lit = v.send("getBeaconsLit");
    var goal = v.send("getBeaconGoal");
    var clock = v.send("getClock");
    var phase = v.send("getPhase");
    var carry = w.send("getCarrying");
    var stam = w.send("getStamina");
    var stamMax = w.send("getStaminaMax");

    objective = "B E A C O N S   " + Math.floor(lit) + " / " + Math.floor(goal);
    var hh = Math.floor(clock);
    var mm = Math.floor((clock - hh) * 60.0);
    clockLine = phase.toUpperCase() + "  " + pad(hh) + ":" + pad(mm) +
                "   " + v.send("getWeather");
    bagLine = "wood " + (carry.wood || 0) + "   stone " + (carry.stone || 0) +
              "   berry " + (carry.berry || 0);
    staminaFrac = stamMax > 0 ? stam / stamMax : 0.0;
    promptLine = v.send("getPrompt");
    toastLine = v.send("getToast");

    var lost = v.send("hasLost");
    var won = v.send("hasWon");
    if (won) objective = "THE VALLEY HOLDS THE LIGHT";
    else if (lost) objective = "YOU FROZE IN THE DARK";

    set("HudObjective", objective);
    set("HudClock", clockLine);
    set("HudBag", bagLine);
    set("HudPrompt", promptLine);
    set("HudToast", toastLine);
    if (bar) {
        bar.scaleX = Math.max(0.001, barWidth * staminaFrac);
    }
}

function pad(n) { return n < 10 ? "0" + n : "" + n; }
function set(name, value) {
    var o = scene.find(name);
    if (o) o.text = value;
}
"""

FX_JS = r"""
// Emberfall Valley -- Fx.js
//
// The pooled one-shot effects: footfall dust, a landing puff, a gather sparkle,
// and the mist that drifts down the valley when the weather turns.  Effects are
// spawned from an inactive template and destroy themselves, which is the
// engine's idiomatic way to pool -- one definition, N clones, no factory code
// per effect.
//
// Params: dustLife=0.7, sparkLife=0.6

function start() {
    var w = scene.find("Weather");
    if (w) w.setEmitting(false);
}

function update(dt) {
}

function dust(x, z) {
    var go = scene.spawn("DustFx", x, 0.15, z);
    if (go) after(dustLife, function () { go.destroy(); });
}

function landing(x, z) {
    var go = scene.spawn("DustFx", x, 0.1, z);
    if (go) {
        go.burst(10);
        after(dustLife * 1.4, function () { go.destroy(); });
    }
}

function pickup(x, y, z) {
    var go = scene.spawn("SparkFx", x, y, z);
    if (go) {
        go.burst(14);
        after(sparkLife, function () { go.destroy(); });
    }
}
"""


def build_scripts():
    return {
        "Ranger.js": RANGER_JS,
        "Valley.js": VALLEY_JS,
        "Forage.js": FORAGE_JS,
        "Beacon.js": BEACON_JS,
        "Camp.js": CAMP_JS,
        "Wildlife.js": WILDLIFE_JS,
        "Hud.js": HUD_JS,
        "Fx.js": FX_JS,
    }


# --------------------------------------------------------------------------- assembly


def build():
    sc, cfire = build_scene()
    build_farm(sc)
    build_stone_circle(sc)
    build_pond(sc)
    build_watchtower(sc)
    build_bridge(sc)
    build_beacons(sc)
    scatter_valley(sc)
    scatter_forest(sc)

    # ---------------------------------------------------------------- the ranger
    root = sc.add(PLAYER, x=-21.0, y=0.0, z=26.0, rotY=0.0, tag="Player",
                  comps=[script("Ranger.js",
                                "speed=7.2, sprint=11.4, turn=190.0, hop=5.4, "
                                "gravity=-19.5, staminaMax=100, staminaDrain=17, "
                                "staminaRegen=13, reach=3.2")])
    # A body of primitives: boots, cloak, belt, hood, pack -- and a lantern that
    # is also a point light, so the ranger carries a little of the camp with them.
    body_part(sc, "RangerBootL", root, "Cube", -0.19, 0.16, 0.0, 0.24, 0.32, 0.36, WOOD_BARK)
    body_part(sc, "RangerBootR", root, "Cube", 0.19, 0.16, 0.0, 0.24, 0.32, 0.36, WOOD_BARK)
    body_part(sc, "RangerLegs", root, "Cube", 0.0, 0.55, 0.0, 0.46, 0.6, 0.4, WOOD)
    body_part(sc, "RangerBody", root, "Capsule", 0.0, 1.18, 0.0, 0.7, 0.85, 0.62, RED)
    body_part(sc, "RangerBelt", root, "Cube", 0.0, 0.86, 0.0, 0.72, 0.14, 0.64, WOOD_INNER)
    body_part(sc, "RangerPack", root, "Cube", 0.0, 1.32, -0.38, 0.6, 0.66, 0.3, WOOD_BARK)
    body_part(sc, "RangerArmL", root, "Capsule", -0.44, 1.22, 0.04, 0.17, 0.66, 0.17, RED,
              rot_x=14.0)
    body_part(sc, "RangerArmR", root, "Capsule", 0.44, 1.22, 0.04, 0.17, 0.66, 0.17, RED,
              rot_x=14.0)
    body_part(sc, "RangerHead", root, "Sphere", 0.0, 1.82, 0.0, 0.5, 0.5, 0.5, TAN)
    body_part(sc, "RangerHood", root, "Cylinder", 0.0, 1.9, 0.02, 0.62, 0.42, 0.62, RED)
    sc.add("Lantern", x=0.56, y=0.98, z=0.2, parent=root, tag="Player",
           comps=[mesh(kind="Cylinder", color="#FFE8C171", emission=0.55, specular=0.1,
                       shininess=12.0),
                  light(kind="Point", color="#FFFFB45E", intensity=1.4, rng=13.0)])

    # ---------------------------------------------------------------- nodes + herds
    for i, (x, z) in enumerate(scatter(23, 26, -44.0, 44.0, min_dist=8.0,
                                       avoid=[(-36.0, -34.0), (34.0, -26.0)])):
        if BANK - 2.5 < x < BANK + BANK_W + 2.5:
            continue
        kind = ("berry", "berry", "stone", "wood")[i % 4]
        model = {"berry": "plant_bushLarge", "stone": "rock_smallC",
                 "wood": "stump_round"}[kind]
        # Berry bushes keep the bush's own green with red only on the berries'
        # glow, rather than a whole bush painted red.
        color = {"berry": LEAF, "stone": STONE_DARK, "wood": WOOD_BARK}[kind]
        prop(sc, model, x, z, 1.3 if kind != "wood" else 1.1, rot_y=(i * 67) % 360,
             color=color, tag="Forage",
             extra=[particles(color="#66FF9A8E" if kind == "berry" else "#66A8E8FF",
                              end="#00FFE9A0", size=0.16, end_size=0.02,
                              rate=4.0, lifetime=1.6, speed=0.25, direction=90, spread=180,
                              gravity=0.1, max_particles=12, additive=True,
                              texture="fx_star.png", emitting=True),
                    script("Forage.js",
                           f'kind={kind}, amount={2 if kind != "wood" else 2}, '
                           f'respawn={38 + i}, reach=3.2')])

    herds = ((30.0, 22.0, "deer", 5), (-14.0, -12.0, "boar", 4), (18.0, -30.0, "deer", 4))
    for hx, hz, kind, count in herds:
        for i in range(count):
            x = hx + ((i % 3) - 1) * 3.4
            z = hz + (i // 3) * 3.2
            unit = sc.add(f"{kind}_{int(x)}_{int(z)}", x=x, y=1.15 if kind == "deer" else 0.78,
                          z=z, rotY=(i * 53) % 360, tag="Wildlife",
                          comps=[script("Wildlife.js",
                                        f"kind={kind}, speed={2.1 if kind == 'deer' else 1.6}, "
                                        f"fleeSpeed={7.6 if kind == 'deer' else 6.2}, "
                                        f"sight={9.0 if kind == 'deer' else 6.5}, "
                                        f"homeRadius=16.0")])
            if kind == "deer":
                body_part(sc, f"DeerBody_{unit}", unit, "Capsule", 0.0, 0.0, 0.0,
                          0.72, 1.5, 0.85, TAN)
                body_part(sc, f"DeerNeck_{unit}", unit, "Capsule", 0.0, 0.62, 0.62,
                          0.3, 0.9, 0.3, TAN, rot_x=24.0)
                body_part(sc, f"DeerHead_{unit}", unit, "Cube", 0.0, 1.06, 0.86,
                          0.4, 0.34, 0.6, TAN)
                body_part(sc, f"DeerLegA_{unit}", unit, "Cube", -0.34, -0.72, 0.34,
                          0.18, 1.1, 0.18, WOOD_BARK)
                body_part(sc, f"DeerLegB_{unit}", unit, "Cube", 0.34, -0.72, 0.34,
                          0.18, 1.1, 0.18, WOOD_BARK)
                body_part(sc, f"DeerLegC_{unit}", unit, "Cube", -0.34, -0.72, -0.34,
                          0.18, 1.1, 0.18, WOOD_BARK)
                body_part(sc, f"DeerLegD_{unit}", unit, "Cube", 0.34, -0.72, -0.34,
                          0.18, 1.1, 0.18, WOOD_BARK)
                body_part(sc, f"DeerAntler_{unit}", unit, "Cylinder", 0.16, 1.34, 0.9,
                          0.06, 0.5, 0.06, WOOD_BARK, rot_x=18.0)
                body_part(sc, f"DeerAntlerB_{unit}", unit, "Cylinder", -0.16, 1.34, 0.9,
                          0.06, 0.5, 0.06, WOOD_BARK, rot_x=18.0)
            else:
                body_part(sc, f"BoarBody_{unit}", unit, "Capsule", 0.0, 0.0, 0.0,
                          0.85, 1.35, 0.95, DIRT)
                body_part(sc, f"BoarHead_{unit}", unit, "Cube", 0.0, 0.1, 0.78,
                          0.6, 0.5, 0.5, DIRT_DARK)
                body_part(sc, f"BoarLegA_{unit}", unit, "Cube", -0.32, -0.62, 0.32,
                          0.18, 0.66, 0.18, DIRT_DARK)
                body_part(sc, f"BoarLegB_{unit}", unit, "Cube", 0.32, -0.62, 0.32,
                          0.18, 0.66, 0.18, DIRT_DARK)
                body_part(sc, f"BoarLegC_{unit}", unit, "Cube", -0.32, -0.62, -0.32,
                          0.18, 0.66, 0.18, DIRT_DARK)
                body_part(sc, f"BoarLegD_{unit}", unit, "Cube", 0.32, -0.62, -0.32,
                          0.18, 0.66, 0.18, DIRT_DARK)

    # ---------------------------------------------------------------- weather + fx
    sc.add("Weather", x=0.0, y=26.0, z=0.0, tag="Fx",
           comps=[particles(color="#38DCE6F2", end="#00DCE6F2", size=0.5, end_size=0.9,
                            rate=44.0, lifetime=6.0, speed=0.6, direction=270, spread=180,
                            gravity=0.25, max_particles=190, additive=True,
                            texture="fx_smoke.png", emitting=False)])
    sc.add("DustFx", tag="Template", active=False,
           comps=[particles(color="#8CD6C7A8", end="#00D6C7A8", size=0.5, end_size=1.4,
                            rate=0.0, lifetime=0.7, speed=1.5, direction=90, spread=180,
                            gravity=-2.2, max_particles=26, additive=True,
                            texture="fx_dust.png", emitting=False)])
    sc.add("SparkFx", tag="Template", active=False,
           comps=[particles(color="#FFFFE9A0", end="#00FFF3C0", size=0.22, end_size=0.02,
                            rate=0.0, lifetime=0.6, speed=3.2, direction=90, spread=180,
                            gravity=-3.0, max_particles=22, additive=True,
                            texture="fx_star.png", emitting=False)])
    sc.add("BeaconFire", tag="Template", active=False,
           comps=[light(kind="Point", color="#FFFFA24A", intensity=3.0, rng=34.0),
                  particles(color="#FFFFC067", end="#20FF5A12", size=0.5, end_size=0.08,
                            rate=34.0, lifetime=1.4, speed=2.6, direction=90, spread=26,
                            gravity=1.6, max_particles=90, additive=True,
                            texture="fx_fire.png", emitting=True)])

    # ---------------------------------------------------------------- director + hud
    sc.add("Valley", tag="Director",
           comps=[script("Valley.js",
                         "dayLength=180, dusk=19.5, dawn=5.5, coldFrom=20.0, "
                         "coldTo=5.0, beacons=3, targetBerry=6")])
    sc.add("Hud", tag="Hud", comps=[script("Hud.js", "barWidth=3.0")])
    sc.add("Fx", tag="Fx", comps=[script("Fx.js", "dustLife=0.7, sparkLife=0.6")])

    sc.add("HudPanel", y=4.15, z=-1.0, sx=13.6, sy=1.5, tag="Hud",
           comps=[sprite("hud_header.png", order=80, screen=True)])
    sc.add("HudObjective", x=-6.2, y=4.45, tag="Hud",
           comps=[text("B E A C O N S   0 / 3", size=0.62, color="#FFFFE6B8",
                       screen=True)])
    sc.add("HudClock", x=-6.2, y=3.9, tag="Hud",
           comps=[text("MORNING 07:15 clear", size=0.42, color="#FFDCE9E4", screen=True)])
    sc.add("HudBag", x=1.4, y=4.45, tag="Hud",
           comps=[text("wood 0   stone 0   berry 0", size=0.5, color="#FFEAD9B0",
                       screen=True)])
    sc.add("StaminaBack", x=-4.9, y=3.4, sx=4.6, sy=0.34, tag="Hud",
           comps=[sprite("ui_slot.png", "#66101418", order=81, screen=True)])
    sc.add("StaminaBar", x=-6.9, y=3.4, sx=3.0, sy=0.24, tag="Hud",
           comps=[sprite("ui_slot.png", "#FF86E0A0", order=82, screen=True)])
    sc.add("HudPrompt", y=-3.2, tag="Hud",
           comps=[text("", size=0.55, color="#FFFFFFFF", align="Center", screen=True)])
    sc.add("HudToast", y=2.9, tag="Hud",
           comps=[text("", size=0.5, color="#FFFFD98A", align="Center", screen=True)])
    sc.add("HudGem", x=5.6, y=4.15, sx=0.7, sy=0.7, tag="Hud",
           comps=[sprite("hud_gem.png", order=83, screen=True)])

    # ---------------------------------------------------------------- assets
    assets = build_assets()

    scripts = build_scripts()
    man = manifest(
        GID, "Emberfall Valley", "open world 3D survival exploration",
        list(scripts.keys()), assets, orientation=0,
        controls={"axis": "left stick / WASD", "Jump": "A / space",
                  "Interact": "B / enter", "Sprint": "Sprint action (hold)"},
        systems=["third-person camera", "day/night cycle with a real directional sun",
                 "weather states", "forage + resource economy", "objective chain",
                 "herds with flee behaviour", "fires as light + warmth sources",
                 "HUD", "pooled particle FX"],
        notes=[
            "3D kit: kenney_nature_kit. All 329 models are OBJs with flat materials "
            "and no textures; Meshes.loadObj ignores mtllib/usemtl and normalises "
            "every model to a unit cube centred on its origin, so each object here "
            "is scaled to its intended size and placed at y = size / 2 to stand on "
            "the ground.",
            "One colour per mesh object, by design: each tint is the face-dominant "
            "material of that model read out of the pack's own .mtl files (see "
            "tools/biggames/nature.py). Canopied trees therefore render with a "
            "green trunk -- the canopy is 2-4x the trunk's faces -- so they are "
            "used as a back-lit belt at the valley edge; the near ground uses the "
            "kit's shrubs, logs and standing stones, which read cleanly in one "
            "colour.",
            "The ranger's gravity is script-owned rather than Rigidbody3D: the "
            "terrain is a flat plane with a script-clamped boundary, and a "
            "script-owned arc behaves identically in the engine and in the offline "
            "harness (tools/run_games.js), which is where the game is tested.",
            "The sun, the sky gradient and the fog colours are driven from "
            "Valley.js through the Light and Camera3D APIs, so the day/night cycle "
            "is real lighting rather than a screen tint.",
            "Audio aliases: the store ships six UI sounds in kenney_ui_pack; every "
            "cue in this game is one of those six under a name that says what it "
            "means.",
        ])
    return sc, man, assets, scripts


def main():
    sc, man, assets, scripts = build()
    readme = """# Emberfall Valley

A third-person open-world survival game set in a river valley, built entirely
from the store's 3D kit (`kenney_nature_kit`).

## The loop

Gather wood, berries and stone from the shrubs, cairns and stumps scattered
across the valley.  Light the three ridge beacons before nightfall -- each one
costs three wood and becomes a warming fire.  Night brings cold that bleeds your
stamina unless you are standing near fire, so the camp and any lit beacon are
where you survive the dark.  Light all three to hold the valley.

## Systems

| System | Where |
| --- | --- |
| Third-person movement, sprint, hop, carry | `Ranger.js` |
| Day/night cycle, weather, objectives, cold | `Valley.js` |
| Gatherable nodes with respawn | `Forage.js` |
| Ridge beacons that light for wood | `Beacon.js` |
| Camp fire: rest, sleep to dawn, eat | `Camp.js` |
| Herds that graze and bolt | `Wildlife.js` |
| Screen-space HUD | `Hud.js` |
| Pooled particle FX | `Fx.js` |

## Reading this game

`tools/run_games.js 11_emberfall --frames 600 --dump /tmp/w` runs it headless,
and `tools/preview3d.py 11_emberfall_valley_3d --world /tmp/w/*.json --out v.png`
renders what the engine will draw -- `--camera cam` is the player's own view,
`--camera free --dist 90` an overhead plan of the valley.
"""
    write_game(GID, man, sc, scripts, readme)
    print(f"  wrote {GID}: {len(sc.objs)} objects, {len(assets)} assets")


if __name__ == "__main__":
    main()
