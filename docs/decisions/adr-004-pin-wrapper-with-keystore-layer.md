# ADR-004: Wrap the master key with the PIN and a device-bound Keystore layer; remove the unauthenticated wrapper

## Status
Accepted. Supersedes the PIN-only wrapper (format v1).

## Date
2026-10-09

## Context
Review of `KeyHierarchy` found two weaknesses:

1. The PIN wrapper was `AES-GCM(PBKDF2(PIN), master)` stored in `shared_prefs`. Anyone who copied that file (forensic image, a future backup bug) could brute-force a 6-12 digit PIN offline with no device involved.
2. `wrapAll` also stored `AES-GCM(KeystoreKey, master)` under `wrapped_ks`. That Keystore key needed no user authentication and **nothing ever read the value**, so it added a way for any code running as the app to recover the master key without the PIN.

## Decision
Format v2: `wrapped_pin = Keystore-AES-GCM( PIN-AES-GCM( master ) )`.

- Inner layer: key = PBKDF2-HMAC-SHA256(PIN, salt, 600,000 iterations). Salt and iteration count are stored (`kdf_iter`) so the cost can be raised later.
- Outer layer: AES-256-GCM key `vault_wrap_key` in the Android Keystore (StrongBox when available, TEE otherwise), non-exportable.
- `wrapped_ks` is deleted. `pin_len` is stored for auto-submit.
- Vaults in format v1 are upgraded transparently on their next successful PIN unlock (`unlockWithPin`), covered by `legacyVaultIsUpgradedToTheKeystoreLayerOnFirstUnlock`.

## Alternatives Considered

### Require user authentication on the outer Keystore key
- Pros: ties unlock to the device credential.
- Cons: the app is meant to be unlockable by its own PIN independent of the device lock; also complicates the duress path (which must work without extra prompts).
- Rejected for now.

### Argon2id instead of PBKDF2
- Pros: memory-hard.
- Cons: needs a native or third-party library on Android; PBKDF2 is in the platform provider, so the attack surface stays small.
- Deferred; the stored `kdf_iter` field leaves room for a new `wrap_v`.

## Consequences
- **What it protects against:** a copy of the app's files (without that phone's Keystore) can no longer be attacked offline; each guess must run on the original device.
- **What it does not:** an attacker who can execute code as the app on the unlocked, original device can still call the Keystore for the outer layer and then attack the inner PBKDF2 layer. That attacker is bounded by PBKDF2 cost and the persistent lockout, not by the Keystore.
- Unlock now costs about one PBKDF2 run at 600k iterations (roughly 2x the old cost); the UI shows a "checking" state.
- A factory reset or Keystore loss makes the PIN wrapper unusable by design; the recovery phrase re-derives the master key.
