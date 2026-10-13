#!/usr/bin/env python3
"""Kenney Nature Kit helpers: model metadata, palette, and material-derived tints.

Why this file exists
--------------------
`Meshes.loadObj` -- the engine's only 3D model path -- reads v/vt/vn/f and
nothing else.  It ignores `mtllib`/`usemtl`, so every face of a model ends up in
one mesh drawn with a single `MeshRenderer.Color`, and it normalises the model to
a unit cube centred on the origin (see `tools/preview3d.py`, which reproduces the
same semantics).

That means two things matter when authoring a 3D scene out of this kit:

  1. Scale.  A model is one unit across after loading, whatever it was in the
     source file, so the scene has to size every object explicitly -- and a
     ground prop sits centred on its origin, i.e. half a unit buried, so props
     are placed at y = size / 2.

  2. Colour.  Since only one colour survives per object, the honest choice is the
     colour of the material that dominates the model's faces -- the trunk of a
     tree is a small minority of its triangles, the canopy is most of them.  This
     module reads that from the .mtl files in the pack so the tints are the kit's
     own palette rather than invented numbers, and `SCULPT` below lists the
     families that survive single-colour rendering cleanly.

`dominant()` is a derivation, not a guess: it counts faces per `usemtl` group.
"""

import collections
import json
import os
import re
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PACK_ID = "kenney_nature_kit"
OBJ_DIR = "Models/OBJ format"


def _manifest():
    with open(os.path.join(ROOT, "app/src/main/assets/store/store_manifest.json"),
              encoding="utf-8") as f:
        return json.load(f)


def pack_zip_path():
    for p in _manifest()["packs"]:
        if p["id"] == PACK_ID:
            return os.path.join(ROOT, "Assets", p["zip"])
    raise SystemExit(f"pack {PACK_ID} is not in the store manifest")


def palette():
    """Material name -> #RRGGBB, read from every .mtl in the kit."""
    out = {}
    with zipfile.ZipFile(pack_zip_path()) as z:
        for name in z.namelist():
            if not name.endswith(".mtl"):
                continue
            cur = None
            for line in z.read(name).decode("utf-8", "replace").splitlines():
                p = line.split()
                if len(p) == 2 and p[0] == "newmtl":
                    cur = p[1]
                elif len(p) == 4 and p[0] == "Kd" and cur:
                    rgb = [int(round(min(1.0, max(0.0, float(v))) * 255)) for v in p[1:]]
                    out[cur] = "#%02X%02X%02X" % tuple(rgb)
    return out


def model_index():
    """model name -> {"faces": n, "materials": {mat: faces}, "dominant": mat, "size": (x,y,z)}"""
    out = {}
    with zipfile.ZipFile(pack_zip_path()) as z:
        for name in z.namelist():
            if not name.endswith(".obj") or OBJ_DIR not in name:
                continue
            text = z.read(name).decode("utf-8", "replace")
            cur, faces = None, collections.Counter()
            mn = [1e9] * 3
            mx = [-1e9] * 3
            for line in text.splitlines():
                if line.startswith("usemtl"):
                    cur = line.split()[1]
                elif line.startswith("f "):
                    faces[cur] += 1
                elif line.startswith("v "):
                    p = line.split()
                    for i in range(3):
                        v = float(p[1 + i])
                        mn[i] = min(mn[i], v)
                        mx[i] = max(mx[i], v)
            dom = faces.most_common(1)[0][0] if faces else "_defaultMat"
            key = os.path.basename(name)[:-4]
            out[key] = {
                "faces": sum(faces.values()),
                "materials": dict(faces),
                "dominant": dom,
                "size": tuple(round(mx[i] - mn[i], 4) for i in range(3)),
            }
    return out


# Models that read correctly when the whole mesh takes one colour.  The kit's
# multi-material models are mostly "body + trim" (a rock plus the grass tufts on
# it, a tent plus its poles): those tint to their dominant material and read fine.
# Trees are the exception -- their trunk is `woodBark` and their canopy is 2-4x
# the faces, so a single-colour tree is a green trunk.  The scenes therefore use
# the kit's shrubs, logs, stumps and standing stones for foliage-scale cover and
# place canopied trees only as distant silhouettes.
TREES = tuple(n for n in ("tree_",) if True)


def is_tree(name):
    return name.startswith("tree_")


def model_asset(name, as_=None):
    """A game.json asset entry importing one .obj out of the kit."""
    return {"pack": PACK_ID, "path": f"{OBJ_DIR}/{name}.obj", "as": as_ or (name + ".obj")}


def sized(name, model_index_cache=None):
    """The model's own bounding box from the source file (pre-normalisation).

    Useful for sizing a scene before the loader stretches it to one unit: a tree
    that is 1.9 units tall in the file and 0.6 wide becomes 1 unit tall and ~0.31
    wide in the engine, so passing the ratio to the scale keeps proportions.
    """
    idx = model_index_cache if model_index_cache is not None else model_index()
    return idx[name]["size"]


def _cli():
    idx = model_index()
    pal = palette()
    print(f"{len(idx)} models, {len(pal)} materials")
    fam = collections.Counter(re.split(r"[_A-Z]", n)[0] for n in idx)
    print("families:", dict(fam))
    print("\nsingle-material (render exactly as authored):")
    for n, m in sorted(idx.items()):
        if len(m["materials"]) == 1:
            print(f"   {n:34s} {m['dominant']:14s} {pal.get(m['dominant'], '?')}  faces {m['faces']}")
    print("\ntrees (canopy-dominant):")
    for n, m in sorted(idx.items()):
        if is_tree(n):
            print(f"   {n:34s} dom {m['dominant']:14s} mats {m['materials']}")


if __name__ == "__main__":
    _cli()
