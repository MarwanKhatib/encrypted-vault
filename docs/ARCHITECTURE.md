# Architecture

Package root: `com.example.encryptedvault`. Dependencies point downwards: `ui` → `data`/`media`/`work` → `crypto`.

| Package | Responsibility | Start reading |
|---|---|---|
| `crypto` | Key hierarchy (PIN / fingerprint / recovery phrase → master key), streaming AES-GCM files | `KeyHierarchy`, `VaultFiles` |
| `security` | In-memory session (lock/unlock), biometric prompt helper | `SessionManager`, `Biometric` |
| `data` | Room database, encrypted metadata, `VaultRepository` (the only place rows become `VaultItem`s), on-disk layout | `VaultRepository`, `VaultStorage` |
| `media` | Plaintext handling: thumbnails, face detection, source folder lookup, restore/share/import | `MediaTransfer`, `RestoreTargets`, `FaceScanner` |
| `work` | Background jobs: `ImportWorker` (encrypt), `FaceScanWorker` (scan old items) | `ImportWorker` |
| `appicon` | Launcher alias switching (disguises) | `AppIcon.kt` |
| `ui/*` | Compose screens, one sub-package per feature; `ui/common` has shared widgets | `ui/VaultRoot.kt`, `ui/MainScaffold.kt` |

## UI map
`VaultRoot` (setup → lock → app) → `MainScaffold` (tabs + back stack) → `LibraryScreen` / `ForYouScreen` / `AlbumsScreen` / `SearchScreen`; pushed screens: `CollectionScreen`, `PeopleScreen`, `ViewerScreen`, `SettingsScreen`. Pure logic that the screens use (and the unit tests cover): `Timeline` (grouping, events, ranges), `ForYou`, `Collections`, `SearchMatcher`, `RestoreTargets`, `Formatters`.

## Data flow
Import: picker → `MediaTransfer.stage` (cache copy + `SourceInfo`) → `ImportWorker` (hash, dedup, encrypt, thumbnail, face crops, encrypted metadata, row `complete`) → grid updates through `VaultRepository.items` (a Flow).
Restore: `ItemActions.requestRestore` → dialog → `MediaTransfer.restoreToGallery` (target from `RestoreTargets.resolve`) → optionally `VaultRepository.deleteForever`.

## Files on disk (`files/vault/`, names are the plaintext SHA-256)
`<hash>.enc` content · `<hash>.thumb.enc` thumbnail · `<hash>.face<N>.enc` face crop. Metadata (name, folder, faces) is in the DB column `photos.metaEnc`, encrypted.

## Rules that keep it safe
- Never add `fallbackToDestructiveMigration()`; every schema bump needs a `Migration` and an entry in `DatabaseMigrationTest`.
- Nothing sensitive goes in a clear-text DB column; extend `PhotoMetadata` (bump `PhotoMetadataCodec.VERSION`) instead.
- Plaintext temp files live only in `cache/{import,share,play}` and are removed by `MediaTransfer.sweepTemporaryPlaintext` (on lock and at start).
- Don't add the INTERNET permission; the manifest removes it from merged libraries.

## How to add…
- **A collection/album type:** add a case to `CollectionSource`, resolve it in `Collections.items/title`, list it in `AlbumsScreen`, add a case to `CollectionsTest`. Per-item flags it may need (e.g. `isPanorama`) live on `VaultItem`; new stored fields need a migration (`DatabaseMigrationTest`).
- **A new item action:** add it to `ItemActions` (dialog in `ItemActionDialogs`), then expose it in `LibraryScreen`/`CollectionScreen`/`ViewerScreen`.
- **A new photo edit (filter, adjustment):** add the field to `Edit` and apply it in `ImageEdits.colorMatrix`/`apply`, add its control to `ui/viewer/EditScreen.kt`, and cover pure parts in `ImageEditsTest`. Edits are saved through `MediaTransfer.stageBitmap` + `ImportWorker`, so they get encryption, thumbnail and face scan for free.
- **A disguise icon:** add vectors + `mipmap-anydpi/ic_launcher_<x>.xml`, an `<activity-alias>` in the manifest, and an entry in `AppIcon`.
- **A better face detector / identity grouping:** implement `FaceDetectionEngine` and pass it to `FaceScanner`.
