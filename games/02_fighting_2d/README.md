# Blade & Bulwark — 2D Fighting Game

26 assets from 5 store packs · 6 scripts · 1 scene, 30 objects · landscape

## Controls

| Input | Action |
|---|---|
| Left joystick X | Walk / dash |
| Joystick up | Jump |
| Button A | Attack 1 |
| Button B | Attack 2 / block when held |
| Tap "HITBOX" | Toggle the live hitbox overlay |

## Systems

**Frame data, not collision.** Every move is described by startup, active and
recovery windows, held in `game.json`'s `frameData` table. A hit registers only
during active frames. This is what separates a fighting game from two health bars
drifting down, and it is why the numbers live in data rather than in code — tuning
a move means editing a table, not a script.

**Separate hurtboxes and hitboxes.** `Fighter.js` publishes both in world space.
`HitboxDebug.js` draws them live (green translucent = hurtbox, red = hitbox on
active frames only). Frame data is invisible by nature: a move that feels unfair is
almost always a hitbox active one frame longer than intended, or a hurtbox
extending past the art. The overlay makes both checkable in seconds, which is why
it ships in the game rather than living only in the editor.

**Combo scaling** (`ComboSystem.js`). Damage per hit falls as a combo extends, so
infinite combos are not optimal. The current combo is displayed with a pop.

**Ghost health bars** (`HealthBarUI.js`). The bright bar snaps down immediately on a
hit while a pale bar behind it drains over ~0.4 s after a short hold. The hold is
what makes a big hit legible, and the trail is what lets a player read how much
damage a move actually did mid-combo. Bars are anchored at their outer edge and
scaled on X, so they drain toward the screen edge as a fighting-game bar should.

**Best of three** (`RoundManager.js`) with intro, fight, K.O. slow motion
(timeScale 0.25), round-win pips, and a time-over rule that awards the round to
whoever has more health — equal health is a draw that awards nobody, which stops
turtling from being a winning strategy.

**Character select** (`CharacterSelect.js`). The roster is data: two fighters
(Soldier, Slime) with stats, portraits and a texture stem. Adding a fighter means
adding an entry plus importing its sheets — everything downstream reads from the
roster, so the select screen cannot drift out of sync with the fighters it chooses
between.

## Animation

All characters use `freecharactersanimationsassetpack` at 96×96 per frame, from
`SpriteSheets(96x96)/<Character>/With_Shadows/`. Every grid is store-verified:

| Action | Frames |
|---|---|
| Idle | 6 |
| Walk | 8 |
| Attack1 / Attack2 | 8 each |
| Block | 6 |
| Hurt | 4 |
| Death | 10 |
| Jump_Fall | 6 |

## Scripts

`Fighter.js` · `ComboSystem.js` · `RoundManager.js` · `HealthBarUI.js` ·
`CharacterSelect.js` · `HitboxDebug.js`
