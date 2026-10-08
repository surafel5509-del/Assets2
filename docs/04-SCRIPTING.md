# 04 — Scripting

Gameplay logic runs as **JavaScript**, executed by Mozilla Rhino 1.7.13 in
interpreted mode. `engine/script/` is `Api.kt` (the object model exposed to
scripts) plus `ScriptSystem.kt` (compilation, lifecycle, the prelude).

Visual scripting (`.bp` graphs) attaches through the same `Script` component, so a
graph and a `.js` file are interchangeable at runtime — see
[03-EDITOR.md](03-EDITOR.md#5-visual-scripting).

## 1. Why Rhino, and why 1.7.13

The dependency is pinned with a comment in `app/build.gradle.kts`:

```kotlin
// JavaScript scripting runtime (interpreted mode).
// 1.7.14 breaks on Android (javax.lang.model), keep 1.7.13
implementation("org.mozilla:rhino:1.7.13")
```

Rhino is pure Java, so it runs on Android with no native library and no JIT —
which matters because Android forbids writable-and-executable memory for apps.
QuickJS would be faster but requires an NDK build and a JNI bridge. Given that the
native core is not yet linked, adding a second native dependency now would be
premature; Rhino is the right call until the JNI boundary exists.

**The consequence for game code:** scripts must be **ES5**. `var`, not `let`/`const`;
`function`, not arrow functions. All four shipped games follow this, and
`tools/validate_games.py` syntax-checks every script with `node --check`, which
would fail on ES6 syntax if it leaked in.

## 2. Lifecycle

```js
function start()          {}   // once, after the object is active
function update(dt)       {}   // every frame
function onCollision(o)   {}   // physics contact
function onTrigger(o)     {}   // trigger-volume enter
function onTriggerExit(o) {}   // trigger-volume leave
function onTap()          {}   // screen tap while this object is hit
function onDestroy()      {}   // before removal
function onStop()         {}   // when the scene stops
```

`self` and `transform` both refer to the object the script is attached to. Scripts
are invoked by name through `send`, which is how the games' systems talk to each
other without holding references:

```js
var rm = scene.find("RoundManager");
if (rm) rm.send("onKnockout", self.name);
```

## 3. The API surface

Exposed by `Api.kt` and documented in-app by `ScriptEditorActivity`'s cheat sheet.

**`self` / `transform`** — `id name tag active order x y rotation scaleX scaleY
worldX worldY z rotX rotY rotZ scaleZ worldZ vx vy vz grounded flipX color text
size visible` plus `setPosition setWorldPosition move rotate distanceTo
distanceTo3 forward overlaps play stopAnimation isAnimationFinished setAnimSpeed
setShaderParam setMeshColor setColor getColor setText getText setVisible
setTexture burst setEmitting setSize getParent setParentTo child destroy
hasComponent setComponentEnabled send is animation index`, plus
`lightIntensity lightColor lightRange fov skyTop skyHorizon` for objects carrying
a `Light` or `Camera3D`.

The last six were added because the shipped games needed them and there was no
other way to reach those components from a script. Day-night lighting, a
speed-based FOV kick and a sky gradient are all scriptable now; before, the only
route was `send()` to a handler that did not exist, which the engine drops
silently.

**`scene`** — `name find findAll count spawn(name[,x,y[,z]]) shake raycast
camera3D gravity3D load reload camera gravityX gravityY`.

**`input`** — `axisX axisY a b aDown bDown touching tapped touchX touchY`.

**`time`** — `time frame fps`.

**`audio`** — `play(name[,vol]) beep stopAll`.

**Helpers** — `log warn error random(a,b) randomInt clamp lerp after(sec,fn)
every(sec,fn)`.

## 4. Patterns the shipped games rely on

These are not engine features; they are the discipline the games apply, recorded
here because they are what make the games behave.

**Frame-rate-independent smoothing.** Never `lerp(a, b, rate * dt)`, which drifts
with frame rate. Use exponential decay:

```js
var k = 1 - Math.exp(-rate * dt);
camX += (wantX - camX) * k;
```

Both camera rigs do this. It is the difference between a camera that feels the same
at 30 Hz and 120 Hz and one that does not.

**Per-swing hit ledgers.** An attack overlapping an enemy for several frames would
apply damage every frame without a guard. `Combat3D.js` records
`{attacker, victim, ttl}` pairs and expires them.

**Deferred structural change.** Scripts spawn and destroy freely, but the engine
flushes those changes after iteration completes — the same rule the native ECS
enforces, so behaviour will not change when the native world takes over.

**Cheap test before expensive test.** Enemy AI checks distance every frame but only
raycasts at 10 Hz, behind a cached result.

## 5. Hot reload

Script editing happens in `ScriptEditorActivity`, which watches the text buffer
(`TextWatcher`) and writes back to the project asset. Re-entering play mode
recompiles. There is **no live hot-swap into a running frame** — the script is
reloaded on scene load, not patched mid-execution. That is a real limitation
against the brief, and it is stated here rather than implied otherwise.

## 6. Verification

Two layers, because they catch different things.

**Static — `tools/validate_games.py`.** Runs `node --check` on every declared
script and cross-references every `audio.play("x")` and every literal
`self.play("clip")` against what `game.json` declares. 2 484 checks, 0 errors.

**Runtime — `tools/run_games.js`.** Implements the API surface from `Api.kt` and
the prelude from `ScriptSystem.kt` closely enough to actually *execute* every
script, 240 frames each, with synthetic input cycling through all the control
presets. It is deliberately strict: touching a property or method the engine does
not expose throws rather than returning `undefined`, because silently returning
`undefined` is exactly what would let a bug ship.

### What running them found

A syntax check had already passed on all 27 scripts. Executing them found 20
failures across seven classes, none of which a parse can see. The first wave came
from running the code at all:

1. **A texture-name syntax that does not exist.** `TilemapBuilder.js` built
   `"tileset_overworld.png#27"` as an atlas index. `Textures.image()` resolves a
   name straight to a file and `BitmapFactory` decodes it — there is no `#`
   parsing anywhere in the engine. The whole overworld would have drawn blank.
   Fixed by using the mechanism the engine does have: a single-frame `.anim` clip
   per tile, whose `cellUv` selects the sub-rectangle.

2. **`send()` calls to handlers that did not exist.** `ScriptSystem.call()`
   returns `null` when the name is not a function — it does not throw. So
   `send("setLightIntensity")`, `send("setStamina")`, `send("applyRoster")` and
   `send("setFov")` were silent no-ops: the day-night cycle ran and the sun never
   moved, the stamina bar filled and sprinting never gated, the character-select
   screen confirmed a fighter and applied nothing. Five fixed — two by adding the
   missing handler, three by exposing the underlying property (`lightIntensity`,
   `lightColor`, `skyTop`, `skyHorizon`, `fov`) on the scripting API and assigning
   it directly.

3. **Animation clips that were never materialised.** `game.json` declares `clips`
   per sheet, but nothing read that key, so no `.anim` files were written and
   every `play()` resolved to nothing — the sprite silently draws its entire
   sheet. `GameLibrary` now writes one `.anim` per declared clip. This one was
   systemic: it affected all four games, and the static validator could not see it
   because the names are built at runtime from `skin` and `states` params.

The third class is why the harness exists. `send()` to a dead handler and `play()`
of an undeclared clip produce no error, no warning and no visible difference in
the editor.

### What asserting on the run found

The first version of the harness passed while asserting almost nothing — nine
checks for 27 scripts run 240 frames each, one of which read a field nothing ever
wrote. Adding two assertions (every `scene.find()` name that never resolves is a
failure; every script defining `start()` or `update()` must actually have been
called) took it from 9 checks to 72 and found four more:

4. **Systems could not find each other.** Games 3 and 4 put every system script on
   one `GameSystems` object, but the scripts look each other up by *script* name —
   `scene.find("TrackGenerator")`, `scene.find("Combat3D")`. `scene.find` matches
   on object name, so every one returned null and the `if (x)` guard swallowed it.
   The runner's track generator never told the obstacle spawner anything; the
   hero's attack never reached the combat system.

5. **`RoundManager`** looked for a `RoundPips` object and sent it `refresh`.
   Neither exists — the renderer is `refreshPips()` on `HealthBars`. The
   round-win pips never lit.

6. **The inventory could be closed but never reopened.** It hid itself with
   `active = false` in `start()`, and `ScriptSystem.update()` skips anything not
   active in the hierarchy — so the `update()` listening for the reopen key never
   ran again.

7. **Both games with enemies shipped without any.** The four enemies are placed
   world entities authored inactive, nothing ever activates them, and `findAll()`
   filters on `isActiveInHierarchy()` — so even the code looking for them could
   not see them.

Classes 4 through 7 are not scripting bugs at all; they are scene-authoring bugs
that only surface once something executes against the scene. Every one of them
silently removes a feature.

### Checking the harness against the engine

A harness that models an API by hand can drift from it, and drift in one
direction is worse than no harness at all: if the harness offers a member
`Api.kt` does not have, it will pass a script the engine would reject. So the
harness now parses `Api.kt` on every run and fails on any member it models that
the engine does not expose.

That check found `setParentTo()` immediately — `InventoryUI.js` called it,
`Api.kt` never defined it, and the harness had been inventing it. Reparenting is
supported by the engine (`Scene.duplicate` assigns `go.parent` directly) but was
never exposed to scripts, while `getParent()` was. It is now, with a cycle check.

The same audit found a property, `index`, that neither the engine nor any reader
ever used — a dead write, removed rather than papered over with a property the
engine does not have.

### Scene props bind by display name

`Rigidbody2D` declares its damping property as `"Linear Drag"`. Six components
across two games said `"Drag"`. Nothing reports an unknown prop; the key is simply
never read, so every one of those bodies ran with zero linear damping — the
player and both fighters would have slid without ever settling.

`validate_games.py` now derives the valid prop names for every component from
`Components.kt` and rejects any key that is not one of them, which took the static
suite from 1 154 checks to 2 295. The check was verified by reintroducing the bug
and confirming it fails.

### Params that no script reads

`applyParams()` binds each `k=v` from a Script component onto the script scope with
`putProperty`. A key the script never mentions is not an error — it just sits
there while the value it was meant to set never changes. Five were dead:

  `facing`      Fighter.js declared it and never read it, so a fighter kept
                whatever `flipX` the scene authored until the first `resetRound()`
                corrected it. Now applied in `start()`.

  `minGap`      ObstacleSpawner.js declared it as the breathing room between
                hazards in a lane and never enforced it, so `z` was drawn freely
                per lane and two obstacles could land within a car length — which
                reads as an unavoidable wall even though a lane was nominally
                clear. Now enforced.

  `atlasCols`,
  `tileSize`    Both on TilemapBuilder.js. `atlasCols` fed the invented
                `atlas.png#27` syntax that was removed; `tileSize` describes the
                source atlas in pixels while the map is built in world units.
                Removed rather than left as decoration.

The validator now rejects any Script param the attached script never references,
taking the static suite to 2 484 checks. Verified by re-adding `tileSize` and
confirming it fails.

### Movement is integrated, and the assertions are mutation-tested

The harness integrates `vx`/`vy` into position each frame the way
`PhysicsWorld.fixedStep` does, so "the player moved" means something. Each
gameplay assertion was then mutation-tested: neutralising the player's velocity
write makes the harness fail with *"the Player never moved under four seconds of
input"*. An assertion that cannot fail is not a test.
