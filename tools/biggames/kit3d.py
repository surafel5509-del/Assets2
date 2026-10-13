#!/usr/bin/env python3
"""kit3d.py -- shared authoring helpers for scenes built out of Kenney's Nature Kit.

Both 3D games in this repository are assembled from the same pack, and both run
into the same two engine facts (see tools/biggames/nature.py for the full note):

  * `Meshes.loadObj` normalises every model to a unit cube centred on its origin,
    so an object's scale *is* the model's size in world units, and a ground prop
    sits at y = size / 2 to stand on the ground.
  * one mesh object draws one colour -- `mtllib`/`usemtl` are ignored -- so the
    tint has to come from somewhere sensible: the model's face-dominant material
    (read from the kit's .mtl files) mapped through a game palette, because the
    kit's own demo palette is neon and reads as placeholder art at game scale.

`prop()` and `ground()` are the two functions every scene is built from.
"""

from __future__ import annotations

import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
if HERE not in sys.path:
    sys.path.insert(0, HERE)

import nature  # noqa: E402
from scene_kit import mesh  # noqa: E402

KIT = nature.PACK_ID
KIT_PAL = nature.palette()
KIT_IDX = nature.model_index()
_SEQ = [0]


def dominant(model):
    """The material that covers most of a model's faces, per the pack's OBJ data."""
    return KIT_IDX[model]["dominant"]


def tint_for(model, palette, mapping):
    """Palette colour for a model: its dominant material, mapped to this game's art."""
    dom = dominant(model)
    return mapping.get(dom, palette.get(dom, KIT_PAL[dom]))


def prop(sc, model, x, z, size, y=None, rot_y=0.0, color=None, tag="Prop",
         palette=None, mapping=None, specular=0.08, shininess=12.0, extra=()):
    """One kit model placed on the ground, sized in world units."""
    if color is None:
        color = tint_for(model, palette or {}, mapping or {})
    yy = size * 0.5 if y is None else y
    _SEQ[0] += 1
    return sc.add(f"{model}_{_SEQ[0]}", x=x, y=yy, z=z, rotY=rot_y,
                  sx=size, sy=size, sz=size, tag=tag,
                  comps=[mesh(kind="Custom Model", model=model + ".obj", color=color,
                              specular=specular, shininess=shininess)] + list(extra))


def ground(sc, model, x, z, sx, sz, color=None, y=0.0, tag="Ground",
           palette=None, mapping=None, rot_y=0.0, specular=0.0):
    """A flat plate: a ground tile or a water/road plane, stretched to size.

    `y` is the height of the plate's **top surface** -- the level a character
    stands on -- not its centre.  A mesh is a unit cube, so the plate is one unit
    thick and hangs below the surface it declares; that is what keeps feet,
    props and road edges on the ground instead of half sunk in it.

    The kit has no textures, so a stretched flat colour plane costs nothing and
    saves a hundred draw calls compared with tiling the same area.
    """
    if color is None:
        color = tint_for(model, palette or {}, mapping or {})
    _SEQ[0] += 1
    return sc.add(f"{model}_g{_SEQ[0]}", x=x, y=y - 0.5, z=z, rotY=rot_y, sx=sx, sy=1.0,
                  sz=sz, tag=tag, comps=[mesh(kind="Custom Model", model=model + ".obj",
                                              color=color, specular=specular, shininess=8.0)])


def body_part(sc, name, parent, kind, x, y, z, sx, sy, sz, color, rot_x=0.0, rot_y=0.0,
              specular=0.15, shininess=20.0, emission=0.0, tag="Unit"):
    """A primitive mesh child: characters and buildings are assembled from these."""
    return sc.add(name, x=x, y=y, z=z, rotX=rot_x, rotY=rot_y, sx=sx, sy=sy, sz=sz,
                  parent=parent, tag=tag,
                  comps=[mesh(kind=kind, color=color, specular=specular,
                              shininess=shininess, emission=emission)])


def model_assets(names, as_suffix=".obj"):
    """`asset()` entries for a list of kit models."""
    from scene_kit import asset  # noqa: PLC0415
    out = []
    for n in names:
        out.append(asset(KIT, nature.model_asset(n)["path"], n + as_suffix))
    return out


def ring(count, cx, cz, radius, start=0.0):
    """`count` evenly spaced (x, z) points on a circle -- used for stone rings."""
    import math  # noqa: PLC0415
    return [(cx + math.sin(math.radians(start + i * 360.0 / count)) * radius,
             cz + math.cos(math.radians(start + i * 360.0 / count)) * radius)
            for i in range(count)]
