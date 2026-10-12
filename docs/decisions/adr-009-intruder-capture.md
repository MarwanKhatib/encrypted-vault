# ADR-009: Intruder capture on failed unlock

## Status
Proposed

## Date
2026-10-12

## Context
When someone guesses the PIN wrong, the app counts the failure and starts a lockout after five tries (`UnlockAttempts`, ADR-004). The owner has no way to see who tried. A common feature in vault apps is to take a photo with the front camera on a wrong PIN and store it inside the vault for the owner to review.

This needs the camera, which the app does not use today. It also creates images of people who did not consent, so the design has to keep those images as protected as the rest of the vault.

## Decision (proposed)
- **Off by default.** Settings → Security → "Capture intruders" is opt-in. Enabling it asks for the CAMERA permission at that moment, not at install.
- On a wrong PIN with the setting on, the app takes one still frame from the front camera, using a capture session that runs only for that unlock attempt. No preview is shown and nothing is saved to the gallery.
- The frame is encrypted with the vault master key and stored as a hidden item in an "Intruder" album. It is not written to `cache/` or the public gallery.
- Each entry records the time, and the number of failures so far. It never records location.
- The album is in Utilities and is not included in backups unless the user chooses that album. (Backups include all items by default; this needs a decision, see below.)
- Duress PIN attempts (ADR-003) do not capture, because a duress unlock must look like a normal unlock.
- If the camera is missing, busy or denied, the failure is still counted and nothing else changes.

## Alternatives Considered

### Capture on every failed attempt, not only after the lockout threshold
- Pros: more evidence.
- Cons: more images of more people, and a noisy album. Capturing after the first failure also fires on the owner's own typos.
- Deferred. Start from the fifth failure, when the lockout begins.

### Store the frame in the gallery
- Pros: visible without opening the app.
- Cons: leaves the vault and is visible to any app with gallery access.
- Rejected.

## Consequences
- Adds the CAMERA permission. ADR-006's "no INTERNET permission" rule still holds; the camera is not an exfiltration channel by itself.
- The capture runs while the lock screen is up, so it needs a short session that releases the camera immediately after one frame.
- Must be tested on a real phone. Emulator cameras do not show the real timing or the permission flow.
- Open question: include the Intruder album in backups by default? Leaning no, so a restore does not bring back captures of other people.
- Legal: in some places recording people without consent is restricted. The setting must say that the owner is responsible for local rules.
