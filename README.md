# Encrypted Photo Vault

An offline Android vault for photos and videos, written in Kotlin with Jetpack Compose. Everything is encrypted on the device, and the app has **no INTERNET permission**: the manifest removes it even if a library requests it.

**Status:** tested on an emulator (unit, instrumented, and device scripts). It has not yet run on a physical phone. Read the known limits below before trusting it with photos you cannot lose.

- Design decisions and the security gap analysis: [`docs/DESIGN_v5.md`](docs/DESIGN_v5.md) (per-gap status and the device checklist).
- Decision records: [`docs/decisions/`](docs/decisions) (ADR-001 native Kotlin, ADR-002 streaming AEAD, ADR-003 duress PIN, ADR-004 PIN wrapper, ADR-005 face features removed, ADR-006 release hardening, ADR-007 encrypted backup, ADR-011 cyber visual design; ADR-012 status panel, face-down lock, intruder log, recovery reminder; ADR-013 auto-import while open; ADR-008 to 010: plain export, intruder capture, and optional idle lock (010 is implemented, 008 and 009 are proposals)).
- Code layout and how to extend it: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).
- Feature map, workflows, use cases, auto vs manual, open gaps: [`docs/FEATURES.md`](docs/FEATURES.md). Measured performance: [`docs/PERFORMANCE.md`](docs/PERFORMANCE.md).

## What you can import

- **Photos:** any image the phone's picker offers (JPEG, PNG, GIF, WebP, HEIC). Only HEIC decoding has been tested on a real file.
- **Videos:** any video the picker offers. MP4 has been tested. Other containers depend on the phone's codecs.
- **Anything else** (documents, text, archives) is refused at the picker, and the app says how many files it skipped.
- **Not supported:** RAW and DNG photos.

Files over 500 MB are skipped.

## Features (modelled on iOS Photos)

- **Tabs:** Library, For You, Albums, Search.
- **Library:** a timeline grid grouped by date, with a **Select** mode for bulk actions (share, favourite, add to album, hide, delete, restore). The ⋯ menu has grouped filters (All items, Favorites, Photos, Videos), a time range, sort order, Settings, and Lock now.
- **Viewer:** pinch and double-tap zoom, swipe between items, video playback, slideshow, favourite, edit, trim (videos), share (GPS removed when the setting is on), an info sheet with an editable caption, and restore to the original folder.
- **Edit (photos):** rotate, flip, crop by dragging any corner or by a locked ratio (1:1, 4:3, 3:4, 16:9, 9:16), five filters, and brightness, contrast, and saturation. Saving writes a new "(edited)" JPEG; the original is never changed.
- **Trim (videos):** start and end handles with a preview. Saving writes a new MP4; the original is never changed.
- **For You:** On This Day and Recently Added.
- **Albums:** your own albums, Favorites, Original Folders (grouped by the folder each item came from), Media Types, and Utilities (Hidden, and Recently Deleted, which keeps items for 30 days).
- **Search:** by name, caption, folder, type, and year or month.
- **Restore to gallery:** each item goes back to its original folder and name. If the folder is gone it is recreated; if Android refuses the exact folder, the item goes to `<root>/Restored/<folder>`.
- **Delete originals:** after an import, the app can ask Android to delete the phone's copies. Copies in Google Photos or another cloud backup are not removed.
- **Backup:** Settings → Backup writes every item, with albums and captions, to one file encrypted with a passphrase you choose. Restore works on any device, and items already in the vault are skipped. The app never sends the file anywhere; you choose where it is saved.
- **Plain export (unencrypted):** Settings → Backup → Export plain copies writes every non-trashed photo and video as ordinary files to a folder you pick, plus `manifest.csv` (names, folders, dates, favourite and hidden flags, captions, albums). The files are **not encrypted**; the app warns first. Location is removed from JPEG copies while Settings → Privacy → Remove location when sharing is on.
- **Unlock:** a PIN pad (6–12 digits; a physical keyboard works too), fingerprint through the Keystore, a 24-word recovery phrase, lockout after wrong PINs, and an optional duress PIN that erases everything.
- **Privacy:** screenshots and the recents preview are always blocked, and the vault locks as soon as the app leaves the screen. Multi-window also locks it. Optional idle lock (Settings → Privacy, off by default) locks after 1, 5 or 15 minutes without touch or key input. File names, folders, captions, and album titles are only stored encrypted.
- **Disguise:** the launcher icon can be switched to Weather, Calculator, Notes, or Clock.

## Build and run

Requirements: JDK 17 and the Android SDK (compile SDK 34).

```sh
export JAVA_HOME=$HOME/tools/jdk17 ANDROID_HOME=$HOME/Android/Sdk
./gradlew assembleDebug                  # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest              # JVM unit tests
./gradlew ktlintCheck detekt             # formatting and static analysis (both must pass)
./gradlew ktlintFormat                   # apply ktlint's formatting
```

Instrumented tests and the device checks run on an emulator or a phone with USB debugging:

```sh
scripts/device-checks.sh unit            # every instrumented test (48 tests; 2 kill-drill phases skip)
scripts/device-checks.sh flows           # trim and backup end to end (needs SAMPLE_MP4=<file>)
SAMPLES_DIR=<dir> scripts/device-checks.sh formats   # import every sample in a folder
scripts/device-checks.sh drill-kill      # force-stop mid-batch, relaunch, verify no loss or duplicates
scripts/device-checks.sh drill-reboot    # reboot mid-batch, relaunch, verify
scripts/device-checks.sh recents         # release build: check the window is secure
```

The `unit` mode runs through `am instrument`, not Gradle, because Gradle reinstalls the app and clears the sample files.

### Release signing

Release builds read `keystore.properties` from the project root. The file is git-ignored; copy `keystore.properties.example` and fill it in. The keystore itself lives outside the repository. Without the file, the release build is signed with the debug key and the build prints a warning. Do not distribute that build.

Back up the keystore **and** `keystore.properties`. Without both, you cannot ship updates to the same app.

## Known limits

- **Not tested on a real phone.** Split-screen auto-lock, the recents thumbnail, and the share flow are unverified on a device.
- **Formats:** RAW and DNG are not supported. Video containers other than MP4 depend on the phone's codecs.
- **Large videos** play from encrypted storage in segments; memory use stays at about one segment, so very large files are not loaded whole. Phones with very little memory may still stutter on 4K.
- **Edited copies** are JPEG and carry no EXIF metadata. Edits are made from a downsampled decode (about 2400 px on the long edge), so the saved copy is smaller than a full-resolution original.
- **Restore** keeps each trashed item's original trash time for backups made in format v2 (this version). Backups made before, in format v1, only recorded that an item was trashed, so those items get a fresh 30-day window.
- **Restored dates:** the media scanner may re-derive the date taken; file name and folder are kept.
- **Disguise icons** change the launcher name and picture only. The app still opens the vault; there is no separate fake calculator screen.
- **Temporary files** for sharing and playback are removed when the vault locks. Leftovers from a crash are removed at the next start if they are over 10 minutes old. Files are not removed when the other app finishes with them.
- **Lockout** is timed on the monotonic clock, so changing the device time does not shorten it. A lockout that spans a reboot is enforced in full again.
- **Not in scope:** face recognition and grouping (removed, see ADR-005), video editing beyond trimming, markup, Live Photos, memories, map view, shared albums, and near-duplicate detection. Exact duplicates are blocked by content hash.

### Open work

Not built yet, in rough order:

- **Intruder capture** (ADR-009): front-camera frame on wrong PIN. Proposed only; needs Camera2 and a real phone to verify.
- **Location removal for HEIC and PNG copies** in plain export: only JPEG copies are cleaned (ADR-008).
- **Library, Albums, and Viewer layout pass:** only the colours, type, and icons changed (ADR-011). No screen layout was redesigned.
- **Device checks** not yet run on a phone: split-screen lock, recents preview, share flow, plain-export folder picker, scroll smoothness.
- **RAW and DNG** decoding: no decoder available offline.

## Contributing

- Every schema change needs a real `Migration` and a case in `DatabaseMigrationTest`. Never add `fallbackToDestructiveMigration()`.
- Public declarations need KDoc; detekt enforces it.
- Run `./gradlew ktlintCheck detekt testDebugUnitTest` before committing.
