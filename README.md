# S Engine — Ultimate Edition

**S Engine** is a 2D **and 3D** game engine **and** full visual editor that runs entirely on an Android phone or tablet – think "a small Unity in your pocket". Create a project, build scenes with a hierarchy / inspector / gizmos, write JavaScript behaviours in the built-in code editor, press **Play** to test immediately, then run your game full-screen.

**v2.1.0** added the professional upgrade: heightmap terrain with collision, AI steering (seek / flee / pursue / wander / patrol), object pooling, 2D + 3D raycasts, terrain height queries, distance fog controls, frustum culling, state-batched opaque rendering, a sun-lit sky, and three full game templates — **Flappy Bird**, **Chase** and **3D Open World**.

**v2.2.0** upgrades the runtime itself:

* **Professional script system** — full lifecycle (`start`, `update`, `onFixedUpdate`, `onLateUpdate`, `onEnable` / `onDisable`, `onCollision`, `onTrigger`, `onTap`, `onDestroy`, `onStop`), a **tween engine** (`tween(obj, "x", 10, 0.5, "backOut")` with 7 easing curves), **CommonJS modules** (`require("Utils.js")`), a math toolkit (`damp`, `smoothstep`, `mapRange`, `wrap`, `lerpAngle`, `rotateToward`, `dist`, `randomPick`, …), timers (`after` / `every` / `wait` / `cancelTimers`), `time.scale` (pause / slow-motion), `time.delta`, `scene.broadcast`, `input.up/down/left/right` + `input.key(code)`, and `assert()`.
* **Most powerful physics engine yet** — **collision layers & masks** (16 layers per collider), **continuous collision detection** (fast bullets never tunnel), **max speed clamps**, **kinematic platform carrying** (riders move with moving platforms), **ground normals** for slope-aware gameplay, **overlap queries** (`overlapCircle` / `overlapSphere`), **sorted multi-hit raycasts** (`raycastAll` in 2D & 3D), plus instant `addImpulse` from scripts.
* **3D audio engine** — positional `audio.play3d(name, x, y, z)` / `play3dLoop` with **distance attenuation + live stereo panning** against the camera listener, `move3d` / `stop3d` handles, falloff profiles (`setFalloff`), SFX/music **bus volumes**, and a **music channel** (`playMusic` / `pauseMusic` / `resumeMusic` / `setMusicVolume`) — all backed by a pure, unit-tested `Audio3DMath` core.

**Phase 1 reliability pass** (architecture inspection first, then targeted fixes — no engine rewrite):

* Rhino scripts now run in a real sandbox: safe standard objects, an allow-list `ClassShutter` and a per-frame instruction/time budget. Host Java/file/network access is denied and infinite loops are aborted with a visible console error.
* Play / Stop resets time scale, profilers, physics accumulators, pending scene loads, input and object pools; `onStop()` is delivered on the owning GL thread before teardown.
* Physics correctness: 3D gravity now applies all three axes, `overlaps()` uses the real box/circle collider manifolds, and kinematic platform carrying works in either broadphase pair order.
* Persistence safety: atomic scene writes with `.bak` recovery, extensionless-safe asset naming, strict zip-slip validation, and exclusion of hidden temp/backup files from exports and standalone APKs.
* One audited scripting API reference shared by the editor, Markdown docs and an automated test; JS/GLSL/Blueprint editor status, Rhino syntax validation, and real Ctrl+S / Ctrl+Z / Ctrl+Y / Ctrl+P shortcuts.
* GL programs, textures and FBOs are explicitly released on the GL thread when the editor or player is destroyed.

See `docs/PHASE1_REPORT.md` for the full Phase 1 file list, test results and remaining work.

> Written from scratch in Kotlin. OpenGL ES 2.0 renderer, custom physics, Mozilla Rhino JavaScript runtime. No NDK, no external game frameworks.

---

## Features

| Area | What you get |
|---|---|
| **Project manager** | Create from templates, open, play, rename, duplicate, delete, export / import projects as `.zip` |
| **Scene editor** | Viewport with grid, pan (drag) & pinch-zoom, tap-to-select, **Move / Rotate / Scale gizmos** with axis handles, snapping, frame selected |
| **Hierarchy** | Parent/child tree, collapse, visibility toggle, rename / duplicate / delete / reorder / create child / unparent |
| **Inspector** | Edit name, tag, sorting order, parent, transform (drag labels to scrub values), every component property, color picker, asset pickers, add / remove / reorder / reset components |
| **Undo / Redo** | Snapshot-based history for every edit; Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z shortcuts |
| **Play mode** | Play / Pause / Step frame inside the editor; scene is restored when you stop (like Unity); Ctrl+P toggles Play/Stop |
| **Rendering** | Squares, circles, triangles, textured sprites (PNG/JPG/WebP) with flip, text, particles, sorting order, camera background |
| **3D rendering** | Perspective camera (follow / look-at / shake / post-FX), primitive + OBJ meshes, per-vertex lighting (sun + 4 point lights), sky with sun disc, distance fog, Toon / Pulse / custom shaders |
| **Terrain** | Procedural heightmap (FBM value noise), texture tiling, baked mesh cache, **collides with rigidbodies**, `scene.terrainHeight(x, z)` queries |
| **AI steering** | `Steering` component: Seek, Flee, Pursue (lead prediction), Wander, Patrol — acceleration-limited movement with optional face-the-movement |
| **Object pooling** | `scene.pool("Enemy", 16)` → `scene.obtain(...)` / `scene.release(obj)` — pre-warmed, reused instances, runtime state reset |
| **Raycasts** | `scene.raycast2D(...)` and `scene.raycast(...)` (3D, incl. terrain) against all colliders |
| **Culling & batching** | Per-frame frustum culling (Gribb–Hartmann planes) + opaque draws grouped by shader/texture to cut GL state changes |
| **Script system** | Full lifecycle incl. fixed/late/update-enable hooks, tween engine, `require()` modules, math/timer toolkit, time scale, broadcast messaging |
| **Physics 2D/3D** | Layers & masks, CCD, max-speed clamps, platform carrying, ground normals, overlap queries, sorted `raycastAll`, impulses |
| **3D audio** | Positional play/loop with live pan + attenuation, listener follows the camera, falloff ranges, SFX/music buses, MediaPlayer music channel |
| **Physics 2D** | Dynamic / Kinematic / Static rigidbodies, box & circle colliders, gravity, bounciness, friction, drag, triggers, collision & trigger callbacks, `grounded` detection |
| **Scripting** | JavaScript (ES6 subset via Rhino) with `start`, `update(dt)`, `onCollision`, `onTrigger`, `onTap`… plus timers, spawning, messaging |
| **Code editor** | Syntax highlighting, auto-indent, undo/redo, quick-symbol keyboard row, built-in API reference, Rhino syntax validation, Ctrl+S / Ctrl+Z / Ctrl+Y |
| **Assets** | Import images and sounds from the device, create scripts, preview / assign / attach from the Assets panel |
| **Audio** | `AudioSource` component + `audio.play()` / `audio.beep()` |
| **Input** | On-screen joystick + A/B buttons, touch position / taps in world space, hardware keyboard & gamepad (WASD / arrows / Space / Enter) |
| **Scenes** | Multiple scenes per project, start scene, `scene.load("Level2")` |
| **Player** | Full-screen runtime ("Build & Run") with landscape / portrait setting |

### Built-in components
**2D:** `SpriteRenderer`, `TextRenderer`, `Camera` (size, background, follow target with smoothing), `Rigidbody2D`, `Collider2D`, `Script`, `ParticleEmitter`, `AudioSource`.
**3D:** `MeshRenderer` (primitives / OBJ, shaders, emission), `Camera3D` (follow / look-at / FOV / post-FX), `Light` (directional sun + point lights), `Rigidbody3D`, `Collider3D` (box / sphere), `Terrain`, `Steering`, `Animator`.

### Templates
* **Empty 2D** – camera + square
* **Platformer Demo** – run, jump, moving platform, coins with particle bursts, score UI
* **Space Shooter** – spawning enemies, bullets, explosions, score, game over
* **Physics Sandbox** – tap to drop bouncy balls and crates onto a pyramid
* **3D Demo** – lights, physics, OBJ models, shaders, character controller
* **Animated Platformer** – skeletal-style clip animations, parallax, moving hazards
* **Blueprint Demo** – visual node graphs compiled to JavaScript
* **Flappy Bird** – one-button classic: scrolling pipe gaps, scoring, game over / retry
* **Chase** – top-down survival: steering hunters, arena obstacles, respawning pickups
* **3D Open World** – heightmap hills with collision, forests, roaming hero, collectible crystals

---

## Scripting example

```js
// Player.js  – params on the Script component: "speed=6, jump=11"
var coins = 0;

function update(dt) {
    self.vx = input.axisX * speed;              // joystick / A-D keys
    if (input.aDown && self.grounded) {         // A button / Space
        self.vy = jump;
        audio.beep();
    }
    if (self.y < -12) scene.reload();
}

function onTrigger(other) {
    if (other.tag == "Coin") {
        coins++;
        var fx = scene.spawn("CoinFX", other.worldX, other.worldY);
        fx.burst(24);
        after(1.5, function () { fx.destroy(); });
        other.destroy();
        scene.find("ScoreText").text = "Coins: " + coins;
    }
}
```

### API reference

The complete, runtime-audited JavaScript API is in [`docs/SCRIPTING_API.md`](docs/SCRIPTING_API.md). That file, the in-editor **API** button and `ApiAuditTest` all consume the same `ApiReference.kt` constant, so the documentation cannot drift away from the runtime without a test failure. It covers lifecycle hooks, `self`/`transform`/`gameObject`, physics and 3D properties, scene queries, object pools, audio, timers, tweens, modules, parameters and the safety model.

Inactive objects make great **prefab templates** – `scene.spawn("Enemy", x, y)` clones them and activates the copy.

---

## Download

Every push is built by GitHub Actions. Grab the APK from:

* **Releases → "S Engine – latest build"** → `SEngine.apk`, or
* **Actions → latest "Build S Engine APK" run → Artifacts → `SEngine-debug-apk`**

Enable "Install unknown apps" for your browser / file manager, then open the APK.

## Building

Requirements: JDK 17 and the Android SDK (API 34). Android Studio Hedgehog or newer works out of the box.

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push is also built by GitHub Actions (`.github/workflows/build-apk.yml`); download the APK from the run's **Artifacts** section.

### Headless regression tests

The JVM-only toolchain compiles the real Kotlin engine against Android/Rhino/JSON stubs and runs every `*Test.kt` with the headless `LocalRunner`. It exercises the actual engine loop, physics, script runtime, serialization and asset/project code — no Android emulator required:

```bash
bash tools/offline-build/bootstrap-toolchain.sh
cp tools/offline-build/build.sh /home/user/toolchain/build.sh
bash /home/user/toolchain/build.sh all
```

`build.sh` auto-discovers test classes, so adding a regression suite is enough to include it in CI locally. The standalone APK export test remains skipped unless the export environment (Gradle/Android SDK/configuration) is provided.

Minimum Android version: 8.0 (API 26). Requires OpenGL ES 2.0.

## Project layout

```
app/src/main/java/com/sengine/
├── engine/
│   ├── Engine.kt            main loop, play/pause/stop, camera follow, particles
│   ├── Input.kt, AudioSystem.kt
│   ├── core/                GameObject, Component, Prop system, components, Scene + JSON serializer
│   ├── math/Affine.kt       2D transforms
│   ├── physics/             impulse-based 2D physics
│   ├── render/              GLES2 renderer, textures/text, editor grid & gizmos
│   └── script/              Rhino JavaScript runtime + script API
├── project/                 project storage, zip import/export, templates
└── ui/                      Projects screen, Editor (hierarchy, inspector, viewport, assets, console),
                             Script editor, full-screen Player, joystick, color picker
```

Projects are stored in app-private storage as plain JSON scenes plus an `assets/` folder:

```
<project>/project.json
<project>/scenes/Main.scene.json
<project>/assets/Player.js, hero.png, jump.wav …
```

## License

MIT
