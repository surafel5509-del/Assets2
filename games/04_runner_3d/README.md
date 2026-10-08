# Neon Overdrive — 3D Endless Runner / Racer

20 assets from 6 store packs · 7 scripts · 1 scene, 21 objects · portrait

## Controls

| Input | Action |
|---|---|
| Left joystick X / drag | Steer |
| Button A | Jump (clears low obstacles) |
| Button B (hold) | Drift; tap to burn nitro |
| Tap on title / game over | Start / retry |

## Systems

**The track is a ring of 14 recycled segments.** Each segment sits at a fixed Z;
when the vehicle passes one, it is moved to the far end and re-decorated. Live
geometry is therefore constant no matter how far the run goes — the difference
between a runner that plays fine for 30 seconds and one that plays fine forever.
Nothing is created or destroyed in the loop; segments are deactivated and reused,
because per-frame allocation is what causes the periodic hitch that ruins a runner.

**Obstacles are pooled** (48 objects) and placed by pattern, never rolled
independently. The generator's invariant is that **at least one lane is always
clear**, enforced explicitly for every pattern including the alternating and pincer
layouts. A generator that rolls obstacles independently will eventually wall the
track off, and the player dies to something they could not have avoided. Difficulty
scales density and pattern, never impossibility.

**The vehicle is kinematic, not a rigid body.** At runner speeds a physics solver
produces tunnelling and unpredictable bounces, and the player has no way to recover.
Motion is driven directly and collisions are discrete AABB overlap tests, which
keeps the outcome of a crash legible and identical every time. Height is part of the
test, so jumping a log actually clears it.

**Near-miss rewards.** Passing within 1.6–3.0 units of an obstacle without hitting
it awards points and nitro. This is the mechanic that turns "avoid things" into
"take risks", and it is why the run has a skill ceiling.

**Speed-driven FOV kick** (`CameraRig.js`). The field of view widens 62° → 82° as
speed rises, plus 4° during nitro. Widening FOV makes the same world units per
second feel dramatically faster without changing the actual speed — the cheapest
effective speed feedback available. It is clamped hard, because past ~85° the screen
edges distort badly and players report motion sickness. The camera also leads
slightly toward the car's lateral velocity, keeping it off the screen edge during
hard steering, which is exactly when you most need to see it.

**Speed ramps toward a ceiling** rather than starting at top speed, because a run
that begins at maximum has no arc and no sense of risk building.

**Steering authority falls as speed rises** (`baseSpeed / speed`), so a full stick at
50 u/s changes lanes rather than teleporting across the track.

**Seeded layout.** Obstacle placement uses a seeded PRNG, so a run can be reproduced
from its seed — which is what would make a local leaderboard honest without any
server.

## HUD

Speed as a large numeral (the FOV kick conveys velocity; the numeral conveys the
number), zero-padded score, distance, a nitro bar that changes colour while burning
so "empty" is distinguishable from "in use" without reading a number, and a combo
flash that pops on scale.

## Assets

Track obstacles are Nature Kit `.obj` models (rock, tree, log). The player car is
`spritestack_cars/BlueCar.png`; traffic is `all_cars/car_005.png` and `car_009.png`.
Pickups and UI come from `all_assets`, particles from `kenney_particle_pack`, audio
from `kenney_ui_pack`.

## Scripts

`TrackGenerator.js` · `VehicleController.js` · `ObstacleSpawner.js` ·
`PickupSystem.js` · `RacerHUD.js` · `CameraRig.js` · `MenuFlow.js`
