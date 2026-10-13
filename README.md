# S Engine

**S Engine** is a 2D game engine **and** full visual editor that runs entirely on an Android phone or tablet – think "a small Unity in your pocket". Create a project, build scenes with a hierarchy / inspector / gizmos, write JavaScript behaviours in the built-in code editor, press **Play** to test immediately, then run your game full-screen.

> Written from scratch in Kotlin. OpenGL ES 2.0 renderer, custom physics, Mozilla Rhino JavaScript runtime. No NDK, no external game frameworks.

---

## Features

| Area | What you get |
|---|---|
| **Project manager** | Create from templates, open, play, rename, duplicate, delete, export / import projects as `.zip` |
| **Scene editor** | Viewport with grid, pan (drag) & pinch-zoom, tap-to-select, **Move / Rotate / Scale gizmos** with axis handles, snapping, frame selected |
| **Hierarchy** | Parent/child tree, collapse, visibility toggle, rename / duplicate / delete / reorder / create child / unparent |
| **Inspector** | Edit name, tag, sorting order, parent, transform (drag labels to scrub values), every component property, color picker, asset pickers, add / remove / reorder / reset components |
| **Undo / Redo** | Snapshot-based history for every edit |
| **Play mode** | Play / Pause / Step frame inside the editor; scene is restored when you stop (like Unity) |
| **Rendering** | Squares, circles, triangles, textured sprites (PNG/JPG/WebP) with flip, text, particles, sorting order, camera background |
| **Physics 2D** | Dynamic / Kinematic / Static rigidbodies, oriented box & circle colliders, gravity, bounciness, friction, drag, triggers, collision & trigger callbacks, `grounded` detection |
| **Scripting** | JavaScript (ES6 subset via Rhino) with `start`, `update(dt)`, `onCollision`, `onTrigger`, `onTap`… plus timers, spawning, messaging |
| **Code editor** | Syntax highlighting, auto-indent, undo/redo, quick-symbol keyboard row, built-in API reference |
| **Assets** | Import images and sounds from the device, create scripts, preview / assign / attach from the Assets panel |
| **Audio** | `AudioSource` component + `audio.play()` / `audio.beep()` |
| **Input** | On-screen joystick + A/B buttons, touch position / taps in world space, hardware keyboard & gamepad (WASD / arrows / Space / Enter) |
| **Scenes** | Multiple scenes per project, start scene, `scene.load("Level2")` |
| **Player** | Full-screen runtime ("Build & Run") with landscape / portrait setting |

### Built-in components
`SpriteRenderer`, `TextRenderer`, `Camera` (size, background, follow target with smoothing), `Rigidbody2D`, `Collider2D`, `Script`, `ParticleEmitter`, `AudioSource`.

### Templates
* **Empty 2D** – camera + square
* **Platformer Demo** – run, jump, moving platform, coins with particle bursts, score UI
* **Space Shooter** – spawning enemies, bullets, explosions, score, game over
* **Physics Sandbox** – tap to drop bouncy balls and crates onto a pyramid
* **Hill Climb Racer** – an original endless off-road game with springy 4×4 handling, generated rolling terrain, coins, fuel, local high scores, persistent garage upgrades, four changing biomes, touch pedals and keyboard controls. It is created automatically on first launch and is also available from **New Project**.

---

## Scripting example

```js
// Player.js  – params on the Script component: "speed=6, jump=11"
var coins = 0;

function update(dt) {
    self.vx = input.axisX * speed;              // joystick / A-D keys
    if (input.aDown && self.grounded) {         // A button / Space
        self.vy = jump;
        audio.beep();
    }
    if (self.y < -12) scene.reload();
}

function onTrigger(other) {
    if (other.tag == "Coin") {
        coins++;
        var fx = scene.spawn("CoinFX", other.worldX, other.worldY);
        fx.burst(24);
        after(1.5, function () { fx.destroy(); });
        other.destroy();
        scene.find("ScoreText").text = "Coins: " + coins;
    }
}
```

### API summary

* **Lifecycle:** `start()`, `update(dt)`, `onCollision(other)`, `onTrigger(other)`, `onTriggerExit(other)`, `onTap()`, `onDestroy()`, `onStop()`
* **self / transform:** `name tag active order x y rotation scaleX scaleY worldX worldY vx vy grounded color visible flipX text size`, `setPosition() move() rotate() addForce() setVelocity() destroy() child() parent distanceTo() overlaps() send() burst() setEmitting() setTexture() hasComponent() setComponentEnabled()`
* **input:** `axisX axisY a b aDown bDown upgrade upgradeDown touching tapped touchX touchY`
* **scene:** `find(name) findAll(tag) count(tag) spawn(name, x, y) load(name) reload() camera gravityX gravityY`
* **save data:** `storage.get(key, fallback)` / `storage.set(key, value)` for project-scoped numbers, booleans and strings
* **time:** `time.time time.frame time.fps` — **audio:** `play(file) beep() stopAll()`
* **helpers:** `log() warn() error() after(sec, fn) every(sec, fn) random() randomInt() clamp() lerp()`

Inactive objects make great **prefab templates** – `scene.spawn("Enemy", x, y)` clones them and activates the copy.

---

## Download

Every push and pull request runs the Android tests and builds both installable APK variants. Download them from **Actions → Build S Engine APK → latest successful run → Artifacts → `SEngine-Android-APKs`**. The artifact contains `app-debug.apk` and `app-release.apk` (the release variant is currently debug-key signed for convenient testing; replace the signing config before publishing).

Enable "Install unknown apps" for your browser / file manager, then open the APK.

## Building

Requirements: JDK 17 and the Android SDK (API 34). Android Studio Hedgehog or newer works out of the box.

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push is also built by GitHub Actions (`.github/workflows/android.yml`); download the APK from the run's **Artifacts** section.

Minimum Android version: 8.0 (API 26). Requires OpenGL ES 2.0.

## Project layout

```
app/src/main/java/com/sengine/
├── engine/
│   ├── Engine.kt            main loop, play/pause/stop, camera follow, particles
│   ├── Input.kt, AudioSystem.kt
│   ├── core/                GameObject, Component, Prop system, components, Scene + JSON serializer
│   ├── math/Affine.kt       2D transforms
│   ├── physics/             impulse-based 2D physics
│   ├── render/              GLES2 renderer, textures/text, editor grid & gizmos
│   └── script/              Rhino JavaScript runtime + script API
├── project/                 project storage, zip import/export, templates
└── ui/                      Projects screen, Editor (hierarchy, inspector, viewport, assets, console),
                             Script editor, full-screen Player, joystick, color picker
```

Projects are stored in app-private storage as plain JSON scenes plus an `assets/` folder:

```
<project>/project.json
<project>/scenes/Main.scene.json
<project>/assets/Player.js, hero.png, jump.wav …
```

## License

MIT
