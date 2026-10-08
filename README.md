# S Engine

A fully offline, mobile-first Android game engine and editor: native C++ core,
Kotlin editor UI, JavaScript gameplay scripting, an offline asset store built from
real asset packs, and four complete games.

**No networking. No AI features. No cloud.** Every asset ships inside the APK, and a
game exported from the editor runs with no connectivity.

## Start here

**[docs/00-ARCHITECTURE.md](docs/00-ARCHITECTURE.md)** — the blueprint: constraints,
layering, frame data flow, and what is verified versus not.

Then, in the order the system should be built:

| | Document | |
|---|---|---|
| 1 | [01-NATIVE-CORE.md](docs/01-NATIVE-CORE.md) | C++ core: memory, ECS, scene graph, math, RHI, JNI seam |
| 2 | [02-GRAPHICS.md](docs/02-GRAPHICS.md) | Rendering: GLES today, PBR/shadows as the plan |
| 3 | [03-EDITOR.md](docs/03-EDITOR.md) | Editor panels, animation editor, Blueprint graph |
| 4 | [04-SCRIPTING.md](docs/04-SCRIPTING.md) | JavaScript runtime and the scripting API |
| 5 | [05-ASSET-STORE.md](docs/05-ASSET-STORE.md) | ZIP ingest, sprite-sheet detection, licensing |
| 6 | [06-BUILD-AND-PROFILING.md](docs/06-BUILD-AND-PROFILING.md) | In-app APK export, runtime profiler |
| 7 | [07-GAMES.md](docs/07-GAMES.md) | The four games and what the validator caught |
| | [LIBRARIES.md](docs/LIBRARIES.md) | Every dependency and why |
| | [DIRECTORY-STRUCTURE.md](docs/DIRECTORY-STRUCTURE.md) | Full annotated tree |

## The four games

| | Game | Genre | Detail |
|---|---|---|---|
| 1 | **Emberfall** | 2D top-down RPG / adventure | [games/01_topdown_rpg](games/01_topdown_rpg/README.md) |
| 2 | **Blade & Bulwark** | 2D fighting | [games/02_fighting_2d](games/02_fighting_2d/README.md) |
| 3 | **Hollow Ridge** | 3D action-adventure | [games/03_action_3d](games/03_action_3d/README.md) |
| 4 | **Neon Overdrive** | 3D endless runner | [games/04_runner_3d](games/04_runner_3d/README.md) |

They are data, not code — `game.json`, scene JSON and ES5 scripts — materialised
into a project by `GameLibrary`, with art imported from the asset store.

## Building and checking

```bash
# C++ core: host build + test suite (no NDK required)
cd native && make
#   → 219 checks, 0 failures — RESULT: PASS

# Regenerate the asset-store catalogue from Assets/*.zip
# Pillow is required, not optional: without it every sprite grid is a guess, and
# the generator now refuses to overwrite the 154 pixel-verified grids rather than
# silently downgrade them.  Use --force if you really mean it.
python3 tools/build_asset_store.py

# Validate all four games against the store manifest and the engine
python3 tools/validate_games.py
#   → 2 484 checks, 0 errors — RESULT: PASS

# Actually execute every game script headlessly (240 frames each)
node tools/run_games.js
#   → RESULT: PASS
```

The Android build (`./gradlew assembleDebug`) needs a JDK and the Android SDK; the
native Android build needs the NDK and `cmake`.

## Verification status — read this

| Layer | Status |
|---|---|
| Native C++ core | **Verified.** `make` → 219 checks, 0 failures |
| Asset store generation | **Verified.** 25 packs, 5 008 files, 154 sprite sheets |
| Games vs. store vs. engine | **Verified.** 2 484 checks, 0 errors |
| Game scripts, executed | **Verified.** `run_games.js` — 27 scripts × 240 frames, 89 checks |
| Kotlin / Gradle | **Not compiled** — no JDK or Android SDK in this environment |
| Native Android build | **Not built** — no NDK, no `cmake` |
| The four games on a device | **Not played** — no Android runtime here |

Every Kotlin file is uncompiled. The game scripts *are* executed — by
`tools/run_games.js`, which reimplements the `Api.kt` surface closely enough to
run them frame by frame, and which found 20 real bugs that a syntax check had
passed. Several of them silently removed whole features: the inventory could be
closed but never reopened, two games shipped with no enemies, and the runner's
systems could not find each other. That is stronger than "internally consistent",
and still not the same claim as "they work on a phone": the harness models the
engine, it is not the engine. The details are in
[docs/00-ARCHITECTURE.md §5](docs/00-ARCHITECTURE.md#5-verification).

Editor features that were requested but are **not implemented** — terrain editor,
Timeline, skeletal animation, node-based particle editor, drag-and-drop UI builder —
are listed explicitly in [docs/03-EDITOR.md §7](docs/03-EDITOR.md#7-features-that-do-not-exist).

## Asset licensing

24 of the 25 asset packs are bundled. `Super Pixel Objects Sample` is indexed but
**not** bundled: its licence excludes game-making tools, and this is one.
`FreeCharactersAnimationsAssetPack` forbids redistribution yet is used by three of
the four games — a tension flagged in
[docs/05-ASSET-STORE.md §5](docs/05-ASSET-STORE.md#5-licensing) rather than buried.
