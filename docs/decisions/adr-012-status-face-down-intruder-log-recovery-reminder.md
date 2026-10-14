# ADR-012: Settings status panel, face-down lock, intruder log, recovery reminder

## Status
Accepted (2026-10-14). Intruder photo capture stays proposed (ADR-009). Device checks pending.

## Context
Competitor vaults and the threat review (DESIGN_v5) raise the same points: users cannot see what protects them, an unattended phone should lock by itself, failed PINs should be visible, and a saved recovery file goes stale.

## Decision
- **Status panel (Settings).** Shows vault state, idle-lock setting, face-down setting, fingerprint state, key storage (hardware or software), and vault size with free space. Key storage comes from `KeyHierarchy.wrapKeyInSecureHardware()` (`KeyInfo.isInsideSecureHardware`). "In the Keystore" is not reported as hardware.
- **Face-down lock (opt-in, off by default).** Accelerometer z below `FaceDown.THRESHOLD` (-6.5 m/s²) locks the vault. Listens only while the app is open. No background service.
- **Intruder log (times only).** `UnlockAttempts` keeps the last 20 wrong-PIN times in preferences. Settings lists them. No photo, no PIN, no location.
- **Duress UX explained in Settings.** The duress PIN opens an empty vault; the PIN screen does not change.
- **Calculator disguise labelled as weak.** The Settings text says it stops casual glances only.
- **Recovery file reminder.** Saving the phrase file at setup records a confirmation time. Settings asks for a re-check every 90 days (`RecoveryReminder`). The app never stores the phrase itself.
- **Clear PIN arrays after setup.** The setup path zeroes its `CharArray` copy in a `finally`. Compose `String` state cannot be zeroed; that limit is accepted.

## Alternatives considered
- **Intruder photo now.** Needs Camera2 and a way to store the image encrypted while the vault is locked (no session key). Not designed yet, so deferred.
- **Background face-down detection.** Would need a foreground service and sensor battery cost. Rejected: ADR-010 keeps background work off.
- **Store the recovery phrase to show again later.** Rejected: contradicts "shown once, never stored" (ADR-004 and setup copy).

## Consequences
- The intruder log is plain preferences: timestamps are visible to anyone with root or a device backup of prefs. `allowBackup=false` keeps it out of cloud backup. Accepted: it holds no secret.
- Face-down lock uses the accelerometer while the app is in the foreground. Small battery cost.
- Key storage line can read "software only" on emulators and some devices. That is a fact about the device, not a bug.
- Device behaviour (sensor threshold, status text) is unverified until a phone run.
