# ADR-010: Optional idle auto-lock (off by default)

## Status
Accepted. Implemented with the default Off; not yet checked on a real phone (see Consequences).

## Date
2026-10-12

## Context
The app locks as soon as it leaves the screen (commit `75255b4`, `MainActivity.onStop`). That was chosen on purpose, and the earlier timer-based auto-lock was removed. Some users, though, leave the vault open on a tablet or a shared device and want it to lock after a set idle time. The earlier removal did not rule this out for everyone; it only removed the default.

Any timer must run on the monotonic clock, like `UnlockAttempts`, so changing the wall clock cannot extend the session.

## Decision (proposed)
- Settings → Privacy → "Lock after idle": Off (default), 1, 5 or 15 minutes. Off keeps today's behaviour exactly.
- The idle timer starts when the app is in the foreground with no touch, key or scroll input. Any of those resets it. It is paused only while the screen is off or the app is in the background, where the instant lock already applies.
- Uses `SystemClock.elapsedRealtime()`. A check runs on each resume and once per minute while the app is visible. There is no background service and no alarm.
- Locking runs the same path as Lock now, including `sweepTemporaryPlaintext`.
- Captured in `AppSettings` (preferences, not the vault database), so it is readable before unlock. It holds no secret.

## Alternatives Considered

### Make auto-lock the default
- Pros: safer on shared devices.
- Cons: reverses the instant-lock decision that the current docs and README describe. Users who expect the default behaviour get a lock they did not ask for.
- Rejected for now. Revisit if testing shows the instant lock is too strict in practice.

### Use a foreground service to keep the timer alive
- Cons: a visible service and battery cost, for a feature most users leave off.
- Rejected.

## Consequences
- Default stays Off, as decided. Settings → Privacy → "Lock after idle" offers Off, 1, 5 and 15 minutes.
- Implemented: `security/IdleLock` (pure, injected clock), `MainActivity` (touch via `onUserInteraction`, once-a-minute check while resumed), `AppSettings.idleLockMinutes`. Unit tests in `IdleLockTest` (4 cases).
- Still open: device check that touch resets the timer and that the lock fires with the screen on. Needs a real phone or the emulator with input injected.
- The README's "locks as soon as the app leaves the screen" line stays true for the default and needs a note for the opt-in.
