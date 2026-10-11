# Encrypted Photo Vault — v5: Senior Design Review & Final Completion Pass

**Acting as:** staff/principal Android engineer doing a pre-build design review
**Method:** walk every user scenario end-to-end, including the ugly ones (crashes, forgotten PINs, OS killing the process mid-task, phone handed to someone else), and flag anything v1–v4 didn't answer.

> Decision taken at scaffold time: **Gap 3 = Option B** (local recovery phrase). See README.md.

---

## SCORECARD (honest self-assessment of v1-v4)

| Area | Grade | Reasoning |
|---|---|---|
| Encryption primitives (AES-256-GCM, StrongBox) | A | Solid, correct, hardware-appropriate |
| Crash-safe file writes (atomic rename) | A | Correctly designed, order-of-operations is right |
| Dedup strategy | A- | Good; missing what happens when a *decrypted* duplicate is found later (see Gap 12) |
| Bulk performance (streaming, parallelism, thumbnails) | A- | Strong; missing video thumbnail generation specifics (Gap 9) |
| Permissions (minimal set) | B+ | Good minimalism; missing Direct Boot & backup-exclusion permissions (Gap 1, 2) |
| **Key recovery / "what if I forget my PIN"** | **F — never addressed** | Critical gap, see Gap 3 |
| **App backup/restore (ADB, Android Auto Backup)** | **F — never addressed** | Could leak vault to adb backup, see Gap 1 |
| **Process death mid-operation** | **D — partially covered** | Crash-safety covered for files, not covered for in-flight ViewModel/UI state |
| **Root/tamper detection** | **Not addressed** | Reasonable to skip for personal-use app, but must be a stated decision, not an oversight — see Gap 7 |
| **Overlay / tapjacking attacks on lock screen** | **Not addressed** | Easy one-line fix, no excuse to skip — Gap 6 |
| **Video & HEIC/RAW format support** | **Partially addressed** | Thumbnail strategy assumed static images only — Gap 9 |
| **EXIF/metadata leakage** | **Not addressed** | GPS location in photo metadata survives naive encryption — Gap 10 |
| **Database schema migrations over time** | **Not addressed** | You will change the schema; no migration plan breaks the app on update — Gap 11 |
| **Testing plan completeness** | **C** | v1 listed test *categories* but no actual scenario checklist — Gap 13 |
| **Accessibility** | **Not addressed** | Minor for personal app but cheap to get right — Gap 14 |
| **Multi-window / split-screen privacy** | **Not addressed** | Gap 6 (paired with overlay fix) |

**Overall: the crypto and performance engineering is genuinely strong (A-tier). The gaps are almost all in "what happens at the edges" — lifecycle, recovery, and real-world OS behavior — which is exactly where personal security apps actually fail in practice.** Below, each gap gets a concrete fix.

---

## GAP 1 — Android's Auto Backup could silently copy your vault to Google's cloud

**Scenario:** You set up a new Pixel, or your current one does its nightly backup. By default, Android's **Auto Backup for Apps** backs up app-private storage (including Room databases and your `files/vault/` directory) to the user's Google Drive account — *unless the app explicitly opts out*. This would mean your "offline-only, no cloud" vault silently ends up in Google's cloud anyway, undermining the entire premise.

**Fix — explicit opt-out in the manifest (one line, must not be skipped):**
```xml
<application
    android:allowBackup="false"
    android:fullBackupContent="false"
    ...>
```
`android:allowBackup="false"` was already in the v2 manifest draft — confirming it's correct, but it needs to be called out explicitly as a *security-critical* line, not just a boilerplate default, so nobody "cleans it up" later thinking it's unused.

**Also fix — block ADB backup specifically:**
Even with `allowBackup="false"`, verify on your actual Pixel with:
```
adb backup -f test.ab com.example.encryptedvault
```
This should produce an empty/rejected backup. Test this once during development — it's a 2-minute check that closes a real hole.

---

## GAP 2 — Direct Boot: what happens if the app is "used" before the phone is first unlocked after reboot

**Scenario:** Phone reboots (update, battery died, manual restart). Android has a "Direct Boot" mode where some apps can run in a limited state *before* the user enters their device PIN/pattern for the first time post-boot. Your app doesn't need Direct Boot support (no legitimate reason for a vault to run before device unlock), but you need to **actively ensure it doesn't accidentally register for it**.

**Fix:** Do not add `android:directBootAware="true"` anywhere, and store all vault data in **credential-encrypted storage** (the Android default for app data, `Context` not `createDeviceProtectedStorageContext()`). This means: until the *device's own* lock screen is unlocked once after boot, your app's files are inaccessible at the OS level too — a second layer of protection for free, just by not opting into the mode that would bypass it.

---

## GAP 3 — "What if I forget my PIN and biometric fails" — the most important missing piece

**Scenario:** You change phones, your face changes (injury, surgery), fingerprint sensor gets damp/dirty repeatedly, or you simply forget the PIN. With the StrongBox-backed design in v3, the master key **cannot be extracted or reset by you, Google, or anyone** — that's the security feature working correctly. But right now, the plan has **no answer for what happens to your photos in that scenario**: they are permanently, unrecoverably lost. No backend, no cloud, no recovery email (by the explicit offline-only requirement) — so this is *correct* on security grounds, but it was never surfaced as a decision to knowingly accept.

**This needs an explicit decision, not a silent default. Two real options:**

**Option A — Zero recovery (maximum security, matches "offline, no external anything" literally):**
State it plainly in the app's first-run screen: *"If you forget your PIN and biometric auth is unavailable, your encrypted photos are permanently unrecoverable. There is no backup, no recovery option, by design."* Add a mandatory acknowledgment checkbox on first setup. This is the honest version of what was asked for so far.

**Option B — Local-only recovery phrase (still 100% offline, no external API, slightly less pure):**
Generate a 12-24 word BIP39-style recovery phrase (same mechanism crypto wallets use, fully offline, open-source library `novacrypto/BIP39` or roll your own from a static wordlist — no network call involved) at vault setup. This phrase, when combined with a KDF, can re-derive the master key independent of biometric/PIN. **You** write it down on paper and store it somewhere physical (safe, drawer) — the app never stores it, never transmits it. This gives you a recovery path that doesn't compromise "no external API/no cloud" at all, since the phrase never leaves your control.

**Recommendation:** Option B. It costs one extra setup screen and is still 100% offline/FOSS, but the difference between "lose your phone and permanently lose every encrypted photo" vs. "lose your phone, but you wrote the recovery phrase down" is enormous in practice. Decide this before writing any crypto code, because it changes the key-derivation hierarchy (§3.1 below).

### 3.1 Updated key hierarchy to support Option B
```
┌─────────────────────────────────────────────┐
│  Recovery Phrase (BIP39, 24 words)          │
│  - Generated once at setup                  │
│  - Shown ONCE on screen, never stored       │
│  - User writes it down physically           │
└─────────────────┬───────────────────────────┘
                  ↓ (PBKDF2/Argon2id derivation)
┌─────────────────────────────────────────────┐
│  Master Vault Key (AES-256)                 │
│  - This is the ACTUAL key that encrypts     │
│    photos                                   │
└─────────────────┬───────────────────────────┘
                  ↓ (wrapped/encrypted by)
┌───────────────────────┬─────────────────────┐
│  PIN-derived wrapper  │  Biometric-gated    │
│  key (day-to-day use) │  StrongBox key      │
│                       │  (day-to-day use,   │
│                       │  faster UX)         │
└───────────────────────┴─────────────────────┘
```
Day-to-day unlock still uses fast biometric/PIN (StrongBox-gated, as designed in v3). The recovery phrase is a *parallel* path to the same master key, used only in the "I'm locked out" scenario — it doesn't weaken daily security at all, since both paths require possessing a secret (either the enrolled biometric/device, or the physical paper phrase).

---

## GAP 4 — Process death mid-operation (not just file-write crashes, but Android killing your app)

**Scenario:** You're encrypting 200 photos in the background. You switch to another app to do something else. Android's memory manager decides it needs RAM and kills your app process entirely (completely normal, happens constantly on Android, not a crash/bug). v4 covered *file-level* crash safety well (atomic rename), but didn't cover what happens to the **batch job's progress tracking** when the whole process dies mid-batch.

**Fix:** Your `WorkManager` job (already in the stack) must be **idempotent and resumable** — not just "runs in background," but specifically designed so that if Android kills and restarts it, it picks up from where it left off rather than restarting from zero or double-processing:
```kotlin
class EncryptionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val pendingPhotos = photoDao.getUnencryptedQueue() // re-query, don't trust in-memory list
        pendingPhotos.forEach { photo ->
            if (photoDao.findByHash(photo.hash) != null) return@forEach // already done, skip (ties into v4 dedup)
            encryptFileSafely(photo.sourceFile, vaultDir, photo.hash)
        }
        return Result.success()
    }
}
```
Because your dedup check (v4, §1.1) is hash-based and checked fresh from the database every time — not from an in-memory "list of files left to do" — a killed-and-restarted worker naturally resumes correctly for free. Worth calling out explicitly as a design principle: **every batch operation should be safely re-runnable from scratch with no bad side effects.**

---

## GAP 5 — Encrypted thumbnail cache can go stale or get out of sync with the main file

**Scenario:** v4 added per-photo encrypted thumbnails for scroll performance. What if thumbnail generation succeeds but the main-file atomic rename (v4 §2.1) fails, or vice versa? You could end up with a thumbnail but no main file, or a main file with no thumbnail (grid shows a broken placeholder).

**Fix:** Generate and atomically commit the thumbnail as part of the **same transaction boundary** as the main file — specifically, don't mark the DB row as "complete" until *both* `.enc` and `.thumb.enc` exist:
```kotlin
// Only after BOTH atomic renames succeed:
photoDao.insertOrUpdate(photo.copy(status = "complete"))
// If either file write fails, status stays "pending" and Gap 4's
// resumable worker will retry cleanly on next run.
```
Grid UI should only query/display rows where `status == "complete"` — a half-finished entry simply doesn't show up yet, rather than showing a broken image.

---

## GAP 6 — Tapjacking / screen overlay attacks on the lock screen, and multi-window leakage

**Scenario (overlay attack):** A malicious app installed on the same phone (sideloaded, or one that abuses the "draw over other apps" permission) could draw an invisible overlay on top of your PIN pad to capture taps, or a fake lock screen to phish your PIN.

**Fix:**
```kotlin
// On the PIN entry view specifically:
pinPadView.filterTouchesWhenObscured = true
```
This is a one-line, built-in Android `View` property that causes the system to **silently discard touch events** if another window is detected drawing over your view at the moment of the tap — exactly the attack this prevents, at zero cost.

**Scenario (multi-window/split-screen leakage):** Pixel supports split-screen multitasking. If your vault is open in one half of the screen showing a decrypted photo, and the user switches apps in the other half, your content remains visible the whole time — unlike the single-app "recent apps" case that `FLAG_SECURE` (v3, Gap 5) already handles.

**Fix:** Detect split-screen/multi-window mode and force-lock immediately if entered while a photo is open:
```kotlin
override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean) {
    super.onMultiWindowModeChanged(isInMultiWindowMode)
    if (isInMultiWindowMode) sessionManager.forceLock()
}
```

---

## GAP 7 — Root detection: explicitly scoped out, not forgotten

**Scenario:** A rooted phone can bypass some app sandboxing, potentially read other apps' process memory, or tamper with the APK.

**Decision, stated explicitly rather than silently skipped:** For a **personal-use, non-Play-Store, offline app** that you control end-to-end, root detection adds real complexity (and most root-detection libraries are either paid, abandoned, or trivially bypassed by the same rooting tools they're trying to detect) for a threat model that mostly matters for apps distributed to *untrusted* users at scale (banking apps worried about fraud farms). Since StrongBox already protects the actual key material even on a rooted device (the key never leaves the physically separate Titan chip, root or not), the marginal benefit here is low.

**Recommendation:** Explicitly skip root detection for v1, document why (as above), revisit only if you ever root your own Pixel for other reasons — at which point you'd want a one-time warning dialog ("device is rooted, StrongBox may be unavailable or weakened") rather than a blocking check.

---

## GAP 8 — What happens on app uninstall/reinstall, or Pixel-to-Pixel migration

**Scenario:** You get a new Pixel and use Google's "Switch to new phone" / Pixel-to-Pixel data transfer. Or you uninstall and reinstall the app during development/testing.

**Fix — two things must be true simultaneously:**
1. Because of Gap 1 (`allowBackup="false"`), the vault's encrypted files and database **will not** transfer automatically via Google's backup/restore or Pixel-to-Pixel transfer tools. This is *correct* (StrongBox keys are hardware-bound to the specific Titan chip and literally cannot move to a new phone's chip anyway — so even if the files transferred, the key wouldn't).
2. This must be **documented behavior you expect**, not a surprise: moving to a new Pixel means starting a fresh vault. If you used Option B (recovery phrase, Gap 3), the *photos* still can't transfer (they're StrongBox-bound), but at minimum you're not catastrophically confused about why "restore" didn't bring your vault back.

**Practical recommendation:** Add an explicit **local export feature** — not cloud, not automatic, but a manual "Export encrypted vault to a folder" action that copies the `.enc` files + database to a user-chosen location (e.g., a USB-C drive, SD card if the Pixel model supports one, or a folder you then manually copy via USB cable to a computer). This is the FOSS/offline-compliant way to let you "back yourself up" without the app ever doing it automatically or via network.

**Status (2026-10-11): implemented.** Settings → Backup writes every item, with albums and captions, to one passphrase-encrypted file, and restores it on any device. The file format and the reason for not copying raw `.enc` files are in `docs/decisions/adr-007-encrypted-export-and-restore.md`. Code: `backup/VaultBackup.kt`, `backup/BackupRecords.kt`, `crypto/BackupCrypto.kt`.

---

## GAP 9 — Video and HEIC/RAW format support was assumed-away, not designed

**Scenario:** Pixel cameras default to capturing in HEIC (newer Pixels) and support Ultra HDR JPEGs; you likely also have videos you'd want encrypted. v4's thumbnail strategy (`downsampleBitmap`) implicitly assumed a static image decodable by `BitmapFactory` — this breaks for video files, and HEIC needs Android's `ImageDecoder` (API 28+) rather than the legacy `BitmapFactory`.

**Fix:**
```kotlin
fun generateThumbnail(file: File, mimeType: String): Bitmap {
    return when {
        mimeType.startsWith("video/") -> {
            // Use MediaMetadataRetriever for video frame extraction
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.path)
                retriever.getFrameAtTime(0) ?: fallbackThumbnail()
            }
        }
        mimeType == "image/heic" || mimeType == "image/heif" -> {
            // ImageDecoder handles HEIC natively on API 28+ (min SDK)
            val source = ImageDecoder.createSource(file)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setTargetSampleSize(calculateSampleSize(file, 300))
            }
        }
        else -> downsampleBitmap(file, maxDimension = 300) // standard JPEG/PNG path
    }
}
```
Also update `READ_MEDIA_VIDEO` (already flagged as conditional in v3) to **required, not optional**, if you want videos in scope for v1 — decide this now since it affects the permission request UX shown to the user on first launch.

---

## GAP 10 — EXIF metadata (GPS location!) survives encryption untouched — this is a real privacy leak

**Scenario:** This is the sharpest gap in the whole plan. Your photos' EXIF data includes **GPS coordinates, device model, timestamp** by default on most phones including Pixel. Encryption protects the *image bytes*, but if you ever share a photo back out (v2's share flow), the decrypted file you hand to WhatsApp/Gmail still carries that original EXIF data — meaning the "security" app could leak your home address via GPS tags on an otherwise-innocuous shared photo, with zero indication to you that it happened.

**Fix — strip or preserve EXIF deliberately, as a user choice, at share time:**
```kotlin
fun stripSensitiveExif(file: File) {
    val exif = ExifInterface(file.path)
    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null)
    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, null)
    exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, null)
    exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, null)
    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, null) // optional, your call
    exif.saveAttributes()
}
```
(`androidx.exifinterface:exifinterface` — Apache 2.0, already effectively free since it's part of AndroidX.)

**Add a toggle in the share dialog:** *"Remove location data before sharing"* (default ON). This is a cheap, high-value fix for a gap that otherwise completely undermines the privacy goal of the whole app at the exact moment — sharing — where it matters most.

---

## GAP 11 — No database migration strategy — your schema WILL change, and Room will crash without one

**Scenario:** You ship v1 with the `photos` table from v2's schema. In v1.1 you add the `sharing_log` table, or add a column. Without a migration plan, Room's default behavior on schema mismatch is either a crash (`IllegalStateException`) or `fallbackToDestructiveMigration()` — which **deletes the entire database**, losing all your metadata (though not the encrypted files themselves, which is some mercy, but you'd lose the ability to decrypt/locate anything without the metadata — effectively the same as losing everything).

**Fix:**
```kotlin
@Database(version = 2, entities = [Photo::class, SharingLog::class, ShareToken::class])
abstract class VaultDatabase : RoomDatabase()

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE sharing_log (...)")
    }
}

Room.databaseBuilder(context, VaultDatabase::class.java, "vault.db")
    .addMigrations(MIGRATION_1_2)
    // Deliberately NO .fallbackToDestructiveMigration() — force yourself
    // to write a real migration every time, since silent data loss is
    // unacceptable for this specific app
    .build()
```
**Rule going forward:** every schema change gets a real `Migration` object, never the destructive fallback, specifically *because* this is a vault app where losing metadata effectively means losing access to your own encrypted photos.

---

## GAP 12 — Dedup doesn't define what happens when you decrypt-and-reshare a photo that creates a byte-identical copy

**Scenario:** v4 nailed dedup at the *encryption* step. But: you share an encrypted photo (decrypt → temp file → share), the receiving app (say, a file manager) somehow saves a copy back into your Pictures folder (plausible with some apps), and later your Device Photos scanner finds this now-decrypted copy sitting on-device again. Hash matches an already-encrypted photo.

**Fix:** When the scanner finds a hash match against an *already-encrypted* photo, don't silently skip (as v4 specified for the simple case) — instead, offer to **securely delete the redundant plaintext copy** outright, since its existence on-device defeats the purpose of having encrypted it in the first place:
```kotlin
if (existingEncrypted != null) {
    // A plaintext copy of an already-vaulted photo exists on-device.
    // This is a security hole, not just a harmless duplicate.
    notifyUser("Found an unencrypted copy of a vaulted photo — delete it?")
    // User-confirmed secure delete, don't auto-delete without asking
}
```

---

## GAP 13 — Testing plan needs an actual scenario checklist, not just category names

v1 listed "unit tests," "integration tests," "security tests" as headers with example snippets, but a senior review needs the **specific scenarios** that must pass before you trust this with real photos:

**Must-pass scenario checklist before first real use:**
- [ ] Kill the app process (via `adb shell am kill`) mid-batch-encryption of 50+ files → relaunch → verify no data loss, no duplicate entries, no orphaned `.tmp` files
- [ ] Pull the battery / force-reboot mid-encryption → verify same as above
- [ ] Encrypt the same photo twice (tap button twice fast) → verify exactly one vault entry
- [ ] Attempt 10 wrong PINs in a row → verify lockout/backoff actually triggers
- [ ] Put phone in split-screen with vault open to a photo → verify auto-lock fires (Gap 6)
- [ ] Press recent-apps button with a photo open → verify thumbnail is blank/secured (Gap 6/v3-Gap5)
- [ ] Share a photo to a test app → verify re-encryption completes AND temp cache file is actually gone (`ls` the cache dir manually)
- [ ] Share a photo → inspect the shared file's EXIF with a separate tool → verify GPS stripped if toggle was on (Gap 10)
- [ ] Revoke photo permission mid-session via system Settings → verify app doesn't crash, re-prompts gracefully (v3 Gap 4)
- [x] `adb backup` the app → verify output is empty/rejected (Gap 1) — *2026-10-11, emulator `vault34`: output 0 bytes*
- [ ] Simulate a schema migration (bump version, add a column) → verify existing vault photos still decrypt correctly after update (Gap 11)
- [ ] Decrypt a HEIC photo and an MP4 video thumbnail → verify both render (Gap 9)
- [ ] Trigger StrongBox unavailable path on an emulator (no secure hardware) → verify graceful TEE fallback, not a crash (v3 §1)

This list is the actual acceptance bar — treat it as a checklist to run through before trusting the app with photos you can't afford to lose.

**Status by item (2026-10-11, emulator `vault34`, release APK signed with the real release key, `scripts/device-checks.sh`):**

| # | Check | Result |
|---|---|---|
| 1 | Kill mid-batch | **Pass.** Force-stop at 145 complete / 2 half-written of 150. After relaunch: 150 entries, one each, no pending rows, no leftovers. This found a real bug (WorkManager's 10 KB input limit) and it is fixed. |
| 2 | Reboot mid-encryption | **Pass.** Reboot at 145 / 2 of 150. After boot: 150 entries, one each, no pending rows. |
| 3 | Same photo twice | **Pass.** `DeviceChecksTest.encryptingTheSamePhotoTwiceMakesOneEntry`. |
| 4 | 10 wrong PINs | **Pass** at the state layer: `DeviceChecksTest.tenWrongPinsLockTheVault`. The UI countdown is not checked. |
| 5 | Split-screen auto-lock | **Not verified.** Code path: `MainActivity.onMultiWindowModeChanged` calls `lockNow()`. Could not enter split-screen with `am start`. Manual check needed. |
| 6 | Recents thumbnail blank | **Partly.** The release window carries `SECURE` (`dumpsys window`). The recents screenshot came back empty, so the blank thumbnail itself is not captured. |
| 7 | Shared copy removed from cache | **Partly.** `DeviceChecksTest.sharedPlaintextCopyIsSweptAway` checks the sweep. The share sheet flow is not driven. |
| 8 | GPS stripped from shared copy | **Partly.** `DeviceChecksTest.gpsIsStrippedFromTheSharedCopy` checks the strip function on a real JPEG with GPS. The share flow and an external EXIF tool are not used. |
| 9 | Revoke photo permission | **Not applicable.** The app holds no storage permission (photos come through the system picker). |
| 10 | `adb backup` gives nothing | **Pass.** Output 0 bytes. |
| 11 | Schema migration keeps photos decryptable | **Partly.** `DatabaseMigrationTest` (v1→v5) passes. Decryption after migration is not tested. |
| 12 | HEIC and MP4 thumbnails render | **Partly.** MP4 passes (`mp4VideoGetsADurationAndAFrame`, sample recorded with `screenrecord`). HEIC is **not run**: no HEIC sample exists on this emulator, and the SDK has no HEIF encoder. Put a real HEIC at `cacheDir/sample.heic` to run it. |
| 13 | No StrongBox → TEE fallback | **Pass by evidence.** This emulator has no StrongBox; the key setup and unlock tests pass through the fallback path. |

Re-run with `scripts/device-checks.sh unit | drill-kill | drill-reboot | recents`.

---

## GAP 14 — Accessibility (minor, but nearly free to fix now vs. painful to retrofit)

**Scenario:** Even for a personal app, content descriptions, sufficient touch-target sizing, and TalkBack compatibility cost almost nothing if designed in from the start with Compose (which has solid accessibility semantics built in) — but one specific thing needs explicit attention here: **don't let accessibility services become a data leak**. A screen reader announcing "Photo: beach_trip_gps_home_address.jpg" would defeat your EXIF-stripping effort (Gap 10) through a completely different channel.

**Fix:** Use generic content descriptions for vault items, not filenames:
```kotlin
Image(
    bitmap = thumbnail,
    contentDescription = "Encrypted photo, ${formatDate(photo.timestamp)}" // not the filename
)
```

---

## FINAL COMPLETED ARCHITECTURE SUMMARY

| Layer | What v1-v4 had | What v5 adds |
|---|---|---|
| Encryption | AES-256-GCM, StrongBox | + Recovery phrase key hierarchy (Gap 3) |
| File safety | Atomic rename, dedup by hash | + Resumable batch jobs (Gap 4), thumbnail/main-file transaction coupling (Gap 5), plaintext-copy cleanup (Gap 12) |
| Lock screen | Biometric + PIN, FLAG_SECURE | + Overlay attack protection, multi-window force-lock (Gap 6) |
| Storage | Room, app-private dir | + allowBackup=false verified via adb, Direct Boot avoidance (Gap 1, 2), migration strategy (Gap 11) |
| Sharing | Content Provider, one-time tokens | + Mandatory EXIF/GPS stripping toggle (Gap 10) |
| Format support | Implicit JPEG/PNG only | + Explicit HEIC + video thumbnail paths (Gap 9) |
| Recovery | None (silent gap) | + Explicit Option A/B decision (Gap 3), manual local export feature (Gap 8) |
| Testing | Category list | + 13-item must-pass scenario checklist (Gap 13) |
| Threat model | Encryption + auth | + Explicit root-detection scoping decision, documented not forgotten (Gap 7) |
| Accessibility | Not addressed | + Non-leaking content descriptions (Gap 14) |

---

## OPEN DECISION (resolved)

**Gap 3 — Recovery strategy:** Option B (local recovery phrase, 100% offline/FOSS) selected at scaffold time. Everything else in this review is a direct addition to the plan.
