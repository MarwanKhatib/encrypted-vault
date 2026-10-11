# ADR-001: Keep the app native Kotlin + Jetpack Compose (not Flutter)

## Status
Accepted

## Date
2026-10-09

## Context
The app's business logic is almost entirely Android platform security and media plumbing:

- Hardware-backed keys: Android Keystore, StrongBox, biometric-gated `CryptoObject`
- `FLAG_SECURE`, multi-window lock, `filterTouchesWhenObscured`, launcher activity aliases (icon disguise)
- WorkManager for resumable imports, MediaStore (including `createDeleteRequest`) for import/restore
- Media3/ExoPlayer reading from an encrypted `DataSource`, Room, an R8-obfuscated release build

The question was whether Flutter would be a better base for the UI/UX and business logic.

## Decision
Stay on Kotlin with Jetpack Compose (Material 3). UI libraries: Compose Material 3, Material Icons Extended, Media3 (player + UI).
Security libraries: Android Keystore, AndroidX Biometric, Tink (streaming AEAD). Data: Room, WorkManager.

## Alternatives Considered

### Flutter
- Pros: one UI toolkit, very good animation and widget ecosystem, hot reload.
- Cons: every item in the Context list above would become a platform channel or a third-party plugin. Each plugin is extra attack surface and an extra thing to audit, and several (secure-storage plugins) wrap the same Keystore calls we already make directly. PIN digits and decrypted bytes would also cross the Dart/native boundary, so the Dart heap would hold secrets we cannot zeroise. Dart's `--obfuscate` renames symbols but ships an AOT snapshot that is not comparable to R8 shrinking plus optimisation. The cross-platform benefit is nil: the features (Keystore, MediaStore, launcher aliases) have no iOS equivalent.
- Rejected: the UI would be nicer to write but the part that matters (security) gets weaker and bigger.

### React Native
- Same objections as Flutter, plus a JavaScript bridge and a larger dependency tree.
- Rejected.

### Kotlin Multiplatform
- Pros: could share the crypto format and business rules with a future iOS app.
- Cons: nothing needs it today.
- Deferred: revisit only if an iOS client is ever planned. The file format (ADR-002) is already specified byte-for-byte so a second client can read it.

## Consequences
- All security-critical code is first-party Kotlin calling platform APIs directly, with no plugin layer.
- UI polish comes from Compose Material 3 motion (`AnimatedContent`, springs) and the extended icon set, not a second UI engine.
- A future iOS app would be a separate codebase; keep `docs/decisions/adr-002` and `KeyHierarchy` documentation current so it can interoperate.
