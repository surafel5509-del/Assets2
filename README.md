# S Engine 3.0.0 — Professional Studio Edition

**S Engine** is a completed 2D **and 3D** game engine **and** full visual editor that runs entirely on an Android phone or tablet. Create a project, build scenes with a hierarchy / inspector / gizmos, write JavaScript behaviours in the built-in code editor, press **Play** to test immediately, then run your game full-screen or export a standalone APK.

**v3.0.0 “Professional Studio Edition”** is the final big-engine upgrade. All v2.2 features remain — terrain, 3D audio, blueprints, animation, asset store, collision layers, CCD, post FX and APK export — and the runtime now adds a deterministic simulation core, future-physics systems, a professional script runtime, a camera director, a two-year project system, studio diagnostics, and two complete reference games.

> Written from scratch in Kotlin. OpenGL ES 2.0 renderer, custom physics, Mozilla Rhino JavaScript runtime. No NDK, no external game frameworks. Minimum Android 8.0 (API 26).

---

## What v3 adds

| Area | Professional Studio Edition |
|---|---|
| **2-year project system** | `project.json` schema v3 with forward migrations, per-project simulation/quality settings, a persisted 24-month `roadmap.json`, milestone sign-off, diagnostics, and a `studio` JavaScript API. See [the full plan](docs/ROADMAP.md). |
| **Deterministic simulation** | Fixed-step `SimulationClock` (30–240 Hz), bounded substeps, interpolation alpha, seeded xorshift32 RNGs for scripts and simulation, reproducible physics and replay-friendly ordering. |
| **Future physics** | Spatial-hash broad phase, iteration budgets, 2D/3D constraint joints (distance / spring / rope / weld), Verlet ropes and cloth, wind and radial impulses, swept circle / box / sphere shape casts, explosion / attraction / wind force fields, CCD, triggers, layers and kinematic platform carrying. |
| **Better script system** | Ordered event bus, ES6-generator coroutines (`waitSec`, `waitFrames`, `waitUntil`, `waitFor`), finite-state machines with enter/update/exit hooks, hot reload with a durable `persist` bag, per-call instruction budgets, profiler timings, deterministic RNG/events, CommonJS modules, tweens, timers, lifecycle hooks, and durable `game.save` / `game.load`. |
| **Better camera system** | Priority-aware 2D/3D camera rigs, dead zones, bounds, look-ahead, trauma shake with decay, smooth blends, snapping, pans, multi-camera activation, and Catmull–Rom cinematic dolly paths. |
| **Powerful editor** | Settings, diagnostics and roadmap dialogs; advanced camera / joint object templates; component inspector; undo/redo; hierarchy; gizmos; script/shader editors; asset pipeline; project import/export; APK export. |
| **Two full games** | **Citadel of Echoes** (six-scene 2D action adventure) and **Neon Apex GP** (three-scene 3D arcade racer), both playable from menu to results with saves, AI, HUDs and v3 APIs. |
| **Release engineering** | Versioned `3.0.0` app, active-branch CI APK workflow, 39 headless regression tests, and project diagnostics that report schema, scenes, assets, simulation settings and roadmap health. |

A deeper architecture and release matrix lives in [Professional Studio Edition](docs/PROFESSIONAL_STUDIO.md).

---

## Two complete reference games

### Citadel of Echoes — 6 scenes
A 2D action adventure with a title screen, two connected levels, a two-phase boss, victory and game-over screens. Includes combat, keys and doors, relics, healing, checkpoints via durable saves, patrol/chase enemies, moving platforms, particle bursts, joints, a Verlet lantern rope, shape-cast ground checks, area explosions, cinematic camera bounds / look-ahead / shake, event-driven HUD messages and a finite-state boss.

**Controls:** joystick / WASD to move, **A / Space** to jump, **B / Enter** to fire echo bolts, tap to advance menus.

### Neon Apex GP — 3 scenes
A 3D arcade racer with a cinematic title camera, a neon circuit, three laps, eight waypoint gates, three AI rivals, boost pads, a countdown coroutine, live HUD, best-lap / total-time saves, cloth banner physics, 3D area forces, camera trauma and a podium results screen.

**Controls:** joystick / A-D to steer, hold **A / Space** to accelerate, **B / Enter** to boost, tap to advance menus.

Both games are ordinary S Engine projects: their scenes are JSON, their behaviours are `.js` assets, and they appear in **New Project → templates**.

---

## Features

| Area | What you get |
|---|---|
| **Project manager** | 12 templates, open / play / rename / duplicate / delete, export / import projects as `.zip`, schema migrations |
| **Studio tools** | v3 project settings, 24-month roadmap with milestone sign-off, diagnostics, health report, `studio` script API |
| **Scene editor** | Viewport with grid, pan / pinch-zoom, tap-to-select, Move / Rotate / Scale gizmos, snapping, frame selected |
| **Hierarchy** | Parent/child tree, collapse, visibility toggle, rename / duplicate / delete / reorder / create child / unparent |
| **Inspector** | Transform, tag, order, every component property, colors, asset pickers, add / remove / reorder / reset, v3 joints and camera rigs |
| **Undo / Redo** | Snapshot-based history for every edit |
| **Play mode** | Play / Pause / Step frame in the editor; scene is restored when you stop |
| **Rendering** | Shapes, textured sprites, text, particles, sorting order, camera backgrounds, custom GLSL shaders and post FX |
| **3D rendering** | Perspective cameras, primitive + OBJ meshes, Blinn-Phong lights, fog, sky, frustum culling, state-batched opaque draws |
| **Terrain & AI** | Procedural heightmap collision, seek / flee / pursue / wander / patrol steering, object pooling |
| **Physics 2D/3D** | Dynamic / kinematic / static bodies, box / circle / sphere colliders, triggers, layers & masks, CCD, joints, soft bodies, shape casts, force fields |
| **Scripting** | ES6 subset via Rhino, full lifecycle, events, coroutines, FSMs, hot reload, budgets, modules, timers, tweens, durable saves |
| **Camera director** | Priority blends, dead zones, bounds, look-ahead, trauma shake, pan, snap, cinematic paths, 2D/3D parity |
| **Audio** | SFX, music channel, 3D positional audio with attenuation and stereo pan |
| **Input** | On-screen joystick + A/B, world-space touch, hardware keyboard and gamepad |
| **Scenes / Player** | Multiple scenes, start scene, `scene.load()`, full-screen player, landscape / portrait |
| **Export** | Installable standalone game APKs from a built S Engine APK |

### Built-in components
**2D:** `SpriteRenderer`, `TextRenderer`, `Camera` / `Camera2D` rig, `Rigidbody2D`, `Collider2D`, `Joint2D`, `Script`, `ParticleEmitter`, `AudioSource`, `Animator`.
**3D:** `MeshRenderer`, `Camera3D`, `Light`, `Rigidbody3D`, `Collider3D`, `Joint3D`, `Terrain`, `Steering`.

### Templates
* **Empty 2D**
* **Platformer Demo**
* **Space Shooter**
* **Physics Sandbox**
* **3D Demo**
* **Animated Platformer**
* **Blueprint Demo**
* **Flappy Bird**
* **Chase**
* **3D Open World**
* **Citadel of Echoes** — full 6-scene game
* **Neon Apex GP** — full 3-scene game

---

## Scripting example (v3)

```js
// Player.js — params on the Script component: "speed=7.5"
var cooldown = 0;

function start() {
    state.set("idle");
    persist.hp = 3;                         // survives hot reload
    camera.setBounds(-26, -14, 26, 16);
    camera.setDeadZone(2, 1.4);
    soft.rope("Lantern", -8, 8, 10, 0.35);
    events.on("pickup", function (kind) { flash(kind); });
    physics.setIterations(2);
    coroutine(function* () {
        yield waitSec(0.5);
        log("ready in " + studio.years() + "-year plan");
        yield waitFor("go");
        camera.playPath([[0,0,0],[8,4,5],[14,0,0]], 3);
    });
}

function idle_update(dt) { }             // called by state.update(dt)

function update(dt) {
    state.update(dt);
    self.vx = input.axisX * speed;
    var ground = physics.circleCast(self.worldX, self.worldY - 0.7, 0, -1, 0.25, 0.9, "Solid");
    if (input.aDown && (self.grounded || ground)) self.vy = 12;
    if (input.bDown && cooldown <= 0) { fire(); cooldown = 0.25; }
}

function fire() {
    var b = scene.spawn("Bolt", self.worldX + 1, self.worldY);
    if (b) { b.tag = "Bolt"; b.vx = 16; }
    physics.explode(self.worldX, self.worldY, 2, 3, "Enemy");
}

function onTrigger(other) {
    if (other.tag == "Relic") {
        game.save("relics", game.load("relics", 0) + 1);
        events.emit("pickup", "Relic");
        other.destroy();
    }
    if (other.tag == "Enemy" && !state.is("hurt")) {
        persist.hp--; state.set("hurt"); camera.shake(0.5);
    }
}

function onHotReload() { log("hp=" + persist.hp); }
```

Inactive objects make great prefab templates — `scene.spawn("Enemy", x, y)` clones them and activates the copy.

### API summary
* **Core:** `self` / `transform`, `input`, `scene`, `time`, `audio`, `log`, `after`, `every`, `tween`, `clamp`, `lerp`, `random`
* **v3 runtime:** `events`, `coroutine`, `waitSec`, `waitFrames`, `waitUntil`, `waitFor`, `state`, `persist`, `onHotReload`, `setRandomSeed`
* **v3 physics:** `physics.circleCast / boxCast / sphereCast / hitDistance / explode / attract / wind / joint / joint3 / setIterations`, `soft.rope / cloth / point / wind / force / explode / step`
* **v3 camera:** `camera.setBounds / setDeadZone / setLookAhead / zoomTo / shake / panTo / snap / setPriority / activate / playPath / stopPath`
* **v3 game/studio:** `game.save / load / has / keys / getVersion / diagnostics`, `studio.report / progress / milestones / next / complete / diagnostics`

The built-in **Script API Reference** in the script editor documents the complete surface.

---

## Project system

```text
<project>/project.json                  schema v3, engine/edition, settings, start scene
<project>/scenes/Main.scene.json         plain JSON scene
<project>/assets/Player.js, hero.png …    scripts, textures, sounds, shaders, models
<project>/roadmap.json                   24-month production plan + sign-off
<project>/userdata/save.json              durable game.save() progression
```

Old v1 / v2 projects auto-migrate to schema v3 without losing scenes, orientation or start scene. The editor's **⚙**, **🩺**, and **🗺** toolbar buttons open settings, diagnostics, and the two-year roadmap.

---

## Verification

Headless regression suite (offline JVM toolchain):

```text
RESULT passed=39 failed=0 skipped=1
```

The skipped case is the optional standalone-APK export test, which requires a CI source APK and signing keystore. The suite covers deterministic simulation, broad phase, joints, soft bodies, shape casts, force fields, camera rigs/director, events, schema migrations, script budgets, coroutines/FSM/hot reload, saves, all ten classic templates, both new full games, the two-year roadmap, project diagnostics and scene serialization.

CI (`.github/workflows/build-apk.yml`) assembles installable debug and release APKs with:

```bash
./gradlew assembleDebug assembleRelease --stacktrace
```

---

## Download / Building

Requirements: JDK 17 and Android SDK API 34. Android Studio Hedgehog or newer works out of the box.

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push to `arena/94ba1396-assets2` and every manual **Build S Engine 3.0.0 APK** workflow run uploads `SEngine-3.0.0-debug-apk` and `SEngine-3.0.0-release-apk` artifacts. A manual **workflow_dispatch** run of the same workflow (or a `[publish-apk]` commit) also places the verified installable files in [`release/`](release/README.md); only those two final APKs are exceptions to the repository's `*.apk` ignore rule. Release builds are signed with the debug key so they install immediately; replace the signing config before publishing to a store.

Enable “Install unknown apps” for your browser / file manager, then open the APK.

---

## Project layout

```text
app/src/main/java/com/sengine/
├── engine/
│   ├── Engine.kt                 main loop, play/pause/stop, v3 hooks
│   ├── SimulationClock.kt        deterministic fixed-step scheduler
│   ├── Input.kt, AudioSystem.kt
│   ├── camera/CameraRig.kt       rigs, director, blends, spline paths
│   ├── core/                     GameObject, components, Scene + JSON serializer, RNG
│   ├── physics/                  2D/3D worlds, broad phase, joints, soft bodies, casts, forces
│   ├── render/                   GLES2 renderer, textures/text, editor grid & gizmos
│   └── script/                   Rhino runtime, EventBus, Api.kt, ApiV3.kt
├── project/                      Project, schema/settings, diagnostics, roadmap,
│                                 GameState, 12 templates, BigGames
├── export/                       standalone APK builder
└── ui/                           Projects, Editor, Inspector, Script editor, Player, Build
app/src/test/java/com/sengine/    39 headless regression tests
docs/                             Professional Studio Edition + 24-month roadmap
tools/offline-build/              Gradle-free JVM verification pipeline
```

## License

MIT
