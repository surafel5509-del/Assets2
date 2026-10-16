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
| **Rendering** | Textured sprites with flip, UV animation, editable normalized pivots, text, custom shaders, and sorting |
| **Physics 2D** | Fixed-step rigidbodies, oriented boxes & circles, gravity, friction/restitution, contact-point impulses, angular inertia/torque, optional rotation lock, triggers and grounded detection |
| **Physics 3D** | Box/sphere rigidbodies, gravity, restitution/friction, raycasts, contact-point angular impulses and editable inertia/rotation lock |
| **Particles** | Continuous or burst emitters with color/size curves, speed & size variance, gravity, drag, rotation/spin, textures and additive blending |
| **Professional world (v3.0)** | Procedural **Terrain** ground with real height queries, **Grass** fields with wind, animated **Water** (waves/fresnel/foam), a ready-made procedural **Human** character with walk cycle, **UI Panels**, and true **3D particle emitters** (point/sphere/box shapes, billboards) |
| **Sky & Sun editor** | Gradient sky, real **360° sky textures** (`Real Sky 360°` in the Asset Store, Camera3D → Sky Mode) or solid sky; a visible small **sun disc** in the sky with a **Sun editor**: colour, sunshine (intensity), temperature in Kelvin, elevation/azimuth, disc size, halo, 5 sun shader styles and an animated day cycle |
| **Textures & materials** | One texture for the whole object **or a different texture on every cube face** (top/bottom/front/back/left/right, per-face in the MeshRenderer), plus tiling, UV offset & rotation, Repeat/Clamp/Mirror wrap and pixel filtering — per object |
| **Particle presets** | **14 ready-made looks** for both 2D and 3D emitters: Fire, Smoke, Explosion, Sparks, Rain, Snow, Magic Trail, Healing, Confetti, Dust, Water Splash, Falling Leaves, Stars and Bubbles — pick one in the Inspector or call `self.burstPreset("Fire", 40)` |
| **ZIP import & extract** | Import **project zips** on the Projects screen, or **asset-pack zips** (textures/sounds/scripts/models/scenes) from the editor's *📦 Import ZIP* button; extraction is protected against zip-slip, oversized and oversized-entry archives |
| **Scripting** | JavaScript (ES6 subset via Rhino) with `awake`, `start`, `update(dt)`, `lateUpdate(dt)`, `onCollision`, `onTrigger`, `onTap`… plus timers, spawning, messaging, torque and impulses, an `events` bus, `game.timeScale` slow-motion, and a `world` API for sun/sky/terrain/water (`world.setSunTemperature(3200)`, `world.setTimeOfDay(0.5)`, `self.setFaceTexture("top", "Grass.png")`, …) |
| **Code editor** | Syntax highlighting, auto-indent, undo/redo, quick-symbol keyboard row, built-in API reference |
| **Assets & animation** | Import images/sounds, edit sprite-sheet clips, set frame rate/order/loop and per-clip pivot in the Sprite & Animation Editor |
| **Audio** | `AudioSource` component + `audio.play()` / `audio.beep()` |
| **Input & controllers** | Classic joystick/action buttons, drive pedals, optional calibrated phone-tilt steering/balance, touch, hardware keyboard & gamepad |
| **Scenes** | Multiple scenes per project, start scene, `scene.load("Level2")` |
| **Player** | Full-screen runtime ("Build & Run") with landscape / portrait setting |

### Built-in components
`SpriteRenderer`, `TextRenderer`, `Camera` (size, background, follow target with smoothing), `Rigidbody2D`, `Collider2D`, `Script`, `ParticleEmitter`, `AudioSource` — plus the Professional components `Sun`, `Terrain`, `Grass`, `Water`, `Human`, `UIPanel` and `ParticleEmitter3D` (Add Component → Rendering 3D / 2D, or the ＋ menu in the editor).

### Templates
* **Empty 2D** – camera + square
* **Platformer Demo** *(2D)* – run, jump, moving platform, collectible coins and score UI
* **Space Shooter** *(2D)* – enemy waves, bullets, explosions, scoring and game-over flow
* **Physics Sandbox** *(2D)* – drop rolling crates and bouncing balls into a ramp-and-wall arena
* **3D Demo** – third-person movement, lights, models, pickups, raycasts and a tumbling angular crate
* **Sunny Valley 3D** *(new)* – a full 3D adventure game: walk a human hero over procedural terrain and grass, splash by the animated pond, and collect 6 sun crystals under a real 360° sky with the Sun editor (try the day cycle!)
* **Crystal Cavern 2D** *(new)* – a complete 2D particle platformer with UI-panel HUD, fire torches, spark rain, bubbles, patrol slimes, a water hazard and 8 crystals
* **Hill Climb Racer** *(2D)* – spring-suspended 4×4, generated road, distinct Country/Desert/Frost/Moon stages, hazards, fuel, coins, garage upgrades, three rotating challenges and accelerometer balance controls. It is available from **New Project** and ships as the sample driving game.

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
* **self / transform:** `name tag active order x y rotation scaleX scaleY worldX worldY vx vy grounded angularVelocity`, `setPosition() move() rotate() addForce() applyImpulse() addTorque() setVelocity() destroy() child() parent distanceTo() overlaps() send() burst() setEmitting() setTexture() hasComponent() setComponentEnabled()`; 3D bodies also expose `angularVelocityX/Y/Z` and `addTorque(x,y,z)`
* **input:** `axisX axisY a b aDown bDown upgrade upgradeDown tiltX touching tapped touchX touchY`
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
│   ├── physics/             fixed-step 2D & 3D physics with angular contact impulses
│   ├── anim/                sprite-sheet clips, normalized pivots and runtime animation
│   ├── render/              GLES2 renderer, textures/text/particles, editor grid & gizmos
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
