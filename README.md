# Encrypted Photo Vault

Offline-only Android vault (Kotlin, Compose, Room, WorkManager, ML Kit bundled face model). **No INTERNET permission** (the manifest strips it even if a library adds it).
Design source: `docs/DESIGN_v5.md` (Gaps 1-14). Code layout and how to extend it: `docs/ARCHITECTURE.md`.

## Features (modelled on iOS Photos)
- **Tabs:** Library · For You · Albums · Search.
- **Library:** edge-to-edge grid with the newest photos at the bottom; floating **Years / Months / Days / All** control (Months shows date-range *events* such as "Nov 2–4, 2024"); title follows the scroll ("March 2024" · "12 Photos, 2 Videos"); pinch to change columns; **Select** mode with check circles and a bottom toolbar (share, favorite, add to album, delete, hide, restore); ⋯ menu for filters (All / Favorites / Photos / Videos), time range (presets + custom), settings and lock.
- **Viewer:** date/time title, filmstrip, white chrome that turns black on tap, swipe-down to dismiss, pinch/double-tap zoom, swipe between items, video playback, slideshow, favorite, **edit**, share (GPS stripped), info sheet with **caption**, original folder, restore target and people.
- **Edit (photos):** rotate 90° either way, flip, crop by dragging any corner or lock to a preset (1:1, 4:3, 3:4, 16:9, 9:16), filters (Vivid, Warm, Cool, Mono, Fade) and brightness/contrast/saturation sliders. Saving encrypts a new "(edited)" JPEG next to the original; the original is never changed.
- **Trim (videos):** the scissors button opens a preview with start and end handles. Saving exports a trimmed MP4 next to the original, which is never changed.
- **For You:** On This Day, Featured Photos, Recently Added.
- **Albums:** My Albums (Recents, Favorites, your own), People, Original Folders, Media Types (Videos, Screenshots, Screen Recordings, Panoramas, Animated), Utilities (Favorites, **Recently Edited**, **Hidden**, **Recently Deleted** with a 30-day window).
- **Look and motion:** Material rounded/outlined icons (`material-icons-extended`), filled icon marks the selected tab, fade-through between tabs, shared-axis slide for pushed screens, spring pop on favorite, selected tiles shrink with a spring.
- **People:** on-device face detection in photos and sampled video frames; you name faces and open a person's photos/videos.
- **Search:** name, caption, folder, type, person, year/month.
- **Restore to gallery:** every item remembers its source folder and goes back there (the folder is recreated if missing); items Android won't let an app write to their exact folder go to `<root>/Restored/<folder>`.
- **Unlock:** PIN keypad with persistent lockout, fingerprint (Keystore-bound), 24-word recovery phrase.
- **App icon:** original icon plus four disguises (Weather, Calculator, Notes, Clock).
- **Privacy:** file names, folders, captions and face tags exist only inside encrypted metadata; no INTERNET permission.
- **Backup:** Settings → Backup writes every item, with albums and captions, to one file encrypted with a passphrase you choose (format in `docs/decisions/adr-007-encrypted-export-and-restore.md`). Restore works on any device; items already in the vault are skipped. The app never sends the file anywhere; you choose where it is saved.

## Build, run, test
```
source env.sh
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # 64 JVM unit tests
./gradlew connectedDebugAndroidTest     # Room migration tests v1→v4 (needs the emulator; removes the app afterwards)
emulator -avd vault34 && adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Debug builds skip `FLAG_SECURE` so `adb screencap` works; release honours the "Block screenshots" setting.

## Known limits (vs. iOS Photos)
- Faces are detected but **not auto-grouped by identity** (needs a face-embedding model; the detector is behind `FaceDetectionEngine`).
- No video editing beyond trimming, markup, Live Photos, Portrait/Selfie detection, Memories movies, map, shared albums or duplicate-merge (dedup by content hash already prevents exact duplicates).
- Edited copies are saved as JPEG without EXIF (so no GPS), at most 4096 px on the long edge.
- Large videos decrypt fully in memory (GCM tag check), so very large files can be slow or fail on low-RAM phones.
- Restored `DATE_TAKEN` may be re-derived by the media scanner; file name and folder are preserved.
- Disguise icons change the launcher name/picture only; the app still opens the vault (no fake calculator screen).
- Share/playback temp files are wiped on lock and next start, not the instant the other app finishes.
- Lockout uses the wall clock; a clock change can shorten it.

## Acceptance checklist (Gap 13)
- [ ] `adb shell am kill` mid-batch (50+ files): no loss, no dupes, no `.tmp` orphans
- [ ] Reboot mid-encryption: same
- [ ] Double-tap encrypt: one entry
- [ ] 10 wrong PINs: lockout/backoff
- [ ] Split-screen with photo open: auto-lock
- [ ] Recents with photo open: blank thumbnail
- [ ] Share: temp file deleted; GPS EXIF stripped (verify with exiftool)
- [ ] Revoke photo permission mid-session: no crash
- [ ] `adb backup` yields empty/rejected
- [ ] Schema bump migration keeps photos decryptable
- [ ] HEIC + MP4 thumbnails render
- [ ] No-StrongBox emulator: TEE fallback, no crash
- [ ] Recovery phrase restores access after PIN reset
