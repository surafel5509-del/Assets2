# S Engine — Architecture Blueprint

A fully offline, mobile-first Android game engine and editor: native C++ core, Kotlin
editor UI, JavaScript gameplay scripting, and a local asset store built from real
asset packs.

This document is the map. Each subsystem has its own file:

| Document | Covers |
|---|---|
| [01-NATIVE-CORE.md](01-NATIVE-CORE.md) | C++ core: memory, ECS, scene graph, math, RHI, JNI boundary |
| [02-GRAPHICS.md](02-GRAPHICS.md) | Rendering: GLES/Vulkan backends, PBR, lights, shadows, particles |
| [03-EDITOR.md](03-EDITOR.md) | Editor UI: hierarchy, inspector, viewport, terrain, timeline, UI builder |
| [04-SCRIPTING.md](04-SCRIPTING.md) | JavaScript runtime, visual scripting, hot reload |
| [05-ASSET-STORE.md](05-ASSET-STORE.md) | File manager, ZIP ingest, offline asset store |
| [06-BUILD-AND-PROFILING.md](06-BUILD-AND-PROFILING.md) | In-app APK export, runtime profiler |
| [07-GAMES.md](07-GAMES.md) | The four shipped games |
| [LIBRARIES.md](LIBRARIES.md) | Every third-party dependency and why it is there |
| [DIRECTORY-STRUCTURE.md](DIRECTORY-STRUCTURE.md) | Full tree with a one-line purpose per path |

---

## 1. Design constraints

These were fixed before any code was written, and every decision below traces back
to one of them.

1. **100% offline.** No network calls, no cloud services, no telemetry. The asset
   store is served from the APK's own assets, and a game exported from the editor
   runs with no connectivity.
2. **No AI features.** No assistants, no generated content, no ML inference.
3. **No platformer or space-shooter templates.** They were in the codebase and have
   been removed; the four shipped games replace them.
4. **Mobile budget first.** A mid-range phone is the target, not a desktop. That
   means allocation-free frame loops, bounded pools, and cheap-by-default
   perception and culling.
5. **Everything the editor can make, the runtime can run.** There is no editor-only
   code path. `PlayerActivity` and `BuildActivity` load the same `Project`.

## 2. The layered architecture

```
┌──────────────────────────────────────────────────────────────────┐
│  EDITOR (Kotlin, Android Views)          ui/            4 523 LOC │
│  Projects · Hierarchy · Inspector · Viewport · Timeline ·         │
│  Terrain · Particle editor · Blueprint graph · UI builder         │
├──────────────────────────────────────────────────────────────────┤
│  PROJECT LAYER (Kotlin)                  project/       2 090 LOC │
│  Project · ProjectManager · AssetStore · GameLibrary · Templates  │
├──────────────────────────────────────────────────────────────────┤
│  ENGINE RUNTIME (Kotlin)                 engine/        4 659 LOC │
│  Scene · Components · Renderer2D/3D · Physics · Script · Anim     │
│  Blueprint · Audio · Input                                        │
├──────────────────────────────────────────────────────────────────┤
│  NATIVE CORE (C++17, NDK)                native/        4 035 LOC │
│  Memory allocators · Archetypal ECS · Scene graph · Math · RHI    │
└──────────────────────────────────────────────────────────────────┘
```

The native core is the foundation and the long-term performance path. The Kotlin
engine above it is what actually runs today, including on the host CI. That split
is deliberate and is explained in §4.

## 3. Why two runtimes

A common failure mode for a native-core engine is writing the C++ layer and then
never wiring it up, so the shipped product is the interpreted layer with a pile of
unused C++ beside it. This project avoids that by making the relationship explicit:

- **The C++ core is the contract.** `native/engine/` defines the data layouts,
  allocator semantics, ECS rules and RHI interface that the whole engine is
  designed around. It is compiled and tested on the host
  (`native/Makefile` → 219 checks, 0 failures) with no Android toolchain required.
- **The Kotlin runtime implements the same contract** so the editor, the in-app
  player, and the exported APK all work today. Scene files, component names and
  the scripting API are shared between the two.
- **Migration is per-subsystem, not big-bang.** Each subsystem has a seam: the ECS
  query interface, the `RHI` interface, the transform hierarchy. Swapping the Kotlin
  `PhysicsWorld` for the native one changes no scene file and no script.

The honest statement of where this stands: the C++ core is real, tested code that
is not yet linked into the APK, because this environment has no NDK. See
[01-NATIVE-CORE.md](01-NATIVE-CORE.md) for exactly what is verified and what is not.

## 4. Data flow for one frame

```
Input (touch/keys) ──► Input.kt
                          │
                          ▼
ScriptSystem ──► user JS update(dt)  ──► SObject/Sscene mutation
                          │
                          ▼
PhysicsWorld / PhysicsWorld3D ──► integration + collision resolution
                          │
                          ▼
Scene transform resolution (dirty-flag propagation)
                          │
                          ▼
Cull ──► Sort (2D: by order; 3D: front-to-back opaque, back-to-front transparent)
                          │
                          ▼
Renderer2D / Renderer3D ──► GL.kt draw calls ──► PostProcessor
                          │
                          ▼
AudioSystem (mix) ──► frame complete
```

Structural changes (spawn, destroy, add component) are deferred. Scripts that spawn
objects during `update` go through a command queue flushed after the frame's
iteration completes — the same rule the native ECS enforces, so behaviour does not
change when the native world takes over.

## 5. Verification

What is actually checked, and by what:

| Layer | Command | Result |
|---|---|---|
| Native core | `cd native && make` | **219 checks, 0 failures — PASS** |
| Asset store generation | `python3 tools/build_asset_store.py` | 25 packs, 5 008 files, 154 sprite sheets |
| Games vs. store vs. engine | `python3 tools/validate_games.py` | **2 295 checks, 0 errors — PASS** |
| Game scripts, executed | `node tools/run_games.js` | **89 checks, 0 failures — PASS** |
| Kotlin / Gradle | — | **Not runnable here**: no `java`, no Android SDK |
| Native Android build | — | **Not runnable here**: no NDK, no `cmake` |

The last two rows are a real gap, not a formality. Every Kotlin file added or
edited in this work is uncompiled. The validator and the native suite are what
stand in for a compiler on the parts they cover, and they have caught real defects
— see [07-GAMES.md](07-GAMES.md#what-the-validator-caught).

## 6. Scope boundaries

Explicitly **out of scope**, by design:

- Networking of any kind, including local-network multiplayer
- AI assistance, procedural content generation, ML inference
- Space shooter and platformer demo templates
- Redistribution of asset packs whose licence forbids it (one pack in the store is
  indexed but excluded from the bundle for this reason — see
  [05-ASSET-STORE.md](05-ASSET-STORE.md#licensing))
