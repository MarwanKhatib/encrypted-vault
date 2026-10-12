# Architecture

Package root: `com.example.encryptedvault`. Dependencies point downwards: `ui` → `data` / `media` / `work` / `backup` → `crypto`. `security` and `appicon` are called from `MainActivity` and `ui`.

| Package | Responsibility | Start reading |
|---|---|---|
| `crypto` | Key hierarchy (PIN / fingerprint / recovery phrase → master key), unlock lockout, streaming AES-GCM files, passphrase-encrypted backup stream | `KeyHierarchy`, `UnlockAttempts`, `VaultFiles`, `BackupCrypto` |
| `security` | In-memory session (lock/unlock), idle timer for opt-in auto-lock, biometric prompt helper, panic wipe, integrity checks | `SessionManager`, `IdleLock`, `Biometric`, `PanicWipe` |
| `data` | Room database (schema v5), encrypted metadata codec, `VaultRepository` (the only place rows become `VaultItem`s), on-disk layout | `VaultRepository`, `VaultDatabase`, `VaultStorage` |
| `media` | Plaintext handling: thumbnails, image edits, video trim, playback data source, restore/share/import staging | `MediaTransfer`, `ImageEdits`, `VideoTrim`, `VaultPlayer`, `RestoreTarget` |
| `backup` | Backup export and restore, and the record wire format | `VaultBackup`, `BackupRecords` |
| `work` | Background import: `ImportWorker` encrypts staged files in budgeted batches | `ImportWorker` |
| `appicon` | Launcher alias switching (disguises) | `AppIcon.kt` |
| `ui/*` | Compose screens, one sub-package per feature; `ui/common` has shared widgets and item actions | `ui/VaultRoot.kt`, `ui/MainScaffold.kt` |

## UI map

`VaultRoot` (setup → lock → app) → `MainScaffold` (tabs and back stack) → `LibraryScreen` / `ForYouScreen` / `AlbumsScreen` / `SearchScreen`. Pushed screens: `CollectionScreen`, `ViewerScreen` (with `EditScreen`, `TrimScreen`, `ItemDetailsSheet`), `SettingsScreen` (with `BackupControls`).

Pure logic used by the screens (and covered by unit tests): `Timeline` (grouping, ranges), `ForYou`, `CollectionSource` (`Collections.items` / `title`), `SearchMatcher`, `RestoreTarget`, `Formatters`, `CalculatorEngine` (decoy), `ImageEdits` (crop math and colour matrix).

## Data flow

- **Import:** picker → `MediaTransfer.stage` (cache copy plus `SourceInfo`, non-photo/video refused, size capped by `MAX_IMPORT_BYTES`) → `ImportWorker` (hash, dedup by content hash, encrypt, thumbnail, encrypted metadata, row `complete`) → grid updates through `VaultRepository.items` (a Flow).
- **Batching:** WorkManager input is limited to 10 KB, so `ImportWorker.batchesOf` budgets entries per batch. `enqueueBatches` chains batches in the unique work `"import"` with APPEND. `importAndWait` is used by tests.
- **Edit (photo):** `EditScreen` → `ImageEdits.apply` → `MediaTransfer.stageBitmap` (JPEG, no EXIF) → `ImportWorker`. The original item is never changed.
- **Trim (video):** `TrimScreen` → `VideoTrim.stageTrim` (Media3 Transformer 1.3.1) → `ImportWorker`. The original item is never changed.
- **Playback:** `VaultPlayer.buildVaultPlayer` reads through `VaultDataSource`, which decrypts one segment at a time (`vaultIo` helper).
- **Restore to gallery:** `ItemActions.requestRestore` → dialog → `MediaTransfer.restoreToGallery` (target from `RestoreTarget.resolve`, with `Restored/<folder>` as fallback) → optionally `VaultRepository.deleteForever`.
- **Backup:** `VaultBackup.export` writes header and records through `BackupCrypto.encrypting` (PBKDF2 key, 1 MiB AES-GCM chunks). Each record is `BackupRecords.write` followed by the item bytes. `VaultBackup.restore` reads through `BackupCrypto.decrypting`, skips items whose hash is already in the vault, and reports a `RestoreReport`.
- **Lock:** `MainActivity.onStop` calls `SessionManager.consumeStopLock`, then `lockNow`, which locks the session and runs `MediaTransfer.sweepTemporaryPlaintext` with `olderThanMs = 0`. The multi-window and configuration-change callbacks also lock. The optional idle lock (ADR-010) is a once-a-minute check while the activity is resumed; touch and key input reset it through `IdleLock`, and it locks only when `Settings → Privacy → Lock after idle` is set and the session is unlocked.

## Files on disk (`files/vault/`, names are the plaintext SHA-256)

- `<hash>.enc`: content, streaming v2 format (`VaultFiles`; legacy v1 still readable).
- `<hash>.thumb.enc`: encrypted thumbnail.
- Metadata (name, folder, caption, albums, flags) is in the DB column `photos.metaEnc`, encrypted by `PhotoMetadataCodec` (version 4).
- Plaintext temp files live only in `cache/{import,share,play}`. Lock removes all of them; app start removes leftovers older than 10 minutes. `PanicWipe` clears the cache directory.

## Build and quality gates

- `./gradlew testDebugUnitTest`: JVM unit tests (185 at last run).
- `./gradlew ktlintCheck detekt`: ktlint 1.3.1 (official style, `.editorconfig` max line length 160, Composable naming ignored) and detekt 1.23.6 with `config/detekt/detekt.yml`. Both must report zero findings.
- Detekt relaxes LongMethod, CyclomaticComplexMethod, LongParameterList, FunctionNaming and MagicNumber for Composables. `TooManyFunctions` thresholds: classes 20, interfaces 16, files 16, objects 16. `KeyHierarchy` carries a deliberate `@Suppress("TooManyFunctions")`.
- Public declarations need KDoc (detekt `UndocumentedPublicClass` and `UndocumentedPublicFunction` are active).
- `scripts/device-checks.sh` runs the instrumented tests and device drills (`unit`, `flows`, `formats`, `drill-kill`, `drill-reboot`, `recents`). The `unit` mode uses `am instrument`, because Gradle reinstalls the app and clears the sample files.

## Test map

- Unit (`app/src/test`): `BackupCryptoTest`, `BackupRecordsTest`, `Bip39Test`, `LockoutTimingTest`, `PinPolicyTest`, `PinVerifierTest`, `VaultFilesTest`, `PhotoMetadataCodecTest`, `ImageEditsTest`, `MediaTransferMimeTest`, `RestoreTargetsTest`, `VideoTrimTest`, `IdleLockTest`, `IntegrityTest`, `PanicWipeFilesTest`, `CollectionsTest`, `FormattersTest`, `CalculatorEngineTest`, `ForYouTest`, `TimelineTest`, `SearchMatcherTest`, `ImportBatchesTest`.
- Instrumented (`app/src/androidTest`, 48 tests on the emulator): `DatabaseMigrationTest`, `DeviceChecksTest`, `ImportFormatsTest`, `KeyHierarchyTest`, `KillMidBatchTest` (two kill-drill phases skip unless `killDrill=true`), `PanicWipeTest`, `TrimAndBackupFlowTest`, `VaultDataSourceTest`.

## Rules that keep it safe

- Never add `fallbackToDestructiveMigration()`. Every schema bump needs a real `Migration` and a case in `DatabaseMigrationTest`.
- Nothing sensitive goes in a clear-text DB column. Extend `PhotoMetadata` and bump `PhotoMetadataCodec.VERSION` instead.
- Don't add the INTERNET permission. The manifest removes it from merged libraries (`tools:node="remove"`).
- Backup files are never sent anywhere by the app; the user chooses the destination through the system file picker.
- The keystore and `keystore.properties` are outside version control. Release builds without them are signed with the debug key and must not be distributed.

## How to add…

- **A collection/album type:** add a case to `CollectionSource`, resolve it in `Collections.items` and `Collections.title`, list it in `AlbumsScreen`, and add a case to `CollectionsTest`. Per-item flags (for example `isPanorama`) live on `VaultItem`. New stored fields need a migration and a `DatabaseMigrationTest` case.
- **A new item action:** add it to `ItemActions` (dialogs in the same file), then expose it in `LibraryScreen`, `CollectionScreen` and `ViewerScreen`.
- **A new photo edit (filter or adjustment):** add the field to `Edit`, apply it in `ImageEdits.colorMatrix` or `ImageEdits.apply`, add its control to `ui/viewer/EditScreen.kt`, and cover the pure parts in `ImageEditsTest`. Saving goes through `MediaTransfer.stageBitmap` and `ImportWorker`, so the result is encrypted and thumbnailed like any import.
- **A new backup field:** add it to `BackupRecord`, write and read it in `BackupRecords` (bounded lengths), map it in `VaultBackup.toRecord` / `applyState`, and cover it in `BackupRecordsTest`. Bump the backup version byte in `BackupCrypto` if the layout changes, and record the decision in an ADR.
- **A disguise icon:** add vectors and `mipmap-anydpi/ic_launcher_<x>.xml`, an `<activity-alias>` in the manifest (disabled by default), and an entry in `AppIcon`.
- **A new decision:** add an ADR under `docs/decisions/` (next number) and link it from `README.md`.
