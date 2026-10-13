"""
scene_kit.py -- the authoring helpers the big games are built from.

The engine's scene format is a flat list of objects with serialised components
whose keys are the *display names* declared in Components.kt.  A typo in a key
does not raise -- the loader simply never reads it and the value falls back to
its default, so a scene can look configured and behave as if it were empty.
tools/validate_games.py catches that (it derives the valid keys from the Kotlin),
which is why these helpers take the key names from one place rather than letting
each game spell them out.

Everything here is deterministic: fixed seeds, sorted iteration, no clock.  A
regenerate produces a byte-identical tree, so a diff shows intent.
"""

from __future__ import annotations

import json
import os
import random

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "games"))


class Scene:
    """A .scene.json under construction.

    `env` is the scene's global block.  Key names here are the ones Scene.kt
    reads; anything else would be inert (and is rejected by the validator).
    """

    def __init__(self, name="Main", gravity=(0.0, -9.81, 0.0), gravity3d=-9.81,
                 ambient="#FF6A6A78", fog=False, fog_color="#FF1A1622",
                 fog_start=40.0, fog_end=120.0):
        self.name = name
        self.env = {
            "gravityX": float(gravity[0]), "gravityY": float(gravity[1]),
            "gravityZ": float(gravity[2]), "gravity3D": float(gravity3d),
            "ambient": ambient,
            "fog": bool(fog), "fogColor": fog_color,
            "fogStart": float(fog_start), "fogEnd": float(fog_end),
        }
        self.objs = []
        self.next = 1
        self._names = set()

    # ------------------------------------------------------------------ objects
    def add(self, name, x=0.0, y=0.0, z=0.0, rotX=0.0, rotY=0.0, rotation=0.0,
            sx=1.0, sy=1.0, sz=1.0, tag="Untagged", active=True, order=0,
            comps=(), parent=None):
        assert name not in self._names, f"duplicate object name in the scene: {name}"
        self._names.add(name)
        oid = self.next
        self.next += 1
        o = {
            "id": oid, "name": name, "tag": tag, "active": bool(active), "order": order,
            "x": float(x), "y": float(y), "z": float(z),
            "rotX": float(rotX), "rotY": float(rotY), "rotation": float(rotation),
            "scaleX": float(sx), "scaleY": float(sy), "scaleZ": float(sz),
            "components": list(comps),
        }
        if parent is not None:
            o["parent"] = parent
        self.objs.append(o)
        return oid

    def dump(self):
        out = {"name": self.name}
        out.update(self.env)
        out["nextId"] = self.next
        out["objects"] = self.objs
        return out


# ---------------------------------------------------------------- components

def sprite(texture="", color="#FFFFFFFF", shape="Square", order=None, screen=False,
           flip_x=False, flip_y=False, shader="", param=1.0):
    c = {
        "type": "SpriteRenderer", "enabled": True, "Shape": shape, "Color": color,
        "Texture": texture, "Flip X": bool(flip_x), "Flip Y": bool(flip_y),
        "Shader": shader, "Shader Param": float(param), "Screen Space UI": bool(screen),
    }
    return c


def text(value="", size=0.5, color="#FFFFFFFF", align="Left", screen=True, bold=True):
    return {
        "type": "TextRenderer", "enabled": True, "Text": value, "Size": float(size),
        "Color": color, "Align": align, "Bold": bool(bold), "Screen Space UI": bool(screen),
    }


def cam2d(size=7.0, bg="#FF0B0710", follow="", smooth=7.0, post="None", fx=1.0):
    return {
        "type": "Camera", "enabled": True, "Size": float(size), "Background": bg,
        "Follow Target": follow, "Follow Smoothing": float(smooth),
        "Post FX": post, "FX Intensity": float(fx), "FX Shader": "",
    }


def cam3d(follow="", off=(0.0, 4.0, 9.0), fov=62.0, near=0.1, far=400.0, smooth=6.0,
          sky_top="#FF3B7BD4", sky_horizon="#FFBFD8F0", post="None", fx=1.0, look_at=True):
    return {
        "type": "Camera3D", "enabled": True, "Field of View": float(fov),
        "Near": float(near), "Far": float(far),
        "Sky Top": sky_top, "Sky Horizon": sky_horizon,
        "Follow Target": follow, "Offset X": float(off[0]), "Offset Y": float(off[1]),
        "Offset Z": float(off[2]), "Look At Target": bool(look_at),
        "Follow Smoothing": float(smooth), "Post FX": post, "FX Intensity": float(fx),
        "FX Shader": "",
    }


def rigid2d(body="Dynamic", mass=1.0, gravity=1.0, drag=0.0, bounce=0.0, friction=0.4,
            vx=0.0, vy=0.0):
    return {
        "type": "Rigidbody2D", "enabled": True, "Body Type": body, "Mass": float(mass),
        "Gravity Scale": float(gravity), "Linear Drag": float(drag),
        "Bounciness": float(bounce), "Friction": float(friction),
        "Velocity X": float(vx), "Velocity Y": float(vy),
    }


def collider2d(shape="Box", w=1.0, h=1.0, r=0.5, ox=0.0, oy=0.0, trigger=False):
    return {
        "type": "Collider2D", "enabled": True, "Shape": shape, "Width": float(w),
        "Height": float(h), "Radius": float(r), "Offset X": float(ox), "Offset Y": float(oy),
        "Is Trigger": bool(trigger),
    }


def rigid3d(body="Dynamic", mass=1.0, gravity=1.0, drag=0.0, bounce=0.0, friction=0.5,
            vx=0.0, vy=0.0, vz=0.0):
    return {
        "type": "Rigidbody3D", "enabled": True, "Body Type": body, "Mass": float(mass),
        "Gravity Scale": float(gravity), "Linear Drag": float(drag),
        "Bounciness": float(bounce), "Friction": float(friction),
        "Velocity X": float(vx), "Velocity Y": float(vy), "Velocity Z": float(vz),
    }


def collider3d(shape="Box", size=(1.0, 1.0, 1.0), radius=0.5, center=(0.0, 0.0, 0.0),
               trigger=False):
    return {
        "type": "Collider3D", "enabled": True, "Shape": shape,
        "Size X": float(size[0]), "Size Y": float(size[1]), "Size Z": float(size[2]),
        "Radius": float(radius),
        "Center X": float(center[0]), "Center Y": float(center[1]), "Center Z": float(center[2]),
        "Is Trigger": bool(trigger),
    }


def mesh(kind="Cube", color="#FFFFFFFF", texture="", tiling=1.0, model="",
         specular=0.25, shininess=24.0, emission=0.0, unlit=False, shader="", param=1.0):
    return {
        "type": "MeshRenderer", "enabled": True, "Mesh": kind, "Model (.obj)": model,
        "Color": color, "Texture": texture, "Tiling": float(tiling),
        "Specular": float(specular), "Shininess": float(shininess),
        "Emission": float(emission), "Unlit": bool(unlit), "Shader": shader,
        "Shader Param": float(param),
    }


def light(kind="Directional", color="#FFFFE8C0", intensity=1.2, rng=120.0):
    return {"type": "Light", "enabled": True, "Type": kind, "Color": color,
            "Intensity": float(intensity), "Range": float(rng)}


def script(name, params=""):
    return {"type": "Script", "enabled": True, "Script": name, "Params": params}


def animator(clip="", play_on_start=False, speed=1.0):
    return {"type": "Animator", "enabled": True, "Clip": clip,
            "Play On Start": bool(play_on_start), "Speed": float(speed)}


def particles(color="#FFFFC060", end="#00FF4010", size=0.22, end_size=0.0, rate=0.0,
              lifetime=0.8, speed=4.5, direction=90.0, spread=180.0, gravity=-6.0,
              max_particles=90, additive=True, texture="", emitting=False):
    return {
        "type": "ParticleEmitter", "enabled": True, "Emitting": bool(emitting),
        "Rate": float(rate), "Lifetime": float(lifetime), "Speed": float(speed),
        "Direction": float(direction), "Spread": float(spread),
        "Start Size": float(size), "End Size": float(end_size),
        "Start Color": color, "End Color": end,
        "Gravity": float(gravity), "Max Particles": int(max_particles),
        "Additive Blend": bool(additive), "Texture": texture,
    }


def audio_src(clip, play_on_start=False, loop=False, volume=1.0):
    return {"type": "AudioSource", "enabled": True, "Clip": clip,
            "Play On Start": bool(play_on_start), "Loop": bool(loop), "Volume": float(volume)}


# ---------------------------------------------------------------- grid helpers

def scatter(seed, count, lo, hi, min_dist=2.5, avoid=(), rect=None):
    """Deterministic points, no closer than min_dist to each other or to `avoid`.

    `rect` can pin the region: (x0, x1, y0, y1); otherwise a square [lo, hi] in x
    and y (`hi` may be a (x1, y1) tuple for a non-square area).
    """
    r = random.Random(seed)
    if rect:
        x0, x1, y0, y1 = rect
    else:
        x1 = hi[0] if isinstance(hi, tuple) else hi
        y1 = hi[1] if isinstance(hi, tuple) else hi
        x0 = y0 = lo
    pts = []
    tries = 0
    while len(pts) < count and tries < count * 200:
        tries += 1
        x, y = r.uniform(x0, x1), r.uniform(y0, y1)
        if any((x - ax) ** 2 + (y - ay) ** 2 < min_dist ** 2 for ax, ay in avoid):
            continue
        if any((x - px) ** 2 + (y - py) ** 2 < min_dist ** 2 for px, py in pts):
            continue
        pts.append((round(x, 3), round(y, 3)))
    return pts


def chunk_map(rows, chunk=8):
    """Dominant-tile chunks for a tilemap, as (cx, cy, tile_id, {id: count}).

    One GameObject per tile would be thousands of objects for a big level, so the
    grid is emitted as NxN chunks: the dominant tile paints the chunk, rarer
    tiles get an overlay sprite.  That is how tools/build_games.py's tilemap did
    it and it is the difference between ~60 draw calls and ~4000.
    """
    height = len(rows)
    width = len(rows[0])
    out = []
    for cy in range(0, height, chunk):
        for cx in range(0, width, chunk):
            counts = {}
            for y in range(cy, min(cy + chunk, height)):
                for x in range(cx, min(cx + chunk, width)):
                    tid = rows[y][x]
                    counts[tid] = counts.get(tid, 0) + 1
            dominant = max(counts.items(), key=lambda kv: (kv[1], kv[0]))[0]
            out.append((cx, cy, dominant, counts))
    return out, width, height


def rect_ring(x0, y0, w, h, tile):
    """A hollow rectangle of `tile` characters, as a dict of (x, y) -> tile."""
    out = {}
    for x in range(x0, x0 + w):
        out[(x, y0)] = tile
        out[(x, y0 + h - 1)] = tile
    for y in range(y0, y0 + h):
        out[(x0, y)] = tile
        out[(x0 + w - 1, y)] = tile
    return out


def blank_grid(w, h, fill="."):
    return [[fill] * w for _ in range(h)]


def paint(grid, cells, overwrite=True):
    for (x, y), t in cells.items():
        if 0 <= y < len(grid) and 0 <= x < len(grid[0]):
            if overwrite or grid[y][x] == ".":
                grid[y][x] = t


def to_rows(grid):
    return ["".join(row) for row in grid]


# ---------------------------------------------------------------- output

def write_game(gid, manifest, scene, scripts, readme, extra_files=None):
    base = os.path.join(ROOT, gid)
    os.makedirs(os.path.join(base, "scenes"), exist_ok=True)
    os.makedirs(os.path.join(base, "scripts"), exist_ok=True)
    with open(os.path.join(base, "game.json"), "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
        f.write("\n")
    scene_name = manifest.get("startScene", "Main")
    with open(os.path.join(base, "scenes", f"{scene_name}.scene.json"), "w", encoding="utf-8") as f:
        json.dump(scene.dump(), f, indent=2)
        f.write("\n")
    for name, src in scripts.items():
        with open(os.path.join(base, "scripts", name), "w", encoding="utf-8") as f:
            f.write(src.strip() + "\n")
    with open(os.path.join(base, "README.md"), "w", encoding="utf-8") as f:
        f.write(readme.strip() + "\n")
    for name, content in (extra_files or {}).items():
        path = os.path.join(base, name)
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(content.strip() + "\n")
    return base


def asset(pack, path, as_name, anim=None, clips=None, tileset=None, note=None):
    a = {"pack": pack, "path": path, "as": as_name}
    if anim:
        a["anim"] = anim
    if clips:
        a["clips"] = clips
    if tileset:
        a["tileset"] = tileset
    if note:
        a["note"] = note
    return a


def clip(frames, fps=10.0, loop=True, texture=None):
    c = {"frames": list(frames), "fps": float(fps), "loop": bool(loop)}
    if texture is not None:
        c["texture"] = texture
    return c


def sheet_clips(prefix, cols, fps=12.0, loop=True, texture=None, frames=None):
    """A single clip named `<prefix>` covering the sheet.

    Everything a game needs from a one-row animation sheet, with the frame list
    spelled out rather than implied: cellUv derives the cell from the *index*, so
    a clip that silently stopped at the wrong count would still render -- just
    with frames missing from the loop.
    """
    idx = frames if frames is not None else list(range(cols))
    return {prefix: clip(idx, fps, loop, texture)}


def manifest(gid, title, genre, scripts, assets, orientation=0, controls=None,
             systems=None, notes=None, extra=None):
    m = {
        "id": gid, "title": title, "genre": genre, "orientation": orientation,
        "startScene": "Main", "scenes": ["Main"],
        "networking": False, "ai_assist": False,
        "scripts": list(scripts),
        "controls": controls or {},
        "systems": list(systems or []),
        "engineNotes": {"notes": list(notes or [])},
        "assets": list(assets),
    }
    if extra:
        for k, v in extra.items():
            if k in ("assets", "scripts", "systems", "engineNotes"):
                raise ValueError(f"manifest(): '{k}' must go through its own argument")
            m[k] = v
    return m
