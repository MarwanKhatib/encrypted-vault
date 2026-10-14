# Vault 0.2.0

**Update safely:** `adb install -r app-release.apk`. Do not uninstall. This release is signed with the release key; a debug-installed app cannot be updated in place (see docs/RELEASE.md).

## Added
- Settings status panel: vault state, auto-lock, face-down, fingerprint, key storage (hardware or software), vault size, free space.
- Opt-in face-down lock.
- Failed PIN log (times only, last 20).
- Recovery file re-check reminder every 90 days.
- Save recovery phrase to a file (setup screen).
- Opt-in auto-import of new gallery photos and videos while the vault is open (asks for media read permission). Originals stay in the gallery, unencrypted, until you delete them.
- Daily purge of expired Recently Deleted items.

## Changed
- Setup clears its PIN copy after use.
- Device checks no longer uninstall the app.

## Known limits
- Not verified on a physical phone yet.
- Intruder photo capture is not built (ADR-009).
- Auto-import does not run while the vault is locked.
