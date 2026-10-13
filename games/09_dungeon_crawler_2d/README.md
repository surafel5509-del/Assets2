# 09 — Crypt of the Ember Sigil

A top-down dungeon crawler. Move with the **left stick**, swing the sword with
**A**. Three sigils are buried in the crypt; claim all three and the run ends,
but the longer you take the more the crypt fills with its own dead.

| | |
|---|---|
| Genre | 2D top-down action-RPG |
| Level | 72 x 56 tiles, 11 rooms, generated at runtime from `DeepDelve.js` |
| Art | Penusbmic *2D Pixel Dungeon* (tiles, monsters, props) + Legacy Fantasy *High Forest* (hero) |
| Enemies | Skeletal swordsman (melee), drowned bride (walks through walls), blood monster (ranged) |

## What makes it a game and not a demo

* **A generated level.** The room grammar is data; the map, the wall colliders,
  the torch placement and the loot are produced at load. Change `ROOMS` in
  `DeepDelve.js` and the crypt changes.
* **Three threats with three counters.** The swordsman is a straight fight. The
  bride ignores walls, so corridors are not safety. The blood monster backs off
  and spits, so standing still loses.
* **Escalation with a deadline.** Waves grow while sigils are unclaimed, which
  turns "explore" into a cost rather than a pause.
* **Regeneration as a mechanic.** Out of combat for 8 seconds and the warden
  heals; the delay, not the rate, is what makes a fight committal.

## Files

```
game.json                 assets, clips and the declared tile cells
scenes/Main.scene.json    camera, warden, HUD and the inactive templates
scripts/DeepDelve.js      level generation, waves, HUD, win/lose
scripts/Warden.js         player controller and sword arc
scripts/Grunt.js          melee AI (patrol / chase / attack)
scripts/Ghost.js          wall-phasing hunter
scripts/Crawler.js        ranged kiter
scripts/Spit.js Bol.js    projectiles
scripts/Fx.js             one-shot particle bursts
```

Built by `tools/build_dungeon.py`; verified by `tools/validate_games.py`,
`tools/run_games.js` and rendered offline with `tools/preview_scene.py`.
