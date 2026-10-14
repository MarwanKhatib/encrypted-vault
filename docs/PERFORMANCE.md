# Performance

Measured numbers only. Each row says device, build, and date, so results can be compared later.

## Measured 2026-10-14

| Metric | Device | Build | Result | Method |
|---|---|---|---|---|
| Cold start to first frame (`TotalTime`) | Pixel 5, Android 14, 1080×2340 | debug 0.1.0 (installed by `device-checks.sh unit`) | median **1100 ms**; runs 1507, 1076, 1118, 1100, 1096 ms | `am force-stop` then `am start -W -n com.example.encryptedvault/.MainActivity`, 5 runs. First run is slower (cold disk cache). |
| APK size, debug | — | `app-debug.apk` | **68.2 MB** | `ls -la app/build/outputs/apk/debug/` |
| APK size, release | — | `app-release.apk` (includes baseline profile) | **5.4 MB** | `ls -la app/build/outputs/apk/release/` |
| Unit tests | JVM | `testDebugUnitTest` | 198 run, 0 fail, 0 skipped | `cleanTestDebugUnitTest testDebugUnitTest` |
| Instrumented tests | Pixel 5 | debug | 49 run, 1 fail (`mp4VideoGetsADurationAndAFrame`, missing `sample.mp4`) | `scripts/device-checks.sh unit` |
| Static analysis | JVM | `ktlintCheck detekt` | 0 findings | Gradle |

Notes:
- Release APK size is from the last release build (2026-10-11), not rebuilt today.
- Start time is for a debug build. Release start time is not measured yet.

## Measured before (emulator, not re-measured)

From `ARCHITECTURE.md` (emulator, software GPU, 300 items, 25 swipes):

| Metric | Result | Note |
|---|---|---|
| Janky frames while scrolling Library | ~48–50% | Emulator GPU cannot show real per-frame cost. Idle frames clean. |
| Median frame time | 46–48 ms | Same as above |

These numbers do not describe the Pixel 5. Real-device scroll timing is still missing.

## Not measured yet

| Metric | Why not yet | How to measure |
|---|---|---|
| Library scroll jank on Pixel 5 | Needs 200+ items and an unlocked vault. `SeedLibraryTest` would wipe the device vault. | Unlock, then `adb shell dumpsys gfxinfo com.example.encryptedvault reset`, scroll, then `dumpsys gfxinfo ... framestats` and read janky %. |
| Import throughput (files/s, 500 files) | No timed run | `scripts/device-checks.sh formats` with `SAMPLES_DIR`, time the run |
| Video trim time | Needs `SAMPLE_MP4` | Time `flows` mode |
| Backup export / restore time for N items | No timed run | Time `TrimAndBackupFlowTest` or a manual export |
| Memory peak during thumbnail load | No profiler run | Android Studio profiler or `dumpsys meminfo` during scroll |
| Battery during idle lock timer | Not measured | `dumpsys batterystats` over 1 h |

## How to run a measurement

```sh
adb -s <serial> shell am force-stop com.example.encryptedvault
adb -s <serial> shell am start -W -n com.example.encryptedvault/.MainActivity
```

```sh
adb -s <serial> shell dumpsys gfxinfo com.example.encryptedvault reset
# ...scroll for 25 swipes...
adb -s <serial> shell dumpsys gfxinfo com.example.encryptedvault
```

Record the result here with date, device, build type, and commit hash.

## Targets (proposed, not yet agreed)

- Cold start under 1.5 s on Pixel 5, release build.
- Janky frames under 5% while scrolling 300 items, release build.
- Release APK under 10 MB.
