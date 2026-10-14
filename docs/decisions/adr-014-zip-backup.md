# ADR-014: Password-protected zip backup for moving to another phone

## Status
Accepted (2026-10-14). Device check pending. Adds one dependency (zip4j 2.11.5).

## Context
The user wants to move the whole vault to a new phone as one file, opened with a password they choose. ADR-007 already provides an encrypted backup (`.evbk`, passphrase-derived keys, format v2). It is not a zip, so other tools cannot open it, and it needs the app to read it.

## Decision
- **Container:** a zip whose only entry is the existing `.evbk` (`vault-backup.evbk`). Data format and restore code are unchanged.
- **Password:** one password, used for both the zip and the `.evbk` passphrase. The dialog asks for it twice at export and requires `BackupCrypto.MIN_PASSPHRASE_LENGTH` characters.
- **Zip encryption:** AES-256 via zip4j (`ZipParameters`, `EncryptionMethod.AES`, `KEY_STRENGTH_256`). Entry stored uncompressed (already encrypted).
- **Restore:** the zip is copied to the cache, the entry is extracted with the password, then the normal restore runs. Cache is wiped in `finally`. A wrong password is reported without detail.
- **Where:** Settings → Backup: "Export zip backup" and "Restore zip backup". The old `.evbk` rows stay.

## Alternatives considered
- **Zip of plain photos.** Easy to open on a PC, but the photos sit unencrypted inside a password the zip standard protects weakly. Rejected.
- **Own container format only (existing `.evbk`).** Already available, but no standard tools can open it. Kept as the inner format.
- **Java `ZipOutputStream` with no encryption.** Not protected. Rejected.

## Consequences
- **Zip layer is weak by standard.** The zip AES key derivation is weaker than the inner `.evbk` derivation. The inner layer keeps the photos protected if the zip password is cracked, but only as far as the `.evbk` passphrase holds. A short password weakens both layers; the dialog enforces a minimum length.
- **Only the app restores it.** Desktop zip tools open the zip and show `vault-backup.evbk`, not photos.
- **Cache use:** export and restore each need temporary space about the size of the backup (`cacheDir/zip-backup`, `cacheDir/zip-restore`). Large vaults may need several GB free.
- **Password cannot be recovered.** Same as the existing passphrase.
- **Tested:** round trip and wrong-password refusal on the JVM (`ZipBackupTest`). Export and restore on a phone are not yet verified.
