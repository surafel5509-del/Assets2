# Emberfall Valley

A third-person open-world survival game set in a river valley, built entirely
from the store's 3D kit (`kenney_nature_kit`).

## The loop

Gather wood, berries and stone from the shrubs, cairns and stumps scattered
across the valley.  Light the three ridge beacons before nightfall -- each one
costs three wood and becomes a warming fire.  Night brings cold that bleeds your
stamina unless you are standing near fire, so the camp and any lit beacon are
where you survive the dark.  Light all three to hold the valley.

## Systems

| System | Where |
| --- | --- |
| Third-person movement, sprint, hop, carry | `Ranger.js` |
| Day/night cycle, weather, objectives, cold | `Valley.js` |
| Gatherable nodes with respawn | `Forage.js` |
| Ridge beacons that light for wood | `Beacon.js` |
| Camp fire: rest, sleep to dawn, eat | `Camp.js` |
| Herds that graze and bolt | `Wildlife.js` |
| Screen-space HUD | `Hud.js` |
| Pooled particle FX | `Fx.js` |

## Reading this game

`tools/run_games.js 11_emberfall --frames 600 --dump /tmp/w` runs it headless,
and `tools/preview3d.py 11_emberfall_valley_3d --world /tmp/w/*.json --out v.png`
renders what the engine will draw -- `--camera cam` is the player's own view,
`--camera free --dist 90` an overhead plan of the valley.
