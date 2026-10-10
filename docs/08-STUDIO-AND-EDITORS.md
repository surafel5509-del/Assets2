# 08 — Studio, Editors and Asset Organisation

This document covers the editor-side features added on this branch. The source is
under `app/src/main/java/com/sengine/`. The rules in `00-ARCHITECTURE.md` still
hold: everything is offline, no AI features, no networking, no cloud. Assets ship
inside the APK.

## Asset folders

`project/AssetFolders.kt` maps each file to a branch in the Asset Library:

| Branch | Files |
|---|---|
| Scripts | `.js` |
| Blueprints | `.bp` |
| Animations | `.anim` |
| Shaders | `.glsl` |
| Models | `.obj` |
| Sprites / Studio | `.sprite`, and images created in the Sprite Studio |
| Sprites | other images |
| Audio / Music, Audio / SFX | sounds, split by name (music, theme, bgm, song, track, ambience, ambient → Music) |
| UI, Controls | `.ui.json`, `.ctrl.json` |
| Data | `.json`, `.csv`, `.txt` |

`Project.createFolder` and `Project.moveAsset` manage the folders. `unique()` gives
collision-free names.

## Sprite Editor Studio

`ui/SpriteEditorActivity.kt`, `ui/PixelCanvasView.kt`, `studio/SpriteDocument.kt`.

- **Tools:** pencil, eraser, bucket fill, line, rectangle and ellipse (outline and
  filled), eyedropper, lighten/darken, and move layer contents. Symmetry mirrors
  on X, Y, or both.
- **Layers and frames:** named layers with visibility, lock and opacity; up to 64
  frames with onion skin; a per-frame duration.
- **Undo:** whole-document snapshots, capped at 40 steps. A stroke is one step.
- **Limits:** 256 px sprite, 64 frames, 64 palette colours.
- **Import:** images are downsampled to 1024 px. Modes are new layer (contain or
  stretch) or replace the active layer (contain). Images over 256 px are
  box-downscaled.
- **To animation:** renders a horizontal sheet to `Sprites/Studio/<name>.png`, writes
  an `.anim` clip with the frame count, FPS and loop, and opens the Animation Editor.
- **File format:** `.sprite` is text with a `SPRITE 1` header, then per-layer
  deflated ARGB in base64. Decode rejects bad magic, mismatched frame counts and
  truncated data.

Pixel-canvas gestures: one finger draws, two fingers pan and pinch-zoom (1–96×). A
second finger cancels a stroke in progress.

## Shaders

`engine/render/Shaders.kt` and `engine/render/ShaderPresets.kt`.

- Sprite, mesh and post stages all use the same contract: a user shader defines
  `vec4 effect(vec4 color, vec2 uv)`. The stage wraps it.
- Presets (12 in 4 categories: Basic, Sprites, Effects, Post) use only the uniforms
  each stage declares (`uTime`, `uParam`, `uTex`, `uResolution`), so they need
  nothing beyond what the library provides.
- Presets are saved as `Shaders/<slug>.glsl`. The Shader Library screen can open
  one in the Script Editor.
- GLSL is compiled on device. A compile error is logged against the asset, and the
  object falls back to the default shader.

## UI Builder and Controls

`engine/overlay/Overlay.kt`, `ui/OverlayEditorActivity.kt`, `ui/OverlayView.kt`,
`ui/PlayOverlay.kt`.

- Elements are anchored and normalised. `hAnchor` is LEFT, CENTER or RIGHT,
  `vAnchor` is TOP, MIDDLE or BOTTOM, and offsets and sizes are screen fractions.
  That keeps layouts valid at any aspect ratio.
- Two kinds: **Controls** (`Controls/<name>.ctrl.json`) feed `input.rawActions`
  and the `a` and `b` buttons. **UI** (`UI/<name>.ui.json`) emit a tap event, which
  reaches scripts as `ui.<id>` and Blueprints as `On UI Tap`.
- Edit mode: drag to move, drag the bottom-right handle to resize, and snap to 1,
  2.5, 5 or 10 percent or off.
- The default control layout is a D-pad (`move`) and buttons `a` and `b`.

## Blueprints

`engine/blueprint/Blueprint.kt`. The palette is generated from `BlueprintNodes.all`,
grouped by category, so new nodes appear with no editor changes. Events and actions
map onto the Script API (see `04-SCRIPTING.md`).

## Verification status

Android-dependent Kotlin is **not compiled locally**. There is no Android SDK here.
Cross-file references were checked by grep, and the pure-Kotlin layer has 29
passing unit tests. The full build runs in CI, which is the authoritative check.
Shaders are unverified until they run on a device.
