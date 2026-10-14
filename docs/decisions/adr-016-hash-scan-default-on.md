# ADR-016: Hash-based gallery scan, on by default

## Status
Accepted (2026-10-14). Replaces the date-watermark part of ADR-013. The one-tap delete prompt stays.

## Context
ADR-013 imported only items added after the switch was turned on, by `DATE_ADDED`. Older photos never came in, and a photo added while the vault was locked could be missed. The user wants the app to add whatever the vault does not have, by default, and keep a manual button for the same job.

## Decision
- **Scan, not watermark.** Every gallery photo and video (any app) is compared with the vault's complete copies by SHA-256 of the content (`PhotoDao.completeHashes`). Only missing files are copied and encrypted (`ImportWorker`, in chunks of 20).
- **On by default.** Runs when the vault opens (throttled to once per 10 minutes), when the gallery changes (immediate), and on the Settings "Scan" button (immediate). Photo access is requested once on open.
- **Hash cache.** A file's hash is cached under `uri|size|modified` in no-backup storage (`ScanCache`), so rescans read only new or changed files.
- **Delete prompt kept.** After a scan adds photos, the app asks once whether to delete originals that are verified in the vault (`MediaTransfer.verifiedOriginalUris`). Android confirms.

## Alternatives considered
- **Keep the date watermark.** Misses old photos and anything added while locked. Rejected.
- **Hash every file on each scan without a cache.** Correct but reads the whole gallery each time. Rejected.
- **Store hashes in the vault database.** Needs a migration for a cache. Kept as a file for now.

## Consequences
- **First scan reads the whole gallery.** Large libraries take a long time and use battery. Copies are made chunk by chunk, so cache use stays bounded.
- **Originals stay unencrypted** until deleted by the user.
- **Hash equality is the test for "already in the vault".** Two identical files are one vault item, as before.
- **Not verified on hardware yet.**
