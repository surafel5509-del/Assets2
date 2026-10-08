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


def check_scene(scene_path, scripts_dir, rep, gid, declared_scripts):
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
            if ctype == "Script":
                # Props serialise under their display names, so the script
                # filename is at "Script", not "script".
                s = c.get("Script", "")
                base = s if s.endswith(".js") else s + ".js"
                declared_scripts.add(base)
                rep.ok(os.path.exists(os.path.join(scripts_dir, base)),
                       f"{where}: script '{s}' has no file in scripts/")
    # Parent references must resolve.
    for o in objects:
        p = o.get("parent")
        if p is not None:
            rep.ok(p in ids, f"{gid}/{name}: object '{o.get('name')}' has unknown parent id {p}")


JS_CALL_RE = re.compile(r"""audio\.play\(\s*['"]([^'"]+)['"]""")


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
