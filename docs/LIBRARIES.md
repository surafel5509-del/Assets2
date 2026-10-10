# Required Libraries

Every third-party dependency, what it is for, and what would replace it.

## Runtime (Android)

From `app/build.gradle.kts`:

| Dependency | Version | Purpose | Notes |
|---|---|---|---|
| `org.mozilla:rhino` | **1.7.13** | JavaScript gameplay scripting | **Pinned.** 1.7.14 breaks on Android via `javax.lang.model`. Pure Java, no JIT — required, since Android forbids W^X memory for apps. |
| `androidx.core:core-ktx` | 1.12.0 | Kotlin extensions for Android APIs | |
| `androidx.appcompat:appcompat` | 1.6.1 | Activity base, theming | |
| `com.google.android.material:material` | 1.11.0 | Material widgets in the editor | |
| `androidx.recyclerview:recyclerview` | 1.3.2 | Hierarchy list | |

**Test:** `junit:junit:4.13.2`, `org.json:json:20231013` (the JVM has no
`org.json`, so it must be supplied for host tests).

No dependency provides rendering, physics, or audio — those are hand-written:

| Subsystem | Implementation |
|---|---|
| Rendering | OpenGL ES 2.0 via `GLES20` directly; shaders are inline GLSL in `Shaders.kt` |
| Physics 2D | `engine/physics/PhysicsWorld.kt` (256 lines) |
| Physics 3D | `engine/physics/PhysicsWorld3D.kt` (236 lines) |
| Audio | `engine/AudioSystem.kt` over `MediaPlayer`/`SoundPool` |
| ZIP / APK | `java.util.zip` plus `export/ZipWriter.kt` |
| Signing | `java.security` (APK Signature Scheme v2, hand-implemented) |

## Build-time tooling

| Tool | Where | Purpose |
|---|---|---|
| Python 3.11 | `tools/build_asset_store.py` | Generates the store manifest, pack map, catalogue and game index |
| Pillow | same | Probes image dimensions and detects sprite-sheet grids |
| Node.js | `tools/validate_games.py` | `node --check` syntax validation of game scripts |
| g++ / make | `native/Makefile` | Host build and test of the C++ core |

Pillow is optional: without it the generator still runs, but every sprite sheet
falls back to a guessed grid. In this sandbox it lives in `/home/user/.venv`
because system-wide `pip install` is blocked by PEP 668.

## Native (planned)

`native/CMakeLists.txt` targets the Android NDK. **Not buildable in this
environment** — no NDK, no `cmake`.

The core deliberately has **no third-party dependencies**. Memory, ECS, scene
graph, math, the RHI and the physics solver are all hand-written (~4 035 lines).
That is a choice with
a cost:

| Considered | Decision | Reasoning |
|---|---|---|
| **Bullet Physics** | Not used — replaced by `native/engine/physics/PhysicsWorld.h` (920 lines, host-tested) | The shipped games need spheres, boxes and capsules, not soft bodies or vehicle suspension. A Bullet CMake build under the NDK is a known source of pain, and a solver you wrote is one you can profile and trim against a phone's frame budget. Revisit only if the 3D games grow ragdolls or wheeled vehicles. |
| **glTF loader (tinygltf / cgltf)** | Not yet needed | `MeshRenderer` loads `.obj`, and every model in the store ships an OBJ alongside its GLB. A loader becomes necessary for skeletal animation or PBR material channels, neither of which exists yet. |
| **stb_image** | Not used | Android's `BitmapFactory` already decodes PNG/JPEG/WebP. |
| **Vulkan-Headers / GLM** | Not used | The RHI interface is hand-defined; math is hand-written in `Math.h`. |

## Asset packs

Not libraries, but they are dependencies of the shipped games and carry licence
obligations. Full detail in [05-ASSET-STORE.md](05-ASSET-STORE.md#5-licensing).

The two that constrain distribution:

- **`Super Pixel Objects Sample`** — licence clause 4 excludes game-making tools.
  Indexed with `distributable: false`, never bundled.
- **`FreeCharactersAnimationsAssetPack`** — forbids redistribution, but is used by
  three of the four games and is bundled in this build. A publicly distributed
  engine must replace those sprites or obtain permission.
