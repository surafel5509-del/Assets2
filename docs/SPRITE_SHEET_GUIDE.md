# Sprite Sheet Guide — v3.2.0

S Engine 3.2.0 adds a complete **sprite-sheet pipeline**: slice any texture into named frames, pack them into a fresh sheet and export a ready-to-play asset set — all from the phone.

## Opening the editor

Three ways in:

* **Main menu → Sprite Sheet Editor** (☰ in the scene editor)
* **Assets bar → + Sheet** (creates a `.sheet` document and opens it)
* **Any `.sheet` asset → Edit in Sprite Sheet Editor**

The editor auto-saves its document on **Save**; **Export** also writes the `.sheet` document alongside the derived assets.

## 1. Choose a source texture

Tap **(choose texture)** and pick any imported image (PNG, JPG, WebP, TGA, PNM, DDS, KTX, HDR…). Picking a texture auto-fills a sensible grid in Grid mode (e.g. `512×64` → `8×1`) and slices it immediately.

## 2. Slice

| Mode | What it does |
|---|---|
| **Grid** | Uniform `Columns × Rows` cells, row-major order. Leftover pixels at the right/bottom edge are ignored. |
| **Cell** | Fixed `Cell W × Cell H` pixels per frame — for sheets with consistent cell sizes that do not divide the image evenly. |
| **Auto/Manual** | **Slice** runs automatic alpha detection: connected opaque regions become frames (bounding boxes, reading order). **Add Rect** drops a new `32×32` rectangle you can drag and resize by its corner handle. |

Auto-slice controls:

* **Alpha ≥** — pixels with alpha below this value are background (default `16`).
* **Min size** — smaller regions are ignored (default `2`), which filters dust/noise.

Auto-slicing is **4-connected**: diagonally touching pixels are separate regions. Overlapping artwork merges into one bounding box — fix those by hand in Manual mode.

## 3. Arrange

* Tap a frame on the sheet (or its row in the list) to select it.
* **Rename** / long-press a frame name to name frames (`run_00`, `idle_3`…).
* **▲ / ▼** reorder frames — order is playback order.
* **Delete / ✕ / Clear** remove frames.
* Manual mode: tap empty space to create a rect, drag to move, drag the yellow corner handle to resize, **pinch** to zoom, drag the background to pan, **double-tap** to fit.

## 4. Preview

The left panel plays the ordered frames at the document's **FPS** with **Loop** on/off. ⏭⏮ step frame-by-frame; ⏯ toggles playback.

## 5. Export

**Export** → name stem, padding, power-of-two toggle. Given the stem `Hero` it writes:

| Asset | Contents |
|---|---|
| `Hero.sheet` | The document (texture, mode, grid settings, fps, loop, named frames) |
| `Hero.png` | **New packed sheet** — your frames shelf-packed with padding, power-of-two sized |
| `Hero.atlas` | Named frames with **UVs** (`u0, vBottom, u1, vTop`) **and pixel rects** (`x, y, w, h`) |
| `Hero.anim` | Named-frame animation clip: `atlas` + `frameNames` in playback order |
| `Hero.png.texture` | Nearest-filter profile (crisp pixels, no mipmaps) |

> Re-exporting the same sheet **overwrites** `Hero.png/.atlas/.anim/.texture` in place, so existing scene and Animator references stay valid.

## 6. Use it in a game

1. Select a GameObject → add an **Animator** component → **Clip** → `Hero.anim`.
2. Add (or keep) a **SpriteRenderer** on the same object — the Animator drives its texture and UVs every frame, frame by frame, through the atlas.
3. Adjust **FPS**, **Loop** and **Speed** either in the `.anim` (Animation Editor) or on the component.

Grid-based clips (uniform `columns/rows` sheets) keep working exactly as before; the Animation Editor detects named-frame atlas clips and shows them read-only — frames are edited in the Sprite Sheet Editor, while fps/loop stay editable there.

### Scripting

* `assets.list("SHEET")` lists sprite-sheet documents.
* `assets.validate(name)` validates a `.sheet` document.
* The exported `.anim` works with the existing `Animator` component — no new runtime API is required.

## Atlas / anim format

```json
// Hero.atlas
{
  "texture": "Hero.png",
  "columns": 1, "rows": 1,
  "frames": { "run_a": [0.031, 0.5, 0.281, 0.0], ... },
  "rects":  { "run_a": [2, 2, 16, 16], ... }
}
```

```json
// Hero.anim  (named-frame clip)
{
  "texture": "Hero.png", "columns": 1, "rows": 1,
  "frames": [0], "fps": 12.0, "loop": true,
  "atlas": "Hero.atlas",
  "frameNames": ["run_a", "run_b", ...]
}
```

Legacy grid clips simply omit `atlas` / `frameNames` and keep the original schema.

## Verification

`app/src/test/java/com/sengine/SpriteSheetTest.kt` covers auto-slicing (threshold, min size, connectivity, reading order), grid/cell slicing, the shelf packer (bounds, overlap, power-of-two, oversize rejection), document round-trips, `AssetKind.SHEET` validation, pixel-rect atlases, the full export pipeline (including re-export stability) and named-frame playback through `AnimationSystem`.
