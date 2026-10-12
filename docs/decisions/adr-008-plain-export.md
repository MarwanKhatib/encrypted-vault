# ADR-008: Optional plain (unencrypted) export as an escape hatch

## Status
Proposed

## Date
2026-10-12

## Context
ADR-007 gives an encrypted backup that only this app can read. Some users want their photos as ordinary files (JPEG, MP4, folders) so they can leave the app, or read them in another tool, without a passphrase. Today the only way out is to restore the encrypted backup into this app, so the vault is a lock-in.

A plain export puts unencrypted media on storage that other apps can read. That contradicts the purpose of the vault, so it needs a narrow scope and a clear warning.

## Decision (proposed)
- Add **Settings → Export plain copies**, behind a confirmation dialog that states the files will be unencrypted and readable by any app with storage access.
- The user picks a folder through the Storage Access Framework. Each item is written with its original name, and albums become sub-folders. Captions and flags go into a `manifest.csv` next to the files.
- Content is decrypted one item at a time and written straight to the chosen folder, with no plaintext left in `cache/`. Partial exports are removed if the user cancels.
- Export is refused while the vault is locked and needs a fresh unlock (the same gate as ADR-007 export).
- Nothing is written automatically, and the app never sends the files anywhere.

## Alternatives Considered

### Zip archive with a password
- Pros: one file, one password.
- Cons: zip encryption is weak (ZipCrypto) or needs a dependency (AES zip). It is also just ADR-007 with worse tooling. Use the ADR-007 backup for that.
- Rejected.

### Export only on a debug or hidden build
- Pros: no risk to normal users.
- Cons: users who need it cannot reach it, and a hidden feature is still a feature to audit.
- Rejected.

## Consequences
- Adds a path where plaintext leaves the encrypted store. Every test for "plaintext never reaches disk" needs an exception for this path, and the exception has to be narrow.
- The warning is the main control. Review the wording before release.
- The export writes into shared storage, so it must not reuse `cache/` or `files/vault/`.
- Open question: whether to strip GPS from exported files by default (the share path already has that option).
