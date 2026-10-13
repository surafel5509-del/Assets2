# Sunken Citadel

A third-person 3D arena-combat game: a knight, a walled citadel and three waves of
Hollows, built from the store's 3D kit (`kenney_nature_kit`) dressed as stone.

## The loop

Waves spawn in a ring around the courtyard and walk at you.  Each enemy telegraphs
its swing before it lands -- a red-eyed wind-up -- and opens up afterwards.  Spend
stamina on a light combo (three swings), a heavy swing (interrupts a wind-up), or a
dodge roll with real invulnerability frames.  Clear three waves, then the Warden of
the Keep wakes up and fights with a slam, a sweep and a charge, faster once it is
below half health.

## Systems

| System | Where |
| --- | --- |
| Movement, combo, heavy, dodge, stamina | `Knight.js` |
| Waves, rest phases, boss summon, score | `Citadel.js` |
| Telegraph/strike/recover melee enemy | `Hollow.js` |
| Faster circling enemy | `Revenant.js` |
| Boss with three attacks and a rage phase | `Warden.js` |
| One definition of a hit | `Combat.js` |
| Walking-over loot | `Pickup.js` |
| Pooled combat FX | `Fx.js` |
| HUD: wave, health, stamina, combo, score | `Hud.js` |

## Reading this game

`tools/run_games.js 12_sunken --frames 900 --dump /tmp/w` runs the waves headless
(an idle knight is killed by wave 2, which is the harness proving the enemies
actually attack), and `tools/preview3d.py 12_sunken_citadel_3d --world /tmp/w/*.json
--out c.png --camera free --target 0,0,0 --dist 60 --pitch 26` renders the
courtyard; `--camera cam` is the knight's own view.
