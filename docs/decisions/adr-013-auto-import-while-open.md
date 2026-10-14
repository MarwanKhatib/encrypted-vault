# ADR-013: Auto-import new gallery items while the vault is open

## Status
Accepted (2026-10-14). Replaces the picker-only import rule for new items. Device checks pending.

## Context
Users want new photos and videos to reach the vault without picking them each time. ADR-001 and the threat model kept the app picker-only and permission-free. Background import is impossible without the session key, which exists only while unlocked, and a background job runs when the app is not in front. So the only workable scope is: while unlocked and in the foreground.

## Decision
- **Opt-in, off by default** (Settings → Privacy → Import new photos automatically).
- **Permissions requested only when enabled:** `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` (API 33+), `READ_EXTERNAL_STORAGE` up to API 32. Denial leaves the switch off and shows a message.
- **Trigger:** a `ContentObserver` on the external images and videos collections, registered in `MainActivity.onResume` and removed in `onPause`. Each resume also checks once.
- **Only while unlocked:** the callback returns at once when the session key is null. Items added while locked are picked up at the next gallery change or next resume.
- **Watermark:** turning the setting on sets `autoImportSince` to now. Only items with a newer `DATE_ADDED` are imported. The watermark moves to the newest imported item after each batch is queued. It never moves back.
- **Same pipeline as the picker:** items are copied to `cache/import` (`MediaTransfer.stage`), encrypted by `ImportWorker` in batches, and deduplicated by content hash. Items over 500 MB are skipped.
- **No delete prompt.** Originals are never removed automatically. Android requires a user confirmation for each deletion anyway.

## Alternatives considered
- **Background import while locked.** Needs a key that can encrypt without the PIN. That weakens the model (ADR-004). Rejected.
- **Background import while unlocked only.** A job cannot run unlocked in practice: the vault locks when the app leaves the screen. Rejected as dead code.
- **Import the whole existing gallery on enable.** Large one-time cost and a surprise. Rejected: only new items.
- **Auto-delete originals.** Not possible silently on Android 11+. Rejected.

## Consequences
- **Originals stay unencrypted** in the gallery until the user deletes them. The Settings text says so. This is the main privacy cost of the feature.
- **Android 14 partial access:** if the user grants only selected photos, the observer sees only those. The app does not ask for full access.
- **Stage failure drops an item.** If a file cannot be copied to the cache, it is skipped and the watermark still moves past it. Accepted; the original is untouched and can be imported by hand.
- **Battery:** the observer runs while the app is open only.
- **Not verified on hardware** yet. Permission prompt, observer firing, and watermark behaviour need a phone run.
