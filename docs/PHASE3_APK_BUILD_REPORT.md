# Phase 3 GitHub Actions APK Build Report

**Result:** success
**Run:** [46012732760](https://github.com/surafel5509-del/Assets2/actions/runs/46012732760)
**Workflow:** `Build APK` / GitHub display name `Build S Engine Ultra APK`
**Branch:** `arena/5831a3b7-assets2`
**Head commit:** `2ee80bf1fc27e5bc1847cf7ca99b048d35cebad2` (`2ee80bf`)
**Trigger:** `push`
**Started:** 2026-11-27T14:05:17Z
**Finished:** 2026-11-27T14:08:43Z
**Job:** `Assemble debug + release APK` — success in 3m23s

## What was fixed

The previous Phase 3 push run `45959888504` failed at Kotlin compilation with four null-safety errors under the repository's Kotlin Gradle plugin `1.9.23`:

```text
Engine.kt:370:38  nullable receiver of type Rigidbody3D?
Engine.kt:451:20  nullable receiver of type Rigidbody3D?
Engine.kt:451:34  nullable receiver of type Rigidbody3D?
SceneRenderer.kt:121:44  nullable receiver of type EditorState?
```

Commit `2ee80bf` replaces the non-null direct calls with safe/null-checked access:

* steering velocity now uses `rb3?.vz ?: rb2?.vy ?: 0f`;
* 3D rigidbody writes are guarded by `if (rb3 != null)`;
* editor view mode uses `editor?.mode3D == true`;
* editor view access uses an explicit non-null assertion.

The same changes were verified locally with the offline suite (`RESULT passed=63 failed=0 skipped=1`, `FINAL_BUILD_EXIT=0`) and with a Kotlin `1.9.23` / JDK 17 main-source compile before pushing. The refreshed offline output is saved in [`PHASE3_TEST_LOG.txt`](PHASE3_TEST_LOG.txt).

## Successful Actions run

All steps completed successfully:

| Step | Result |
|---|---|
| Set up job | success |
| Checkout | success |
| Set up JDK 17 | success |
| Set up Gradle | success |
| Set up Android SDK | success |
| Build APKs | success |
| Upload debug APK | success |
| Upload release APK (debug-key signed, installable) | success |
| Post Set up Gradle | success |
| Post Set up JDK 17 | success |
| Post Checkout | success |
| Complete job | success |

The run produced two GitHub Actions artifacts:

| Artifact | Artifact ID | Size |
|---|---:|---:|
| `SEngine-debug-apk` | `15205380282` | 6,015,940 bytes |
| `SEngine-release-apk` | `15204930585` | 4,699,237 bytes |

Both artifacts are available from the run's **Artifacts** section:
<https://github.com/surafel5509-del/Assets2/actions/runs/46012732760#artifacts>

The release artifact is debug-key signed and installable. In line with the repository `.gitignore`, APK binaries are uploaded as Actions artifacts and are not committed to Git.

## Notes

The run has one non-fatal annotation: `gradle/actions/setup-gradle@v4` and `actions/upload-artifact@v4` target Node.js 20 and are forced onto Node.js 24 by the current runner. The build, upload steps and job conclusion are all successful.

Phase 1 / Phase 2 preservation tags remain unchanged. This successful APK build commit is tagged `phase-3-apk-build`.
