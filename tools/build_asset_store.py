#!/usr/bin/env python3
"""
build_asset_store.py -- S Engine offline Asset Store generator.

Scans every ZIP in ``Assets/``, extracts it into a scratch cache, profiles each
file (kind, pixel dimensions, sprite-sheet grid, category, license) and emits
the catalog the on-device Asset Store reads:

    app/src/main/assets/store/store_manifest.json     <- parsed by AssetStore.kt
    docs/asset-store/CATALOG.md                       <- human readable catalog

Design notes
------------
* The ZIPs themselves are the source of truth and are shipped inside the APK by
  pointing ``android.sourceSets.main.assets.srcDir`` at ``Assets/`` (see
  app/build.gradle.kts).  Nothing is duplicated in git; only this manifest is.
* Packs are extracted *lazily on device*, one ZIP at a time, so a 60 MB store
  never costs 60 MB of storage up front.
* ``Pillow`` is optional.  With it we run a real alpha-channel grid analysis to
  recover sprite-sheet frame sizes; without it we fall back to a size-ratio
  heuristic and mark the entry ``"sheetGuess": true``.

Usage:
    python3 tools/build_asset_store.py [--repo DIR] [--cache DIR] [--no-pil]
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import sys
import zipfile
from collections import Counter, defaultdict
from datetime import datetime, timezone

SCHEMA_VERSION = 2

# --------------------------------------------------------------------------- #
# Per-pack metadata.  Everything here is human-curated: the licences were read
# out of the packs' own licence/readme files, and `distributable` decides
# whether a pack may legally ship inside an engine APK's asset store.
# --------------------------------------------------------------------------- #
PACK_META = {
    "2D Pixel Dungeon Asset Pack v2.0": dict(
        title="2D Pixel Dungeon", kind="2d",
        license="Free for personal/commercial use (see pack)",
        author="Penusbmic", distributable=True,
        tags=["pixel", "dungeon", "rpg", "monsters", "npc", "tileset"]),
    "All Assets": dict(
        title="DawnLike UI & Icons", kind="2d",
        license="CC-BY 3.0 (DawnLike)", author="DragonDePlatino", distributable=True,
        tags=["ui", "icons", "hud", "pixel"]),
    "Enemy_Animations_Set": dict(
        title="Enemy Animations Set", kind="2d",
        license="Free for personal/commercial use", author="Community pack",
        distributable=True, tags=["pixel", "enemy", "skeleton", "vampire", "animation"]),
    "Final": dict(
        title="Anokolisa Fantasy Tiles & Backgrounds", kind="2d",
        license="Free to use, no credit required (Anokolisa)", author="Anokolisa",
        distributable=True, tags=["pixel", "tileset", "background", "fantasy"]),
    "Free asset pack, thank u so much!": dict(
        title="Free Fantasy Backgrounds", kind="2d",
        license="Free for personal/commercial use", author="Community pack",
        distributable=True, tags=["background", "parallax", "fantasy"]),
    "FreeCharactersAnimationsAssetPack": dict(
        title="Free Character Animations (96x96)", kind="2d",
        license="Personal & commercial OK; no redistribution/resale",
        author="Community pack", distributable=True,
        tags=["pixel", "character", "combat", "fighting", "animation"]),
    "Free_Medieval_Fantasy_UI_Pack": dict(
        title="Free Medieval Fantasy UI Pack", kind="2d",
        license="Free for personal and commercial use", author="Community pack",
        distributable=True, tags=["ui", "pixel", "fantasy", "hud", "buttons", "icons"]),
    "Legacy Fantasy - Debug Map": dict(
        title="Legacy Fantasy - Debug Map", kind="2d",
        license="Free, no restrictions (Anokolisa)", author="Anokolisa",
        distributable=True, tags=["pixel", "tileset", "mockup", "fantasy"]),
    "Legacy-Fantasy - High Forest 2.0": dict(
        title="Legacy Fantasy - High Forest 2.3", kind="2d",
        license="Free, no restrictions (Anokolisa)", author="Anokolisa",
        distributable=True,
        tags=["pixel", "character", "tileset", "props", "mob", "animation"]),
    "MemaoCharacterFantasySpritePack-v3": dict(
        title="Memao Fantasy Character Sprites v3", kind="2d",
        license="Free for personal/commercial use", author="Memao",
        distributable=True, tags=["pixel", "character", "fantasy", "animation"]),
    "RPG Top Down Character Asset Pack - FREE": dict(
        title="RPG Top-Down Characters (Free)", kind="2d",
        license="Free version (see pack licence)", author="Community pack",
        distributable=True, tags=["pixel", "topdown", "rpg", "character", "32x32"]),
    "SpriteStack_Cars": dict(
        title="SpriteStack Cars", kind="2d",
        license="Free for personal/commercial use", author="SpriteStack",
        distributable=True, tags=["pixel", "vehicle", "car", "topdown"]),
    # NOTE: untiedgames' licence explicitly withholds a grant from "game making
    # tools (programs whose primary function is creating games)".  Shipping it
    # inside an engine's built-in store is therefore NOT allowed.  It stays
    # indexed (so user-imported copies still work) but is flagged
    # distributable=False and excluded from the bundled store by default.
    "Super Pixel Objects Sample": dict(
        title="Super Pixel Objects (restricted)", kind="2d",
        license="untiedgames licence - NO grant to game-making tools",
        author="Will Tice (@untiedgames)", distributable=False,
        tags=["pixel", "props", "restricted"]),
    "Tiny RPG Character Asset Pack 02 v1.01-Free Demon_A&Blood Monster_A": dict(
        title="Tiny RPG Characters 02 - Demon & Blood Monster", kind="2d",
        license="Free for personal/commercial use", author="Community pack",
        distributable=True, tags=["pixel", "enemy", "rpg", "100x100", "animation"]),
    "Warped Vehicles Files": dict(
        title="Warped Vehicles", kind="2d",
        license="See public-license.pdf in pack", author="Warped",
        distributable=True, tags=["pixel", "vehicle", "car", "animation"]),
    "all_cars": dict(
        title="Top-Down Car Sprites", kind="2d",
        license="Free for personal/commercial use", author="Community pack",
        distributable=True, tags=["topdown", "vehicle", "car", "racing"]),
    "kenney_nature-kit": dict(
        title="Kenney Nature Kit (3D)", kind="3d",
        license="CC0 1.0", author="Kenney", distributable=True,
        tags=["3d", "nature", "lowpoly", "model", "texture"]),
    "kenney_particle-pack": dict(
        title="Kenney Particle Pack", kind="2d",
        license="CC0 1.0", author="Kenney", distributable=True,
        tags=["particles", "fx", "fire", "smoke", "magic"]),
    "kenney_pixel-ui-pack": dict(
        title="Kenney Pixel UI Pack", kind="2d",
        license="CC0 1.0", author="Kenney", distributable=True,
        tags=["ui", "pixel", "9-slice", "buttons"]),
    "kenney_roguelike-characters": dict(
        title="Kenney Roguelike Characters", kind="2d",
        license="CC0 1.0", author="Kenney", distributable=True,
        tags=["pixel", "roguelike", "character", "tileset"]),
    "kenney_ui-pack": dict(
        title="Kenney UI Pack", kind="2d",
        license="CC0 1.0", author="Kenney", distributable=True,
        tags=["ui", "buttons", "icons", "audio", "font", "vector"]),
    "motor bikes": dict(
        title="Motor Bikes", kind="2d",
        license="Free for personal/commercial use", author="Community pack",
        distributable=True, tags=["vehicle", "bike", "racing"]),
    "school bus": dict(
        title="School Bus", kind="2d",
        license="Free for personal/commercial use", author="Community pack",
        distributable=True, tags=["vehicle", "bus"]),
    "swat van": dict(
        title="SWAT Van", kind="2d",
        license="Free for personal/commercial use", author="Community pack",
        distributable=True, tags=["vehicle", "van"]),
    "tinyRPG_dragonRegaliaGUI_v1_0": dict(
        title="Dragon Regalia GUI", kind="2d",
        license="Free for personal/commercial use", author="Dragon Regalia",
        distributable=True, tags=["ui", "gui", "rpg", "hud", "frames"]),
}

# --------------------------------------------------------------------------- #
# Classification
# --------------------------------------------------------------------------- #
IMAGE_EXT = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tga"}
AUDIO_EXT = {".wav", ".ogg", ".mp3", ".m4a"}
MODEL_EXT = {".obj", ".glb", ".gltf", ".fbx", ".dae", ".stl"}
FONT_EXT = {".ttf", ".otf"}
PREVIEW_EXT = {".gif"}
DOC_EXT = {".txt", ".md", ".pdf", ".html"}
SKIP_EXT = {".aseprite", ".ase", ".ai", ".svg", ".url", ".unitypackage", ".blend"}
# For the same model we index one runtime format plus one interchange format.
MODEL_PREFERRED = {".glb", ".gltf", ".obj"}

CATEGORY_ORDER = [
    "Characters", "Enemies", "Tilesets", "UI", "Particles", "Props",
    "Backgrounds", "Vehicles", "3D Models", "Textures", "Audio", "Fonts",
    "Documents", "Previews",
]


def classify(rel: str, ext: str) -> str:
    """Map a file to one of CATEGORY_ORDER using path keywords."""
    p = rel.lower()
    if ext in AUDIO_EXT:
        return "Audio"
    if ext in FONT_EXT:
        return "Fonts"
    if ext in MODEL_EXT:
        return "3D Models"
    if ext in DOC_EXT:
        return "Documents"
    if ext in PREVIEW_EXT:
        return "Previews"
    if any(k in p for k in ("particle", "/fx", "flame", "fire_0", "smoke", "muzzle",
                            "spark", "scratch", "slash", "magic", "light_", "circle_0",
                            "dirt_0", "flare", "smoke_")):
        return "Particles"
    if any(k in p for k in ("ui", "hud", "gui", "button", "/icon", "panel", "9-slice",
                            "border", "menu", "frame", "cursor", "checkbox", "slider",
                            "arrow", "badge", "dfgui")):
        return "UI"
    if any(k in p for k in ("tile",)):
        return "Tilesets"
    if any(k in p for k in ("background", "parallax", "/sky", "mockup", "cover",
                            "mountain", "screenshot")):
        return "Backgrounds"
    if any(k in p for k in ("vehicle", "car", "bike", "motor", "bus", "van", "tank",
                            "plane", "truck", "wheel")):
        return "Vehicles"
    if any(k in p for k in ("monster", "enemy", "enemies", "/mob", "skeleton", "vampire",
                            "demon", "slime", "boar", "blood_monster", "zombie", "boss")):
        return "Enemies"
    if any(k in p for k in ("character", "hero", "player", "human", "knight", "soldier",
                            "priest", "npc", "roguelike", "memao", "man", "woman",
                            "warrior", "mage")):
        return "Characters"
    if any(k in p for k in ("prop", "object", "barrel", "crate", "chest", "coin", "gem",
                            "item", "potion", "weapon", "tool", "food", "plant", "tree",
                            "rock", "nature", "grass", "bush", "flower", "mushroom",
                            "fence", "sign", "torch", "campfire", "building", "house",
                            "hive", "interior")):
        return "Props"
    return "Textures"


# --------------------------------------------------------------------------- #
# Image / sprite-sheet profiling
# --------------------------------------------------------------------------- #
FRAME_SIZES = [16, 24, 32, 40, 48, 56, 64, 80, 96, 100, 112, 128, 144, 160, 192, 224, 256]


def png_dimensions(path: str):
    """Read width/height straight out of the IHDR chunk (no decoder needed)."""
    try:
        with open(path, "rb") as f:
            head = f.read(33)
    except OSError:
        return None
    if head[:8] != b"\x89PNG\r\n\x1a\n" or head[12:16] != b"IHDR":
        return None
    w, h = struct.unpack(">II", head[16:24])
    return w, h


def detect_sheet(path: str, w: int, h: int):
    """
    Recover the sprite-sheet grid from the alpha channel.

    A candidate (fw, fh) is *admissible* only when every interior grid line is a
    fully transparent column/row.  Admissible grids are ambiguous -- any integer
    multiple of the true frame size is also admissible -- so we break the tie
    with a content test: in the correct grid each frame's ink is inset from the
    frame's left and right edges, while an over-large frame makes the ink touch
    both edges and an under-large frame leaves whole frames blank.
    """
    try:
        from PIL import Image
    except ImportError:
        return guess_sheet(w, h)
    if w * h > 4_000_000:      # backdrops/posters: not sprite grids
        return None
    try:
        Image.MAX_IMAGE_PIXELS = None
        im = Image.open(path).convert("RGBA")
        alpha = im.getchannel("A")
        if alpha.getbbox() is None:
            return None
    except Exception:
        return guess_sheet(w, h)

    # Column/row emptiness is tested with C-level crops, not a Python pixel loop,
    # and memoised because many candidate grids share boundary lines.
    _cols, _rows, _frames = {}, {}, {}

    def col_empty(x: int) -> bool:
        v = _cols.get(x)
        if v is None:
            v = alpha.crop((x, 0, x + 1, h)).getbbox() is None
            _cols[x] = v
        return v

    def row_empty(y: int) -> bool:
        v = _rows.get(y)
        if v is None:
            v = alpha.crop((0, y, w, y + 1)).getbbox() is None
            _rows[y] = v
        return v

    def frame_box(cx: int, cy: int, fw: int, fh: int):
        key = (cx * fw, cy * fh, cx * fw + fw, cy * fh + fh)
        if key not in _frames:
            _frames[key] = alpha.crop(key).getbbox()
        return _frames[key]

    # ---- Pass 1: frame height.
    # Any integer multiple of the true height is also admissible, so take the
    # SMALLEST admissible fh whose row bands all contain ink.  (Height cannot be
    # disambiguated by a padding test: a sprite legitimately sits on the floor of
    # its frame, so "ink touches the bottom edge" carries no signal.)
    fh = None
    for cand_h in FRAME_SIZES:
        if h % cand_h:
            continue
        rows = h // cand_h
        if rows > 1 and not all(row_empty(cand_h * j) for j in range(1, rows)):
            continue
        if rows > 1 and any(frame_box(0, r, w, cand_h) is None for r in range(rows)):
            continue
        fh = cand_h
        break
    if fh is None:
        return None
    rows = h // fh

    # ---- Pass 2: frame width, scored on horizontal padding.
    best = None
    for fw in FRAME_SIZES:
        if w % fw:
            continue
        cols = w // fw
        if cols * rows < 2:
            continue
        if cols > 1 and not all(col_empty(fw * i) for i in range(1, cols)):
            continue
        padded = inked = 0
        for cy in range(rows):
            for cx in range(cols):
                box = frame_box(cx, cy, fw, fh)
                if box is None:
                    continue
                inked += 1
                if box[0] > 0 and box[2] < fw:
                    padded += 1
        if inked == 0:
            continue
        blank = (cols * rows - inked) / (cols * rows)
        score = padded / inked - 2.0 * blank
        # Strict > keeps the first (smallest) fw on ties.
        if best is None or score > best[0]:
            best = (score, fw, cols)
    if best is None:
        return None
    _, fw, cols = best
    return {"frameW": fw, "frameH": fh, "cols": cols, "rows": rows,
            "frames": cols * rows, "guess": False}


def apply_frame_family(files):
    """
    Reconcile sprite-sheet grids inside one directory family.

    A character exported as ``Hero_Idle-Sheet.png`` / ``Hero_Attack-Sheet.png``
    / ``Hero-Sheet.png`` (the master) shares one frame size.  The master often
    defeats the padding test -- when the sprite fills its frame there is no
    padding left to measure -- so we let the majority vote of its siblings win,
    but only when that frame size divides the master's dimensions exactly.

    Scoped to a directory on purpose: packs routinely mix several frame sizes
    (Legacy Fantasy ships 48x32 tiles next to 80x80 characters), so a
    pack-wide vote would corrupt correct data.
    """
    families = defaultdict(list)
    for f in files:
        if f.get("sheet") and not f["sheet"].get("guess"):
            families[os.path.dirname(f["path"])].append(f)
    fixed = 0
    for group in families.values():
        if len(group) < 3:
            continue
        votes = Counter((f["sheet"]["frameW"], f["sheet"]["frameH"]) for f in group)
        (fw, fh), count = votes.most_common(1)[0]
        if count < 2:
            continue
        for f in group:
            s = f["sheet"]
            if (s["frameW"], s["frameH"]) == (fw, fh):
                continue
            w, h = f.get("w", 0), f.get("h", 0)
            if not w or not h or w % fw or h % fh:
                continue
            if (w // fw) * (h // fh) < 2:
                continue
            s.update(frameW=fw, frameH=fh, cols=w // fw, rows=h // fh,
                     frames=(w // fw) * (h // fh))
            fixed += 1
    return fixed


def guess_sheet(w: int, h: int):
    """Pillow-less fallback: square frames sized off the shorter edge."""
    if h <= 32 and w % h == 0 and w // h >= 2:
        return {"frameW": h, "frameH": h, "cols": w // h, "rows": 1,
                "frames": w // h, "guess": True}
    for fw in FRAME_SIZES:
        if w % fw == 0 and h % fw == 0 and (w // fw) * (h // fw) >= 2:
            return {"frameW": fw, "frameH": fw, "cols": w // fw, "rows": h // fw,
                    "frames": (w // fw) * (h // fw), "guess": True}
    return None


# --------------------------------------------------------------------------- #
# Extraction
# --------------------------------------------------------------------------- #
def safe_member_name(name: str):
    """Normalise a zip entry: Windows separators, absolute paths, '..' escapes."""
    n = name.replace("\\", "/")
    parts = [p for p in n.split("/") if p not in ("", ".", "..")]
    if not parts:
        return None
    if parts[0] == "__MACOSX":
        return None
    if parts[-1].startswith("._") or parts[-1] == ".DS_Store" or parts[-1].endswith("~"):
        return None
    return "/".join(parts)


def slugify(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "_", text.lower()).strip("_")
    return s or "pack"


def strip_root(files):
    """
    Most packs wrap everything in a single top-level folder whose name repeats
    the zip name.  Drop it so store paths stay short.
    """
    roots = {f.split("/", 1)[0] for f in files}
    if len(roots) == 1 and all("/" in f for f in files):
        root = roots.pop()
        return [f[len(root) + 1:] for f in files], root
    return files, None


# --------------------------------------------------------------------------- #
def scan_pack(zip_path: str, pack_id: str, cache: str, use_pil: bool):
    meta = PACK_META.get(os.path.splitext(os.path.basename(zip_path))[0], {})
    entries = []
    with zipfile.ZipFile(zip_path) as zf:
        infos = [i for i in zf.infolist() if not i.is_dir()]
        names = []
        for i in infos:
            n = safe_member_name(i.filename)
            if n:
                names.append((i, n))
        rels, dropped_root = strip_root([n for _, n in names])
        rel_map = {i.filename: r for (i, _), r in zip(names, rels)}

        out_dir = os.path.join(cache, pack_id)
        if os.path.isdir(out_dir):
            shutil.rmtree(out_dir)

        # Alternate model formats are collected in a first pass, keyed by
        # BASENAME.  Two details matter:
        #   * inline collection would miss every format whose name sorts after
        #     the preferred one ("OBJ format/x.obj" is read before
        #     "STL format/x.stl"), which is how altFormats ended up empty;
        #   * the key cannot be the full path, because each format lives in its
        #     own sibling folder -- "Models/OBJ format/bed.obj" and
        #     "Models/STL format/bed.stl" share nothing but the basename.
        # Basenames are unique within a pack's model set (verified: 329 models,
        # one entry each in DAE/FBX/GLTF/OBJ/STL).
        model_groups = defaultdict(list)
        for _, n in names:
            e = os.path.splitext(n)[1].lower()
            if e in MODEL_EXT and e not in MODEL_PREFERRED:
                model_groups[os.path.splitext(os.path.basename(n))[0]].append(e)

        for info, _ in names:
            rel = rel_map[info.filename]
            ext = os.path.splitext(rel)[1].lower()
            if ext in SKIP_EXT:
                continue
            if ext in MODEL_EXT and ext not in MODEL_PREFERRED:
                continue
            dest = os.path.join(out_dir, rel)
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with zf.open(info) as src, open(dest, "wb") as dst:
                shutil.copyfileobj(src, dst, 1 << 16)
            entries.append((rel, ext, os.path.getsize(dest)))

    files = []
    for rel, ext, size in sorted(entries):
        rec = {
            "path": rel,
            "name": os.path.basename(rel),
            "ext": ext[1:],
            "bytes": size,
        }
        cat = classify(rel, ext)
        if ext in IMAGE_EXT:
            rec["kind"] = "image"
            dims = png_dimensions(os.path.join(cache, pack_id, rel)) if ext == ".png" else None
            if dims:
                rec["w"], rec["h"] = dims
                w, h = dims
                if max(w, h) >= 32:
                    sheet = (detect_sheet(os.path.join(cache, pack_id, rel), w, h)
                             if use_pil else guess_sheet(w, h))
                    if sheet and sheet["frames"] >= 2:
                        rec["sheet"] = sheet
            else:
                rec["kind"] = "image"
        elif ext in AUDIO_EXT:
            rec["kind"] = "audio"
        elif ext in MODEL_EXT:
            rec["kind"] = "model"
            alts = sorted({a[1:] for a in
                           model_groups.get(os.path.splitext(os.path.basename(rel))[0], [])})
            if alts:
                rec["altFormats"] = alts
        elif ext in FONT_EXT:
            rec["kind"] = "font"
        elif ext in PREVIEW_EXT:
            rec["kind"] = "preview"
        elif ext in DOC_EXT:
            rec["kind"] = "doc"
        else:
            rec["kind"] = "other"
        rec["category"] = cat
        files.append(rec)

    reconciled = apply_frame_family(files)

    cats = Counter(f["category"] for f in files)
    pack = {
        "id": pack_id,
        "title": meta.get("title", os.path.basename(zip_path)),
        "zip": os.path.basename(zip_path),
        # Safe filename used inside the APK.  Original pack names carry spaces,
        # commas and exclamation marks, which are awkward as Android asset names;
        # the packaged name is always "<id>.zip".
        "zipAsset": pack_id + ".zip",
        "zipBytes": os.path.getsize(zip_path),
        "kind": meta.get("kind", "2d"),
        "license": meta.get("license", "Unknown - review before shipping"),
        "author": meta.get("author", "Unknown"),
        "distributable": meta.get("distributable", True),
        "tags": meta.get("tags", []),
        "fileCount": len(files),
        "bytes": sum(f["bytes"] for f in files),
        "categories": dict(sorted(cats.items(), key=lambda kv: -kv[1])),
        "files": files,
    }
    if reconciled:
        pack["sheetsReconciled"] = reconciled
    return pack, dropped_root


def main() -> int:
    ap = argparse.ArgumentParser(description="Build the S Engine offline asset store manifest.")
    default_repo = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap.add_argument("--repo", default=default_repo)
    ap.add_argument("--cache", default=None, help="scratch extraction dir (default .asset-cache/)")
    ap.add_argument("--no-pil", action="store_true", help="skip Pillow sheet analysis")
    ap.add_argument("--force", action="store_true",
                    help="overwrite a pixel-verified manifest with guessed grids")
    ap.add_argument("--out", default=None)
    ap.add_argument("--keep-cache", action="store_true")
    args = ap.parse_args()

    repo = os.path.abspath(args.repo)
    src_dir = os.path.join(repo, "Assets")
    cache = args.cache or os.path.join(repo, ".asset-cache")
    out_path = args.out or os.path.join(repo, "app/src/main/assets/store/store_manifest.json")

    try:
        import PIL  # noqa: F401
        use_pil = not args.no_pil
    except ImportError:
        use_pil = False
    if not use_pil:
        # Without Pillow every grid is a guess, and a guess is worse than nothing:
        # the games and the validator trust these numbers.  Regenerating over a
        # manifest that was built with real pixel analysis would silently replace
        # verified grids with heuristics, so refuse unless asked to.
        verified = 0
        if os.path.exists(out_path):
            try:
                with open(out_path, encoding="utf-8") as f:
                    prev = json.load(f)
                verified = sum(1 for pk in prev.get("packs", [])
                               for fl in pk.get("files", [])
                               if fl.get("sheet") and not fl["sheet"].get("guess"))
            except Exception:
                verified = 0
        if verified and not args.force:
            print(f"error: {out_path} already holds {verified} pixel-verified sprite "
                  f"grids,\n       and Pillow is unavailable so this run could only "
                  f"guess them.\n"
                  f"       Install Pillow, or pass --force to overwrite anyway.",
                  file=sys.stderr)
            return 1
        print("note: Pillow unavailable - sprite-sheet grids will be heuristically guessed")

    zips = sorted(f for f in os.listdir(src_dir) if f.lower().endswith(".zip"))
    if not zips:
        print(f"error: no ZIPs found in {src_dir}", file=sys.stderr)
        return 1

    packs = []
    for z in zips:
        pid = slugify(os.path.splitext(z)[0])
        try:
            pack, dropped = scan_pack(os.path.join(src_dir, z), pid, cache, use_pil)
        except Exception as e:  # a broken pack must not kill the build
            print(f"  SKIP {z}: {e}", file=sys.stderr)
            continue
        packs.append(pack)
        note = "" if pack["distributable"] else "  [RESTRICTED LICENCE - not bundled]"
        print(f"  {pid:<58} {pack['fileCount']:>5} files  {pack['bytes']/1048576:6.1f} MB{note}")
        if dropped:
            print(f"      (stripped wrapper folder '{dropped}/')")
        if pack.get("sheetsReconciled"):
            print(f"      (frame-family vote fixed {pack['sheetsReconciled']} master sheet grids)")

    packs.sort(key=lambda p: p["id"])
    totals = {
        "packs": len(packs),
        "bundledPacks": sum(1 for p in packs if p["distributable"]),
        "files": sum(p["fileCount"] for p in packs),
        "bytes": sum(p["bytes"] for p in packs),
        "zipBytes": sum(p["zipBytes"] for p in packs),
    }
    for kind in ("image", "audio", "model", "font"):
        totals[kind + "s"] = sum(sum(1 for f in p["files"] if f["kind"] == kind) for p in packs)
    totals["sheets"] = sum(sum(1 for f in p["files"] if "sheet" in f) for p in packs)

    cat_totals = Counter()
    for p in packs:
        cat_totals.update(p["categories"])

    manifest = {
        "schema": SCHEMA_VERSION,
        "engine": "S Engine 2.0 - Android",
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "generator": "tools/build_asset_store.py",
        "storeRoot": "store",
        "notes": [
            "Packs ship as ZIPs inside the APK (assets/ == repo Assets/).",
            "A pack is extracted to filesDir/store/<id>/ on first use, then reused.",
            "distributable=false packs are indexed for user-imported content only.",
        ],
        "totals": totals,
        "categories": CATEGORY_ORDER,
        "categoryTotals": dict(sorted(cat_totals.items(), key=lambda kv: -kv[1])),
        "packs": packs,
    }

    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(manifest, f, separators=(",", ":"), ensure_ascii=False)
    size = os.path.getsize(out_path)
    print(f"\nwrote {os.path.relpath(out_path, repo)}  ({size/1024:.0f} KB)")
    print(f"  packs={totals['packs']} (bundled {totals['bundledPacks']})  "
          f"files={totals['files']}  images={totals['images']}  models={totals['models']}  "
          f"audio={totals['audios']}  sheets={totals['sheets']}")

    write_pack_map(repo, manifest)
    write_catalog(repo, manifest)
    write_game_index(repo)
    if not args.keep_cache:
        shutil.rmtree(cache, ignore_errors=True)
    return 0


def write_pack_map(repo: str, m: dict):
    """
    Tab-separated "original zip name<TAB>packaged zip name" map.

    Gradle reads this to copy Assets/*.zip into the APK under safe names
    (see the stageStoreAssets task in app/build.gradle.kts).  Keeping the map
    in a plain text file means the build script needs no JSON parser.
    """
    out = os.path.join(repo, "app/src/main/assets/store/pack_map.txt")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    lines = ["# generated by tools/build_asset_store.py - do not edit",
             "# original zip in Assets/ <TAB> filename packaged into the APK"]
    for p in m["packs"]:
        lines.append(f"{p['zip']}\t{p['zipAsset']}")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote {os.path.relpath(out, repo)}")


def write_catalog(repo: str, m: dict):
    out = os.path.join(repo, "docs/asset-store/CATALOG.md")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    t = m["totals"]
    L = []
    L.append("# Local Asset Store - Catalog")
    L.append("")
    L.append("_Generated by `tools/build_asset_store.py`. Do not edit by hand; "
             "re-run the tool after adding a ZIP to `Assets/`._")
    L.append("")
    L.append(f"Generated: {m['generatedAt']}  |  manifest schema v{m['schema']}")
    L.append("")
    L.append("| | |")
    L.append("|---|---|")
    L.append(f"| Packs indexed | **{t['packs']}** ({t['bundledPacks']} shippable) |")
    L.append(f"| Files indexed | **{t['files']}** |")
    L.append(f"| Images | {t['images']} |")
    L.append(f"| Sprite sheets auto-sliced | {t['sheets']} |")
    L.append(f"| 3D models | {t['models']} |")
    L.append(f"| Audio clips | {t['audios']} |")
    L.append(f"| Fonts | {t['fonts']} |")
    L.append(f"| Extracted payload | {t['bytes']/1048576:.1f} MB |")
    L.append(f"| Shipped ZIP payload | {t['zipBytes']/1048576:.1f} MB |")
    L.append("")
    L.append("## Categories")
    L.append("")
    L.append("| Category | Files |")
    L.append("|---|---|")
    for k, v in m["categoryTotals"].items():
        L.append(f"| {k} | {v} |")
    L.append("")
    L.append("## Packs")
    L.append("")
    for p in m["packs"]:
        flag = "" if p["distributable"] else " :no_entry_sign: **restricted - not bundled**"
        L.append(f"### {p['title']}{flag}")
        L.append("")
        L.append(f"`{p['id']}` - {p['zip']}  |  {p['fileCount']} files, "
                 f"{p['bytes']/1048576:.1f} MB  |  **{p['license']}**  |  {p['author']}")
        L.append("")
        L.append("Tags: " + ", ".join(f"`{t_}`" for t_ in p["tags"]))
        L.append("")
        L.append("| Category | Count |")
        L.append("|---|---|")
        for k, v in p["categories"].items():
            L.append(f"| {k} | {v} |")
        L.append("")
        sample = [f for f in p["files"] if f["kind"] == "image"][:12]
        if sample:
            L.append("Sample entries:")
            L.append("")
            L.append("```")
            for f in sample:
                dims = f"{f['w']}x{f['h']}" if "w" in f else "?"
                sh = ""
                if "sheet" in f:
                    s = f["sheet"]
                    sh = f"  [{s['frameW']}x{s['frameH']} x{s['frames']}" + \
                         (" (guessed)" if s.get("guess") else "") + "]"
                L.append(f"{f['path']}  {dims}{sh}")
            L.append("```")
            L.append("")
    with open(out, "w", encoding="utf-8") as f:
        f.write("\n".join(L) + "\n")
    print(f"wrote {os.path.relpath(out, repo)}")


def write_game_index(repo: str):
    """
    Ordered list of shipped games, read by GameLibrary at runtime.

    Generated from the games/ directory rather than maintained by hand, so the
    index cannot drift from what is actually present.  Gradle copies games/ into
    the APK verbatim, which carries this file with it.
    """
    root = os.path.join(repo, "games")
    if not os.path.isdir(root):
        return
    ids = []
    for name in sorted(os.listdir(root)):
        gj = os.path.join(root, name, "game.json")
        if not os.path.isfile(gj):
            continue
        try:
            with open(gj, encoding="utf-8") as f:
                meta = json.load(f)
        except Exception as e:
            print(f"WARNING: {name}/game.json unreadable: {e}")
            continue
        gid = meta.get("id") or name
        if gid != name:
            print(f"WARNING: games/{name}/game.json id '{gid}' != directory name")
        ids.append(name)
    out = os.path.join(root, "index.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(ids, f, indent=2)
        f.write("\n")
    print(f"wrote {os.path.relpath(out, repo)}  ({len(ids)} games: {', '.join(ids)})")



if __name__ == "__main__":
    sys.exit(main())


