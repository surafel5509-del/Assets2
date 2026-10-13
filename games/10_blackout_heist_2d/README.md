# Blackout Heist

A top-down stealth heist.

One thief, three guards, two cameras and a searchlight, in a depot that is only
lit by the lamps on its walls.  The thief comes in over the fence at the loading
dock and has to get out the same way he came in.

**The objective chain.**  The keycard is on a desk in the store room.  The vault
will not open without it.  Three crates come out of the vault, and the van at the
loading dock will not start until they are on board.  The camera terminal in the
server room is optional and worth the trip: it blinds the whole security grid for
the rest of the run.

**The rule the game is played on.**  A guard sees you only when all three of
these are true: you are inside its range, you are inside its cone (which opens up
close, because peripheral vision is real), and there is an unbroken line of sight
across the level.  Break any one of them and you are gone again.  A guard's marker
goes from pale yellow to amber to red as its suspicion fills; red is the last
stage before it runs at you.

**Light and noise.**  Every lamp draws a pool on the floor, and that pool is also
the extra range a guard can pick you out at -- so a lit room is a room to cross
quickly and a dark corner is worth walking to.  Running is loud enough to be
heard through a wall; creeping is slow, quiet and much harder to spot.

**Controls.**  Left stick / WASD to move, push further to run.  Toggle Sneak to
creep.  Hold Interact to take the keycard, crack a crate or the safe, cut the
camera feed, or climb into the van.

**Winning and losing.**  A guard that reaches you ends the run, and so does the
alarm filling up while a camera or the searchlight holds you.  Press Interact on
the aftermath screen to start again.  Three crates in the van and you drive.

## Files

* `scenes/Main.scene.json` -- the depot: floor and wall tiles, dressing, the
  thief, three guards, three eyes, the HUD and the director.
* `scripts/Thief.js` -- player movement, the sneak toggle, the held interact.
* `scripts/Guard.js` -- patrol / suspect / chase / search, and the vision test.
* `scripts/Cam.js` -- the sweeping camera beam, and the dock searchlight.
* `scripts/Loot.js`, `scripts/Terminal.js`, `scripts/Exit.js` -- the objectives.
* `scripts/Heist.js` -- objectives, alarm, clock, win and loss.
* `scripts/Hud.js` -- objectives, loot, alarm bar and the hold meter.
* `scripts/Burst.js`, `scripts/Puff.js` -- noise, cracks and dust.

## Regenerating

```
python3 tools/build_heist.py
```

Rooms, corridors and their floor materials come from the `ROOMS` table at the top
of the builder; the walls are whatever is left between them.  Change a room and
the tiles, the walkable grid baked into the scripts and the connectivity proof
all follow.
