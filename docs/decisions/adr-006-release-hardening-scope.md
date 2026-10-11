# ADR-006: Release hardening is defence in depth; the keys are the protection

## Status
Accepted

## Date
2026-10-09

## Context
The goal was to make the APK hard to reverse and the photos impossible to decrypt "illegitimately". Obfuscation cannot do the second part: the decompiled code of a correct design reveals nothing that breaks it, because security rests on secrets (PIN, Keystore keys, recovery phrase), not on the algorithm being unknown.

## Decision
Layered, with each layer's limit stated:

| Layer | What it does | What it does not do |
|---|---|---|
| R8 shrink + optimise + obfuscate (`proguard-rules.pro`, `-repackageclasses`, no source names) | Removes dead code (APK 68 MB debug to about 5 MB release), renames classes/methods so decompiled output is hard to follow | Does not hide logic from a patient analyst; strings such as preference keys stay readable |
| `Log.*` calls removed in release | Nothing about internal state reaches logcat | n/a |
| `Integrity.check` at start (hardened builds only) | Refuses to run if the APK was repackaged as `debuggable` or a debugger is attached | Cannot stop a rooted attacker with a hooking framework, who controls the check itself |
| `allowBackup=false`, no INTERNET permission, `FLAG_SECURE` | No cloud/adb copy, no exfiltration channel, no screenshots or recents preview | n/a |
| Keys: Keystore/StrongBox outer layer, PBKDF2 600k, lockout, duress PIN | The actual barrier to decryption | Weak PINs are still guessable by an attacker running on the unlocked device; PIN policy rejects trivial ones |

Release builds are signed with the debug key for local testing only (`build.gradle.kts` says so); distribution needs a real release keystore.

## Alternatives Considered

### Commercial packers / native protection (DexGuard, native `.so` for core logic)
- Pros: raises reverse-engineering cost further.
- Cons: cost, build complexity, and still bypassable by hooking; moving crypto into native code does not change what the keys protect.
- Deferred.

### Play Integrity / SafetyNet attestation
- Rejected: requires network and Google services, which this app deliberately avoids.

### Root detection
- Rejected (also design Gap 7): trivially bypassed by root-hiding tools, and StrongBox already protects key material on rooted devices.

## Consequences
- Keep `app/build/outputs/mapping/release/mapping.txt` for each release you distribute; without it crash traces cannot be decoded.
- Do not describe the app as "unreadable if reverse engineered"; describe it as "encrypted with keys that never leave the device and are not derivable from the code".
