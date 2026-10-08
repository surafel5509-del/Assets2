# 07 — The Four Games

Each game is a directory under `games/` containing `game.json` (metadata and asset
declarations), `scenes/*.scene.json`, and `scripts/*.js`. They are data, not code:
`GameLibrary` materialises them into a project, importing their art from the asset
store.

```
games/
├── index.json                  generated; ordered list read by GameLibrary
├── 01_topdown_rpg/             Emberfall   — 2D top-down RPG / adventure
├── 02_fighting_2d/             Blade & Bulwark — 2D fighting
├── 03_action_3d/               Hollow Ridge — 3D action-adventure
└── 04_runner_3d/               Neon Overdrive — 3D endless runner
```

| | Title | Genre | Assets | Packs | Scripts | Scene objects |
|---|---|---|---|---|---|---|
| 1 | Emberfall | 2D top-down RPG | 35 | 8 | 7 | 27 |
| 2 | Blade & Bulwark | 2D fighting | 26 | 5 | 6 | 30 |
| 3 | Hollow Ridge | 3D action-adventure | 38 | 5 | 7 | 22 |
| 4 | Neon Overdrive | 3D endless runner | 20 | 6 | 7 | 21 |

Per-game detail is in each directory's `README.md`.

## 1. Design decisions worth recording

These are the choices that make each game work, and they are not obvious from
reading the code cold.

### Game 1 — 8-way movement over a 4-direction sheet

Movement is 8-directional; the sprite sheet is not. Hashing all 32 cells of
`blonde_man.png` shows only **20 unique frames**: rows 0–3 are a 4-direction walk
cycle (column 0 is identical to column 3), and rows 4–7 repeat those four
directions as a 2-frame action cycle.

An earlier claim that the sheet held 8 directions was wrong, and it is the kind of
error that produces a game where the character faces the wrong way a quarter of the
time. Movement resolves to the nearest of 4 sprite directions while velocity stays
continuous.

The tilemap is built in chunks rather than tile-by-tile, because a 40×30 map of
individual objects is 1 200 scene objects and the transform resolution cost shows
immediately.

### Game 2 — frame data, not hitbox collisions

A fighting game is defined by its frame data. Every move has startup, active, and
recovery windows, and a hit only registers during active frames. Hurtboxes and
hitboxes are separate, and `HitboxDebug.js` draws both live — a move that feels
unfair is almost always a hitbox active one frame too long, and that is unfindable
without the overlay.

Combo scaling reduces damage as a combo extends, so infinite combos are not
optimal. Health bars use a lagging "ghost" bar that drains over ~0.4 s, which is
what lets a player read how much damage a move actually did mid-combo.

### Game 3 — camera-relative everything

`input.axisY = up` must move the character *away from the camera*, not along world
−Z, so input is rotated by the camera yaw before it becomes velocity. Without that,
orbiting the camera makes controls feel inverted.

The camera is positioned from the target outward and pulled in on obstruction.
Moving the target instead would fight the follow smoothing. Grounding uses coyote
time and jump buffering, because on a touchscreen the thumb lands a few frames late
and a controller demanding frame-perfect input feels broken even when it is
"correct".

The hero is a billboarded sprite over 3D geometry. The Nature Kit has no rigged
characters and the engine has no skeletal animation, so this is a documented
trade-off rather than a missing asset.

### Game 4 — a track that can never wall you off

The track is a ring of 14 recycled segments, so live geometry is constant no matter
how far the run goes. Obstacles come from a 48-object pool, deactivated rather than
destroyed — per-frame allocation is what causes the periodic hitch that ruins a
runner.

The generator's invariant is that **at least one lane is always clear**, enforced
explicitly for every pattern. A generator that rolls obstacles independently will
eventually block the track, and the player dies to something they could not have
avoided.

The vehicle is driven kinematically, not by the physics solver. At runner speeds a
solver produces tunnelling and unpredictable bounces with no way for the player to
recover. Speed also drives a camera FOV kick (62° → 82°), which is the cheapest
effective speed feedback available.

## 2. What the validator caught

`tools/validate_games.py` cross-checks every game against the store manifest and the
engine's `ComponentRegistry`: 2 602 checks, 0 errors. It found real defects, not
stylistic ones:

1. **Nine enemy sheet paths still carried a stripped wrapper folder.** The
   generator removes ZIP wrapper directories, so the manifest path is
   `enemies-skeleton1_idle.png`, not `Enemy_Animations_Set/enemies-skeleton1_idle.png`.
2. **A stage backdrop that does not exist.** `free_asset_pack_thank_u_so_much`
   contains tile sheets, not backgrounds.
3. **All four game 3/4 asset paths were wrong on the first run** — a nonexistent
   pack id (`sprite_stack_cars` vs `spritestack_cars`) and three invented paths
   (`PNG/Car 05.png` where the real name is `car_005.png`).
4. **Game 3's hero pointed at a sheet with no valid grid.** The Memao 384×1968 sheet
   has sprites bleeding across row boundaries; the store correctly refuses to guess
   a grid, and the validator refuses to let a game declare an animation on a
   non-sheet.
5. **Component type names are registry keys, not class names** — `"Camera"`, not
   `Camera2D`; `"Script"`, not `ScriptComponent`. Properties serialise under their
   Inspector **display names** (`"Texture"`, `"Params"`, `"Is Trigger"`).
6. **Four `game.json` `id` values disagreed with their directory names**, which
   would have broken `GameLibrary`'s asset-path resolution.

## 3. Validating

```bash
python3 tools/validate_games.py              # all games
python3 tools/validate_games.py runner       # filter by slug substring
python3 tools/validate_games.py --repo /path/to/repo
```

Exits 0 only on `RESULT: PASS`. Checks include: `game.json` fields; every asset
reference against the manifest, its pack, and that pack's licence; scene JSON
against the 14-key component whitelist; parent references; script existence and
`node --check` syntax; `audio.play()` targets against declared audio; and the
absence of the excluded platformer/shooter templates.

## 4. Not verified

The games are **not run**. There is no Android runtime here, so nothing confirms
that they play correctly, that frame timings feel right, or that the cameras are
framed well. What is verified is that they are internally consistent and reference
only assets that exist. That distinction matters: a game can pass 2 602 checks and
still be no fun.
