# S Engine 3.0.0 — Two-Year Project System

This is the **24-month production plan** shipped with every S Engine project. It is a project-management system, not a claim that every optional future polish item already exists: v3 delivers the engine foundation, studio tooling and two complete games, then gives a team 24 dated milestones to ship and sign off a large commercial project.

## How it works

* `roadmap.json` is stored beside `project.json`, so it travels with exported/imported `.zip` projects.
* Month 1 starts on the project's creation date; due dates are derived from that epoch day.
* 24 monthly milestones are grouped into 8 quarterly phases.
* 19 milestones are **shipped** with S Engine v3 and are locked as engine capabilities.
* 5 milestones are **project sign-off** work; the editor's **🗺 Roadmap** dialog toggles them complete.
* `StudioPlan.load()` forward-merges new engine milestones without discarding user progress.
* Scripts can inspect the plan through `studio.report()`, `studio.progress()`, `studio.milestones()`, `studio.next()`, `studio.complete("M20")` and `studio.diagnostics()`.
* Project settings and diagnostics live next to the plan: **⚙ Settings**, **🩺 Diagnostics**, **🗺 Roadmap**.

## Phases

| Months | Phase | Outcome |
|---:|---|---|
| 1–3 | Foundation | Charter, schema/migrations, deterministic clock, quality settings and diagnostics |
| 4–6 | Future Physics | Broad phase, joints, soft bodies, shape casts, CCD and force fields |
| 7–9 | Script Runtime | Events, coroutines, FSMs, hot reload, budgets, profiler and durable saves |
| 10–12 | Camera & Renderer | Priority rigs, blends, bounds, shake, paths and 2D/3D director parity |
| 13–15 | Studio Editor | Settings UI, diagnostics, object templates for joints/cameras, inspector parity |
| 16–18 | Content & Games | Two complete reference games, regression suite, CI APK pipeline |
| 19–21 | Scale & Polish | Localization, accessibility, streaming/LOD, memory budgets, replay/netcode sandbox |
| 22–24 | Release | GPU particles/occlusion/post-FX polish, store build, QA sign-off, retrospective |

> The quarterly grouping above is the production narrative; `StudioPlan.PHASES` assigns three calendar months per phase in the persisted plan.

## Milestones

Legend: **S** = shipped engine capability in v3, **P** = planned project sign-off.

| ID | Month | Phase | Milestone | Acceptance criterion | State |
|---|---:|---|---|---|---|
| M01 | 1 | Foundation | Project charter, 24-month release map and schema | Roadmap persists with the project and reports progress | S |
| M02 | 2 | Foundation | Project schema v3, migrations and per-project settings | Old v1/v2 projects migrate without scene loss; settings apply at play start | S |
| M03 | 3 | Foundation | Deterministic fixed-step clock and seeded simulation | Same seed + inputs produce the same 60 Hz steps and interpolation alpha | S |
| M04 | 4 | Future Physics | Spatial-hash broad phase and solver iteration budget | Broad phase keeps candidate pairs conservative and reduces brute-force work | S |
| M05 | 5 | Future Physics | Constraint joints: distance, spring, rope and weld | 2D/3D joints converge, obey rest length and break at a configured force | S |
| M06 | 6 | Future Physics | Verlet ropes, cloth, wind and radial impulses | Soft bodies remain stable across frames and respond to wind/impulses | S |
| M07 | 7 | Script Runtime | Swept circle/box/sphere shape casts and CCD | Fast bodies hit the first collider; casts report nearest hit and distance | S |
| M08 | 8 | Script Runtime | Area force fields: explode, attract and wind | Forces affect tagged dynamic bodies inside a radius without tunneling | S |
| M09 | 9 | Script Runtime | Event bus, ES6 coroutines and deterministic timers | Ordered pub/sub, once/wildcard listeners and waitFor/waitSec fibers work | S |
| M10 | 10 | Camera & Renderer | FSM states, hot reload, instruction budgets and profiler timings | State enter/update hooks run; reload keeps persist; runaway loops are stopped | S |
| M11 | 11 | Camera & Renderer | Durable save slots and full-game state API | `game.save/load/has/keys` persist progress across scene loads and launches | S |
| M12 | 12 | Camera & Renderer | Camera rigs: priority, dead zones, bounds and look-ahead | Multiple cameras blend by priority; follow respects dead zones and bounds | S |
| M13 | 13 | Studio Editor | Trauma shake, blends, snapping and Catmull–Rom dolly paths | Shake decays smoothly; cinematic paths interpolate and can be stopped | S |
| M14 | 14 | Studio Editor | Camera director with script control and 2D/3D parity | `camera.playPath/activate/setBounds` drive both 2D and 3D rigs | S |
| M15 | 15 | Studio Editor | Project settings, diagnostics and health reports in the editor | Settings are editable; diagnostics report schema, scenes, assets and warnings | S |
| M16 | 16 | Content & Games | Object creation for joints and advanced camera rigs | Editor menu adds v3 joints/cameras and inspector exposes their properties | S |
| M17 | 17 | Content & Games | Full game 1: Citadel of Echoes, six scenes | Menu → two levels → boss → victory/game over, with saves and AI | S |
| M18 | 18 | Content & Games | Full game 2: Neon Apex GP, three scenes | Countdown, three laps, rival AI, boosts, best-lap saves and podium | S |
| M19 | 19 | Scale & Polish | Headless regression suite and CI APK pipeline | All engine/game tests pass; CI assembles installable debug + release APKs | S |
| M20 | 20 | Scale & Polish | Localization, accessibility and scalable UI | All UI strings externalized; touch targets and font scaling verified | P |
| M21 | 21 | Scale & Polish | Asset bundles, streaming, LOD and memory budgets | Large scenes stream in under target memory ceiling on mid-range phones | P |
| M22 | 22 | Scale & Polish | Replay and networking-ready deterministic sandbox | Replays reproduce a session; netcode spike validates lockstep transport | P |
| M23 | 23 | Release | Console-quality polish: GPU particles, occlusion and post FX | Render profile hits frame budget with reference games at High quality | P |
| M24 | 24 | Release | Release candidate: store build, QA sign-off and retrospective | Signed store build passes full QA matrix; two-year plan reviewed | P |

## Release status

* Engine / editor / tools / games: **v3.0.0 complete**.
* Default plan progress on a new project: **19/24 (79%)**, because 19 engine capabilities ship with the editor.
* M20–M24 are deliberately team/project deliverables; the roadmap dialog tracks them instead of pretending future production work has already happened.
* `ProjectDiagnostics.report()` includes roadmap progress and the next open milestone, so every health check shows both code and schedule status.

## Example

```js
// StudioProbe.js
function start() {
    log("plan=" + studio.years() + "y/" + studio.months() + "m progress=" + studio.progress() + "%");
    log("next=" + studio.next());
    log(studio.diagnostics());
    studio.complete("M20");
}
```

Open **🗺 Roadmap → View full two-year report** for dated milestones, acceptance criteria and per-project sign-off.
