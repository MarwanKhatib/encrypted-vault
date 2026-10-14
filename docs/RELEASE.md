# Releases and safe updates

## Why updates erased data

Android keeps an app's data only when the new install has the **same signature** (signing key) as the installed app. A different key makes `adb install` or the system installer refuse the update. The only way forward is then to uninstall, and uninstalling deletes the vault.

Signature mismatches in this project:

| Build | Key | Keeps data on update? |
|---|---|---|
| `debug` (Android Studio, `assembleDebug`) | `~/.android/debug.keystore` on that machine | Only with other debug builds from the same machine |
| `release` with `keystore.properties` | `/home/u0/keys/vault-release.jks` | Yes, with the same keystore |
| `release` without `keystore.properties` | debug key | Only with that machine's debug builds |

Switching between a debug install and a release install therefore loses the vault. `scripts/device-checks.sh recents` used to do this on purpose; it no longer uninstalls.

## Rules

1. **Never distribute or install a debug-signed release.** The build prints a warning when `keystore.properties` is missing.
2. **Keep `vault-release.jks` and `keystore.properties` safe.** Back them up offline. Losing the keystore means no update can ever keep the vault.
3. **Bump `versionCode` in `app/build.gradle.kts` on every release.** Equal or newer is required.
4. **Update with `adb install -r app-release.apk`** (or the system installer). Never uninstall first.
5. **Before any reinstall, export a backup** (Settings → Backup → Export encrypted backup) and keep the passphrase. The backup restores on any device.
6. Room migrations keep existing data across schema changes. Schema changes need a migration and a test (see `CONTRIBUTING` in README).

## Publishing a release (private repo)

The signing key stays on this machine. It is not uploaded to GitHub.

```sh
cd /home/u0/project/encrypted-vault
./gradlew --no-daemon testDebugUnitTest ktlintCheck detekt assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
$HOME/Android/Sdk/build-tools/34.0.0/apksigner verify --print-certs "$APK"   # check the signer is vault-release
sha256sum "$APK" > app/build/outputs/apk/release/app-release.apk.sha256
git tag -a v0.2.0 -m "Vault 0.2.0"
git push origin v0.2.0
gh release create v0.2.0 "$APK" app/build/outputs/apk/release/app-release.apk.sha256 \
  --repo MarwanKhatib/encrypted-vault --title "Vault 0.2.0" --notes-file docs/RELEASE_NOTES_0.2.0.md
```

The repo is private, so the release is visible only to collaborators with access.

## Installing a release

```sh
sha256sum -c app-release.apk.sha256
adb install -r app-release.apk   # keeps data when the signer matches
```

If `adb install` reports `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or `signatures do not match`, stop. Do not uninstall. Your data is still there. Ask for the release signed with the same key.

## Signer fingerprint

Record the certificate SHA-256 of the release key here after the first `apksigner verify --print-certs`, so every later release can be checked against it:

- `vault-release` SHA-256 (signer certificate, from `apksigner verify --print-certs` on 0.2.0): `3bd2f0cdb487de92f9fa71fdbc95e8b8cc62bc2574cd74e2d2b227cbac186d98`
