# S Engine Phase 3 — complete model and sprite workflows

**Runtime:** S Engine 3.1.0, Android `versionCode 5`, `versionName "3.1.0"`

Phase 3 is an additive expansion of the Phase 2 Big Edition runtime. All Phase 1 reliability work and all Phase 2 data assets, Canvas UI, physics, rendering, scripting and editor workflows remain available. This guide covers the new complete-model-bundle, sprite-editing, texture-profile and track-based 3D-animation systems. For the authoritative JavaScript reference, see [`SCRIPTING_API.md`](SCRIPTING_API.md); for verification and file-by-file results, see [`PHASE3_REPORT.md`](PHASE3_REPORT.md) and [`PHASE3_TEST_LOG.txt`](PHASE3_TEST_LOG.txt).

## 1. Screenshot-format catalogue

The built-in asset store now mirrors the supplied model-store screenshot with these categories:

* Blender Models
* FBX Models
* Cinema 4D Models
* 3ds Max Models
* Maya Models
* Obj Models
* Rigged
* Animated Models
* Lowpoly Models
* ZIP / RAR / ESR Packs

`AssetKind` accepts the corresponding extensions, including `.blend`, `.fbx`, `.fbxa`, `.c4d`, `.3ds`, `.max`, `.ma`, `.mb`, `.obj`, `.mtl`, `.gltf`, `.glb`, `.dae`, `.stl`, `.ply`, `.off`, `.bvh`, `.anim3d`, `.usd`, `.usdz`, `.vox`, `.md2`, `.md3`, `.md5mesh`, `.smd`, `.lwo` and `.lws`. The store also adds **3D Animations**, **Texture Settings**, **Sprite Editor** and **Archive Bundles** categories.

### Runtime decoding vs. authoring sources

The runtime mesh pipeline decodes open interchange formats:

* Wavefront `.obj` with `.mtl` diffuse texture lookup
* ASCII and binary `.stl`
* ASCII `.ply` and `.off`
* `.gltf` / `.glb` JSON and binary buffers, including Base64 data URIs
* COLLADA `.dae` vertex positions and triangle lists
* ASCII FBX `Vertices` / `PolygonVertexIndex` takes
* legacy binary `.3ds` main/editor chunks

Closed proprietary DCC files — `.blend`, `.c4d`, `.max`, `.ma`, `.mb` and binary FBX — are **retained as authoring sources** in a bundle. They are not falsely claimed as native in-app decoders. A complete model pack links them to an open `renderModel` companion such as OBJ or glTF; `.modelpack` metadata records the conversion decision. This is the practical mobile strategy for "all requested model categories" without shipping native Blender, Cinema 4D, 3ds Max or Maya libraries.

## 2. One-archive complete model bundles

Use **Import Bundle** in the editor Assets panel, or the project manager's **Import** action, to install one archive. The asset menu also offers **Install Bundle** on an imported `.zip`, `.rar` or `.esr` asset. The installer:

1. Extracts all safe archive members into the project's `assets/` directory.
2. Reads an optional `manifest.json`, `bundle.json`, `model.json`, `package.json`, `*.modelpack` or JSON `.esr` manifest.
3. Keeps proprietary DCC source files as source assets.
4. Resolves a runtime `renderModel` and a primary texture.
5. Generates or normalizes a `.mat` material, 2D `.anim` clip, 3D `.anim3d` clip, `.texture` profile and `.modelpack` metadata.
6. Creates a scene object wired with `MeshRenderer`, `Animator` and `ModelAnimator`; named 3D tracks become child joint objects.
7. Saves the target scene and updates the start scene when needed.

Manifest fields include `name`, `model`, `renderModel`, `textures`, `primaryTexture`, `material`, `spriteAnimation`, `animation3d`, `spriteSheet`, `columns`, `rows`, `frames`, `fps`, `scene`, `objectName` and `createObject`. Missing fields are inferred from archive contents.

### Archive support

| Format | Status |
|---|---|
| Standard ZIP | Fully supported; deflated and stored entries through `ZipInputStream` |
| `.esr` JSON | Fully supported; Base64/text members under `files` or `entries` |
| `ESR1` + ZIP | Fully supported; the four-byte prefix is skipped and the ZIP payload is read |
| RAR 4.x / 5.x store mode | Supported without native libraries; uncompressed members are copied |
| Compressed RAR members | Rejected with an actionable "re-save as ZIP, ESR or RAR store mode" message |
| 7-Zip | Recognized but rejected with "re-save the bundle as ZIP or ESR" |

`.esr` is retained as a first-class bundle alias. Zip-slip paths, absolute paths, drive letters, member-count limits, per-file limits and 128 MB archive limits are enforced.

### Built-in complete bundles

The store includes **Fruit Shop Complete Model Pack**, **Complete ZIP Bundle**, **Complete RAR Store Bundle** and **Fruit Shop JSON ESR**. Each installs a representative `.blend` and ASCII `.fbx` source, an OBJ/MTL render companion, a generated PNG texture, a material, a 2D sprite clip, a 3D track clip, a texture profile and a scene object. Format-guide items cover every screenshot category.

## 3. Texture profiles and decoding

`.texture` and `.texmeta` assets store validated per-image settings:

* `wrapS` / `wrapT`: repeat, clamp or mirror
* `minFilter` / `magFilter`: nearest or linear
* `mipmaps`, `srgb`, `premultiply`, `maxSize`
* `alphaCutoff`, `compression`
* `atlasColumns`, `atlasRows`, `spriteSheet`

The renderer resolves sidecar candidates such as `Albedo.texture`, `Albedo.png.texture` and `Albedo.texmeta`; the GL cache stamp includes the profile file so edits trigger a reload. Android `BitmapFactory` continues to handle PNG/JPEG/WebP/BMP/GIF. Pure-JVM decoders add PNG (including filter types 0–4), TGA/RLE, PPM/PGM, DDS DXT1/3/5, KTX and Radiance HDR. KTX2 and OpenEXR are recognized as asset kinds but are not decoded in this release. The 3D renderer falls back to an OBJ `.mtl` `map_Kd` texture when a `MeshRenderer` has no explicit texture.

## 4. Professional sprite editor

Open **+ Sprite** from the Assets panel, choose **Edit in Sprite Editor** on a `.sprite` asset, or import an image into the editor. `SpriteEditorActivity` is registered in `AndroidManifest.xml` and provides:

* Layers, frames, duplicate frame, delete frame, layer visibility, reorder and delete
* Pencil, eraser, line, rectangle, ellipse, flood fill and eyedropper
* Palette, current-colour swatch, adjustable brush size and filled-shape toggle
* Onion skin, checkerboard transparency, pixel grid, zoom and frame playback
* Undo / redo, horizontal / vertical flip and 90° rotation
* FPS, frame count and dirty-state status

The underlying `SpriteDocument` is pure JVM and serializes ARGB layers as Base64 frames. **Save** writes the `.sprite` document and exports a PNG sheet, `.atlas`, `.anim` clip and `.png.texture` profile. `SpriteAssets.export` lays frames out in a grid bounded by a conservative 4096 px GL limit, computes named UVs and writes nearest-neighbour sprite-sheet settings.

## 5. Track-based 3D animation

`Animation3DClip` normalizes JSON `.anim3d`, glTF-style `.glanim` / `.clip`, BioVision `.bvh` and FBX-ASCII takes to named `AnimationTrack`s with times, positions, rotations, scales and interpolation modes. `Animation3DImporters` validates at least one channel per track and monotonic times.

`ModelAnimator` is registered as the 26th component type. `Engine.updateModelAnimators` loads a clip cache, advances `time` by `dt * speed`, samples the pose and drives named joint objects or the owner transform in both EDIT and PLAY modes. New script helpers:

```js
self.playModelAnimation("FruitShop.anim3d");
self.setModelAnimationSpeed(1.25);
self.setModelAnimationTime(0.5);
self.stopModelAnimation();
```

The asset menu's **Play 3D on** action assigns a clip to the selected object. The canonical API reference documents these helpers, and `ApiAuditTest` verifies that each documented function exists in the runtime.

## 6. Validated data assets

`AssetValidator` now supports `ANIMATION3D`, `SPRITE`, `TEXTURE_META` and `MODELPACK` in addition to Phase 2 kinds. It checks 3D clip tracks, sprite layer/frame consistency, texture-profile ranges and model-pack `renderModel` / texture fields. The editor's `+ Data` menu adds 3D Animation, Sprite Document, Texture Profile and Model Pack templates. The Script Editor uses the same validator, so saved data assets cannot silently drift from runtime expectations.

## 7. Verification and explicit limits

The offline JVM suite compiles the real Kotlin engine against Android/Rhino/JSON stubs and runs every `*Test.kt`. `Phase3Test.kt` covers screenshot categories and asset kinds, ZIP / ESR JSON / ESR1+ZIP / RAR-store installation, zip-slip safety, wired scene objects, OBJ/MTL runtime rendering, PNG decoding, sprite editing / undo / serialization / export, 3D clip sampling, validator acceptance / rejection, component registration, API documentation, manifest registration and version consistency. See [`PHASE3_TEST_LOG.txt`](PHASE3_TEST_LOG.txt) for the final output.

Explicit limits, by design:

* No claim of binary Blender, Cinema 4D, 3ds Max, Maya or compressed-RAR native decoding. Source files are preserved and linked to open runtime companions.
* 7-Zip and proprietary compressed RAR members are rejected with actionable errors rather than silently dropped.
* KTX2 and OpenEXR are recognized asset kinds but are not decoded in this release.
* GL texture upload, mipmap generation, Android file pickers, on-device touch interactions and the Activity lifecycle are compile-checked and wired, but require on-device validation. The headless suite exercises pure JVM and data paths.
* Standalone APK export remains skipped in the offline suite unless its Android SDK/export environment is provided.
