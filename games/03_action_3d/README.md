# Hollow Ridge — 3D Action-Adventure

38 assets from 5 store packs · 7 scripts · 1 scene, 22 objects · landscape

## Controls

| Input | Action |
|---|---|
| Left joystick | Move, **camera-relative** |
| Drag right side | Orbit the camera |
| Joystick up | Jump |
| Button A | Attack (cone sweep in front) |
| Button B | Interact with the prompted object |

## Systems

**Camera-relative movement** is the whole point of the controller.
`input.axisY = up` must move the character *away from the camera*, not along world
−Z, so `CharacterController3D.js` rotates the input vector by the camera's yaw
before it becomes velocity. Without that, orbiting the camera makes the controls
feel inverted and unpredictable.

**Orbit camera with collision pull-in** (`ThirdPersonCamera.js`). The rig is
positioned from the target outward along the view ray and pulled in when a raycast
finds geometry in the way. Moving the *target* instead would fight the follow
smoothing and make the camera feel drunk. Pull-in is fast and pull-out is slow —
that asymmetry stops the camera strobing when the player stands in a doorway.

**Coyote time and jump buffering.** Both exist because on a touchscreen the thumb
lands a few frames late. A controller that demands frame-perfect input feels broken
even when it is technically correct.

**Two-tier enemy perception** (`EnemyAI3D.js`). A cheap distance check runs every
frame; the expensive line-of-sight raycast runs only at 10 Hz behind a cached
result, and only when the distance check has already passed. Doing it the other way
round means N raycasts per frame for N enemies — the easiest way to lose a frame
budget on a phone. The 100 ms lag in an enemy noticing you is imperceptible. The
LOS ray runs chest-to-chest so enemies do not see through the floor.

**Cone melee** (`Combat3D.js`) — a radius test plus an angular test around the
attacker's facing, in the horizontal plane only. Including Y would make it
impossible to hit an enemy standing at a slightly different height, which on uneven
terrain reads as the attack simply not registering. A per-swing hit ledger prevents
one swing applying damage on every overlapping frame.

**Day/night rig** (`LightingRig.js`) drives the directional light's elevation,
intensity and colour, plus the sky gradient. Both intensity *and* colour ramp:
ramping intensity alone gives a flat grey "someone turned a dimmer down" look,
while ramping toward amber is what actually sells sunset. Torch point lights
switch off during the day, which is a free win on a mobile GPU.

**Context interaction** (`InteractionSystem.js`) resolves the closest eligible
object in range and shows a verb that depends on what it is — "Open chest", "Pull
lever", "Rest at campfire". The prompt is one reused object, not one per
interactable, so walking through a village allocates nothing.

## Trade-offs

**Characters are billboarded sprites over 3D geometry.** The Kenney Nature Kit has
no rigged characters and the engine has no skeletal animation, so this is a
documented compromise rather than a missing asset. The hero uses the
`freecharactersanimationsassetpack` 96×96 sheets (Idle, Walk, Attack1, Jump_Fall,
Hurt, Death) — all grid-verified.

The Memao 384×1968 sheet was tried first and **rejected**: two of its columns have
sprites bleeding across row boundaries, so it has no admissible uniform grid and the
store correctly declines to guess one.

**Shadows are projected blob sprites**, not shadow maps. Cascaded shadow maps belong
to the Vulkan/GLES3 backend (see `docs/02-GRAPHICS.md`), not the GLES2 runtime this
scene runs on today.

**Models are `.obj`.** `MeshRenderer` loads OBJ only (`AssetKind.MODEL = ["obj"]`).
Every Nature Kit model ships in DAE/FBX/GLTF/OBJ/STL; this game references the OBJ
path and the store reports the rest in `altFormats`.

## Scripts

`ThirdPersonCamera.js` · `CharacterController3D.js` · `EnemyAI3D.js` ·
`InteractionSystem.js` · `Combat3D.js` · `LightingRig.js` · `AdventureHUD.js`
