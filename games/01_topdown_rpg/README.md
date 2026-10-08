# Emberfall — 2D Top-Down RPG / Adventure

35 assets from 8 store packs · 7 scripts · 1 scene, 27 objects · portrait

## Controls

| Input | Action |
|---|---|
| Left joystick | 8-direction movement |
| Button A | Attack (sword arc in the facing direction) |
| Button B | Interact / advance dialogue |
| Tap | Talk to an NPC under the cursor |

## Systems

**Movement is 8-way; the sprite sheet is 4-way.** This is the single most important
thing to know about this game. Hashing all 32 cells of `blonde_man.png` shows only
**20 unique frames**: rows 0–3 are a 4-direction walk cycle where column 0 is
byte-identical to column 3, and rows 4–7 repeat those same four directions as a
2-frame action cycle. Velocity is continuous in 8 directions while the sprite snaps
to the nearest of 4. Treating the sheet as 8-directional makes the character face
the wrong way a quarter of the time.

**Tilemap chunking.** `TilemapBuilder.js` builds the map in chunks rather than one
object per tile. A 40×30 map as individual objects is 1 200 scene nodes, and the
per-frame transform resolution cost is immediately visible.

**Enemy AI** (`EnemyAI.js`) is a finite state machine — idle, patrol, chase, attack,
hurt, death — over the `enemy_animations_set` sheets (skeleton and vampire, each
with idle/movement/attack/take_damage/death cycles at 32×32).

**Combat** (`Combat.js`) resolves attacks as an arc in the facing direction: a
radius test plus an angular test. A radius-only test lets you hit enemies behind
you; a box test clips at the corners.

**Inventory UI** (`InventoryUI.js`) and **branching dialogue** (`NpcDialogue.js`)
round out the RPG layer, with `GameManager.js` holding quest and progress state.

## Assets

Characters come from `rpg_top_down_character_asset_pack_free`; tilesets from
`legacy_fantasy_debug_map` and `final`; enemy animation sheets from
`enemy_animations_set`; UI icons from `all_assets`; 9-slice frames from
`tinyrpg_dragonregaliagui_v1_0`; particles from `kenney_particle_pack`; audio from
`kenney_ui_pack`.

Two path details that are easy to get wrong:

- The manifest **strips ZIP wrapper folders**. Enemy sheets are
  `enemies-skeleton1_idle.png`, not `Enemy_Animations_Set/enemies-skeleton1_idle.png`.
- `free_asset_pack_thank_u_so_much` contains tile sheets, **not** backgrounds.

## Scripts

`GameManager.js` · `PlayerController.js` · `EnemyAI.js` · `Combat.js` ·
`InventoryUI.js` · `NpcDialogue.js` · `TilemapBuilder.js`
