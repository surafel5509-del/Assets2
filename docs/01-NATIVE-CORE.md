# 01 — Native Core (C++17, Android NDK)

The foundation layer: memory, entity-component-system, scene graph, math, and the
render hardware interface. Written to be compiled both for Android (NDK, via
`native/CMakeLists.txt`) and on the host for testing (`native/Makefile`, g++).

**Size:** 4 035 lines of engine code across 16 files, plus a 1 161-line host test
suite. **Status:** compiled and passing on the host — `219 checks, 0 failures`.

```
native/
├── Makefile                 host build + test runner (g++, no NDK needed)
├── CMakeLists.txt           Android NDK build (unrunnable in this environment)
├── engine/
│   ├── core/    Types.h  Memory.h/.cpp  Handle.h  Hash.h
│   ├── math/    Math.h                            (374)
│   ├── ecs/     World.h/.cpp
│   ├── scene/   SceneGraph.h/.cpp
│   └── render/  RHI.h  NullRHI.h/.cpp
│                Pbr.h                             (304) reference BRDF
│                ShadowCascades.h                  (241) cascaded shadow maps
│   └── physics/ PhysicsWorld.h                    (920) rigid bodies, joints, rays
└── tests/       test_engine.cpp                  (1161) 219 checks, 18 groups
```

Line counts are engine headers and sources only; the two newest files are marked
because they are the ones the graphics docs depend on.

---

## 1. Memory

`engine/core/Memory.h/.cpp` — 143 + 180 lines.

Three allocators plus a frame stack, all verified by the test suite:

| Allocator | Purpose | Behaviour |
|---|---|---|
| `LinearAllocator` | Per-frame scratch | Bump pointer, single `reset()`. No individual frees. |
| `PoolAllocator` | Fixed-size components | Free-list over fixed blocks; O(1) alloc/free, zero fragmentation. |
| `BucketAllocator` | Small varied allocations | Size-classed pools; rounds up to the nearest bucket. |
| `FrameStack` | Frame-scoped temporaries | Marks and rewinds; anything allocated after a mark is released by rewinding to it. |

The design rule: **the frame loop allocates nothing from the general heap.**
Per-frame data comes from `FrameStack` or `LinearAllocator` and is released in one
operation. Long-lived data comes from `PoolAllocator` so component lifetimes cannot
fragment the heap over a long session.

### Handles and lifetime

`engine/core/Handle.h` — generational handles.

A handle is `{u32 index, u32 generation}`. Freeing a slot increments its
generation, so a stale handle fails an `isAlive` check instead of silently
referring to whatever now occupies that slot. This is the single most valuable
safety property in the core: dangling entity references are the class of bug that
otherwise surfaces as a crash hours into a play session.

### String interning

`engine/core/Hash.h` — component and tag names are interned to dense `u32`s via
`StringInterner`. Component *types* are therefore integers, which makes archetype
keys cheap to hash and compare. Interned indices are stable for the process
lifetime, which is what lets query result vectors stay valid across frames.

## 2. Entity-Component-System

`engine/ecs/World.h/.cpp` — 420 + 329 lines. Archetypal storage.

### Layout

An **archetype** is a unique set of component types. Entities with identical
component sets are stored contiguously in parallel arrays, one per component type,
inside that archetype's `Chunk`. Iterating a query is then a linear walk over
dense memory — the property that makes an ECS worth having.

Adding or removing a component moves the entity to a different archetype. That
migration is the expensive operation, and it is why component sets are designed
up-front rather than toggled per frame.

### The rules the tests enforce

These are not style preferences; each one is a bug that was found and fixed, and
the test suite now guards it.

1. **`World::add<T>` is get-or-add.** An early version placement-`new`'d
   unconditionally, which silently overwrote an existing component's data. It now
   reports whether it created the component via a `createdOut` flag.
2. **Migration order matters.** `moveTo()` calls `dst->copySharedFrom(src, srcRow,
   dstRow)` *before* `src->popRow(srcRow)`. Reversing these copies from a row that
   has already been swapped away.
3. **Archetype indices are stable forever.** Query result vectors hold archetype
   indices; if compaction ever renumbered them, results would silently point at the
   wrong entities.
4. **Structural change during iteration is illegal.** Spawning or destroying inside
   a query iterator invalidates the row indices being walked. Use the command
   buffer:

```cpp
auto& cmd = world.commands();
for (auto [e, hp] : world.each<Health>()) {
    if (hp.value <= 0) cmd.destroy(e);      // deferred, not immediate
}
world.flushCommands();
```

`CommandBuffer::add` always writes the payload, whereas `World::add` does not —
the distinction matters when you are resurrecting an entity from a pool.

### Component requirements

Components must satisfy `isComponentV` — trivially relocatable. Rows move between
archetypes with `memcpy`, so a component with a non-trivial move constructor or a
pointer to itself will corrupt on migration. This is checked at compile time.

## 3. Scene graph

`engine/scene/SceneGraph.h/.cpp` — 125 + 205 lines.

Parent/child transform hierarchy with **dirty-flag propagation**: writing a local
transform marks the node and its descendants dirty; world transforms are recomputed
lazily on read. A 1 000-node scene where one leaf moves costs one node's
recomputation, not a thousand.

Two bugs here are worth recording because both were silent:

- `valid()` originally conflated liveness with the `active` flag, so deactivating a
  node made its transform look invalid and children inherited garbage. There is now
  a dedicated `alive` bit, separate from `active`.
- `create()` referenced a `m_dummy` sentinel that did not exist. It uses
  `m_names` now.

The Kotlin `Scene`/`GameObject` layer mirrors this: objects have a parent, a local
transform, and children, and world transforms are resolved before rendering.

## 4. Math

`engine/math/Math.h` — 332 lines, header-only.

`Vec2/Vec3/Vec4`, `Quat`, `Mat4`, `AABB`, plus rigid-transform helpers.

Three defects were found by the test suite and are now regression-tested:

- **`decomposeRigid` returned the inverse rotation.** All four antisymmetric terms
  were sign-flipped. Any animation or camera driven through decomposition rotated
  the wrong way — and did so plausibly, which is what made it hard to spot.
- **`Quat::fromEuler` mapped pitch→Y and yaw→Z.** It is now built YXZ from
  `fromAxisAngle`, matching the convention the editor's Inspector displays.
- **`invertRigid`** is derived rather than a general 4×4 inverse, so it is cheaper
  and cannot accumulate error on a pure rotation+translation matrix.

There is also a **general `Mat4::inverse`** by cofactor expansion, added because
`invertRigid` cannot serve a view-projection matrix — that carries the perspective
divide and is not a rigid transform. It reports `false` on singular input rather
than returning garbage, which matters because a silently wrong projection
inverse turns into shadows and picking being wrong everywhere rather than one
obvious crash. The suite asserts `M * M⁻¹ == I` on a real camera matrix and that a
zero matrix is refused.

## 5. Render hardware interface

`engine/render/RHI.h` — 260 lines. `NullRHI.h/.cpp` — 109 + 187 lines.

`RHI` is the abstraction over the graphics API: device, command recording, buffers,
textures, render passes. Two backends are planned (OpenGL ES 3.x, Vulkan); one is
implemented (`NullRHI`) and it is the one the core is tested against.

`NullRHI` is not a stub. It validates the recording contract: it tracks resource
lifetimes, and it **counts draws issued outside a render pass as an error**. A
backend that silently accepted out-of-pass draws would let a real backend ship code
that only works by accident on one driver.

## 6. Physics

`engine/physics/PhysicsWorld.h` — 920 lines, header-only. Rigid bodies, collision
detection, joints and raycasting.

Written rather than ported from Bullet. Three reasons, all mobile:

- The shipped games need spheres, boxes and capsules — not soft bodies, cloth, or
  vehicle suspension curves.
- A Bullet CMake build under the NDK is a known source of pain, and a dependency
  that fights the build costs more than one you maintain.
- A solver you wrote is one you can profile and trim. On a phone the frame budget
  is the whole constraint, and dead features are not free.

**Solver.** Sequential impulses — accumulate and clamp a normal impulse per
contact over 8 iterations, with Coulomb friction capped by `mu * normalImpulse`,
then a separate position pass for penetration.

**Shapes.** Sphere, box, capsule. Sphere↔sphere and sphere↔{box,capsule} are
exact. **Box↔box is approximated by AABB** — full OBB separation testing needs 15
axes plus incident/reference face clipping, and its failure mode (a box
occasionally sliding through a corner) is invisible in the shipped games, where
boxes are axis-aligned ground and crates. It is flagged in the header rather than
left to be discovered.

**Joints.** Distance and point-to-point, each either rigid or spring-damper. A
hinge reduces to point-to-point plus an axis limit.

**Tuning constants** are named at the top of the header because they decide
whether a stack of crates settles or buzzes: `kLinearSlop`, `kBaumgarte`,
`kRestitutionThreshold`, iteration counts.

### Three solver rules the tests enforce

Each of these is a bug that was found, not a style choice.

1. **Position bias must not enter the velocity impulse.** Adding
   `(beta/h) * penetration` to the normal impulse looks correct but injects real
   velocity: a ball measured 0.8742 m/s upward off the floor at *both*
   restitution 0 and 0.8 — the bias moved it, not the bounce. Penetration is
   corrected by the position pass, which cannot add energy.

2. **Restitution is a target velocity captured before the solve**, not a scaling
   of the current velocity. Recomputing it inside the iteration loop means the
   first iteration applies the bounce, the second sees the bodies separating, and
   the accumulated impulse cancels the bounce it just made — restitution 0.8 came
   off the floor at exactly 0.0 m/s.

3. **Joints must subtract relative velocity and accumulate.** Applying a full
   position correction on every iteration drove the weld joint to NaN by the
   fifth second of simulation. Springs are additionally applied *once* per step
   as a force; inside the iteration loop a stiffness-60 spring behaved like 480.

`step(dt)` subdivides into fixed substeps and caps at 8 per call, so one long
frame cannot send the solver into a spiral. The consequence is that `step(1.0f)`
simulates 0.133 s and drops the rest — callers must step in a loop, which is what
a real frame loop does anyway.

## 7. JNI boundary

The planned seam between the C++ core and the Kotlin editor. Not yet linked — this
environment has no NDK.

The intended shape, so that the boundary does not become a per-frame bottleneck:

- **No per-object JNI calls.** Crossing JNI costs on the order of tens of
  nanoseconds; doing it per entity per frame is thousands of crossings. The native
  world is driven by a single `nativeTick(dt)` call and returns nothing.
- **Bulk data crosses as direct `ByteBuffer`s**, not as object arrays. The editor
  reads transform and component data from a mapped buffer the native side fills.
- **Handles, not object references.** The editor holds `u32` entity handles and
  resolves them lazily. Passing `jobject`s into native code would pin Kotlin
  objects and defeat the generational-handle safety property.

## 8. What is verified and what is not

**Verified here** — `cd native && make`:

```
LinearAllocator"          PoolAllocator"           BucketAllocator"
FrameStack"               Generational handles"    String interning"
ECS: entities and components"                     ECS: archetype storage and migration"
ECS: queries and iteration"                       ECS: command buffer"
Scene graph: hierarchy and dirty propagation"
Math: matrices, quaternions, decomposition"
NullRHI: resource lifetime and draw contract"
PBR: Cook-Torrance shading"
Shadow cascades: splits, matrices, stability"
Physics: integration and gravity"
Physics: collision response"
Physics: raycasting and joints"
---------------------------------------------
219 checks, 0 failures
RESULT: PASS
```

**Not verified here:**

- The Android NDK build (`native/CMakeLists.txt` is written but `cmake` and the NDK
  are absent).
- The JNI layer, which does not exist yet.
- Any interaction between the native core and the Kotlin runtime, because they are
  not linked.

The host `Makefile` uses `-MMD -MP` for dependency tracking. That is not a
stylistic choice: without it, editing a header leaves stale object files and the
suite passes against code that no longer exists.
