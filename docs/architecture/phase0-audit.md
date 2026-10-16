# S Engine — Phase 0 audit and baseline

Scope: `edition.md` §6 Phase 0 (audit and baseline), with the Phase 1 foundation work that follows it on this branch.
Every statement below was checked against the source tree, Gradle files, manifest, or CI logs at the time of writing.

## 1. Branch and baseline

| Item | Status |
|---|---|
| Working branch | `arena/b97ba415-assets2` (branched from `45a6d0b7346b6f98b9d4c0e73caf79fe9a76a069`). `edition.md` and the original brief name `arena/69b25c4c-assets2`; that branch is not present in this sandbox, so this branch is used instead. |
| Baseline CI | GitHub Actions run `38864079363` on `arena/69b25c4c-assets2` at `45a6d0b`: **success** (job "Test and build Android APKs", all steps green, artifact `SEngine-Android-APKs`). |
| Local Gradle/Android build | **Not run.** The sandbox has no Android SDK, and Maven/Google/Gradle/Adoptium hosts are unreachable from it. The build is verified by CI only. |
| Pre-existing failures | None found in the baseline CI run. |

## 2. Build configuration

- Gradle wrapper 8.7, Android Gradle Plugin 8.3.2, Kotlin 1.9.23, JVM target 17 (`build.gradle.kts`, `gradle/wrapper`).
- `app/build.gradle.kts`: `compileSdk 34`, `minSdk 26`, `targetSdk 34`, `applicationId com.sengine.app`.
- Dependencies: Mozilla Rhino 1.7.13 (1.7.14 is known to break on Android, so it stays pinned); unit tests use JUnit 4.13.2 and org.json 20231013.
- `unitTests.isReturnDefaultValues = true`. Release build is signed with the debug key.
- `settings.gradle.kts` uses `repositoriesMode FAIL_ON_PROJECT_REPOS`.

## 3. Manifest

- `<uses-feature android:glEsVersion="0x00020000" android:required="true" />`. The app therefore requires OpenGL ES 2.0 at install time. ES 3.0 is **not** a manifest requirement (see `edition.md` §4 and the product constraints); it is detected at runtime.
- Activities: `ProjectsActivity` (launcher), `EditorActivity`, `ScriptEditorActivity`, `PlayerActivity`, `BuildActivity`, `AssetStoreActivity`, `AnimationEditorActivity`, `BlueprintEditorActivity`. One `FileProvider`.
- Exported games (`export/ApkBuilder.kt`) repackage this app's own APK and patch its manifest, so manifest GL changes propagate to exported games.

## 4. Source map (main, Kotlin, lines from `wc -l`)

| Area | Files (lines) | Notes |
|---|---|---|
| Engine core | `engine/Engine.kt` (340), `engine/Input.kt`, `engine/AudioSystem.kt` | `Engine.tick(dt)` runs on the GL thread under `engine.lock`; commands go through `post()`. Log ring buffer of 300; profiler `renderMs`, `drawCalls`. |
| Scene and components | `engine/core/Scene.kt` (188), `core/Components.kt` (466), `core/GameObject.kt`, `core/Scene` JSON serializer | Scene JSON has no `schemaVersion` (see §7). |
| Rendering | `engine/render/SceneRenderer.kt` (583), `Renderer2D.kt` (180), `Renderer3D.kt` (167), `PostProcessor.kt`, `Textures.kt`, `Shaders.kt`, `GL.kt`, `Meshes.kt` (246), `View2D.kt`, `View3D.kt`, `EditorState.kt` | Renderer is GLES 2 (`android.opengl.GLES20`) before this branch. |
| Shaders | Inline GLSL 1.00 strings in `Renderer2D.kt`, `Renderer3D.kt`, `Shaders.kt`; user effects are `.glsl` assets | 10 built-in programs (sprite, mesh, post, line, sky) plus 8 post effects. |
| Physics | `engine/physics/PhysicsWorld.kt` (374), `PhysicsWorld3D.kt` (352) | Fixed-step 2D and 3D. No GL. |
| Scripting | `engine/script/Api.kt` (309), `ScriptSystem.kt` (255) | Rhino JavaScript API. No GL. |
| Animation | `engine/anim/AnimationClip.kt` (115) | Sprite-sheet clips. No GL. |
| Blueprint | `engine/blueprint/Blueprint.kt` (297) | No GL. |
| Project and assets | `project/Project.kt` (121), `ProjectManager.kt`, `AssetLibrary.kt` (1083), `Templates.kt` (477), `HillClimbTemplate.kt` (780) | Shader templates in `AssetLibrary.kt` (about line 791) and `NEW_SHADER` in `Templates.kt` (line 24). |
| Export | `export/ApkBuilder.kt` (96), `ApkSignerV2.kt`, `AxmlPatcher.kt`, `export/GameRuntime.kt` | Standalone games detect `assets/game/build.json` and extract to `filesDir/game`. |
| UI | `ui/EditorActivity.kt` (909), `PlayerActivity.kt` (131), `ScriptEditorActivity.kt`, `InspectorPanel.kt`, `ViewportController.kt`, `Viewport3DController.kt`, others | Only `EditorActivity` and `PlayerActivity` create a `GLSurfaceView`. |

Total Kotlin under `app/src`: about 14,200 lines including tests.

## 5. GL surface inventory (before this branch)

Count of `GLES20.` / `GLSurfaceView` / `GL10` references per file at HEAD:

| File | Refs | Role |
|---|---|---|
| `render/Renderer3D.kt` | 55 | Mesh, sky, lights, depth state |
| `render/Shaders.kt` | 41 | Program build and uniform/attrib lookup |
| `render/PostProcessor.kt` | 37 | FBO, RGBA texture, DEPTH_COMPONENT16 renderbuffer |
| `render/Renderer2D.kt` | 34 | Quads, lines, circles, wire boxes, OBB |
| `render/GL.kt` | 26 | Compile/link helpers |
| `render/SceneRenderer.kt` | 15 | Frame loop, clears, blend changes |
| `render/Textures.kt` | 10 | Texture upload, `GLUtils` for text bitmaps |
| `ui/EditorActivity.kt`, `ui/PlayerActivity.kt` | 0 direct calls | `setEGLContextClientVersion(2)` in both (the only ES 2 pin in the app) |

Known GL-related risks found in the audit (not yet addressed unless noted in §8):
- `GL.compile` returned a program id even when linking failed, so shader errors were silent.
- `ShaderLibrary.source()` reads the `.glsl` asset from disk on every sprite/mesh/post lookup, even when the file has not changed. Cached programs are reused by timestamp, but the file is still read each time. (Pre-existing; not changed in this phase.)
- Image texture cache is unbounded; text bitmaps are 72 px and the text cache is capped at 128 entries.
- Ad-hoc GL state toggles across passes; per-draw uniform updates; immediate-mode vertex pointers from heap buffers.
- `PostProcessor` has no context-loss handling.

## 6. Tests

- `app/src/test/java/com/sengine/`: `ApkExportTest.kt`, `EngineSimulationTest.kt` (205 lines), `EngineV2Test.kt` (234), `HillClimbTemplateTest.kt` (123). 21 `@Test` methods in total.
- No renderer or GL test existed before this branch. No `androidTest` source set exists, so there are no on-device tests.
- CI runs `./gradlew testDebugUnitTest`, then `assembleDebug assembleRelease`, and uploads the APKs.

## 7. Scene and project schema

- `project.json`: `name`, `engine` ("S Engine 1.0"), `startScene`, `created`, `orientation`, `controlScheme`. **No `schemaVersion`.**
- Scene JSON: `name`, `gravity*`, `ambient`, `fog*`, `nextId`, `objects`. **No `schemaVersion`.**
- `edition.md` §7 requires a schema version and a migration when the schema changes. Adding the field is deferred until a schema change actually needs a migration, so that no unversioned data is rewritten without a reason.
- **Fixtures (added):** `app/src/test/resources/fixtures/project/` contains a complete project folder in the current format:
  - `project.json`.
  - `scenes/Main.scene.json`: a 2D scene with a camera (custom post shader), a sprite with a hierarchy child, a static ground with a custom shader, particles, text, audio, script, animator, and a disabled object.
  - `scenes/Arena3D.scene.json`: a 3D scene with fog, a directional light, sphere and plane meshes, and 3D physics.
  - `scenes/Legacy.scene.json`: a minimal old-style scene that omits `gravity3D`, `ambient`, `fog*`, and `nextId`.
  - `SceneFixtureTest` (10 tests) checks the loader's values, that a save reproduces each fixture's structure, that a second load/save cycle is stable, and that missing legacy fields get defaults and are written in the current format on save.
  - Numbers are compared with a tolerance because the saver widens floats to doubles.

### Findings from the fixture work

1. **Unknown component types are silently dropped (data-loss risk, not yet fixed).** `SceneSerializer.applyObjectJson` runs `ComponentRegistry.create(type) ?: continue`. A component whose type this build does not know (a newer engine version, or a renamed type) is removed from the scene in memory, and the next save writes the scene without it. This conflicts with the rule "never silently discard project data". The same applies to unknown object-level keys. The fix needs a design decision: keep unknown component JSON verbatim and write it back, and decide whether the editor should show it. It is the first item for the next step.
2. **Float noise in saved files.** The saver writes `Float` values as `Double` (`0.4f` becomes `0.4000000059604645`). Loading is unaffected, but saved files and diffs are noisy. Formatting through `Float.toString()` would fix it. This is cosmetic and is not changed here.
3. **No `schemaVersion`** in `project.json` or scene JSON (see above). The legacy fixture shows that missing fields currently act as the schema-version mechanism.

## 8. Phase 1 status on this branch

Implemented (source only; **not yet compiled by Gradle** in this sandbox):

- `render/api/GraphicsApi.kt`: `GraphicsBackend` (GLES30, GLES20), `ShaderDialect`, `GlCapabilities` (version/GLSL parsing, `describe`), `GraphicsBackendSelector`, `GraphicsInitException`.
- `render/api/ShaderSourceSet.kt`: one interface per dialect; `ShaderDiagnostics` maps driver `0:<line>` errors back to the user's effect code.
- `render/gles2/Gles2Shaders.kt`: GLSL ES 1.00 sources, moved verbatim from HEAD (checked byte-for-byte).
- `render/gles3/Gles3Shaders.kt`: GLSL ES 3.00 sources (`#version 300 es`, `in`/`out`, explicit `fragColor`). A `texture2D` macro shim keeps the user-effect ABI unchanged.
- `platform/android/GlSurface.kt`: ES 3 first EGL config chooser and context factory, ES 2 fallback, `GlInfo.query()`, `GraphicsStatus`.
- `render/GL.kt`: `tryCompile` returns stage-tagged errors (`vertex:`, `fragment:`, `link:`) and never returns a program id for a failed link; `compileOrThrow`; `drainErrors`.
- `render/Shaders.kt`: `ShaderLibrary.init(set)`; user shaders report errors that point at the effect line.
- `render/SceneRenderer.kt`: selects the backend from the driver's strings, builds the matching shader set, and reports the result via `GraphicsStatus`, `Log` (tag `SEngine.GL`) and the engine log. If a built-in ES 3 set fails to build, it falls back to the ES 2 set on the same ES 3 context. If init fails entirely, frames show a dark-red screen with the error in the log.
- `ui/EditorActivity.kt`, `ui/PlayerActivity.kt`: `setEGLContextClientVersion(2)` replaced by `GlSurface.install`; the stats line shows the active backend.

Not yet done in Phase 1 (honest gaps):

- `render/Textures.kt` and `render/PostProcessor.kt` still call `GLES20` directly. This is valid on an ES 3 context, but ES 3-only features (VAOs, UBOs, instancing) are not used anywhere yet.
- No context-loss handling beyond re-running init on `onSurfaceCreated`. Textures and post targets are rebuilt there; there is no device-side verification of resume or context recreation.
- No user-facing error dialog for init failure; the failure is shown in the log and status line.
- The Phase 1 gate ("actual existing scenes render on ES 3; app survives resize, pause/resume and context recreation") **has not been verified on a device or emulator.**

## 9. Phase 0 checklist

- [x] Source tree, Gradle files, manifest, CI config and GL call sites mapped (§2–§5).
- [x] Baseline CI on the `69b25c4c` branch recorded as green (§1).
- [x] Build verified by CI: `Test and build Android APKs` succeeded on `2aa8649` (unit tests and debug/release APKs). Local Gradle build remains blocked by the sandbox (no Android SDK; Maven/Google hosts unreachable).
- [x] Renderer classes, shaders and GL call counts listed (§5).
- [x] Scene and project schema recorded (§7).
- [x] Representative scene and project fixtures added with `SceneFixtureTest` (§7). Unknown-component data loss found and recorded (§7, finding 1); not yet fixed.
- [x] Audit report and implementation checklist (this document).

## 10. Verification log for this branch

- Unit tests `GraphicsApiTest` (9 tests) and `ShaderSourcesTest` (13 tests) were written under `app/src/test/java/com/sengine/render/`.
- Because Gradle and the Android SDK are unavailable in the sandbox, these tests were compiled with Kotlin 2.4.21 (JVM 17) and run through a local JUnit-compatible harness (the same `@Test` and `Assert` semantics, no Gradle). Result: **22 of 22 passed.** Mutation checks (a renamed ES 3 attribute, an ES 3 token leaking into ES 2, and a varying type mismatch) each made the suite fail, so the lint tests detect the errors they target.
- The harness is not part of the repository.
- **CI (authoritative build check):**
  - Run `38865877370` on `04c9185` (first push): **failure** in `:app:compileDebugKotlin`, three unresolved `EGL14.EGL_OPENGL_ES3_BIT` references in `GlSurface.kt`. The constant is not in `EGL14`. Fixed by declaring the EGL_KHR_create_context value `0x40` locally.
  - Run `38866267838` on `2aa8649`: **success.** Steps `Run unit tests` (`testDebugUnitTest`, which includes the new `render` tests) and `Assemble debug and release APKs` both succeeded, and the APK artifact was uploaded. The per-test count was not retrieved, because the CI log and artifact storage were unreachable from the sandbox; the step result is the evidence.
  - Lesson: the sandbox can only type-check pure-Kotlin code. Android-facing code must be validated by CI until a local Android SDK is available.
- `ShaderDiagnostics` mapping tests and the ES 3 shader lint tests validate the static properties of the sources. They do **not** prove that the GLSL compiles on a real driver; that requires an on-device run.
