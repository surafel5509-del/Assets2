# Studio 6.2 verification

## Local results — 2026-10-14

| Check | Result |
| --- | --- |
| Kotlin 1.9.23 compilation of runtime, project, export and editor-history sources | Passed |
| JVM tests | **46 passed; 1 conditional APK-export test skipped; 0 failures** |
| Kotlin syntax parsing across all main/test sources | Passed |
| Editor/view Kotlin check against Android API 34 and temporary AndroidX/Material API-shape shims | Passed; **not a real Android app build** |
| `git diff --check` | Passed |
| Gradle/Android build and lint | **Not run successfully**: Gradle distribution download failed in the restricted network environment |
| Emulator/device UI, graphics, audio and install tests | **Not performed** |

The direct JVM run used a temporary JRE 17/Kotlin toolchain outside the repository, Android API classes, and source-built JSON 20231013, Rhino 1.7.13 and JUnit 4.13.2 dependencies. No test-only substitutes for the engine or physics were used. The separate UI API-shape check is a limited type/syntax check: it does not validate AndroidX binary linkage, resources, theme inflation, rendering, touch behaviour, or lifecycle behaviour on a device. No APK was produced here.

The APK export test requires a source APK and signing inputs. Its assumption skip is not counted as a passing export test. The normal Gradle/CI build remains the authoritative release gate.

## Regression coverage

- Render-rate-independent fixed-step counts; bounded catch-up; fractional remainder; invalid deltas and clock reconfiguration.
- Time scaling, unscaled elapsed time, reset, pause/step/stop, and repeated play-session ECS cleanup.
- History branching/redo invalidation, selection restoration, combined entry/memory budgets.
- Atomic saves, backup rotation, path containment, recovery isolation, export exclusions.
- Versioned/legacy scene loading, unknown component preservation, invalid IDs/parents/cycles.
- 2,001-object deep hierarchies: traversal, transforms, duplication, deletion without recursive stack overflow.
- Hybrid ECS transform ownership, writeback, entity cleanup, stale handles, and duplicate component rejection.
- Parallel-job index coverage, surfaced worker failures, invalid batch sizes, independent scheduler lifetimes.
- Existing platformer/shooter/sandbox simulations, 3D physics/pickups, animated sprites and blueprints.

The existing simulation tests initially exposed the hybrid ECS rewinding scripts and physics. After correcting synchronization, the movement, grounding, animation, and blueprint tests passed.

## Reproduce using the supported build

With JDK 17 and Android SDK 34 installed:

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Inspect `app/build/reports/tests/testDebugUnitTest/`, the lint report, and the debug APK. CI executes these tasks with failure propagation and keeps reports as workflow artifacts rather than committing logs to source control.

## Device acceptance checklist (pending)

Test a compact landscape phone and a larger tablet, including a hardware keyboard where available.

- [ ] Create/open each 2D, 3D, animated, and blueprint sample; check rendering and input.
- [ ] Search hierarchy by name/tag/component; confirm ancestors and collapsed-branch matches appear.
- [ ] Filter assets and add components through search; verify selected-object editing.
- [ ] Run command search by button and Ctrl+K; verify Ctrl+S/Z/Shift+Z/Y without breaking text entry.
- [ ] Try all workspace presets; confirm inspector access and usable viewport area on a small display.
- [ ] Create/edit/delete/duplicate/reparent objects; undo/redo and verify selection.
- [ ] Pause and step; stop restores the edit scene. Repeat Play/Stop without accumulating ECS entities.
- [ ] Remove a referenced asset; Problems should select the affected object. Missing components must not crash Reset or lose data.
- [ ] Make edits, wait at least 30 seconds, force-stop the app (not a graceful exit), reopen and restore recovery. Verify the last saved scene was not replaced before confirmation.
- [ ] Save twice, restore the previous backup, undo the restoration, save and reopen.
- [ ] Exercise save failure/read-only/full-storage handling; ensure errors are visible and original data remains readable.
- [ ] Observe profiler while running/stopping; compare frame intervals with external tools. GPU time is not reported by this panel.
- [ ] Build a standalone game APK using real signing inputs; verify with `apksigner`, install, launch, and confirm no recovery/backups are packaged.
- [ ] Background/resume the editor and player; check audio, GL context recreation, input state and unsaved work.
