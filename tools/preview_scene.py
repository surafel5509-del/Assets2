#!/usr/bin/env python3
"""
preview_scene.py -- render a game level to a PNG, offline, without the engine.

Why this exists: the engine needs an Android device, and most of what makes a
level look like a level is decided at *runtime* -- a tilemap a script builds, a
procedural forest, a wave of spawned enemies.  None of that is visible in a
`.scene.json`.  So the pipeline is:

    node tools/run_games.js <game> --dump /tmp/world      # run the scripts, dump the world
    python3 tools/preview_scene.py <game> --world /tmp/world/<game>.json --out level.png

and this file turns that world into an image.  It re-implements exactly three
engine rules, and takes them from the Kotlin rather than from memory:

  * `AnimationClip.cellUv`  -- frame index -> UV rect on the sheet
  * `SceneRenderer.sortedObjects` -- draw order is (order, creation index)
  * `View2D`                -- orthographic, +Y is UP, size = half-height

Everything else (texture resolution, tinting, scaling) is a best effort: this is
a design tool for checking that the right *art* is in the right *place*, not a
reference implementation of the renderer.  A tile sampled from the wrong cell
shows up here immediately, which is the entire point -- on device it is simply
"the sprite looks wrong" with no clue as to which of 300 objects did it.

Usage:
    python3 tools/preview_scene.py 09_dungeon_crawler_2d \
        --world /tmp/world/09_dungeon_crawler_2d.json --out /tmp/level.png \
        [--ppu 24] [--mode 2d|plan] [--follow Camera|Player|first] \
        [--center X,Y] [--size UNITS] [--max-cells N] [--frames DIR]

`--mode plan` draws a 3D scene from above (x/z), which is how a level layout is
checked: paths, buildings, the racing line.  `--frames DIR` renders a filmstrip
from the per-frame dumps written by `run_games.js --every N`.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from probe_png import Png, read_png, write_png, draw_text  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(os.environ.get("TMPDIR", "/tmp"), "sengine-preview")


# --------------------------------------------------------------------------- assets


class AssetTable:
    """`as` name -> source PNG (extracted from its pack ZIP on demand)."""

    def __init__(self, repo, manifest_path=None):
        self.repo = repo
        with open(os.path.join(repo, "app/src/main/assets/store/store_manifest.json"),
                  encoding="utf-8") as f:
            self.manifest = json.load(f)
        self.packs = {p["id"]: p for p in self.manifest["packs"]}
        self.zips = {}
        self._images = {}
        self.entries = {}       # as -> (pack, path)
        self.clips = {}         # clip name -> (texture, cols, rows, first frame, fps, loop)

    def declare(self, assets):
        for a in assets:
            self.entries[a.get("as") or os.path.basename(a.get("path", ""))] = (
                a.get("pack"), a.get("path"))
            grid = a.get("anim") or a.get("tileset")
            if not grid or not a.get("clips"):
                continue
            cols = int(grid.get("columns", 1)) or 1
            rows = int(grid.get("rows", 1)) or 1
            for name, clip in a["clips"].items():
                frames = clip.get("frames") or [0]
                self.clips[name] = (a.get("as"), cols, rows, int(frames[0]),
                                    float(clip.get("fps", 8)), bool(clip.get("loop", True)))

    def _zip(self, pack_id):
        if pack_id not in self.zips:
            p = self.packs[pack_id]
            self.zips[pack_id] = zipfile.ZipFile(os.path.join(self.repo, "Assets", p["zip"]))
        return self.zips[pack_id]

    def image(self, name):
        """Resolve an asset name to a decoded PNG, caching both ZIP and pixels."""
        if name in self._images:
            return self._images[name]
        ent = self.entries.get(name)
        img = None
        if ent and ent[0] in self.packs:
            pack, path = ent
            os.makedirs(CACHE, exist_ok=True)
            safe = f"{pack}__{path}".replace("/", "_").replace(" ", "_")
            local = os.path.join(CACHE, safe)
            if not os.path.exists(local):
                z = self._zip(pack)
                try:
                    data = z.read(path)
                except KeyError:
                    # The store indexes paths exactly as they appear in the ZIP;
                    # some packs nest one extra top-level folder, so fall back to
                    # matching on the tail of the path.
                    hits = [n for n in z.namelist() if n.endswith(path)]
                    if not hits:
                        data = None
                    else:
                        data = z.read(hits[0])
                if data is None:
                    self._images[name] = None
                    return None
                open(local, "wb").write(data)
            try:
                img = read_png(local)
            except Exception as e:                                  # noqa: BLE001
                print(f"  ! {name}: {e}", file=sys.stderr)
                img = None
        else:
            # A name the game.json does not declare: look for it in the project
            # scene folders, purely so authored-but-undeclared art is visible.
            print(f"  ! texture '{name}' is not declared by game.json", file=sys.stderr)
        self._images[name] = img
        return img


# --------------------------------------------------------------------------- world


class Node:
    __slots__ = ("name", "tag", "x", "y", "z", "wx", "wy", "wz", "rotation", "rotX", "rotY",
                 "sx", "sy", "sz", "order", "texture", "animation", "color", "visible",
                 "text", "index", "parent")

    def __init__(self, d, index):
        self.index = index
        self.name = d.get("name", "?")
        self.tag = d.get("tag", "Untagged")
        self.x = float(d.get("x", 0)); self.y = float(d.get("y", 0)); self.z = float(d.get("z", 0))
        self.wx, self.wy, self.wz = self.x, self.y, self.z
        self.rotation = float(d.get("rotation", 0))
        self.rotX = float(d.get("rotX", 0)); self.rotY = float(d.get("rotY", 0))
        self.sx = float(d.get("scaleX", 1)); self.sy = float(d.get("scaleY", 1)); self.sz = float(d.get("scaleZ", 1))
        self.order = int(d.get("order", 0))
        self.texture = d.get("texture", "") or ""
        self.animation = d.get("animation", "") or ""
        self.color = d.get("color", "#FFFFFFFF")
        self.visible = d.get("visible", True)
        self.text = d.get("text", "") or ""
        self.parent = d.get("parent")


def world_positions(nodes):
    """Parent chain -> world transform.  Rotation is only ever Z in these games."""
    by_id = {n.index: n for n in nodes}
    # The dump uses the engine's object ids; index is the array position.  Build
    # the id map from the raw dicts instead, so parenting survives.
    return by_id


def resolve(nodes_raw):
    by_id = {}
    for d in nodes_raw:
        by_id[d.get("id")] = d
    for d in nodes_raw:
        x, y, z = float(d.get("x", 0)), float(d.get("y", 0)), float(d.get("z", 0))
        rot = float(d.get("rotation", 0))
        sx, sy, sz = float(d.get("scaleX", 1)), float(d.get("scaleY", 1)), float(d.get("scaleZ", 1))
        p = by_id.get(d.get("parent"))
        guard = 0
        while p is not None and guard < 32:
            guard += 1
            pr = math.radians(float(p.get("rotation", 0)))
            # scale then rotate then translate (matches Affine.setTRS)
            cx, cy = x * sx, y * sy
            x = cx * math.cos(pr) - cy * math.sin(pr) + float(p.get("x", 0))
            y = cx * math.sin(pr) + cy * math.cos(pr) + float(p.get("y", 0))
            z += float(p.get("z", 0))
            rot += float(p.get("rotation", 0))
            sx *= float(p.get("scaleX", 1)); sy *= float(p.get("scaleY", 1)); sz *= float(p.get("scaleZ", 1))
            p = by_id.get(p.get("parent"))
        d["_wx"], d["_wy"], d["_wz"] = x, y, z
        d["_rot"] = rot
        d["_sx"], d["_sy"] = sx, sy
    return nodes_raw


def parse_color(s, default=(255, 255, 255, 255)):
    if not s or not isinstance(s, str) or not s.startswith("#"):
        return default
    h = s[1:]
    try:
        if len(h) == 8:
            a, r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4, 6))
        elif len(h) == 6:
            r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4)); a = 255
        else:
            return default
    except ValueError:
        return default
    return (r, g, b, a)


# --------------------------------------------------------------------------- painter


class Canvas(Png):
    pass


def new_canvas(w, h, bg):
    return Png(w, h, [[bg] * w for _ in range(h)])


def blend(dst, s):
    if s[3] >= 255:
        return s
    if s[3] == 0:
        return dst
    a = s[3] / 255.0
    return (int(s[0] * a + dst[0] * (1 - a)),
            int(s[1] * a + dst[1] * (1 - a)),
            int(s[2] * a + dst[2] * (1 - a)),
            max(dst[3], s[3]))


def draw_quad(cv, image, uv, dst_cx, dst_cy, dst_w, dst_h, rot_deg, tint, flip_x=False, flip_y=False):
    """Draw a (possibly sub-rect) sprite with rotation, tint and alpha.

    Inverse-mapped: walk the destination bounding box, transform each pixel back
    into the sprite's local space, sample nearest-neighbour.  That keeps rotated
    and non-uniformly scaled sprites correct, which matters because chunked
    tilemaps and stretched floor tiles are non-uniform on purpose.
    """
    if image is None or dst_w <= 0 or dst_h <= 0:
        return 0
    u0, v0, u1, v1 = uv
    sx0, sy0 = u0 * image.w, v0 * image.h
    sx1, sy1 = u1 * image.w, v1 * image.h
    rad = math.radians(-rot_deg)                  # screen y is flipped below
    cos_r, sin_r = math.cos(rad), math.sin(rad)
    half_w, half_h = dst_w / 2.0, dst_h / 2.0
    span = int(math.ceil(math.hypot(dst_w, dst_h) / 2.0)) + 1
    drawn = 0
    for dy in range(-span, span + 1):
        for dx in range(-span, span + 1):
            lx = (dx * cos_r - dy * sin_r) / half_w     # -1..1
            ly = (dx * sin_r + dy * cos_r) / half_h
            if lx < -1 or lx > 1 or ly < -1 or ly > 1:
                continue
            px = int(dst_cx) + dx
            py = int(dst_cy) + dy
            if px < 0 or py < 0 or px >= cv.w or py >= cv.h:
                continue
            u = (lx + 1) * 0.5
            v = (ly + 1) * 0.5
            if flip_x:
                u = 1 - u
            if flip_y:
                v = 1 - v
            sxp = int(sx0 + (sx1 - sx0) * u)
            syp = int(sy0 + (sy1 - sy0) * v)
            if sxp < 0 or syp < 0 or sxp >= image.w or syp >= image.h:
                continue
            src = image.px[syp][sxp]
            if src[3] == 0:
                continue
            t = (src[0] * tint[0] // 255, src[1] * tint[1] // 255,
                 src[2] * tint[2] // 255, src[3] * tint[3] // 255)
            cv.px[py][px] = blend(cv.px[py][px], t)
            drawn += 1
    return drawn


def draw_box(cv, cx, cy, w, h, color):
    for y in range(int(cy - h / 2), int(cy + h / 2) + 1):
        for x in range(int(cx - w / 2), int(cx + w / 2) + 1):
            if 0 <= x < cv.w and 0 <= y < cv.h:
                cv.px[y][x] = blend(cv.px[y][x], color)


# --------------------------------------------------------------------------- render


def render(game_dir, world_file, out, ppu, mode, follow, center, size_override,
           max_cells, bg_override, label, scale_out, force_ui=False):
    with open(os.path.join(game_dir, "game.json"), encoding="utf-8") as f:
        meta = json.load(f)
    with open(world_file, encoding="utf-8") as f:
        world = json.load(f)
    assets_file = world_file.replace(".json", ".assets.json")
    if os.path.exists(assets_file):
        with open(assets_file, encoding="utf-8") as f:
            meta_assets = json.load(f).get("assets", [])
    else:
        meta_assets = meta.get("assets", [])

    table = AssetTable(ROOT)
    table.declare(meta_assets)

    objs = resolve(world["objects"])

    # --- camera / view
    scn_file = os.path.join(game_dir, "scenes", (meta.get("startScene") or "Main") + ".scene.json")
    view_half, view_bg = (5.0, (20, 24, 32, 255))
    if os.path.exists(scn_file):
        with open(scn_file, encoding="utf-8") as f:
            scn = json.load(f)
        for o in scn.get("objects", []):
            for c in o.get("components", []):
                if c.get("type") == "Camera":
                    view_half = float(c.get("Size", 5.0))
                    view_bg = parse_color(c.get("Background"))
                    if follow == "auto":
                        follow = c.get("Follow Target") or "first"
                if c.get("type") == "Camera3D":
                    view_bg = parse_color(c.get("Sky Horizon"))
    if bg_override:
        view_bg = parse_color(bg_override)
    if size_override:
        view_half = size_override

    # --- work out what part of the world to look at
    target = None
    if follow and follow not in ("first", "auto", "none"):
        target = next((o for o in objs if o.get("name") == follow), None)
    if target is None:
        target = next((o for o in objs if o.get("name") == "Player"), None)
    if target is None:
        target = next((o for o in objs if o.get("name") == "Hero"), None)
    if target is None:
        target = next((o for o in objs if o.get("name") == "Car"), None)
    if center:
        cx, cy = center
    elif max_cells and target is None:
        cx = cy = 0.0
    elif target is not None:
        if mode == "plan":
            cx, cy = target["_wx"], target["_wz"]
        else:
            cx, cy = target["_wx"], target["_wy"]
    else:
        # Nothing to follow: frame everything that draws.
        pts = [(o["_wx"], o["_wz"] if mode == "plan" else o["_wy"])
               for o in objs if o.get("texture") or o.get("animation")]
        if not pts:
            pts = [(0.0, 0.0)]
        cx = (min(p[0] for p in pts) + max(p[0] for p in pts)) / 2
        cy = (min(p[1] for p in pts) + max(p[1] for p in pts)) / 2

    if mode == "plan" or max_cells:
        # Whole-level overview: crop the world bounds into the image.
        pts = [(o["_wx"], o["_wz"] if mode == "plan" else o["_wy"]) for o in objs]
        if pts:
            lo_x = min(p[0] for p in pts); hi_x = max(p[0] for p in pts)
            lo_y = min(p[1] for p in pts); hi_y = max(p[1] for p in pts)
            if center:
                cx, cy = center
                lo_x, hi_x = cx - view_half, cx + view_half
                lo_y, hi_y = cy - view_half, cy + view_half
            span = max(hi_x - lo_x, hi_y - lo_y, 1.0) * 1.05
            cx, cy = (lo_x + hi_x) / 2, (lo_y + hi_y) / 2
            view_half = span / 2

    aspect = 16 / 9.0
    img_h = max(64, int(2 * view_half * ppu))
    img_w = int(img_h * aspect)
    cv = new_canvas(img_w, img_h, view_bg)

    def project(wx, wy):
        sx = img_w / 2.0 + (wx - cx) * ppu
        sy = img_h / 2.0 - (wy - cy) * ppu
        return sx, sy

    # --- draw, in the engine's order
    ordered = sorted(enumerate(objs), key=lambda p: (p[1].get("order", 0), p[0]))
    drawn = skipped = 0
    for idx, o in ordered:
        if o.get("_destroyed") or not o.get("visible", True):
            continue
        # Screen-space UI is drawn by SceneRenderer.drawScreenUI through its own
        # fixed +/-5-unit view (below), so it must not be drawn here: at its
        # authored coordinates a HUD panel lands in the middle of the level and
        # hides the thing this tool exists to check.
        if o.get("screen"):
            continue
        texture = o.get("animation") or o.get("texture") or ""
        clip = table.clips.get(o.get("animation")) if o.get("animation") else None
        image = None
        uv = (0.0, 0.0, 1.0, 1.0)
        if clip:
            tex_name, cols, rows, frame, _fps, _loop = clip
            image = table.image(tex_name)
            if image is not None:
                col = frame % cols
                row = min(frame // cols, rows - 1)
                uv = (col / cols, row / rows, (col + 1) / cols, (row + 1) / rows)
        if image is None and texture:
            image = table.image(texture)
        if image is None:
            if o.get("text"):            # HUD text: draw it as a label box
                sx, sy = project(o["_wx"], o["_wy"])
                draw_text(cv, o["text"][:26], int(sx) + 4, int(sy) - 3,
                          color=parse_color(o.get("color")), scale=max(1, ppu // 22),
                          background=None)
                drawn += 1
                continue
            if o.get("texture") or o.get("animation"):
                skipped += 1
            continue
        if mode == "plan":
            wx, wy, rot = o["_wx"], o["_wz"], o.get("rotY", 0)
        else:
            wx, wy, rot = o["_wx"], o["_wy"], o["_rot"]
        sx, sy = project(wx, wy)
        w = o["_sx"] * ppu
        h = o["_sy"] * ppu
        if draw_quad(cv, image, uv, sx, sy, w, h, rot, parse_color(o.get("color"))):
            drawn += 1

    # --- particle emitters.  A steady emitter is approximated by a soft disc of
    # its start colour at its position: enough to see that a torch reads as a
    # light source, without simulating the engine's particle pool here.
    for idx, o in ordered:
        if not o.get("_emitting"):
            continue
        sx, sy = project(o["_wx"], o["_wy"] if mode == "2d" else o["_wz"])
        r = max(2.0, o["_size"] * ppu)
        col = parse_color(o["_color"])
        for ring in range(4):
            rr = r * (1.0 - ring * 0.22)
            a = int(col[3] * (0.30 - ring * 0.06))
            if a <= 0:
                continue
            c = (col[0], col[1], col[2], a)
            for y in range(int(sy - rr), int(sy + rr) + 1):
                for x in range(int(sx - rr), int(sx + rr) + 1):
                    if 0 <= x < cv.w and 0 <= y < cv.h and (x - sx) ** 2 + (y - sy) ** 2 <= rr * rr:
                        cv.px[y][x] = blend(cv.px[y][x], c)

    # --- screen-space UI, on top, in a fixed 10-unit-tall view (View2D size 5).
    # Every object the scene marks screen-space lands here, which is how the HUD
    # can be checked offline: the engine's drawScreenUI ignores the game camera
    # and lays these out in a +/-5-unit view of their own.
    # A --center crop is a look at the world, and the screen-space HUD would sit
    # on top of it in its own fixed view (which is why a centred render used to
    # show a HUD panel floating over the room being inspected).  It is drawn for
    # whole-level stills, or on request.
    ui_ppu = img_h / 10.0
    for idx, o in ordered:
        if center is not None and not force_ui:
            break
        if not o.get("screen") or o.get("_destroyed") or not o.get("visible", True):
            continue
        sx = img_w / 2.0 + o["_wx"] * ui_ppu
        sy = img_h / 2.0 - o["_wy"] * ui_ppu
        texture = o.get("texture") or ""
        image = table.image(texture) if texture else None
        if image is not None:
            if draw_quad(cv, image, (0.0, 0.0, 1.0, 1.0), sx, sy,
                         o["_sx"] * ui_ppu, o["_sy"] * ui_ppu, 0.0,
                         parse_color(o.get("color"))):
                drawn += 1
            continue
        if o.get("text"):
            col = parse_color(o.get("color"))
            draw_text(cv, o["text"][:40], int(sx), int(sy), color=col,
                      scale=max(1, int(ui_ppu / 16)), background=None)
            drawn += 1

    if label:
        draw_text(cv, f"{meta.get('title', '')[:22]}", 4, 4, color=(255, 255, 255, 255),
                  scale=2, background=(0, 0, 0, 190))

    if scale_out > 1:
        cv = cv.scaled(scale_out)
    os.makedirs(os.path.dirname(os.path.abspath(out)) or ".", exist_ok=True)
    write_png(out, cv)
    print(f"{os.path.basename(game_dir)}: {len(objs)} objects, {drawn} drawn, "
          f"{skipped} unresolved  ->  {out}  ({cv.w}x{cv.h})")
    if skipped:
        print(f"  {skipped} sprite(s) had no resolvable texture -- check the "
              f"`as` names in game.json", file=sys.stderr)
    return 0


def filmstrip(frames_dir, out, cols, ppu, game_dir):
    """A filmstrip from the per-frame dumps: `run_games.js --every N --dump D`.

    Each frame is rendered from the same game directory, so the walk cycle,
    the spawns and the camera follow are all visible across the strip -- which is
    the only way to check motion offline."""

    files = sorted(f for f in os.listdir(frames_dir) if f.endswith(".json"))
    if not files:
        print(f"no frames in {frames_dir}", file=sys.stderr)
        return 2
    tiles = []
    for f in files:
        tmp = os.path.join(frames_dir, "_one.png")
        render(game_dir, os.path.join(frames_dir, f), tmp,
               ppu=ppu, mode="2d", follow="auto", center=None, size_override=None,
               max_cells=None, bg_override=None, label=False, scale_out=1)
        tiles.append(read_png(tmp))
    rows = (len(tiles) + cols - 1) // cols
    each_w = tiles[0].w // 2
    each_h = tiles[0].h // 2
    sheet = new_canvas(each_w * cols, each_h * rows, (12, 14, 18, 255))
    for i, t in enumerate(tiles):
        small = Png(each_w, each_h, [[t.px[y * 2][x * 2] for x in range(each_w)] for y in range(each_h)])
        sheet.blit(small, (i % cols) * each_w, (i // cols) * each_h)
        draw_text(sheet, files[i].replace("frame_", "").replace(".json", ""),
                  (i % cols) * each_w + 2, (i // cols) * each_h + 2, color=(255, 230, 120, 255))
    write_png(out, sheet)
    print(f"filmstrip: {len(tiles)} frames -> {out} ({sheet.w}x{sheet.h})")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser(description="Render a game world dump to a PNG.")
    ap.add_argument("game", help="game id, e.g. 09_dungeon_crawler_2d")
    ap.add_argument("--repo", default=ROOT)
    ap.add_argument("--world", help="world dump from run_games.js --dump")
    ap.add_argument("--frames", help="directory of per-frame dumps (filmstrip)")
    ap.add_argument("--cols", type=int, default=4, help="filmstrip columns")
    ap.add_argument("--out", default="/tmp/preview.png")
    ap.add_argument("--ppu", type=int, default=16, help="pixels per world unit")
    ap.add_argument("--scale", type=int, default=1, help="integer upscale of the output")
    ap.add_argument("--mode", choices=["2d", "plan"], default="2d")
    ap.add_argument("--follow", default="auto")
    ap.add_argument("--center", help="X,Y")
    ap.add_argument("--size", type=float, help="half-height in world units")
    ap.add_argument("--whole", action="store_true", help="frame the whole level")
    ap.add_argument("--bg", help="override background colour")
    ap.add_argument("--no-label", action="store_true")
    ap.add_argument("--ui", action="store_true",
                    help="draw the screen-space HUD even on a --center crop")
    args = ap.parse_args()

    game_dir = args.game if os.path.isdir(args.game) else os.path.join(args.repo, "games", args.game)
    if not os.path.isdir(game_dir):
        print(f"no such game: {args.game}", file=sys.stderr)
        return 2
    if args.frames:
        return filmstrip(args.frames, args.out, args.cols, args.ppu, game_dir)
    world = args.world or os.path.join("/tmp/world", os.path.basename(game_dir) + ".json")
    center = tuple(float(v) for v in args.center.split(",")) if args.center else None
    return render(game_dir, world, args.out, args.ppu, args.mode, args.follow, center,
                  args.size, args.whole, args.bg, not args.no_label, args.scale,
                  force_ui=args.ui)


if __name__ == "__main__":
    raise SystemExit(main())
