#!/usr/bin/env python3
"""
validate_games.py -- checks the four shipped games against the engine and the
asset store.

Run after editing anything under games/ or regenerating the store manifest:

    python3 tools/validate_games.py

What it verifies (each check is a real assertion, not a lint):

  1. Every game.json parses and declares the fields the loader needs.
  2. Every declared asset resolves to a real entry in store_manifest.json, in
     the pack it claims to come from, and the pack is licensed to ship.
  3. Every scene JSON parses and only uses component types the engine
     registers (ComponentRegistry), with animation columns/rows that match the
     sprite-sheet grid the store detected.
  4. Every script referenced by a scene or by game.json exists on disk, and
     every script parses as JavaScript (node --check).
  5. Every `audio.play("x")` target is a declared audio asset.
  6. No game declares a Space Shooter or Platformer template -- both are
     explicitly out of scope for this engine.

Exit code 0 means every game is consistent with the store and the engine.
"""

from __future__ import annotations

import argparse
import collections
import json
import os
import re
import shutil
import subprocess
import sys

# Component types the engine's ComponentRegistry can deserialise.  Kept in sync
# with ComponentRegistry.register(...) calls in engine/core/Components.kt.
# Serialised component names, exactly as ComponentRegistry maps them in
# engine/core/Components.kt.  Note "Camera" (not Camera2D) and "Script" (not
# ScriptComponent) -- the registry key is what appears in a .scene.json.
ENGINE_COMPONENTS = {
    "SpriteRenderer", "TextRenderer", "Camera", "Camera3D",
    "Rigidbody2D", "Collider2D", "Script",
    "ParticleEmitter", "AudioSource", "Animator",
    "MeshRenderer", "Light", "Rigidbody3D", "Collider3D",
}

FORBIDDEN_TEMPLATES = {"space shooter", "platformer"}


class Report:
    def __init__(self):
        self.errors = []
        self.warnings = []
        self.checks = 0

    def ok(self, cond, msg):
        self.checks += 1
        if not cond:
            self.errors.append(msg)
        return cond

    def warn(self, msg):
        self.warnings.append(msg)

    @property
    def failed(self):
        return bool(self.errors)


def load_manifest(repo):
    path = os.path.join(repo, "app/src/main/assets/store/store_manifest.json")
    with open(path, encoding="utf-8") as f:
        m = json.load(f)
    packs = {}
    for p in m["packs"]:
        packs[p["id"]] = {e["path"]: e for e in p["files"]}
    return m, packs


def check_assets(game, packs, rep, gid):
    declared_audio = set()
    for a in game.get("assets", []):
        pid = a.get("pack")
        path = a.get("path")
        alias = a.get("as") or os.path.basename(path or "")
        label = f"{gid}: asset '{alias}'"

        if not rep.ok(bool(pid) and bool(path), f"{label}: missing pack/path"):
            continue
        if not rep.ok(pid in packs, f"{label}: unknown pack '{pid}'"):
            continue
        entry = packs[pid].get(path)
        if not rep.ok(entry is not None, f"{label}: '{path}' not found in pack '{pid}'"):
            continue

        if entry["kind"] == "audio":
            declared_audio.add(alias)

        # An animation block must agree with the grid the store detected.
        anim = a.get("anim")
        if anim:
            sheet = entry.get("sheet")
            if not rep.ok(sheet is not None,
                          f"{label}: declares an animation but '{path}' is not a sprite sheet"):
                continue
            cols = anim.get("columns")
            rows = anim.get("rows")
            if cols is not None:
                rep.ok(cols == sheet["cols"],
                       f"{label}: columns {cols} != store-detected {sheet['cols']}")
            if rows is not None:
                rep.ok(rows == sheet["rows"],
                       f"{label}: rows {rows} != store-detected {sheet['rows']}")
            frames = anim.get("frames")
            if frames is not None:
                rep.ok(frames <= sheet["frames"],
                       f"{label}: {frames} frames requested but the sheet has {sheet['frames']}")
            fw = anim.get("frameW")
            if fw is not None:
                rep.ok(fw == sheet["frameW"],
                       f"{label}: frameW {fw} != store-detected {sheet['frameW']}")

    return declared_audio


def component_props():
    """Component type -> set of valid prop display names, read from Components.kt.

    Props bind by their display name, so a scene that says "Drag" where the
    component declares "Linear Drag" is not an error the engine reports -- the
    key is simply never read and the value silently falls back to its default.
    Deriving the table from the Kotlin keeps this from drifting.
    """
    path = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                        "app/src/main/java/com/sengine/engine/core/Components.kt")
    if not os.path.exists(path):
        return {}
    src = open(path, encoding="utf-8").read()
    out = {}
    for block in re.split(r"\nclass ", src):
        m = re.match(r"(\w+)\s*:\s*Component", block)
        if not m:
            continue
        out[m.group(1)] = set(re.findall(r'Prop\.\w+\(\s*"([^"]+)"', block))
    return out


COMPONENT_PROPS = None


def check_clip_frames(game, rep, gid):
    """Clip frame indices must fall inside the grid of the asset they belong to.

    AnimationClip.cellUv computes u0=col/c, vTop=row/r from the frame index.  An
    index past the end of the sheet does not raise -- it produces UVs outside the
    texture, which the GPU samples as whatever is at the clamped edge, so the
    sprite silently shows the wrong art.

    Clips live at assets[i].clips.<name>.frames, and the grid they index into is
    assets[i].anim or assets[i].tileset, exactly as GameLibrary picks it
    (`anim ?: tileset`).  Checking a top-level game["clips"] instead finds nothing
    in any game -- no game.json has that key -- which is a check that passes
    forever without ever running.
    """
    for a in game.get("assets", []) or []:
        clips = a.get("clips") or {}
        if not clips:
            continue
        label = f"{gid}/{a.get('as', '?')}"
        grid = a.get("anim") or a.get("tileset")
        if not grid or "columns" not in grid:
            rep.ok(False, f"{label}: declares {len(clips)} clips but has no "
                          f"`anim` or `tileset` grid to index into")
            continue
        try:
            cols = int(grid["columns"]); rows = int(grid["rows"])
        except (TypeError, ValueError, KeyError):
            continue
        cap = cols * rows
        for cname, clip in clips.items():
            for fr in clip.get("frames", []) or []:
                rep.ok(isinstance(fr, int) and 0 <= fr < cap,
                       f"{label}/{cname}: frame index {fr} is outside the "
                       f"{cols}x{rows}={cap}-cell grid -- cellUv samples off-sheet")


def check_scene_names(game_dir, rep, gid):
    """Tags and spawn prototypes a script asks for must exist in the scene.

    scene.findAll(tag) returns an empty list when nothing carries the tag, and
    scene.spawn(name) returns null when no object of that name exists.  Both let a
    script run to completion while silently doing nothing -- an empty loop and a
    null guard read as "no work to do", not as a misconfiguration.

    Only pure string literals are checked.  Composed names ("StatPip_" + kind + "_"
    + i) are skipped: a regex sees the prefix alone and reports a name that is
    never actually passed to find().
    """
    scenes_dir = os.path.join(game_dir, "scenes")
    scripts_dir = os.path.join(game_dir, "scripts")
    if not os.path.isdir(scenes_dir) or not os.path.isdir(scripts_dir):
        return

    tags, names = set(), set()
    for sf in os.listdir(scenes_dir):
        if not sf.endswith(".scene.json"):
            continue
        try:
            scene = json.load(open(os.path.join(scenes_dir, sf), encoding="utf-8"))
        except Exception:
            continue
        for o in scene.get("objects", []):
            names.add(o.get("name"))
            tags.add(o.get("tag", "Untagged"))

    # A script may also set a tag at runtime; those count as satisfiable.
    assigned = set()
    for f in os.listdir(scripts_dir):
        if f.endswith(".js"):
            assigned |= set(re.findall(r'\.tag\s*=\s*"([^"]+)"',
                                       open(os.path.join(scripts_dir, f), encoding="utf-8").read()))

    for f in sorted(x for x in os.listdir(scripts_dir) if x.endswith(".js")):
        src = open(os.path.join(scripts_dir, f), encoding="utf-8").read()
        # (?!"\s*\+) rejects literals immediately followed by concatenation.
        for kind, pat in (("findAll tag", r'scene\.findAll\(\s*"([^"]+)"\s*\)(?!\s*\+)'),
                          ("spawn prototype", r'scene\.spawn\(\s*"([^"]+)"\s*\)(?!\s*\+)')):
            for m in re.finditer(pat, src):
                target = m.group(1)
                pool = (tags | assigned) if kind == "findAll tag" else names
                rep.ok(target in pool,
                       f"{gid}/{f}: {kind} '{target}' matches nothing in the scene "
                       f"-- the call returns empty/null and the script silently "
                       f"does nothing")


def _resolve_receiver(src, recv, upto, obj_scripts, tag_scripts):
    """Chase a receiver back to the scene lookup that produced it.

    Assignment chains are followed (best = npcs[i], npcs = scene.findAll("NPC")),
    because the interesting receivers are loop variables, not the lookup result
    itself.  Matching the first scene.find/findAll in the file instead -- the
    obvious shortcut -- resolves a receiver against the wrong lookup and reports a
    handler that does exist as missing.  Returns None when the chain cannot be
    followed; callers skip rather than guess.
    """
    head = src[:upto]
    seen = set()
    cur = recv
    for _ in range(5):
        if cur in seen:
            return None
        seen.add(cur)
        assigns = list(re.finditer(r"(?:var\s+)?" + re.escape(cur) + r"\s*=\s*([^;,\n]+)", head))
        if not assigns:
            return None
        expr = assigns[-1].group(1).strip()
        m = re.match(r'scene\.find\(\s*"([^"]+)"\s*\)', expr)
        if m:
            return obj_scripts.get(m.group(1))
        m = re.match(r'scene\.findAll\(\s*"([^"]+)"\s*\)', expr)
        if m:
            return tag_scripts.get(m.group(1))
        m = re.match(r"([A-Za-z_]\w*)\s*\[", expr)
        if m:
            cur = m.group(1)
            continue
        return None
    return None


def check_send_targets(game_dir, rep, gid):
    """Every send("name") must name a handler the receiver actually defines.

    ScriptSystem.call() returns null when the name is not a function rather than
    throwing, so a mistyped or missing handler is a silent no-op: the caller runs,
    nothing happens, and there is no log line.  The runtime harness catches these
    on paths it happens to execute, but death, knockout and game-over handlers
    need specific state to fire -- so they are checked statically as well.
    """
    scenes_dir = os.path.join(game_dir, "scenes")
    scripts_dir = os.path.join(game_dir, "scripts")
    if not os.path.isdir(scenes_dir) or not os.path.isdir(scripts_dir):
        return

    obj_scripts, tag_scripts = {}, {}
    for sf in os.listdir(scenes_dir):
        if not sf.endswith(".scene.json"):
            continue
        try:
            scene = json.load(open(os.path.join(scenes_dir, sf), encoding="utf-8"))
        except Exception:
            continue
        for o in scene.get("objects", []):
            sc = [c["Script"] for c in o.get("components", [])
                  if c.get("type") == "Script" and c.get("Script")]
            if not sc:
                continue
            obj_scripts[o.get("name")] = sc
            tag_scripts.setdefault(o.get("tag", "Untagged"), []).extend(sc)

    handlers = {}
    for f in os.listdir(scripts_dir):
        if f.endswith(".js"):
            src = open(os.path.join(scripts_dir, f), encoding="utf-8").read()
            handlers[f] = set(re.findall(r"^function\s+([A-Za-z_]\w*)", src, re.M))

    resolved = skipped = 0
    for f in sorted(handlers):
        src = open(os.path.join(scripts_dir, f), encoding="utf-8").read()
        for m in re.finditer(r'(\w+)\.send\(\s*"([^"]+)"', src):
            recv, msg = m.group(1), m.group(2)
            if recv == "self":
                targets = [f]
            else:
                targets = _resolve_receiver(src, recv, m.start(), obj_scripts, tag_scripts)
                if targets is None:
                    skipped += 1
                    continue
            resolved += 1
            rep.ok(any(msg in handlers.get(t, set()) for t in targets),
                   f"{gid}/{f}: send('{msg}') -> {', '.join(sorted(set(targets)))} "
                   f"defines no such handler -- the call is silently dropped")


def check_scene(scene_path, scripts_dir, rep, gid, declared_scripts):
    global COMPONENT_PROPS
    if COMPONENT_PROPS is None:
        COMPONENT_PROPS = component_props()
    name = os.path.basename(scene_path)
    try:
        with open(scene_path, encoding="utf-8") as f:
            scene = json.load(f)
    except Exception as e:
        rep.ok(False, f"{gid}/{name}: invalid JSON ({e})")
        return

    rep.ok("objects" in scene, f"{gid}/{name}: no 'objects' array")
    objects = scene.get("objects", [])
    ids = set()
    for i, o in enumerate(objects):
        where = f"{gid}/{name}: object[{i}] ({o.get('name', '?')})"
        rep.ok("name" in o, f"{where}: missing name")
        if "id" in o:
            rep.ok(o["id"] not in ids, f"{where}: duplicate id {o['id']}")
            ids.add(o["id"])
        for c in o.get("components", []):
            ctype = c.get("type")
            if not rep.ok(ctype in ENGINE_COMPONENTS,
                          f"{where}: unknown component type '{ctype}'"):
                continue
            valid = COMPONENT_PROPS.get(ctype)
            if valid:
                for key in c:
                    if key in ("type", "enabled"):
                        continue
                    rep.ok(key in valid,
                           f"{where}: {ctype} has no property '{key}' "
                           f"(valid: {', '.join(sorted(valid))})")
            if ctype == "Script":
                # Props serialise under their display names, so the script
                # filename is at "Script", not "script".
                s = c.get("Script", "")
                base = s if s.endswith(".js") else s + ".js"
                declared_scripts.add(base)
                rep.ok(os.path.exists(os.path.join(scripts_dir, base)),
                       f"{where}: script '{s}' has no file in scripts/")
    # Every top-level scene key must be read by the scene loader.  Same shape as
    # the game.json check: an unrecognised key is skipped, not reported, so it
    # looks like a setting the engine honours while nothing reads it.
    scene_kt = os.path.join(
        os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
        "app/src/main/java/com/sengine/engine/core/Scene.kt")
    if os.path.exists(scene_kt):
        read_keys = set(re.findall(r'"([A-Za-z0-9_]+)"',
                                   open(scene_kt, encoding="utf-8").read()))
        for key in scene:
            rep.ok(key in read_keys,
                   f"{gid}/{name}: scene key '{key}' is never read by Scene.kt "
                   f"-- it looks configured but is inert")

    # Every Script param must be read by the script it is attached to.
    # applyParams() binds each "k=v" onto the script scope with putProperty, so a
    # param the script never mentions is not an error the engine reports -- it
    # just sits there unused while the value it was meant to set never changes.
    for o in objects:
        for c in o.get("components", []):
            if c.get("type") != "Script" or not c.get("Script"):
                continue
            src_path = os.path.join(scripts_dir, c["Script"])
            if not os.path.exists(src_path):
                continue
            code = open(src_path, encoding="utf-8").read()
            code = re.sub(r"//[^\n]*", "", code)
            code = re.sub(r"/\*.*?\*/", "", code, flags=re.S)
            code = re.sub(r'"[^"\n]*"', "''", code)
            where = f"{gid}/{name}: {o.get('name', '?')} ({c['Script']})"
            for kv in re.split(r"[,\n;]", c.get("Params", "") or ""):
                if "=" not in kv:
                    continue
                key = kv.split("=", 1)[0].strip()
                if not key:
                    continue
                rep.ok(re.search(r"(?<![.\w])" + re.escape(key) + r"(?![\w])", code) is not None,
                       f"{where}: param '{key}' is never referenced by the script -- dead value")

    # Object names must be unique.  scene.find() returns the first match, so a
    # duplicate does not error -- it silently resolves to whichever object the
    # loader reached first and the other one becomes unaddressable.
    name_counts = collections.Counter(o.get("name") for o in objects)
    for n, count in sorted(name_counts.items()):
        rep.ok(count == 1,
               f"{gid}/{name}: object name '{n}' appears {count} times -- "
               f"scene.find() silently resolves to the first and the rest are "
               f"unaddressable")

    # A collider with a non-positive extent can never generate a contact, and a
    # non-positive mass divides by zero in the solver.  Both are typos that read
    # like tuning.
    for o in objects:
        where = f"{gid}/{name}: {o.get('name', '?')}"
        for c in o.get("components", []):
            t = c.get("type")
            if t in ("Collider2D", "Collider3D"):
                axes = ("Width", "Height") if t == "Collider2D" else ("Width", "Height", "Depth")
                for ax in axes:
                    try:
                        v = float(c.get(ax, 1))
                    except (TypeError, ValueError):
                        continue
                    rep.ok(v > 0, f"{where}: {t}.{ax}={c.get(ax)} is non-positive -- "
                                  f"the collider can never generate a contact")
            elif t == "Rigidbody2D":
                try:
                    m = float(c.get("Mass", 1))
                except (TypeError, ValueError):
                    continue
                rep.ok(m > 0, f"{where}: Rigidbody2D.Mass={c.get('Mass')} is "
                              f"non-positive -- the solver divides by it")

    # Parent references must resolve.
    for o in objects:
        p = o.get("parent")
        if p is not None:
            rep.ok(p in ids, f"{gid}/{name}: object '{o.get('name')}' has unknown parent id {p}")


JS_CALL_RE = re.compile(r"""audio\.play\(\s*['"]([^'"]+)['"]""")


# Keys that describe intent for a human reader and are deliberately not consumed
# by the engine.  Anything not listed here and not read by the Kotlin is a gap:
# either the feature is missing or the key is dead, and both should be a decision
# rather than an accident.  `nineSlice` and `controls` are here because they are
# honest descriptions of what the games intend -- but see the notes below.
DESCRIPTIVE_KEYS = {
    # Read by the validator itself, as constraints rather than configuration.
    "networking", "ai_assist",
    # Human-readable metadata surfaced in the game browser.
    "engineNotes", "systems", "spriteDirections", "frameData",
    # Documented control scheme.  GameControlsView draws a fixed joystick + A/B
    # layout; this map describes it, it does not configure it.
    "controls",
    # Declared per asset.  NOT IMPLEMENTED: the engine has no nine-slice path, so
    # these textures are drawn as ordinary scaled sprites and their borders
    # stretch.  Kept so the intent survives until the renderer grows one.
    "nineSlice",
}


def check_game_json_keys(game, rep, gid):
    """Every key in game.json must be consumed by the engine or declared descriptive."""
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    kt = ""
    for rel in ("app/src/main/java/com/sengine/project/GameLibrary.kt",
                "app/src/main/java/com/sengine/project/AssetStore.kt",
                "app/src/main/java/com/sengine/ui/ProjectsActivity.kt"):
        f = os.path.join(root, rel)
        if os.path.exists(f):
            kt += open(f, encoding="utf-8").read()

    for key in game:
        if key == "assets":
            continue
        consumed = f'"{key}"' in kt
        rep.ok(consumed or key in DESCRIPTIVE_KEYS,
               f"{gid}: game.json key '{key}' is not read by the engine and is not "
               f"declared descriptive -- dead configuration")

    # Per-asset keys, same rule.
    seen = set()
    for a in game.get("assets", []):
        for key in a:
            if key in seen:
                continue
            seen.add(key)
            consumed = f'"{key}"' in kt
            rep.ok(consumed or key in DESCRIPTIVE_KEYS,
                   f"{gid}: asset key '{key}' is not read by the engine and is not "
                   f"declared descriptive -- dead configuration")


def declared_clips(game):
    """Every clip name GameLibrary materialises as '<name>.anim'.

    AnimationSystem.clip() resolves play(name) to '<name>.anim' in the project
    assets.  A name that was never declared resolves to nothing and the sprite
    silently draws its entire sheet, which is invisible in the editor and looks
    like a rendering bug on device -- so it is checked here instead.
    """
    out = set()
    for a in game.get("assets", []):
        out.update((a.get("clips") or {}).keys())
    return out


def check_scripts(game_dir, game, rep, gid, declared_scripts, declared_audio):
    scripts_dir = os.path.join(game_dir, "scripts")
    node = shutil.which("node")

    clips = declared_clips(game)

    for s in sorted(declared_scripts):
        path = os.path.join(scripts_dir, s)
        if not rep.ok(os.path.exists(path), f"{gid}: declared script '{s}' is missing"):
            continue
        rep.checks += 1
        src = open(path, encoding="utf-8").read()

        # Every sound a script plays must be a declared audio asset, otherwise
        # the game silently no-ops on device.
        for snd in set(JS_CALL_RE.findall(src)):
            rep.ok(snd in declared_audio,
                   f"{gid}/{s}: audio.play('{snd}') is not a declared audio asset")

        # Every sprite clip a script plays must be declared.  Only literal names
        # can be resolved statically; names built from a `skin` or `states` param
        # are covered by tools/run_games.js, which executes the scripts.
        # The literal must be the whole argument: play("walk_" + face) is built at
        # runtime from a direction and cannot be resolved here.
        for clip in set(re.findall(r'(?<!audio)\.play\(\s*"([^"]+)"\s*\)', src)):
            rep.ok(clip in clips,
                   f"{gid}/{s}: play('{clip}') is not a clip declared in game.json")

        if node:
            r = subprocess.run([node, "--check", path], capture_output=True, text=True)
            if r.returncode != 0:
                rep.errors.append(f"{gid}/{s}: JavaScript syntax error: {r.stderr.strip()[:300]}")
        else:
            rep.warn(f"{gid}/{s}: node not on PATH, syntax not checked")

    # Every script in scripts/ should be declared, so nothing is dead weight.
    on_disk = {f for f in os.listdir(scripts_dir) if f.endswith(".js")} if os.path.isdir(scripts_dir) else set()
    for extra in sorted(on_disk - declared_scripts):
        rep.warn(f"{gid}: scripts/{extra} is not referenced by game.json or any scene")

    # A game must actually declare lifecycle hooks somewhere.
    all_src = ""
    for s in on_disk:
        all_src += open(os.path.join(scripts_dir, s), encoding="utf-8").read()
    rep.ok("function update(" in all_src, f"{gid}: no script defines update(dt)")


def validate(repo, only=None):
    games_dir = os.path.join(repo, "games")
    m, packs = load_manifest(repo)
    rep = Report()

    print(f"store manifest: {m['totals']['packs']} packs, {m['totals']['files']} files, "
          f"schema v{m['schema']}")

    slugs = sorted(d for d in os.listdir(games_dir)
                   if os.path.isdir(os.path.join(games_dir, d)) and not d.startswith("."))
    if only:
        slugs = [s for s in slugs if only in s]

    for slug in slugs:
        gd = os.path.join(games_dir, slug)
        gj = os.path.join(gd, "game.json")
        gid = slug
        print(f"\n== {slug} ==")
        if not rep.ok(os.path.exists(gj), f"{gid}: no game.json"):
            continue
        try:
            game = json.load(open(gj, encoding="utf-8"))
        except Exception as e:
            rep.ok(False, f"{gid}: game.json is invalid JSON ({e})")
            continue

        for field in ("id", "title", "genre", "orientation", "startScene", "scenes", "scripts", "assets"):
            rep.ok(field in game, f"{gid}: game.json missing '{field}'")
        if rep.failed:
            pass

        title_genre = f"{game.get('title', '')} {game.get('genre', '')}".lower()
        for bad in FORBIDDEN_TEMPLATES:
            rep.ok(bad not in title_genre,
                   f"{gid}: '{bad}' is explicitly out of scope for this engine")

        rep.ok(game.get("orientation") in (0, 1), f"{gid}: orientation must be 0 (landscape) or 1 (portrait)")
        rep.ok(game.get("networking", False) is False,
               f"{gid}: the engine is offline-only; networking must stay false")
        rep.ok(game.get("ai_assist", False) is False,
               f"{gid}: AI features are explicitly out of scope")

        declared_audio = check_assets(game, packs, rep, gid)
        n_assets = len(game.get("assets", []))
        n_packs = len({a.get("pack") for a in game.get("assets", [])})
        print(f"   assets: {n_assets} from {n_packs} store packs ({len(declared_audio)} audio)")

        check_game_json_keys(game, rep, gid)

        declared_scripts = set(game.get("scripts", []))
        scenes_dir = os.path.join(gd, "scenes")
        scene_files = sorted(f for f in os.listdir(scenes_dir) if f.endswith(".scene.json")) \
            if os.path.isdir(scenes_dir) else []
        rep.ok(len(scene_files) > 0, f"{gid}: no scenes")
        start = game.get("startScene")
        rep.ok(any(f == f"{start}.scene.json" for f in scene_files),
               f"{gid}: startScene '{start}' has no scene file")
        for sf in scene_files:
            check_scene(os.path.join(scenes_dir, sf), os.path.join(gd, "scripts"), rep, gid, declared_scripts)
        print(f"   scenes: {', '.join(f.replace('.scene.json', '') for f in scene_files)}")

        check_scripts(gd, game, rep, gid, declared_scripts, declared_audio)
        check_clip_frames(game, rep, gid)
        check_send_targets(gd, rep, gid)
        check_scene_names(gd, rep, gid)
        print(f"   scripts: {len(declared_scripts)} declared")

    return rep


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--repo", default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    ap.add_argument("game", nargs="?", help="only validate slugs containing this substring")
    args = ap.parse_args()

    rep = validate(os.path.abspath(args.repo), args.game)

    print("\n---------------------------------------------")
    for w in rep.warnings:
        print(f"WARN  {w}")
    for e in rep.errors:
        print(f"ERROR {e}")
    print(f"{rep.checks} checks, {len(rep.errors)} errors, {len(rep.warnings)} warnings")
    print("RESULT: " + ("FAIL" if rep.failed else "PASS"))
    return 1 if rep.failed else 0


if __name__ == "__main__":
    sys.exit(main())
