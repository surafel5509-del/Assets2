# S Engine — Phase 1 Report

**Branch:** `arena/5831a3b7-assets2` (active)  
**Date:** 2026-11-26  
**Scope:** Architecture inspection first, then targeted reliability fixes in the real Android/Kotlin engine. This is **Phase 1**, not a claim that the entire professional-engine roadmap is complete. No demo/mockup, no engine rewrite, and no removal of working features.

## Architecture inspection

The existing engine is an app-private Android/Kotlin project using OpenGL ES 2.0, Rhino interpreted JavaScript, JSON scene/project storage, a `Prop`-described component system, `Scene`/`GameObject` hierarchy, fixed-step 2D/3D physics, an `Engine` command queue with EDIT/PLAY/PAUSED modes, project/asset/zip management, an in-app script/blueprint/animation editor, and an APK embed/sign export path. The inspection covered core, scripting, physics, math, rendering, audio, input, UI activities, project/export storage, templates, animation/blueprint systems, documentation, CI and the offline test toolchain.

Key findings that drove Phase 1:

* Rhino exposed host Java classes through LiveConnect and had no infinite-loop budget.
* A Rhino `Context` is thread-local; reusing one from a previous Engine could bypass the new factory configuration.
* Play/Stop did not reset all mutable runtime state, and teardown from the UI thread could skip `onStop()`.
* `PhysicsWorld3D` ignored X/Z gravity; 2D kinematic carrying failed for one broadphase pair order; `overlaps()` ignored real circle/offset colliders.
* Scene writes were not atomic and had no recovery; extensionless asset names could gain a trailing dot; zip-slip validation used a prefix check without a separator; hidden temp/backup files could leak into exports.
* Editor/script-editor API documentation had drifted; there were no Ctrl shortcuts; the GLSL editor status claimed JavaScript; GL resources had no explicit release path.
* The offline build script did not auto-discover new test classes and had a stub-path/bootstrap defect. CI push branches omitted the active branch.

## What changed

### Scripting runtime and API consistency
* Added `ScriptContextFactory.kt`: interpreted ES6 Rhino, deny-all-by-default `ClassShutter` with an engine API allow-list, `initSafeStandardObjects()`, 20k-instruction observer threshold, and a latching 5M-instruction / 250ms per-frame budget. `ScriptBudgetException` is an `Error` because Rhino only unconditionally aborts on `Error` from the instruction observer.
* Rewired `ScriptSystem.kt` to use the factory, detach stale thread-local contexts, guard owner-thread teardown, isolate timer/tween callback errors, keep per-script scopes, parameters, CommonJS modules and lifecycle dispatch, and catch/report budget aborts at every JS boundary.
* Added `ApiReference.kt` as the single source of truth for `ScriptEditorActivity.API_DOC`, `docs/SCRIPTING_API.md`, and `ApiAuditTest`.
* `Api.kt`: `overlaps()` now delegates to real physics collider manifolds instead of a rough AABB approximation.
* `ScriptEditorActivity.kt`: Rhino/Blueprint syntax validation with visible status and a save warning, correct GLSL/Blueprint/JavaScript labels, and Ctrl+S / Ctrl+Z / Ctrl+Y shortcuts.

### Runtime lifecycle and audio
* `Engine.kt`: `resetRuntimeState()` clears time scale, profilers, physics accumulators, pending scene loads, input, pools and counters on both play and stop; `release()` is idempotent, thread-safe and queues teardown for the owner thread.
* `AudioSystem.kt`: bounded stream tracking, `stopAll()` works without a pool, non-looping music releases its `MediaPlayer` on completion, and failed music initialization no longer leaks the player.
* `EditorActivity.kt` / `PlayerActivity.kt`: drain engine teardown and free GL resources on the GL thread before destroying the view; real editor shortcuts Ctrl+S / Ctrl+Z / Ctrl+Y / Ctrl+Shift+Z / Ctrl+P.

### Physics
* `PhysicsWorld3D.kt`: applies `gravityX`, `gravity3D` and `gravityZ`; signed-safe trigger pair decoding; sphere ray origin inside collider handled.
* `PhysicsWorld.kt`: kinematic carry works when the mover is either `a` or `b`; signed-safe trigger pair decoding; new exact `overlaps(a, b)` used by scripts.

### Persistence, export and GL
* `Project.kt`: safe name normalization, atomic writes with hidden `.bak` recovery, `lastLoadRecovered`, and extensionless-safe `uniqueAssetName()`.
* `ProjectManager.kt`: pure `resolveZipEntry()` with separator-enforced zip-slip guard; export excludes hidden internal files.
* `ApkBuilder.kt`: excludes hidden/backup/temp files anywhere in the project tree.
* `SceneRenderer.kt`, `Shaders.kt`, `Textures.kt`, `Renderer2D.kt`, `Renderer3D.kt`: explicit GL program/texture/FBO release on the GL thread; context-loss caches are discarded without touching stale ids.

### Build, CI and docs
* `bootstrap-toolchain.sh`: safe stub handling and correct Kotlin compiler install/check.
* `build.sh`: correct repo-relative stub path and auto-discovery of every `*Test.kt` with `SENGINE_REPO`/`-Dsengine.repo`.
* JUnit stub: added `assertNull(String, Object)`.
* `.github/workflows/build-apk.yml`: active branch added to push and pull-request triggers.
* `README.md`: Phase 1 summary, canonical API reference link, shortcut/build/test instructions, corrected workflow filename and typo.
* `docs/SCRIPTING_API.md`: generated from `ApiReference.API_DOC`.

## Files created

* `app/src/main/java/com/sengine/engine/script/ScriptContextFactory.kt`
* `app/src/main/java/com/sengine/engine/script/ApiReference.kt`
* `app/src/test/java/com/sengine/ApiAuditTest.kt`
* `app/src/test/java/com/sengine/Phase1RegressionTest.kt`
* `docs/SCRIPTING_API.md`
* `docs/PHASE1_TEST_LOG.txt`
* `docs/PHASE1_REPORT.md` (this report)

## Files modified

* `.github/workflows/build-apk.yml`
* `README.md`
* `app/src/main/java/com/sengine/engine/AudioSystem.kt`
* `app/src/main/java/com/sengine/engine/Engine.kt`
* `app/src/main/java/com/sengine/engine/physics/PhysicsWorld.kt`
* `app/src/main/java/com/sengine/engine/physics/PhysicsWorld3D.kt`
* `app/src/main/java/com/sengine/engine/render/Renderer2D.kt`
* `app/src/main/java/com/sengine/engine/render/Renderer3D.kt`
* `app/src/main/java/com/sengine/engine/render/SceneRenderer.kt`
* `app/src/main/java/com/sengine/engine/render/Shaders.kt`
* `app/src/main/java/com/sengine/engine/render/Textures.kt`
* `app/src/main/java/com/sengine/engine/script/Api.kt`
* `app/src/main/java/com/sengine/engine/script/ScriptSystem.kt`
* `app/src/main/java/com/sengine/export/ApkBuilder.kt`
* `app/src/main/java/com/sengine/project/Project.kt`
* `app/src/main/java/com/sengine/project/ProjectManager.kt`
* `app/src/main/java/com/sengine/ui/EditorActivity.kt`
* `app/src/main/java/com/sengine/ui/PlayerActivity.kt`
* `app/src/main/java/com/sengine/ui/ScriptEditorActivity.kt`
* `tools/offline-build/bootstrap-toolchain.sh`
* `tools/offline-build/build.sh`
* `tools/offline-build/stubs/org/junit/Assert.java`

## Tests actually run

Final command:

```bash
cp tools/offline-build/build.sh /home/user/toolchain/build.sh
timeout 300 bash /home/user/toolchain/build.sh all
```

Actual result from `docs/PHASE1_TEST_LOG.txt`:

```
== compiling java stubs ==
== compiling main (kotlinc) ==
main ok: 182 classes
== compiling tests (kotlinc) ==
tests ok: 18 classes
== running tests ==
SKIP com.sengine.ApkExportTest.exportStandaloneGameApk (export env not set)
RESULT passed=44 failed=0 skipped=1
ALL_EXIT=0
```

Historical pre-Phase-1 baseline was `RESULT passed=23 failed=0 skipped=1`. The final suite has **44 passing tests**: all 23 pre-existing runnable tests still pass, plus 4 API audit tests and 17 Phase 1 regression tests. The only skip is the standalone APK export test, which requires an Android/Gradle export environment that is not available in this sandbox.

New regression coverage includes: host Java/LiveConnect denial, raw `unwrap().getClass()` denial, infinite-loop abort with engine recovery, stale Rhino context isolation, timer/tween error isolation, random helper overloads, play/stop state reset, `onStop()` on owner thread, 3D horizontal gravity, exact circle/offset `overlaps()`, reversed kinematic carry, atomic scene save/recovery, asset/scene path traversal, extensionless naming, zip-slip, export hidden-file exclusion, audio stream cleanup, Markdown/editor/runtime API equality, and real behavior of every API named in the master prompt including `scene.load()` / `scene.reload()`.

## Remaining bugs, verification gaps and deferred work

Known unresolved issues / verification gaps:

* `Component.fromJson` still silently catches all property parse exceptions; malformed component properties produce no diagnostic.
* `Input` keeps raw UI-thread state and copies it per frame; concurrent touch/key stress on a device is not yet covered by an instrumentation test.
* GL release is implemented and compiled, but EGL context-loss/recreation behavior needs on-device validation.
* GLSL shaders are compiled by the GL runtime; the script editor validates JS/Blueprint syntax but does not precompile GLSL.
* Editor shortcuts and syntax-validation dialogs are compiled but not yet covered by Android instrumentation tests.
* `ApkExportTest` remains skipped; the GitHub Actions APK workflow was updated for the active branch but was not executed in this sandbox. A real Gradle/Android SDK build is still required before claiming APK export verified.
* Hidden-file exclusion in `ApkBuilder` is covered indirectly by export tests but not by a full standalone APK assembly in this environment.

Deferred roadmap features (not Phase 1 fixes): polygon 2D colliders, joints/physics materials, spot lights, orthographic 3D camera, PBR/real-time shadows, skeletal animation, tilemaps, full canvas/UI component set, fonts/prefab/scene asset kinds, dockable/resizable panels, multi-select/grouping enhancements, context-menu/search/filter expansion, and broader rendering/integration coverage. These are Phase 2+ work; the existing systems were preserved and remain usable.

## Preserved working version

* Source checkpoint: `8922d9a1375838a82d8818639739ce1d6d1e4222` on `arena/5831a3b7-assets2`.
* Final working version: local annotated tag `phase-1-working` at the report commit (`git rev-parse phase-1-working`).
* Branch pushed to `origin/arena/5831a3b7-assets2`; no other branch was pushed.
* Recovery: generated toolchain content under `/home/user/toolchain` is outside the repository and may not survive a sandbox reset; regenerate with `bash tools/offline-build/bootstrap-toolchain.sh`, then `cp tools/offline-build/build.sh /home/user/toolchain/build.sh`.
