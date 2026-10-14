# Vault 0.2.0

**Update safely:** `adb install -r app-release.apk`. Do not uninstall. This release is signed with the release key; a debug-installed app cannot be updated in place (see docs/RELEASE.md).

## Added
- Settings status panel: vault state, auto-lock, face-down, auto-import, fingerprint, key storage (hardware or software), vault size, free space.
- Opt-in face-down lock.
- Failed PIN log (times only, last 20).
- Recovery file re-check reminder every 90 days.
- Save recovery phrase to a file (setup screen).
- Opt-in auto-import of new gallery photos and videos while the vault is open (asks for media read permission). After each import you can delete the verified originals with one tap; Android confirms.
- Import existing: copies every gallery photo and video already on the phone, oldest first, in batches. Originals stay in the gallery.
- Password-protected zip backup (AES-256). Export, copy to another phone, restore with the same password.
- Daily purge of expired Recently Deleted items.

## Changed
- Original files are offered for deletion only when their vault copy is complete and decrypts.
- A batch that partly fails reports the failed count instead of "all added".
- Setup clears its PIN copy after use.
- Device checks no longer uninstall the app.

## Known limits
- Not verified on a physical phone yet beyond install and first use.
- Intruder photo capture is not built (ADR-009).
- Auto-import and import existing run only while the vault is unlocked.
- Zip backup: the zip holds the encrypted backup, not plain photos. Only the app restores it.
