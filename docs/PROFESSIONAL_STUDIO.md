# Professional Studio Edition — architecture & release matrix

**Version:** 3.0.0 · **Edition:** Professional Studio Edition · **Schema:** v3 · **Status:** final v3 release

This document is the technical companion to the README. It maps the broad “professional big game engine” upgrade to concrete, testable systems in the repository.

---

## 1. Architecture

```text
Android UI (Projects / Editor / Player / Build)
        │
        ├── project/          schema v3, settings, migrations, diagnostics, roadmap,
        │                     durable saves, 12 templates, 2 full games
        │
        └── engine/
             ├── Engine.kt               orchestration + play/pause/stop + scene loads
             ├── SimulationClock.kt       fixed-step accumulator / interpolation alpha
             ├── core/                   GameObject, Component registry, JSON serializer, RNG
             ├── physics/                2D/3D worlds, broad phase, contacts, joints,
             │                           soft bodies, shape casts, force fields
             ├── camera/                 CameraRig, CameraDirector, blends, spline paths
             ├── script/                 Rhino runtime, EventBus, ScriptSystem, Api, ApiV3
             ├── render/                 GLES2 scene renderer, textures, text, particles
             └── Input / Audio            joystick, keyboard, touch, SFX, music, 3D audio
```

All runtime state is plain Kotlin data that serializes to JSON. No native code, no external game framework, no hidden binary project format.

---

## 2. “2 years project system”

Implemented in `project/ProjectSchema.kt`, `project/Project.kt`, `project/StudioPlan.kt` and the editor toolbar.

* **Schema v3** — `project.json` carries engine id/version/edition, schema version, start scene, orientation, creation timestamp and a nested `settings` object.
* **Migrations** — chained, idempotent 0→1, 1→2, 2→3 migrations; older v1/v2 projects keep scenes and start scene. Unknown newer schemas are preserved, not overwritten.
* **Settings** — fixed Hz, max substeps, solver/joint iterations, broad-phase toggle/cell size, deterministic seed, quality, script instruction cap, camera blend duration and auto-migrate policy. All are applied by `Engine.applySettings()` at play start.
* **Roadmap** — `roadmap.json` holds 24 monthly milestones, phase, title, acceptance criterion, shipped flag and per-project done flag. `StudioPlan.load()` merges built-in milestones forward; due dates derive from project creation date.
* **Diagnostics** — schema health, scene/asset counts, start-scene check, per-scene camera check, simulation settings, physics/script/camera capability summary, and roadmap progress.
* **Editor** — ⚙ Settings, 🩺 Diagnostics, 🗺 Roadmap; roadmap dialog signs off milestones, marks next complete, shows a full report and resets project sign-off.
* **Script API** — `studio.report() / progress() / years() / months() / next() / milestones() / complete(id) / diagnostics()`.

See [ROADMAP.md](ROADMAP.md) for all 24 milestones.

---

## 3. Future physics

| System | File | What it does |
|---|---|---|
| Deterministic clock | `engine/SimulationClock.kt` | Fixed accumulator, bounded substeps, interpolation alpha, total step counter |
| Broad phase | `engine/physics/Broadphase.kt` | Spatial hash candidate pairs with conservative AABB overlap and brute-force comparison counters |
| Joints | `engine/physics/Joints.kt`, `core/Components.kt` | 2D/3D distance, spring, rope and weld constraints, stiffness/damping, break force, target-name components |
| Soft bodies | `engine/physics/SoftBody.kt` | Verlet particles, distance constraints, ropes, cloth, pinning, wind, forces, radial impulses, stable substeps |
| Shape casts | `engine/physics/ShapeCast.kt` | Swept circle / box / sphere against colliders, nearest hit + distance |
| Force fields | `engine/physics/ForceFields.kt` | Radial explosion / attraction, directional wind, tagged body filtering |
| Worlds | `engine/physics/PhysicsWorld.kt`, `PhysicsWorld3D.kt` | Clock-driven stepping, iteration refresh, contact/trigger callbacks, CCD, kinematic carrying, ground normals, casts and forces |

Existing v2 physics remains: layers/masks, CCD, max-speed clamps, overlap queries, sorted raycasts, impulses, terrain collision and 3D rigidbodies.

**Why it is “future” physics:** deterministic replay seeds, spatial-hash scaling, constraint/soft-body simulation, swept queries and area forces are the foundations for larger worlds, destructibles, cloth, vehicles and networked lockstep.

---

## 4. Better script system

`engine/script/ScriptSystem.kt`, `EventBus.kt`, `Api.kt`, `ApiV3.kt` and `core/DeterministicRandom.kt`.

* **Lifecycle** — `start`, `update`, `onFixedUpdate`, `onLateUpdate`, `onEnable`, `onDisable`, `onCollision`, `onTrigger`, `onTriggerExit`, `onTap`, `onDestroy`, `onStop`, `onHotReload`.
* **Events** — deterministic ordered bus with `on`, `once`, `off`, wildcard listeners, `last` and `count`; script `events.emit()` is decoupled from coroutine signalling so both subscribers and waiting fibers run.
* **Coroutines** — ES6 generators driven from JS-side `__fiber_tick()`: `waitSec`, `waitFrames`, `waitUntil`, `waitFor`, `cancelCoroutine`, `activeCoroutines`.
* **State machines** — `state.set/get/is/elapsed/update` with `<state>_enter`, `<state>_update`, `<state>_exit` hooks and previous-state tracking.
* **Hot reload** — `Engine.hotReloadScripts()` recompiles scripts while preserving each instance's `persist` bag and calls `onHotReload()`.
* **Safety** — shared `ScriptBudget.factory` observes Rhino instruction counts, plus a safety-net budget in `ensureContext()` so thread-Context reuse cannot bypass the cap; runaway scripts are disabled while the engine keeps running.
* **Determinism** — xorshift32 RNG with `fork(salt)`, seeded `random()`, `randomInt`, `setRandomSeed`, deterministic event and fiber ordering.
* **Productivity** — CommonJS `require`, timers, tweens with 7 easings, math helpers, profiler timings, frame-instruction counter, per-instance scope and inspector params.
* **Game services** — `game.save/load/has/clear/keys`, engine metadata, fixed-step introspection and `game.diagnostics()`.
* **v3 physics/camera bridges** — `physics`, `soft`, `camera` namespaces described above.

The in-editor **Script API Reference** has been expanded with the full v3 surface.

---

## 5. Better camera system

`engine/camera/CameraRig.kt` plus the camera system in `Engine.kt`.

* Priority-aware active camera selection for 2D and 3D.
* Follow with smoothing, dead zones, look-ahead and 3D forward-velocity look-ahead.
* Bounds clamping, zoom limits, panorama pan, snap-to-target.
* Trauma-based shake with configurable decay.
* Blends between rigs with configurable duration; `CameraDirector` tracks active id, cinematic path and blend state.
* Catmull–Rom dolly paths from `camera.playPath([[x,y,z], …], seconds)`.
* `camera.activate(name, seconds)`, `setPriority`, `setBounds`, `setDeadZone`, `setLookAhead`, `zoomTo`, `shake`, `panTo`, `snap`, `stopPath`.
* v3 fields on `Camera2D` / `Camera3D` components are serialized and editable in the inspector.

---

## 6. Editor upgrades

* Toolbar: **⚙ Project Settings**, **🩺 Diagnostics**, **🗺 2-Year Roadmap** alongside profiler / play / undo / save / menu.
* Settings dialog: all v3 simulation and quality settings with validation and coercion.
* Diagnostics dialog: saves the open scene and shows a complete health report.
* Roadmap dialog: 24 milestones, shipped vs project sign-off, mark-next, full report, reset.
* Object creation menu: **Joint 2D**, **Advanced Camera**, **Joint 3D**, **Advanced Camera 3D** with sensible default rigs.
* Object long-press menu: attach joints / advanced cameras to the selected object.
* About dialog updated to v3; script editor API reference updated to v3.
* New Project template list automatically includes both full games through `Templates.all`.

---

## 7. Two full games

### Citadel of Echoes
`project/BigGames.kt` → 6 saved scenes, 11 script assets, inactive prefab templates, HUD, event-driven messages, durable progression.

Scenes: `Main`, `Crypt`, `Halls`, `Throne`, `Victory`, `GameOver`.

Systems used: fixed-step physics, shape-cast ground check, joints/soft rope, particles, area explosions, FSM boss, coroutines, events, hot-reload-ready `persist`, camera bounds/dead zone/look-ahead/shake, `game.save/load`, scene loads, trigger callbacks, kinematic platforms.

### Neon Apex GP
`project/BigGames.kt` → 3 saved scenes, 5 script assets, 8 waypoint gates, 3 rivals, boost pads, cloth banner, cinematic menu camera, podium.

Scenes: `Main`, `Circuit`, `Podium`.

Systems used: 3D camera rig, Catmull–Rom menu path, countdown coroutine, waypoint/lap state machine, best-lap saves, rival AI, boost event + 3D area force, cloth wind, camera trauma, HUD text.

Headless tests drive both games from menu → gameplay → results, including boss defeat and three-lap race completion.

---

## 8. Verification

Offline JVM pipeline (`tools/offline-build/build.sh all`):

```text
main ok: 251 classes
tests ok: 18 classes
RESULT passed=39 failed=0 skipped=1
SKIP com.sengine.ApkExportTest.exportStandaloneGameApk (export env not set)
```

Coverage includes all previous engine tests plus `EngineV3Test` (12) and `BigGamesTest` (4). The optional APK export test requires `SENGINE_SOURCE_APK`, `SENGINE_KEYSTORE` and `SENGINE_EXPORT_OUT`; CI APK assembly is the release path.

CI: `.github/workflows/build-apk.yml` on the active branch builds `SEngine-3.0.0-debug-apk` and `SEngine-3.0.0-release-apk` with `./gradlew assembleDebug assembleRelease --stacktrace`.

---

## 9. Final-release scope & limits

**Done in v3:** professional project/roadmap system, deterministic core, advanced physics, script runtime, camera director, editor tooling, two complete games, docs, version bump, CI branch, tests.

**Platform limits, by design:** Android API 26+, OpenGL ES 2.0, Rhino interpreted ES6 subset (no JIT), no NDK, no Vulkan/Metal, no desktop editor. Release APKs are debug-key signed for immediate installation; replace signing config for store publication. M20–M24 in the roadmap are explicit future production milestones, not hidden incomplete engine work.

The engine is “final done” for version 3.0.0: a complete, playable, testable, buildable Android game engine and editor with two shipped games.
