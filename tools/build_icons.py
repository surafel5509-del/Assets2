#!/usr/bin/env python3
"""
build_icons.py -- generates the S Engine icon set as Android VectorDrawables.

Every icon is a 24x24 grid drawn with a consistent 1.8-unit round-capped stroke,
so the editor reads as one family. Fills are used only where a shape needs to
read as solid (a filled rectangle tool, a play button, a bucket).

    python3 tools/build_icons.py           # writes app/src/main/res/drawable/ic_*.xml

Each icon is a list of (pathData, filled) pairs. The XML is tinted at runtime,
so one set serves the dark theme's text colour, the accent, and disabled states.
"""

import os

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")

def circle(cx, cy, r):
    return f"M{cx - r},{cy} a{r},{r} 0 1,0 {2 * r},0 a{r},{r} 0 1,0 {-2 * r},0"

def rect(x, y, w, h):
    return f"M{x},{y} H{x + w} V{y + h} H{x} Z"

def rrect(x, y, w, h, r):
    return (f"M{x + r},{y} H{x + w - r} A{r},{r} 0 0,1 {x + w},{y + r} V{y + h - r} "
            f"A{r},{r} 0 0,1 {x + w - r},{y + h} H{x + r} A{r},{r} 0 0,1 {x},{y + h - r} "
            f"V{y + r} A{r},{r} 0 0,1 {x + r},{y} Z")

ICONS = {
    # ---- Sprite Editor Studio tools
    "pencil": [("M4,20 L4,16 L15,5 L19,9 L8,20 Z", False), ("M13,7 L17,11", False)],
    "eraser": [("M4,15 L11,6 L20,13 L13,20 H8 Z", False), ("M9,11 L16,17", False), ("M8,20 H20", False)],
    "bucket": [("M5,11 L11,5 L17,11 L11,17 Z", False), ("M18,14 C18,16 19.5,17.5 19.5,19 A1.5,1.5 0 0 1 16.5,19 C16.5,17.5 18,16 18,14 Z", True), ("M4,21 H20", False)],
    "line": [("M4,20 L20,4", False), ("M4,20 m-1.5,0 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0", True), ("M20,4 m-1.5,0 a1.5,1.5 0 1,0 3,0 a1.5,1.5 0 1,0 -3,0", True)],
    "rect": [(rect(4, 5, 16, 14), False)],
    "rect_fill": [(rect(4, 5, 16, 14), True)],
    "ellipse": [(circle(12, 12, 8), False)],
    "ellipse_fill": [(circle(12, 12, 8), True)],
    "eyedropper": [("M15,5 L19,9 L10,18 L6,18 L6,14 Z", False), ("M13,7 L17,11", False)],
    "shade": [(circle(12, 12, 8), False), ("M12,4 A8,8 0 0,1 12,20 Z", True)],
    "move": [("M12,3 V21 M3,12 H21", False), ("M9,6 L12,3 L15,6 M9,18 L12,21 L15,18 M6,9 L3,12 L6,15 M18,9 L21,12 L18,15", False)],
    "symmetry": [("M12,3 V21", False), ("M3,7 L9,17 H3 Z", True), ("M21,7 L15,17 H21 Z", True)],
    "onion": [("M3,12 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0", False), ("M11,12 a5,5 0 1,0 10,0 a5,5 0 1,0 -10,0", False)],
    "grid": [("M9,3 V21 M15,3 V21 M3,9 H21 M3,15 H21", False)],
    "grade": [("M4,6 H20 M4,12 H20 M4,18 H20", False), (circle(9, 6, 2), True), (circle(16, 12, 2), True), (circle(7, 18, 2), True)],
    "outline": [(rect(4, 4, 16, 16), False), (rect(8, 8, 8, 8), False)],
    "flip_h": [("M12,3 V21", False), ("M9,7 L3,17 H9 Z", True), ("M15,7 L21,17 H15 Z", False)],
    "flip_v": [("M3,12 H21", False), ("M7,9 L12,3 L17,9 Z", True), ("M7,15 L12,21 L17,15 Z", False)],
    "rotate": [("M20,12 A8,8 0 1,1 17,6", False), ("M17,2 V6 H21", False)],
    "palette": [("M12,3 A9,9 0 1,0 12,21 C13,21 14,20 14,19 C14,18 13,17.5 13,16.5 C13,15.5 14,15 15,15 H17 A4,4 0 0,0 21,11 A9,9 0 0,0 12,3 Z", False), (circle(7.5, 11, 1.3), True), (circle(10.5, 7, 1.3), True), (circle(15.5, 7.5, 1.3), True)],
    "resize": [(rect(4, 4, 16, 16), False), ("M8,16 L16,8", False), ("M11,8 H16 V13", False)],
    "snap": [("M6,3 V13 A6,6 0 0,0 18,13 V3 H14 V13 A2,2 0 0,1 10,13 V3 Z", False)],
    # ---- frames, layers and playback
    "play": [("M7,4 L20,12 L7,20 Z", True)],
    "pause": [("M7,4 H10 V20 H7 Z", True), ("M14,4 H17 V20 H14 Z", True)],
    "stop": [(rect(6, 6, 12, 12), True)],
    "step_back": [("M17,5 L8,12 L17,19 Z", True), ("M6,5 V19", False)],
    "step_forward": [("M7,5 L16,12 L7,19 Z", True), ("M18,5 V19", False)],
    "frame_add": [(rect(3, 7, 18, 10), False), ("M7,7 V17 M17,7 V17", False), ("M12,10 V14 M10,12 H14", False)],
    "frame_copy": [(rect(8, 8, 11, 11), False), ("M5,16 V5 H16", False)],
    "frame_remove": [(rect(3, 7, 18, 10), False), ("M7,7 V17 M17,7 V17", False), ("M9,12 H15", False)],
    "frames": [(rect(3, 6, 18, 12), False), ("M7,6 V18 M17,6 V18", False)],
    "layer": [("M12,3 L21,8 L12,13 L3,8 Z", False), ("M3,13 L12,18 L21,13", False), ("M3,17 L12,22 L21,17", False)],
    "layer_add": [("M12,3 L21,8 L12,13 L3,8 Z", False), ("M12,15 V21 M9,18 H15", False)],
    "merge_down": [("M12,4 V14", False), ("M7,10 L12,15 L17,10", False), ("M5,20 H19", False)],
    "eye": [("M2,12 Q12,3 22,12 Q12,21 2,12 Z", False), (circle(12, 12, 3), True)],
    "eye_off": [("M2,12 Q12,3 22,12 Q12,21 2,12 Z", False), (circle(12, 12, 3), True), ("M3,3 L21,21", False)],
    "lock": [(rect(6, 11, 12, 10), True), ("M8,11 V7 A4,4 0 0,1 16,7 V11", False)],
    "chevron_up": [("M6,15 L12,9 L18,15", False)],
    "chevron_down": [("M6,9 L12,15 L18,9", False)],
    "chevron_left": [("M15,6 L9,12 L15,18", False)],
    "chevron_right": [("M9,6 L15,12 L9,18", False)],
    "add": [("M12,5 V19 M5,12 H19", False)],
    "remove": [("M5,12 H19", False)],
    "duplicate": [(rect(8, 8, 11, 11), False), ("M5,16 V5 H16", False)],
    "trash": [("M5,7 H19", False), ("M9,7 V4 H15 V7", False), ("M7,7 L8,21 H16 L17,7", False)],
    "undo": [("M9,14 L4,9 L9,4", False), ("M4,9 H14 A6,6 0 0,1 14,21 H10", False)],
    "redo": [("M15,14 L20,9 L15,4", False), ("M20,9 H10 A6,6 0 0,0 10,21 H14", False)],
    "save": [("M4,4 H16 L20,8 V20 H4 Z", False), ("M8,4 V10 H16 V4", False), ("M8,20 V14 H16 V20", False)],
    "import": [("M12,3 V15", False), ("M7,10 L12,15 L17,10", False), ("M4,15 V20 H20 V15", False)],
    "export": [("M12,15 V3", False), ("M7,8 L12,3 L17,8", False), ("M4,15 V20 H20 V15", False)],
    "text": [("M5,6 V4 H19 V6", False), ("M12,4 V20", False), ("M9,20 H15", False)],
    "image": [(rect(3, 4, 18, 16), False), ("M3,16 L9,10 L14,15 L17,12 L21,16", False), (circle(16, 8, 1.6), True)],
    # ---- asset branches
    "folder": [("M3,6 H10 L12,8 H21 V19 H3 Z", False)],
    "folder_add": [("M3,6 H10 L12,8 H21 V19 H3 Z", False), ("M12,11 V17 M9,14 H15", False)],
    "script": [("M8,4 H5 V20 H8", False), ("M16,4 H19 V20 H16", False), ("M10,9 H14 M10,15 H14", False)],
    "blueprint": [(rect(3, 4, 6, 5), False), (rect(15, 15, 6, 5), False), ("M9,6.5 H12 V17.5 H15", False)],
    "sound": [("M4,9 H8 L13,5 V19 L8,15 H4 Z", True), ("M16,9 Q18,12 16,15", False), ("M18.5,6 Q22.5,12 18.5,18", False)],
    "music": [("M9,18 V5 L19,3 V16", False), (circle(6.5, 18, 2.5), True), (circle(16.5, 16, 2.5), True)],
    "animation": [(rect(3, 5, 18, 14), False), ("M10,9 L15,12 L10,15 Z", True)],
    "sprite": [(rect(4, 4, 6, 6), True), (rect(14, 4, 6, 6), False), (rect(4, 14, 6, 6), False), (rect(14, 14, 6, 6), True)],
    "shader": [("M3,8 Q7,4 11,8 T19,8 T23,8", False), ("M3,14 Q7,10 11,14 T19,14 T23,14", False), ("M3,20 Q7,16 11,20 T19,20", False)],
    "model": [("M12,3 L20,7 V17 L12,21 L4,17 V7 Z", False), ("M4,7 L12,11 L20,7", False), ("M12,11 V21", False)],
    "particles": [(circle(7, 7, 1.8), True), (circle(16, 6, 1.2), True), (circle(12, 13, 2.2), True), (circle(18, 17, 1.6), True), (circle(6, 18, 1.2), True)],
    "data": [("M5,6 A7,3 0 0,0 19,6 V18 A7,3 0 0,1 5,18 Z", False), ("M5,6 A7,3 0 0,1 19,6", False), ("M5,12 A7,3 0 0,0 19,12", False)],
    "ui_screen": [(rect(3, 4, 18, 16), False), (rect(6, 8, 6, 4), False), (rect(14, 8, 4, 8), False)],
    "controls": [("M7,8 H17 A5,5 0 0,1 21,13 A3,3 0 0,1 17,16 L14,14 H10 L7,16 A3,3 0 0,1 3,13 A5,5 0 0,1 7,8 Z", False), ("M7,11 V13 M6,12 H8", False), (circle(16, 11, 0.9), True), (circle(18, 13.5, 0.9), True)],
    "panel": [(rrect(3, 5, 18, 14, 3), False), ("M7,9 H17 M7,12 H13", False)],
    "label": [("M5,6 V4 H19 V6", False), ("M12,4 V20", False)],
    "button": [(rrect(3, 7, 18, 10, 5), False), ("M9,12 H15", False)],
    "toggle": [(rrect(3, 7, 18, 10, 5), False), (circle(16, 12, 2.5), True)],
    "joystick": [(circle(12, 12, 8), False), (circle(12, 12, 3.2), True)],
    "dpad": [("M9,3 H15 V9 H21 V15 H15 V21 H9 V15 H3 V9 H9 Z", False)],
    "progress": [(rrect(3, 9, 18, 6, 3), False), (rrect(4.5, 10.5, 9, 3, 1.5), True)],
    # ---- project, build, store, scene
    "store": [("M5,8 H19 V20 H5 Z", False), ("M9,8 V5 A3,3 0 0,1 15,5 V8", False)],
    "packs": [("M3,7 L12,3 L21,7 V17 L12,21 L3,17 Z", False), ("M3,7 L12,11 L21,7", False), ("M12,11 V21", False)],
    "build": [("M12,3 V15", False), ("M7,10 L12,15 L17,10", False), ("M5,21 H19", False)],
    "scene": [(rect(3, 5, 18, 14), False), ("M3,17 L9,11 L13,15 L16,12 L21,17", False), (circle(16.5, 9, 1.5), True)],
    "hierarchy": [(rect(3, 4, 6, 4), False), (rect(9, 10, 6, 4), False), (rect(15, 16, 6, 4), False), ("M6,8 V12 H9 M12,12 V16 H15", False)],
    "console": [("M4,5 L10,11 L4,17", False), ("M12,18 H20", False)],
    "gear": [(circle(12, 12, 3), False), ("M12,3 V6 M12,18 V21 M3,12 H6 M18,12 H21 M5.6,5.6 L7.7,7.7 M16.3,16.3 L18.4,18.4 M5.6,18.4 L7.7,16.3 M16.3,7.7 L18.4,5.6", False)],
    "zoom_in": [(circle(10, 10, 6), False), ("M15,15 L21,21 M10,7 V13 M7,10 H13", False)],
    "zoom_out": [(circle(10, 10, 6), False), ("M15,15 L21,21 M7,10 H13", False)],
    "resize_canvas": [(rect(4, 4, 16, 16), False), ("M4,12 H9 M15,12 H20", False), ("M12,4 V9 M12,15 V20", False)],
    "check": [("M4,12 L9,17 L20,6", False)],
    "close": [("M6,6 L18,18 M18,6 L6,18", False)],
    "info": [(circle(12, 12, 9), False), ("M12,11 V17", False), (circle(12, 7.6, 1.1), True)],
    "warning": [("M12,3 L22,20 H2 Z", False), ("M12,10 V14", False), (circle(12, 17, 1), True)],
    "tile": [(rect(3, 3, 8, 8), False), (rect(13, 3, 8, 8), False), (rect(3, 13, 8, 8), False), (rect(13, 13, 8, 8), False)],
    "sparkle": [("M12,3 L13.5,10.5 L21,12 L13.5,13.5 L12,21 L10.5,13.5 L3,12 L10.5,10.5 Z", True)],
    "run": [("M7,4 L20,12 L7,20 Z", True)],
    "hand": [("M9,11 V5 A1.5,1.5 0 0,1 12,5 V11 V4 A1.5,1.5 0 0,1 15,4 V11 V6 A1.5,1.5 0 0,1 18,6 V14 A6,6 0 0,1 12,20 H11 A6,6 0 0,1 5,14 V11 A1.5,1.5 0 0,1 8,11", False)],
}

HEADER = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/build_icons.py. Edit the generator, not this file. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
"""

def xml(name, parts):
    out = [HEADER]
    for d, filled in parts:
        if filled:
            out.append(f'    <path android:fillColor="#FFFFFFFF" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.2" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{d}"/>\n')
        else:
            out.append(f'    <path android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="1.8" android:strokeLineCap="round" android:strokeLineJoin="round" android:pathData="{d}"/>\n')
    out.append("</vector>\n")
    return "".join(out)

def main():
    os.makedirs(OUT, exist_ok=True)
    for name, parts in ICONS.items():
        with open(os.path.join(OUT, f"ic_{name}.xml"), "w", encoding="utf-8") as f:
            f.write(xml(name, parts))
    print(f"wrote {len(ICONS)} icons to {os.path.normpath(OUT)}")

if __name__ == "__main__":
    main()
