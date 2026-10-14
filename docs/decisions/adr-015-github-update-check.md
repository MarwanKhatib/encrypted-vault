# ADR-015: Update check and install from GitHub releases

## Status
Accepted (2026-10-14). Replaces the "no INTERNET permission" rule from ADR-001 for update checks only. Repository made public for this. Device check pending.

## Context
Updates were installed by hand over adb. The user wants a notification when a new release is published, and an update button that downloads and installs it with a progress bar. Android cannot install silently for a normal app, and the repo is private, so a download needs a public URL.

## Decision
- **INTERNET** is declared, but only `update/ReleaseFeed` uses it: `api.github.com` for the latest release, and `github.com/MarwanKhatib/encrypted-vault/releases/download/` for the assets. HTTPS only. Other URLs are refused.
- **Repository is public.** Checked before the change: no keystore, no key, no real passwords in history (only `CHANGE_ME` placeholders). Releases are the only download path.
- **Daily check** (`UpdateCheckWorker`, WorkManager, network required). When a newer tag exists it posts a notification that opens Settings → Updates. It never downloads by itself.
- **Update button** (Settings → Updates): download the APK with a progress bar, then check in order: SHA-256 against the release's `.sha256` asset, then signer certificate against the installed app, then version is newer. Any failure deletes the download.
- **Install** through `PackageInstaller` (full install, same package). Android shows its confirm dialog; the user taps Install. The vault is kept because the signer is the same release key. The downloaded APK is deleted once the session is committed.
- **Result** comes back to `InstallResultReceiver` (not exported).
- **Permissions added:** `INTERNET`, `ACCESS_NETWORK_STATE`, `REQUEST_INSTALL_PACKAGES`, `POST_NOTIFICATIONS`.

## Alternatives considered
- **Private repo with a token in the app.** A token inside the APK can be extracted by anyone who has the APK. Rejected.
- **Silent install.** Not allowed for normal apps on Android. Rejected.
- **Updates through an app store.** Possible later; it changes distribution and trust.

## Consequences
- **The app now makes network requests.** Only to GitHub, only for update checks. Photos, keys, and vault metadata are never sent. GitHub sees the phone's IP address and the request time.
- **Publishing is the update channel.** A bad release reaches every phone that checks. Test each release on the test phone before publishing.
- **The signing key is the trust anchor.** If it is lost or leaks, updates can no longer be trusted. Keep `vault-release.jks` backed up offline.
- **The first build with the updater must be installed by hand** (`adb install -r`). Phones on older builds have no updater.
- **Users may need to allow installs from this app once** (Android "Install unknown apps" for this app). Android asks; the app cannot grant it.
- **Not verified on hardware:** download, confirm dialog, and install over a real phone.
