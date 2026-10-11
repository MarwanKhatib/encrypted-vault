# ADR-007: Encrypted export and restore with a user passphrase

## Status
Accepted

## Date
2026-10-11

## Context
The vault cannot leave the device today. `allowBackup=false` (ADR-006) keeps Android Auto Backup and `adb backup` from copying it, and Gap 8 of `DESIGN_v5.md` records that a new phone or a reinstall starts with an empty vault. Losing a phone therefore loses every photo.

The per-item master key lives in the Android Keystore and inside the PIN wrapper, so the ciphertext in `vault/` is useless on another device. A backup has to carry the content in a form that a fresh vault can read, without the source device's keys.

## Decision
A user-initiated export writes one file to a location the user picks through the Storage Access Framework. Restore reads that file back into the unlocked vault.

- **Format `EVBK`, version 1.** Header: magic `EVBK`, version byte, 16-byte PBKDF2 salt, 12-byte base nonce, iteration count (u32).
- **Key.** PBKDF2-HMAC-SHA256 with 600,000 iterations over the passphrase, giving a 256-bit AES key. This matches the PIN wrapper's cost (`KeyHierarchy`) so there is one KDF to reason about.
- **Content encryption.** The body is split into 1 MiB chunks. Each chunk is AES-256-GCM encrypted with nonce = base nonce XOR chunk index (8-byte big-endian in the low bytes). The AAD is the chunk index plus a final-chunk flag, so reordering, dropping, or truncating chunks fails authentication. Chunks are streamed, so memory stays bounded for large videos.
- **Plaintext inside the stream.** A manifest-free record sequence: for each item, its clear columns (mime, timestamps, flags, size, dimensions, duration), its metadata (name, folder, caption, encoded with `PhotoMetadataCodec`), its album titles, and its content bytes. Content is decrypted from the vault on the fly and never written unencrypted to disk.
- **Passphrase.** At least 12 characters. The app cannot recover a lost passphrase; the UI says so before export.
- **Restore.** Content goes through the existing `ImportWorker` (hash dedup, idempotent, resumable). Favourites, hidden flags, captions, and album membership are applied afterwards. A backup from a newer format version is refused.

## Alternatives Considered

### Copy the raw `vault/` directory
- Pros: simple, no new format.
- Cons: the ciphertext is bound to this device's Keystore and PIN wrapper, so it cannot be restored elsewhere without the recovery phrase, and it still leaks file layout.
- Rejected.

### Export under the recovery phrase
- Pros: no second secret.
- Cons: ties the backup to the Gap 3 recovery phrase, which many users never store. A separate passphrase is easier to reason about.
- Rejected for now; the format leaves room to add it as a version-2 key source.

### Argon2id instead of PBKDF2
- Pros: stronger against GPU attacks.
- Cons: needs a new native or third-party dependency. PBKDF2 at 600k is already the project standard.
- Deferred. Revisit with the next dependency review.

### Tink streaming AEAD for the body
- Pros: audited construction.
- Cons: adds a dependency for a format that is small enough to review in one file.
- Rejected; the chunked GCM construction is covered by unit tests for tamper, reorder and truncation.

## Consequences
- The backup file is as sensitive as the vault itself. The export screen tells the user to store it somewhere they trust. The app never sends it anywhere; it leaves the device only if the user copies it.
- No INTERNET permission is needed; SAF handles file access.
- Restore into a non-empty vault merges by hash, so re-running a restore is safe.
- Changing the format requires a new version byte. Readers must refuse unknown versions instead of guessing.
