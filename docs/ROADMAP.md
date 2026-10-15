# Capabilities and next milestones

## Current boundary

This is a native Android game editor, not a desktop Unity clone. Its active viewport uses OpenGL ES 2.0. The existing runtime supports sprites, primitive/OBJ meshes, basic lighting and post effects, simple box/circle and box/sphere physics, JavaScript (Rhino), blueprints, sprite animation, particles, audio, and game APK repackaging.

The Studio upgrade integrates editor search, diagnostics, recovery, safer storage, bounded history, profiler views, fixed-step scheduling, hybrid ECS synchronization, and job error/lifecycle fixes. Tests cover core behaviour, but no production certification or performance guarantee is implied.

## Experimental or incomplete areas

The repository contains modules called ray tracing, virtualized geometry/Nanite, global illumination/Lumen, Burst, FSR/DLSS, networking, XR, terrain, skeletal animation, and others. Some contain useful algorithms or configuration/data structures. They are **not verified end-to-end equivalents** of the systems those names evoke. Selecting a quality setting or adding a component does not prove renderer/runtime integration.

Similarly, listing an extension in `AssetKind` does not make it a supported importer. OBJ and the existing sprite/audio/script workflows are distinct from a complete FBX/glTF/PSD pipeline. There is no completed multi-platform build service, production multiplayer backend, or desktop plugin ecosystem.

## Engineering milestones

### 1. Finish release qualification

- Run the complete Gradle build, lint, and device checklist on phone/tablet hardware.
- Verify signed game exports using `apksigner` and install/run on a clean device.
- Add Android instrumentation coverage for recovery, command search, gestures, focus, and configuration changes.
- Establish performance budgets for 1k/10k objects and large textures. Snapshot history and file operations still serialize on the editor thread; move expensive work to a versioned background save pipeline.

### 2. Asset pipeline and scene authoring

- Stable asset GUIDs, dependency indexing, transactional rename/move, missing-reference repair, import settings, and thumbnails cached off the UI thread.
- Verify one real glTF importer rather than advertising unsupported extensions.
- Scene transactions, multi-selection, transform undo coalescing, persistent prefab overrides, and migration fixtures.
- Harden archive import with entry/expanded-size budgets and transactional import cleanup.

### 3. Rendering and physics

- Explicit renderer capability negotiation and separation of actual GPU features from experimental models.
- Material compilation, resource lifetime/context-loss tests, batching, measured GPU profiling, and device-specific quality limits.
- Shared fixed-step phases for joints/controllers and explicit script fixed-update callbacks; interpolation in rendering.
- Broadphase/contact stability, scale-aware colliders, and collision regression suites before new solver features.

### 4. Runtime extensibility and shipping

- System lifecycle/dependency registration, cancellation and backpressure for tasks, resource leak checks.
- Authoritative ECS ownership rules and structural command buffers if scaling beyond the current hybrid bridge.
- Script debugging, hot-reload state migration, plugin version contracts, and trust boundaries for imported scripts.
- A separately tested network transport, authority model, replication and reconciliation—not just networking configuration classes.

Each milestone should land with a working sample, reproducible tests, measured constraints, and a clear supported/experimental label. Feature counts and branding are not acceptance criteria.
