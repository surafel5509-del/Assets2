# S Engine Studio 6.2

An Android-native 2D/3D game editor and runtime, written in Kotlin. Create scenes, attach JavaScript behaviours or blueprints, work with sprites and meshes, test games in the editor, and export Android game packages.

**Studio 6.2 is a foundations and workflow upgrade—not a claim of Unity or Unreal feature parity.** The repository also contains experimental systems whose names and configuration options do not imply a complete, integrated implementation. See [capabilities and roadmap](docs/ROADMAP.md) before relying on an advanced feature.

## What's new

### A more usable native editor

- Slate workspace theme, dedicated project header, clearer play controls, and an unsaved-change indicator.
- **Command palette:** searchable, executable commands for scene operations, transform tools, runtime controls, windows, object creation, and Android builds. Unavailable commands are omitted.
- **Searchable hierarchy:** matches names, tags, and component types; retains ancestor context and reveals matches inside collapsed branches.
- **Searchable assets:** filter by filename or asset kind. Component addition now uses a searchable picker.
- **Problems panel:** checks invalid IDs, foreign parents, non-finite transforms/properties, zero scales, missing asset references, missing cameras, and unavailable components. Select an object by tapping its problem. Structural errors block editor Play.
- **Profiler panel:** live FPS, draw calls, managed heap usage, CPU script/physics/render timings, fixed-step counts, dropped physics time, and a scrolling frame-interval graph. These are CPU/engine measurements, not GPU timings.
- **Workspace presets:** Balanced, Scene focus, and Inspect; the chosen preset persists. Balanced hides the inspector on smaller screens to leave usable viewport space. Individual panel toggles remain available.

### Reliable scene and project handling

- Versioned scene JSON, with support for existing unversioned scenes. Future schemas, duplicate/invalid IDs, missing parents, and cycles are rejected rather than silently repaired.
- Unknown component data survives load/save. The inspector explains why that component cannot be edited.
- Cycle-safe parenting and iterative hierarchy traversal, transform computation, subtree duplication, and deletion.
- Atomic, synced scene/metadata/text-asset writes. Scene saves rotate three backups by default.
- Separate recovery snapshots every 30 seconds while the editor is active with dirty edits. Recovery is offered when opening a scene and can be restored as an undoable, unsaved edit.
- Last-backup restoration from the editor menu. A failed scene load offers an explicit backup fallback instead of creating a replacement scene.
- Snapshot undo/redo bounded to **100 entries and an estimated 16 MiB total string storage**, preserving selection. Large scenes may retain fewer entries; a single oversized snapshot is not retained.
- Shared export rules exclude recovery directories, hidden files, temporary files, and backup files from project archives and game APKs. Project file paths and ZIP extraction use path-segment-aware containment checks.

### Runtime fixes

- A tested fixed-step accumulator is shared by the 2D and 3D solvers: at most five substeps per rendered frame, retained fractional time, and explicit dropped-time accounting. Fixed-step scheduling is not a cross-platform determinism guarantee.
- Time scale reaches scripts and simulation; invalid deltas cannot poison the clock. Scaled simulation time and unscaled elapsed time are separate.
- **Hybrid ECS no longer rewinds physics/script transforms.** It imports current transforms before running ECS systems, applies system output, removes destroyed mappings, and resets mappings across scene/play transitions.
- Each engine owns its job scheduler. Stopping one engine no longer shuts down another engine's scheduler. Invalid batch sizes fail immediately, and worker exceptions reach callers.

## Use the editor

1. Create or open a project. Existing templates include platformer, shooter, 3D, animation, and blueprint examples.
2. Add objects using **＋** or **Commands → Create**. Select an object in the hierarchy or viewport to edit it in the inspector.
3. Open **Problems** to inspect scene issues; tap a row to select/frame its object.
4. Use **Play**, **Pause**, and **Step** to test. **Stop** restores the edit scene.
5. **Save** writes the current scene and rotates backups. A `•` beside the scene name means an edit has been recorded since the last successful save. This is a conservative dirty flag, not a content hash.
6. Use **Workspace** to reclaim viewport space. Use **Profiler** to inspect measured runtime work.

### Keyboard shortcuts

| Shortcut | Action |
| --- | --- |
| Ctrl+K | Command palette |
| Ctrl+S | Save scene |
| Ctrl+Z | Undo |
| Ctrl+Shift+Z / Ctrl+Y | Redo |

### Recovery and compatibility

Recovery files live in `<project>/.recovery/` and never silently replace saved scenes. A successful scene save removes that scene's recovery file. Backups live beside scene files as `.bak1`–`.bak3`; `.bak1` is the previously saved version. Keep external copies of important projects: recovery and backups on the same device are not disaster recovery.

Scene schema version is **1**; application/API version is **620**. These are separate version spaces. Schema 0 (no version field) is accepted. Unknown components are preserved, but not executed. Preserve a project archive before opening projects in older editor versions, which may discard unfamiliar data.

## Build and verify

Requirements: **JDK 17**, Android SDK platform **34**, build tools **34.0.0**, and network access to Google's Maven repository, Maven Central, and Gradle distributions. Minimum device version: Android 8.0 / API 26, with OpenGL ES 2.0.

```sh
# Set ANDROID_HOME, or set sdk.dir in your untracked local.properties.
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions runs the same checks and uploads test/lint reports plus a debug APK **only if all checks succeed**. It no longer masks failed commands or commits build logs back into the repository.

Release builds currently use the debug signing configuration. **Configure your own release signing before distribution.** The standalone game-APK export test also requires `SENGINE_SOURCE_APK`, `SENGINE_KEYSTORE`, and `SENGINE_EXPORT_OUT`; it is skipped without these inputs. Compilation/unit tests do not replace device rendering, audio, input, lifecycle, and APK installation testing.

See [verification notes and device checklist](docs/VERIFICATION.md).

## Repository map

| Path | Purpose |
| --- | --- |
| `app/src/main/java/com/sengine/engine/` | Runtime, components, math, solvers, renderer, scripting, experimental systems |
| `app/src/main/java/com/sengine/editor/` | Android-independent editor state utilities |
| `app/src/main/java/com/sengine/ui/` | Native editor, inspector, hierarchy, asset/script/animation/blueprint tools |
| `app/src/main/java/com/sengine/project/` | Projects, templates, atomic storage, export filtering |
| `app/src/main/java/com/sengine/export/` | APK repackaging/signing and embedded-game runtime |
| `app/src/test/` | JVM regression tests |
| `Assets/` | Existing source asset archives; respect each pack's license |

Historical edition notes remain in `ULTIMATE_EDITION_CHANGELOG.md`. Their feature claims are not a current support contract; this README and the capability roadmap describe the scope of this upgrade.
