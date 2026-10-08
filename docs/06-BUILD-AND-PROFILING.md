# 06 — Build & Profiling

Two independent capabilities: exporting a standalone installable APK from inside
the app, and measuring a running game.

## 1. In-app APK export

`export/` — 529 lines across 6 files.

```
ApkBuilder.kt       96   orchestration: repackage, patch, align, sign
AxmlPatcher.kt     155   binary AndroidManifest rewriting
ApkSignerV2.kt     101   APK Signature Scheme v2
ZipWriter.kt        79   ZIP assembly with per-entry compression control
SigningKeys.kt      52   key generation / storage
GameRuntime.kt      46   the embedded project bootstrap
```

### How it works

The engine does not compile a new APK from source — there is no Android toolchain
on a phone, and no way to install one. Instead it **repackages its own APK**:

1. Take the running app's APK as the source.
2. Embed the project under `assets/game/`.
3. Rewrite the binary `AndroidManifest.xml` with the game's package name, label and
   version (`AxmlPatcher`).
4. Strip the original signature (`MANIFEST.MF`, `*.SF`, `*.RSA`, `*.DSA`, `*.EC`).
5. Zip-align, storing already-compressed formats uncompressed — `png jpg jpeg webp
   ogg mp3 m4a arsc` — which is what alignment requires and what makes asset
   loading mmap-able.
6. Sign with APK Signature Scheme v2.

`GameRuntime` is the bootstrap that makes the result a game rather than the editor:
on launch it checks for `assets/game/`, and if present runs that project directly
instead of showing the project list.

### Guardrails

- `validPackage` enforces `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z0-9_]+)+$` and rejects
  `com.sengine.app` itself, so a build cannot overwrite the editor.
- `defaultPackage(projectName)` derives `com.sengine.game.<name>` and prefixes
  `game` when the cleaned name would start with a digit, which is otherwise an
  invalid Java package segment.
- Progress is reported per stage, so a failed build says which stage failed.

This is the honest scope: it produces a **signed, installable APK containing the
game and the engine runtime**. It is not a from-source Gradle build, and it does not
shrink or obfuscate — the exported APK is roughly the size of the editor plus the
project's assets.

## 2. Profiler

`Engine.kt` maintains a smoothed, `@Volatile` stats surface:

| Field | Meaning |
|---|---|
| `fps` | frames per second, averaged over a 0.5 s window |
| `scriptMs` | time spent in JavaScript `update` |
| `physicsMs` | time in the physics step |
| `renderMs` | time in draw submission |
| `drawCalls` | per-frame draw call count |

`drawCalls` is counted at the point of issue in both `Renderer2D` (2 sites) and
`Renderer3D` (2 sites), and reset per frame in `SceneRenderer` — so it is a real
count of GL calls, not an estimate from object counts.

The 0.5 s averaging window for FPS is deliberate: a per-frame FPS readout is
useless because it is just the reciprocal of the last frame time and jitters
constantly.

### What is not measured

- **GPU time.** There is no `GL_TIME_ELAPSED` query. `renderMs` is CPU-side
  submission time, which on a tile-based deferred mobile GPU can be far below the
  actual GPU cost — a scene can be GPU-bound while `renderMs` reads 1 ms. This is
  the most misleading gap in the profiler and should be closed first.
- **Memory.** No heap or native-heap counters (`Runtime.getRuntime`,
  `Debug.getNativeHeapAllocatedSize`).
- **Allocation rate**, which is what actually causes mobile frame hitches.
- **Per-object cost.** Everything is aggregate; there is no way to see which
  script or emitter is expensive.

## 3. Verification status

Neither capability can be exercised in this environment: `BuildActivity` needs an
Android device to have a source APK, and the profiler needs a GL context. Both are
**unrun here**. What is verified is that they compile against nothing — i.e. they
are not verified at all, because there is no `java` in this sandbox. That is stated
plainly rather than presented as tested.
