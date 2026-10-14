# Vault 0.4.0

**Update:** tap Settings → Updates → Update in the app, or `adb install -r app-release.apk`. Do not uninstall.

## Added
- In-app update: daily check with a notification, Update button, download progress, checksum and signer check, then Android's install confirm.
- Update check now says why it failed (no public release, offline, or other).
- Hash-based gallery scan on by default; "Delete originals already in vault" action.
- Drag selection in the Library (press and drag across photos in selection mode).

## Known limits
- Not verified on a physical phone beyond install and the update check.
- Intruder photo capture is not built (ADR-009).
- The first gallery scan reads every photo and can take a long time.
