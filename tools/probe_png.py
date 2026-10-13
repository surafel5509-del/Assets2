#!/usr/bin/env python3
"""
probe_png.py -- look at a PNG without Pillow.

This sandbox has no Pillow and no numpy, and the asset packs ship their art as
PNGs.  When a level has to be authored against a tileset atlas, the tile grid and
which cell holds which tile are *not* guessable: a wrong grid produces UVs that
sample the wrong art, which the engine renders as garbage rather than as an
error.  The store's own sheet detector (tools/build_asset_store.py) only runs
where Pillow exists, and it deliberately refuses to guess grids for opaque
tilesets -- every boundary line of an opaque atlas is non-transparent, so the
alpha test that recovers sprite-sheet grids has nothing to work with.

So this tool decodes the PNG itself (zlib + the five PNG filter types, stdlib
only) and writes back a *proof sheet*: the atlas with a grid overlay, or a chart
that labels every cell with its index so an author can pick cells by looking.

Usage:
    python3 tools/probe_png.py FILE.png                        # size + filter types
    python3 tools/probe_png.py FILE.png --grid 16 --out g.png  # overlay a grid
    python3 tools/probe_png.py FILE.png --chart --cell 16 --out c.png
    python3 tools/probe_png.py FILE.png --zoom 3 --crop 0,0,64,64 --out z.png

Options:
    --grid N        draw lattice lines every N pixels
    --cell W[,H]    cell size for --chart (default 16x16)
    --chart         label each cell with its row-major index
    --zoom N        integer upscale (nearest neighbour)
    --crop X,Y,W,H  crop before drawing
    --prefix TEXT   write a text header instead of the pixel ruler
    --list          print every distinct colour count (useful for palettes)

The decoder handles the forms these packs actually use: 8-bit RGB, RGBA,
greyscale and palette with tRNS.  16-bit and interlaced files are rejected with a
clear message rather than silently mis-decoded.
"""

from __future__ import annotations

import argparse
import struct
import sys
import zlib

# --------------------------------------------------------------------------- PNG


class Png:
    def __init__(self, w, h, px, palette=None):
        self.w = w
        self.h = h
        self.px = px          # list of rows, each a list of (r, g, b, a) tuples
        self.palette = palette

    def at(self, x, y):
        if x < 0 or y < 0 or x >= self.w or y >= self.h:
            return (0, 0, 0, 0)
        return self.px[y][x]

    def blank(self, w, h, fill=(0, 0, 0, 255)):
        return Png(w, h, [[fill] * w for _ in range(h)])

    def filled(self, w, h, fill):
        return self.blank(w, h, fill)

    def blit(self, src, dx, dy):
        for y in range(src.h):
            ty = dy + y
            if ty < 0 or ty >= self.h:
                continue
            row = self.px[ty]
            srow = src.px[y]
            for x in range(src.w):
                tx = dx + x
                if 0 <= tx < self.w:
                    row[tx] = srow[x]

    def crop(self, x, y, w, h):
        out = []
        for yy in range(y, y + h):
            out.append([self.at(xx, yy) for xx in range(x, x + w)])
        return Png(w, h, out)

    def scaled(self, n):
        out = []
        for y in range(self.h):
            row = self.px[y]
            big = []
            for x in range(self.w):
                big.extend([row[x]] * n)
            for _ in range(n):
                out.append(list(big))
        return Png(self.w * n, self.h * n, out)


def read_png(path):
    data = open(path, "rb").read()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("not a PNG file")
    pos = 8
    idat = b""
    palette = None
    trns = None
    w = h = depth = ctype = None
    interlace = 0
    while pos < len(data):
        (ln,) = struct.unpack(">I", data[pos:pos + 4])
        typ = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b"IHDR":
            w, h, depth, ctype, _comp, _filt, interlace = struct.unpack(">IIBBBBB", body)
        elif typ == b"PLTE":
            palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif typ == b"tRNS":
            trns = body
        elif typ == b"IDAT":
            idat += body
        elif typ == b"IEND":
            break

    if depth != 8:
        raise ValueError(f"only 8-bit channels are supported (this file is {depth}-bit)")
    if interlace:
        raise ValueError("Adam7-interlaced PNGs are not supported")

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}.get(ctype)
    if channels is None:
        raise ValueError(f"unsupported colour type {ctype}")

    raw = zlib.decompress(idat)
    stride = w * channels
    prev = bytearray(stride)
    rows = []
    p = 0
    for _ in range(h):
        ftype = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if ftype == 1:                     # Sub
            for i in range(channels, stride):
                line[i] = (line[i] + line[i - channels]) & 0xFF
        elif ftype == 2:                   # Up
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif ftype == 3:                   # Average
            for i in range(stride):
                left = line[i - channels] if i >= channels else 0
                line[i] = (line[i] + ((left + prev[i]) >> 1)) & 0xFF
        elif ftype == 4:                   # Paeth
            for i in range(stride):
                a = line[i - channels] if i >= channels else 0
                b = prev[i]
                c = prev[i - channels] if i >= channels else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        elif ftype != 0:
            raise ValueError(f"unknown filter type {ftype}")
        prev = line

        row = []
        for x in range(w):
            i = x * channels
            if ctype == 6:
                row.append((line[i], line[i + 1], line[i + 2], line[i + 3]))
            elif ctype == 2:
                row.append((line[i], line[i + 1], line[i + 2], 255))
            elif ctype == 0:
                g = line[i]
                row.append((g, g, g, 255))
            elif ctype == 4:
                g = line[i]
                row.append((g, g, g, line[i + 1]))
            else:                          # palette
                idx = line[i]
                r, g, b = palette[idx] if palette and idx < len(palette) else (0, 0, 0)
                a = trns[idx] if trns and idx < len(trns) else 255
                row.append((r, g, b, a))
        rows.append(row)

    return Png(w, h, rows, palette)


def write_png(path, png: Png):
    def chunk(typ, body):
        return (struct.pack(">I", len(body)) + typ + body
                + struct.pack(">I", zlib.crc32(typ + body) & 0xFFFFFFFF))

    raw = bytearray()
    for row in png.px:
        raw.append(0)
        for (r, g, b, a) in row:
            raw += bytes((r, g, b, a))
    body = (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", png.w, png.h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 6))
            + chunk(b"IEND", b""))
    open(path, "wb").write(body)
    return len(body)


# ------------------------------------------------------------------- tiny font
# 3x5 digits and a few letters, so a chart can label cells. Kept literal because
# a font renderer is exactly the dependency this tool exists to avoid.
FONT = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"],
    ":": ["000", "010", "000", "010", "000"],
    "-": ["000", "000", "111", "000", "000"],
    "x": ["000", "101", "010", "101", "000"],
    " ": ["000", "000", "000", "000", "000"],
    "#": ["101", "111", "101", "111", "101"],
    ".": ["000", "000", "000", "000", "010"],
}


def draw_text(png: Png, text, x, y, color=(255, 255, 255, 255), scale=1, background=(0, 0, 0, 200)):
    """5 rows of 3 px per glyph, 1 px gap. Background is drawn per glyph cell."""
    cx = x
    for ch in str(text):
        glyph = FONT.get(ch, FONT[" "])
        if background is not None:
            for gy in range(5 * scale):
                for gx in range(3 * scale + scale):
                    px, py = cx + gx, y + gy
                    if 0 <= px < png.w and 0 <= py < png.h:
                        png.px[py][px] = background
        for row in range(5):
            for col in range(3):
                if glyph[row][col] == "1":
                    for sy in range(scale):
                        for sx in range(scale):
                            px, py = cx + col * scale + sx, y + row * scale + sy
                            if 0 <= px < png.w and 0 <= py < png.h:
                                png.px[py][px] = color
        cx += 4 * scale
    return cx


def hline(png, x, y, w, color):
    for i in range(w):
        if 0 <= x + i < png.w and 0 <= y < png.h:
            png.px[y][x + i] = color


def vline(png, x, y, h, color):
    for i in range(h):
        if 0 <= x < png.w and 0 <= y + i < png.h:
            png.px[y + i][x] = color


# ------------------------------------------------------------------- operations


def make_grid(src: Png, cell_w, cell_h, major=4):
    out = Png(src.w, src.h, [list(r) for r in src.px])
    dim = (255, 255, 255, 90)
    bright = (255, 64, 200, 200)
    for x in range(0, src.w + 1, cell_w):
        col = bright if (x // cell_w) % major == 0 else dim
        vline(out, x, 0, src.h, col)
    for y in range(0, src.h + 1, cell_h):
        col = bright if (y // cell_h) % major == 0 else dim
        hline(out, 0, y, src.w, col)
    return out


def make_chart(src: Png, cell_w, cell_h, zoom=2, label=True, cols_limit=None):
    cols = max(1, src.w // cell_w)
    rows = max(1, src.h // cell_h)
    pad = 6 if label else 2
    cw, ch = cell_w * zoom + pad, cell_h * zoom + pad + (6 * zoom if label else 0)
    out = src.filled(cols * cw, rows * ch, (24, 26, 32, 255))
    for r in range(rows):
        for c in range(cols):
            tile = src.crop(c * cell_w, r * cell_h, cell_w, cell_h).scaled(zoom)
            ox, oy = c * cw + pad // 2, r * ch + pad // 2
            out.blit(tile, ox, oy)
            if label:
                draw_text(out, str(r * cols + c), ox, oy + cell_h * zoom + 1,
                          color=(255, 220, 120, 255), scale=zoom)
    return out


def connected_boxes(src: Png, min_area=64, min_alpha=32, max_boxes=200):
    """Bounding boxes of connected non-transparent regions.

    The packs draw their tilesets as *sprites*, not as a uniform grid: a 5x5-cell
    grass blob is one drawing that happens to straddle five grid lines.  Nothing
    in the file says where it ends, so the box has to be measured.  Rectangles
    that overlap are merged, because the interesting unit is "the drawing", and
    these atlases are packing many overlapping doodads per row.
    """
    w, h = src.w, src.h
    opaque = [[src.px[y][x][3] >= min_alpha for x in range(w)] for y in range(h)]
    seen = [[False] * w for _ in range(h)]
    boxes = []
    for y0 in range(h):
        for x0 in range(w):
            if not opaque[y0][x0] or seen[y0][x0]:
                continue
            stack = [(x0, y0)]
            seen[y0][x0] = True
            minx = maxx = x0
            miny = maxy = y0
            area = 0
            while stack:
                x, y = stack.pop()
                area += 1
                if x < minx: minx = x
                if x > maxx: maxx = x
                if y < miny: miny = y
                if y > maxy: maxy = y
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and opaque[ny][nx] and not seen[ny][nx]:
                        seen[ny][nx] = True
                        stack.append((nx, ny))
            if area >= min_area:
                boxes.append([minx, miny, maxx + 1, maxy + 1, area])

    merged = True
    while merged:
        merged = False
        out = []
        while boxes:
            a = boxes.pop()
            i = 0
            while i < len(boxes):
                b = boxes[i]
                if not (a[2] <= b[0] or b[2] <= a[0] or a[3] <= b[1] or b[3] <= a[1]):
                    a = [min(a[0], b[0]), min(a[1], b[1]), max(a[2], b[2]), max(a[3], b[3]), a[4] + b[4]]
                    boxes.pop(i)
                    merged = True
                else:
                    i += 1
            out.append(a)
        boxes = out
    boxes.sort(key=lambda b: (b[1], b[0]))
    return boxes[:max_boxes]


def class_map(src: Png, cell, min_alpha=200):
    """One letter per cell, by dominant colour, for picking tiles out of an atlas.

    Every tileset in the store is opaque art with no grid metadata, and the
    store's detector deliberately declines to guess grids for them.  Choosing a
    cell for "grass" therefore needs a human looking at the pixels -- this table
    is the machine-readable half of that: it says which cells are a single flat
    colour (i.e. a tileable ground tile) and roughly what colour, so a palette can
    be picked and then confirmed by eye on a chart.
    """
    import colorsys
    cols, rows = src.w // cell, src.h // cell
    out = []
    for cy in range(rows):
        line = []
        for cx in range(cols):
            opaque = []
            for y in range(cy * cell, (cy + 1) * cell):
                for x in range(cx * cell, (cx + 1) * cell):
                    p = src.px[y][x]
                    if p[3] >= min_alpha:
                        opaque.append(p)
            frac = len(opaque) / float(cell * cell)
            if frac < 0.02:
                line.append(".")
                continue
            r = sum(p[0] for p in opaque) // len(opaque)
            g = sum(p[1] for p in opaque) // len(opaque)
            b = sum(p[2] for p in opaque) // len(opaque)
            hues = len(set((p[0] // 24, p[1] // 24, p[2] // 24) for p in opaque))
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            hue = h * 360
            if s < 0.15:
                ch = "W" if v > 0.55 else ("#" if v > 0.25 else "K")
            elif hue < 35 or hue >= 340:
                ch = "R" if v < 0.45 else "r"
            elif hue < 70:
                ch = "Y" if v > 0.45 else "y"
            elif hue < 170:
                ch = "G" if v > 0.45 else "g"
            elif hue < 260:
                ch = "B" if v > 0.45 else "b"
            else:
                ch = "P"
            if frac < 0.5:
                ch = ch.lower() if ch.isupper() else ","
            # uppercase a solid, tileable cell with a single colour
            if frac > 0.99 and hues <= 2:
                ch = ch.upper() if ch.upper() not in ("W",) else ch
            line.append(ch)
        out.append("".join(line))
    return out


def colour_list(src: Png, top=24):
    counts = {}
    for row in src.px:
        for px in row:
            counts[px] = counts.get(px, 0) + 1
    ordered = sorted(counts.items(), key=lambda kv: -kv[1])[:top]
    total = src.w * src.h
    print(f"  {len(counts)} distinct colours")
    for (r, g, b, a), n in ordered:
        print(f"    #{r:02X}{g:02X}{b:02X}{a:02X}  {n:>8} px  {100.0 * n / total:5.1f}%")


def main() -> int:
    ap = argparse.ArgumentParser(description="Inspect / rasterise a PNG without Pillow.")
    ap.add_argument("file")
    ap.add_argument("--grid", type=int, default=0, help="draw lattice lines every N px")
    ap.add_argument("--cell", default="16", help="cell size for --chart: W or WxH")
    ap.add_argument("--chart", action="store_true", help="render a labelled cell chart")
    ap.add_argument("--zoom", type=int, default=1, help="integer upscale before writing")
    ap.add_argument("--crop", default="", help="X,Y,W,H")
    ap.add_argument("--out", default="", help="output PNG (without it, only a summary prints)")
    ap.add_argument("--list", action="store_true", help="list the most common colours")
    ap.add_argument("--map", type=int, default=0, metavar="CELL",
                    help="print a one-letter-per-cell colour map of the atlas")
    ap.add_argument("--boxes", action="store_true",
                    help="list bounding boxes of connected opaque regions (sprite extents)")
    ap.add_argument("--min-area", type=int, default=64)
    ap.add_argument("--cell-of", type=int, default=16,
                    help="with --boxes, also print the box in cell units for this cell size")
    args = ap.parse_args()

    try:
        img = read_png(args.file)
    except Exception as e:                                        # noqa: BLE001
        print(f"error: {args.file}: {e}", file=sys.stderr)
        return 2

    print(f"{args.file}: {img.w}x{img.h}")
    if args.crop:
        x, y, w, h = (int(v) for v in args.crop.split(","))
        img = img.crop(x, y, w, h)
        print(f"  cropped to {w}x{h} at {x},{y}")

    if args.list:
        colour_list(img)

    if args.map:
        cell = args.map
        lines = class_map(img, cell)
        print(f"  class map, {img.w // cell} x {img.h // cell} cells of {cell}px")
        print(f"  legend: G/g green  R/r brown-red  Y/y olive  B/b blue  P purple  "
              f"W light  # dark-mid  K black  . empty  |  UPPER = solid single-colour cell")
        hang = len(str(len(lines) - 1))
        for i, line in enumerate(lines):
            print(f"  {str(i).rjust(hang)}  {line}")
        print(f"  column ruler: {''.join(str(c % 10) for c in range(len(lines[0])))}")

    if args.boxes:
        cell = max(1, args.cell_of)
        for (x0, y0, x1, y1, area) in connected_boxes(img, args.min_area):
            c0, r0 = x0 // cell, y0 // cell
            cw, rh = (x1 - x0) / cell, (y1 - y0) / cell
            print(f"    px {x0:>4},{y0:>4} -> {x1:>4},{y1:>4}  ({x1 - x0}x{y1 - y0})"
                  f"  cells col{c0} row{r0} {cw:g}x{rh:g}  area {area}")
        if not args.out:
            return 0

    if not args.out:
        return 0

    out = img
    if args.chart:
        cw, _, chh = args.cell.partition("x")
        cell_w, cell_h = int(cw), int(chh or cw)
        out = make_chart(out, cell_w, cell_h, zoom=max(1, args.zoom))
    else:
        if args.grid:
            out = make_grid(out, args.grid, args.grid)
        if args.zoom > 1:
            out = out.scaled(args.zoom)

    size = write_png(args.out, out)
    print(f"  wrote {args.out} ({out.w}x{out.h}, {size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
