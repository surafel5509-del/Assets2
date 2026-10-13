#!/usr/bin/env python3
"""Offline 3D renderer for S Engine scenes -- the stand-in for Renderer3D.

There is no GPU, no JDK and no Android SDK in this sandbox, so a 3D scene cannot
be looked at any other way.  This is deliberately *not* a prettier renderer than
the engine's: it reproduces Renderer3D's pipeline step for step, because the
point is to see what the game will look like on the device and to catch art
mistakes (a mesh the size of a house, a cave with no light, a camera under the
terrain) before they ship.

Faithful to the engine means:

  * Geometry -- `Meshes.loadObj` semantics: v/vt/vn/f parsed, polygons fanned,
    per-face geometric normals when a face carries no `vn`, and the model
    normalised to a unit cube centred on its bounding-box centre.  A model is
    therefore one unit across and centred on its object's origin, so ground
    props are placed at y = half their height.
  * Primitives -- the same sizes `Meshes.primitive` builds: cube 1x1x1, sphere
    r=0.5, plane 1x1 at y=0, cylinder h=1 r=0.5, cone, torus R=0.35 r=0.15,
    capsule, pyramid.
  * Shading -- the `meshFs` GLSL in Shaders.kt: flat normal lighting from the
    scene's directional light (or a default sun when there is none), up to four
    nearest point lights with quadratic falloff, Blinn-Phong specular, emission,
    the fog band, and the sky gradient the Camera3D declares.  Alpha < 1 meshes
    draw last, back to front, as the engine's transparent pass does.
  * Camera -- `View3D.setFromWorld` plus `Engine.updateCamera3DFollow`: with
    `--camera cam` the view is exactly what the player sees, eye at the follow
    target plus the camera's offsets, looking at the target.

Usage:
  python3 tools/preview3d.py <game-dir-or-id> --world DUMP.json --out shot.png
      [--size 1280x720] [--camera free|cam] [--yaw 40] [--pitch 24] [--dist 48]
      [--target X,Y,Z] [--eye X,Y,Z] [--lookat X,Y,Z] [--no-sky] [--tint HEX]
"""

import argparse
import json
import math
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from probe_png import Png, write_png  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(os.environ.get("TMPDIR", "/tmp"), "sengine-preview")
MESH_KINDS = ["Cube", "Sphere", "Plane", "Cylinder", "Cone", "Torus", "Capsule",
              "Pyramid", "Custom Model"]


# --------------------------------------------------------------------------- maths


def mat_identity():
    return [1.0 if i % 5 == 0 else 0.0 for i in range(16)]


def mat_mul(a, b):
    out = [0.0] * 16
    for c in range(4):
        for r in range(4):
            out[c * 4 + r] = sum(a[k * 4 + r] * b[c * 4 + k] for k in range(4))
    return out


def mat_trs(px, py, pz, rx, ry, rz, sx, sy, sz):
    """Column-major T * Rz * Ry * Rx * S, matching Mat4.euler in the engine."""
    cx, sx_, cy, sy_, cz, sz_ = (math.cos(math.radians(rx)), math.sin(math.radians(rx)),
                                 math.cos(math.radians(ry)), math.sin(math.radians(ry)),
                                 math.cos(math.radians(rz)), math.sin(math.radians(rz)))
    m = (cz * cy, sz_ * cy, -sy_,
         cz * sy_ * sx_ - sz_ * cx, sz_ * sy_ * sx_ + cz * cx, cy * sx_,
         cz * sy_ * cx + sz_ * sx_, sz_ * sy_ * cx - cz * sx_, cy * cx)
    return [m[0] * sx, m[1] * sx, m[2] * sx, 0.0,
            m[3] * sy, m[4] * sy, m[5] * sy, 0.0,
            m[6] * sz, m[7] * sz, m[8] * sz, 0.0,
            px, py, pz, 1.0]


def mat_point(m, x, y, z):
    return (m[0] * x + m[4] * y + m[8] * z + m[12],
            m[1] * x + m[5] * y + m[9] * z + m[13],
            m[2] * x + m[6] * y + m[10] * z + m[14])


def mat_dir(m, x, y, z):
    return (m[0] * x + m[4] * y + m[8] * z,
            m[1] * x + m[5] * y + m[9] * z,
            m[2] * x + m[6] * y + m[10] * z)


def mat_inverse(m):
    """General 4x4 inverse -- the normal matrix is its transpose, as in the engine."""
    a = m
    inv = [0.0] * 16
    inv[0] = (a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] +
              a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10])
    inv[4] = (-a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] -
              a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10])
    inv[8] = (a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] +
              a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9])
    inv[12] = (-a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] -
               a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9])
    inv[1] = (-a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] -
              a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10])
    inv[5] = (a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] +
              a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10])
    inv[9] = (-a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] -
              a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9])
    inv[13] = (a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] +
               a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9])
    inv[2] = (a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] +
              a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6])
    inv[6] = (-a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] -
              a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6])
    inv[10] = (a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] +
               a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5])
    inv[14] = (-a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] -
               a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5])
    inv[3] = (-a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] -
              a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6])
    inv[7] = (a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] +
              a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6])
    inv[11] = (-a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] -
               a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5])
    inv[15] = (a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] +
               a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5])
    det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12]
    if abs(det) < 1e-12:
        return mat_identity()
    return [v / det for v in inv]


def perspective(fov_deg, aspect, near, far):
    f = 1.0 / math.tan(math.radians(fov_deg) / 2.0)
    return [f / aspect, 0.0, 0.0, 0.0,
            0.0, f, 0.0, 0.0,
            0.0, 0.0, (far + near) / (near - far), -1.0,
            0.0, 0.0, (2 * far * near) / (near - far), 0.0]


def look_at(eye, target, up=(0.0, 1.0, 0.0)):
    fx, fy, fz = (target[0] - eye[0], target[1] - eye[1], target[2] - eye[2])
    fl = math.sqrt(fx * fx + fy * fy + fz * fz) or 1e-6
    fx, fy, fz = fx / fl, fy / fl, fz / fl
    ux, uy, uz = up
    sx, sy, sz = (fy * uz - fz * uy, fz * ux - fx * uz, fx * uy - fy * ux)
    sl = math.sqrt(sx * sx + sy * sy + sz * sz) or 1e-6
    sx, sy, sz = sx / sl, sy / sl, sz / sl
    ux2, uy2, uz2 = (sy * fz - sz * fy, sz * fx - sx * fz, sx * fy - sy * fx)
    return [sx, ux2, -fx, 0.0,
            sy, uy2, -fy, 0.0,
            sz, uz2, -fz, 0.0,
            -(sx * eye[0] + sy * eye[1] + sz * eye[2]),
            -(ux2 * eye[0] + uy2 * eye[1] + uz2 * eye[2]),
            (fx * eye[0] + fy * eye[1] + fz * eye[2]), 1.0]


# --------------------------------------------------------------------------- geometry


def face_normal(a, b, c):
    ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
    vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
    nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
    ln = math.sqrt(nx * nx + ny * ny + nz * nz) or 1e-8
    return (nx / ln, ny / ln, nz / ln)


class Mesh:
    __slots__ = ("verts",)

    def __init__(self, verts):
        self.verts = verts            # triples of [x,y,z, nx,ny,nz, u,v]


def load_obj(text):
    """Meshes.loadObj: v/vt/vn/f, polygons fanned, per-face normals, unit cube."""
    pos, uvs, nrm, out = [], [], [], []
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line[0] == "#":
            continue
        p = line.split()
        if p[0] == "v":
            pos.append((float(p[1]), float(p[2]), float(p[3])))
        elif p[0] == "vt":
            uvs.append((float(p[1]), float(p[2]) if len(p) > 2 else 0.0))
        elif p[0] == "vn":
            nrm.append((float(p[1]), float(p[2]), float(p[3])))
        elif p[0] == "f":
            idx = []
            for v in p[1:]:
                s = v.split("/")

                def ix(str_, size):
                    if not str_:
                        return -1
                    i = int(str_)
                    return size + i if i < 0 else i - 1
                idx.append((ix(s[0], len(pos)),
                            ix(s[1], len(uvs)) if len(s) > 1 else -1,
                            ix(s[2], len(nrm)) if len(s) > 2 else -1))
            for k in range(1, len(idx) - 1):
                tri = (idx[0], idx[k], idx[k + 1])
                fn = face_normal(pos[tri[0][0]], pos[tri[1][0]], pos[tri[2][0]])
                for vi, ti, ni in tri:
                    v = pos[vi]
                    n = nrm[ni] if ni >= 0 else fn
                    uv = uvs[ti] if ti >= 0 else (0.0, 0.0)
                    out.append([v[0], v[1], v[2], n[0], n[1], n[2], uv[0], 1.0 - uv[1]])
    mn = [min(v[i] for v in out) for i in range(3)]
    mx = [max(v[i] for v in out) for i in range(3)]
    size = max(mx[0] - mn[0], mx[1] - mn[1], mx[2] - mn[2]) or 1e-6
    for v in out:
        for k in range(3):
            v[k] = (v[k] - (mn[k] + mx[k]) / 2.0) / size
    return Mesh(out)


def primitive(kind):
    if kind == 1:                                     # sphere(24, 16, 0.5)
        seg, rings, r, out = 24, 16, 0.5, []

        def pt(i, j):
            th, ph = j * math.pi / rings, i * 2 * math.pi / seg
            x, y, z = math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph)
            return [x * r, y * r, z * r, x, y, z, i / seg, j / rings]
        for j in range(rings):
            for i in range(seg):
                out += [pt(i, j), pt(i + 1, j + 1), pt(i + 1, j)]
                out += [pt(i, j), pt(i, j + 1), pt(i + 1, j + 1)]
        return Mesh(out)
    if kind == 2:                                     # plane(), 1x1, y=0
        return Mesh([
            [-0.5, 0, 0.5, 0, 1, 0, 0, 1], [0.5, 0, 0.5, 0, 1, 0, 1, 1],
            [0.5, 0, -0.5, 0, 1, 0, 1, 0],
            [-0.5, 0, 0.5, 0, 1, 0, 0, 1], [0.5, 0, -0.5, 0, 1, 0, 1, 0],
            [-0.5, 0, -0.5, 0, 1, 0, 0, 0]])
    if kind in (3, 4):                                # cylinder / cone, h=1
        seg, h = 24, 0.5
        r_top, r_bottom = (0.5, 0.5) if kind == 3 else (0.0, 0.5)
        slope = r_bottom - r_top
        nl = math.sqrt(1 + slope * slope)
        ny = slope / nl
        out = []
        for i in range(seg):
            a0, a1 = i * 2 * math.pi / seg, (i + 1) * 2 * math.pi / seg
            c0, s0, c1, s1 = math.cos(a0), math.sin(a0), math.cos(a1), math.sin(a1)
            v = [[c0 * r_bottom, -h, s0 * r_bottom, c0 / nl, ny, s0 / nl, i / seg, 1],
                 [c1 * r_top, h, s1 * r_top, c1 / nl, ny, s1 / nl, (i + 1) / seg, 0],
                 [c1 * r_bottom, -h, s1 * r_bottom, c1 / nl, ny, s1 / nl, (i + 1) / seg, 1],
                 [c0 * r_bottom, -h, s0 * r_bottom, c0 / nl, ny, s0 / nl, i / seg, 1],
                 [c0 * r_top, h, s0 * r_top, c0 / nl, ny, s0 / nl, i / seg, 0],
                 [c1 * r_top, h, s1 * r_top, c1 / nl, ny, s1 / nl, (i + 1) / seg, 0]]
            out += v
            if r_top > 0:
                out += [[0, h, 0, 0, 1, 0, 0.5, 0.5],
                        [c1 * r_top, h, s1 * r_top, 0, 1, 0, 0.5 + c1 / 2, 0.5 + s1 / 2],
                        [c0 * r_top, h, s0 * r_top, 0, 1, 0, 0.5 + c0 / 2, 0.5 + s0 / 2]]
            out += [[0, -h, 0, 0, -1, 0, 0.5, 0.5],
                    [c0 * r_bottom, -h, s0 * r_bottom, 0, -1, 0, 0.5 + c0 / 2, 0.5 + s0 / 2],
                    [c1 * r_bottom, -h, s1 * r_bottom, 0, -1, 0, 0.5 + c1 / 2, 0.5 + s1 / 2]]
        return Mesh(out)
    if kind == 5:                                     # torus(28, 14, 0.35, 0.15)
        seg, sides, R, r, out = 28, 14, 0.35, 0.15, []
        for i in range(seg):
            for j in range(sides):
                a0, a1 = i * 2 * math.pi / seg, (i + 1) * 2 * math.pi / seg
                b0, b1 = j * 2 * math.pi / sides, (j + 1) * 2 * math.pi / sides

                def p(a, b):
                    nx, ny, nz = math.cos(a) * math.cos(b), math.sin(b), math.sin(a) * math.cos(b)
                    return [math.cos(a) * R + math.cos(a) * r * math.cos(b),
                            math.sin(a) * R + r * math.sin(b),
                            math.sin(a) * r * math.cos(b), nx, ny, nz, 0, 0]
                out += [p(a0, b0), p(a1, b0), p(a1, b1), p(a0, b0), p(a1, b1), p(a0, b1)]
        return Mesh(out)
    if kind == 6:                                     # capsule(20, 0.25)
        seg, r, out = 20, 0.25, []
        for cyl in (0.5, -0.5):
            for i in range(seg):
                a0, a1 = i * 2 * math.pi / seg, (i + 1) * 2 * math.pi / seg
                c0, s0, c1, s1 = math.cos(a0), math.sin(a0), math.cos(a1), math.sin(a1)
                out += [[c0 * r, cyl, s0 * r, c0, 0, s0, 0, 0],
                        [c1 * r, cyl, s1 * r, c1, 0, s1, 1, 0],
                        [c1 * r, cyl - 0.5 if cyl < 0 else cyl + 0.5, s1 * r, c1, 0, s1, 1, 1]]
                out += [[c0 * r, cyl, s0 * r, c0, 0, s0, 0, 0],
                        [c1 * r, cyl - 0.5 if cyl < 0 else cyl + 0.5, s1 * r, c1, 0, s1, 1, 1],
                        [c0 * r, cyl - 0.5 if cyl < 0 else cyl + 0.5, s0 * r, c0, 0, s0, 0, 1]]
        for hemi, ycen in ((1, 0.5), (-1, -0.5)):
            for j in range(8):
                for i in range(seg):
                    th0, th1 = j * math.pi / 16, (j + 1) * math.pi / 16
                    ph0, ph1 = i * 2 * math.pi / seg, (i + 1) * 2 * math.pi / seg

                    def pt(th, ph):
                        x, y, z = math.sin(th) * math.cos(ph), hemi * math.cos(th), math.sin(th) * math.sin(ph)
                        return [x * r, ycen + y * r, z * r, x, y * hemi, z, 0, 0]
                    out += [pt(th0, ph0), pt(th1, ph1), pt(th1, ph0)]
                    out += [pt(th0, ph0), pt(th0, ph1), pt(th1, ph1)]
        return Mesh(out)
    if kind == 7:                                     # pyramid()
        apex = (0.0, 0.5, 0.0)
        base = [(-0.5, -0.5, 0.5), (0.5, -0.5, 0.5), (0.5, -0.5, -0.5), (-0.5, -0.5, -0.5)]
        out = []
        for i in range(4):
            a, c = base[i], base[(i + 1) % 4]
            n = face_normal(a, c, apex)
            for p, uv in ((a, (0, 1)), (c, (1, 1)), (apex, (0.5, 0))):
                out.append([p[0], p[1], p[2], n[0], n[1], n[2], uv[0], uv[1]])
        for t in ((0, 3, 2), (0, 2, 1)):
            for k in t:
                p = base[k]
                out.append([p[0], p[1], p[2], 0.0, -1.0, 0.0, p[0] + 0.5, p[2] + 0.5])
        return Mesh(out)
    return Mesh([  # 0 -> cube(), 1x1x1 about the origin
        [-0.5, -0.5, 0.5, 0, 0, 1, 0, 1], [0.5, -0.5, 0.5, 0, 0, 1, 1, 1],
        [0.5, 0.5, 0.5, 0, 0, 1, 1, 0],
        [-0.5, -0.5, 0.5, 0, 0, 1, 0, 1], [0.5, 0.5, 0.5, 0, 0, 1, 1, 0],
        [-0.5, 0.5, 0.5, 0, 0, 1, 0, 0],
        [0.5, -0.5, -0.5, 0, 0, -1, 0, 1], [-0.5, -0.5, -0.5, 0, 0, -1, 1, 1],
        [-0.5, 0.5, -0.5, 0, 0, -1, 1, 0],
        [0.5, -0.5, -0.5, 0, 0, -1, 0, 1], [-0.5, 0.5, -0.5, 0, 0, -1, 1, 0],
        [0.5, 0.5, -0.5, 0, 0, -1, 0, 0],
        [-0.5, -0.5, -0.5, -1, 0, 0, 0, 1], [-0.5, -0.5, 0.5, -1, 0, 0, 1, 1],
        [-0.5, 0.5, 0.5, -1, 0, 0, 1, 0],
        [-0.5, -0.5, -0.5, -1, 0, 0, 0, 1], [-0.5, 0.5, 0.5, -1, 0, 0, 1, 0],
        [-0.5, 0.5, -0.5, -1, 0, 0, 0, 0],
        [0.5, -0.5, 0.5, 1, 0, 0, 0, 1], [0.5, -0.5, -0.5, 1, 0, 0, 1, 1],
        [0.5, 0.5, -0.5, 1, 0, 0, 1, 0],
        [0.5, -0.5, 0.5, 1, 0, 0, 0, 1], [0.5, 0.5, -0.5, 1, 0, 0, 1, 0],
        [0.5, 0.5, 0.5, 1, 0, 0, 0, 0],
        [-0.5, 0.5, 0.5, 0, 1, 0, 0, 1], [0.5, 0.5, 0.5, 0, 1, 0, 1, 1],
        [0.5, 0.5, -0.5, 0, 1, 0, 1, 0],
        [-0.5, 0.5, 0.5, 0, 1, 0, 0, 1], [0.5, 0.5, -0.5, 0, 1, 0, 1, 0],
        [-0.5, 0.5, -0.5, 0, 1, 0, 0, 0],
        [-0.5, -0.5, -0.5, 0, -1, 0, 0, 1], [0.5, -0.5, -0.5, 0, -1, 0, 1, 1],
        [0.5, -0.5, 0.5, 0, -1, 0, 1, 0],
        [-0.5, -0.5, -0.5, 0, -1, 0, 0, 1], [0.5, -0.5, 0.5, 0, -1, 0, 1, 0],
        [-0.5, -0.5, 0.5, 0, -1, 0, 0, 0]])


class ModelStore:
    """`Mesh` kind + `Model (.obj)` name -> Mesh, reading OBJ files from the ZIPs."""

    def __init__(self, repo, assets):
        self.repo = repo
        with open(os.path.join(repo, "app/src/main/assets/store/store_manifest.json"),
                  encoding="utf-8") as f:
            manifest = json.load(f)
        self.packs = {p["id"]: p for p in manifest["packs"]}
        self.entries = {}
        for a in assets:
            self.entries[a.get("as") or os.path.basename(a.get("path", ""))] = (
                a.get("pack"), a.get("path"))
        self._prims = {}
        self._models = {}
        self._zipped = {}
        self.missing = set()

    def _zip(self, pack_id):
        if pack_id not in self._zipped:
            p = self.packs[pack_id]
            self._zipped[pack_id] = zipfile.ZipFile(os.path.join(self.repo, "Assets", p["zip"]))
        return self._zipped[pack_id]

    def get(self, kind, model_name):
        if kind != "Custom Model" or not model_name:
            idx = MESH_KINDS.index(kind) if kind in MESH_KINDS else 0
            if idx not in self._prims:
                self._prims[idx] = primitive(idx)
            return self._prims[idx], None
        if model_name in self._models:
            return self._models[model_name]
        mesh, err = None, f"model '{model_name}' is not declared by game.json"
        ent = self.entries.get(model_name)
        if ent and ent[0] in self.packs:
            pack, path = ent
            os.makedirs(CACHE, exist_ok=True)
            local = os.path.join(CACHE, f"{pack}__{path}".replace("/", "_").replace(" ", "_"))
            if not os.path.exists(local):
                z = self._zip(pack)
                try:
                    data = z.read(path)
                except KeyError:
                    hits = [n for n in z.namelist() if n.endswith(path)]
                    data = z.read(hits[0]) if hits else None
                if data is not None:
                    open(local, "wb").write(data)
            if os.path.exists(local):
                mesh = load_obj(open(local, encoding="utf-8", errors="replace").read())
                err = None
        self._models[model_name] = (mesh, err)
        if err:
            self.missing.add(err)
        return mesh, err


# --------------------------------------------------------------------------- render


def parse_color(s, default=(255, 255, 255, 255)):
    if not isinstance(s, str) or not s.startswith("#"):
        return default
    h = s[1:]
    if len(h) == 6:
        h = "FF" + h
    if len(h) != 8:
        return default
    return (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16))


def render(world, models, size, camera_mode, yaw, pitch, dist, target, eye_override,
           lookat_override, sky=True):
    W, H = size
    cv = Png(W, H, [[(0, 0, 0, 255)] * W for _ in range(H)])
    objs = [o for o in world["objects"] if o.get("visible", True)]
    by_id = {o["id"]: o for o in objs}
    stats = {"tris": 0, "raster": 0, "meshes": 0, "skipped": 0}

    def world_matrix(o):
        chain, cur, seen = [], o, set()
        while cur is not None and cur["id"] not in seen:
            seen.add(cur["id"])
            chain.append(cur)
            par = cur.get("parent")
            cur = by_id.get(par) if par not in (None, -1) else None
        m = mat_identity()
        for node in reversed(chain):
            m = mat_mul(m, mat_trs(node["x"], node["y"], node["z"], node.get("rotX", 0),
                                   node.get("rotY", 0), node.get("rotZ", 0),
                                   node.get("scaleX", 1), node.get("scaleY", 1),
                                   node.get("scaleZ", 1)))
        return m

    W4 = {o["id"]: world_matrix(o) for o in objs}

    cam_obj = next((o for o in objs if o.get("camera3d")), None)
    cam = cam_obj["camera3d"] if cam_obj else {}
    fov = cam.get("fov", 60.0)
    near = cam.get("near", 0.1)
    far = cam.get("far", 500.0)
    sky_top = parse_color(cam.get("skyTop", "#FF3B7BD4"))
    sky_hor = parse_color(cam.get("skyHorizon", "#FFBFD8F0"))

    def follow_obj():
        if not cam.get("follow"):
            return None
        return by_id.get(cam["follow"]) or next(
            (o for o in objs if o["name"] == cam["follow"]), None)

    look = None
    if eye_override:
        eye = eye_override
    elif camera_mode == "cam" and cam_obj:
        t = follow_obj()
        if t is not None:
            tm = W4[t["id"]]
            eye = (tm[12] + cam.get("offsetX", 0), tm[13] + cam.get("offsetY", 0),
                   tm[14] + cam.get("offsetZ", 0))
            if cam.get("lookAt", True):
                look = (tm[12], tm[13], tm[14])
        else:
            m = W4[cam_obj["id"]]
            eye = (m[12], m[13], m[14])
    elif camera_mode == "cam":
        eye = (0.0, 14.0, 22.0)
    else:
        cz = math.cos(math.radians(pitch))
        eye = (target[0] + dist * cz * math.sin(math.radians(yaw)),
               target[1] + dist * math.sin(math.radians(pitch)),
               target[2] + dist * cz * math.cos(math.radians(yaw)))
    if lookat_override:
        look = lookat_override
    if look is None:
        look = tuple(target)
    fwd = (look[0] - eye[0], look[1] - eye[1], look[2] - eye[2])
    fl = math.sqrt(sum(v * v for v in fwd)) or 1e-6
    fwd = tuple(v / fl for v in fwd)
    right = (fwd[1] * 0.0 - fwd[2] * 1.0, fwd[2] * 0.0 - fwd[0] * 0.0, fwd[0] * 1.0 - fwd[1] * 0.0)
    rl = math.sqrt(sum(v * v for v in right)) or 1e-6
    right = tuple(v / rl for v in right)
    up = (right[1] * fwd[2] - right[2] * fwd[1], right[2] * fwd[0] - right[0] * fwd[2],
          right[0] * fwd[1] - right[1] * fwd[0])

    view = look_at(eye, look)
    aspect = W / max(1, H)
    proj = perspective(fov, aspect, near, far)
    view_proj = mat_mul(proj, view)

    # --- lights: Renderer3D.setupLights
    amb = parse_color(world.get("ambient", "#FF404040"))
    ambient = (amb[0] / 255.0, amb[1] / 255.0, amb[2] / 255.0)
    dir_dir, dir_color, has_dir, points = (-0.4, -1.0, -0.3), (0.0, 0.0, 0.0), False, []
    for o in objs:
        l = o.get("light")
        if not l:
            continue
        m = W4[o["id"]]
        c = parse_color(l["color"])
        if l["kind"].lower().startswith("dir"):
            if not has_dir:
                d = mat_dir(m, 0.0, 0.0, -1.0)
                ln = math.sqrt(sum(v * v for v in d)) or 1e-6
                dir_dir = tuple(v / ln for v in d)
                dir_color = tuple(c[i] / 255.0 * l["intensity"] for i in range(3))
                has_dir = True
        else:
            p = (m[12], m[13], m[14])
            points.append((math.dist(eye, p), p, l,
                           tuple(c[i] / 255.0 * l["intensity"] for i in range(3))))
    if not has_dir and not points:
        dir_color = (0.85, 0.83, 0.8)
    points.sort(key=lambda t: t[0])
    points = points[:4]

    # --- sky: Renderer3D.drawSky
    if sky:
        ground = (sky_hor[0] * 0.45, sky_hor[1] * 0.45, sky_hor[2] * 0.5)
        tan_y = math.tan(math.radians(fov) / 2.0)
        tan_x = tan_y * aspect
        for y in range(H):
            ndc_y = 1.0 - 2.0 * (y + 0.5) / H
            for x in range(W):
                ndc_x = 2.0 * (x + 0.5) / W - 1.0
                dx = right[0] * ndc_x * tan_x + up[0] * ndc_y * tan_y + fwd[0]
                dy = right[1] * ndc_x * tan_x + up[1] * ndc_y * tan_y + fwd[1]
                dz = right[2] * ndc_x * tan_x + up[2] * ndc_y * tan_y + fwd[2]
                dl = math.sqrt(dx * dx + dy * dy + dz * dz) or 1e-6
                nd = dy / dl
                if nd >= 0:
                    t = nd ** 0.6
                    cv.px[y][x] = (int(sky_hor[0] * (1 - t) + sky_top[0] * t),
                                   int(sky_hor[1] * (1 - t) + sky_top[1] * t),
                                   int(sky_hor[2] * (1 - t) + sky_top[2] * t), 255)
                else:
                    t = min(1.0, -nd * 4.0)
                    cv.px[y][x] = (int(sky_hor[0] * (1 - t) + ground[0] * t),
                                   int(sky_hor[1] * (1 - t) + ground[1] * t),
                                   int(sky_hor[2] * (1 - t) + ground[2] * t), 255)

    zbuf = [[1e30] * W for _ in range(H)]

    fog_on = bool(world.get("fog"))
    fog_color = parse_color(world.get("fogColor", "#FF808080"))
    fog_start = world.get("fogStart", 0)
    fog_end = world.get("fogEnd", 1)

    def raster(screen, rgb, alpha, depth_test, wcs=None):
        """Fill one triangle.

        Fog is interpolated *per pixel* from the vertices' world positions -- the
        engine's fragment shader does the same.  A flat per-triangle fog value is
        visibly wrong on a big ground plane: its two triangles get two different
        fog depths and the diagonal between them shows as a hard seam.
        """
        (x0, y0, w0_), (x1, y1, w1_), (x2, y2, w2_) = screen
        minx = max(0, int(min(x0, x1, x2)))
        maxx = min(W - 1, int(max(x0, x1, x2)) + 1)
        miny = max(0, int(min(y0, y1, y2)))
        maxy = min(H - 1, int(max(y0, y1, y2)) + 1)
        if minx > maxx or miny > maxy:
            return
        det = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(det) < 1e-9:
            return
        stats["raster"] += 1
        for yy in range(miny, maxy + 1):
            py = yy + 0.5
            row = cv.px[yy]
            zb = zbuf[yy]
            for xx in range(minx, maxx + 1):
                px = xx + 0.5
                b0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / det
                b1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / det
                b2 = 1.0 - b0 - b1
                if b0 < 0 or b1 < 0 or b2 < 0:
                    continue
                invw = b0 / w0_ + b1 / w1_ + b2 / w2_
                if invw <= 0:
                    continue
                depth = 1.0 / invw
                if depth_test and (depth <= 0 or depth >= zb[xx]):
                    continue
                if depth_test:
                    zb[xx] = depth
                if fog_on and wcs is not None:
                    wx = (b0 * wcs[0][0] / w0_ + b1 * wcs[1][0] / w1_ + b2 * wcs[2][0] / w2_) * depth
                    wy = (b0 * wcs[0][1] / w0_ + b1 * wcs[1][1] / w1_ + b2 * wcs[2][1] / w2_) * depth
                    wz = (b0 * wcs[0][2] / w0_ + b1 * wcs[1][2] / w1_ + b2 * wcs[2][2] / w2_) * depth
                    ddx, ddy, ddz = eye[0] - wx, eye[1] - wy, eye[2] - wz
                    f = min(1.0, max(0.0, (math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz) - fog_start) /
                                      max(0.001, fog_end - fog_start)))
                    rr = int(rgb[0] * (1 - f) + fog_color[0] * f)
                    gg = int(rgb[1] * (1 - f) + fog_color[1] * f)
                    bb = int(rgb[2] * (1 - f) + fog_color[2] * f)
                    rgb_px = (rr, gg, bb, rgb[3])
                else:
                    rgb_px = rgb
                if alpha >= 0.999:
                    row[xx] = rgb_px
                else:
                    dst = row[xx]
                    row[xx] = (int(rgb_px[0] * alpha + dst[0] * (1 - alpha)),
                               int(rgb_px[1] * alpha + dst[1] * (1 - alpha)),
                               int(rgb_px[2] * alpha + dst[2] * (1 - alpha)), dst[3])

    def draw_object(o, mr, texture):
        mesh, err = models.get(mr.get("kind", "Cube"), mr.get("model", ""))
        if err:
            stats["skipped"] += 1
            return
        m = W4[o["id"]]
        inv = mat_inverse(m)
        base = parse_color(mr.get("color", "#FFD0D4DC"))
        unlit = bool(mr.get("unlit"))
        verts = mesh.verts
        for t in range(0, len(verts), 3):
            stats["tris"] += 1
            p = [mat_point(m, *verts[t + k][:3]) for k in range(3)]
            # Clip against the near plane before projecting.  The engine never
            # culls faces (no GL_CULL_FACE anywhere in it), so neither does this:
            # occlusion is the depth buffer's job here too.
            poly = []
            for pt in p:
                cw = (view_proj[3] * pt[0] + view_proj[7] * pt[1] +
                      view_proj[11] * pt[2] + view_proj[15])
                poly.append((pt, cw))
            front = [v for v in poly if v[1] > 0.001]
            if len(front) == 3:
                tri_pts = [(v[0], v[1]) for v in poly]
            elif len(front) == 0:
                continue
            else:
                out = []
                for i in range(3):
                    a, b = poly[i], poly[(i + 1) % 3]
                    if a[1] > 0.001:
                        out.append(a)
                    if (a[1] > 0.001) != (b[1] > 0.001):
                        f = (0.001 - a[1]) / (b[1] - a[1])
                        ip = (a[0][0] + (b[0][0] - a[0][0]) * f,
                              a[0][1] + (b[0][1] - a[0][1]) * f,
                              a[0][2] + (b[0][2] - a[0][2]) * f)
                        out.append((ip, 0.0011))
                tri_pts = out
            if len(tri_pts) < 3:
                continue
            screen = []
            for pt, cw in tri_pts:
                cx = view_proj[0] * pt[0] + view_proj[4] * pt[1] + view_proj[8] * pt[2] + view_proj[12]
                cy = view_proj[1] * pt[0] + view_proj[5] * pt[1] + view_proj[9] * pt[2] + view_proj[13]
                screen.append(((cx / cw * 0.5 + 0.5) * W, (1.0 - (cy / cw * 0.5 + 0.5)) * H, cw, pt))
            # fan the clipped polygon (it is a triangle or a quad)
            fan = []
            for k in range(1, len(screen) - 1):
                fan.append((screen[0], screen[k], screen[k + 1]))
            n_avg = tuple(sum(verts[t + k][3 + i] for k in range(3)) / 3.0 for i in range(3))
            nw = mat_dir(inv, n_avg[0], n_avg[1], n_avg[2])
            nl = math.sqrt(sum(v * v for v in nw)) or 1e-6
            nw = tuple(v / nl for v in nw)
            wc = (sum(s[3][0] for s in screen) / len(screen),
                  sum(s[3][1] for s in screen) / len(screen),
                  sum(s[3][2] for s in screen) / len(screen))
            texel = texture(verts[t][6], verts[t][7], mr.get("tiling", 1.0)) if texture else (1.0, 1.0, 1.0)
            if unlit:
                r, g, b = (base[0] / 255.0 * texel[0], base[1] / 255.0 * texel[1],
                           base[2] / 255.0 * texel[2])
            else:
                lx, ly, lz = -dir_dir[0], -dir_dir[1], -dir_dir[2]
                diff = max(0.0, nw[0] * lx + nw[1] * ly + nw[2] * lz)
                light = [ambient[i] + dir_color[i] * diff for i in range(3)]
                vdir = (eye[0] - wc[0], eye[1] - wc[1], eye[2] - wc[2])
                vl = math.sqrt(sum(v * v for v in vdir)) or 1e-6
                vdir = tuple(v / vl for v in vdir)
                shine = mr.get("shininess", 32.0)
                spec_amt = mr.get("specular", 0.35)
                hl = (lx + vdir[0], ly + vdir[1], lz + vdir[2])
                hln = math.sqrt(sum(v * v for v in hl)) or 1e-6
                hn = tuple(v / hln for v in hl)
                sp = max(0.0, sum(nw[i] * hn[i] for i in range(3))) ** shine * spec_amt if diff > 1e-4 else 0.0
                specular = [dir_color[i] * sp for i in range(3)]
                for _, lpos, l, lc in points:
                    dx, dy, dz = lpos[0] - wc[0], lpos[1] - wc[1], lpos[2] - wc[2]
                    dist = math.sqrt(dx * dx + dy * dy + dz * dz) or 1e-6
                    att = max(0.0, min(1.0, 1.0 - dist / max(l["range"], 0.001))) ** 2
                    pl = (dx / dist, dy / dist, dz / dist)
                    pd = max(0.0, sum(nw[i] * pl[i] for i in range(3)))
                    for i in range(3):
                        light[i] += lc[i] * pd * att
                    if pd > 1e-4:
                        hh = (pl[0] + vdir[0], pl[1] + vdir[1], pl[2] + vdir[2])
                        hl2 = math.sqrt(sum(v * v for v in hh)) or 1e-6
                        hn2 = tuple(v / hl2 for v in hh)
                        sp2 = max(0.0, sum(nw[i] * hn2[i] for i in range(3))) ** shine * spec_amt * att
                        for i in range(3):
                            specular[i] += lc[i] * sp2
                r = base[0] / 255.0 * texel[0] * light[0] + specular[0]
                g = base[1] / 255.0 * texel[1] * light[1] + specular[1]
                b = base[2] / 255.0 * texel[2] * light[2] + specular[2]
            em = mr.get("emission", 0.0)
            if em:
                r += base[0] / 255.0 * em
                g += base[1] / 255.0 * em
                b += base[2] / 255.0 * em
            alpha = base[3] / 255.0
            rgb = (max(0, min(255, int(r * 255))), max(0, min(255, int(g * 255))),
                   max(0, min(255, int(b * 255))), 255)
            for tri in fan:
                raster([(s[0], s[1], s[2]) for s in tri], rgb, alpha,
                       depth_test=(alpha >= 0.999), wcs=[s[3] for s in tri])
        stats["meshes"] += 1

    opaque, transparent = [], []
    for o in objs:
        mr = o.get("mesh")
        if not mr:
            continue
        a = parse_color(mr.get("color", "#FFD0D4DC"))[3] / 255.0
        m = W4[o["id"]]
        d = math.dist(eye, (m[12], m[13], m[14]))
        (opaque if a >= 0.999 else transparent).append((d, o, mr))
    trans = None
    try:
        from preview_scene import AssetTable  # noqa: PLC0415
        trans = AssetTable(ROOT)
        trans.declare(json.load(open(os.path.join(GAME_DIR, "game.json"), encoding="utf-8"))
                      .get("assets", []))
    except Exception:                                 # noqa: BLE001
        trans = None

    def sampler(name):
        if not name or trans is None:
            return None
        img = trans.image(name)
        if img is None:
            return None

        def sample(u, v, tiling=1.0):
            x = int((u * tiling) % 1.0 * img.w) % img.w
            y = int((1.0 - (v * tiling) % 1.0) * img.h) % img.h
            px = img.px[y][x]
            return (px[0] / 255.0, px[1] / 255.0, px[2] / 255.0)
        return sample

    for _, o, mr in opaque:
        draw_object(o, mr, sampler(mr.get("texture", "")))
    for _, o, mr in transparent:                      # back to front, as the engine
        draw_object(o, mr, sampler(mr.get("texture", "")))

    caption = (f"{len(objs)} objects, {stats['meshes']} meshes, {stats['tris']} tris, "
               f"{stats['raster']} rasterised")
    if stats["skipped"]:
        caption += f", {stats['skipped']} skipped"
    return cv, caption


GAME_DIR = "."


def main() -> int:
    global GAME_DIR
    ap = argparse.ArgumentParser()
    ap.add_argument("game")
    ap.add_argument("--world", required=True)
    ap.add_argument("--out", default="preview3d.png")
    ap.add_argument("--size", default="1280x720")
    ap.add_argument("--camera", default="free", choices=["free", "cam"])
    ap.add_argument("--yaw", type=float, default=40.0)
    ap.add_argument("--pitch", type=float, default=24.0)
    ap.add_argument("--dist", type=float, default=48.0)
    ap.add_argument("--target", default="")
    ap.add_argument("--eye", default="")
    ap.add_argument("--lookat", default="")
    ap.add_argument("--no-sky", action="store_true")
    args = ap.parse_args()

    game_dir = args.game if os.path.isdir(args.game) else None
    if game_dir is None:
        base = os.path.join(ROOT, "games")
        for d in sorted(os.listdir(base)):
            if args.game in d:
                game_dir = os.path.join(base, d)
                break
    if game_dir is None:
        print(f"no game matching '{args.game}'", file=sys.stderr)
        return 2
    GAME_DIR = game_dir
    meta = json.load(open(os.path.join(game_dir, "game.json"), encoding="utf-8"))
    world = json.load(open(args.world, encoding="utf-8"))
    W, H = (int(v) for v in args.size.lower().split("x"))
    models = ModelStore(ROOT, meta.get("assets", []))
    target = tuple(float(v) for v in args.target.split(",")) if args.target else (0.0, 0.0, 0.0)
    eye = tuple(float(v) for v in args.eye.split(",")) if args.eye else None
    look = tuple(float(v) for v in args.lookat.split(",")) if args.lookat else None

    img, caption = render(world, models, (W, H), args.camera, args.yaw, args.pitch,
                          args.dist, target, eye, look, sky=not args.no_sky)
    write_png(args.out, img)
    print(f"{os.path.basename(game_dir)}: {caption}  ->  {args.out}  ({W}x{H})")
    for e in sorted(models.missing):
        print(f"  ! {e}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
