# ADR-003: Duress PIN performs a crypto-erase, then opens a fresh empty vault

## Status
Accepted

## Date
2026-10-09

## Context
Users under coercion need a PIN that makes the vault's contents disappear without an obvious "wiped" screen. Requirements: irreversible, fast, survives the process being killed mid-wipe, and gives no signal to an observer.

Physical overwrite of flash storage is not a reliable erase on modern phones (wear levelling and journaling keep old blocks), so deleting or overwriting files cannot be the guarantee.

## Decision
Entering the duress PIN on the lock screen runs `PanicWipe.execute`:

1. **Crypto-erase first**: delete the PIN wrapper, the duress verifier and both Keystore keys. From this moment all ciphertext is permanently unreadable, even if the process dies.
2. Cancel WorkManager jobs so nothing re-creates rows or files.
3. Close and delete `vault.db`, delete `files/vault/*` and the cache (which may hold staged plaintext).
4. Create a brand-new vault under the entered PIN and unlock it, so the user lands in a normal, empty library.

The duress PIN is stored only as a salted PBKDF2 verifier (never a key), must differ from the real PIN, must have the same length (so auto-submit triggers), and cannot be weak. `changePin` refuses a PIN equal to the duress PIN or one of a different length. The new vault's recovery phrase is discarded on purpose.

## Alternatives Considered

### Overwrite files with random data, then delete
- Rejected: unreliable on flash, slow for 500 MB files, and does nothing for copies the OS or a backup already holds.

### Show an error or "vault reset" screen after wiping
- Rejected: advertises that data was destroyed, which can escalate coercion.

### Wipe after N wrong PINs
- Not part of this ADR: a different trade-off (accidental data loss). Candidate for an opt-in setting later.

## Consequences
- **Irreversible by design.** The setup dialog says so in red; the recovery phrase cannot restore wiped data.
- Wrong-PIN and duress paths both cost one PBKDF2 run, so timing does not reveal which was typed.
- A rooted observer can still see that `duress_*` preference keys exist before the wipe; hiding even that would need a decoy entry on every vault (not done).
- Covered by `PanicWipeTest` (instrumented), `PanicWipeFilesTest`, `PinPolicyTest`, `KeyHierarchyTest`.
