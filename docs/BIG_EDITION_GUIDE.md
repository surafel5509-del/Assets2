# S Engine Big Edition — feature guide

**Runtime 3.0.0 Big Edition** keeps the complete Phase 1 engine intact and adds real project, runtime, editor and scripting systems. **Phase 3 extends this baseline to runtime 3.1.0** with complete model bundles, a sprite editor, texture profiles and track-based 3D animation; see [`PHASE3_GUIDE.md`](PHASE3_GUIDE.md). For the authoritative JavaScript reference, see [`SCRIPTING_API.md`](SCRIPTING_API.md); for verification and file-by-file results, see [`PHASE2_REPORT.md`](PHASE2_REPORT.md) and [`PHASE3_REPORT.md`](PHASE3_REPORT.md).

## 1. Data assets

Use **Assets → + Data** to create a validated starter file. The editor and the `assets` scripting bridge reject malformed JSON, binary names and path traversal.

| Extension | Purpose | Editor actions |
|---|---|---|
| `.prefab` | Reusable object hierarchy with local transforms and components | **Instantiate**, **Capture selection**, **Edit** |
| `.mat` | Colour/texture/specular/emission/shader material definition | **Apply to selected**, **Edit** |
| `.atlas` | Named UV rectangles inside a sprite sheet | **Assign atlas to selected**, **Edit** |
| `.tilemap` | Grid map, tileset, palette and tile size | **Create Tilemap Object**, **Load onto selected**, **Edit** |
| `.scene` | Editable scene snapshot JSON | **Load Snapshot**, **Edit** |
| `.txt` | Notes, dialogue, CSV or custom JSON data | **Edit** |
| `.ttf` / `.otf` | Binary custom font imported from the device | **Use font on selected** |

The data editor validates as you type. Invalid files are flagged in the status bar and cannot be saved without an explicit override. `assets.readText`, `assets.writeText`, `assets.exists`, `assets.validate` and `assets.list` expose the same validation to scripts.

## 2. Prefabs

1. Select an object and its children in the hierarchy.
2. Create a `.prefab`, or use **Capture selection** on an existing prefab.
3. Use **Instantiate** to place the hierarchy at the viewport centre, or call `scene.spawnPrefab("Enemy.prefab", x, y)` from JavaScript.
4. Prefab roots keep their local offset; component data, child hierarchy and runtime reset state are preserved.

`scene.saveSnapshot("Checkpoint")` writes the live scene as a `.scene` asset. Use **Load Snapshot** in edit mode to replace the current scene; undo history still provides a restore point.

## 3. Canvas UI

Create a **Canvas UI** preset from the object menu, or add the components individually:

- `UICanvas` — stretch / aspect-fit / pixel-perfect reference scaling, opacity, interactivity and sort order.
- `UIRect` — normalised anchor, size and pivot.
- `UIImage`, `UIText`, `UIButton`, `UIProgressBar` — screen-space drawables. Buttons dispatch `onClick(label)` and `onUIButtonClick(label)` on a `Script` attached to the same object.

Example button callback:

```js
function onClick(label) {
    log("Pressed " + label);
    var bar = scene.find("Progress");
    if (bar) bar.uiProgress = Math.min(1, bar.uiProgress + 0.25);
    scene.spawnPrefab("Enemy.prefab", 4, 3);
}
```

`self.uiText`, `self.uiProgress`, `isUIClicked()`, `setUIRect(x, y, w, h)`, `setUIVisible(enabled)` and `setUIImage(name)` are available from scripts. UI input uses the same normalised canvas rectangle as the renderer; the top-most button wins a tap.

## 4. Materials, atlases, fonts and tilemaps

- Assign a `.mat` to a `SpriteRenderer` or `MeshRenderer`; the renderer reads a cached copy, so scene data is not mutated.
- Assign a `.atlas` to a sprite or `UIImage` and choose a frame name; atlas frames override four-component UVs.
- Import a `.ttf`/`.otf` font and assign it to `TextRenderer` or `UIText`. Failures fall back to the Android default face.
- A `Tilemap` draws textured or palette-coloured cells. Enable **Solid Collision** to create one collider per solid cell; `setTile`, `getTile` and `tileAtWorld` work at runtime.

## 5. Physics 2D

`Collider2D` now supports Box, Circle and Polygon shapes. Polygon vertices are parsed into a safe CCW convex hull, used by SAT collision, raycasts and overlap queries. `PhysicsMaterial2D` overrides friction, bounciness and density per object. `Joint2D` supplies distance or spring constraints with anchors, stiffness, damping, stress flags and optional breaking stretch. Fixed-step integration, CCD, kinematic carrying, impulses, triggers and all existing API queries remain active.

Useful script helpers: `setColliderVertices("x,y; x,y; ...")`, `setJointLength(v)`, `setJointTarget(name)`, `isJointStressed()`, `isJointBroken()`, `setTile(col, row, id)`, `getTile(col, row)` and `tileAtWorld(x, y)`.

## 6. 3D rendering

- `Camera3D.projection = 1` switches to an orthographic camera; `orthoSize` is the half-height in world units. `setOrthoSize(v)` does the same from a script.
- `Light.kind = 2` creates a spot light. `coneAngle`, `penumbra`, `range`, `color` and `intensity` feed the mesh shader's two nearest spot lights; the editor draws the cone.
- `setSpotCone(deg)` switches a light to Spot mode from JavaScript.
- `MeshRenderer.material` and `SpriteRenderer.material` reference `.mat` assets instead of baking material values into the scene.

## 7. Template and asset store

**Big Edition Showcase** is the last built-in template, so the existing Platformer / Space Shooter positional template shortcuts are unchanged. It installs eight validated assets, including a prefab, material, atlas, tilemap, snapshot and two scripts, then builds a playable scene with a tap-to-spawn UI button.

The built-in store gains a **Data Assets** category and a **Big Edition Pack** for projects created before v3.0.0. Phase 3 preserves all of these items and adds **Archive Bundles**, **Texture Settings**, **Sprite Editor**, **3D Animations** and the screenshot model-format categories.

## 8. Phase 3 extensions

Phase 3 is additive: every Big Edition workflow above still works. Use the **Import Bundle** button in the editor Assets panel, or the project manager's **Import** action, to install a complete ZIP / ESR / RAR-store model archive. The installer keeps proprietary authoring files, generates a `.modelpack` manifest and wires the runtime `renderModel`, texture, material, 2D clip, 3D clip and scene object. Use **+ Sprite** or the sprite asset menu to open the new layered Sprite Editor, then **Save** to export a PNG sheet, `.atlas`, `.anim` and `.texture` sidecar. Model clips can be assigned with the **Play 3D on** asset action or driven from JavaScript with `playModelAnimation`.

See [`PHASE3_GUIDE.md`](PHASE3_GUIDE.md) for the complete workflow and explicit format limits.

## 9. Verification

The JVM-only offline suite compiles the real Kotlin engine against Android/Rhino/JSON stubs and runs every `*Test.kt`:

```bash
bash tools/offline-build/bootstrap-toolchain.sh
cp tools/offline-build/build.sh /home/user/toolchain/build.sh
bash /home/user/toolchain/build.sh all
```

`BigEditionTest.kt` covers asset validation, cache invalidation, prefab hierarchy, tilemap collision, polygon queries, materials, joints, UI callbacks, the `assets` bridge, orthographic projection, spot-light wiring and the showcase template. `ApiAuditTest` verifies every documented function against the runtime, including `SAssets`, the new lifecycle hooks and Phase 3 model-animation helpers. `Phase3Test.kt` adds end-to-end archive, model-pack, sprite, texture, 3D-animation and version-consistency coverage. The Phase 2 and Phase 3 final outputs are saved in [`PHASE2_TEST_LOG.txt`](PHASE2_TEST_LOG.txt) and [`PHASE3_TEST_LOG.txt`](PHASE3_TEST_LOG.txt).
