# Phase 2 Report — S Engine Big Edition

**Branch:** `arena/5831a3b7-assets2`
**Implementation commit:** `00293f3415e5cbc2d82d015e29d9d099e81884d1` (`00293f3`)
**Preservation tag:** `phase-2-big-edition` (annotated, created on the report commit)
**Runtime:** S Engine 3.0.0, Android `versionCode 4`, `versionName "3.0.0"`
**Baseline:** historical Phase 1 remained intact at `0f53b49` / tag `phase-1-working`.

## Outcome

Phase 2 is a real expansion of the existing Android/Kotlin engine, not a rewrite or mockup. All Phase 1 systems are preserved: the Rhino sandbox and instruction budget, runtime reset/release, atomic project storage, scripting lifecycle, physics queries, editor shortcuts, GL teardown and the audited API. Big Edition adds validated project data, new runtime components, renderer features, editor workflows, a playable template, documentation and headless regression tests.

The final offline build passed:

```
main ok: 218 classes
tests ok: 21 classes
API AUDIT documented=135 runtime=177 prelude=31 missing=[]
RESULT passed=54 failed=0 skipped=1
```

The single skip is the pre-existing `ApkExportTest.exportStandaloneGameApk`, which requires the external export/SDK environment. The complete output is saved in [`PHASE2_TEST_LOG.txt`](PHASE2_TEST_LOG.txt). Final command:

```bash
cp tools/offline-build/build.sh /home/user/toolchain/build.sh
timeout 300 bash /home/user/toolchain/build.sh all
```

## What was added

### Asset model and validation

- `core/Prop.kt` — expanded `AssetKind` to 13 kinds: texture, script, sound, shader, animation, model, prefab, material, atlas, tilemap, scene, font and text. Added extension/binary helpers and `ofName`.
- `core/BigEditionData.kt` — `TilemapData`, `MaterialAsset`, `SpriteAtlas`, `PrefabAsset` hierarchy capture/instantiation and `AssetValidator`.
- `project/BigEditionAssets.kt` — valid starter content for all new text asset kinds.
- `project/BigEditionLibraries.kt` — `MaterialCache` and `AtlasCache` with modification-time invalidation.
- `render/Textures.kt` — custom `.ttf`/`.otf` font loading with safe fallback to the Android default face.

Validation is shared by the editor, imported text assets, the data editor, `SAssets.writeText` and the regression tests. Binary assets and traversal names are rejected by the scripting bridge.

### Components and physics

- `core/BigEditionComponents.kt` — `Tilemap`, `UIFrame`, `UICanvas`, `UIRect`, `UIImage`, `UIText`, `UIButton`, `UIProgressBar`, `PhysicsMaterial2D` and `Joint2D`.
- `core/Components.kt` — 25-type registry/categories; material, atlas/frame and font fields; mesh/sprite `copyFrom`; orthographic camera and spot-light fields; polygon `Collider2D` vertices, `worldPoints` and a CCW convex-hull parser.
- `physics/PhysicsWorld.kt` — fixed-step 2D simulation with box/circle/convex-polygon SAT collisions, solid tilemap cells, material overrides, distance/spring joints, CCD, impulses, friction, restitution, triggers, sorted raycasts and overlap queries. Existing box/circle, kinematic-carry, layer/mask and script behaviour is preserved.
- `Engine.kt` / `UISystem.kt` — screen-space UI hit testing before script updates, runtime button reset and `onClick(label)` / `onUIButtonClick(label)` dispatch.

### Rendering

- `render/SceneRenderer.kt` — asset-backed materials, atlas UV frames, custom-font text, tilemap quads, Canvas UI pass with sorting/opacity/progress/buttons, orthographic camera wiring, polygon collider overlays and spot-cone gizmos.
- `render/Renderer3D.kt` / `render/Shaders.kt` — two nearest spot lights with direction, range, colour, inner/outer cone and penumbra falloff in the mesh fragment shader; active point/spot counts are test-visible.
- `render/View3D.kt` — real orthographic projection matrix using `Mat4.ortho` and a configurable half-height.

### Script API

- `script/Api.kt` — `setMaterial`, `setSpriteFrame`, `setFont`, `setSpotCone`, `setOrthoSize`, UI/text/progress helpers, tilemap getters/setters, joint helpers, polygon vertices, prefab spawning, scene snapshots and the safe `SAssets` bridge. Lower-case Rhino aliases make `self.uiProgress` and `self.uiText` work as documented.
- `script/ScriptSystem.kt` — UI button callbacks, `assets` global, root-only scene attachment and duplicate-instance protection.
- `script/ScriptContextFactory.kt` — allow-lists `SAssets`.
- `script/ApiReference.kt` and [`docs/SCRIPTING_API.md`](SCRIPTING_API.md) — canonical v3.0.0 reference. The audit now covers `SAssets` and the new lifecycle hooks.

### Editor, projects and store

- `ui/EditorActivity.kt` — `+ Data` menu, font import, new object presets (tilemap, polygon physics, material surface, joints, Canvas UI, spot light, orthographic camera), data asset icons/menus, prefab capture/instantiate, material/atlas/tilemap/font application, scene snapshot loading and validated creation.
- `ui/ScriptEditorActivity.kt` — data-asset validation/status/format reference; invalid data cannot be saved without an explicit override.
- `project/AssetLibrary.kt` — **Data Assets** category, a Big Edition Pack and standalone installable material/atlas/tilemap/prefab/UI-script/snapshot/text items.
- `project/Templates.kt` — appended **Big Edition Showcase** so the existing positional Platformer / Space Shooter shortcuts remain unchanged. The template installs eight validated assets and builds a playable scene with a tap-to-spawn UI button.
- `app/build.gradle.kts`, `project/Project.kt`, `README.md`, [`docs/BIG_EDITION_GUIDE.md`](BIG_EDITION_GUIDE.md) — versioning and documentation.

## Bugs found and fixed during integration

1. `PrefabAsset.instantiate` called `Scene.duplicate` on the destination scene, so it looked for children in the wrong object list and lost prefab subtrees. It now deep-copies from the temporary prefab scene and preserves parent links.
2. Rhino exposes `getUIProgress` as `UIProgress`, not the documented `uiProgress`; lower-case aliases were added for UI progress/text.
3. `ScriptSystem.begin()` called recursive `attach()` for every flat scene object, registering child scripts multiple times. It now starts at root objects and guards against duplicate instances.
4. First-frame taps were cleared by the intended Play-mode input reset; the regression test starts Play before dispatching a UI tap. No runtime reset safety was removed.
5. Data assets are now validated before editor creation, script writes, store installation where applicable and snapshot loading.

## Test coverage

`BigEditionTest.kt` adds ten headless tests covering asset kinds/validation, material/atlas cache invalidation, prefab hierarchy and offsets, tilemap raycasts/collision, polygon hulls and point/ray queries, material restitution, distance joints, Canvas UI callbacks, the safe `assets` bridge/prefab spawning, orthographic projection, spot-light shader wiring and the full Big Edition Showcase template round-trip/runtime. `ApiAuditTest.kt` was extended to include `SAssets` and UI lifecycle hooks; the final audit reports `documented=135 runtime=177 missing=[]`.

Existing Phase 1 and v2 suites still pass. The showcase test verifies that a real UI tap logs the callback, spawns a prefab and produces no runtime errors.

## Known limitations

- The offline suite uses Android/Rhino/JSON stubs; OpenGL shader compilation, EGL context loss/recreation, font rendering and multi-touch behaviour still need on-device validation.
- GLSL source is validated by editor status but not precompiled by the JVM suite; the GL runtime remains the compiler of record.
- `ApkExportTest.exportStandaloneGameApk` remains skipped without the external export environment/SDK.
- Supabase and Vercel were connected but not used; no cloud service was required by this upgrade.
- PBR ray-traced shadows, real-time shadow maps, skeletal animation, dockable/resizable panels and expanded multi-select remain future work; the candidate Phase 1 backlog items implemented here are data assets, prefab/scene serialization, tilemaps, full Canvas UI, fonts, polygon colliders, joints/materials, spot lights and orthographic 3D cameras.

## Preservation

Phase 1 remains distinct at commits `8922d9a` and `0f53b49`, with annotated tag `phase-1-working`. Phase 2 implementation is preserved at `00293f3`; this report is committed immediately afterward and tagged `phase-2-big-edition`. The feature guide is [`BIG_EDITION_GUIDE.md`](BIG_EDITION_GUIDE.md), the canonical API is [`SCRIPTING_API.md`](SCRIPTING_API.md), and the final test output is [`PHASE2_TEST_LOG.txt`](PHASE2_TEST_LOG.txt).
